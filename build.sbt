import sbt.Keys.name
import sbt.Test

Global / scalaVersion := "3.7.2"

Global / organization := "io.github.givesocialmovement"

name := "play-moonlight"

version := "2.0.0"
versionScheme := Some("semver-spec")

lazy val moonlight = project in file(".")

libraryDependencies ++= Seq(
  "org.playframework" %% "play-slick" % "6.2.0" withSources () withJavadoc (),
  "com.typesafe.play" %% "play-guice" % "2.9.7" withSources () withJavadoc (),
  "com.google.inject" % "guice" % "5.1.0",
  "org.postgresql" % "postgresql" % "42.7.7" withSources () withJavadoc (),
  "org.playframework" %% "play-json" % "3.0.5",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.4",
  "io.dropwizard" % "dropwizard-metrics-graphite" % "2.1.4",
  Dependencies.quartz,
  "org.playframework" %% "play-slick-evolutions" % "6.2.0" % Test,
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.lihaoyi" %% "utest" % "0.7.10" % Test,
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.17.2" % Test,
  "com.h2database" % "h2" % "2.1.210" % Test,
  "org.scalatestplus" %% "mockito-5-18" % "3.2.19.0" % Test,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

Test / parallelExecution := false

bintrayOrganization := Some("givers")

bintrayRepository := "maven"

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

pomIncludeRepository := { _ => false }

Global / homepage := Some(url("https://github.com/GIVESocialMovement/moonlight"))

Global / scmInfo := Some(
  ScmInfo(
    url("https://github.com/GIVESocialMovement/moonlight"),
    "scm:git@github.com:GIVESocialMovement/moonlight.git"
  )
)

Global / developers := List(
  Developer(id = "tanin", name = "tanin", email = "developers@giveasia.org", url = url("https://github.com/tanin47"))
)

Global / licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

testFrameworks += new TestFramework("utest.runner.Framework")

publishMavenStyle := true

Test / publishArtifact := false

publishTo := sonatypePublishToBundle.value

coverageFailOnMinimum := false
coverageMinimumStmtTotal := 70
coverageMinimumBranchTotal := 70
coverageMinimumStmtPerPackage := 70
coverageMinimumBranchPerPackage := 70
coverageMinimumStmtPerFile := 70
coverageMinimumBranchPerFile := 70
coverageExcludedFiles := ".*MoonlightApplication;.*MoonlightSettings"
