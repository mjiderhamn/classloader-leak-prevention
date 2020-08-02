package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.security.CodeSource;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Release this classloader from Apache Commons Logging (ACL) by calling 
 * {@code LogFactory.release(getCurrentClassLoader());}
 * Use reflection in case ACL is not present.
 * Tip: Do this last, in case other shutdown procedures want to log something.
 * 
 * @author Mattias Jiderhamn
 */
public class ApacheCommonsLoggingCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Class<?> logFactory = preventor.findClass("org.apache.commons.logging.LogFactory");
    if(logFactory != null) { // Apache Commons Logging present
      try {
        final CodeSource codeSource = logFactory.getProtectionDomain().getCodeSource();
        final String codeSourceStr = codeSource != null ? codeSource.toString() : "";
        if (codeSourceStr.contains("spring-jcl")) {
          preventor.info("ignore ApacheCommonsLoggingCleanUp for spring-jcl at " + codeSource);
          return;
        }
      } catch (SecurityException ex) {
        preventor.error(ex);
      }
      preventor.info("Releasing web app classloader from Apache Commons Logging");
      try {
        logFactory.getMethod("release", java.lang.ClassLoader.class)
            .invoke(null, preventor.getClassLoader());
      }
      catch (Exception ex) {
        preventor.error(ex);
      }
    }
    
  }
}