package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Cleanup for removing custom {@link java.util.logging.Level}s loaded within the protected class loader.
 * @author Mattias Jiderhamn
 */
public class JavaUtilLoggingLevelCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Class<?> knownLevelClass = preventor.findClass("java.util.logging.Level$KnownLevel");
    if(knownLevelClass != null) {
      final Field levelObjectField = preventor.findField(knownLevelClass, "levelObject");
      if(levelObjectField != null) {

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (knownLevelClass) {
          final Map<?, List/*<KnownLevel>*/> nameToLevels = preventor.getStaticFieldValue(knownLevelClass, "nameToLevels");
          final Map<?, List/*<KnownLevel>*/> intToLevels = preventor.getStaticFieldValue(knownLevelClass, "intToLevels");
          if(nameToLevels != null) {
            final Set/*<KnownLevel>*/ removed = process(preventor, knownLevelClass, levelObjectField, nameToLevels);
            if(intToLevels != null) {
              for(List/*<KnownLevel>*/ knownLevels : intToLevels.values()) {
                knownLevels.removeAll(removed);
              }
            }
          }
          else if(intToLevels != null) { // Use intToLevels as fallback; both should contain same values
            process(preventor, knownLevelClass, levelObjectField, intToLevels);
          }
        }
      }
      else 
        preventor.warn("Found " + knownLevelClass + " but not levelObject field");
    }
  }

  private Set/*<KnownLevel>*/ process(ClassLoaderLeakPreventor preventor, Class<?> knownLevelClass, 
                                      Field levelObjectField, Map<?, List/*<KnownLevel>*/> levelsMaps) {
    final Set/*<KnownLevel>*/ output = new HashSet<Object>();
    for(List/*<KnownLevel>*/ knownLevels : levelsMaps.values()) {
      for(Iterator/*<KnownLevel>*/ iter = knownLevels.listIterator(); iter.hasNext(); ) {
        final Object /* KnownLevel */ knownLevel = iter.next();
        final Level levelObject = preventor.getFieldValue(levelObjectField, knownLevel);
        if(preventor.isLoadedInClassLoader(levelObject)) {
          preventor.warn(Level.class.getName() + " subclass loaded by protected ClassLoader: " +
              levelObject.getClass() + "; removing from " + knownLevelClass);
          iter.remove();
          output.add(knownLevel);
        }
      }
    }
    return output;
  }
}