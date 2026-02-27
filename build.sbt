import sbt.Keys.{name, publishTo, version}
import sbt.Test
import xerial.sbt.Sonatype.sonatypeCentralHost

Global / scalaVersion := "2.13.18"

Global / organization := "io.github.givesocialmovement"
Global / organizationName := "give.asia"
Global / organizationHomepage := Some(url("https://give.asia"))

lazy val macros = (project in file("macros"))
  .settings(
    version := "1.2.0",
    name := "play-moonlight-macros",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      Dependencies.quartz
    ),
    publishMavenStyle := true,
    Test / publishArtifact := false
  )

lazy val moonlight = (project in file("."))
  .settings(
    name := "play-moonlight",
    version := "1.2.0",
    pomIncludeRepository := { _ => false },
    publishMavenStyle := true
  )
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

Global / description := "Background job scheduler"
Global / licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

testFrameworks += new TestFramework("utest.runner.Framework")

/**
 * see [[https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html]]
 */

// Remove all additional repository other than Maven Central from POM
pomIncludeRepository := { _ => false }
publishMavenStyle := true

Test / publishArtifact := false

sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

coverageFailOnMinimum := false
coverageMinimumStmtTotal := 70
coverageMinimumBranchTotal := 70
coverageMinimumStmtPerPackage := 70
coverageMinimumBranchPerPackage := 70
coverageMinimumStmtPerFile := 70
coverageMinimumBranchPerFile := 70
coverageExcludedFiles := ".*MoonlightApplication;.*MoonlightSettings"
