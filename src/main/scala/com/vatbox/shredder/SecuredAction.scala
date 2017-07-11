package com.vatbox.shredder

import com.typesafe.scalalogging.Logger
import com.vatbox.shredder.SecuredAction.ActionResources
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Configuration, Environment}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by erez on 21/07/2016.
  */
class LimitedRequest[A](val limitations: List[Statement],
                        request: Request[A],
                        val xTraceToken: Option[String],
                        val callingUser: Option[String],
                        val callingServer: Option[String],
                        val actionName: String) extends WrappedRequest[A](request) {

  val permissions: List[Permission] = limitations.flatMap(_.permissions)

  /**
    * Returns the string representing the initiator of this request / chain of requests
    *
    * @return the initiator
    */
  def getInitiator: String = {
    if (callingUser.isDefined)
      callingUser.get
    else
      callingServer.get
  }

  /**
    * Returns a string representation of the calling server and calling user (for logging)
    *
    * @return String in the format of Server: SERVERNAME, User: USERNAME
    *         or User: USERNAME or Server: SERVERNAME
    */
  def getAuthorizationString: String = {
    (callingUser, callingServer) match {
      case (Some(user), Some(server)) =>
        s"Server: $server, User: $user"
      case (Some(user), None) =>
        s"User: $user"
      case (None, Some(server)) =>
        s"Server: $server"
      case (None, None) =>
        "NotAvailable"
    }
  }

  // TODO: write tests
  /**
    * Determines if current user can access the resource in this action
    *
    * @param resourceName The name of the resource to check
    * @return True or False
    */
  def canAccessResource(resourceName: String): Boolean = {
    permissions.exists(_.all) || permissions.flatMap(_.resources).contains(resourceName)
  }

  // TODO: write tests
  /**
    * Determines if current user can access all these resources
    *
    * @param resourcesNames The name of the resources to check
    * @return True or False
    */
  def canAccessResource(resourcesNames: List[String]): Boolean = {
    resourcesNames.forall(canAccessResource)
  }
}

object SecuredAction {
  private val cache = new TimeCache(30 * 1000)

  def clearCache(): Unit = cache.clear()

  def apply(actionName: String)(implicit configuration: Configuration, wSClient: WSClient, environment: Environment): SecuredAction =
    new SecuredAction(actionName)(configuration, wSClient, environment)

  def apply(actionName: String, actionResources: ActionResources)
           (implicit configuration: Configuration, wSClient: WSClient, environment: Environment): SecuredAction = {
    new SecuredAction(actionName, Some(actionResources))(configuration, wSClient, environment)
  }

  case class ActionResources(actionResourceExtractor: Request[_] => Set[String],
                             allActionResources: Future[Set[String]])

}

class SecuredAction(val actionName: String,
                    val actionResources: Option[ActionResources] = None)
                   (implicit override val configuration: Configuration,
                    override val wSClient: WSClient,
                    override val environment: Environment)
  extends SecuredActionTrait {
  override val cache: TimeCache = SecuredAction.cache

  override def provider: PermissionProvider = new WSPermissionProvider(configuration.getString("shredder.krang_url").get)(wSClient, exec)
}

trait SecuredActionTrait extends ActionBuilder[LimitedRequest] with PermissionChecker with MDCLogging {
  def actionName: String

  def actionResources: Option[ActionResources]

  def configuration: Configuration

  def wSClient: WSClient

  def environment: Environment

  def serverName: String = configuration.getString("shredder.this_server_name").get

  def cache: TimeCache

  implicit val exec: ExecutionContext = executionContext
  implicit val log: Logger = logger

