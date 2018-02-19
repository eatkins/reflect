package com.swoval

import sbt._

object Dependencies {
  val scalaMacros = "org.scala-lang" % "scala-reflect"
  val utestVersion = "0.6.3"
  val utest = "com.lihaoyi" %% "utest" % utestVersion % "test"
  val zinc = "org.scala-sbt" %% "zinc" % "1.0.5" % "provided"
}
