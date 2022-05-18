name := """test-project"""
organization := "givers.moonlight"

version := "1.0-SNAPSHOT"

lazy val moonlight = RootProject(file(".."))
lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .aggregate(moonlight)
  .dependsOn(moonlight)

scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  ws,
  guice,
  "com.google.inject" % "guice" % "5.1.0",
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
  "org.postgresql" % "postgresql" % "42.2.14"
)

Compile / herokuAppName := Option(sys.props("herokuAppName")).filter(_.nonEmpty).getOrElse("moonlight-test")
Compile / herokuJdkVersion := "1.8"
