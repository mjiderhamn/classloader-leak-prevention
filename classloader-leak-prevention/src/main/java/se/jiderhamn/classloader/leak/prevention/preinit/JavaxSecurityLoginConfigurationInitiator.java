package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * The class javax.security.auth.login.Configuration will keep a strong static reference to the
 * contextClassLoader of Thread from which the class is loaded.
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * 
 * @author Mattias Jiderhamn
 */
public class JavaxSecurityLoginConfigurationInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    try {
      Class.forName("javax.security.auth.login.Configuration", true, preventor.getLeakSafeClassLoader());
    }
    catch (ClassNotFoundException e) {
      // Do nothing
    }
  }
}