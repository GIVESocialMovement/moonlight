package givers.moonlight.util

import io.netty.util.{HashedWheelTimer, Timeout}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Timeout scheduler
 */
object TimeoutScheduler {
  val timer = new HashedWheelTimer(1, TimeUnit.SECONDS)

  def failPromiseAfter(promise: Promise[_], after: Duration): Timeout = {
    timer.newTimeout(
      (_: Timeout) => {
        promise.failure(new RichFuture.TimeoutException())
      },
      after.toNanos,
      TimeUnit.NANOSECONDS
    )
  }
}

object RichFuture {
  class TimeoutException extends Exception

  /**
   * Return future that can be failed if it's not completed for some time
   *
   * @param future future without timeout
   * @param timeout timeout duration
   * @param ec execution context
   * @return wrapped combined future
   */
  def withTimeout[T](future: Future[T], timeout: Duration)(implicit ec: ExecutionContext): Future[T] = {
    val prom        = Promise[T]()
    val schedule    = TimeoutScheduler.failPromiseAfter(prom, timeout)
    val combinedFut = Future.firstCompletedOf(List(future, prom.future))

    future.onComplete { _ => schedule.cancel() }

    combinedFut
  }

  /**
   * Pimp my Library pattern for future
   *
   * @param future future to wrap
   */
  implicit class TimeoutAwareFuture[T](val future: Future[T]) extends AnyVal {

    /**
     * Return future that can be failed if it's not completed for some time
     *
     * @param timeout timeout
     * @param ec execution context
     * @return wrapped combined future
     */
    def withTimeout(timeout: Duration)(implicit ec: ExecutionContext): Future[T] =
      RichFuture.withTimeout(future, timeout)
  }
}

