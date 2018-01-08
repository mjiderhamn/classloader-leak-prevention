package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Method;
import java.util.Map;

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
        if(defaultInstance != null) {
          final Object defaultTypeFactory = defaultInstance.invoke(null);
          if(defaultTypeFactory != null) {
            final Method clearCache = preventor.findMethod(typeFactoryClass, "clearCache");
            if(clearCache != null) { 
              clearCache.invoke(defaultTypeFactory);
            }
            else { // Version < 2.4.1
              final Object typeCache = preventor.getFieldValue(defaultTypeFactory, "_typeCache");
              if(typeCache instanceof Map) {
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (typeCache) {
                  ((Map) typeCache).clear();
                }
              }
            }
          }
        }
      }
      catch (Exception e) {
        preventor.error(e);
      }
    }
  }
}