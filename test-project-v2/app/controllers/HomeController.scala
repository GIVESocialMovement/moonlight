package controllers

import executors.SimpleExecutor
import givers.moonlight.BackgroundJob
import givers.moonlight.v2.repository.BackgroundJobRepository

import java.util.Date
import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.api.data.Form
import play.api.data.Forms._

import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject()(
                                repo: BackgroundJobRepository,
                                controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext) extends AbstractController(controllerComponents)  {

  def index: Action[AnyContent] = Action.async {
    repo.getJobs(0, 10).map { jobs =>
      Ok(views.html.index(jobs))
    }
  }

  case class AddParam(data: String, priority: Int)

  val addForm: Form[AddParam] = Form(
    mapping(
      "data" -> text,
      "priority" -> number
    )(AddParam.apply)(AddParam.unapply)
  )

  def add: Action[AnyContent] = Action.async { implicit req =>
    addForm.bindFromRequest().fold(
      hasErrors = { error =>
        throw new Exception("Invalid form data: " + error)
      },
      success = { param =>
        repo
          .enqueue(BackgroundJob(
            id = -1,
            createdAt = new Date(),
            shouldRunAt = new Date(System.currentTimeMillis() + 10000),
            initiatedAtOpt = None,
            startedAtOpt = None,
            finishedAtOpt = None,
            status = BackgroundJob.Status.Pending,
            error = "",
            tryCount = 0,
            jobType = SimpleExecutor.Type.id,
            paramsInJsonString = SimpleExecutor.Type.serDe.serialize(SimpleExecutor.Job(param.data)),
            priority = param.priority
          ))
          .map { _ => Redirect("/") }
      }
    )
  }
}
