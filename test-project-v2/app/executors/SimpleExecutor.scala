package executors

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import givers.moonlight.{JobExecutor, JobInSerDe, JobType}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SimpleExecutor @Inject()(implicit ec: ExecutionContext, actorSystem: ActorSystem)
    extends JobExecutor(SimpleExecutor.jobType) {

  private val blocking = actorSystem.dispatchers.lookup("akka.actor.blocking-dispatcher")

  override def run(data: SimpleExecutor.Job): Future[Unit] = {

    val start = System.currentTimeMillis()

    def someAsyncBlockingProcessing(str: String): Future[Unit] = {
      Future {
        println(s"start waiting $str " + Thread.currentThread().getName)
      }.map { _ =>
        println(
          s"processing blocking $str " + Thread.currentThread().getName + " " + (System.currentTimeMillis() - start)
        )
        Thread.sleep(3000)
      }(blocking)
        .map { _ =>
          println(s"finish waiting $str " + Thread.currentThread().getName + " " + (System.currentTimeMillis() - start))
        }
    }

    Future
      .sequence(
        (1 to 3)
          .map(n => someAsyncBlockingProcessing(s"1.$n"))
      )
      .map(_ => ())
      .flatMap(_ => someAsyncBlockingProcessing("2"))
  }
}

object SimpleExecutor {
  case class Job(data: String)

  val jobType: JobType[Job] = JobType("Simple", JobInSerDe.json(Json.format[Job]))
}
