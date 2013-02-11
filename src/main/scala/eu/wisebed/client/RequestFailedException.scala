package eu.wisebed.client

import eu.wisebed.api.v3.common.NodeUrn
import com.google.common.base.Joiner
import scala.collection.JavaConversions._

class RequestFailedException(val nodeUrns: List[NodeUrn], val statusCode: Int, val errorMessage: String)
  extends Exception {

  def this(nodeUrns: List[NodeUrn], e: Exception) = this(nodeUrns, -1, e.getMessage)

  override def toString = this.getClass.getSimpleName +
    "[statusCode=%d, nodeUrns=[%s], errorMessage=%s]".format(statusCode, Joiner.on(",").join(nodeUrns), errorMessage)
}
