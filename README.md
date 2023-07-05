Moonlight
==========

[![CircleCI](https://circleci.com/gh/GIVESocialMovement/moonlight/tree/master.svg?style=shield)](https://circleci.com/gh/GIVESocialMovement/moonlight/tree/master)
[![codecov](https://codecov.io/gh/GIVESocialMovement/moonlight/branch/master/graph/badge.svg)](https://codecov.io/gh/GIVESocialMovement/moonlight)

Moonlight is a simple delayed job (or, in other words, background job) framework for Playframework on Heroku.

Here are important notes:
* Moonlight uses Heroku's Postgresql through Slick. This requires you to configure Slick correctly.
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
libraryDependencies += "givers.moonlight" %% "play-moonlight" % "x.x.x"
```

The artifacts are hosted here: https://bintray.com/givers/maven/play-moonlight

### 3. Create a executor

You can define a delayed job by providing `WorkerSpec` which allows you to specify params and job runner. Here's an example:

```
@Singleton
class SimpleExecutor @Inject()(implicit ec: ExecutionContext)
    extends JobExecutor(SimpleExecutor.Type) {

  override def run(data: SimpleExecutor.Job): Future[Unit] = Future.successful(())
}

object SimpleExecutor {
  case class Job(data: String)

  implicit case object Type extends JobTypeJson[Job]("Simple")(Json.format)
}
```

### 4. Install Moonlight's module

Create a module with defined `SimpleExecutor`:

```
class MoonlightModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration)  = Seq(
    bind[MoonlightSettings].toInstance(new MoonlightSettings(
        parallelism = Runtime.getRuntime.availableProcessors(),
        pauseDurationWhenNoJobs = 10.seconds,
        maintenanceInterval = 1.minutes,
        betweenRunAttemptInterval = 10.minutes,
        countMetricsCollectionInterval = 1.minute,
        maxJobRetries = 3,
        jobRunTimeout = 10.seconds,
        completedJobsTtl = (24 * 30).hours,
        executors = Seq(simpleExecutor)
    ))
  )
}
```

Then, install the module and configure Slick's database connection in `application.conf` by:

```
play.modules.enabled += "modules.MoonlightModule"

slick.dbs.default.db.properties.url="postgres://user:pass@localhost:5432/database"
```


### 5. Run Moonlight

You can run Moonlight locally with `sbt 'runMain givers.moonlight.v2.MoonlightApplication dev'`.

On Heroku, you can run Moonlight by adding the below line to `Procfile`:

```
moonlight: target/universal/stage/bin/giveasia -Dconfig.resourceapplication.conf -main givers.moonlight.v2.MoonlightApplication -- prod
```

Please see a full example in the folder `test-project-v2`.


Interested in using the framework?
-----------------------------------

Please don't hesitate to ask questions by opening a Github issue. We want you to use Moonlight successfully.
