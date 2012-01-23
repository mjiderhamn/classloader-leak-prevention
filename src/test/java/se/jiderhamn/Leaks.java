package se.jiderhamn;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Annotation to indicate whether test case is expected to leak classloaders or not */
@Retention(RetentionPolicy.RUNTIME)
public @interface Leaks {
  /** Is this test expected to leak classloaders? */
  boolean value() default true;
}
