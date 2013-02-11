package eu.wisebed.client

import de.uniluebeck.itm.tr.util._
import eu.wisebed.api.v3.controller.RequestStatus
import eu.wisebed.api.v3.wsn.{FlashProgramsConfiguration, WSN}
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import scala.util.Random
import scala.collection.JavaConversions._
import scala.collection.immutable.Nil
import eu.wisebed.api.v3.common.NodeUrn
import util.Logging
import eu.wisebed.wiseml.WiseMLHelper
import scala.Some

abstract class Reservation(val wsn: WSN) extends Logging {

  assertConnected()

  abstract class Operation {

    def isComplete(value: Int): Boolean
  }

  object RESET_OPERATION extends Operation {

    def isComplete(value: Int) = value >= 1
  }

  object FLASH_OPERATION extends Operation {

    def isComplete(value: Int) = value >= 100
  }

  // TODO model life cycle of experiment: CONNECTED -> RUNNING -> ENDED

  private val requestIdGenerator: Random = new Random()

  private val requestCache: TimedCache[Long, (Operation, ProgressSettableFutureMap[NodeUrn, Any])] = new TimedCache()

  private var nodesAttachedListeners: List[List[NodeUrn] => Unit] = Nil

  def onNodesAttached(listener: List[NodeUrn] => Unit) {
    nodesAttachedListeners ::= listener
  }

  protected def notifyNodesAttached(nodeUrns: List[NodeUrn]) {
    for (listener <- nodesAttachedListeners) {
      listener(nodeUrns)
    }
  }

  private var nodesDetachedListeners: List[List[NodeUrn] => Unit] = Nil

  protected def notifyNodesDetached(nodeUrns: List[NodeUrn]) {
    for (listener <- nodesDetachedListeners) {
      listener(nodeUrns)
    }
  }

  def onNodesDetached(listener: List[NodeUrn] => Unit) {
    nodesDetachedListeners ::= listener
  }

  case class Notification(nodeUrn: Option[NodeUrn], timestamp: DateTime, msg: String)

  private var notificationListeners: List[Notification => Unit] = Nil

  def onNotification(listener: Notification => Unit) {
    notificationListeners ::= listener
  }

  protected def notifyNotification(notification: Notification) {
    for (listener <- notificationListeners) {
      listener(notification)
    }
  }

  private var messageListeners: List[(NodeUrn, DateTime, Array[Byte]) => Unit] = Nil

  def onMessage(listener: (NodeUrn, DateTime, Array[Byte]) => Unit) {
    messageListeners ::= listener
  }

  protected def notifyMessage(nodeUrn: NodeUrn, timestamp: DateTime, buffer: Array[Byte]) {
    for (listener <- messageListeners) {
      listener(nodeUrn, timestamp, buffer)
    }
  }

  private var experimentStartedListeners: List[() => Unit] = Nil

  def onExperimentStarted(listener: () => Unit) {
    experimentStartedListeners ::= listener
  }

  protected def notifyExperimentStarted() {
    for (listener <- experimentStartedListeners) {
      listener()
    }
  }

  private var experimentEndedListeners: List[() => Unit] = Nil

  def onExperimentEnded(listener: () => Unit) {
    experimentEndedListeners ::= listener
  }

  protected def notifyExperimentEnded() {
    for (listener <- experimentEndedListeners) {
      listener()
    }
  }

  def areNodesAlive(nodeUrns: List[NodeUrn],
                    timeout: Int,
                    timeUnit: TimeUnit): ProgressListenableFutureMap[NodeUrn, Any] = {
    assertConnected()
    val (requestId, requestMap) = prepareRequest(nodeUrns, timeout, timeUnit)
    wsn.areNodesAlive(requestId, nodeUrns)
    requestMap
  }

  def reset(nodeUrns: List[NodeUrn],
            timeout: Int,
            timeUnit: TimeUnit): ProgressListenableFutureMap[NodeUrn, Any] = {
    assertConnected()
    val (requestId, requestMap) = prepareRequest(nodeUrns, timeout, timeUnit)
    wsn.resetNodes(requestId, nodeUrns)
    requestMap
  }

  def flash(nodeUrns: List[NodeUrn],
            imageBytes: Array[Byte],
            timeout: Long,
            timeUnit: TimeUnit): ProgressListenableFutureMap[NodeUrn, Any] = {
    assertConnected()
    val (requestId, requestMap) = prepareRequest(nodeUrns, timeout, timeUnit)
    wsn.flashPrograms(requestId, createFlashProgramsConfigurationList(nodeUrns, imageBytes))
    requestMap
  }

  private def prepareRequest(nodeUrns: List[NodeUrn],
                             timeout: Long,
                             timeUnit: TimeUnit): (Long, ProgressSettableFutureMap[NodeUrn, Any]) = {
    val requestId = requestIdGenerator.nextLong()
    val requestMap = new ProgressSettableFutureMap[NodeUrn, Any](
      Map(nodeUrns.map(nodeUrn => (nodeUrn, ProgressSettableFuture.create[Any]())): _*)
    )

    requestCache.put(requestId, (RESET_OPERATION, requestMap), timeout, timeUnit)

    (requestId, requestMap)
  }

  private def createFlashProgramsConfigurationList(nodeUrns: List[NodeUrn],
                                                   imageBytes: Array[Byte]): List[FlashProgramsConfiguration] = {
    val config = new FlashProgramsConfiguration()
    config.getNodeUrns.addAll(nodeUrns)
    config.setProgram(imageBytes)
    List(config)
  }

  def reservedNodeUrns(): List[NodeUrn] = {
    assertConnected()
    WiseMLHelper.getNodeUrns(wsn.getNetwork).toList.map(nodeUrnString => {
      new NodeUrn(nodeUrnString)
    })
  }

  def shutdown()

  protected def assertConnected()

  protected def progressRequestStatusReceived(requestStatus: RequestStatus) {
    val requestId = requestStatus.getRequestId
    requestCache.get(requestId) match {
      case (operation, progressMap) => {
        for (status <- requestStatus.getStatus) {

          val nodeUrn = status.getNodeUrn
          val value = status.getValue
          val msg = status.getMsg
          val futureEntry = if (progressMap.containsKey(nodeUrn)) {
            Some(progressMap.get(nodeUrn))
          } else {
            None
          }

          futureEntry match {
            case Some(future) => {
              if (value < 0) {
                future.asInstanceOf[ProgressSettableFuture[Any]].setException(new Exception(msg))
              } else if (operation.isComplete(value)) {
                future.asInstanceOf[ProgressSettableFuture[Any]].set(Unit)
              } else if (value >= 0) {
                future.asInstanceOf[ProgressSettableFuture[Any]].setProgress(value.toFloat / 100)
              }
            }
            case _ => logger.warn(
              "Received request status for known requestId (" + requestId + ") but unknown node URN (" + nodeUrn + ")")
          }
        }
      }
      case _ => logger.trace("Ignoring requestStatus received for unknown requestId " + requestId)
    }
  }
}