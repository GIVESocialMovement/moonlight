package givers.moonlight

import play.api.libs.json.{Json, OFormat}

import scala.concurrent.Future


abstract class Worker[Param <: Job](implicit val jsonFormat: OFormat[Param]) {

  def run(job: BackgroundJob): Unit = {
    run(
      param = jsonFormat.reads(Json.parse(job.paramsInJsonString)).get,
      job = job
    )
  }

  def run(param: Param, job: BackgroundJob): Unit
}

trait AsyncSupport[JobData <: Job] {
  implicit val jsonFormat: OFormat[JobData]

  def runAsync(job: BackgroundJob): Future[Unit] = {
    runAsync(
      job = job,
      data = jsonFormat.reads(Json.parse(job.paramsInJsonString)).get
    )
  }

  def runAsync(job: BackgroundJob, data: JobData): Future[Unit]
}
