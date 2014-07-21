name := """recoenv"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  "com.livestream" %% "scredis" % "1.1.2",
  "postgresql" % "postgresql" % "9.1-901.jdbc4",
  jdbc,
  anorm,
  cache,
  ws
)
