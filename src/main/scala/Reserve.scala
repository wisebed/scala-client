import eu.wisebed.api.rs.ConfidentialReservationData
import eu.wisebed.api.snaa.AuthenticationTriple
import eu.wisebed.api.WisebedServiceHelper
import java.net.URL
import javax.xml.datatype.DatatypeFactory
import org.joda.time.DateTime

object Reserve {

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

  def buildAuthenticationTripleList(urnPrefix: String, username: String,
                                    password: String): java.util.List[AuthenticationTriple] = {

    val triple: AuthenticationTriple = new AuthenticationTriple
    triple.setUrnPrefix(urnPrefix)
    triple.setUsername(username)
    triple.setPassword(password)

    com.google.common.collect.Lists.newArrayList(triple)
  }

  def parseReservationData(args: Array[String], durationInMinutes: Int, nodeUrnsString: String): ConfidentialReservationData = {

    val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()
    val data = new ConfidentialReservationData()
    val from = new DateTime()
    val to = from.plusMinutes(durationInMinutes)

    data.setFrom(datatypeFactory.newXMLGregorianCalendar(from.toGregorianCalendar))
    data.setTo(datatypeFactory.newXMLGregorianCalendar(to.toGregorianCalendar))

    val nodeUrns = augmentString(nodeUrnsString).split(',')

    nodeUrns.foreach(nodeUrn => data.getNodeUrns.add(nodeUrn))

    data
  }
}
