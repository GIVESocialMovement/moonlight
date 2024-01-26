package givers.moonlight.v2

import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import com.codahale.metrics.MetricRegistry
import givers.moonlight.util.DateTimeFactory
import givers.moonlight.util.RichDate.RichDate
import givers.moonlight.v2.repository.BackgroundJobRepository
import givers.moonlight.{JobExecutor, JobInSerDe, JobType, JobTypeJson}
import helpers.BackgroundJobFixture
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.Succeeded
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike
import play.api.inject.Injector
import play.api.libs.json.Json

import java.time.{Duration, LocalTime, ZoneId, ZonedDateTime}
import java.util.Date
import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random
import scala.util.control.NoStackTrace

class JobDispatcherSpec extends AsyncWordSpecLike with Matchers with AsyncIdiomaticMockito with BackgroundJobFixture {

  object Executor1 {
    case class JobData(shouldSucceed: Boolean)

    case object Type extends JobTypeJson[Executor1.JobData]("Executor1")(Json.format)
  }

  class Executor1 extends JobExecutor(Executor1.Type) {
    override def run(data: Executor1.JobData): Future[Unit] = {
      Option
        .when(data.shouldSucceed)(())
        .fold(Future.failed[Unit](new Exception("worker failure example") with NoStackTrace))(_ =>
          Future.successful(())
        )
    }
  }

  class TestScheduler(schedulerCancel: Cancellable) extends Scheduler {
    def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(implicit
      executor: ExecutionContext
    ): Cancellable = {
      runnable.run()

      schedulerCancel
    }

    def scheduleAtFixedRate(initialDelay: FiniteDuration, interval: FiniteDuration)(runnable: Runnable)(implicit
      executor: ExecutionContext
    ): Cancellable = {
      runnable.run()

      schedulerCancel
    }

    override def scheduleOnce(delay: Duration, runnable: Runnable, executor: ExecutionContext): Cancellable = ???

    override def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)(runnable: Runnable)(
      implicit executor: ExecutionContext
    ): Cancellable = ???

    override def scheduleWithFixedDelay(
      initialDelay: Duration,
      delay: Duration,
      runnable: Runnable,
      executor: ExecutionContext
    ): Cancellable = ???

