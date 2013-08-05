package eu.wisebed.client

import scopt.mutable.OptionParser
import de.uniluebeck.itm.tr.util.StringUtils.fromStringToByteArray
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import eu.wisebed.api.v3.common.NodeUrn

object Format extends Enumeration {

  type Format = Value

  val ASCII, BYTES = Value
}

import Format._

class SendClientConfig extends Config {

  var payloadString: String = ""

  var srkString: Option[String] = None

  var nodeUrns: Option[List[NodeUrn]] = None

  var format: Format= ASCII
}

class SendClient(args: Array[String]) extends WisebedClient[SendClientConfig] {

  private val initialConfig = new SendClientConfig()

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

    opt("f", "format", "BYTES | ASCII (default: BYTES)", {
      formatString: String => initialConfig.format = Format.withName(formatString)
    })

    opt("p", "payload", "payload (encoded) as string (cf. format option)", {
      payloadString: String => {
        payloadString match {
          case "" => throw new IllegalArgumentException("payload is missing")
          case _ => initialConfig.payloadString = payloadString
        }
      }
    })
  }

  init(args, initialConfig, optionParser)

  def send(): RequestTracker = {

    val payload: Array[Byte] = {
      config.format match {
        case BYTES => fromStringToByteArray(config.payloadString)
        case ASCII => config.payloadString.getBytes
      }
    }

    val reservation: Reservation = connectToReservation(config.srkString.get)
    config.nodeUrns match {
      case None =>
        reservation.send(reservation.reservedNodeUrns(), payload, 10, TimeUnit.SECONDS)
      case Some(x) =>
        reservation.send(x, payload, 10, TimeUnit.SECONDS)
    }
  }
}

object Send extends App {
  {
    val tracker = new SendClient(args).send()

    tracker.onNodeProgress((nodeUrn, progressInPercent) => {
      println("Operation progress for node %s: %d".format(nodeUrn, progressInPercent))
    })

    tracker.onNodeFailure((nodeUrn, e) => {
      println("Operation failed for node %s: %s".format(nodeUrn, e.exception.getMessage))
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
      new SendClient(args).shutdown()
      System.exit(0)
    })

    tracker.onFailure(e => {
      println("Operation failed: %s".format(e.toString))
      System.exit(1)
    })

    tracker.onProgress(progressInPercent => {
      println("Operation progress: %d".format(progressInPercent))
    })
  }
}
