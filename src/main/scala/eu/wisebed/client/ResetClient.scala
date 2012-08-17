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

class ResetClientConfig extends Config {
  var srkString: Option[String] = None
  var nodeUrns: Option[List[String]] = None
}

class ResetClient(args: Array[String]) extends WisebedClient[ResetClientConfig] {

  private val initialConfig = new ResetClientConfig()

  private val optionParser = new OptionParser("reset", true) {

    opt("s", "secretReservationKey", "the secret reservation key identifying the reservation (issued by the RS)", {
      srkString: String => { initialConfig.srkString = Some(srkString) }
    })

    opt("n", "nodeUrns", "a comma-separated list of node URNs that are to be reserved", {
      nodeUrnString: String => { initialConfig.nodeUrns = Some(List(nodeUrnString.split(",").map(nodeUrn => nodeUrn.trim): _*)) }
    })
  }

  init(args, initialConfig, optionParser)

  def reset(): (ListenableFuture[java.util.List[Any]], Map[String, ProgressListenableFuture[Any]]) = {

    val reservation: Reservation = connectToReservation(config.srkString.get)
    reservation.reset(config.nodeUrns.get, 10, TimeUnit.SECONDS)
  }
}

object Reset {

  def main(args: Array[String]) {

    val client = new ResetClient(args)
    val (future, futureMap): (ListenableFuture[Any], Map[String, ProgressListenableFuture[Any]]) = client.reset()

    for ((nodeUrn, future) <- futureMap) {

      future.addProgressListener(new Runnable() {
        def run() {
          println("Resetting progress for \"" + nodeUrn + "\": " + future.getProgress())
        }
      }, MoreExecutors.sameThreadExecutor())

      future.addListener(new Runnable() {
        def run() {
          try {
            future.get()
            println("Resetting complete for \"" + nodeUrn + "\"")
          } catch {
            case e: Exception => println("Exception while resetting \"" + nodeUrn + "\": " + e)
          }
        }
      }, MoreExecutors.sameThreadExecutor())
    }

    future.addListener(new Runnable() {
      def run() = {
        println("Resetting nodes completed. Shutting down...")
        client.shutdown()
        System.exit(0)
      }
    }, MoreExecutors.sameThreadExecutor())
  }
}
