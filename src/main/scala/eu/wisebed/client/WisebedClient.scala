package eu.wisebed.client

import eu.wisebed.api.v3.rs.RS
import eu.wisebed.api.v3.snaa.{SNAA, AuthenticationTriple}
import org.apache.log4j.{PatternLayout, ConsoleAppender, Level}
import org.joda.time.{Duration, DateTime}
import eu.wisebed.api.v3.WisebedServiceHelper
import eu.wisebed.api.v3.sm.SessionManagement
import javax.xml.ws.Holder
import eu.wisebed.api.v3.common.{SecretReservationKey, SecretAuthenticationKey, KeyValuePair}
import scala.collection
import collection.mutable
import scala.collection.JavaConversions._
import scopt.mutable.OptionParser
import eu.wisebed.api.v3.wsn.WSN
import eu.wisebed.api.v3.common.NodeUrnPrefix
import eu.wisebed.api.v3.common.NodeUrn
import util.Logging

abstract class WisebedClient[ConfigClass <: Config] extends Logging {

  protected implicit def stringToNodeUrn(s: String): NodeUrn = new NodeUrn(s)

  protected implicit def stringToNodeUrnPrefix(s: String): NodeUrnPrefix = new NodeUrnPrefix(s)

  de.uniluebeck.itm.tr.util.Logging.setLoggingDefaults(
    Level.INFO,
    new ConsoleAppender(new PatternLayout(de.uniluebeck.itm.tr.util.Logging.DEFAULT_PATTERN_LAYOUT))
  )

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
          _sm.get
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

    configParser.opt(
      "config",
      "the testbed configuration file",
      initialConfig.parseFromConfigFile
    )

    configParser.opt(
      "controllerEndpointUrl",
      "The endpoint URL of the local controller",
      initialConfig.setControllerEndpointUrl
    )

    configParser.parse(args) match {
      case true => _config = Some(initialConfig)
      case false => throw new IllegalArgumentException("Invalid command line parameters!")
    }
  }

  def authenticate(): List[SecretAuthenticationKey] = {
    val authenticationTripeList = buildAuthenticationTripleList()
    List((snaa.authenticate(authenticationTripeList)): _*)
  }

  def makeReservation(secretAuthenticationKeys: List[SecretAuthenticationKey],
                      offset: Duration,
                      duration: Duration,
                      nodeUrns: List[NodeUrn]): List[SecretReservationKey] = {

    val nodeUrnList = List(nodeUrns: _*)
    val from = DateTime.now.plus(offset)
    val to = from.plus(duration)

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

    val controllerEndpointUrl = config.controllerEndpointUrl match {
      case None => throw new RuntimeException("Controller endpoint URL must be set")
      case Some(x) => x
    }

    val reservation = new SoapReservation(wsn, controllerEndpointUrl)
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
    val servedNodeUrnPrefixes = new Holder[java.util.List[NodeUrnPrefix]]()
    val options = new Holder[java.util.List[KeyValuePair]]()

    sm.getConfiguration(rsEndpointUrl, snaaEndpointUrl, servedNodeUrnPrefixes, options)

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
      urnPrefixSrkPair: String => {
        val split = urnPrefixSrkPair.split(',')
        val srk = new SecretReservationKey()
        srk.setUrnPrefix(new NodeUrnPrefix(split(0).trim()))
        srk.setSecretReservationKey(split(1).trim())
        srk
      }
    }): _*)
  }
}
