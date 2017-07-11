name := """stimpy"""

organization := """org.asm"""

version := s"0.1.$buildNumber"

scalaVersion := "2.11.8"

lazy val buildNumber = sys.props.getOrElse("BUILD_NUMBER", default = "1")

lazy val `stimpy` = project in file(".")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.typesafe.play" %% "play" % "2.5.4" % "provided",
  "com.github.tototoshi" %% "play-json4s-native" % "0.5.0",
  "com.typesafe.play" % "play-test_2.11" % "2.5.4",
  "org.scalatestplus.play" % "scalatestplus-play_2.11" % "1.5.1",
  "com.typesafe.scala-logging" % "scala-logging_2.11" % "3.5.0"
)

fork in run := true

publishTo := Some("Sonatype Snapshots Nexus" at "https://oss.sonatype.org/content/repositories/snapshots")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