  def invokeBlock[A](request: Request[A], block: (LimitedRequest[A]) => Future[Result]): Future[Result] = {

    val optCallingUserId = request.headers.get(userHeader)
    val optCallingServerId = request.headers.get(serverHeader)
    val optXTraceToken = request.headers.get(X_TRACE_TOKEN_ATTR_NAME)

    val authString = getAuthorizationString(optCallingUserId, optCallingServerId)
    val actionString = s"$serverName::$actionName"
    val logSuffix = s" [remoteAddress=${request.remoteAddress}] ${optXTraceToken.map(t => s"; [$X_TRACE_TOKEN_ATTR_NAME=$t]").getOrElse("")}"

    if ((environment.mode == play.api.Mode.Test && configuration.getBoolean("shredder.ignore_in_test").getOrElse(false))
      || configuration.getBoolean("shredder.byPass").getOrElse(false)) {
      val callingUser = optCallingUserId.getOrElse("Ignored User")
      block(new LimitedRequest(Nil, request, optXTraceToken, Some(callingUser), optCallingServerId, actionName))
    } else {
      val q: Future[(Boolean, List[Statement])] =
        canPerformAction(optCallingUserId, optCallingServerId, optXTraceToken, serverName, actionName)
      q.flatMap {
        case (false, _) =>
          val msg = s"$authString not allowed to perform $actionString$logSuffix"
          logDebugWithMDC(extractMDCFromIncomingRequest(request))(msg)
          Future.successful(Results.Forbidden(msg))
        case (true, limitations) =>
          logDebugWithMDC(extractMDCFromIncomingRequest(request))(s"$authString is allowed to perform $actionString with limitations $limitations$logSuffix")
          actionResources match {
            case Some(ActionResources(actionResourceExtractor, futureAllActionResources)) =>
              val futureRequestedResources: Future[Set[String]] = Try {
                actionResourceExtractor(request)
              } match {
                case Success(resources) =>
                  logDebugWithMDC(extractMDCFromIncomingRequest(request))(s"$authString asks to perform $actionString " +
                    s"on resources [${resources.mkString(",")}]$logSuffix")
                  Future successful resources
                case Failure(t) => futureAllActionResources.map { allResources =>
                  logWarnExceptionWithMDC(extractMDCFromIncomingRequest(request))(s"actionResourceExtractor for $actionString threw exception - assuming all " +
                    s"action resource privileges required, i.e: [${allResources.mkString(",")}]$logSuffix", t)
                  allResources
                }
              }
              (for {
                allResources <- futureAllActionResources
                requestedResources <- futureRequestedResources
                allowedResources <- actionAllowedResources(optCallingUserId, optCallingServerId, serverName, actionName, allResources)
              } yield (requestedResources -- allowedResources, requestedResources, allowedResources)) flatMap {
                case (unallowed, requested, allowed) if unallowed.isEmpty =>
                  logDebugWithMDC(extractMDCFromIncomingRequest(request))(s"$authString is allowed to perform $actionString [requested resources: " +
                    s"${requested.mkString(",")}] [allowed resources: ${allowed.mkString(",")}]$logSuffix")
                  block(new LimitedRequest(limitations, request, optXTraceToken, optCallingUserId, optCallingServerId, actionName))
                case (unallowed, requested, allowed) =>
                  val msg = s"$authString not allowed to perform " +
                    s"$actionString - [requested resources: ${requested.mkString(",")}] " +
                    s"[allowed resources: ${allowed.mkString(",")}] [unallowed resources: ${unallowed.mkString(",")}]$logSuffix"
                  logDebugWithMDC(extractMDCFromIncomingRequest(request))(msg)
                  Future.successful(Results.Forbidden(msg))
              }
            case None =>
              block(new LimitedRequest(limitations, request, optXTraceToken, optCallingUserId, optCallingServerId, actionName))
          }
      }
      /*.recoverWith {
              case t:Throwable => logger error (s"action $actionName unauthorized due to unexpected response from Krang OR " +
                s"unexpected failure of secured action $actionName", t)
                Future successful Results.Forbidden(s"action $serverName::$actionName unauthorized due to unexpected " +
                  s"response from Krang OR unexpected failure of secured action. check $serverName logs for further details.")
            }*/
    }
  }

  private def getAuthorizationString(callingUser: Option[String], callingServer: Option[String]): String = {
    (callingUser, callingServer) match {
      case (Some(user), Some(server)) =>
        s"Server: $server, User: $user"
      case (Some(user), None) =>
        s"User: $user"
      case (None, Some(server)) =>
        s"Server: $server"
      case (None, None) =>
        s"NotAvailable"
    }
  }
}