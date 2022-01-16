package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
    clearClassInfoCache(preventor);
  }

  /**
   * Clears the BeanInfo SoftReference-based cache introduced in JDK 9
   *
   * References:
   * * Clear explanation of the root cause: https://bugs.openjdk.java.net/browse/JDK-8207331
   * * Issue which triggered the JDK fix: https://bugs.openjdk.java.net/browse/JDK-8231454
   * * Fix commit (JDK16+ only as of now): https://github.com/openjdk/jdk/commit/2ee2b4ae
   */
  private void clearClassInfoCache(ClassLoaderLeakPreventor preventor) {
      try {
          final Class<?> classInfoClass = preventor.findClass("com.sun.beans.introspect.ClassInfo");
          if (classInfoClass == null) {
            return;
          }

          Field cacheField = preventor.findField(classInfoClass, "CACHE");
          if (cacheField == null) {
            return;  // Either pre-JDK9 or exception occurred (should have been logged as warn at this point)
          }

          Object cacheInstance = cacheField.get(null);
          if (cacheInstance == null) {
            return;
          }
          Method clearMethod = cacheInstance.getClass().getSuperclass().getDeclaredMethod("clear");
          clearMethod.invoke(cacheInstance);
      }
      catch (Exception e) {
          preventor.warn(e);
      }
  }

}
