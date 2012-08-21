package eu.wisebed.client

import eu.wisebed.api.v3.wsn.WSN
import eu.wisebed.api.v3.controller.{Notification, Controller, RequestStatus}
import eu.wisebed.api.v3.common.Message
import java.net.URL
import com.weiglewilczek.slf4s.Logging
import scala.collection.JavaConversions._
import org.joda.time.DateTime

class SoapReservation(wsn1: WSN,
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

    def nodesAttached(nodeUrns: java.util.List[String]) {
      notifyNodesAttached(List(nodeUrns:_*))
    }

    def nodesDetached(nodeUrns: java.util.List[String]) {
      notifyNodesDetached(List(nodeUrns:_*))
    }

    def receive(messageList: java.util.List[Message]) {
      logger.trace("SoapReservationController.receive(" + messageList + ")")
      for (message <- messageList) {
        val nodeUrn = message.getSourceNodeUrn
        val timestamp = new DateTime(message.getTimestamp.toGregorianCalendar)
        val buffer = message.getBinaryData
        notifyMessage(nodeUrn, timestamp, buffer)
      }
    }

    def receiveStatus(requestStatusList: java.util.List[RequestStatus]) {
      logger.trace("SoapReservationController.receiveStatus(" + requestStatusList + ")")
      for (requestStatus <- requestStatusList) {
        progressRequestStatusReceived(requestStatus)
      }
    }

    def receiveNotification(notificationList: java.util.List[eu.wisebed.api.v3.controller.Notification]) {
      logger.info("SoapReservationController.receiveNotification(" + notificationList + ")")
      for (notification <- notificationList) {
        val nodeUrn: String = notification.getNodeUrn
        val timestamp: DateTime = new DateTime(notification.getTimestamp.toGregorianCalendar)
        val msg: String = notification.getMsg
        notifyNotification(new Notification(nodeUrn, timestamp, msg))
      }
    }

    def reservationStarted() {
      logger.trace("SoapReservationController.experimentStarted()")
      notifyExperimentStarted()
    }

    def reservationEnded() {
      logger.trace("SoapReservationController.experimentEnded()")
      notifyExperimentEnded()
    }
  }

  protected def assertConnected() {
    _endpoint match {
      case Some(_) => // nothing to do
      case None => {
        _endpoint = Some(javax.xml.ws.Endpoint.publish(
          controllerEndpointUrl.toString,
          new SoapReservationController()))
        wsn.addController(controllerEndpointUrl.toString)
      }
    }
  }

  def shutdown() {
    _endpoint match {
      case Some(e) => {
        try {
          wsn.removeController(controllerEndpointUrl.toString)
        } catch {
          case e:Exception => // ignore
        }
        e.stop()
        _endpoint = None
      }
      case None => // nothing to do
    }
  }
}