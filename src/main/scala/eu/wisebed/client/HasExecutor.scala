package eu.wisebed.client

import java.util.concurrent.{TimeUnit, Executors, ExecutorService}
import de.uniluebeck.itm.tr.util.ExecutorUtils

trait HasExecutor {

  protected lazy val executor: ExecutorService = Executors.newCachedThreadPool()

  protected def shutdownExecutor() {
    ExecutorUtils.shutdown(executor, 1, TimeUnit.SECONDS)
  }
}
