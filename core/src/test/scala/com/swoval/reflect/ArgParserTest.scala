package com.swoval.reflect

import java.util
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

import CommandArgs.{ Arg, ParsedArgs, arg }
import utest._

object ArgParserTest extends TestSuite {
  implicit class ParsedArgsOps(val p: ParsedArgs) extends AnyVal {
    def get(key: String): Seq[String] = p.get(key) match {
      case null => Nil
      case l    => Seq(l.asScala: _*)
    }
    def getUnparsed: Seq[String] = Seq(p.getUnparsed.asScala: _*)
  }
  def parseArgs(names: String*)(arguments: String*): ParsedArgsOps = {
    CommandArgs.parser(args(names: _*): _*).parse(arguments: _*)
  }
  def args(names: String*): Array[Arg] = {
    val res = Array.ofDim[Arg](names.size)
    names.zipWithIndex foreach { case (name, i) => res(i) = arg("", name) }
    res
  }
  def list(args: String*): util.List[String] = {
    val res = new util.ArrayList[String]()
    args.foreach(res.add)
    res
  }
  val tests = Tests {
    'single - {
      'flag - {
        parseArgs(names = "--foo")(arguments = "--foo").get("--foo") ==> Nil
      }
      'value - {
        parseArgs("--foo")("--foo", "bar").get("--foo") ==> "bar" :: Nil
      }
      'list - {
        parseArgs("--foo")("--foo").get("--foo") ==> Nil
        parseArgs("--foo")("--foo", "bar").get("--foo") ==> Seq("bar")
        parseArgs("--foo")("--foo", "foo", "bar").get("--foo") ==> Seq("foo", "bar")
      }
      'empty - {
        parseArgs("--foo")().get("--foo") ==> Nil
      }
      'equals - {
        'noType - {
          parseArgs("--foo=")("--foo=bar").get("--foo=") ==> Seq("bar")
        }
        'type - {
          parseArgs("--foo=<uint>")("--foo=1").get("--foo=") ==> Seq("1")
        }
      }
    }
    'multiple - {
      'names - {
        'singleArgument - {
          val desc = "desc"
          val keys = Seq("--foo", "-f")

          {
            val parsed =
              CommandArgs.parser(arg(desc, keys.head, keys.last)).parse("--foo", "bar")
            parsed.get("--foo") ==> list("bar")
          }
          {
            val parsed = CommandArgs.parser(arg(desc, keys.head, keys.last)).parse("-f", "bar")
            parsed.get("-f") ==> list("bar")
          }
        }
      }
      'present - {
        val parsed = parseArgs("--foo", "--check")("--foo", "foo", "bar", "--check")
        parsed.get("--foo") ==> Seq("foo", "bar")
        parsed.get("--check") ==> Nil
      }
      'missing - {
        val parsed = parseArgs("--foo", "--value")("--foo", "foo", "bar", "--value")
        parsed.get("--foo") ==> Seq("foo", "bar")
        parsed.get("--value") ==> Nil
      }
      'equals - {
        'single - {
          val parsed = parseArgs("--foo=", "--value")("--foo=bar", "--value", "foo", "bar")
          parsed.get("--foo=") ==> Seq("bar")
          parsed.get("--value") ==> Seq("foo", "bar")
        }
        'multiple - {
          val parsed =
            parseArgs("--foo=", "--value")("--foo=bar", "baz", "--value", "foo", "bar")
          parsed.get("--foo=") ==> Seq("bar", "baz")
          parsed.get("--value") ==> Seq("foo", "bar")
        }
      }
    }
    'unparsed - {
      'head - {
        val parsed = parseArgs("--foo")("blah", "--foo", "foo", "bar")
        parsed.get("--foo") ==> Seq("foo", "bar")
        parsed.getUnparsed ==> Seq("blah")
      }
      'tail - {
        val parsed = parseArgs("--foo")("foo", "bar")
        parsed.getUnparsed ==> Seq("foo", "bar")
      }
    }
    'getArgs - {
      val parsed = CommandArgs.parser(arg("desc", "--foo")).parse()
      parsed.getArgs.asScala.toIndexedSeq.flatMap(_.getNames.asScala.toIndexedSeq) ==> Seq("--foo")
      parsed.getArgs.asScala.toIndexedSeq.map(_.getDescription) ==> Seq("desc")
    }
    'helpMessage - {
      'basic - {
        val overview = s"\nOptions:"
        val desc = "foo is a key"
        val key = "--foo=<string>"
        val indent = 2
        val parsed = CommandArgs.parser(arg(desc, key)).parse("--help")
        assert(parsed.getUnparsed.contains("--help"))
        val output = CommandArgs.defaultHelp(overview, parsed)
        output ==> s"$overview\n${" " * indent}$key - $desc"
      }
      'multipleKeys - {
        val overview = s"Parsed output\nOptions:"
        val desc = "foo is a key"
        val key = "--foo=<string>"
        val otherDesc = "bar is a key"
        val otherKey = "--bar"
        val otherKeyShort = "-b"
        val indent = 2
        val parsed =
          CommandArgs.parser(arg(desc, key), arg(otherDesc, otherKey, "-b")).parse("--help")
        assert(parsed.getUnparsed.contains("--help"))
        val output = CommandArgs.defaultHelp(overview, parsed)
        val indentStr = " " * indent
        val padding = " " * (key.length - otherKey.length - otherKeyShort.length - 1)
        val args = Seq(
          s"$otherKey,$otherKeyShort$padding - $otherDesc",
          s"$key - $desc"
        ).map(indentStr + _) mkString "\n"
        output ==> s"$overview\n$args"
      }
      'wrapping - {
        val overview = s"Parsed output\nOptions:"
        val desc = "foo is a key with a very long descriptive text that will be wrapped around"
        val splitIndex = desc.indexOf(" wrapped around")
        val (partOne, partTwo) = (desc.substring(0, splitIndex), desc.substring(splitIndex + 1))
        val key = "--foo=<string>"
        val indent = 2
        val parsed = CommandArgs.parser(arg(desc, key)).parse("--help")
        assert(parsed.getUnparsed.contains("--help"))
        val output = CommandArgs.defaultHelp(overview, parsed)
        val padding = " " * (indent + key.length + 3)
        val expected = s"$overview\n${" " * indent}$key - $partOne\n$padding$partTwo".trim
        output ==> expected
      }
    }
    'startupMessage - {
      'basic - {
        val overview = s"Running program\nArguments:"
        val desc = "foo is a key"
        val key = "--foo=<string>"
        val indent = 2
        val parsed = CommandArgs.parser(arg(desc, key)).parse("--foo=bar")
        val output = CommandArgs.defaultStartup(overview, parsed)
        output ==> s"""$overview\n${" " * indent}--foo = "bar""""
      }
      'multipleKeys - {
        val overview = s"Running program\nArguments:"
        val desc = "foo is a key"
        val key = "--foo=<string>"
        val otherDesc = "desc"
        val otherKey = "--a-longer-key"
        val otherKeyShort = "-a"
        val lastKey = "--the-long-key"
        val indent = 2
        val parsed =
          CommandArgs
            .parser(arg(desc, key), arg(otherDesc, otherKey, otherKeyShort), arg(desc, lastKey))
            .parse("--foo=bar", "--a-longer-key", "foo", "bar", "-a")
        val output = CommandArgs.defaultStartup(overview, parsed)
        val sanitizedKey = key.take(5)
        val indentStr = " " * indent
        val padding = " " * (otherKey.length - sanitizedKey.length)
        val shortPadding = " " * (otherKey.length - otherKeyShort.length)
        val keys = Seq(
          s"$otherKeyShort$shortPadding = []",
          s"""$otherKey = ["foo", "bar"]""",
          s"""$sanitizedKey$padding = "bar"""",
          s"$lastKey = <unset>"
        ).map(indentStr + _)
        output ==> s"$overview\n${keys mkString "\n"}"
      }
    }
  }
}
