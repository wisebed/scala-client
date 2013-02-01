package eu.wisebed.client

import java.io.{ File, FileReader }
import java.net.{URI, URL}
import java.util.Properties
import collection.mutable
import eu.wisebed.api.v3.common.NodeUrnPrefix

class Credentials(val urnPrefix: NodeUrnPrefix, val username: String, val password: String)

class Config {

  var controllerEndpointUrl: Option[URL] = None

  val setControllerEndpointUrl: String => Unit = {
    param => controllerEndpointUrl = Some(URI.create(param).toURL)
  }

  var smEndpointUrl: Option[URL] = None

  var rsEndpointUrl: Option[URL] = None

  var snaaEndpointUrl: Option[URL] = None

  var credentials: List[Credentials] = null

  val parseFromConfigFile: String => Unit = { param =>

    val configFile = new File(param)

    if (!configFile.exists) {
      throw new IllegalArgumentException("Configuration file " + configFile.getAbsolutePath + " does not exist!")
    } else if (!configFile.isFile) {
      throw new IllegalArgumentException("Configuration file " + configFile.getAbsolutePath + " is not a file!")
    } else if (!configFile.canRead) {
      throw new IllegalArgumentException("Configuration file " + configFile.getAbsolutePath + " can't be read!!")
    }

    val properties = new Properties()
    properties.load(new FileReader(configFile))

    {
      implicit def propertyToOptionalUrl(o: Object): Option[URL] = {
        o match {
          case url: String => Some(new URL(url))
          case _ => None
        }
      }

      smEndpointUrl = properties.get("testbed.sm.endpointurl")
      rsEndpointUrl = properties.get("testbed.rs.endpointurl")
      snaaEndpointUrl = properties.get("testbed.snaa.endpointurl")
    }

    credentials = List({

      implicit def propertyToStringArray(o: Object): Array[String] = {
        val stringValue: String = o.asInstanceOf[String]
        val split = stringValue.split(",")
        split.map(s => s.trim())
      }

      implicit def stringToNodeUrnPrefix(s:String):NodeUrnPrefix = new NodeUrnPrefix(s)

      val urnPrefixes: Array[String] = properties.get("testbed.urnprefixes")
      val usernames: Array[String] = properties.get("testbed.usernames")
      val passwords: Array[String] = properties.get("testbed.passwords")

      if (urnPrefixes.length != usernames.length || urnPrefixes.length != passwords.length) {
        throw new IllegalArgumentException("The number of urnPrefixes / usernames / passwords do not match!")
      }

      val list = mutable.MutableList[Credentials]()
      for (i <- urnPrefixes.indices) {
        list += new Credentials(urnPrefixes(i), usernames(i), passwords(i))
      }

      list
    }: _*)
  }
}
