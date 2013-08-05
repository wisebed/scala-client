package eu.wisebed.client

import eu.wisebed.api.v3.common.NodeUrn

class RequestFailedException(val failed: Map[NodeUrn, (Int, String)]) extends Exception {

  def getFailedNodeUrns: Set[NodeUrn] = failed.keySet

  def getStatusCode(nodeUrn: NodeUrn): Option[Int] = failed.get(nodeUrn) match {
    case Some(x) => Some(x._1)
    case None => None
  }

  def getErrorMessage(nodeUrn: NodeUrn): Option[String] = failed.get(nodeUrn) match {
    case Some(x) => Some(x._2)
    case None => None
  }

  override def toString = {
    val s = new StringBuilder()
    failed.keys.foreach({
      case nodeUrn => {
        s ++ nodeUrn.toString
        s ++ " => "
        getStatusCode(nodeUrn) match {
          case Some(x) => s ++ x.toString
          case None =>
        }
        getErrorMessage(nodeUrn) match {
          case Some(x) => s ++ x.toString
          case None =>
        }
      }
    })
    s.toString()
  }
}
