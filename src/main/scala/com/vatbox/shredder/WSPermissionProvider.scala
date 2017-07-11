package com.vatbox.shredder

import java.net.ConnectException

import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods
import play.api.libs.ws.{WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by erez on 25/07/2016.
  */
class WSPermissionProvider(serverUrl: String)(implicit wSClient: WSClient, executionContext: ExecutionContext) extends PermissionProvider with MDCLogging{
  implicit val formats = DefaultFormats

  override def provide(name: String)(implicit xTraceToken: Option[String]): Future[List[Statement]] = {
    val req: WSRequest = {
      val midReq: WSRequest = wSClient.url(s"$serverUrl/internal/api/v1/credentials?name=$name")
      if (xTraceToken.isDefined) {
        midReq.withHeaders(X_TRACE_TOKEN_ATTR_NAME -> xTraceToken.get)
      } else midReq
    }
    req.get
      .map(a => JsonMethods.parse(a.body).extract[List[Statement]])
      .recoverWith{case t:ConnectException => Future failed new ConnectException(s"failed communicating with krang - ${t.getMessage}")}
  }
}
