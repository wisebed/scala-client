package eu.wisebed.client

import eu.wisebed.api.v3.common.NodeUrn
import scala.collection.JavaConversions._
import scopt.mutable.OptionParser
import java.util.concurrent.TimeUnit
import java.io.File
import com.google.common.io.Files

class FlashClientConfig extends Config {

  var srkString: Option[String] = None

  var nodeUrns: Option[List[NodeUrn]] = None

  var image: Option[File] = None

  var timeout: Long = TimeUnit.MINUTES.toSeconds(2)

  var timeUnit: TimeUnit = TimeUnit.SECONDS
}

class FlashClient(args: Array[String]) extends WisebedClient[FlashClientConfig] {

  private val initialConfig = new FlashClientConfig()

  private val optionParser = new OptionParser("flash", true) {

    opt("s", "secretReservationKey", "the secret reservation key identifying the reservation (issued by the RS)", {
      srkString: String => {
        initialConfig.srkString = Some(srkString)
      }
    })

    opt("n", "nodeUrns", "a comma-separated list of node URNs that are to be flashed", {
      nodeUrnString: String => {
        initialConfig.nodeUrns = Some(List(nodeUrnString.split(",").map(nodeUrn => new NodeUrn(nodeUrn.trim)): _*))
      }
    })

    opt("i", "image", "an image file to be flashed onto the nodes", {
      image: String => {
        val file = new File(image)
        initialConfig.image = if (file.exists && file.isFile && file.canRead) {
          Some(file)
        } else {
          throw new IllegalArgumentException("The image file \"" + file.getAbsolutePath + "\" does not exist, "
            + "is not a file or cannot be read")
        }
      }
    })

    intOpt("t", "timeout",
    "timeout (in seconds) after which the flash operation shall be regarded as failed (default: 2 minutes)", {
      timeout: Int => initialConfig.timeout = timeout
    })
  }

  init(args, initialConfig, optionParser)

  def flash(): RequestTracker = {
    val imageBytes: Array[Byte] = Files.toByteArray(config.image match {
      case Some(x) => x
      case _ => throw new RuntimeException("This should not happen")
    })
    val reservation: Reservation = connectToReservation(config.srkString.get)
    config.nodeUrns match {
      case None =>
        reservation.flash(reservation.reservedNodeUrns(), imageBytes, config.timeout, config.timeUnit)
      case Some(x) =>
        reservation.flash(x, imageBytes, config.timeout, config.timeUnit)
    }
  }
}

object Flash extends App {
  {
    val tracker = new FlashClient(args).flash()

    tracker.onNodeProgress((nodeUrn, progressInPercent) => {
      println("Operation progress for node %s: %d".format(nodeUrn, progressInPercent))
    })

    tracker.onNodeFailure((nodeUrn, e) => {
      println("Operation failed for node %s: %s".format(nodeUrn, e.errorMessage))
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
          try {
            future.get()
            true
          } catch {
            case e: Exception => e
          }
        }))
      }
      new FlashClient(args).shutdown()
      System.exit(0)
    })

    tracker.onFailure(e => {
      println("Operation failed: %s".format(e.errorMessage))
      System.exit(e.statusCode)
    })
  }
}