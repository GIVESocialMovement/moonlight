Moonlight
==========

[![CircleCI](https://circleci.com/gh/GIVESocialMovement/moonlight/tree/master.svg?style=shield)](https://circleci.com/gh/GIVESocialMovement/moonlight/tree/master)
[![codecov](https://codecov.io/gh/GIVESocialMovement/moonlight/branch/master/graph/badge.svg)](https://codecov.io/gh/GIVESocialMovement/moonlight)

Moonlight is a simple delayed job (or, in other words, background job) framework for Playframework on Heroku.

Here are important notes:
* Moonlight uses Heroku's Postgresql through Slick. This requires you to configure Slick correctly.
* No concurrency. No lock. Moonlight can only have one worker.
* Support retrying up to 3 times. Retry happens one hour later after a failure.
* Use JSON to serialize/deserialize job's params.

At [GIVE.asia](https://give.asia), we've built Moonlight because we wanted a delayed job framework for moderate load.
At the same time, we want to avoid introducing a new datastore to our system.

We are using Playframework on Heroku with Postgresql (through Slick), so Moonlight fits perfectly into our setting.


Usage
------

The usage requires some degree of involvement.

Please see a full working example in the folder `test-project` and the live demo here: https://moonlight-test.herokuapp.com.
(The worker dyno isn't free, so it's off. Therefore, the job will be added but not processed).


### 1. Setup Postgresql's table

We've provided a set of SQLs for creating/updating required schemas. Please see `conf/evolutions/default/1.sql`.

Please apply the SQLs to your database.


### 2. Include Moonlight in your build.sbt

```
resolvers += Resolver.bintrayRepo("givers", "maven")
libraryDependencies += "givers.moonlight" %% "play-moonlight" % "0.1.1"
```

### 3. Create a worker

You can define a delayed job by providing `WorkerSpec` which allows you to specify params and job runner. Here's an example:

```
import com.google.inject.{Inject, Singleton}
import givers.moonlight.{BackgroundJob, Worker, WorkerSpec}
import play.api.Logger
import play.api.libs.json.{Json, OFormat}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object SimpleWorkerSpec extends WorkerSpec {
  case class Job(userId: Long) extends givers.moonlight.Job

  type Data = Job
  type Runner = SimpleWorker

  implicit val classTag = ClassTag(classOf[SimpleWorker])
  implicit val jsonFormat: OFormat[Job] = Json.format[Job]

  val identifier = "Simple"  // Job's identifier (should be unique)
  val previousIdentifiers = Set.empty  // In case, you'd like to change identifier
}

@Singleton
class SimpleWorker @Inject()(
  // You may inject any needed dependency here
  userService: UserService
) extends Worker[SimpleWorkerSpec.Job] {

  private[this] val logger = Logger(this.getClass)

  def run(param: SimpleWorkerSpec.Job, job: BackgroundJob): Unit = {
    val user = userService.getById(param.userId)
    println(s"Process user (id=${user.id})")
  }
}
```


### 4. Install Moonlight's module

Create a module with defined `WorkerSpec`:

```
package modules

import givers.moonlight.Moonlight
import play.api.{Configuration, Environment}

class MoonlightModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration)  = Seq(
    bind[Moonlight].toInstance(new Moonlight(SimpleWorkerSpec))
  )
}
```

Then, install the module and configure Slick's database connection in `application.conf` by:

```
play.modules.enabled += "modules.MoonlightModule"

slick.dbs.default.db.properties.url="postgres://user:pass@localhost:5432/database"
```


### 5. Run Moonlight

You can run Moonlight locally with `sbt 'runMain givers.moonlight.Main dev'`.

On Heroku, you can run Moonlight by adding the below line to `Procfile`:

```
worker: ./target/universal/stage/bin/[your_app_name] -Dconfig.resource=application.conf -main moonlight.Main -- prod
```

Please see a full example in the folder `test-project`.


Interested in using the framework?
-----------------------------------

Please don't hesitate to ask questions by opening a Github issue. We want you to use Moonlight successfully.


Future improvement
-------------------

* Support more than one worker by implementing Postgresql-based mutual lock.
* Improve Moonlight API to prevent mistakes like defining a job but forgetting to register the runner.
* Reduce the verbosity of WorkerSpec. We should be able to use Macros to define `jsonFormat` and `classTag`.
