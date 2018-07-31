package givers.moonlight

import java.util.Date

import helpers.{BaseSpec, SimpleWorker}
import utest._

object SimpleWorkerSpec extends BaseSpec {

  val tests = Tests {
    val job = BackgroundJob(
      id = 1L,
      createdAt = new Date(),
      shouldRunAt = new Date(),
      startedAtOpt = None,
      finishedAtOpt = None,
      status = BackgroundJob.Status.Pending,
      error = "",
      tryCount = 0,
      jobType = "Simple",
      paramsInJsonString = """{"data": "something"}"""
    )
    val worker = new SimpleWorker

    "Succeed" - {
      worker.run(job)
    }

    "Fail" - {
      val ex = intercept[Exception] { worker.run(job.copy(paramsInJsonString = """{"data":"error"}""")) }
      assert(ex.getMessage == "FakeError")
    }
  }
}
