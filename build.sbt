name := """recoenv"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  "net.debasishg" %% "redisclient" % "2.13",
  "postgresql" % "postgresql" % "9.1-901.jdbc4",
  jdbc,
  anorm,
  cache,
  ws
)
