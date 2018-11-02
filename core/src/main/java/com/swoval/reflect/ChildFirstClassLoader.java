package com.swoval.reflect;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Provides a class loader that will find classes in the provided urls before falling back to the
 * parent class loader if it is unable to find the class in the provided classpath. System classes
 * in the java.* and sun.* packages will always be loaded by the parent. The user may force the
 * parent or child to load non system classes by providing instances of {@link Predicate} for both
 * the parent or child.
 */
public class ChildFirstClassLoader extends URLClassLoader {
  /*
   * The loaded classes cache is so that if the URLClassLoader is able to reach classes that have
   * already been loaded by one of its parents, that it doesn't try to reload the class. There can
   * be a lot of headaches with incompatible class instances being passed around without this
   * cache.
   */
  private final Map<String, Class<?>> loaded;
  private final Function<String, RequiredClassLoader> whichClassLoader;
  private final URL[] urls;
  public static Function<String, RequiredClassLoader> UNSPECIFIED_LOADER = new Function<String, RequiredClassLoader>() {
    @Override
    public RequiredClassLoader apply(final String s) {
      return RequiredClassLoader.UNSPECIFIED;
    }
  };

  /**
   * Construct an instance of {@link ChildFirstClassLoader}.
   * @param urls the {@link java.net.URL}s to include in the class path of the ChildFirstClassLoader
   * @param whichClassLoader specifies which {@link java.lang.ClassLoader} to use for a particular
   * class. If this function returns {@link RequiredClassLoader#FORCE_PARENT}, then the parent
   * {@link java.lang.ClassLoader} will be used. Otherwise the URL ClassLoader will be tried first
   * and will fall back to the parent loader.
   * @param parent the parent class loader. This will usually be the system or application class loader.
   * @param loaded the map of class name to previously loaded class instances
   */
  private ChildFirstClassLoader(
      final URL[] urls,
      final Function<String, RequiredClassLoader> whichClassLoader,
      final ClassLoader parent,
      final Map<String, Class<?>> loaded) {
    super(urls, parent);
    this.whichClassLoader = whichClassLoader;
    this.loaded = loaded;
    this.urls = urls;

    if (loaded.isEmpty()) fillCache();
  }

  /**
   * Construct an instance of {@link ChildFirstClassLoader}.
   * @param urls the {@link java.net.URL}s to include in the class path of the ChildFirstClassLoader
   */
  public ChildFirstClassLoader(final URL[] urls) {
    this(urls, UNSPECIFIED_LOADER, Thread.currentThread().getContextClassLoader(), new HashMap<>());
  }
  /**
   * Construct an instance of {@link ChildFirstClassLoader}.
   * @param urls the {@link java.net.URL}s to include in the class path of the ChildFirstClassLoader
   * @param whichClassLoader specifies which {@link java.lang.ClassLoader} to use for a particular
   * class. If this function returns {@link RequiredClassLoader#FORCE_PARENT}, then the parent
   * {@link java.lang.ClassLoader} will be used. Otherwise the URL ClassLoader will be tried first
   * and will fall back to the parent loader.
   * @param parent the parent class loader. This will usually be the system or application class loader.
   */
  public ChildFirstClassLoader(
      final URL[] urls,
      final Function<String, RequiredClassLoader> whichClassLoader,
      final ClassLoader parent) {
    this(urls, whichClassLoader, parent, new HashMap<>());
  }
  /**
   * Default constructor that allows the ChildFirstClassLoader to be used as the java system class
   * loader.
   *
   * @param parent The parent classloader.
   */
  public ChildFirstClassLoader(final ClassLoader parent) {
    this(new URL[0], UNSPECIFIED_LOADER, parent, new HashMap<>());
  }

  /**
   * Create a new ChildFirstClassLoader with updated set of URLs and a deep copy of the loaded
   * classes.
   *
   * @param urls The new set of urls
   * @return The new ChildFirstClassLoader
   */
  public ChildFirstClassLoader copy(final URL[] urls) {
    return new ChildFirstClassLoader(urls, whichClassLoader, getParent(), new HashMap<>(loaded));
  }

  /**
   * Create a new ChildFirstClassLoader with updated predicates and a deep copy of the loaded
   * classes.
   *
   * @param whichClassLoader the functioning determining which classloader to use to load a
   * particular class
   * @return the new ChildFirstClassLoader with updated whichClassLoader function.
   */
  public ChildFirstClassLoader copy(final Function<String, RequiredClassLoader> whichClassLoader) {
    return new ChildFirstClassLoader(
        urls, whichClassLoader, getParent(), new HashMap<>(loaded));
  }

  /**
   * Copies this ChildFirstClassLoader with a deep copy of the loaded classes.
   *
   * @return The new ChildClassFirstClassLoader
   */
  public ChildFirstClassLoader copy() {
    return new ChildFirstClassLoader(urls, whichClassLoader, getParent(), new HashMap<>(loaded));
  }

  @Override
  public Class<?> loadClass(final String name, final boolean resolve)
      throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> clazz = loaded.get(name);
      if (clazz != null) {
        return clazz;
      }
      if (name.startsWith("java.")
          || name.startsWith("sun.")
          || whichClassLoader.apply(name) == RequiredClassLoader.FORCE_PARENT) {
        clazz = getParent().loadClass(name);
      } else {
        try {
          clazz = findClass(name);
        } catch (final ClassNotFoundException e) {
          clazz = getParent().loadClass(name);
        }
      }
      if (resolve) {
        resolveClass(clazz);
      }
      loaded.put(name, clazz);
      return clazz;
    }
  }

  @Override
  public Class<?> loadClass(final String name) throws ClassNotFoundException {
    return loadClass(name, false);
  }

  @Override
  public String toString() {
    final StringBuilder urlString = new StringBuilder();
    urlString.append('[');
    for (URL u : urls) urlString.append(u.toString()).append(',');
    urlString.append(']');
    return "ChildFirstClassLoader(" + urlString + ", " + getParent() + ")";
  }

  /**
   * See
   * [[https://docs.oracle.com/javase/9/docs/api/java/lang/instrument/Instrumentation.html#appendToSystemClassLoaderSearch-java.util.jar.JarFile-
   * java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch]]
   *
   * @param name the class name
   */
  @SuppressWarnings("unused")
  public void appendToClassPathForInstrumentation(String name) {
    try {
      super.addURL(Paths.get(name).toUri().toURL());
    } catch (MalformedURLException e) {
      throw new InternalError(e);
    }
  }

  private void fillCache() {
    ClassLoader loader = getParent();
    while (loader != null) {
      for (Class<?> clazz : Agent.getInitiatedClasses(loader)) {
        loaded.put(clazz.getName(), clazz);
      }
      loader = loader.getParent();
    }
  }
}
