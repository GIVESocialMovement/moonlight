package givers.moonlight.v2.repository

import givers.moonlight.BackgroundJob.Status
import givers.moonlight.persistence.table.BackgroundJobTableComponent
import givers.moonlight.util.RichDate.RichDate
import givers.moonlight.{BackgroundJob, JobId}
import helpers.{DatabaseSchemaSupport, DatabaseSpec, H2SlickJdbcProfile}
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike
import play.api.libs.json.{Json, OFormat}

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class BackgroundJobJdbcRepositorySpec extends AsyncWordSpecLike
  with Matchers
  with DatabaseSpec
  with DatabaseSchemaSupport
  with AsyncIdiomaticMockito {

  import profile.api._

  val tables = new H2SlickJdbcProfile with BackgroundJobTableComponent

  override protected def schemas: profile.SchemaDescription = tables.backgroundJobs.schema

  private val repo = new BackgroundJobJdbcRepository(
    dbConfigProvider,
    tables
  )

  case class JobExampleParam(campaignId: Long, oneMoreId: String) extends givers.moonlight.Job

  private implicit val jobId: JobId[JobExampleParam] = JobId[JobExampleParam]("example")
  private implicit val jsonFormat: OFormat[JobExampleParam] = Json.format[JobExampleParam]
  private val param =  JobExampleParam(444, "555")
  private val priority = 1

  private val nowDate = new Date(123456789)

  private val jobExample = BackgroundJob(
    id = -1,
    createdAt = new Date(),
    shouldRunAt = nowDate,
    initiatedAtOpt = None,
    startedAtOpt = None,
    finishedAtOpt = None,
    status = BackgroundJob.Status.Pending,
    error = "",
    tryCount = 0,
    jobType = jobId.value,
    paramsInJsonString = """{"campaignId":444,"oneMoreId":"555"}""",
    priority = priority
  )

  "BackgroundJobJdbcRepository.enqueue" should {
    "enqueue a job" in {
      for {
        savedJob <- repo.enqueue(BackgroundJob.forEnqueue(nowDate, priority, param))
        dbContent <- db.run(tables.backgroundJobs.result)
      } yield {
        val expectedJob = jobExample.copy(id = savedJob.id, createdAt = savedJob.createdAt)
        savedJob shouldBe expectedJob
        dbContent should contain theSameElementsAs Seq(expectedJob)
      }
    }
  }

  "BackgroundJobJdbcRepository.getJobs" should {
    "get jobs considering skip/take" in {
      for {
        _ <- insertJob(jobExample)
        two <- insertJob(jobExample)
        // wrong status
        _ <- insertJob(jobExample.copy(jobType = "other-type"))
        three <- insertJob(jobExample)
        _ <- insertJob(jobExample)
        jobs <- repo.getJobs(skip = 1, take = 2, Seq("example"))
      } yield {
        jobs.map(_.id) should contain theSameElementsAs Seq(two.id, three.id)
      }
    }
  }

  "BackgroundJobJdbcRepository.getJobsReadyForStart" should {
    "get jobs considering select conditions" in {
      val maxAttempts = 3
      val interval = 1.minute
      val currentDate = nowDate.add(1.milli) // after nowDate
      val futureDate = nowDate.add(10.milli)

      for {
        okToStart <- insertJob(jobExample)
        okToRestart <- insertJob(jobExample.copy(
          status = Status.Failed,
          tryCount = maxAttempts - 1,
          finishedAtOpt = Some(new Date(currentDate.getTime - interval.toMillis - 1)))
        )
        // wrong type
        _ <- insertJob(jobExample.copy(jobType = "other-type"))
        // is not supposed to be started now
        _ <- insertJob(jobExample.copy(shouldRunAt = futureDate))
        // max attempts reached
        _ <- insertJob(jobExample.copy(status = Status.Failed, tryCount = maxAttempts))
        // was failed a moment ago so for now is not supposed to be restarted
        _ <- insertJob(jobExample.copy(status = Status.Failed))
        // wrong status
        _ <- insertJob(jobExample.copy(status = Status.Initiated))
        // wrong status
        _ <- insertJob(jobExample.copy(status = Status.Started))
        // wrong status
        _ <- insertJob(jobExample.copy(status = Status.Succeeded))
        jobs <- repo.getJobsReadyForStart(3, maxAttempts, currentDate, interval, Seq("example"))
        topJob <- repo.getJobReadyForStart(maxAttempts, currentDate, interval, Seq("example"))
      } yield {
        jobs shouldBe Seq(okToStart, okToRestart)
        topJob shouldBe Some(okToStart)
      }
    }

    "get jobs in right order" in {
      val now = nowDate.add(1.milli)
      val aBitLater = nowDate.add(2.milli)

      for {
        second <- insertJob(jobExample.copy(priority = 1, createdAt = aBitLater))
        first <- insertJob(jobExample.copy(priority = 1, createdAt = now))
        fourth <- insertJob(jobExample.copy(priority = 2, createdAt = aBitLater))
        // status is not important
        third <- insertJob(jobExample.copy(priority = 2, createdAt = now, status = Status.Failed, finishedAtOpt = Some(now)))
        jobs <- repo.getJobsReadyForStart(4, 1, aBitLater, 0.second, Seq("example"))
      } yield {
        jobs.map(_.id) shouldBe Seq(first, second, third, fourth).map(_.id)
      }
    }
  }

  "BackgroundJobJdbcRepository.maintainExpiredJobs" should {
    "change statuses of expired jobs" in {
      val timeout = 1.minute
      val timeoutForSub = timeout + 100.milli

      for {
        first <- insertJob(jobExample.copy(status = Status.Initiated, initiatedAtOpt = Some(nowDate.sub(timeoutForSub))))
        second <- insertJob(jobExample.copy(status = Status.Initiated))
        third <- insertJob(jobExample.copy(status = Status.Started, startedAtOpt = Some(nowDate.sub(timeoutForSub))))
        fourth <- insertJob(jobExample.copy(status = Status.Started))
        changedJobs <- repo.maintainExpiredJobs(timeout, nowDate)
        dbContent <- db.run(tables.backgroundJobs.result)
      } yield {
        changedJobs shouldBe 2

        dbContent.map(_.copy(error = "")) should contain theSameElementsAs Seq(
          first.copy(status = Status.Pending, finishedAtOpt = Some(nowDate)),
          second,
          third.copy(status = Status.Failed, finishedAtOpt = Some(nowDate)),
          fourth
        )
      }
    }
  }

  "BackgroundJobJdbcRepository.markJobAsStarted" should {
    "change job status to started" in {
      val updateDate = nowDate.add(1.minute)
      for {
        first <- insertJob(jobExample)
        dummy <- insertJob(jobExample)
        isChanged <- repo.markJobAsStarted(first.id, 1, updateDate)
        dbContent <- db.run(tables.backgroundJobs.result)
      } yield {
        isChanged shouldBe true

        dbContent should contain theSameElementsAs Seq(
          first.copy(
            status = Status.Started,
            startedAtOpt = Some(updateDate),
            initiatedAtOpt = Some(updateDate),
            tryCount = 1
          ),
          dummy
        )
      }
    }

    "not change job status" when {
      "job is already started" in {
        val updateDate = nowDate.add(1.minute)

        for {
          first <- insertJob(jobExample.copy(status = Status.Started, startedAtOpt = Some(updateDate)))
          dummy <- insertJob(jobExample.copy(status = Status.Started, startedAtOpt = Some(updateDate)))
          isChanged <- repo.markJobAsStarted(first.id, 1, updateDate)
          dbContent <- db.run(tables.backgroundJobs.result)
        } yield {
          isChanged shouldBe false

          dbContent should contain theSameElementsAs Seq(first, dummy)
        }
      }
      "job is already succeeded" in {
        val updateDate = nowDate.add(1.minute)

        for {
          first <- insertJob(jobExample.copy(status = Status.Succeeded, startedAtOpt = Some(updateDate)))
          isChanged <- repo.markJobAsStarted(first.id, 1, updateDate)
          dbContent <- db.run(tables.backgroundJobs.result)
        } yield {
          isChanged shouldBe false

          dbContent should contain theSameElementsAs Seq(first)
        }
      }
    }
  }

  "BackgroundJobJdbcRepository.markJobAsSucceed" should {
    "change job status to succeeded" in {
      val updateDate = nowDate.add(1.minute)

      for {
        first <- insertJob(jobExample)
        dummy <- insertJob(jobExample)
        _ <- repo.markJobAsSucceed(first.id, updateDate)
        dbContent <- db.run(tables.backgroundJobs.result)
      } yield {
        dbContent should contain theSameElementsAs Seq(
          first.copy(
            status = Status.Succeeded,
            finishedAtOpt = Some(updateDate)
          ),
          dummy
        )
      }
    }
  }

  "BackgroundJobJdbcRepository.markJobAsFailed" should {
    "change job status to failed" in {
      val updateDate = nowDate.add(1.minute)
      val error = new Exception("error example")

      for {
        first <- insertJob(jobExample)
        dummy <- insertJob(jobExample)
        _ <- repo.markJobAsFailed(first.id, error, updateDate)
        dbContent <- db.run(tables.backgroundJobs.result)
      } yield {
        dbContent.map(_.copy(error = "")) should contain theSameElementsAs Seq(
          first.copy(
            status = Status.Failed,
            finishedAtOpt = Some(updateDate)
          ),
          dummy
        )
      }
    }
  }

  private def insertJob(job: BackgroundJob): Future[BackgroundJob] = {
    db
      .run((tables.backgroundJobs returning tables.backgroundJobs.map(_.id)) += job)
      .map(insertedId => job.copy(id = insertedId))
  }
}
