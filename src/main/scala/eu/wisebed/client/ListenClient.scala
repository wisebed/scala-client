package eu.wisebed.client

import scopt.mutable.OptionParser
import de.uniluebeck.itm.tr.util.StringUtils
import org.joda.time.DateTime

object Listen extends App {

  val client = new ListenClient(args)

  client.startListening()
  while (true) {
    System.in.read()
  }
  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run() {
      client.shutdown()
    }
  })
}

class ListenClientConfig extends Config {

  var srkString: Option[String] = None

  val setSrkString: (String) => Unit = {
    param => srkString = Some(param)
  }

  var nodeUrns: Option[List[String]] = None

}

class ListenClient(args: Array[String]) extends WisebedClient[ListenClientConfig] {

  private val initialConfig = new ListenClientConfig()

  private val optionParser = new OptionParser("listen", true) {
    opt(
      "secretReservationKey",
      "the secret reservation key identifying the reservation (issued by the RS)",
      initialConfig.setSrkString
    )
  }

  init(args, initialConfig, optionParser)

  def startListening() {

    connectToReservation(config.srkString.get)

    reservation.onMessage((nodeUrn, timestamp, buffer) => {
      print(timestamp)
      print(" | ")
      print(nodeUrn)
      print(" | ")
      print("UPSTREAM_MESSAGE_EVENT")
      print(" | ")
      print(StringUtils.replaceNonPrintableAsciiCharacters(buffer))
      println()
    })

    reservation.onNotification(notification => {
      print(notification.timestamp)
      print(" | ")
      print(notification.nodeUrn match {
        case Some(urn) => urn
        case None => ""
      })
      print(" | ")
      print("NOTIFICATION_EVENT")
      print(" | ")
      print(notification.msg)
    })

    reservation.onNodesAttached(nodeUrns => {
      nodeUrns.foreach(nodeUrn => {
        print(new DateTime())
        print(" | ")
        print(nodeUrn)
        print(" | ")
        print("NODE_ATTACHED_EVENT")
        println(" |")
      })
    })

    reservation.onNodesDetached(nodeUrns => {
      nodeUrns.foreach(nodeUrn => {
        print(new DateTime())
        print(" | ")
        print(nodeUrn)
        print(" | ")
        print("NODE_DETACHED_EVENT")
        println(" |")
      })
    })

    reservation.onExperimentEnded(() => {
      print(new DateTime())
      print(" | ")
      print("")
      print(" | ")
      print("RESERVATION_ENDED_EVENT")
      println(" |")
      System.exit(0)
    })

    reservation.onExperimentStarted(() => {
      print(new DateTime())
      print(" | ")
      print("")
      print(" | ")
      print("RESERVATION_STARTED_EVENT")
      println(" |")
    })
  }
}
