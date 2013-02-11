package eu.wisebed.client

import scopt.mutable.OptionParser
import de.uniluebeck.itm.tr.util.ProgressListenableFutureMap
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor
import scala.collection.JavaConversions._
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

  def reset(): ProgressListenableFutureMap[NodeUrn, Any] = {

    val reservation: Reservation = connectToReservation(config.srkString.get)
    config.nodeUrns match {
      case None =>
        reservation.reset(reservation.reservedNodeUrns(), 10, TimeUnit.SECONDS)
      case Some(x) =>
        reservation.reset(x, 10, TimeUnit.SECONDS)
    }
  }
}

object Reset extends App {
  {
    val executor = sameThreadExecutor()
    val client = new ResetClient(args)
    val futureMap = client.reset()

    for (nodeUrn <- futureMap.keySet) {

      val future = futureMap.get(nodeUrn)

      future.addProgressListener(new Runnable() {
        def run() {
          println("Resetting progress for \"" + nodeUrn + "\": " + future.getProgress)
        }
      }, executor)

      future.addListener(new Runnable() {
        def run() {
          try {
            future.get()
            println("Resetting complete for \"" + nodeUrn + "\"")
          } catch {
            case e: Exception => println("Exception while resetting \"" + nodeUrn + "\": " + e)
          }
        }
      }, executor)
    }

    futureMap.addListener(new Runnable() {
      def run() {
        println("Resetting nodes completed. Shutting down...")
        client.shutdown()
        System.exit(0)
      }
    }, executor)
  }
}
