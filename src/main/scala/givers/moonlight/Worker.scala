package givers.moonlight

import java.util.concurrent.TimeUnit

import play.api.libs.json.{Json, OFormat}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


abstract class Worker[Param <: Job](implicit jsonFormat: OFormat[Param]) {

  val DEFAULT_FUTURE_TIMEOUT = Duration.apply(30, TimeUnit.SECONDS)

  protected[this] def await[T](future: Future[T]): T = Await.result(future, DEFAULT_FUTURE_TIMEOUT)

  def run(job: BackgroundJob): Unit = {
    run(
      param = jsonFormat.reads(Json.parse(job.paramsInJsonString)).get,
      job = job
    )
  }

  def run(param: Param, job: BackgroundJob): Unit
}
