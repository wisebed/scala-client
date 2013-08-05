package eu.wisebed.client

import eu.wisebed.api.v3.common.NodeUrn

class NodeRequestFailedException(val nodeUrn: NodeUrn, val statusCode: Int, val exception: Exception) extends Exception {

}
