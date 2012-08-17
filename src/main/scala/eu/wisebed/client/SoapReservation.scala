package eu.wisebed.client
import eu.wisebed.api.v3.wsn.WSN
import eu.wisebed.api.v3.controller.Controller
import eu.wisebed.api.v3.common.Message
import eu.wisebed.api.v3.controller.RequestStatus
import java.net.URL
import com.weiglewilczek.slf4s.Logging
import scala.collection.JavaConversions._
import org.joda.time.DateTime
import scala.collection.mutable.Buffer

class SoapReservation(wsn1: WSN, val controllerEndpointUrl: URL) extends Reservation(wsn1) with Logging {

  @javax.jws.WebService(
    name = "Controller",
    endpointInterface = "eu.wisebed.api.v3.controller.Controller",
    portName = "ControllerPort",
    serviceName = "ControllerService",
    targetNamespace = "http://wisebed.eu/api/v3/controller")
  private class SoapReservationController extends Controller {

    def receive(messageList: java.util.List[Message]) {
      logger.trace("SoapReservationController.receive(" + messageList + ")")
      for (message <- messageList) {
        val nodeUrn = message.getSourceNodeUrn()
        val timestamp = new DateTime(message.getTimestamp().toGregorianCalendar())
        val buffer = message.getBinaryData()
        notifyMessage(nodeUrn, timestamp, buffer)
      }
    }

    def receiveStatus(requestStatusList: java.util.List[RequestStatus]) {
      logger.trace("SoapReservationController.receiveStatus(" + requestStatusList + ")")
      for (requestStatus <- requestStatusList) {
        progressRequestStatusReceived(requestStatus)
      }
    }

    def receiveNotification(notificationList: java.util.List[String]) {
      logger.info("SoapReservationController.receiveNotification(" + notificationList + ")")
      for (notification <- notificationList) {
        notifyNotification(notification)
      }
    }

    def experimentEnded() {
      logger.trace("SoapReservationController.experimentEnded()")
      notifyExperimentEnded()
    }
  }

  private var _endpoint: Option[javax.xml.ws.Endpoint] = None

  protected def assertConnected() = {
    _endpoint = Some(javax.xml.ws.Endpoint.publish(
      controllerEndpointUrl.toString(),
      new SoapReservationController()))
    wsn.addController(controllerEndpointUrl.toString())
    notifyExperimentStarted()
  }

  def shutdown() {
    _endpoint match {
      case Some(e) => {
        e.stop()
        _endpoint = None
      }
      case None => // nothing to do
    }
  }
}