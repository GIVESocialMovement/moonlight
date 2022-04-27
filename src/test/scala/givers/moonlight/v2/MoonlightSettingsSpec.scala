package givers.moonlight.v2

import givers.moonlight.{AsyncSupport, AsyncWorkerSpec, BackgroundJob, Worker, WorkerSpec}
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.inject.Injector
import play.api.libs.json.{Json, OFormat}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

class MoonlightSettingsSpec extends AnyWordSpecLike with Matchers with IdiomaticMockito{
  object WorkerSpecExample {
    case class JobData(shouldSucceed: Boolean) extends givers.moonlight.Job
    implicit val jsonFormat: OFormat[JobData] = Json.format[JobData]
  }

  class WorkerSpecExample(val identifier: String, val previousIdentifiers: Set[String]) extends WorkerSpec with AsyncWorkerSpec {
    type Data = WorkerSpecExample.JobData
    type Runner = WorkerExample

    implicit val classTag = ClassTag(classOf[WorkerExample])
    implicit val jsonFormat: OFormat[WorkerSpecExample.JobData] = WorkerSpecExample.jsonFormat
  }

  class WorkerExample() extends Worker[WorkerSpecExample.JobData] with AsyncSupport[WorkerSpecExample.JobData] {
    def run(param: WorkerSpecExample.JobData, job: BackgroundJob): Unit = ()

    override def runAsync(job: BackgroundJob, data: WorkerSpecExample.JobData): Future[Unit] = Future.successful(())
  }

  private val workerSpecs = Seq(
    new WorkerSpecExample("w1", Set.empty),
    new WorkerSpecExample("w2", Set.empty),
    new WorkerSpecExample("w_2", Set("w2"))
  )

  private val settings = MoonlightSettings(1, 0.seconds, 0.seconds, 0.seconds, 1, 0.seconds, workerSpecs)

  private implicit val injector: Injector = mock[Injector]

  "MoonlightSettings.getWorkerByJobType" should {
    "throw an exception" when {
      "there is no worker with such type" in {
        an[WorkerSearchException] should be thrownBy settings.getWorkerByJobType("w0")
      }

      "there are several worker candidates" in {
        an[WorkerSearchException] should be thrownBy settings.getWorkerByJobType("w2")
      }
    }

    "return worker" in {
      val worker = new WorkerExample
      injector.instanceOf[WorkerExample] returns worker

      settings.getWorkerByJobType("w1") shouldBe worker
    }
  }
}
