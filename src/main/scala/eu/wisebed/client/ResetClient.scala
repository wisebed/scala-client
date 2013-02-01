package eu.wisebed.client

import scopt.mutable.OptionParser
import de.uniluebeck.itm.tr.util.ProgressListenableFuture
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ListenableFuture
import eu.wisebed.api.v3.common.NodeUrn

class ResetClientConfig extends Config {

  var srkString: Option[String] = None

  var nodeUrns: Option[List[NodeUrn]] = None
}

class ResetClient(args: Array[String]) extends WisebedClient[ResetClientConfig] {

  private val initialConfig = new ResetClientConfig()

  private val optionParser = new OptionParser("reset", true) {

    opt("s", "secretReservationKey", "the secret reservation key identifying the reservation (issued by the RS)", {
      srkString: String => {
        initialConfig.srkString = Some(srkString)
      }
    })

    opt("n", "nodeUrns", "a comma-separated list of node URNs that are to be reserved", {
      nodeUrnString: String => {
        initialConfig.nodeUrns = Some(List(nodeUrnString.split(",").map(nodeUrn => new NodeUrn(nodeUrn.trim)): _*))
      }
    })
  }

  init(args, initialConfig, optionParser)

  def reset(): (ListenableFuture[java.util.List[Any]], Map[NodeUrn, ProgressListenableFuture[Any]]) = {

    val reservation: Reservation = connectToReservation(config.srkString.get)
    reservation.reset(config.nodeUrns.get, 10, TimeUnit.SECONDS)
  }
}

object Reset {

  def main(args: Array[String]) {

    val client = new ResetClient(args)
    val (future, futureMap): (ListenableFuture[java.util.List[Any]], Map[NodeUrn, ProgressListenableFuture[Any]]) = client
      .reset()

    for ((nodeUrn, future) <- futureMap) {

      future.addProgressListener(new Runnable() {
        def run() {
          println("Resetting progress for \"" + nodeUrn + "\": " + future.getProgress)
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
      def run() {
        println("Resetting nodes completed. Shutting down...")
        client.shutdown()
        System.exit(0)
      }
    }, MoreExecutors.sameThreadExecutor())
  }
}
