package com.swoval.reflect;

import java.net.URL;
import java.util.function.Function;

/**
 * An enum-like class that is used to determine which ClassLoader to use to load a particular class.
 * See {@link ChildFirstClassLoader#ChildFirstClassLoader(URL[], Function, ClassLoader)}.
 */
public final class RequiredClassLoader {
  private RequiredClassLoader() {}

  /** Require that URLClassLoader is used. */
  @SuppressWarnings("unused")
  public static RequiredClassLoader FORCE_CHILD = new RequiredClassLoader();
  /** Require that parent ClassLoader is used. */
  public static RequiredClassLoader FORCE_PARENT = new RequiredClassLoader();
  /** Do not specify any particular ClassLoader. */
  public static RequiredClassLoader UNSPECIFIED = new RequiredClassLoader();
}
