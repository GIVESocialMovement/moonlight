package controllers

import java.util.Date

import givers.moonlight.BackgroundJobService
import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.data.Form
import play.api.data.Forms._
import worker.SimpleWorkerSpec

import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject()(
  backgroundJobService: BackgroundJobService,
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext) extends AbstractController(controllerComponents)  {

  def index = Action.async {
    backgroundJobService.getAll(10).map { jobs =>
      Ok(views.html.index(jobs))
    }
  }

  case class AddParam(data: String, priority: Int)

  val addForm = Form(
    mapping(
      "data" -> text,
      "priority" -> number
    )(AddParam.apply)(AddParam.unapply)
  )

  def add = Action.async { implicit req =>
    addForm.bindFromRequest.fold(
      hasErrors = { error =>
        throw new Exception("Invalid form data: " + error)
      },
      success = { param =>
        backgroundJobService
          .queue(
            shouldRunAt = new Date(System.currentTimeMillis() + 10000),
            priority = param.priority,
            param = SimpleWorkerSpec.Job(param.data)
          )
          .map { _ => Redirect("/") }
      }
    )
  }
}
