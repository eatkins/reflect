package com.swoval.reflect;

import java.lang.instrument.Instrumentation;

/**
 * Simple java agent implementation that provides access to the loaded classes of a particular class
 * loader. The only method is getInitiatedClasses, which delegates to
 * Instrumentation#getInitiatedClasses. It will typically be used recursively, e.g.
 *
 * <pre>{@code
 * ClassLoader loader = Thread.currentThread().getContextClassLoader();
 * List<Class<?>> result = new ArrayList<>();
 * while (loader != null) {
 *   for (Class<?> clazz : com.swoval.reflect.Agent.getInitiatedClass(loader)) {
 *     result.add(clazz);
 *   }
 *   loader = loader.getParent();
 * }
 *
 * }</pre>
 *
 * The main use case is that for testing, it allows us to verify that a particular class was loaded
 * by the correct ClassLoader.
 */
public class Agent {
  private static Instrumentation instrumentation = null;

  /**
   * Get an array of loaded classes.
   *
   * @param loader the ClassLoader for which the method returns the loaded classes
   * @return the loaded classes for the provided ClassLoader.
   */
  public static Class[] getInitiatedClasses(final ClassLoader loader) {
    return instrumentation == null ? new Class[0] : instrumentation.getInitiatedClasses(loader);
  }

  /**
  * Set the global Instrumentation instance before the main method is called.
  */
  public static void premain(final String args, final Instrumentation inst) {
    instrumentation = inst;
  }
}
