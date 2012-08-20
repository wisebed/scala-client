package eu.wisebed.client

import com.weiglewilczek.slf4s.Logging
import eu.wisebed.api.v3.common.SecretReservationKey
import java.io.File
import scopt.mutable.OptionParser
import de.uniluebeck.itm.tr.util.ProgressListenableFuture
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.MoreExecutors
import scala.collection.JavaConversions._
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

  def startListening() = {

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
  }
}

object Listen {

  def main(args: Array[String]) {
    val client = new ListenClient(args)
    client.startListening()
    while (true) {
      System.in.read()
    }
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() { def run() = client.shutdown() }))
  }
}
