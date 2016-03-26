package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Heavily inspired by org.apache.catalina.loader.WebappClassLoader.clearReferencesRmiTargets()
 * @author Mattias Jiderhamn
 */
public class RmiTargetsCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    try {
      final Class<?> objectTableClass = preventor.findClass("sun.rmi.transport.ObjectTable");
      if(objectTableClass != null) {
        clearRmiTargetsMap(preventor, (Map<?, ?>) preventor.getStaticFieldValue(objectTableClass, "objTable"));
        clearRmiTargetsMap(preventor, (Map<?, ?>) preventor.getStaticFieldValue(objectTableClass, "implTable"));
      }
    }
    catch (Exception ex) {
      preventor.error(ex);
    }
  }

  /** Iterate RMI Targets Map and remove entries loaded by protected ClassLoader */
  @SuppressWarnings("WeakerAccess")
  protected void clearRmiTargetsMap(ClassLoaderLeakPreventor preventor, Map<?, ?> rmiTargetsMap) {
    try {
      final Field cclField = preventor.findFieldOfClass("sun.rmi.transport.Target", "ccl");
      preventor.debug("Looping " + rmiTargetsMap.size() + " RMI Targets to find leaks");
      for(Iterator<?> iter = rmiTargetsMap.values().iterator(); iter.hasNext(); ) {
        Object target = iter.next(); // sun.rmi.transport.Target
        ClassLoader ccl = (ClassLoader) cclField.get(target);
        if(preventor.isClassLoaderOrChild(ccl)) {
          preventor.warn("Removing RMI Target: " + target);
          iter.remove();
        }
      }
    }
    catch (Exception ex) {
      preventor.error(ex);
    }
  }

}