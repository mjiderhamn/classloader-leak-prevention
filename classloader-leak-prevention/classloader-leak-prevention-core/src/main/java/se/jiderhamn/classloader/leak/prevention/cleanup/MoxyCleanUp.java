package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Cleanup for leak caused by EclipseLink MOXy
 * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=529270
 * @author Mattias Jiderhamn
 */
public class MoxyCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Class<?> helperClass = findClass(preventor, "org.eclipse.persistence.jaxb.javamodel.Helper");
    if(helperClass != null) {
      unsetField(preventor, helperClass, "COLLECTION_CLASS");
      unsetField(preventor, helperClass, "LIST_CLASS");
      unsetField(preventor, helperClass, "SET_CLASS");
      unsetField(preventor, helperClass, "MAP_CLASS");
      unsetField(preventor, helperClass, "JAXBELEMENT_CLASS");
      unsetField(preventor, helperClass, "OBJECT_CLASS");
    }

    final Class<?> propertyClass = findClass(preventor, "org.eclipse.persistence.jaxb.compiler.Property");
    if(propertyClass != null) {
      unsetField(preventor, propertyClass, "OBJECT_CLASS");
      unsetField(preventor, propertyClass, "XML_ADAPTER_CLASS");
    }
  }
  
  public Class<?> findClass(ClassLoaderLeakPreventor preventor, String className) {
    try {
      return Class.forName(className, true, preventor.getLeakSafeClassLoader());
    }
    catch (ClassNotFoundException e) {
      // Silently ignore
      return null;
    }
    catch (Exception ex) { // Example SecurityException
      preventor.warn(ex);
      return null;
    }
  }

  private void unsetField(ClassLoaderLeakPreventor preventor,
                          Class<?> clazz, String fieldName) {
    final Field field = preventor.findField(clazz, fieldName);
    if(field != null) {
      try {
        field.set(null, null);
      }
      catch (Exception e) {
        preventor.warn(e);
      }
    }
    else 
      preventor.warn("Unable to find field " + fieldName + " of class " + clazz);
  }
}