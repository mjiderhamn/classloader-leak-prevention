package se.jiderhamn.classloader;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 
 * Annotation that defines what packages packages to be ignored by {@link RedefiningClassLoader}, so that they will 
 * be loaded by the parent/system {@link ClassLoader}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PackagesLoadedOutsideClassLoader {
  
  /** Packages to be ignored by {@link RedefiningClassLoader}, on the form "foo.bar." (note the ending dot!) */
  String[] packages();
  
  /** 
   * Should the packages in {@link #packages()} be added to {@link RedefiningClassLoader#DEFAULT_IGNORED_PACKAGES}?
   * {@code false} means {@link #packages()} will instead replace, and {@link RedefiningClassLoader#DEFAULT_IGNORED_PACKAGES}
   * will be redefined by {@link RedefiningClassLoader} unless specified by {@link #packages()}.
   */
  boolean addToDefaults() default false;
}
