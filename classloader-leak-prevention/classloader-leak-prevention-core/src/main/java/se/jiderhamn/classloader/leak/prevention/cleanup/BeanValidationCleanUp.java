package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;
import java.util.Map;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clean up leak caused by cache in {@link javax.validation.Validation}
 * @author Mattias Jiderhamn
 */
public class BeanValidationCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Class<?> offendingClass = 
        preventor.findClass("javax.validation.Validation$DefaultValidationProviderResolver");
    if(offendingClass != null) { // Class is present on class path
      Field offendingField = preventor.findField(offendingClass, "providersPerClassloader");
      if(offendingField != null) {
        final Object providersPerClassloader = preventor.getStaticFieldValue(offendingField);
        if(providersPerClassloader instanceof Map) { // Map<ClassLoader, List<ValidationProvider<?>>> in offending code
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
          synchronized (providersPerClassloader) {
            // Fix the leak!
            ((Map<?, ?>)providersPerClassloader).remove(preventor.getClassLoader());
          }
        }
      }
    }
  }
}