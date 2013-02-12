package eu.wisebed.client

import scopt.mutable.OptionParser
import eu.wisebed.api.v3.common.NodeUrn

class AreNodesConnectedClientConfig extends Config {

  var nodeUrns: Option[List[NodeUrn]] = None
}

class AreNodesConnectedClient(args: Array[String]) extends WisebedClient[AreNodesConnectedClientConfig] {

  private val initialConfig = new AreNodesConnectedClientConfig()

  private val optionParser = new OptionParser("areNodesConnected", true) {
    opt("n", "nodeUrns", "a comma-separated list of node URNs that are to be reserved", {
      nodeUrnString: String => {
        initialConfig.nodeUrns = Some(List(nodeUrnString.split(",").map(nodeUrn => new NodeUrn(nodeUrn.trim)): _*))
      }
    })
  }

  init(args, initialConfig, optionParser)

  def areNodesConnected(): Map[NodeUrn, Boolean] = areNodesConnected(config.nodeUrns.getOrElse(testbedNodes))
}

object AreNodesConnected extends App {
  {
    new AreNodesConnectedClient(args).areNodesConnected().foreach({
      case (nodeUrn, isConnected) => {
        println("%s => %b".format(nodeUrn, isConnected))
      }
    })
  }
}
