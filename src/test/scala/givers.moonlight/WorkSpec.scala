package givers.moonlight

import java.util.Date

import helpers._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import play.api.Application
import play.api.inject.Injector
import utest._

import scala.concurrent.Future

object WorkSpec extends BaseSpec {

  val tests = Tests {
    val injector = mock[Injector]
    val app = mock[Application]
    val config = Config(maxErrorCountToKillOpt = Some(10), timeoutInMillis = 60L * 60L * 1000L)
    val moonlight = new Moonlight(config, Seq(SimpleWorker, AmbiguousWorker), None)
    val backgroundJobService = mock[BackgroundJobService]
    val work = new Work(app, moonlight, backgroundJobService)
    val worker = mock[SimpleWorker]

    when(app.injector).thenReturn(injector)
    when(backgroundJobService.start(any())).thenReturn(Future(()))
    when(backgroundJobService.succeed(any())).thenReturn(Future(()))
    when(backgroundJobService.fail(any(), any())).thenReturn(Future(()))
    when(injector.instanceOf[SimpleWorker]).thenReturn(worker)

    "Get worker" - {
      "Succeed" - {
        assert(work.getWorker("Simple") == worker)
        verify(injector).instanceOf[SimpleWorker]
        ()
      }

      "Use previous identifier" - {
        assert(work.getWorker("PreviousSimple") == worker)
        verify(injector).instanceOf[SimpleWorker]
        ()
      }

      "Fail because of unknown identifier" - {
        val ex = intercept[Exception] { work.getWorker("Unknown") }
        assert(ex.getMessage.contains("Unrecognized job type"))
        verifyZeroInteractions(injector)
        ()
      }

      "Fail because of ambiguity" - {
        val ex = intercept[Exception] { work.getWorker("Ambiguous") }
        assert(ex.getMessage.contains("Ambiguous job type"))
        verifyZeroInteractions(injector)
        ()
      }
    }

    "Run one job" - {
      val job = BackgroundJob(
        id = 1L,
        createdAt = new Date(),
        shouldRunAt = new Date(),
        initiatedAtOpt = None,
        startedAtOpt = None,
        finishedAtOpt = None,
        status = BackgroundJob.Status.Initiated,
        error = "",
        tryCount = 0,
        jobType = "Simple",
        paramsInJsonString = """{"data": "something"}"""
      )
      when(backgroundJobService.getById(any())).thenReturn(Future(Some(job)))

      "Get, run, and succeed" - {
        work.runJob(job.id)

        verify(worker).run(job)
        verify(backgroundJobService).getById(job.id)
        verify(backgroundJobService).start(job.id)
        verify(backgroundJobService).succeed(job.id)
        verifyNoMoreInteractions(backgroundJobService)
      }

      "Get, run, and fail" - {
        when(worker.run(any())).thenAnswer(new Answer[Unit] {
          override def answer(invocation: InvocationOnMock) = throw new Exception("FakeError")
        })

        intercept[Exception] {
          work.runJob(job.id)
        }

        verify(worker).run(job)
        verify(backgroundJobService).getById(job.id)
        verify(backgroundJobService).start(job.id)
        verify(backgroundJobService).fail(eq(job.id), argThat { e: Throwable => e.getMessage == "FakeError" })
        verifyNoMoreInteractions(backgroundJobService)
      }

      "InterruptedException occurs" - {
        when(worker.run(any())).thenAnswer(new Answer[Unit] {
          override def answer(invocation: InvocationOnMock) = throw new InterruptedException()
        })
        when(backgroundJobService.get()).thenReturn(Future(Some(job)))

        intercept[InterruptedException] {
          work.runJob(job.id)
        }

        verify(worker).run(job)
        verify(backgroundJobService).getById(job.id)
        verify(backgroundJobService).start(job.id)
        verifyNoMoreInteractions(backgroundJobService)
      }
    }
  }
}
