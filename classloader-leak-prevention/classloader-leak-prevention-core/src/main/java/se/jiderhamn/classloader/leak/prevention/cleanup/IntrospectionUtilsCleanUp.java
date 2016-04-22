package se.jiderhamn.classloader.leak.prevention.cleanup;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clear IntrospectionUtils caches of Tomcat and Apache Commons Modeler
 * @author Mattias Jiderhamn
 */
public class IntrospectionUtilsCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    // Tomcat
    final Class<?> tomcatIntrospectionUtils = preventor.findClass("org.apache.tomcat.util.IntrospectionUtils");
    if(tomcatIntrospectionUtils != null) {
      try {
        tomcatIntrospectionUtils.getMethod("clear").invoke(null);
      }
      catch (Exception ex) {
        if(! preventor.isJBoss()) // JBoss includes this class, but no cache and no clear() method
          preventor.error(ex);
      }
    }

    // Apache Commons Modeler
    final Class<?> modelIntrospectionUtils = preventor.findClass("org.apache.commons.modeler.util.IntrospectionUtils");
    if(modelIntrospectionUtils != null && ! preventor.isClassLoaderOrChild(modelIntrospectionUtils.getClassLoader())) { // Loaded outside protected ClassLoader
      try {
        modelIntrospectionUtils.getMethod("clear").invoke(null);
      }
      catch (Exception ex) {
        preventor.warn("org.apache.commons.modeler.util.IntrospectionUtils needs to be cleared but there was an error, " +
            "consider upgrading Apache Commons Modeler");
        preventor.error(ex);
      }
    }
  }
}