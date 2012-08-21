package eu.wisebed.client

import scopt.mutable.OptionParser
import de.uniluebeck.itm.tr.util.StringUtils
import org.joda.time.DateTime

class ListenClientConfig extends Config {
  var srkString: Option[String] = None
  var nodeUrns: Option[List[String]] = None
}

class ListenClient(args: Array[String]) extends WisebedClient[ListenClientConfig] {

  private val initialConfig = new ListenClientConfig()

  private val optionParser = new OptionParser("listen", true) {
    opt("s", "secretReservationKey", "the secret reservation key identifying the reservation (issued by the RS)", {
      srkString: String => { initialConfig.srkString = Some(srkString) }
    })
  }

  init(args, initialConfig, optionParser)

  def startListening() {

    connectToReservation(config.srkString.get)

    reservation.onMessage((nodeUrn, timestamp, buffer) => {
      print(timestamp)
      print(" | ")
      print(nodeUrn)
      print(" | ")
      print(StringUtils.replaceNonPrintableAsciiCharacters(buffer))
      println()
    })

    reservation.onNotification(notification => {
      print(new DateTime())
      print(" | ")
      print("Notification")
      print(" | ")
      print(notification)
    })

    reservation.onNodesAttached(nodeUrns => {
      nodeUrns.foreach(nodeUrn => println("Node \"" + nodeUrn + "\" was attached"))
    })

    reservation.onNodesDetached(nodeUrns => {
      nodeUrns.foreach(nodeUrn => println("Node \"" + nodeUrn + "\" was detached"))
    })
  }
}

object Listen {

  def main(args: Array[String]) {
    val client = new ListenClient(args)
    client.startListening()
    while (true) {
      System.in.read()
    }
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() { client.shutdown() }
    })
  }
}
