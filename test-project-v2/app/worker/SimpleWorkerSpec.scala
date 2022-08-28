package worker

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import givers.moonlight.{AsyncSupport, AsyncWorkerSpec, BackgroundJob, Worker}
import play.api.libs.json.{Json, OFormat}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object SimpleWorkerSpec extends AsyncWorkerSpec {
  case class Job(data: String) extends givers.moonlight.Job

  type Data = Job
  type Runner = SimpleWorker

  implicit val classTag = ClassTag(classOf[SimpleWorker])
  implicit val jsonFormat: OFormat[Job] = Json.format[Job]

  val identifier = "SimpleAsync"
  val previousIdentifiers = Set.empty
}

@Singleton
class SimpleWorker @Inject() (implicit ec: ExecutionContext, actorSystem: ActorSystem)
    extends Worker[SimpleWorkerSpec.Job]
    with AsyncSupport[SimpleWorkerSpec.Job] {
  def run(param: SimpleWorkerSpec.Job, job: BackgroundJob): Unit = ???

  val blocking = actorSystem.dispatchers.lookup("akka.actor.blocking-dispatcher")

  override def runAsync(job: BackgroundJob, data: SimpleWorkerSpec.Job): Future[Unit] = {

    val start = System.currentTimeMillis()

    def someAsyncBlockingProcessing(str: String) = {
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
