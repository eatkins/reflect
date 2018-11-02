package com.swoval.reflect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides utilities for parsing command line arguments. A simple example:
 *
 * <pre>{@code
 * package foo.bar;
 *
 * import com.swoval.reflect.CommandArgs;
 *
 * public class Runner {
 *   public static void main(String[] args) {
 *     final CommandArgs.ParsedArgs parsedArgs =
 *       CommandArgs.parser(CommandArgs.arg("A command line argument", "--argv1")).parse(args);
 *     if (parsedArgs.getUnparsed().contains("--help")) {
 *       System.out.println(CommandArgs.defaultHelp("Help:\nUsage:", parsedArgs));
 *     } else if (parsedArgs.get("--argv1") == null) {
 *       System.out.println("The '--argv1' parameter is unspecified.");
 *     } else {
 *       System.out.println(
 *         CommandArgs.defaultStartup("Running program with arguments:", parsedArgs));
 *     }
 *   }
 * }
 * }</pre>
 *
 * If the above code is compiled and run with ["--argv1", "foo"], then it should print out:
 *
 * <pre>
 * Running program with arguments:
 *   --argv1 = "foo"
 * </pre>
 *
 * If instead it's run with ["--help], then it should print out:
 *
 * <pre>
 * Help:
 * Usage:
 *   --argv1 - A command line argument
 * </pre>
 *
 * Finally, if run with no args, then it should print out:
 *
 * <pre>
 * The '--argv' parameter is unspecified.
 * </pre>
 */
public class CommandArgs {
  private CommandArgs() {}

  private static final int MAX_HELP_COLUMNS = 80;
  private static final int ARG_INDENT = 2;
  private static final String ARG_INDENT_STR = getIndent(ARG_INDENT);
  private static final Comparator<ArgString> ARG_STRING_COMPARATOR =
      Comparator.comparing(a -> a.key);

  /** Parses command line arguments. */
  public interface Parser {

    /**
     * Parse the command line arguments.
     *
     * @param args the arguments to parse.
     * @return the {@link ParsedArgs} for this parser and command line arguments.
     */
    ParsedArgs parse(final String... args);
  }

  /**
   * Produces a banner string. Suppose that the ParsedArgs have defined parameters (--arg1,
   * --the-longer-arg) with respective descriptions ("the first argument", "an argument with a
   * longer name") and that the overview string is "HELP:", then the format will look like:
   *
   * <pre>
   * HELP:
   *   --arg1           - the first argument
   *   --the-longer-arg - an argument with a longer name
   * </pre>
   *
   * The arguments will be printed in alphabetical order, dropping the prefix (any number of leading
   * '-' characters).
   *
   * @param overview the text to display above the argument list
   * @param parsedArgs the {@link ParsedArgs} that includes the runtime grouping of the program
   *     arguments.
   * @return the startup banner string.
   */
  public static String defaultHelp(final String overview, final ParsedArgs parsedArgs) {
    final StringBuilder builder = new StringBuilder();
    builder.append(overview);
    final List<Arg> args = parsedArgs.getArgs();
    final List<FormattedArg> formattedArgs = new ArrayList<>();
    for (final Arg arg : args) {
      formattedArgs.add(formatStartArg(arg, parsedArgs));
    }
    final int keyLength = maxKeyLength(formattedArgs, true);
    final List<ArgString> argStrings = new ArrayList<>();
    for (final FormattedArg formattedArg : formattedArgs) {
      argStrings.add(formatHelpArg(formattedArg, keyLength));
    }
    argStrings.sort(ARG_STRING_COMPARATOR);
    for (final ArgString argString : argStrings) {
      builder.append('\n');
      builder.append(argString.value);
    }
    return builder.toString();
  }

  /**
   * Produces a banner string. Suppose that the ParsedArgs have defined parameters (--arg1,
   * --a-longer-arg) and that the program args are ["--arg1", "foo"] and that the overview string is
   * "Running foo with arguments:", then the format will look like:
   *
   * <pre>
   * Running foo with arguments:
   *   --a-longer-arg = &lt;unset&gt;
   *   --arg1         = "foo"
   * </pre>
   *
   * The arguments will be printed in alphabetical order, dropping the prefix (any number of leading
   * '-' characters). Arguments with aliases will be printed separately.
   *
   * @param overview the text to display above the argument list
   * @param parsedArgs the {@link ParsedArgs} that includes the runtime grouping of the program
   *     arguments.
   * @return the startup banner string.
   */
  public static String defaultStartup(final String overview, final ParsedArgs parsedArgs) {
    final StringBuilder builder = new StringBuilder();
    builder.append(overview);
    final List<FormattedArg> formattedArgs = new ArrayList<>();
    for (final Arg arg : parsedArgs.getArgs()) {
      formattedArgs.add(formatStartArg(arg, parsedArgs));
    }
    int keyLength = maxKeyLength(formattedArgs, false);
    final List<ArgString> argStrings = new ArrayList<>();
    for (final FormattedArg formattedArg : formattedArgs) {
      argStrings.addAll(formatStartArg(formattedArg, keyLength));
    }
    argStrings.sort(ARG_STRING_COMPARATOR);

    for (final ArgString argString : argStrings) {
      builder.append('\n');
      builder.append(argString.value);
    }
    return builder.toString();
  }

