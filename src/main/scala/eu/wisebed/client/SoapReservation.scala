package eu.wisebed.client

import eu.wisebed.api.v3.wsn.WSN
import eu.wisebed.api.v3.controller.{Controller, RequestStatus}
import eu.wisebed.api.v3.common.Message
import java.net.URL
import scala.collection.JavaConversions._
import org.joda.time.DateTime
import eu.wisebed.api.v3.common.NodeUrn
import javax.xml.ws.Endpoint
import util.Logging

class SoapReservation(val wsn1: WSN,
                      val controllerEndpointUrl: URL,
                      private var _endpoint: Option[javax.xml.ws.Endpoint] = None)
  extends Reservation(wsn1) with Logging {

  @javax.jws.WebService(
    name = "Controller",
    endpointInterface = "eu.wisebed.api.v3.controller.Controller",
    portName = "ControllerPort",
    serviceName = "ControllerService",
    targetNamespace = "http://wisebed.eu/api/v3/controller")
  private class SoapReservationController extends Controller {

    override def nodesAttached(timestamp: DateTime, nodeUrns: java.util.List[NodeUrn]) {
      notifyNodesAttached(timestamp, List(nodeUrns: _*))
    }

    override def nodesDetached(timestamp: DateTime, nodeUrns: java.util.List[NodeUrn]) {
      notifyNodesDetached(timestamp, List(nodeUrns: _*))
    }

    override def receive(messageList: java.util.List[Message]) {
      logger.trace("SoapReservationController.receive(" + messageList + ")")
      for (message <- messageList) {
        val nodeUrn = message.getSourceNodeUrn
        val timestamp = new DateTime(message.getTimestamp.toGregorianCalendar)
        val buffer = message.getBinaryData
        notifyMessage(nodeUrn, timestamp, buffer)
      }
    }

    override def receiveStatus(requestStatusList: java.util.List[RequestStatus]) {
      logger.trace("SoapReservationController.receiveStatus(" + requestStatusList + ")")
      for (requestStatus <- requestStatusList) {
        progressRequestStatusReceived(requestStatus)
      }
    }

    override def receiveNotification(notificationList: java.util.List[eu.wisebed.api.v3.controller.Notification]) {
      logger.info("SoapReservationController.receiveNotification(" + notificationList + ")")
      for (notification <- notificationList) {
        val nodeUrn: Option[NodeUrn] = notification.getNodeUrn match {
          case urn: NodeUrn => Some(urn)
          case _ => None
        }
        notifyNotification(new Notification(nodeUrn, notification.getTimestamp, notification.getMsg))
      }
    }

    override def reservationStarted(timestamp: DateTime) {
      logger.trace("SoapReservationController.experimentStarted(" + timestamp + ")")
      notifyExperimentStarted(timestamp)
    }

    override def reservationEnded(timestamp: DateTime) {
      logger.trace("SoapReservationController.experimentEnded(" + timestamp + ")")
      notifyExperimentEnded(timestamp)
    }
  }

  protected def assertConnected() {
    _endpoint match {
      case Some(_) => // nothing to do
      case None => {
        val endpoint: Endpoint = Endpoint.publish(controllerEndpointUrl.toString, new SoapReservationController())
        _endpoint = Some(endpoint)
        wsn.addController(controllerEndpointUrl.toString, null)
      }
    }
  }

  override def shutdown() {
    super.shutdown()
    _endpoint match {
      case Some(e) => {
        try {
          wsn.removeController(controllerEndpointUrl.toString, null)
        } catch {
          case e: Exception => // ignore
        }
        e.stop()
        _endpoint = None
      }
      case None => // nothing to do
    }
  }
}