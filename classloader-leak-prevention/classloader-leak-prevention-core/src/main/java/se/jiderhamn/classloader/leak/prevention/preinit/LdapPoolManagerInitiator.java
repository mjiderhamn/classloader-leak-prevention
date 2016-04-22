package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * The contextClassLoader of the thread loading the com.sun.jndi.ldap.LdapPoolManager class may be kept
 * from being garbage collected, since it will start a new thread if the system property
 * {@code com.sun.jndi.ldap.connect.pool.timeout} is set to a value greater than 0.
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * 
 * @author Mattias Jiderhamn
 */
public class LdapPoolManagerInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    try {
      Class.forName("com.sun.jndi.ldap.LdapPoolManager");
    }
    catch(ClassNotFoundException cnfex) {
      if(preventor.isOracleJRE())
        preventor.error(cnfex);
    }
    
  }
}