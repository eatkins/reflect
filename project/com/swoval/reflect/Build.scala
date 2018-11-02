package com.swoval.reflect

import java.util.jar.JarFile

import com.swoval.reflect.BuildKeys._
import com.swoval.reflect.Dependencies._
import com.typesafe.sbt.pgp.PgpKeys.publishSigned
import sbt.Keys._
import sbt._

import scala.sys.process._
import scala.util.{ Properties, Try }

object Build {
  val scalaCrossVersions @ Seq(scala211, scala212, scala213) = Seq("2.11.12", "2.12.7", "2.13.0-M5")

  def baseVersion: String = "0.1.0-SNAPSHOT"

  def settings(args: Def.Setting[_]*): SettingsDefinition =
    Def.SettingsDefinition.wrapSettingsDefinition(args)

  def commonSettings: SettingsDefinition =
    settings(
      organization := "com.swoval",
      homepage := Some(url("https://github.com/swoval/reflect")),
      scmInfo := Some(
        ScmInfo(url("https://github.com/swoval/swoval"), "git@github.com:swoval/swoval.git")),
      developers := List(
        Developer("username",
                  "Ethan Atkins",
                  "ethan.atkins@gmail.com",
                  url("https://github.com/eatkins"))),
      licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
      publishMavenStyle in publishLocal := false,
      publishTo := {
        val p = publishTo.value
        if (sys.props.get("SonatypeSnapshot").fold(false)(_ == "true"))
          Some(Opts.resolver.sonatypeSnapshots): Option[Resolver]
        else if (sys.props.get("SonatypeRelease").fold(false)(_ == "true"))
          Some(Opts.resolver.sonatypeReleases): Option[Resolver]
        else p
      },
      version := {
        val v = baseVersion
        if (sys.props.get("SonatypeSnapshot").fold(false)(_ == "true")) {
          if (v.endsWith("-SNAPSHOT")) v else s"$v-SNAPSHOT"
        } else {
          v
        }
      },
      scalaVersion in ThisBuild := scala212,
      java8rt in ThisBuild := {
        if (Properties.isMac) {
          Seq("mdfind", "-name", "rt.jar").!! match {
            case null => None
            case res =>
              res.split("\n").find { n =>
                !n.endsWith("alt-rt.jar") && {
                  val version =
                    Try(Option(new JarFile(n).getManifest)).toOption.flatten
                      .map(_.getMainAttributes.getValue("Specification-Version"))
                  version.getOrElse("0").split("\\.").last == "8"
                }
              }
          }
        } else {
          None
        }
      }
    ) ++ (if (Properties.isMac) Nil else settings(publish := {}, publishSigned := {}))

  lazy val projects = Seq(
    core.project,
    scala.project,
  )

  lazy val reflect = (project in file("."))
    .aggregate(projects: _*)
    .settings(
      publish := {},
      publishSigned := {},
      publishLocal := {}
    )

  lazy val core = project
    .settings(
      commonSettings,
      name := "reflect-core",
      crossPaths := false,
      autoScalaLibrary := false,
      javacOptions ++= Seq("-source", "1.8", "-target", "1.8") ++
        java8rt.value.map(rt => Seq("-bootclasspath", rt)).getOrElse(Seq.empty),
      doc / javacOptions := Nil,
      testFrameworks += new TestFramework("utest.runner.Framework"),
      libraryDependencies += utest,
      genTestResourceClasses := Plugins.genCoreTestResourceClasses.value
    )

  lazy val scala = project
    .settings(
      commonSettings,
      (scalacOptions in Compile) ++= {
        if (scalaVersion.value == scala211) Seq("-Xexperimental") else Nil
      },
      crossScalaVersions := scalaCrossVersions,
      testFrameworks += new TestFramework("utest.runner.Framework"),
      scalacOptions := Seq("-unchecked", "-deprecation", "-feature"),
      fork in Test := true,
      javaOptions in Test ++= Def.taskDyn {
        val forked = (fork in Test).value
        lazy val agent = (packageConfiguration in (Compile, packageBin)).value.jar
        Def.task {
          val loader = "-Djava.system.class.loader=com.swoval.reflect.ChildFirstClassLoader"
          if (forked) Seq(loader, s"-javaagent:$agent") else Seq.empty
        }
      }.value,
      packageOptions in (Compile, packageBin) +=
        Package.ManifestAttributes("Premain-Class" -> "com.swoval.reflect.Agent"),
      genTestResourceClasses := Plugins.genReflectTestResourceClasses.value,
      testOnly in Test := {
        (packageBin in Compile).value
        (testOnly in Test).evaluated
      },
      test in Test := {
        (packageBin in Compile).value
        (test in Test).value
      },
      libraryDependencies ++= Seq(
        scalaMacros % scalaVersion.value,
        utest,
        zinc
      )
    )
    .dependsOn(core)
}
