package givers.moonlight.scheduled.quartz

import com.codahale.metrics.MetricRegistry
import givers.moonlight.scheduled.CronSchedule.{*, given}
import givers.moonlight.scheduled.quartz.QuartzScheduler
import givers.moonlight.scheduled.quartz.QuartzCronExpression.*
import givers.moonlight.scheduled.{ScheduledJob, Scheduler, SchedulerInput}
import givers.moonlight.util.AsyncIdiomaticMockito
import givers.moonlight.v2.MoonlightSettings
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike
import play.api.inject.Injector

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class QuartzSchedulerSpec extends AsyncWordSpecLike with Matchers with AsyncIdiomaticMockito with BeforeAndAfter {

  val stopFn = new AtomicReference[Option[Scheduler.StopScheduler]](None)

  before {
    stopFn.set(None)
  }

  after {
    stopFn.get().foreach(stop => stop())
  }

  class SimpleScript()(implicit val executionContext: ExecutionContext) extends ScheduledJob[AtomicInteger] {
    override def run(input: AtomicInteger): Future[Unit] = {
      input.incrementAndGet()
      Future.unit
    }
  }

  "ScriptProcessorImpl.run" should {
    "run jobs" in {
      val ref = new AtomicInteger(0)

      val injector: Injector = mock[Injector]
      val in = SchedulerInput(ref, cronExpression("* * * * * ? *"), 1.minute).forJob[SimpleScript]

      injector.instanceOf(classOf[SimpleScript]) returns new SimpleScript

      val settings = MoonlightSettings(
        parallelism = 1,
        pauseDurationWhenNoJobs = 1.minute,
        maintenanceInterval = 1.hour,
        countMetricsCollectionInterval = 5.seconds,
        betweenRunAttemptInterval = 30.minutes,
        maxJobRetries = 3,
        jobRunTimeout = 1.second,
        completedJobsTtl = 90.days,
        executors = Seq.empty,
        schedulerInputs = Seq(in)
      )

      val processor = new QuartzScheduler(settings, new MetricRegistry, injector)

      stopFn.set(Some(processor.run()))

      eventually {
        ref.get() should be > 0
      }
    }
  }
}
