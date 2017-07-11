package com.vatbox.shredder

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MDC
import play.api.mvc.Request

import scala.collection.JavaConversions._

trait MDCLogging extends LazyLogging {

  val X_TRACE_TOKEN_ATTR_NAME = "x-trace-token"

  def logDebugWithMDC[T](mdc: => Map[String,String])(msg: => String): Unit = if (logger.underlying.isDebugEnabled) {
    MDC.setContextMap(mdc)
    try {logger.debug(msg)} finally {
      mdc.foreach{e => MDC.remove(e._1)}
    }
  }

  def logInfoWithMDC[T](mdc: => Map[String,String])(msg: => String): Unit = if (logger.underlying.isInfoEnabled) {
    MDC.setContextMap(mdc)
    try {logger.info(msg)} finally {
      mdc.foreach{e => MDC.remove(e._1)}
    }
  }

  def logInfoExceptionWithMDC[T](mdc: => Map[String,String])(msg: => String,t:Throwable): Unit = if (logger.underlying.isInfoEnabled) {
    MDC.setContextMap(mdc)
    try {logger.info(msg,t)} finally {
      mdc.foreach{e => MDC.remove(e._1)}
    }
  }

  def logWarnWithMDC[T](mdc: => Map[String,String])(msg: => String): Unit = if (logger.underlying.isWarnEnabled) {
    MDC.setContextMap(mdc)
    try {logger.warn(msg)} finally {
      mdc.foreach{e => MDC.remove(e._1)}
    }
  }

  def logWarnExceptionWithMDC[T](mdc: => Map[String,String])(msg: => String,t:Throwable): Unit = if (logger.underlying.isWarnEnabled) {
    MDC.setContextMap(mdc)
    try {logger.warn(msg,t)} finally {
      mdc.foreach{e => MDC.remove(e._1)}
    }
  }

  def logErrorWithMDC[T](mdc: => Map[String,String])(msg: => String): Unit = if (logger.underlying.isErrorEnabled) {
    MDC.setContextMap(mdc)
    try {logger.error(msg)} finally {
      mdc.foreach{e => MDC.remove(e._1)}
    }
  }

  def logErrorExceptionWithMDC[T](mdc: => Map[String,String])(msg: => String,t:Throwable): Unit = if (logger.underlying.isErrorEnabled) {
    MDC.setContextMap(mdc)
    try {logger.error(msg,t)} finally {
      mdc.foreach{e => MDC.remove(e._1)}
    }
  }

  def extractMDCFromIncomingRequest(request: Request[_]): Map[String, String] = List(
    request.headers.get(X_TRACE_TOKEN_ATTR_NAME).map(X_TRACE_TOKEN_ATTR_NAME -> _),
    Some("remoteAddress" -> request.remoteAddress)
  ).flatten.toMap
}