    override def scheduleAtFixedRate(
      initialDelay: Duration,
      interval: Duration,
      runnable: Runnable,
      executor: ExecutionContext
    ): Cancellable = ???
  }

  "JobDispatcher" should {
    "throw JobTypeExecutorAlreadyExists" in {
      val repo = mock[BackgroundJobRepository]
      implicit val injector: Injector = mock[Injector]
      implicit val scheduler: Scheduler = new TestScheduler(mock[Cancellable])
      implicit val dateTimeFactory: DateTimeFactory = mock[DateTimeFactory]
      implicit val random: Random = mock[Random]
      val settings = MoonlightSettings(
        parallelism = 1,
        pauseDurationWhenNoJobs = 1.minute,
        maintenanceInterval = 1.hour,
        countMetricsCollectionInterval = 5.seconds,
        betweenRunAttemptInterval = 30.minutes,
        maxJobRetries = 3,
        jobRunTimeout = 1.second,
        completedJobsTtl = 90.days,
        executors = Seq(new Executor1, new Executor1),
        schedulerInputs = Set.empty
      )

      an[JobTypeExecutorAlreadyExists] should be thrownBy new JobDispatcher(
        repo,
        settings,
        dateTimeFactory,
        new MetricRegistry
      )
    }
  }

  "JobDispatcher.runLoop" should {
    "run jobs" in {
      val parallelism = 2

      val firstId = 1
      val secondId = 2
      val thirdId = 3
      val fourthId = 4

      val zoneId = ZoneId.systemDefault()
      val date = ZonedDateTime.of(2022, 9, 14, 16, 2, 2, 0, zoneId)
      val currentDate = new Date(date.toEpochSecond * 1000)

      val repo = mock[BackgroundJobRepository]
      val settings = MoonlightSettings(
        parallelism = parallelism,
        pauseDurationWhenNoJobs = 1.minute,
        maintenanceInterval = 1.hour,
        countMetricsCollectionInterval = 5.seconds,
        betweenRunAttemptInterval = 30.minutes,
        maxJobRetries = 3,
        jobRunTimeout = 1.second,
        completedJobsTtl = 90.days,
        executors = Seq(new Executor1),
        schedulerInputs = Set.empty
      )

      val schedulerCancel = mock[Cancellable]
      schedulerCancel.cancel() returns true

      implicit val injector: Injector = mock[Injector]
      implicit val scheduler: Scheduler = new TestScheduler(schedulerCancel)
      implicit val dateTimeFactory: DateTimeFactory = mock[DateTimeFactory]
      implicit val random: Random = mock[Random]

      val dispatcher = new JobDispatcher(repo, settings, dateTimeFactory, new MetricRegistry)

      dateTimeFactory.now returns currentDate

      // promise that will be completed when all jobs were retrieved
      // it's needed for proper synchronisation to avoid "wait-based" sync
      val allJobsWereUsed = Promise[Unit]

      // simulates metrics
      repo.countPendingJobReadyForStart(currentDate) returns Future.successful(1)
      repo.count returns Future.successful(1)

      // simulates maintenance
      date.minusDays(90).toLocalDate.atTime(LocalTime.MIDNIGHT).atZone(zoneId).toEpochSecond
      repo
        .deleteJobsSucceededOrFailedBefore(
          new Date(date.minusDays(90).toLocalDate.atTime(LocalTime.MIDNIGHT).atZone(zoneId).toEpochSecond * 1000)
        )
        .returns(Future.successful(50))

      // simulates maintenance 2
      repo.maintainExpiredJobs(1.second, currentDate) returns Future.successful(100)

      // simulates 3+ calls of job retrieving method
      repo
        .getPendingJobReadyForStart(currentDate)
        // this job will be completed
        .returns(
          Future.successful(
            Some(
              jobOfType("Executor1", firstId)
                .copy(paramsInJsonString = """{"shouldSucceed": true}""")
            )
          )
        )
        // there is no worker for this job so it will just log error
        .andThen(Future.successful(Some(jobOfType("Executor2", secondId))))
        .andThenAnswer {
          Future.successful(None)
        }

      // after getPendingJobReadyForStart returns None dispatcher will call getFailedJobReadyForRetry
      repo
        .getFailedJobReadyForRetry(3, currentDate.sub(30.minutes))
        // this job will fail because of param
        .returns(
          Future.successful(
            Some(
              jobOfType("Executor1", thirdId)
                .copy(paramsInJsonString = """{"shouldSucceed": false}""")
            )
          )
        )
        // this job will be skipped because of concurrency simulation
        .andThen(
          Future.successful(
            Some(
              jobOfType("Executor1", fourthId)
                .copy(paramsInJsonString = """{"shouldSucceed": false}""")
            )
          )
        )
        // no more jobs
        .andThenAnswer {
          if (!allJobsWereUsed.isCompleted) {
            allJobsWereUsed.success(())
          }

          Future.successful(None)
        }

      repo.tryMarkJobAsStarted(firstId, 1, *, *, *, *) returns Future.successful(true)
      repo.tryMarkJobAsStarted(thirdId, 1, *, *, *, *) returns Future.successful(true)
      // simulate concurrent run.
      // When Future return false it means that the job was picked by other instance of the application
      repo.tryMarkJobAsStarted(fourthId, 1, *, *, *, *) returns Future.successful(false)
      repo.markJobAsFailed(secondId, *, *) returns Future.successful(())
      repo.markJobAsFailed(thirdId, *, *) returns Future.successful(())

      repo.markJobAsSucceed(firstId, *) returns Future.successful(())

      random.between(60000L, 66000L) returns 61000L

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
