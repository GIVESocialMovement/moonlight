package givers.moonlight

import givers.moonlight.util.DateTimeFactory
import givers.moonlight.v2.repository.BackgroundJobRepository
import helpers.SimpleJobExecutor
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date
import scala.concurrent.Future

class BackgroundJobServiceImplSpec extends AsyncWordSpecLike with Matchers with AsyncIdiomaticMockito {

  "BackgroundJobServiceImpl.enqueue" should {
    "create now background job" in {
      val repo = mock[BackgroundJobRepository]
      val dateTimeFactory = mock[DateTimeFactory]
      val service = new BackgroundJobServiceImpl(repo, dateTimeFactory)

      val jobData = SimpleJobExecutor.JobData("some work")
      val jobType = SimpleJobExecutor.jobType
      val jobDesc = BackgroundJobDescription(jobType, jobData)
      val now = Date.from(ZonedDateTime.of(2022, 6, 27, 13, 46, 10, 4, ZoneOffset.UTC).toInstant)
      val shouldRunAt = Date.from(ZonedDateTime.of(2022, 6, 28, 0, 0, 0, 0, ZoneOffset.UTC).toInstant)
      val priority = 5

      val expectedBackgroundJob = BackgroundJob(
        id = -1,
        createdAt = now,
        shouldRunAt = shouldRunAt,
        initiatedAtOpt = None,
        startedAtOpt = None,
        finishedAtOpt = None,
        status = BackgroundJob.Status.Pending,
        error = "",
        tryCount = 0,
        jobType = jobType.id,
        paramsInJsonString = """{"data":"some work"}""",
        priority = priority
      )

      val expectedCreatedBackgroundJob = expectedBackgroundJob.copy(id = 998)

      repo.enqueue(expectedBackgroundJob) returns Future.successful(expectedCreatedBackgroundJob)
      dateTimeFactory.now returns now

      service.enqueue(jobDesc, Some(shouldRunAt), BackgroundJobPriority.HIGH).map { result =>
        result shouldBe expectedCreatedBackgroundJob
      }
    }
  }
}
