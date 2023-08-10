lazy val moonlight = project in file(".")

scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "5.0.0" withSources () withJavadoc (),
  "com.typesafe.play" %% "play-guice" % "2.8.15" withSources () withJavadoc (),
  "com.google.inject" % "guice" % "5.1.0",
  "org.postgresql" % "postgresql" % "42.2.14" withSources () withJavadoc (),
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "io.netty" % "netty-common" % "4.1.76.Final",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.4",
  "io.dropwizard" % "dropwizard-metrics-graphite" % "2.1.4",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0" % Test,
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.lihaoyi" %% "utest" % "0.7.10" % Test,
  "org.mockito" %% "mockito-scala-scalatest" % "1.17.5" % Test,
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.17.2" % Test,
  "com.h2database" % "h2" % "2.1.210" % Test,
  "org.scalatest" %% "scalatest" % "3.2.11" % Test
)

organization := "io.github.givesocialmovement"

name := "play-moonlight"

version := "1.0.3"

Test / parallelExecution := false

bintrayOrganization := Some("givers")

bintrayRepository := "maven"

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

pomIncludeRepository := { _ => false }

homepage := Some(url("https://github.com/GIVESocialMovement/moonlight"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/GIVESocialMovement/moonlight"),
    "scm:git@github.com:GIVESocialMovement/moonlight.git"
  )
)

developers := List(
  Developer(id = "tanin", name = "tanin", email = "developers@giveasia.org", url = url("https://github.com/tanin47"))
)

licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

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
