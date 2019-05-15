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

object RunSpec extends BaseSpec {

  val tests = Tests {
    val injector = mock[Injector]
    val app = mock[Application]
    val config = Config(maxErrorCountToKillOpt = Some(10), timeoutInMillis = 1L * 60L * 60L * 1000L)
    val moonlight = new Moonlight(config, Seq.empty, None)
    val backgroundJobService = mock[BackgroundJobService]
    val work = mock[Work]
    val run = new Run(app, moonlight, backgroundJobService, work)
    run.sleep = { _ => () }
    val running = new AtomicBoolean(true)

    when(app.injector).thenReturn(injector)
    when(backgroundJobService.updateTimeoutStartedJobs(config.timeoutInMillis)).thenReturn(Future(()))
    when(backgroundJobService.updateTimeoutInitiatededJobs()).thenReturn(Future(()))
    when(backgroundJobService.start(any())).thenReturn(Future(()))
    when(backgroundJobService.initiate(any(), any())).thenReturn(Future(()))

    "Pick and run one job" - {
      val job = BackgroundJob(
        id = 1L,
        createdAt = new Date(),
        shouldRunAt = new Date(),
        initiatedAtOpt = None,
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

        run.pickAndRunJob(running)

        assert(running.get())

        verify(work).runJob(job.id)
        verify(backgroundJobService).updateTimeoutStartedJobs(config.timeoutInMillis)
        verify(backgroundJobService).updateTimeoutInitiatededJobs()
        verify(backgroundJobService).get()
        verify(backgroundJobService).initiate(job.id, 1)
        verifyNoMoreInteractions(backgroundJobService)
      }

      "Get, run, and fail" - {
        when(work.runJob(any())).thenAnswer(new Answer[Unit] {
          override def answer(invocation: InvocationOnMock) = throw new Exception("FakeError")
        })
        when(backgroundJobService.get()).thenReturn(Future(Some(job)))

        run.pickAndRunJob(running)

        running.get() ==> true
        run.errorCount.get ==> 1

        verify(work).runJob(job.id)
        verify(backgroundJobService).updateTimeoutStartedJobs(config.timeoutInMillis)
        verify(backgroundJobService).updateTimeoutInitiatededJobs()
        verify(backgroundJobService).get()
        verify(backgroundJobService).initiate(job.id, 1)
        verifyNoMoreInteractions(backgroundJobService)
      }

      "Fail too many times" - {
        when(work.runJob(any())).thenAnswer(new Answer[Unit] {
          override def answer(invocation: InvocationOnMock) = throw new Exception("FakeError")
        })
        when(backgroundJobService.get()).thenReturn(Future(Some(job)))

        0.to(8).foreach { _ =>
          run.pickAndRunJob(running)
        }
        running.get() ==> true

        run.pickAndRunJob(running)
        running.get() ==> false

        verify(work, times(10)).runJob(job.id)
        verify(backgroundJobService, times(10)).updateTimeoutStartedJobs(config.timeoutInMillis)
        verify(backgroundJobService, times(10)).updateTimeoutInitiatededJobs()
        verify(backgroundJobService, times(10)).get()
        verify(backgroundJobService, times(10)).initiate(job.id, 1)
        verifyNoMoreInteractions(backgroundJobService)
      }

      "No job" - {
        when(backgroundJobService.get()).thenReturn(Future(None))

        run.pickAndRunJob(running)

        running.get() ==> true

        verify(backgroundJobService).updateTimeoutStartedJobs(config.timeoutInMillis)
        verify(backgroundJobService).updateTimeoutInitiatededJobs()
        verify(backgroundJobService).get()
        verifyNoMoreInteractions(backgroundJobService)
      }

      "InterruptedException occurs" - {
        when(work.runJob(any())).thenAnswer(new Answer[Unit] {
          override def answer(invocation: InvocationOnMock) = throw new InterruptedException()
        })
        when(backgroundJobService.get()).thenReturn(Future(Some(job)))

        run.pickAndRunJob(running)

        running.get() ==> false

        verify(work).runJob(job.id)
        verify(backgroundJobService).updateTimeoutStartedJobs(config.timeoutInMillis)
        verify(backgroundJobService).updateTimeoutInitiatededJobs()
        verify(backgroundJobService).get()
        verify(backgroundJobService).initiate(job.id, 1)
        verifyNoMoreInteractions(backgroundJobService)
      }

      "General error occurs" - {
        when(backgroundJobService.get()).thenReturn(Future.failed(new Exception()))

        run.pickAndRunJob(running)

        running.get() ==> false

        verify(backgroundJobService).updateTimeoutStartedJobs(config.timeoutInMillis)
        verify(backgroundJobService).updateTimeoutInitiatededJobs()
        verify(backgroundJobService).get()
        verifyNoMoreInteractions(backgroundJobService)
      }
    }
  }
}
