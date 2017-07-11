package com.vatbox.shredder

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by erez on 25/07/2016.
  */
case class Statement(allow: Boolean, actions: List[ActionData], permissions: List[Permission], fromRole: Option[String] = None)

case class Permission(all: Boolean = false, resources: List[String] = Nil)

case class ActionData(service: String, action: String)

trait PermissionChecker {
  def cache: TimeCache

  def provider: PermissionProvider

  val all = "*"

  protected def canPerformAction(callingUser: Option[String],
                                 callingServer: Option[String],
                                 xTraceToken: Option[String],
                                 serverName: String,
                                 actionName: String)
                                (implicit executionContext: ExecutionContext): Future[(Boolean, List[Statement])] = {
    for {
      serverPermissions <- callingServer match {
        case Some(server) => getPermissions(server)
        case None => Future.successful(Nil)
      }

      userPermissions <- callingUser match {
        case Some(user) => getPermissions(user)
        case None => Future.successful(Nil)
      }

      actionInPermissions = (serverPermissions ::: userPermissions).filter(statement =>
        statement.actions.contains(ActionData(serverName, actionName)) ||
          statement.actions.contains(ActionData(all, actionName)) ||
          statement.actions.contains(ActionData(serverName, all)) ||
          statement.actions.contains(ActionData(all, all)))

    } yield (actionInPermissions.nonEmpty && actionInPermissions.forall(_.allow), actionInPermissions)
  }

  protected def actionAllowedResources(callingUser: Option[String],
                                       callingServer: Option[String],
                                       serverName: String,
                                       actionName: String,
                                       allActionResources: Set[String])
                                      (implicit executionContext: ExecutionContext): Future[Set[String]] = {

    def statementAllowedResources(serviceName: String,
                                          actionName: String,
                                          statement: Statement,
                                          allActionResources: Set[String]): Set[String] = {
      statement.actions.flatMap {
        case ActionData(`all`, `all`) => permissionsAllowedResources(statement.permissions, allActionResources)
        case ActionData(`serviceName`, `all`) => permissionsAllowedResources(statement.permissions, allActionResources)
        case ActionData(`serviceName`, `actionName`) => permissionsAllowedResources(statement.permissions, allActionResources)
        case _ => Set.empty[String]
      }.toSet
    }

    def permissionsAllowedResources(permissions: List[Permission], allActionResources: Set[String]) = {
      permissions.flatMap {
        case Permission(true, _) => allActionResources
        case Permission(false, resources) => resources
      }
    }

    for {
      serverPermissions <- callingServer match {
        case Some(server) => getPermissions(server)
        case None => Future.successful(Nil)
      }

      userPermissions <- callingUser match {
        case Some(user) => getPermissions(user)
        case None => Future.successful(Nil)
      }

      (allowedStatements, unallowedStatements) = (serverPermissions ::: userPermissions).partition(_.allow)
      allowedResources = allowedStatements
        .flatMap(s => statementAllowedResources(serverName, actionName, s, allActionResources)).toSet
      unallowedResources = unallowedStatements
        .flatMap(s => statementAllowedResources(serverName, actionName, s, allActionResources)).toSet

    } yield allowedResources -- unallowedResources
  }

  private def getPermissions(name: String)(implicit executionContext: ExecutionContext, xTraceToken: Option[String] = None): Future[List[Statement]] = {
    cache.getFromCache(name) match {
      case Some(x) => Future.successful(x)
      case None =>
        val res = provider.provide(name)

        res.onSuccess { case a => cache.addToCache(name, a) }
        res
    }
  }
}