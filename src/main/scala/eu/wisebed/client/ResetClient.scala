package eu.wisebed.client

import scopt.mutable.OptionParser
import java.util.concurrent.TimeUnit
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

  def reset(): RequestTracker = {

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
    val tracker = new ResetClient(args).reset()

    tracker.onNodeProgress((nodeUrn, progressInPercent) => {
      println("Operation progress for node %s: %d".format(nodeUrn, progressInPercent))
    })

    tracker.onNodeFailure((nodeUrn, e) => {
      println("Operation failed for node %s: %s".format(nodeUrn, e.errorMessage))
    })

    tracker.onNodeCompletion(nodeUrn => {
      println("Operation completion for node %s".format(nodeUrn))
    })

    tracker.onCompletion(() => {
      for ((nodeUrn, future) <- tracker.map) {
        println("%s => %s".format(nodeUrn, {
          try {
            future.get()
            true
          } catch {
            case e: Exception => e
          }
        }))
      }
      new ResetClient(args).shutdown()
      System.exit(0)
    })

    tracker.onFailure(e => {
      println("Operation failed: %s".format(e.errorMessage))
      System.exit(e.statusCode)
    })

    tracker.onProgress(progressInPercent => {
      println("Operation progress: %d".format(progressInPercent))
    })
  }
}
