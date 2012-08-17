package eu.wisebed.client

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
import scopt.mutable.OptionParser
import com.weiglewilczek.slf4s.Logging

abstract class WisebedClient[ConfigClass <: Config](val args: Array[String], val initialConfig: ConfigClass) extends Logging {

  de.uniluebeck.itm.tr.util.Logging.setLoggingDefaults(
    Level.INFO,
    new ConsoleAppender(
      new PatternLayout(de.uniluebeck.itm.tr.util.Logging.DEFAULT_PATTERN_LAYOUT),
      "System.err"))

  protected val configParser = new OptionParser("reserve", true) {
    opt("c", "config", "<configfile>", "the testbed configuration file", {
      configFileName: String => { initialConfig.parseFromConfigFile(new File(configFileName)) }
    })
  }

  private var _snaa: Option[SNAA] = None

  private var _rs: Option[RS] = None

  private var _sm: Option[SessionManagement] = None

  private var _config: Option[ConfigClass] = None

  private var _testbedOptions: Option[collection.mutable.HashMap[String, String]] = None

  def snaa: SNAA = {
    _snaa.getOrElse({
      config.snaaEndpointUrl match {
        case Some(url) => WisebedServiceHelper.getSNAAService(url.toString())
        case None => {
          loadConfigurationFromTestbed()
          _snaa.get
        }
      }
    })
  }

  def rs: RS = {
    _rs.getOrElse({
      config.rsEndpointUrl match {
        case Some(url) => WisebedServiceHelper.getRSService(url.toString())
        case None => {
          loadConfigurationFromTestbed()
          _rs.get
        }
      }
    })
  }

  private def loadConfigurationFromTestbed() = {

    logger.debug("Loading testbed configuration from session management endpoint " + sm + "...")

    val rsEndpointUrl = new Holder[String]()
    val snaaEndpointUrl = new Holder[String]()
    val options = new Holder[java.util.List[KeyValuePair]]()

    sm.getConfiguration(rsEndpointUrl, snaaEndpointUrl, options)

    _rs = _rs match {
      case Some(endpoint) => _rs
      case None => Some(WisebedServiceHelper.getRSService(rsEndpointUrl.value))
    }

    _snaa = _snaa match {
      case Some(endpoint) => _snaa
      case None => Some(WisebedServiceHelper.getSNAAService(snaaEndpointUrl.value))
    }

    _testbedOptions match {
      case Some(options) => _testbedOptions
      case None => {
        val map = new collection.mutable.HashMap[String, String]()
        options.value.foreach(kv => map += ((kv.getKey, kv.getValue)))
        Some(map)
      }
    }
  }

  def sm: SessionManagement = {
    _sm.getOrElse({
      config.smEndpointUrl match {
        case Some(url) => { WisebedServiceHelper.getSessionManagementService(url.toString()) }
        case None => throwIllegalStateException
      }
    })
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

    if (configParser.parse(args)) {
      _config = Some(initialConfig)
    } else {
      throw new IllegalArgumentException("Could not parse config file!")
    }
  }

  def authenticate(): List[SecretAuthenticationKey] = {
    val authenticationTripeList = buildAuthenticationTripleList()
    List((snaa.authenticate(authenticationTripeList)): _*)
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
    config.credentials.map(
      credential => {
        val triple = new AuthenticationTriple()
        triple.setUrnPrefix(credential.urnPrefix)
        triple.setUsername(credential.username)
        triple.setPassword(credential.password)
        triple
      })
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
