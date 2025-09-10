name := """test-project-v2"""
organization := "givers.moonlight"

version := "3.0-SNAPSHOT"

lazy val moonlight = RootProject(file(".."))
lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .aggregate(moonlight)
  .dependsOn(moonlight)

scalaVersion := "3.7.2"

run / fork := true

libraryDependencies ++= Seq(
  ws,
  guice,
  "com.google.inject" % "guice" % "5.1.0",
  "org.playframework" %% "play-slick" % "6.2.0",
  "org.playframework" %% "play-slick-evolutions" % "6.2.0",
  "org.postgresql" % "postgresql" % "42.7.7"
)
