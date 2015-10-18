package se.jiderhamn.classloader.leak;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Configure the Runnable that can be used to prevent the leak */
@Retention(RetentionPolicy.RUNTIME)
public @interface LeakPreventor {
  Class<? extends Runnable> value();
}
