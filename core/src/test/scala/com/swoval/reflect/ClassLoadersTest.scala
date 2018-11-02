package com.swoval.reflect

import java.nio.file.{ Files, Path, Paths, StandardCopyOption }
import java.util

import utest._

import scala.collection.JavaConverters._
import TestClasses._

object ClassLoadersTest extends TestSuite {
  private val coreDir = Paths.get("").toAbsolutePath match {
    case p if p.getFileName.toString == "reflect" => p.resolve("core")
    case p                                        => p
  }
  private val classPathUrls = Seq("classes", "test-classes").map { dir =>
    coreDir.resolve(s"target/$dir").toUri.toURL
  }.toArray
  private val resourceDir = coreDir.resolve("src/test/resources/classes")
  private def copy(path: Path, target: Path): Unit = {
    Files.walk(path).iterator.asScala.filter(!Files.isDirectory(_)).foreach { f =>
      val relative = path.relativize(f)
      Files.createDirectories(target.resolve(relative.getParent))
      Files.copy(f, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
    }
  }
  private def loader = new ChildFirstClassLoader(classPathUrls)
  private def invoke(method: String, args: AnyRef*) =
    ClassLoaders.invokeStaticMethod(loader, "com.swoval.reflect.TestModule", method, args: _*)
  private def classInvoke(className: String, constructorArgs: AnyRef*)(methodName: String,
                                                                       args: AnyRef*): AnyRef =
    ClassLoaders.invokeMethod(newInstance(className, constructorArgs: _*), methodName, args: _*)

  private def newInstance(name: String, args: AnyRef*): AnyRef =
    ClassLoaders.newInstance(loader, name, args: _*)
  private def box[T](t: T): Object = t match {
    case b: Boolean => java.lang.Boolean.valueOf(b)
    case b: Byte    => java.lang.Byte.valueOf(b)
    case c: Char    => java.lang.Character.valueOf(c)
    case d: Double  => java.lang.Double.valueOf(d)
    case f: Float   => java.lang.Float.valueOf(f)
    case i: Int     => java.lang.Integer.valueOf(i)
    case l: Long    => java.lang.Long.valueOf(l)
    case s: Short   => java.lang.Short.valueOf(s)
    case x: AnyRef  => x
  }
  val tests = Tests {
    'staticInvoke - {
      'success - {
        'primitive - {
          invoke("boolean", box(true)) ==> 1
          invoke("byte", box(1.toByte)) ==> 2
          invoke("char", box('x')) ==> 'x'.toInt
          invoke("double", box(1.0)) ==> 2
          invoke("float", box(1.0f)) ==> 2
          invoke("int", box(1)) ==> 2
          invoke("long", box(1L)) ==> 2
          invoke("short", box(1.toShort)) ==> 2
        }
        'subtype - {
          val l = loader
          val bar = ClassLoaders.newInstance(l, "com.swoval.reflect.TestClasses$Bar")
          ClassLoaders.invokeStaticMethod(l, "com.swoval.reflect.TestModule", "bar", bar) ==> 4
        }
        'reload - {
          val tempDir = Files.createTempDirectory("ClassLoadersTest")
          try {
            val resourceClassPathUrls = tempDir.toUri.toURL +: classPathUrls

            {
              val l = loader
              val bar = ClassLoaders.newInstance(l, "com.swoval.reflect.TestClasses$Bar")
              ClassLoaders.invokeStaticMethod(l, "com.swoval.reflect.TestModule", "bar", bar) ==> 4
            }

            {
              copy(resourceDir.resolve("1"), tempDir)
              val l = new ChildFirstClassLoader(resourceClassPathUrls)
              val bar = ClassLoaders.newInstance(l, "com.swoval.reflect.TestClasses$Bar")
              ClassLoaders.invokeStaticMethod(l, "com.swoval.reflect.TestModule", "bar", bar) ==> 1
            }

            {
              copy(resourceDir.resolve("2"), tempDir)
              val l = new ChildFirstClassLoader(resourceClassPathUrls)
              val bar = ClassLoaders.newInstance(l, "com.swoval.reflect.TestClasses$Bar")
              ClassLoaders.invokeStaticMethod(l, "com.swoval.reflect.TestModule", "bar", bar) ==> 2
            }
          } finally {
            Files.walk(tempDir).iterator.asScala.toIndexedSeq.reverse.foreach(Files.deleteIfExists)
          }
        }
      }
      'failure - {
        'primitive - {
          intercept[NoSuchMethodException](invoke("boolean", new Object) ==> null)
          intercept[NoSuchMethodException](invoke("byte", new Object) ==> null)
          intercept[NoSuchMethodException](invoke("char", new Object) ==> null)
          intercept[NoSuchMethodException](invoke("double", new Object) ==> null)
          intercept[NoSuchMethodException](invoke("float", new Object) ==> null)
          intercept[NoSuchMethodException](invoke("int", new Object) ==> null)
          intercept[NoSuchMethodException](invoke("long", new Object) ==> null)
          intercept[NoSuchMethodException](invoke("short", new Object) ==> null)
          intercept[NoSuchMethodException](invoke("short") ==> null)
          intercept[NoSuchMethodException](invoke("short", new Object, new Object) ==> null)
          intercept[NoSuchMethodException](
            invoke("foo", java.lang.Integer.valueOf(1), new Object) ==> null)
          intercept[NoSuchMethodException](invoke("foo", new Object, new Object) ==> null)
          ()
        }
      }
    }
    'newInstance - {
      'success - {
        'primitives - {
          classInvoke("com.swoval.reflect.TestClasses$Baz", box(true))("x") ==> 1
          classInvoke("com.swoval.reflect.TestClasses$Baz", box(1.toByte))("x") ==> 2
          classInvoke("com.swoval.reflect.TestClasses$Baz", box('x'))("x") ==> 'x'.toInt
          classInvoke("com.swoval.reflect.TestClasses$Baz", box(1.0))("x") ==> 2
          classInvoke("com.swoval.reflect.TestClasses$Baz", box(1.0f))("x") ==> 2
          classInvoke("com.swoval.reflect.TestClasses$Baz", box(2))("x") ==> 2
          classInvoke("com.swoval.reflect.TestClasses$Baz", box(1L))("x") ==> 2
          classInvoke("com.swoval.reflect.TestClasses$Baz", box(1.toShort))("x") ==> 2
          classInvoke("com.swoval.reflect.TestClasses$Baz", box(1.toShort))("foo", box(1), box(1)) ==> 2
        }
      }
      'failure - {
        intercept[NoSuchMethodException](
          newInstance("com.swoval.reflect.TestClasses$Baz", new Object))
        intercept[NoSuchMethodException](newInstance("com.swoval.reflect.TestClasses$Baz"))
        ()
      }
    }
    'arguments - {
      val obj = "com.swoval.reflect.TestModule"
      val l = loader
      val arg = ClassLoaders.newInstance(l, "com.swoval.reflect.TestClasses$Bar")
      val clazz = l.loadClass("com.swoval.reflect.TestClasses$Foo")
      ClassLoaders.invokeStaticMethod(l, obj, "bar", ClassLoaders.argument(clazz, arg)) ==> 4
      ClassLoaders.invokeStaticMethod(l, obj, "bar", arg) ==> 4
    }
  }
}

object TestClasses {
  class Foo
  class Bar extends Foo
  class Baz(val x: Int) {
    def this(boolean: Boolean) = this(if (boolean) 1 else 0)
    def this(byte: Byte) = this(byte.toInt + 1)
    def this(char: Char) = this(char.toInt)
    def this(double: Double) = this((double + 1).toInt)
    def this(float: Float) = this((float + 1).toInt)
    def this(long: Long) = this((long + 1).toInt)
    def this(short: Short) = this(short + 1)

    def foo(x: Int, y: Int): Int = x + y
  }
}

object TestModule {
  def boolean(boolean: Boolean): Int = if (boolean) 1 else 0
  def byte(byte: Byte): Int = byte.toInt + 1
  def char(char: Char): Int = char.toInt
  def double(double: Double): Double = double + 1
  def float(float: Float): Float = float + 1
  def int(int: Int): Int = int + 1
  def long(long: Long): Long = long + 1
  def short(short: Short): Int = short + 1

  def foo(x: Int, y: Int): Int = x + y

  def bar(foo: Foo): Int = 4
}
