package com.vatbox.shredder

import scala.concurrent.Future

/**
  * Created by erez on 25/07/2016.
  */
trait PermissionProvider {
  def provide(name:String)(implicit xTraceToken:Option[String]) : Future[List[Statement]]
}
