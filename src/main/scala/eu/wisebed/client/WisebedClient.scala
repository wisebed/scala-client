package eu.wisebed.client

import de.uniluebeck.itm.tr.util.Logging
import eu.wisebed.api.v3.rs.{ RS, ConfidentialReservationData }
import eu.wisebed.api.v3.snaa.{ SNAA, AuthenticationTriple }
import javax.xml.datatype.DatatypeFactory
import org.apache.log4j.{ PatternLayout, ConsoleAppender, Level }
import org.joda.time.DateTime
import java.io.File
import eu.wisebed.api.v3.WisebedServiceHelper
import eu.wisebed.api.v3.sm.SessionManagement
import javax.xml.ws.Holder
import eu.wisebed.api.v3.common.{ SecretReservationKey, SecretAuthenticationKey, KeyValuePair }
import scala.collection
import collection.mutable
import scala.collection.JavaConversions._
import scopt.immutable.OptionParser

abstract class WisebedClient[ConfigClass <: Config](val args: Array[String], val initialConfig: ConfigClass) {

  de.uniluebeck.itm.tr.util.Logging.setLoggingDefaults(
    Level.INFO,
    new ConsoleAppender(
      new PatternLayout(Logging.DEFAULT_PATTERN_LAYOUT),
      "System.err"))

  protected val configParser = new OptionParser[ConfigClass]("reserve", true) {
    def options = Seq(
      opt("c", "config", "the testbed configuration file") {
        (configFileName: String, config: ConfigClass) =>
          {
            config.parseFromConfigFile(new File(configFileName))
            config
          }
      })
  }

  private var _snaa: Option[SNAA] = None

  private var _rs: Option[RS] = None

  private var _sm: Option[SessionManagement] = None

  private var _config: Option[ConfigClass] = None

  private var _testbedOptions: Option[collection.mutable.HashMap[String, String]] = None

  def snaa: SNAA = {
    _snaa.getOrElse(throwIllegalStateException)
  }

  def rs: RS = {

    _rs.getOrElse({

      val rsEndpointUrl = new Holder[String]()
      val snaaEndpointUrl = new Holder[String]()
      val options = new Holder[java.util.List[KeyValuePair]]()

      sm.getConfiguration(rsEndpointUrl, snaaEndpointUrl, options)

      _rs = Some(WisebedServiceHelper.getRSService(rsEndpointUrl.value))
      _snaa = Some(WisebedServiceHelper.getSNAAService(snaaEndpointUrl.value))

      val map = new collection.mutable.HashMap[String, String]()
      options.value.foreach(kv => map += ((kv.getKey, kv.getValue)))
      _testbedOptions = Some(map)

      _rs.get
    })
  }

  def sm: SessionManagement = {
    _sm.getOrElse(throwIllegalStateException)
  }

  def config: ConfigClass = _config match {
    case Some(config) => config
    case None => throwIllegalStateException
  }

  def testbedOptions: mutable.HashMap[String, String] = _testbedOptions match {
    case Some(options) => options
    case None => throwIllegalStateException
  }

  private def throwIllegalStateException =
    throw new IllegalStateException("init() must be called before accessing config")

  def init() {

    _config = configParser.parse(args, initialConfig) match {
      case Some(config) => Some(config)
      case None => throw new IllegalArgumentException("Could not parse config file!")
    }
  }

  def authenticate(): List[SecretAuthenticationKey] = {
    List(snaa.authenticate(buildAuthenticationTripleList()): _*)
  }

  def makeReservation(secretAuthenticationKeys: List[SecretAuthenticationKey],
    durationInMinutes: Int,
    nodeUrns: Array[String]): List[SecretReservationKey] = {

    val reservationData = buildConfidentialReservationData(
      durationInMinutes,
      nodeUrns)

    List(rs.makeReservation(secretAuthenticationKeys, reservationData): _*)
  }

  private def buildAuthenticationTripleList(): List[AuthenticationTriple] = {

    val map: List[AuthenticationTriple] = config.credentials.map(
      credential => {
        val triple = new AuthenticationTriple()
        triple.setUrnPrefix(credential.urnPrefix)
        triple.setUsername(credential.username)
        triple.setPassword(credential.password)
        triple
      })
    List[AuthenticationTriple]()
  }

  private def buildConfidentialReservationData(durationInMinutes: Int, nodeUrns: Array[String]): ConfidentialReservationData = {

    val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()
    val data = new ConfidentialReservationData()
    val from = new DateTime()
    val to = from.plusMinutes(durationInMinutes)

    data.setFrom(datatypeFactory.newXMLGregorianCalendar(from.toGregorianCalendar))
    data.setTo(datatypeFactory.newXMLGregorianCalendar(to.toGregorianCalendar))
    nodeUrns.foreach(nodeUrn => data.getNodeUrns().add(nodeUrn))

    data
  }
}
