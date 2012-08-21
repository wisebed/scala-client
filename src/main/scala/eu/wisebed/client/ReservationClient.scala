package eu.wisebed.client

import com.weiglewilczek.slf4s.Logging
import eu.wisebed.api.v3.common.SecretReservationKey
import java.io.File
import scopt.mutable.OptionParser
import org.apache.log4j.Logger
import org.apache.log4j.Level

class ReservationClientConfig extends Config {
  var durationInMinutes: Int = 0
  var nodeUrns: List[String] = null
}

class ReservationClient(args: Array[String]) extends WisebedClient[ReservationClientConfig] {

  Logger.getRootLogger().setLevel(Level.TRACE)

  private val initialConfig = new ReservationClientConfig()

  private val optionParser = new OptionParser("reserve", true) {

    intOpt("d", "durationInMinutes", "the duration of the reservation to be made in minutes", {
      durationInMinutes: Int => { initialConfig.durationInMinutes = durationInMinutes }
    })

    opt("n", "nodeUrns", "a comma-separated list of node URNs that are to be reserved", {
      nodeUrnString: String => {
        initialConfig.nodeUrns = List(nodeUrnString.split(",").map(nodeUrn => nodeUrn.trim):_*)
      }
    })
  }

  init(args, initialConfig, optionParser)

  def reserve(): List[SecretReservationKey] = {
    val secretAuthenticationKey = authenticate()
    makeReservation(secretAuthenticationKey, config.durationInMinutes, config.nodeUrns)
  }
}
