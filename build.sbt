lazy val moonlight = project in file(".")

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "3.0.3" withSources() withJavadoc(),
  "com.typesafe.play" %% "play-guice" % "2.6.21" withSources() withJavadoc(),
  "org.postgresql" % "postgresql" % "42.2.5" withSources() withJavadoc(),
  "com.typesafe.play" %% "play-json" % "2.6.13",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.3" % Test,
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.lihaoyi" %% "utest" % "0.6.3" % Test
)

organization := "givers.moonlight"
name := "play-moonlight"
version := "0.3.0"
parallelExecution in Test := false

publishMavenStyle := true

bintrayOrganization := Some("givers")

bintrayRepository := "maven"

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

testFrameworks += new TestFramework("utest.runner.Framework")
