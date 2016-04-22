package se.jiderhamn.classloader.leak.prevention.cleanup;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clear {@link java.beans.Introspector} cache
 * @author Mattias Jiderhamn
 */
public class BeanIntrospectorCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    java.beans.Introspector.flushCaches(); // Clear cache of strong references
  }
}