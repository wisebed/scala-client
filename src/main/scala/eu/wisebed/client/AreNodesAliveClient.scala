package eu.wisebed.client

import scopt.mutable.OptionParser
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import eu.wisebed.api.v3.common.NodeUrn
import scala.Int

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

  def areNodesAlive(): RequestTracker = {

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
    val tracker = new AreNodesAliveClient(args).areNodesAlive()

    tracker.onNodeProgress((nodeUrn, progressInPercent) => {
      println("Operation progress for node %s: %d".format(nodeUrn, progressInPercent))
    })

    tracker.onNodeFailure((nodeUrn, e) => {
      println("Operation failed for node %s: %s".format(nodeUrn, e.exception.getMessage))
    })

    tracker.onNodeCompletion(nodeUrn => {
      println("Operation completion for node %s".format(nodeUrn))
    })

    tracker.onProgress(progressInPercent => {
      println("Operation progress: %d".format(progressInPercent))
    })

    tracker.onCompletion(() => {
      for ((nodeUrn, future) <- tracker.map) {
        println("%s => %s".format(nodeUrn, {
          val value: Int = future.get().getValue
          value match {
            case 1 => "true"
            case _ => "false (%s)".format(future.get().getMsg)
          }
        }))
      }
      new AreNodesAliveClient(args).shutdown()
      System.exit(0)
    })

    tracker.onFailure(e => {
      println("Operation failed: %s".format(e.toString))
      System.exit(1)
    })
  }
}
