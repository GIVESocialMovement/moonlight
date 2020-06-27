lazy val moonlight = project in file(".")

scalaVersion := "2.12.11"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "5.0.0" withSources() withJavadoc(),
  "com.typesafe.play" %% "play-guice" % "2.8.2" withSources() withJavadoc(),
  "org.postgresql" % "postgresql" % "42.2.14" withSources() withJavadoc(),
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0" % Test,
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.lihaoyi" %% "utest" % "0.6.3" % Test
)

organization := "givers.moonlight"
name := "play-moonlight"
version := "0.16.2"
parallelExecution in Test := false

publishMavenStyle := true

bintrayOrganization := Some("givers")

bintrayRepository := "maven"

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

testFrameworks += new TestFramework("utest.runner.Framework")
