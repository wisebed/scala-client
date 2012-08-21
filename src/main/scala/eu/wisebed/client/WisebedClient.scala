package eu.wisebed.client

import eu.wisebed.api.v3.rs.RS
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
import eu.wisebed.api.v3.wsn.WSN
import java.net.URL

abstract class WisebedClient[ConfigClass <: Config] extends Logging {

  de.uniluebeck.itm.tr.util.Logging.setLoggingDefaults(
    Level.INFO,
    new ConsoleAppender(
      new PatternLayout(de.uniluebeck.itm.tr.util.Logging.DEFAULT_PATTERN_LAYOUT),
      "System.err"))

  private var _snaa: Option[SNAA] = None

  private var _rs: Option[RS] = None

  private var _sm: Option[SessionManagement] = None

  private var _config: Option[ConfigClass] = None

  private var _testbedOptions: Option[collection.mutable.HashMap[String, String]] = None

  private var _wsn: Option[WSN] = None
  
  private var _reservation: Option[SoapReservation] = None

  def snaa: SNAA = {
    _snaa.getOrElse({
      config.snaaEndpointUrl match {
        case Some(url) => WisebedServiceHelper.getSNAAService(url.toString)
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
        case Some(url) => WisebedServiceHelper.getRSService(url.toString)
        case None => {
          loadConfigurationFromTestbed()
          _rs.get
        }
      }
    })
  }

  def sm: SessionManagement = {
    _sm.getOrElse({
      config.smEndpointUrl match {
        case Some(url) => {
          _sm = Some(WisebedServiceHelper.getSessionManagementService(url.toString))
        }
        case None => throwIllegalStateException
      }
    })
  }

  def wsn: WSN = {
    _wsn.getOrElse({
      throw new IllegalStateException("You must call connectToReservation() before accessing the wsn service!")
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

  def init(args: Array[String], initialConfig: ConfigClass, configParser: OptionParser) {

    configParser.opt("c", "config", "<configfile>", "the testbed configuration file", {
      configFileName: String => { initialConfig.parseFromConfigFile(new File(configFileName)) }
    })

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

    val datatypeFactory = DatatypeFactory.newInstance()
    val from = datatypeFactory.newXMLGregorianCalendar(DateTime.now.toGregorianCalendar)
    val to = datatypeFactory.newXMLGregorianCalendar(DateTime.now.plusMinutes(durationInMinutes).toGregorianCalendar)
    val nodeUrnList = List(nodeUrns:_*)

    List(rs.makeReservation(secretAuthenticationKeys, nodeUrnList, from, to): _*)
  }

  def reservation: Reservation = {
    _reservation.getOrElse({
      throw new IllegalStateException("You must connect to a reservation before accessing WisebedClient.reservation")
    })
  }

  def connectToReservation(secretReservationKeyString: String): Reservation = {
    // TODO handle connections to multiple reservations
    
    val srks = parseSecretReservationKeys(secretReservationKeyString)
    
    val wsnUrl = sm.getInstance(srks)
    _wsn = Some(WisebedServiceHelper.getWSNService(wsnUrl))
    
    val reservation = new SoapReservation(wsn, new URL("http://opium.local:1234/controller"))
    _reservation = Some(reservation)
    reservation
  }

  def shutdown() {
    _reservation match {
      case Some(reservation) => {
        reservation.shutdown()
        _reservation = None
      }
      case None => // nothing to do
    }
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
      case Some(_) => _testbedOptions
      case None => {
        val map = new collection.mutable.HashMap[String, String]()
        options.value.foreach(kv => map += ((kv.getKey, kv.getValue)))
        _testbedOptions = Some(map)
      }
    }
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

  /**
   * urnPrefix1,srk1;urnPrefix2,srk2;...;urnPrefixn,srkn -> [(urnPrefix1,srk1),(urnPrefix2,srk2), ... , (urnPrefixn, srkn)]
   */
  private def parseSecretReservationKeys(srkString: String): List[SecretReservationKey] = {
    List(augmentString(srkString).split(';').map({
      urnPrefixSrkPair: String =>
        {
          val split = urnPrefixSrkPair.split(',')
          val srk = new SecretReservationKey()
          srk.setUrnPrefix(split(0).trim())
          srk.setSecretReservationKey(split(1).trim())
          srk
        }
    }): _*)
  }
}
