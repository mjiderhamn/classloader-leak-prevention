package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clean up caches in {@link ResourceBundle}
 * @author Mattias Jiderhamn
 */
public class ResourceBundleCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    try {
      try { // First try Java 1.6 method
        final Method clearCache16 = ResourceBundle.class.getMethod("clearCache", ClassLoader.class);
        preventor.debug("Since Java 1.6+ is used, we can call " + clearCache16);
        clearCache16.invoke(null, preventor.getClassLoader());
      }
      catch (NoSuchMethodException e) {
        // Not Java 1.6+, we have to clear manually
        final Map<?,?> cacheList = preventor.getStaticFieldValue(ResourceBundle.class, "cacheList"); // Java 5: SoftCache extends AbstractMap
        final Iterator<?> iter = cacheList.keySet().iterator();
        Field loaderRefField = null;
        while(iter.hasNext()) {
          Object key = iter.next(); // CacheKey
          
          if(loaderRefField == null) { // First time
            loaderRefField = key.getClass().getDeclaredField("loaderRef");
            loaderRefField.setAccessible(true);
          }
          WeakReference<ClassLoader> loaderRef = (WeakReference<ClassLoader>) loaderRefField.get(key); // LoaderReference extends WeakReference
          ClassLoader classLoader = loaderRef.get();
          
          if(preventor.isClassLoaderOrChild(classLoader)) {
            preventor.info("Removing ResourceBundle from cache: " + key);
            iter.remove();
          }
          
        }
      }
    }
    catch(Exception ex) {
      preventor.error(ex);
    }
    
    // (CacheKey of java.util.ResourceBundle.NONEXISTENT_BUNDLE will point to first referring classloader...)
  }
}