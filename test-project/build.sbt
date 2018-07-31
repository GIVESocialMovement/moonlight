name := """test-project"""
organization := "givers.moonlight"

version := "1.0-SNAPSHOT"

// Uncomment the below lines when developing locally
//lazy val moonlight = RootProject(file(".."))
//lazy val root = (project in file("."))
//  .enablePlugins(PlayScala)
//  .aggregate(moonlight)
//  .dependsOn(moonlight)

lazy val root = (project in file(".")).enablePlugins(PlayScala, JavaAppPackaging)

scalaVersion := "2.12.6"

resolvers += Resolver.bintrayRepo("givers", "maven")

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-slick" % "3.0.3",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.3",
  "org.postgresql" % "postgresql" % "42.2.4",
  "givers.moonlight" %% "play-moonlight" % "0.1.1" changing(),
)

