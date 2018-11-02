package com.swoval.reflect

import java.io.File
import java.nio.file.{ Files, Path, Paths, StandardCopyOption }

import sbt._
import Keys._

import scala.tools.nsc
import scala.tools.nsc.reporters.StoreReporter
import scala.collection.JavaConverters._

object Plugins {
  private def withCompiler[R](classpath: String, outputDir: String)(f: nsc.Global => R): R = {
    val settings = new nsc.Settings()
    settings.bootclasspath.value = classpath
    settings.classpath.value = classpath
    settings.outputDirs.add(outputDir, outputDir)
    f(nsc.Global(settings, new StoreReporter))
  }
  def classPath(config: ConfigKey): Def.Initialize[Task[String]] = Def.task {
    (fullClasspath in config).value
      .map(_.data)
      .mkString(File.pathSeparator)
  }
  def copy(path: Path, target: Path): Unit =
    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
  def genCoreTestResourceClasses: Def.Initialize[Task[Unit]] = Def.task {
    val cp = classPath(Test).value

    IO.withTemporaryDirectory { dir =>
      val path = dir.toPath
      val resourceDir = (resourceDirectory in Test).value.toPath
      val classResourceDir: Int => Path =
        i => resourceDir.resolve(s"classes/$i/com/swoval/reflect")
      withCompiler(cp, path.toString) { g =>
        val file = path.resolve("TestModule.scala").toFile
        (1 to 2) foreach { i =>
          IO.write(
            file,
            s"""
             |package com.swoval.reflect
             |
             |object TestClasses {
             |  class Foo
             |  class Bar extends Foo
             |}
             |
             |import TestClasses._
             |
             |object TestModule {
             |  def bar(foo: Foo): Int = $i
             |}
           """.stripMargin
          )
          // If compile fails, it won't throw an exception.
          new g.Run().compile(List(file.toString))
          val outputDir = classResourceDir(i)
          Files.createDirectories(outputDir)
          Files
            .walk(path)
            .iterator
            .asScala
            .filter(_.getFileName.toString.endsWith(".class"))
            .foreach { f =>
              copy(f, outputDir.resolve(f.getFileName))
            }
        }
      }
    }
  }
  def genReflectTestResourceClasses: Def.Initialize[Task[Unit]] = Def.task {
    val cp = classPath(Compile).value
    IO.withTemporaryDirectory { dir =>
      val path = dir.toPath
      val resourceDir = (resourceDirectory in Test).value.toPath
      withCompiler(cp, dir.toString) { g =>
        (resources in Test).value collect {
          case f if f.getName == "Bar.scala.template"  => ("Bar", IO.read(f))
          case f if f.getName == "Buzz.scala.template" => ("Buzz", IO.read(f))
        } foreach {
          case ("Bar", f) =>
            Seq(6, 7) foreach { i =>
              IO.write(path.resolve("Bar.scala").toFile, f.replaceAll("\\$\\$impl", i.toString))
              // If compile fails, it won't throw an exception.
              new g.Run().compile(List(path.resolve("Bar.scala").toString))
              copy(path.resolve("com/swoval/reflect/Bar$.class"),
                   resourceDir.resolve(s"Bar$$.class.$i"))
            }
          case ("Buzz", f) =>
            IO.write(path.resolve("Buzz.scala").toFile, f)
            // If compile fails, it won't throw an exception.
            new g.Run().compile(List(path.resolve("Buzz.scala").toString))
            copy(path.resolve("com/swoval/reflect/Buzz.class"), resourceDir.resolve(s"Buzz.class"))
            IO.delete(path.resolve("com").toFile)
            IO.delete(path.resolve("Buzz.scala").toFile)
          case (_, _) =>
        }
      }
    }
  }
}
