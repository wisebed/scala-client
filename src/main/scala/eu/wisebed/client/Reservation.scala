package eu.wisebed.client

import de.uniluebeck.itm.tr.util._
import eu.wisebed.api.v3.controller.{SingleNodeRequestStatus, RequestStatus}
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

abstract class Reservation(val wsn: WSN) extends Logging with HasExecutor {

  assertConnected()

  abstract class Operation {

    def isComplete(value: Int): Boolean
  }

  object SEND_OPERATION extends Operation {

    def isComplete(value: Int) = value >= 0
  }

  object ARE_NODES_ALIVE_OPERATION extends Operation {

    def isComplete(value: Int) = value >= 0
  }

  object RESET_OPERATION extends Operation {

    def isComplete(value: Int) = value >= 1
  }

  object FLASH_OPERATION extends Operation {

    def isComplete(value: Int) = value >= 100
  }

  // TODO model life cycle of experiment: CONNECTED -> RUNNING -> ENDED

  private val requestIdGenerator: Random = new Random()

  private val requestCache: TimedCache[Long, (Operation, ProgressSettableFutureMap[NodeUrn, SingleNodeRequestStatus])] =
    new TimedCache()

  private var nodesAttachedListeners: List[(DateTime, List[NodeUrn]) => Unit] = Nil

  def onNodesAttached(listener: (DateTime, List[NodeUrn]) => Unit) {
    nodesAttachedListeners ::= listener
  }

  protected def notifyNodesAttached(timestamp: DateTime, nodeUrns: List[NodeUrn]) {
    for (listener <- nodesAttachedListeners) {
      listener(timestamp, nodeUrns)
    }
  }

  private var nodesDetachedListeners: List[(DateTime, List[NodeUrn]) => Unit] = Nil

  protected def notifyNodesDetached(timestamp: DateTime, nodeUrns: List[NodeUrn]) {
    for (listener <- nodesDetachedListeners) {
      listener(timestamp, nodeUrns)
    }
  }

  def onNodesDetached(listener: (DateTime, List[NodeUrn]) => Unit) {
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

  private var experimentStartedListeners: List[DateTime => Unit] = Nil

  def onExperimentStarted(listener: DateTime => Unit) {
    experimentStartedListeners ::= listener
  }

  protected def notifyExperimentStarted(timestamp: DateTime) {
    for (listener <- experimentStartedListeners) {
      listener(timestamp)
    }
  }

  private var experimentEndedListeners: List[DateTime => Unit] = Nil

  def onExperimentEnded(listener: DateTime => Unit) {
    experimentEndedListeners ::= listener
  }

  protected def notifyExperimentEnded(timestamp: DateTime) {
    for (listener <- experimentEndedListeners) {
      listener(timestamp)
    }
  }

  def areNodesAlive(nodeUrns: List[NodeUrn], timeout: Int, timeUnit: TimeUnit): RequestTracker = {
    assertConnected()
    executeRequest(nodeUrns, ARE_NODES_ALIVE_OPERATION, timeout, timeUnit, requestId => {
      wsn.areNodesAlive(requestId, nodeUrns)
    })
  }

  def flash(nodeUrns: List[NodeUrn], imageBytes: Array[Byte], timeout: Long, timeUnit: TimeUnit): RequestTracker = {
    assertConnected()
    executeRequest(nodeUrns, FLASH_OPERATION, timeout, timeUnit, requestId => {
      wsn.flashPrograms(requestId, createFlashProgramsConfigurationList(nodeUrns, imageBytes))
    })
  }

  def reset(nodeUrns: List[NodeUrn], timeout: Int, timeUnit: TimeUnit): RequestTracker = {
    assertConnected()
    executeRequest(nodeUrns, RESET_OPERATION, timeout, timeUnit, requestId => {
      wsn.resetNodes(requestId, nodeUrns)
    })
  }

  def send(nodeUrns: List[NodeUrn], bytes: Array[Byte], timeout: Int, timeUnit: TimeUnit): RequestTracker = {
    assertConnected()
    executeRequest(nodeUrns, SEND_OPERATION, timeout, timeUnit, requestId => wsn.send(requestId, nodeUrns, bytes))
  }

  protected def executeRequest(nodeUrns: List[NodeUrn],
                               operation: Operation,
                               timeout: Long,
                               timeUnit: TimeUnit,
                               runnable: Long => Unit): RequestTracker = {
    val requestId = requestIdGenerator.nextLong()
    val requestMap = new ProgressSettableFutureMap[NodeUrn, SingleNodeRequestStatus](
      Map(nodeUrns.map(nodeUrn => (nodeUrn, ProgressSettableFuture.create[SingleNodeRequestStatus]())): _*)
    )
    requestCache.put(requestId, (operation, requestMap), timeout, timeUnit)
    executor.execute(new Runnable {
      def run() {
        runnable(requestId)
      }
    })
    new RequestTracker(requestMap)
  }

  protected def createFlashProgramsConfigurationList(nodeUrns: List[NodeUrn],
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

  def shutdown() {
    shutdownExecutor()
  }

  protected def assertConnected()

  protected def progressRequestStatusReceived(requestStatus: RequestStatus) {
    val requestId = requestStatus.getRequestId
    requestCache.get(requestId) match {
      case (operation, progressMap) => {
        for (status <- requestStatus.getSingleNodeRequestStatus) {

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
                val e = new NodeRequestFailedException(nodeUrn, value, new Exception(msg))
                future.asInstanceOf[ProgressSettableFuture[SingleNodeRequestStatus]].setException(e)
              } else if (operation.isComplete(value)) {
                future.asInstanceOf[ProgressSettableFuture[SingleNodeRequestStatus]].set(status)
              } else if (value >= 0) {
                future.asInstanceOf[ProgressSettableFuture[SingleNodeRequestStatus]].setProgress(value.toFloat / 100)
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