package com.swoval.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Provides utilities for laading classes and invoking methods using reflection. */
public class ClassLoaders {
  private ClassLoaders() {}

  /**
   * Represents an argument for a method to be invoked via reflection. It exists because calling
   * getClass on the instance may return a sub type of the method argument, which prevents direct
   * lookup with {@link Class#getMethod(String, Class[])}.
   */
  public static final class Argument {
    final Class<?> clazz;
    final Object instance;

    private Argument(final Class<?> clazz, final Object instance) {
      this.clazz = clazz;
      this.instance = instance;
    }

    @Override
    public String toString() {
      return "Argument(" + clazz + ", " + instance + ")";
    }
  }

  /**
   * Construct an instance of {@link Argument} for a given {@link Class} and {@link Object}
   * instance.
   *
   * @param clazz the class of the expected method parameter type
   * @param instance the object to pass in as a method parameter
   * @return an {@link Argument} instance.
   */
  public static Argument argument(final Class<?> clazz, final Object instance) {
    return new Argument(clazz, instance);
  }

  /**
   * Use reflection to invoke a static method. Usage:
   *
   * <p><i>file</i>: Foo.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * public class Foo {
   *   public static int foo(final int x) {
   *     return x + 1;
   *   }
   * }
   * }</pre>
   *
   * <i>file</i>: ReflectionTest.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * import com.swoval.reflect.ClassLoaders;
   *
   * public class ReflectionTest {
   *   public static void main(final String[] args) {
   *     try {
   *       Object result = ClassLoaders.invokeStaticMethod(
   *           Thread.currentThread().getContextClassLoader(),
   *           "foo.bar.Foo",
   *           "foo",
   *           java.lang.Integer.valueOf(args[0])
   *           );
   *       System.out.println(result);
   *     } catch (final Exception e) {
   *       throw new RuntimeException(e);
   *     }
   *   }
   * }
   * }</pre>
   *
   * If the ReflectionTest program is run with the command line argument "1", then it should print
   * "2".
   *
   * @param loader the {@link ClassLoader} to load the class
   * @param className the fully qualified name of the class
   * @param methodName the method to invoke
   * @param args the {@link Object} instances to pass into the method
   * @return the value of the evaluated static method.
   * @throws ClassNotFoundException if the class cannot be loaded
   * @throws IllegalAccessException if the method is not public
   * @throws NoSuchMethodException if the method doesn't exist
   */
  public static Object invokeStaticMethod(
      final ClassLoader loader,
      final String className,
      final String methodName,
      final Object... args)
      throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
    return invokeStaticMethod(loader, className, methodName, argumentsFromObjects(args));
  }

  /**
   * Use reflection to invoke a static method. Usage:
   *
   * <p><i>file</i>: Foo.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * public class Foo {
   *   public static int foo(final int x) {
   *     return x + 1;
   *   }
   * }
   * }</pre>
   *
   * <i>file</i>: ReflectionTest.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * import com.swoval.reflect.ClassLoaders;
   *
   * public class ReflectionTest {
   *   public static void main(final String[] args) {
   *     try {
   *       Object result = ClassLoaders.invokeStaticMethod(
   *           Thread.currentThread().getContextClassLoader(),
   *           "foo.bar.Foo",
   *           "foo",
   *           ClassLoaders.argument(int.class, java.lang.Integer.valueOf(args[0]))
   *           );
   *       System.out.println(result);
   *     } catch (final Exception e) {
   *       throw new RuntimeException(e);
   *     }
   *   }
   * }
   * }</pre>
   *
   * If the ReflectionTest program is run with the command line argument "1", then it should print
   * "2".
   *
   * @param loader the {@link ClassLoader} to load the class
   * @param className the fully qualified name of the class
   * @param methodName the method to invoke
   * @param args the {@link ClassLoaders.Argument} instances that specify both the expected
   * parameter type of the method argument and the actual value to pass into the method. Note that
   * the value may not have the same type as the parameter because it may be a subtype.
   * @return the value of the evaluated static method.
   * @throws ClassNotFoundException if the class cannot be loaded
   * @throws IllegalAccessException if the method is not public
   * @throws NoSuchMethodException if the method doesn't exist
   */
  public static Object invokeStaticMethod(
      final ClassLoader loader,
      final String className,
      final String methodName,
      final Argument... args)
      throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
    final Class<?> clazz = loader.loadClass(className);
    final Class<?>[] classes = classesFromArgs(args);
    Method method = findMethod(clazz, methodName, classes);
    try {
      return method.invoke(null, objectsFromArgs(args));
    } catch (final InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Use reflection to invoke an object method. Usage:
   *
   * <p><i>file</i>: Foo.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * public class Foo {
   *   public int foo(final int x) {
   *     return x + 1;
   *   }
   * }
   * }</pre>
   *
   * <i>file</i>: ReflectionTest.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * import com.swoval.reflect.ClassLoaders;
   *
   * public class ReflectionTest {
   *   public static void main(final String[] args) {
   *     try {
   *       ClassLoader loader = Thread.currentThread().getContextClassLoader();
   *       Object foo = ClassLoaders.newInstance(loader, "foo.bar.Foo");
   *       ClassLoaders.Argument arg = ClassLoaders.argument(int.class, Integer.valueOf(args[0]));
   *       Object result = ClassLoaders.invokeMethod(foo, "foo", arg);
   *       System.out.println(result);
   *     } catch (final Exception e) {
   *       throw new RuntimeException(e);
   *     }
   *   }
   * }
   * }</pre>
   *
   * If the ReflectionTest program is run with the command line argument "1", then it should print
   * "2".
   *
   * @param target the object to invoke the
   * @param methodName the method to invoke
   * @param args the {@link Object} instances to pass into the method
   * @return the value of the evaluated static method.
   * @throws IllegalAccessException if the method is not public
   * @throws NoSuchMethodException if the method doesn't exist
   */
  public static Object invokeMethod(
      final Object target, final String methodName, final Object... args)
      throws NoSuchMethodException, IllegalAccessException {
    return invokeMethod(target, methodName, argumentsFromObjects(args));
  }

  /**
   * Use reflection to invoke an object method. Usage:
   *
   * <p><i>file</i>: Foo.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * public class Foo {
   *   public int foo(final int x) {
   *     return x + 1;
   *   }
   * }
   * }</pre>
   *
   * <i>file</i>: ReflectionTest.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * import com.swoval.reflect.ClassLoaders;
   *
   * public class ReflectionTest {
   *   public static void main(final String[] args) {
   *     try {
   *       ClassLoader loader = Thread.currentThread().getContextClassLoader();
   *       Object foo = ClassLoaders.newInstance(loader, "foo.bar.Foo");
   *       ClassLoaders.Argument arg = ClassLoaders.argument(int.class, Integer.valueOf(args[0]));
   *       Object result = ClassLoaders.invokeMethod(foo, "foo", arg);
   *       System.out.println(result);
   *     } catch (final Exception e) {
   *       throw new RuntimeException(e);
   *     }
   *   }
   * }
   * }</pre>
   *
   * If the ReflectionTest program is run with the command line argument "1", then it should print
   * "2".
   *
   * @param target the object to invoke the
   * @param methodName the method to invoke
   * @param args the {@link ClassLoaders.Argument} instances that specify both the expected
   * parameter type of the method argument and the actual value to pass into the method. Note that
   * the value may not have the same type as the parameter because it may be a subtype.
   * @return the value of the evaluated static method.
   * @throws IllegalAccessException if the method is not public
   * @throws NoSuchMethodException if the method doesn't exist
   */
  public static Object invokeMethod(
      final Object target, final String methodName, final Argument... args)
      throws NoSuchMethodException, IllegalAccessException {
    final Method method = findMethod(target.getClass(), methodName, classesFromArgs(args));
    try {
      return method.invoke(target, objectsFromArgs(args));
    } catch (final InvocationTargetException e) {
      throw new RuntimeException(e); // Should be unreachable.
    }
  }

  /**
   * Use reflection to invoke an object method. Usage:
   *
   * <p><i>file</i>: Bar.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * public class Bar {
   *   public Bar(final int x) {
   *     System.out.println(x + 1);
   *   }
   * }
   * }</pre>
   *
   * <i>file</i>: ReflectionTest.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * import com.swoval.reflect.ClassLoaders;
   *
   * public class ReflectionTest3 {
   *   public static void main(final String[] args) {
   *     try {
   *       final ClassLoader loader = Thread.currentThread().getContextClassLoader();
   *       final Object bar = ClassLoaders.newInstance(loader, "foo.bar.Bar", Integer.valueOf(args[0]));
   *     } catch (final Exception e) {
   *       throw new RuntimeException(e);
   *     }
   *   }
   * }
   * }</pre>
   *
   * If the ReflectionTest program is run with the command line argument "1", then it should print
   * "2".
   *
   * @param loader the {@link ClassLoader} with which we may load the class specified by the
   * className parameter
   * @param className the fully qualified name of the class to instantiate.
   * @param args the {@link ClassLoaders.Argument} instances that specify both the expected
   *     parameter type of the method argument and the actual value to pass into the method. Note
   *     that the value may not have the same type as the parameter because it may be a subtype.
   * @return the value of the evaluated static method.
   * @throws IllegalAccessException if the method is not public
   * @throws NoSuchMethodException if the method doesn't exist
   */
  public static Object newInstance(
      final ClassLoader loader, final String className, final Object... args)
      throws ClassNotFoundException, InvocationTargetException, IllegalAccessException,
          InstantiationException, NoSuchMethodException {
    return newInstance(loader, className, argumentsFromObjects(args));
  }

  /**
   * Use reflection to invoke an object method. Usage:
   *
   * <p><i>file</i>: Bar.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * public class Bar {
   *   public Bar(final int x) {
   *     System.out.println(x);
   *   }
   * }
   * }</pre>
   *
   * <i>file</i>: ReflectionTest.java
   *
   * <pre>{@code
   * package foo.bar;
   *
   * import com.swoval.reflect.ClassLoaders;
   *
   * public class ReflectionTest3 {
   *   public static void main(final String[] args) {
   *     try {
   *       final ClassLoader loader = Thread.currentThread().getContextClassLoader();
   *       final ClassLoaders.Argument arg = ClassLoaders.argument(int.class, Integer.valueOf(args[0]));
   *       final Object bar = ClassLoaders.newInstance(loader, "foo.bar.Bar", arg);
   *     } catch (final Exception e) {
   *       throw new RuntimeException(e);
   *     }
   *   }
   * }
   * }</pre>
   *
   * If the ReflectionTest program is run with the command line argument "1", then it should print
   * "2".
   *
   * @param loader the {@link ClassLoader} with which we may load the class specified by the
   * className parameter
   * @param className the fully qualified name of the class to instantiate.
   * @param args the {@link ClassLoaders.Argument} instances that specify both the expected
   *     parameter type of the method argument and the actual value to pass into the method. Note
   *     that the value may not have the same type as the parameter because it may be a subtype.
   * @return the value of the evaluated static method.
   * @throws IllegalAccessException if the method is not public
   * @throws NoSuchMethodException if the method doesn't exist
   */
  public static Object newInstance(
      final ClassLoader loader, final String className, final Argument... args)
      throws ClassNotFoundException, InvocationTargetException, IllegalAccessException,
          InstantiationException, NoSuchMethodException {
    final Class<?> clazz = loader.loadClass(className);
    final Class<?>[] classes = classesFromArgs(args);
    Constructor<?> constructor = null;
    try {
      constructor = clazz.getConstructor(classes);
    } catch (final NoSuchMethodException e) {
      final Constructor<?>[] methods = clazz.getConstructors();
      for (Constructor<?> cons : methods) {
        final Class<?>[] paramTypes = cons.getParameterTypes();
        if (paramTypes.length == classes.length) {
          boolean found = true;
          for (int i = 0; i < paramTypes.length && found; ++i) {
            Class<?> paramType = paramTypes[i];
            Class<?> argType = classes[i];
            if (!paramType.isAssignableFrom(argType)) {
              found = compatible(paramType, argType);
            }
          }
          if (found) constructor = cons;
        }
      }
    }
    if (constructor == null)
      throw new NoSuchMethodException("No constructor with argument types: " + Arrays.asList(args));
    return constructor.newInstance(objectsFromArgs(args));
  }

  private static Method findMethod(
      final Class<?> clazz, final String methodName, final Class<?>[] classes)
      throws NoSuchMethodException {
    try {
      return clazz.getMethod(methodName, classes);
    } catch (final NoSuchMethodException e) {
      final Method[] methods = clazz.getMethods();
      for (Method m : methods) {
        final Class<?>[] paramTypes = m.getParameterTypes();
        if (m.getName().equals(methodName) && paramTypes.length == classes.length) {
          boolean found = true;
          for (int i = 0; i < paramTypes.length && found; ++i) {
            Class<?> paramType = paramTypes[i];
            Class<?> argType = classes[i];
            if (!paramType.isAssignableFrom(argType)) {
              found = compatible(paramType, argType);
            }
          }
          if (found) return m;
        }
      }
    }
    throw new NoSuchMethodException(clazz.getName() + "." + methodName);
  }

  private static Class<?>[] classesFromArgs(final Argument[] args) {
    final Class<?>[] result = new Class<?>[args.length];
    for (int i = 0; i < args.length; ++i) {
      result[i] = args[i].clazz;
    }
    return result;
  }

  private static Object[] objectsFromArgs(final Argument[] args) {
    final Object[] result = new Object[args.length];
    for (int i = 0; i < args.length; ++i) {
      result[i] = args[i].instance;
    }
    return result;
  }

  private static Argument[] argumentsFromObjects(final Object[] args) {
    final Argument[] result = new Argument[args.length];
    for (int i = 0; i < args.length; ++i) {
      final Object object = args[i];
      result[i] = argument(object.getClass(), object);
    }
    return result;
  }

  private static boolean compatible(final Class<?> param, final Class<?> arg) {
    return ((param.equals(int.class) && arg.equals(Integer.class))
        || (param.equals(long.class) && arg.equals(Long.class))
        || (param.equals(boolean.class) && arg.equals(Boolean.class))
        || (param.equals(byte.class) && arg.equals(Byte.class))
        || (param.equals(short.class) && arg.equals(Short.class))
        || (param.equals(float.class) && arg.equals(Float.class))
        || (param.equals(double.class) && arg.equals(Double.class))
        || (param.equals(char.class) && arg.equals(Character.class)));
  }
}
