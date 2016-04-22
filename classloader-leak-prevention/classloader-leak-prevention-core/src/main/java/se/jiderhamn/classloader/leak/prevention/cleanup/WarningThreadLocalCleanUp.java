package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.ref.Reference;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * {@link ClassLoaderPreMortemCleanUp} that does not clear {@link ThreadLocal}s to remove the leak, but only logs a 
 * warning
 * @author Mattias Jiderhamn
 */
@SuppressWarnings("unused")
public class WarningThreadLocalCleanUp extends ThreadLocalCleanUp {

  /**
   * Log not {@link ThreadLocal#remove()}ed leak as a warning. 
   */
  protected void processLeak(ClassLoaderLeakPreventor preventor, Thread thread, Reference<?> entry, 
                             ThreadLocal<?> threadLocal, Object value, String message) {
    preventor.warn(message);
  } 
}