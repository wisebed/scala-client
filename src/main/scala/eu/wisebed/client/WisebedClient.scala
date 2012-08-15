package eu.wisebed.client

import de.uniluebeck.itm.tr.util.Logging
import eu.wisebed.api.rs.ConfidentialReservationData
import eu.wisebed.api.snaa.AuthenticationTriple
import javax.xml.datatype.DatatypeFactory
import org.apache.log4j.{PatternLayout, ConsoleAppender, Level}
import org.joda.time.DateTime

trait WisebedClient {

  de.uniluebeck.itm.tr.util.Logging.setLoggingDefaults(
    Level.INFO,
    new ConsoleAppender(
      new PatternLayout(Logging.DEFAULT_PATTERN_LAYOUT),
      "System.err"
    )
  )

  def hello() = "hello"

  def buildAuthenticationTripleList(urnPrefix: String, username: String,
                                    password: String): java.util.List[AuthenticationTriple] = {

    val triple: AuthenticationTriple = new AuthenticationTriple
    triple.setUrnPrefix(urnPrefix)
    triple.setUsername(username)
    triple.setPassword(password)

    com.google.common.collect.Lists.newArrayList(triple)
  }

  def parseReservationData(args: Array[String], durationInMinutes: Int,
                           nodeUrnsString: String): ConfidentialReservationData = {

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
