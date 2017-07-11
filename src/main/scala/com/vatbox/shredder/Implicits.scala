package com.vatbox.shredder

import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by assafepstein on 25/09/2016.
  */
object Implicits {

  implicit class RichFuture[T](f: Future[T]) {
    def logOnCompletion(action: String)(implicit log: Logger, ec: ExecutionContext): Future[T] = f andThen {
      case Success(result) => log debug s"async action '$action' completed successfully with result: $result"
      case Failure(throwable) => log info s"async action '$action' failed: $throwable"
    }
  }

}

