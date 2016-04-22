package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * Custom java.security.Provider loaded in your web application and registered with
 * java.security.Security.addProvider() must be unregistered with java.security.Security.removeProvider()
 * at application shutdown, or it will cause leaks.
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * @author Mattias Jiderhamn
 */
public class SecurityProvidersInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    java.security.Security.getProviders();
  }
}