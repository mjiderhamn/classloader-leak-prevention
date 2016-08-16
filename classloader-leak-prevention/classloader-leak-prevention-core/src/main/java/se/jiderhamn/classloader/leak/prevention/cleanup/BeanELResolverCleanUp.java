package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clean for the cache of {@link javax.el.BeanELResolver}
 * @author Mattias Jiderhamn
 */
public class BeanELResolverCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    java.beans.Introspector.flushCaches(); // This must also be done          

    final Class<?> beanElResolverClass = preventor.findClass("javax.el.BeanELResolver");
    if(beanElResolverClass != null) {
      boolean cleared = false;
      try {
        final Method purgeBeanClasses = beanElResolverClass.getDeclaredMethod("purgeBeanClasses", ClassLoader.class);
        purgeBeanClasses.setAccessible(true);
        purgeBeanClasses.invoke(beanElResolverClass.newInstance(), preventor.getClassLoader());
        cleared = true;
      }
      catch (NoSuchMethodException e) {
        // Version of javax.el probably > 2.2; no real need to clear
      }
      catch (Exception e) {
        preventor.error(e);
      }
      
      if(! cleared) {
        // Fallback, if purgeBeanClasses() could not be called
        final Field propertiesField = preventor.findField(beanElResolverClass, "properties");
        if(propertiesField != null) {
          try {
            final Map<?, ?> properties = (Map<?, ?>) propertiesField.get(null);
            properties.clear();
          }
          catch (Exception e) {
            preventor.error(e);
          }
        }
      }
    }
  }
}