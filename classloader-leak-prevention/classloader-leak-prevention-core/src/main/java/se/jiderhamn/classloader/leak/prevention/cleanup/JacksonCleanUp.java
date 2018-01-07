package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Method;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clear Jackson TypeFactory cache as per https://github.com/FasterXML/jackson-databind/issues/1363
 * @author Mattias Jiderhamn
 */
public class JacksonCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Class<?> typeFactoryClass = preventor.findClass("com.fasterxml.jackson.databind.type.TypeFactory");
    if(typeFactoryClass != null && ! preventor.isLoadedInClassLoader(typeFactoryClass)) {
      try {
        final Method defaultInstance = preventor.findMethod(typeFactoryClass, "defaultInstance");
        final Method clearCache = preventor.findMethod(typeFactoryClass, "clearCache");
        if(defaultInstance != null && clearCache != null) {
          final Object defaultTypeFactory = defaultInstance.invoke(null);
          if(defaultTypeFactory != null) {
            clearCache.invoke(defaultTypeFactory);
          }
        }
      }
      catch (Exception e) {
        preventor.error(e);
      }
    }
  }
}