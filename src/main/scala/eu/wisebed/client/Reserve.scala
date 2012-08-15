package eu.wisebed.client

import com.weiglewilczek.slf4s.Logging
import eu.wisebed.api.v3.WisebedServiceHelper
import eu.wisebed.api.v3.WisebedServiceHelper
import java.net.URL

object Reserve extends WisebedClient with Logging {

  def main(args: Array[String]) {

    val snaaEndpointUrl = new URL(args(0))
    val rsEndpointUrl = new URL(args(1))
    val urnPrefix = args(2)
    val username = args(3)
    val password = args(4)
    val durationInMinutes = augmentString(args(5)).toInt
    val nodeUrns = args(6)

    val snaa = WisebedServiceHelper.getSNAAService(snaaEndpointUrl.toString)
    val rs = WisebedServiceHelper.getRSService(rsEndpointUrl.toString)

    val authenticationTriples = buildAuthenticationTripleList(urnPrefix, username, password)
    val secretAuthenticationKeys = snaa.authenticate(authenticationTriples)
    val reservationData = parseReservationData(args, durationInMinutes, nodeUrns)
    val reservation = rs.makeReservation(secretAuthenticationKeys, reservationData)

    println(reservation)
  }
}
