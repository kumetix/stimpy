package com.vatbox.shredder

import org.joda.time.DateTime

import scala.collection.concurrent.TrieMap

/**
  * Created by erez on 21/07/2016.
  */
class TimeCache(timeoutInMilis : Int) {
  private val insideCache: TrieMap[String, (List[Statement], DateTime)] = TrieMap()

  def addToCache(key: String, value : List[Statement]) = insideCache.put(key, (value, DateTime.now()))

  def getFromCache(key: String) = insideCache.get(key).flatMap(data => if (data._2.plusMillis(timeoutInMilis).isBeforeNow) None else Some(data._1))

  def clear() = insideCache.clear
}
