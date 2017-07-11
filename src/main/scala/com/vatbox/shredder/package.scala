package com.vatbox

import play.api.Configuration
import play.api.libs.ws.WSRequest
import play.api.mvc.{AnyContent, Request}

import scala.language.implicitConversions

/**
  * Created by erez on 21/07/2016.
  */
package object shredder {
  val userHeader = "vatbox-user-id"
  val serverHeader = "CallingServer"

  implicit def wsRequestToSecuredWSRequest(req: WSRequest): SecuredWSRequest = new SecuredWSRequest(req)
  implicit def wsSecuredRequestToWSRequest(req: SecuredWSRequest): WSRequest = req.underlyingRequest

  class SecuredWSRequest(val underlyingRequest : WSRequest) {
    def withServerCredentials(implicit configuration: Configuration) : WSRequest = {
      underlyingRequest.withHeaders((serverHeader, configuration.getString("shredder.this_server_name").get))
    }

    def withUserCredentials(userName: String) : WSRequest = {
      underlyingRequest.withHeaders((userHeader, userName))
    }

    def withUserCredentials(req: Request[AnyContent]) : WSRequest = {
      underlyingRequest.withHeaders((userHeader, req.headers.get(userHeader).get))
    }
  }
}