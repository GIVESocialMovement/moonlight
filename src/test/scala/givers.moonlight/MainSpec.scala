package givers.moonlight

import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

import helpers._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import play.api.Application
import play.api.inject.Injector
import utest._

import scala.concurrent.Future

object MainSpec extends BaseSpec {

  val tests = Tests {
    val injector = mock[Injector]
    val app = mock[Application]
    val moonlight = new Moonlight(SimpleWorker, AmbiguousWorker)
    val backgroundJobService = mock[BackgroundJobService]
    val main = new Main(app, moonlight, backgroundJobService, { t => () })
    val worker = mock[SimpleWorker]
    val running = new AtomicBoolean(true)

    when(app.injector).thenReturn(injector)
    when(backgroundJobService.updateTimeoutJobs()).thenReturn(Future(()))
    when(backgroundJobService.start(any(), any())).thenReturn(Future(()))
    when(backgroundJobService.succeed(any())).thenReturn(Future(()))
    when(backgroundJobService.fail(any(), any())).thenReturn(Future(()))
    when(injector.instanceOf[SimpleWorker]).thenReturn(worker)

    "Get worker" - {
      "Succeed" - {
        assert(main.getWorker("Simple") == worker)
        verify(injector).instanceOf[SimpleWorker]
        ()
      }

      "Use previous identifier" - {
        assert(main.getWorker("PreviousSimple") == worker)
        verify(injector).instanceOf[SimpleWorker]
        ()
      }

      "Fail because of unknown identifier" - {
        val ex = intercept[Exception] { main.getWorker("Unknown") }
        assert(ex.getMessage.contains("Unrecognized job type"))
        verifyZeroInteractions(injector)
        ()
      }

      "Fail because of ambiguity" - {
        val ex = intercept[Exception] { main.getWorker("Ambiguous") }
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
        startedAtOpt = None,
        finishedAtOpt = None,
        status = BackgroundJob.Status.Pending,
        error = "",
        tryCount = 0,
        jobType = "Simple",
        paramsInJsonString = """{"data": "something"}"""
      )

      "Get, run, and succeed" - {
        when(backgroundJobService.get()).thenReturn(Future(Some(job)))

        main.runOneJob(running)

        assert(running.get())

        verify(worker).run(job)
        verify(backgroundJobService).updateTimeoutJobs()
        verify(backgroundJobService).get()
        verify(backgroundJobService).start(job.id, 1)
        verify(backgroundJobService).succeed(job.id)
        verifyNoMoreInteractions(backgroundJobService)
      }

      "Get, run, and fail" - {
        when(worker.run(any())).thenAnswer(new Answer[Unit] {
          override def answer(invocation: InvocationOnMock) = throw new Exception("FakeError")
        })
        when(backgroundJobService.get()).thenReturn(Future(Some(job)))

        main.runOneJob(running)

        assert(running.get())

        verify(worker).run(job)
        verify(backgroundJobService).updateTimeoutJobs()
        verify(backgroundJobService).get()
        verify(backgroundJobService).start(job.id, 1)
        verify(backgroundJobService).fail(eq(job.id), argThat { e: Throwable => e.getMessage == "FakeError" })
        verifyNoMoreInteractions(backgroundJobService)
      }

      "No job" - {
        when(backgroundJobService.get()).thenReturn(Future(None))

        main.runOneJob(running)

        assert(running.get())

        verify(backgroundJobService).updateTimeoutJobs()
        verify(backgroundJobService).get()
        verifyNoMoreInteractions(backgroundJobService)
      }

      "InterruptedException occurs" - {
        when(worker.run(any())).thenAnswer(new Answer[Unit] {
          override def answer(invocation: InvocationOnMock) = throw new InterruptedException()
        })
        when(backgroundJobService.get()).thenReturn(Future(Some(job)))

        main.runOneJob(running)

        assert(!running.get())

        verify(worker).run(job)
        verify(backgroundJobService).updateTimeoutJobs()
        verify(backgroundJobService).get()
        verify(backgroundJobService).start(job.id, 1)
        verifyNoMoreInteractions(backgroundJobService)
      }

      "General error occurs" - {
        when(backgroundJobService.get()).thenReturn(Future.failed(new Exception()))

        main.runOneJob(running)

        assert(!running.get())

        verify(backgroundJobService).updateTimeoutJobs()
        verify(backgroundJobService).get()
        verifyNoMoreInteractions(backgroundJobService)
      }
    }
  }
}
