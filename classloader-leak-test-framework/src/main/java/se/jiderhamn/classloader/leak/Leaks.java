package se.jiderhamn.classloader.leak;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Annotation to indicate whether test case is expected to leak classloaders or not */
@Retention(RetentionPolicy.RUNTIME)
public @interface Leaks {
  /** Is this test expected to leak classloaders? */
  boolean value() default true;
  
  /** 
   * Should the thread pause for a couple of seconds before throwing the test failed error?
   * Set this to true to allow some time to aquire a heap dump to track down leaks.
   */
  boolean haltBeforeError() default false;
  
  /** 
   * Set this to true to automatically generate a heap dump of classloader could not be garbage collected. Only works
   * on Sun/Oracle JVM. 
   */
  boolean dumpHeapOnError() default false;
  
}