  /**
   * Represents a command line argument. An argument is specified by a list of names and a
   * description. The reason that it takes a list of names is to support the use case where there is
   * a verbose and short versino of a flag, e.g. "--help" and "-h".
   */
  public static class Arg {
    private final List<String> names;
    private final String description;

    private Arg(final String description, final List<String> names) {
      this.description = description;
      this.names = Collections.unmodifiableList(names);
    }

    /**
     * Returns the names associated with this command line argument.
     *
     * @return the names associated with this command line argument.
     */
    public List<String> getNames() {
      return names;
    }

    /**
     * Returns the description of this command line argument.
     *
     * @return the description of this command line argument.
     */
    public String getDescription() {
      return description;
    }
  }

  /**
   * Instantiate an {@link Arg} with a given description and list of names. Names can be specified
   * as a raw name, e.g. --foo, or as key/value, e.g. --foo=&lt;string&gt;. When the raw format is
   * used, the parser will group all of the arguments together that follow that key until the end of
   * the command list is reached or until another key is found in the argument list. When the
   * key/value syntax is used, the argument must be specified with the associated value, e.g.
   * "--foo=bar", but tail arguments may still be specified, e.g. ["--foo=bar" "baz", "--buzz=true"]
   * should group "bar" and "baz" with "--foo" and "true" with "--buzz".
   *
   * @param description the description of the argument that can be used to generate help and
   *     startup banners.
   * @param name the primary name of the argument
   * @param aliases the (optional) aliases for the argument. Note that when using aliases and
   *     constructing a parser using {@link CommandArgs#parser(Arg...)} it is necessary to extract
   *     the values for each alias separately using {@link ParsedArgs#get}.
   * @return the {@link Arg} instance with the given parameters.
   */
  public static Arg arg(final String description, final String name, final String... aliases) {
    final List<String> names = new ArrayList<>();
    names.add(name);
    Collections.addAll(names, aliases);
    return new Arg(description, names);
  }

  /**
   * Represents the command line arguments after parsing and grouping into key/value pairs where the
   * keys are the names of the arguments and
   */
  public static final class ParsedArgs {
    private final List<String> unparsed = new ArrayList<>();
    private final Map<String, List<String>> values = new HashMap<>();
    private final List<Arg> args;

    private ParsedArgs(final List<Arg> args) {
      this.args = Collections.unmodifiableList(args);
    }

    /**
     * Returns all of the {@link Arg} instances for the command line arguments handled by this
     * parser.
     *
     * @return all of the {@link Arg} instances for the command line arguments.
     */
    public List<Arg> getArgs() {
      return args;
    }

    /**
     * Returns all of the command line arguments that couldn't be associated with a particular key.
     *
     * @return the command line arguments.
     */
    public List<String> getUnparsed() {
      return Collections.unmodifiableList(unparsed);
    }

    /**
     * Get the values associated with a particular key. Note that if the key is specified with
     * key/value syntax, then the '=' must be present, e.g. if there is an argument specified with
     * "--foo=&lt;int&gt;", then that argument must be accessed with "--foo=", not "--foo".
     *
     * @param name the argument to get the values
     * @return null if the argument was unspecified in the argument list. Otherwise return the list
     *     of values associated with the key.
     */
    public List<String> get(final String name) {
      final List<String> result = values.get(name);
      return result == null ? null : Collections.unmodifiableList(result);
    }
  }

  /**
   * Builds a parser that will parse and extract the values for the provided {@link Arg} instances.
   *
   * @param args the command line arguments that this parser will handle.
   * @return the {@link Parser} instance for these {@link Arg}s.
   */
  public static Parser parser(final Arg... args) {
    final Set<String> set = new HashSet<>();
    for (final Arg arg : args) {
      for (final String name : arg.getNames()) {
        set.add(name(name));
      }
    }
    final List<Arg> argsList = new ArrayList<>();
    Collections.addAll(argsList, args);
    return arguments -> {
      final List<String> list = new ArrayList<>();
      Collections.addAll(list, arguments);
      return parseArgs(list, set, new ParsedArgs(argsList));
    };
  }

  private static String stripPrefix(final String string) {
    if (string.isEmpty()) {
      return string;
    } else {
      int i = 0;
      while (string.charAt(i) == '-') {
        i += 1;
      }
      return string.substring(i);
    }
  }

