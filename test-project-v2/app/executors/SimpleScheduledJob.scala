package executors

import com.google.inject.Inject
import givers.moonlight.scheduled.ScheduledJob
import play.api.Logger

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class SimpleScheduledJob @Inject() (implicit val executionContext: ExecutionContext) extends ScheduledJob {
  type IN = (AtomicInteger, String)

  private val logger = Logger(this.getClass)
  override def run(input: IN): Future[Unit] = {
    val (atomic, name) = input

    logger.info(s"[$name] current ${atomic.get()}")
    val modified = atomic.incrementAndGet()
    logger.info(s"[$name] modified $modified")

    Future.unit
  }
}
