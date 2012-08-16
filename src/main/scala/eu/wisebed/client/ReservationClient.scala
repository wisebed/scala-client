package eu.wisebed.client

import com.weiglewilczek.slf4s.Logging
import eu.wisebed.api.v3.common.SecretReservationKey
import java.io.File

class ReservationClientConfig extends Config {

  var durationInMinutes: Int = 0

  var nodeUrns: Array[String] = null
}

class ReservationClient(args: Array[String])
  extends WisebedClient[ReservationClientConfig](args, new ReservationClientConfig())
  with Logging {

  configParser.intOpt("d", "durationInMinutes", "the duration of the reservation to be made in minutes") {
    (durationInMinutes: Int, config: ReservationClientConfig) => {
      config.durationInMinutes = durationInMinutes
      config
    }
  }

  configParser.opt("n", "nodeUrns", "a comma-separated list of node URNs that are to be reserved") {
    (nodeUrnString: String, config: ReservationClientConfig) => {
      config.nodeUrns = nodeUrnString.split(",").map(nodeUrn => nodeUrn.trim)
      config
    }
  }

  init()

  def reserve(): List[SecretReservationKey] = {
    makeReservation(authenticate(), config.durationInMinutes, config.nodeUrns)
  }
}

object Reserve {

  def main(args: Array[String]) {
    println(new ReservationClient(args).reserve())
  }
}
