package eu.wisebed.client.util

import org.slf4j.LoggerFactory

trait Logging {

  protected lazy val logger = LoggerFactory.getLogger(this.getClass)

}
