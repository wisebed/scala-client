package eu.wisebed.client

import scopt.mutable.OptionParser
import de.uniluebeck.itm.tr.util.ProgressListenableFutureMap
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor
import scala.collection.JavaConversions._
import eu.wisebed.api.v3.common.NodeUrn

class AreNodesAliveClientConfig extends Config {

  var srkString: Option[String] = None

  var nodeUrns: Option[List[NodeUrn]] = None
}

class AreNodesAliveClient(args: Array[String]) extends WisebedClient[AreNodesAliveClientConfig] {

  private val initialConfig = new AreNodesAliveClientConfig()

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

  def areNodesAlive(): ProgressListenableFutureMap[NodeUrn, Any] = {

    val reservation: Reservation = connectToReservation(config.srkString.get)
    config.nodeUrns match {
      case None =>
        reservation.areNodesAlive(reservation.reservedNodeUrns(), 10, TimeUnit.SECONDS)
      case Some(x) =>
        reservation.areNodesAlive(x, 10, TimeUnit.SECONDS)
    }
  }
}

object AreNodesAlive extends App {
  {
    val executor = sameThreadExecutor()
    val client = new AreNodesAliveClient(args)
    val futureMap = client.areNodesAlive()

    for (nodeUrn <- futureMap.keySet) {

      val future = futureMap.get(nodeUrn)

      future.addProgressListener(new Runnable() {
        def run() {
          println("Liveness check progress for \"" + nodeUrn + "\": " + future.getProgress)
        }
      }, executor)

      future.addListener(new Runnable() {
        def run() {
          try {
            future.get()
            println("Liveness check complete for \"" + nodeUrn + "\"")
          } catch {
            case e: Exception => println("Exception while resetting \"" + nodeUrn + "\": " + e)
          }
        }
      }, executor)
    }

    futureMap.addListener(new Runnable() {
      def run() {
        println("Liveness check completed. Shutting down...")
        client.shutdown()
        System.exit(0)
      }
    }, executor)
  }
}
