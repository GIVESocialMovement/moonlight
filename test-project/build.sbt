name := """test-project"""
organization := "givers.moonlight"

version := "1.0-SNAPSHOT"

//lazy val moonlight = RootProject(file(".."))
lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  //.aggregate(moonlight)
  //.dependsOn(moonlight)

scalaVersion := "2.12.11"

libraryDependencies ++= Seq(
  ws,
  guice,
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
  "org.postgresql" % "postgresql" % "42.2.14",
  "io.github.givesocialmovement" %% "play-moonlight" % "0.16.2"
)

Compile / herokuAppName := Option(sys.props("herokuAppName")).filter(_.nonEmpty).getOrElse("moonlight-test")
Compile / herokuJdkVersion := "1.8"
