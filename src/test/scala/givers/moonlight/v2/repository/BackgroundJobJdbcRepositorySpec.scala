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

class BackgroundJobJdbcRepositorySpec
    extends AsyncWordSpecLike
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
  private val param = JobExampleParam(444, "555")
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

  private val failedJobExample = jobExample.copy(status = Status.Failed)
  private val succeededJobExample = jobExample.copy(status = Status.Succeeded)

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
        three <- insertJob(jobExample)
        _ <- insertJob(jobExample)
        jobs <- repo.getJobs(skip = 1, take = 2)
      } yield {
        jobs.map(_.id) should contain theSameElementsAs Seq(two.id, three.id)
      }
    }
  }

  "BackgroundJobJdbcRepository.getPendingJobReadyForStart" should {
    "get job considering select conditions" in {
      val maxAttempts = 3
      val currentDate = nowDate.add(1.milli) // after nowDate
      val futureDate = nowDate.add(10.milli)

      for {
        okToStart <- insertJob(jobExample)
        // is not supposed to be started now
        _ <- insertJob(jobExample.copy(shouldRunAt = futureDate))
        // max attempts reached
        _ <- insertJob(failedJobExample.copy(tryCount = maxAttempts))
        // was failed a moment ago so for now is not supposed to be restarted
        _ <- insertJob(failedJobExample)
        // wrong status
        _ <- insertJob(jobExample.copy(status = Status.Started))
        // wrong status
        _ <- insertJob(jobExample.copy(status = Status.Succeeded))
        topJob <- repo.getPendingJobReadyForStart(currentDate)
      } yield {
        topJob shouldBe Some(okToStart)
      }
    }

    "get jobs in right order" in {
      val now = nowDate.add(1.milli)
      val aBitLater = nowDate.add(2.milli)

      for {
        _ <- insertJob(jobExample.copy(priority = 2, createdAt = aBitLater))
        third <- insertJob(jobExample.copy(priority = 2, createdAt = now, finishedAtOpt = Some(now)))
        firstJobResult <- repo.getPendingJobReadyForStart(aBitLater)
        second <- insertJob(jobExample.copy(priority = 1, createdAt = aBitLater))
        secondJobResult <- repo.getPendingJobReadyForStart(aBitLater)
        first <- insertJob(jobExample.copy(priority = 1, createdAt = now))
        thirdJobResult <- repo.getPendingJobReadyForStart(aBitLater)
      } yield {
        firstJobResult.map(_.id) shouldBe Some(third.id)
        secondJobResult.map(_.id) shouldBe Some(second.id)
        thirdJobResult.map(_.id) shouldBe Some(first.id)
      }
    }
  }

  "BackgroundJobJdbcRepository.getFailedJobReadyForRetry" should {
    "get job considering select conditions" in {
      val maxAttempts = 3
      val interval = 1.minute
      val currentDate = nowDate.add(1.milli) // after nowDate
      val futureDate = nowDate.add(10.milli)

      for {
        okToRestart <- insertJob(
          jobExample.copy(
            status = Status.Failed,
            tryCount = maxAttempts - 1,
            finishedAtOpt = Some(new Date(currentDate.getTime - interval.toMillis - 1))
          )
        )
        // is not supposed to be started now
        _ <- insertJob(jobExample.copy(shouldRunAt = futureDate))
        // max attempts reached
        _ <- insertJob(failedJobExample.copy(tryCount = maxAttempts))
        // was failed a moment ago so for now is not supposed to be restarted
        _ <- insertJob(failedJobExample)
        // wrong status
        _ <- insertJob(jobExample.copy(status = Status.Started))
        // wrong status
        _ <- insertJob(jobExample.copy(status = Status.Succeeded))
        topJob <- repo.getFailedJobReadyForRetry(maxAttempts, currentDate.sub(interval))
      } yield {
        topJob shouldBe Some(okToRestart)
      }
    }

    "get jobs in right order" in {
      val now = nowDate.add(1.milli)
      val aBitLater = nowDate.add(2.milli)

      for {
        _ <- insertJob(failedJobExample.copy(priority = 2, createdAt = now))
        first <- insertJob(failedJobExample.copy(priority = 2, createdAt = now, finishedAtOpt = Some(now)))
        firstJobResult <- repo.getFailedJobReadyForRetry(2, aBitLater)
        _ <- insertJob(failedJobExample.copy(priority = 2, createdAt = aBitLater, finishedAtOpt = Some(now)))
        secondJobResult <- repo.getFailedJobReadyForRetry(1, aBitLater)
        third <- insertJob(failedJobExample.copy(priority = 1, createdAt = now, finishedAtOpt = Some(now)))
        thirdJobResult <- repo.getFailedJobReadyForRetry(1, aBitLater)
      } yield {
        firstJobResult.map(_.id) shouldBe Some(first.id)
        secondJobResult.map(_.id) shouldBe Some(first.id)
        thirdJobResult.map(_.id) shouldBe Some(third.id)
      }
    }
  }

  "BackgroundJobJdbcRepository.maintainExpiredJobs" should {
    "change statuses of expired jobs" in {
      val timeout = 1.minute
      val timeoutForSub = timeout + 100.milli

      for {
        first <- insertJob(jobExample.copy(status = Status.Started, startedAtOpt = Some(nowDate.sub(timeoutForSub))))
        second <- insertJob(jobExample.copy(status = Status.Started))
        changedJobs <- repo.maintainExpiredJobs(timeout, nowDate)
        dbContent <- db.run(tables.backgroundJobs.result)
      } yield {
        changedJobs shouldBe 1

        dbContent.map(_.copy(error = "")) should contain theSameElementsAs Seq(
          first.copy(status = Status.Failed, finishedAtOpt = Some(nowDate)),
          second
        )
      }
    }
  }

  "BackgroundJobJdbcRepository.tryMarkJobAsStarted" should {
    "change job status to started" in {
      val updateDate = nowDate.add(1.minute)
      for {
        first <- insertJob(jobExample)
        dummy <- insertJob(jobExample)
        isChanged <- repo.tryMarkJobAsStarted(first.id, 1, updateDate, 1, nowDate, 1.minute)
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

    "change job status to started if it is failed, tries are available, and required delay is reached" in {
      val updateDate = nowDate.add(1.minute)
      for {
        first <- insertJob(failedJobExample.copy(tryCount = 98, finishedAtOpt = Some(nowDate.sub(61.seconds))))
        dummy <- insertJob(jobExample)
        isChanged <- repo.tryMarkJobAsStarted(first.id, 1, updateDate, 99, nowDate, 1.minute)
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
      "job has wrong status" in {
        val updateDate = nowDate.add(1.minute)

        for {
          alreadyStarted <- insertJob(jobExample.copy(status = Status.Started, startedAtOpt = Some(updateDate)))
          alreadySucceeded <- insertJob(jobExample.copy(status = Status.Succeeded, startedAtOpt = Some(updateDate)))
          dummy <- insertJob(jobExample.copy(status = Status.Started, startedAtOpt = Some(updateDate)))
          alreadyStartedIsChanged <- repo.tryMarkJobAsStarted(alreadyStarted.id, 1, updateDate, 1, nowDate, 1.minute)
          alreadySucceededIsChanged <- repo.tryMarkJobAsStarted(
            alreadySucceeded.id,
            1,
            updateDate,
            1,
            nowDate,
            1.minute
          )
          dbContent <- db.run(tables.backgroundJobs.result)
        } yield {
          alreadyStartedIsChanged shouldBe false
          alreadySucceededIsChanged shouldBe false

          dbContent should contain theSameElementsAs Seq(alreadyStarted, alreadySucceeded, dummy)
        }
      }

      "job is pending but should bot be started" in {
        val updateDate = nowDate.add(1.minute)
        for {
          pending <- insertJob(jobExample.copy(shouldRunAt = updateDate))
          dummy <- insertJob(jobExample)
          isChanged <- repo.tryMarkJobAsStarted(pending.id, 1, updateDate, 1, nowDate, 1.minute)
          dbContent <- db.run(tables.backgroundJobs.result)
        } yield {
          isChanged shouldBe false

          dbContent should contain theSameElementsAs Seq(pending, dummy)
        }
      }

      "job is failed and max try limit is reached" in {
        val updateDate = nowDate.add(1.minute)
        for {
          failed <- insertJob(failedJobExample.copy(tryCount = 100))
          dummy <- insertJob(jobExample)
          isChanged <- repo.tryMarkJobAsStarted(failed.id, 1, updateDate, 99, nowDate, 1.minute)
          dbContent <- db.run(tables.backgroundJobs.result)
        } yield {
          isChanged shouldBe false

          dbContent should contain theSameElementsAs Seq(failed, dummy)
        }
      }

      "job is failed but no required delay is reached" in {
        val updateDate = nowDate.add(1.minute)
        for {
          failed <- insertJob(failedJobExample.copy(finishedAtOpt = Some(nowDate.sub(59.seconds))))
          dummy <- insertJob(jobExample)
          isChanged <- repo.tryMarkJobAsStarted(failed.id, 1, updateDate, 1, nowDate, 1.minute)
          dbContent <- db.run(tables.backgroundJobs.result)
        } yield {
          isChanged shouldBe false

          dbContent should contain theSameElementsAs Seq(failed, dummy)
        }
      }

      "job is already succeeded" in {
        val updateDate = nowDate.add(1.minute)

        for {
          first <- insertJob(jobExample.copy(status = Status.Succeeded, startedAtOpt = Some(updateDate)))
          isChanged <- repo.tryMarkJobAsStarted(first.id, 1, updateDate, 1, nowDate, 1.minute)
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

  "BackgroundJobJdbcRepository.deleteJobsSucceededOrFailedBefore" should {
    "delete jobs that satisfy conditions" in {
      val boundDate = nowDate.add(1.minute)
      val futureDate = nowDate.add(2.minute)

      val j1 = jobExample.copy(finishedAtOpt = Some(nowDate)) // wrong status
      val j2 = jobExample.copy(finishedAtOpt = Some(nowDate), status = Status.Started) // wrong status
      val j3 = failedJobExample.copy(finishedAtOpt = Some(nowDate))
      val j4 = succeededJobExample.copy(finishedAtOpt = Some(nowDate))

      val j5 = jobExample.copy(finishedAtOpt = Some(futureDate)) // wrong date
      val j6 = failedJobExample.copy(finishedAtOpt = Some(futureDate), status = Status.Started) // wrong date
      val j7 = failedJobExample.copy(finishedAtOpt = Some(futureDate)) // wrong date
      val j8 = succeededJobExample.copy(finishedAtOpt = Some(futureDate)) // wrong date

      for {
        j1Ins <- insertJob(j1)
        j2Ins <- insertJob(j2)
        _ <- insertJob(j3)
        _ <- insertJob(j4)
        j5Ins <- insertJob(j5)
        j6Ins <- insertJob(j6)
        j7Ins <- insertJob(j7)
        j8Ins <- insertJob(j8)
        removedCount <- repo.deleteJobsSucceededOrFailedBefore(boundDate)
        dbContent <- db.run(tables.backgroundJobs.result)
      } yield {
        removedCount shouldBe 2

        dbContent.map(_.id) should contain theSameElementsAs Seq(j1Ins, j2Ins, j5Ins, j6Ins, j7Ins, j8Ins).map(_.id)
      }
    }
  }

  private def insertJob(job: BackgroundJob): Future[BackgroundJob] = {
    db
      .run((tables.backgroundJobs returning tables.backgroundJobs.map(_.id)) += job)
      .map(insertedId => job.copy(id = insertedId))
  }
}
