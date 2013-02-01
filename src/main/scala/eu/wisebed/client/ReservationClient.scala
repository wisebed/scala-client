package eu.wisebed.client

import eu.wisebed.api.v3.common.SecretReservationKey
import scopt.mutable.OptionParser
import org.apache.log4j.Logger
import org.apache.log4j.Level
import eu.wisebed.api.v3.common.NodeUrn
import eu.wisebed.wiseml.WiseMLHelper

import scala.collection.JavaConversions._
import eu.wisebed.api.v3.rs.ReservationConflictFault_Exception
import org.joda.time.Duration

object Reserve extends App {

  try {

    val srks = new ReservationClient(args).reserve()
    val pairs = srks.map(srk => srk.getUrnPrefix + "," + srk.getSecretReservationKey)

    println(pairs.reduceLeft(_ + "," + _))

    System.exit(0)

  } catch {
    case e: ReservationConflictFault_Exception => {
      System.err.println(e.getFaultInfo.getMessage)
      System.exit(1)
    }
  }
}

class ReservationClientConfig extends Config {

  var duration: Duration = Duration.ZERO

  var offset: Duration = Duration.ZERO

  var nodeUrns: List[NodeUrn] = null

  val setNodeUrns: (String) => Unit = {
    nodeUrnsString => this.nodeUrns = List(nodeUrnsString.split(",").map(nodeUrn => new NodeUrn(nodeUrn.trim)): _*)
  }

  val setDuration: (String) => Unit = {
    duration => this.duration = Duration.standardMinutes(augmentString(duration).toInt)
  }

  val setOffset: (String) => Unit = {
    offset => this.offset = Duration.standardMinutes(augmentString(offset).toInt)
  }
}

class ReservationClient(args: Array[String]) extends WisebedClient[ReservationClientConfig] {

  Logger.getRootLogger.setLevel(Level.TRACE)

  private val initialConfig = new ReservationClientConfig()

  private val optionParser = new OptionParser("reserve", true) {

    opt(
      "duration",
      "the duration of the reservation to be made (in minutes)",
      initialConfig.setDuration
    )

    opt(
      "offset",
      "the offset (from now) of the reservation to be made (in minutes)",
      initialConfig.setOffset
    )

    opt(
      "nodeUrns",
      "a comma-separated list of node URNs that are to be reserved",
      initialConfig.setNodeUrns
    )
  }

  init(args, initialConfig, optionParser)

  def reserve(): List[SecretReservationKey] = {
    val nodeUrns: List[NodeUrn] = config.nodeUrns match {
      case x: List[NodeUrn] => x
      case null => {
        logger debug "Fetching all available node URNs from session management service (%s)".format(sm)
        val network: String = sm.getNetwork
        logger debug "Fetched network description: %s".format(network)
        val nodes = List(WiseMLHelper.getNodeUrns(network).map(urn => new NodeUrn(urn)): _*)
        logger debug "Fetched nodes %s".format(nodes)
        nodes
      }
    }
    makeReservation(authenticate(), config.offset, config.duration, nodeUrns)
  }
}
