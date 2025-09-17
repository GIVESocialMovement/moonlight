package givers.moonlight.util

//import io.netty.util.{HashedWheelTimer, Timeout}

import java.util.concurrent.{Callable, Executors, ScheduledFuture, TimeUnit}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Timeout scheduler
 */
object TimeoutScheduler {
//  val timer = new HashedWheelTimer(1, TimeUnit.SECONDS)
//  new DefaultScheduler
//  val scheduler = new Scheduler()
  private val scheduler = Executors.newScheduledThreadPool(1)

  def failPromiseAfter(promise: Promise[?], after: Duration): ScheduledFuture[Unit] = {
    scheduler.schedule(
      new Callable[Unit] {
        override def call(): Unit = promise.failure(new RichFuture.TimeoutException())
      },
      after.toNanos,
      TimeUnit.NANOSECONDS
    )
//    timer.newTimeout(
//      (_: Timeout) => {
//        promise.failure(new RichFuture.TimeoutException())
//      },
//      after.toNanos,
//      TimeUnit.NANOSECONDS
//    )
  }
}

object RichFuture {
  class TimeoutException extends Exception

  /**
   * Pimp my Library pattern for future
   */
  extension[T](future: Future[T]) {
    /**
     * Return future that can be failed if it's not completed for some time
     *
     * @param timeout
     *   timeout
     * @param ec
     *   execution context
     * @return
     *   wrapped combined future
     */
    def withTimeout(timeout: Duration)(using ec: ExecutionContext): Future[T] =
      val prom = Promise[T]()
      val schedule = TimeoutScheduler.failPromiseAfter(prom, timeout)
      val combinedFut = Future.firstCompletedOf(List(future, prom.future))
  
      future.onComplete { _ => schedule.cancel(false) }
  
      combinedFut
  }
}
