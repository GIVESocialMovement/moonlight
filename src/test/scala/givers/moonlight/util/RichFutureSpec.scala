package givers.moonlight.util

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}

class RichFutureSpec extends AsyncWordSpecLike with Matchers with BeforeAndAfterAll {
  import RichFuture._

  private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(0)

  "RichFuture.withTimeout" should {
    "fail the future" when {
      "timeout was reached" in {
        val promise = Promise[Boolean]()
        scheduler.schedule(() => promise.success(true), 4, TimeUnit.SECONDS)

        val future = RichFuture.withTimeout(promise.future, 1.second)

        recoverToSucceededIf[TimeoutException](future)
      }
    }

    "succeed future" when {
      "timeout was not reached" in {
        RichFuture.withTimeout(Future(true), 1.second).map {
          _ shouldBe true
        }
      }
    }
  }

  "'pimped' RichFuture.withTimeout" should {
    "succeed future" when {
      "timeout was not reached" in {
        Future(true).withTimeout(1.second).map {
          _ shouldBe true
        }
      }
    }
  }

  override def afterAll(): Unit = {
    scheduler.shutdown()
    super.afterAll()
  }
}
