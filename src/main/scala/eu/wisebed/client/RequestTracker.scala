package eu.wisebed.client

import eu.wisebed.api.v3.common.NodeUrn
import de.uniluebeck.itm.tr.util.ProgressListenableFutureMap
import com.google.common.util.concurrent.MoreExecutors
import scala.collection.JavaConversions._

class RequestTracker(private val futureMap: ProgressListenableFutureMap[NodeUrn, Any]) {

  private var nodeProgressListeners = List[(NodeUrn, Int) => Unit]()

  private var nodeCompletionListeners = List[NodeUrn => Unit]()

  private var nodeFailureListeners = List[(NodeUrn, RequestFailedException) => Unit]()

  private var progressListeners = List[Int => Unit]()

  private var completionListeners = List[() => Unit]()

  private var failureListeners = List[(RequestFailedException) => Unit]()

  private val executor = MoreExecutors.sameThreadExecutor()

  for (nodeUrn <- futureMap.keySet()) {

    val future = futureMap.get(nodeUrn)

    future.addProgressListener(new Runnable() {
      def run() {
        notifyNodeProgress(nodeUrn, (future.getProgress * 100).toInt)
      }
    }, executor)

    future.addListener(new Runnable() {
      def run() {
        try {
          future.get()
          notifyNodeCompletion(nodeUrn)
        } catch {
          case e: RequestFailedException => {
            notifyNodeFailure(nodeUrn, e)
          }
          case e: Exception => {
            notifyNodeFailure(nodeUrn, new RequestFailedException(futureMap.keySet().toList, e))
          }
        }
      }
    }, executor)
  }

  futureMap.addListener(new Runnable() {
    def run() {
      try {
        futureMap.get() // check if exception occurred
        notifyCompletion()
      } catch {
        case e: RequestFailedException => {
          notifyFailure(e)
        }
        case e: Exception => {
          notifyFailure(new RequestFailedException(futureMap.keySet().toList, e))
        }
      }
    }
  }, executor)

  def onNodeProgress(listener: (NodeUrn, Int) => Unit) {
    nodeProgressListeners ::= listener
  }

  def onNodeCompletion(listener: NodeUrn => Unit) {
    nodeCompletionListeners ::= listener
  }

  def onNodeFailure(listener: (NodeUrn, RequestFailedException) => Unit) {
    nodeFailureListeners ::= listener
  }

  def onProgress(listener: (Int) => Unit) {
    progressListeners ::= listener
  }

  def onCompletion(listener: () => Unit) {
    completionListeners ::= listener
  }

  def onFailure(listener: (RequestFailedException) => Unit) {
    failureListeners ::= listener
  }

  def notifyNodeProgress(nodeUrn: NodeUrn, progressInPercent: Int) {
    nodeProgressListeners.foreach(listener => listener(nodeUrn, progressInPercent))
  }

  def notifyNodeCompletion(nodeUrn: NodeUrn) {
    nodeCompletionListeners.foreach(listener => listener(nodeUrn))
  }

  def notifyNodeFailure(nodeUrn: NodeUrn, exception: RequestFailedException) {
    nodeFailureListeners.foreach(listener => listener(nodeUrn, exception))
  }

  def notifyProgress(progressInPercent: Int) {
    progressListeners.foreach(listener => listener(progressInPercent))
  }

  def notifyCompletion() {
    completionListeners.foreach(listener => listener())
  }

  def notifyFailure(e: RequestFailedException) {
    failureListeners.foreach(listener => listener(e))
  }

  def map = futureMap
}
