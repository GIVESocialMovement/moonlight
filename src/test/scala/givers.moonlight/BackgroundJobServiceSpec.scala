package givers.moonlight

import java.util.Date

import helpers.{BaseSpec, SimpleWorker}
import utest._

import scala.concurrent.Future

object BackgroundJobServiceSpec extends BaseSpec {

  val ONE_HOUR_IN_MILLIS = 3600L * 1000L

  val tests = Tests {
    resetDatabase()
    val service = new BackgroundJobService(dbConfigProvider)

    "Queue a job and get" - {
      assert(await(service.get()).isEmpty)

      val shouldRunAt = new Date(System.currentTimeMillis() - 1L)
      val created = await(service.queue(shouldRunAt, SimpleWorker.Job("some work")))

      val fetched = await(service.get())
      val list = await(service.getAll(100))
      assert(
        created.jobType == SimpleWorker.identifier,
        created.status == BackgroundJob.Status.Pending,
        created.tryCount == 0,
        created.shouldRunAt == shouldRunAt,
        created.paramsInJsonString == """{"data":"some work"}""",
        fetched.contains(created),
        list == Seq(created)
      )
    }

    "Get a failed job after one hour" - {
      val created = await(service.queue(new Date(System.currentTimeMillis() - 1000L), SimpleWorker.Job("some work")))
      await(service.fail(created.id, new Exception("Fake error")))

      assert(await(service.get()).isEmpty)
      updateFinishedAtOpt(created.id, Some(new Date(System.currentTimeMillis() - (ONE_HOUR_IN_MILLIS + 1))))

      assert(await(service.get()).map(_.id).contains(created.id))
    }

    "Queue a job in the far future" - {
      await(service.queue(new Date(System.currentTimeMillis() + 100000L), SimpleWorker.Job("some work")))
      assert(await(service.get()).isEmpty)
    }

    "Start" - {
      val someOtherTask = await(service.queue(new Date(), SimpleWorker.Job("some work")))
      val created = await(service.queue(new Date(), SimpleWorker.Job("some work")))

      await(service.start(created.id, 12))

      val started = await(service.getById(created.id)).get
      assert(
        started.status == BackgroundJob.Status.Started,
        started.tryCount == 12,
        started.startedAtOpt.exists(_.getTime >= created.createdAt.getTime),
        started.finishedAtOpt.isEmpty,
        await(service.getById(someOtherTask.id)).contains(someOtherTask)
      )
    }

    "Fail" - {
      val someOtherTask = await(service.queue(new Date(), SimpleWorker.Job("some work")))
      val created = await(service.queue(new Date(), SimpleWorker.Job("some work")))

      await(service.start(created.id, 12))
      await(service.fail(created.id, new Exception("Fake error")))

      val failed = await(service.getById(created.id)).get
      assert(
        failed.status == BackgroundJob.Status.Failed,
        failed.finishedAtOpt.exists(_.getTime >= created.createdAt.getTime),
        await(service.getById(someOtherTask.id)).contains(someOtherTask)
      )
    }

    "Succeed" - {
      val someOtherTask = await(service.queue(new Date(), SimpleWorker.Job("some work")))
      val created = await(service.queue(new Date(), SimpleWorker.Job("some work")))

      await(service.start(created.id, 12))
      await(service.succeed(created.id))

      val succeeded = await(service.getById(created.id)).get
      assert(
        succeeded.status == BackgroundJob.Status.Succeeded,
        succeeded.finishedAtOpt.exists(_.getTime >= created.createdAt.getTime),
        await(service.getById(someOtherTask.id)).contains(someOtherTask)
      )
    }

    "Update timeout jobs" - {
      val timedOut = await(service.queue(new Date(), SimpleWorker.Job("some work")))
      val notTimedOut = await(service.queue(new Date(), SimpleWorker.Job("some work")))
      val notStarted = await(service.queue(new Date(), SimpleWorker.Job("some work")))

      await(service.start(timedOut.id, 1))
      await(service.start(notTimedOut.id, 1))

      updateStartedAtOpt(timedOut.id, Some(new Date(System.currentTimeMillis() - 2L * ONE_HOUR_IN_MILLIS)))  // 2 hours
      updateStartedAtOpt(notTimedOut.id, Some(new Date(System.currentTimeMillis() - (ONE_HOUR_IN_MILLIS / 2L))))  // 30 minutes

      await(service.updateTimeoutJobs())

      val retrievedTimedOut = await(service.getById(timedOut.id)).get
      val retrievedNotTimedOut = await(service.getById(notTimedOut.id)).get
      assert(
        await(service.getById(notStarted.id)).contains(notStarted),
        retrievedTimedOut.status == BackgroundJob.Status.Failed,
        retrievedTimedOut.error == "Timeout",
        retrievedNotTimedOut.status == BackgroundJob.Status.Started,
        retrievedNotTimedOut.error == ""
      )
    }
  }

  def updateStartedAtOpt(id: Long, startedAtOpt: Option[Date]): Unit = {
    import BackgroundJob._
    import slick.jdbc.PostgresProfile.api._

    val query = TableQuery[BackgroundJobTable]
    await(BaseSpec.db.run {
      query.filter(_.id === id).map(_.startedAtOpt).update(startedAtOpt)
    })
  }

  def updateFinishedAtOpt(id: Long, finishedAtOpt: Option[Date]): Unit = {
    import BackgroundJob._
    import slick.jdbc.PostgresProfile.api._

    val query = TableQuery[BackgroundJobTable]
    await(BaseSpec.db.run {
      query.filter(_.id === id).map(_.finishedAtOpt).update(finishedAtOpt)
    })
  }
}
