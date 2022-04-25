name := """test-project-v2"""
organization := "givers.moonlight"

version := "2.0-SNAPSHOT"

lazy val moonlight = RootProject(file(".."))
lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .aggregate(moonlight)
  .dependsOn(moonlight)

scalaVersion := "2.13.8"

run / fork := true

libraryDependencies ++= Seq(
  ws,
  guice,
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
  "org.postgresql" % "postgresql" % "42.2.14"
)
