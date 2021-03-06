name := """recoeng"""

organization := "com.ruimo"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.2"

resolvers += "ruimo.com" at "http://static.ruimo.com/release"

libraryDependencies ++= Seq(
  "com.livestream" %% "scredis" % "1.1.2",
  "postgresql" % "postgresql" % "9.1-901.jdbc4",
  "com.ruimo" %% "recoengcommon" % "1.1-SNAPSHOT",
  jdbc,
  anorm,
  cache,
  ws
)

publishTo := Some(
  Resolver.file(
    "recoengcommon",
    new File(Option(System.getenv("RELEASE_DIR")).getOrElse("/tmp"))
  )
)
