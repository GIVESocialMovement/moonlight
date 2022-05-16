package givers.moonlight

import givers.moonlight.persistence.table.postgres.PgBackgroundJobTableComponent

import java.util.Date
import helpers.{AnotherWorker, BaseSpec, SimpleWorker}
import utest._

object BackgroundJobServiceSpec extends BaseSpec {

  val tc = new PgBackgroundJobTableComponent

  val ONE_HOUR_IN_MILLIS = 3600L * 1000L
  val TEN_MINUTES_IN_MILLIS = 60L * 10L * 1000L

  val tests = Tests {
    resetDatabase()
    val moonlight = new Moonlight(
      config = Config(None, 3000, 3000),
      workers = Seq(SimpleWorker),
      startJobOpt = None,
      canStartJobOpt = None
    )
    val service = new BackgroundJobService(moonlight, dbConfigProvider)

    "Queue a job and get" - {
      assert(await(service.get()).isEmpty)

      val shouldRunAt = new Date(System.currentTimeMillis() - 1L)

      // AnotherWorker is absent in supportedWorkerTypes so should be ignored
      await(service.queue(shouldRunAt, 1, AnotherWorker.Job("some work")))
      assert(await(service.get()).isEmpty)

      val low = await(service.queue(shouldRunAt, 1, SimpleWorker.Job("some work")))
      val high = await(service.queue(shouldRunAt, 0, SimpleWorker.Job("some work")))

      val fetched = await(service.get())
      val list = await(service.getAll(100))
      assert(
        high.jobType == SimpleWorker.identifier,
        high.status == BackgroundJob.Status.Pending,
        high.tryCount == 0,
        high.priority == 0,
        high.shouldRunAt == shouldRunAt,
        high.paramsInJsonString == """{"data":"some work"}""",
        fetched.contains(high),
        list == Seq(high, low)
      )
    }

    "Get a failed job after one hour" - {
      val created = await(service.queue(new Date(System.currentTimeMillis() - 1000L), 0, SimpleWorker.Job("some work")))
      await(service.fail(created.id, new Exception("Fake error")))

      assert(await(service.get()).isEmpty)
      updateFinishedAtOpt(created.id, Some(new Date(System.currentTimeMillis() - (ONE_HOUR_IN_MILLIS + 1))))

      assert(await(service.get()).map(_.id).contains(created.id))
    }

    "Queue a job in the far future" - {
      await(service.queue(new Date(System.currentTimeMillis() + 100000L), 0, SimpleWorker.Job("some work")))
      assert(await(service.get()).isEmpty)
    }

    "Initiate" - {
      val someOtherTask = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))
      val created = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))

      await(service.initiate(created.id, 12))

      val started = await(service.getById(created.id)).get
      assert(
        started.status == BackgroundJob.Status.Initiated,
        started.tryCount == 12,
        started.initiatedAtOpt.exists(_.getTime >= created.createdAt.getTime),
        started.startedAtOpt.isEmpty,
        started.finishedAtOpt.isEmpty,
        await(service.getById(someOtherTask.id)).contains(someOtherTask)
      )
    }

    "Start" - {
      val someOtherTask = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))
      val created = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))

      await(service.initiate(created.id, 12))
      await(service.start(created.id))

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
      val someOtherTask = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))
      val created = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))

      await(service.initiate(created.id, 12))
      await(service.start(created.id))
      await(service.fail(created.id, new Exception("Fake error")))

      val failed = await(service.getById(created.id)).get
      assert(
        failed.status == BackgroundJob.Status.Failed,
        failed.finishedAtOpt.exists(_.getTime >= created.createdAt.getTime),
        await(service.getById(someOtherTask.id)).contains(someOtherTask)
      )
    }

    "Succeed" - {
      val someOtherTask = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))
      val created = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))

      await(service.initiate(created.id, 12))
      await(service.start(created.id))
      await(service.succeed(created.id))

      val succeeded = await(service.getById(created.id)).get
      assert(
        succeeded.status == BackgroundJob.Status.Succeeded,
        succeeded.finishedAtOpt.exists(_.getTime >= created.createdAt.getTime),
        await(service.getById(someOtherTask.id)).contains(someOtherTask)
      )
    }

    "Update timeout started jobs" - {
      val timedOut = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))
      val notTimedOut = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))
      val notStarted = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))

      await(service.initiate(timedOut.id, 1))
      await(service.initiate(notTimedOut.id, 1))

      await(service.start(timedOut.id))
      await(service.start(notTimedOut.id))

      updateStartedAtOpt(timedOut.id, Some(new Date(System.currentTimeMillis() - 2L * ONE_HOUR_IN_MILLIS)))  // 2 hours
      updateStartedAtOpt(notTimedOut.id, Some(new Date(System.currentTimeMillis() - (ONE_HOUR_IN_MILLIS / 2L))))  // 30 minutes

      await(service.updateTimeoutStartedJobs(ONE_HOUR_IN_MILLIS))

      val retrievedTimedOut = await(service.getById(timedOut.id)).get
      val retrievedNotTimedOut = await(service.getById(notTimedOut.id)).get
      assert(
        await(service.getById(notStarted.id)).contains(notStarted),
        retrievedTimedOut.status == BackgroundJob.Status.Failed,
        retrievedTimedOut.error == "Timeout (from Started)",
        retrievedTimedOut.finishedAtOpt.isDefined,
        retrievedNotTimedOut.status == BackgroundJob.Status.Started,
        retrievedNotTimedOut.error == "",
        retrievedNotTimedOut.finishedAtOpt.isEmpty
      )
    }

    "Update timeout initiated jobs" - {
      val timedOut = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))
      val notTimedOut = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))
      val notInitiated = await(service.queue(new Date(), 0, SimpleWorker.Job("some work")))


      await(service.initiate(timedOut.id, 1))
      await(service.initiate(notTimedOut.id, 1))

      updateInitiatedAtOpt(timedOut.id, Some(new Date(System.currentTimeMillis() - 2L * TEN_MINUTES_IN_MILLIS)))  // 20 minutes
      updateInitiatedAtOpt(notTimedOut.id, Some(new Date(System.currentTimeMillis() - (TEN_MINUTES_IN_MILLIS / 2L))))  // 5 minutes

      await(service.updateTimeoutInitiatededJobs(TEN_MINUTES_IN_MILLIS))

      val retrievedTimedOut = await(service.getById(timedOut.id)).get
      val retrievedNotTimedOut = await(service.getById(notTimedOut.id)).get
      assert(
        await(service.getById(notInitiated.id)).contains(notInitiated),
        retrievedTimedOut.status == BackgroundJob.Status.Pending,
        retrievedTimedOut.error == "Timeout (from Initiated)",
        retrievedTimedOut.finishedAtOpt.isDefined,
        retrievedNotTimedOut.status == BackgroundJob.Status.Initiated,
        retrievedNotTimedOut.error == "",
        retrievedNotTimedOut.finishedAtOpt.isEmpty
      )
    }
  }

  def updateInitiatedAtOpt(id: Long, initiatedAtOpt: Option[Date]): Unit = {
    import tc.profile.api._
    import tc._

    await(BaseSpec.db.run {
      tc.backgroundJobs.filter(_.id === id).map(_.initiatedAtOpt).update(initiatedAtOpt)
    })
  }

  def updateStartedAtOpt(id: Long, startedAtOpt: Option[Date]): Unit = {
    import tc.profile.api._
    import tc._

    await(BaseSpec.db.run {
      tc.backgroundJobs.filter(_.id === id).map(_.startedAtOpt).update(startedAtOpt)
    })
  }

  def updateFinishedAtOpt(id: Long, finishedAtOpt: Option[Date]): Unit = {
    import tc.profile.api._
    import tc._

    await(BaseSpec.db.run {
      tc.backgroundJobs.filter(_.id === id).map(_.finishedAtOpt).update(finishedAtOpt)
    })
  }
}
