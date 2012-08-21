package eu.wisebed.client

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

import com.weiglewilczek.slf4s.Logging

import de.uniluebeck.itm.tr.util.ProgressListenableFuture
import de.uniluebeck.itm.tr.util.ProgressSettableFuture
import de.uniluebeck.itm.tr.util.TimedCache

import eu.wisebed.api.v3.controller.RequestStatus
import eu.wisebed.api.v3.wsn.WSN

import java.util.concurrent.TimeUnit

import org.joda.time.DateTime

import scala.util.Random
import scala.collection.JavaConversions._
import scala.collection.immutable.Nil
import javax.annotation.Nullable


abstract class Reservation(val wsn: WSN) extends Logging {

  assertConnected()

  abstract class Operation { def isComplete(value: Int): Boolean }
  object RESET extends Operation { def isComplete(value: Int) = value >= 1 }

  // TODO model life cycle of experiment: CONNECTED -> RUNNING -> ENDED

  private val requestIdGenerator: Random = new Random()

  private val requestCache: TimedCache[Long, (Operation, Map[String, ProgressSettableFuture[Any]])] = new TimedCache()

  case class Notification(@Nullable nodeUrn:String, timestamp: DateTime, msg: String)
  private var notificationListeners: List[Notification => Unit] = Nil
  def onNotification(listener: Notification => Unit) = notificationListeners ::= listener
  protected def notifyNotification(notification: Notification) = for (listener <- notificationListeners) listener(notification)

  private var messageListeners: List[(String, DateTime, Array[Byte]) => Unit] = Nil
  def onMessage(listener: (String, DateTime, Array[Byte]) => Unit) = messageListeners ::= listener
  protected def notifyMessage(nodeUrn: String, timestamp: DateTime, buffer: Array[Byte]) = {
    for (listener <- messageListeners) listener(nodeUrn, timestamp, buffer)
  }

  private var experimentStartedListeners: List[() => Unit] = Nil
  def onExperimentStarted(listener: () => Unit) = experimentStartedListeners ::= listener
  protected def notifyExperimentStarted() = for (listener <- experimentStartedListeners) listener()

  private var experimentEndedListeners: List[() => Unit] = Nil
  def onExperimentEnded(listener: () => Unit) = experimentEndedListeners ::= listener
  protected def notifyExperimentEnded() = for (listener <- experimentEndedListeners) listener()

  def reset(nodeUrns: List[String], timeout: Int, timeUnit: TimeUnit): (ListenableFuture[java.util.List[Any]], Map[String, ProgressSettableFuture[Any]]) = {

    assertConnected()

    val requestId = requestIdGenerator.nextLong()
    val requestMap = Map(nodeUrns.map(nodeUrn => (nodeUrn, ProgressSettableFuture.create[Any]())): _*)

    var futures: List[ProgressListenableFuture[Any]] = Nil
    for (future <- requestMap.values) futures ::= future
    val requestFuture: ListenableFuture[java.util.List[Any]] = Futures.allAsList(futures)

    requestCache.put(requestId, (RESET, requestMap), timeout, timeUnit)
    wsn.resetNodes(requestId, nodeUrns)

    (requestFuture, requestMap)
  }

  def shutdown()

  protected def assertConnected()

  protected def progressRequestStatusReceived(requestStatus: RequestStatus) {
    val requestId = requestStatus.getRequestId()
    requestCache.get(requestId) match {
      case (operation, progressMap) => {
        for (status <- requestStatus.getStatus()) {

          val nodeUrn = status.getNodeUrn()
          val value = status.getValue()
          val msg = status.getMsg()
          val futureEntry = progressMap.get(nodeUrn)

          futureEntry match {
            case Some(future) => {
              if (value < 0) {
                future.setException(new Exception(msg))
              } else if (operation.isComplete(value)) {
                future.set(Unit)
              } else if (value >= 1) {
                future.setProgress(value / 100)
              }
            }
            case _ => logger.warn("Received request status for known requestId (" + requestId + ") but unknown node URN (" + nodeUrn + ")")
          }
        }
      }
      case _ => logger.trace("Ignoring requestStatus received for unknown requestId " + requestId)
    }
  }
}