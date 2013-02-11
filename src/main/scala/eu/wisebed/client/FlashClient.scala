package eu.wisebed.client

import eu.wisebed.api.v3.common.NodeUrn
import com.google.common.util.concurrent.MoreExecutors._
import de.uniluebeck.itm.tr.util.ProgressListenableFutureMap
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

  def flash(): ProgressListenableFutureMap[NodeUrn, Any] = {
    val imageBytes: Array[Byte] = Files.toByteArray(config.image match {
      case Some(x) => x
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
    val executor = sameThreadExecutor()
    val client = new FlashClient(args)
    val futureMap = client.flash()

    for (nodeUrn <- futureMap.keySet()) {

      val future = futureMap.get(nodeUrn)

      future.addProgressListener(new Runnable() {
        def run() {
          println("Flash progress for \"" + nodeUrn + "\": " + (future.getProgress * 100).toInt)
        }
      }, executor)

      future.addListener(new Runnable() {
        def run() {
          try {
            println("Flash complete for \"" + nodeUrn + "\"")
          } catch {
            case e: Exception => println("Exception while resetting \"" + nodeUrn + "\": " + e)
          }
        }
      }, executor)
    }

    futureMap.addListener(new Runnable() {
      def run() {
        println("Flashing nodes completed. Shutting down...")
        client.shutdown()
        System.exit(0)
      }
    }, executor)
  }
}