  private static ArgString formatHelpArg(final FormattedArg formattedArg, final int keyLength) {
    final StringBuilder builder = new StringBuilder();
    builder.append(ARG_INDENT_STR);
    builder.append(formattedArg.helpName);
    final int padding = keyLength - formattedArg.helpName.length();
    if (padding > 0) builder.append(getIndent(padding));
    builder.append(" - ");
    final int offset = keyLength + ARG_INDENT + 3;
    final String desc = formattedArg.desc;
    if (offset + desc.length() < MAX_HELP_COLUMNS) {
      builder.append(desc);
    } else {
      int position = offset;
      final String[] parts = desc.split(" ");
      for (int j = 0; j < parts.length; ++j) {
        final String part = parts[j];
        if (position + part.length() < MAX_HELP_COLUMNS) {
          if (j > 0) builder.append(' ');
          builder.append(part);
        } else {
          position = offset;
          builder.append('\n');
          builder.append(getIndent(offset));
          builder.append(part);
        }
        position += part.length() + 1;
      }
    }
    return new ArgString(stripPrefix(formattedArg.helpName), builder.toString());
  }

  private static List<ArgString> formatStartArg(
      final FormattedArg formattedArg, final int keyLength) {
    final List<ArgString> argStrings = new ArrayList<>();
    final List<String> names = formattedArg.shortNames;
    for (final String name : names) {
      final StringBuilder builder = new StringBuilder();
      builder.append(ARG_INDENT_STR);
      builder.append(name);
      final int padding = keyLength - name.length();
      if (padding > 0) builder.append(getIndent(padding));
      builder.append(" = ");
      final List<String> values = formattedArg.values.get(name);
      if (values == null) {
        builder.append("<unset>");
      } else if (values.size() == 1) {
        builder.append('"');
        builder.append(values.get(0));
        builder.append('"');
      } else {
        builder.append('[');
        for (int k = 0; k < values.size(); ++k) {
          if (k > 0) builder.append(", ");
          builder.append('"');
          builder.append(values.get(k));
          builder.append('"');
        }
        builder.append(']');
      }
      argStrings.add(new ArgString(stripPrefix(name), builder.toString()));
    }
    return argStrings;
  }

  private static ParsedArgs parseArgs(
      final List<String> args, final Set<String> argNames, final ParsedArgs parsedArgs) {
    while (!args.isEmpty()) {
      final String arg = args.remove(0);
      final String name = name(arg);
      if (argNames.contains(name)) {
        final List<String> result = new ArrayList<>();
        if (!name.equals(arg)) result.add(value(arg));
        while (!args.isEmpty() && !argNames.contains(name(args.get(0)))) {
          result.add(args.remove(0));
        }
        parsedArgs.values.put(name, result);
      } else {
        parsedArgs.unparsed.add(arg);
      }
    }
    return parsedArgs;
  }

  private static class ArgString {
    final String key;
    final String value;

    ArgString(final String key, final String value) {
      this.key = key;
      this.value = value;
    }
  }

  private static class FormattedArg {
    private final String helpName;
    private final String desc;
    private final List<String> shortNames;
    private final Map<String, List<String>> values;

    FormattedArg(
        final String helpName,
        final String desc,
        final List<String> shortNames,
        final Map<String, List<String>> values) {
      this.helpName = helpName;
      this.desc = desc;
      this.shortNames = shortNames;
      this.values = values;
    }
  }

  private static int maxKeyLength(final List<FormattedArg> formattedArgs, boolean help) {
    int i = 0;
    for (final FormattedArg formattedArg : formattedArgs) {
      final List<String> names = new ArrayList<>();
      if (help) names.add(formattedArg.helpName);
      else names.addAll(formattedArg.shortNames);
      for (final String name : names) {
        i = name.length() > i ? name.length() : i;
      }
    }
    return i;
  }

  private static String name(final String arg) {
    return name(arg, true);
  }

  private static String name(final String arg, boolean includeEquals) {
    final int equalsIndex = arg.indexOf('=');
    return equalsIndex > -1 ? arg.substring(0, includeEquals ? equalsIndex + 1 : equalsIndex) : arg;
  }

  private static String value(final String arg) {
    final int equalsIndex = arg.indexOf('=');
    return equalsIndex > 0 && equalsIndex < arg.length() - 1 ? arg.substring(equalsIndex + 1) : arg;
  }

  private static String getIndent(final int indent) {
    final StringBuilder builder = new StringBuilder(indent);
    for (int i = 0; i < indent; ++i) builder.append(' ');
    return builder.toString();
  }

  private static FormattedArg formatStartArg(final Arg arg, final ParsedArgs parsedArgs) {
    final StringBuilder builder = new StringBuilder();
    final List<String> names = arg.getNames();
    for (int i = 0; i < names.size(); ++i) {
      if (i != 0) builder.append(',');
      builder.append(names.get(i));
    }
    final List<String> shortNames = new ArrayList<>();
    final Map<String, List<String>> values = new HashMap<>();
    for (final String rawName : names) {
      final String name = name(rawName, false);
      shortNames.add(name);
      final List<String> v = parsedArgs.get(name(rawName));
      if (v != null) values.put(name, v);
    }
    return new FormattedArg(builder.toString(), arg.getDescription(), shortNames, values);
  }
}
