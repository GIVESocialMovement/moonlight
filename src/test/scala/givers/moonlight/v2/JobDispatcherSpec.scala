package givers.moonlight.v2

import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import givers.moonlight.util.DateTimeFactory
import givers.moonlight.v2.repository.BackgroundJobRepository
import givers.moonlight.{AsyncSupport, AsyncWorkerSpec, BackgroundJob, Worker, WorkerSpec}
import helpers.BackgroundJobFixture
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.Succeeded
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike
import play.api.inject.Injector
import play.api.libs.json.{Json, OFormat}

import java.util.Date
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.DurationInt
import scala.reflect.{ClassTag, classTag}

class JobDispatcherSpec
  extends AsyncWordSpecLike
  with Matchers
  with AsyncIdiomaticMockito
  with BackgroundJobFixture {


  object Worker1Spec extends WorkerSpec with AsyncWorkerSpec {
    case class JobData(shouldSucceed: Boolean) extends givers.moonlight.Job

    type Data = JobData
    type Runner = Worker1

    implicit val classTag = ClassTag(classOf[Worker1])
    implicit val jsonFormat: OFormat[JobData] = Json.format[JobData]

    val identifier = "Worker1"
    val previousIdentifiers = Set.empty
  }

  class Worker1() extends Worker[Worker1Spec.JobData] with AsyncSupport[Worker1Spec.JobData] {
    def run(param: Worker1Spec.JobData, job: BackgroundJob): Unit = ()

    override def runAsync(job: BackgroundJob, data: Worker1Spec.JobData): Future[Unit] = {
      Option.when(data.shouldSucceed)(())
        .fold(Future.failed[Unit](new Exception("worker failure example")))(_ => Future.successful(()))
    }
  }


  "JobDispatcher.runLoop" should {
    "run jobs" in {
      val parallelism = 2

      val firstId = 1
      val secondId = 2
      val thirdId = 3
      val fourthId = 4

      val currentDate = new Date(123)

      val repo = mock[BackgroundJobRepository]
      val settings = MoonlightSettings(
        parallelism = parallelism,
        pauseDurationWhenNoJobs = 1.minute,
        maintenanceInterval = 1.hour,
        betweenRunAttemptInterval = 30.minutes,
        maxJobRetries = 3,
        jobRunTimeout = 1.second,
        workerSpecs = Seq(Worker1Spec)
      )
      implicit val injector = mock[Injector]
      implicit val scheduler = mock[Scheduler]
      implicit val dateTimeFactory = mock[DateTimeFactory]
      val schedulerCancel = mock[Cancellable]

      val dispatcher = new JobDispatcher(repo, settings, dateTimeFactory)

      dateTimeFactory.now returns currentDate
      scheduler.scheduleAtFixedRate(0.minutes, 1.hour)(*)(*) returns schedulerCancel
      schedulerCancel.cancel() returns true

      // promise that will be completed when all jobs were retrieved
      // it's needed for proper synchronisation to avoid "wait-based" sync
      val allJobsWereUsed = Promise[Unit]

      // simulates 3+ calls of job retrieving method
      repo.getJobReadyForStart(3, currentDate, 30.minutes)
        // this job will be completed
        .returns(Future.successful(Some(jobOfType("Worker1", firstId).copy(paramsInJsonString = """{"shouldSucceed": true}"""))))
        // there is no worker for this job so it will just log error
        .andThen(Future.successful(Some(jobOfType("Worker2", secondId))))
        // this job will fail because of param
        .andThen(Future.successful(Some(jobOfType("Worker1", thirdId).copy(paramsInJsonString = """{"shouldSucceed": false}"""))))
        // this job will be skipped because of concurrency simulation
        .andThen(Future.successful(Some(jobOfType("Worker1", fourthId).copy(paramsInJsonString = """{"shouldSucceed": false}"""))))
        // no more jobs
        .andThenAnswer {
          if(!allJobsWereUsed.isCompleted) {
            allJobsWereUsed.success(())
          }

          Future.successful(None)
        }

      repo.markJobAsStarted(firstId, 1, *) returns Future.successful(true)
      repo.markJobAsStarted(thirdId, 1, *) returns Future.successful(true)
      // simulate concurrent run.
      // When Future return false it means that the job was picked my other instance of the application
      repo.markJobAsStarted(fourthId, 1, *) returns Future.successful(false)
      repo.markJobAsFailed(secondId, *, *) returns Future.successful(())
      repo.markJobAsFailed(thirdId, *, *) returns Future.successful(())

      repo.markJobAsSucceed(firstId, *) returns Future.successful(())

      injector.instanceOf[Worker1](classTag[Worker1]) returns new Worker1
      scheduler.scheduleOnce(1.minute, *)(*) returns null

      val (cancelLoop, loopFuture) = dispatcher.runLoop()

      for {
        _ <- allJobsWereUsed.future
        _ = cancelLoop.cancel()
        _ <- loopFuture
      } yield {
        Succeeded
      }
    }
  }
}
