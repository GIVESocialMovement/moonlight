import sbt.Keys.name
import sbt.Test

Global / scalaVersion := "2.13.18"

Global / organization := "io.github.givesocialmovement"

name := "play-moonlight"

version := "1.2.0"

lazy val macros = (project in file("macros"))
  .settings(
    version := "1.2.0",
    name := "play-moonlight-macros",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      Dependencies.quartz
    ),
    publishMavenStyle := true,
    Test / publishArtifact := false,
    publishTo := sonatypePublishToBundle.value
  )

lazy val moonlight = (project in file("."))
  .dependsOn(macros)

libraryDependencies ++= Seq(
  "org.playframework" %% "play-slick" % "6.2.0",
  "org.playframework" %% "play-slick-evolutions" % "6.2.0" withSources () withJavadoc (),
  "org.playframework" %% "play-guice" % "3.0.10" withSources () withJavadoc (),
  "com.google.inject" % "guice" % "6.0.0",
  "org.postgresql" % "postgresql" % "42.7.7" withSources () withJavadoc (),
  "org.playframework" %% "play-json" % "3.0.6",
  "io.netty" % "netty-common" % "4.1.76.Final",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.4",
  "io.dropwizard" % "dropwizard-metrics-graphite" % "2.1.4",
  Dependencies.quartz,
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.lihaoyi" %% "utest" % "0.7.10" % Test,
  "org.mockito" %% "mockito-scala-scalatest" % "1.17.5" % Test,
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.17.2" % Test,
  "com.h2database" % "h2" % "2.1.210" % Test,
  "org.scalatest" %% "scalatest" % "3.2.11" % Test
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
