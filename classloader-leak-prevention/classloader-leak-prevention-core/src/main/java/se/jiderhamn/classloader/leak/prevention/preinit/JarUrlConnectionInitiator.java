package se.jiderhamn.classloader.leak.prevention.preinit;

import java.net.URL;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * The caching mechanism of JarURLConnection can prevent JAR files to be reloaded. See
 * <a href="http://bugs.sun.com/bugdatabase/view_bug.do;jsessionid=b8cdbff5fd7a2482996ac1c7f708?bug_id=4405789">this bug report</a>.
 * It is not entirely clear whether this will actually leak classloaders.
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * 
 * @author Mattias Jiderhamn
 */
public class JarUrlConnectionInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    // This probably does not affect classloaders, but prevents some problems with .jar files
    try {
      // URL needs to be well-formed, but does not need to exist
      new URL("jar:file://dummy.jar!/").openConnection().setDefaultUseCaches(false);
    }
    catch (Exception ex) {
      preventor.error(ex);
    }
    
  }
}