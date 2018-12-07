package givers.moonlight

import java.util.concurrent.TimeUnit

import play.api.libs.json.{Json, OFormat}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


abstract class Worker[Param <: Job](implicit jsonFormat: OFormat[Param]) {

  def run(job: BackgroundJob): Unit = {
    run(
      param = jsonFormat.reads(Json.parse(job.paramsInJsonString)).get,
      job = job
    )
  }

  def run(param: Param, job: BackgroundJob): Unit
}
