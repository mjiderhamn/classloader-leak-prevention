package se.jiderhamn.classloader.leak.prevention.preinit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * sun.misc.GC.requestLatency(long), which is known to be called from
 * javax.management.remote.rmi.RMIConnectorServer.start(), will cause the current
 * contextClassLoader to be unavailable for garbage collection.
 * 
 * See http://svn.apache.org/viewvc/tomcat/trunk/java/org/apache/catalina/core/JreMemoryLeakPreventionListener.java?view=markup#l106 and 
 * http://svn.apache.org/viewvc/tomcat/trunk/java/org/apache/catalina/core/JreMemoryLeakPreventionListener.java?view=markup#l296
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * 
 * @author Mattias Jiderhamn
 */
public class SunGCInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    try {
      Class<?> gcClass = this.getGCClass();
      final Method requestLatency = gcClass.getDeclaredMethod("requestLatency", long.class);
      requestLatency.setAccessible(true);
      requestLatency.invoke(null, Long.valueOf(Long.MAX_VALUE - 1));
    }
    catch (ClassNotFoundException cnfex) {
      if(preventor.isOracleJRE())
        preventor.error(cnfex);
    }
    catch (NoSuchMethodException nsmex) {
      preventor.error(nsmex);
    }
    catch (IllegalAccessException iaex) {
      preventor.error(iaex);
    }
    catch (InvocationTargetException itex) {
      preventor.error(itex);
    }
  }

  private Class<?> getGCClass() throws ClassNotFoundException {
    try {
      return Class.forName("sun.misc.GC");
    } catch (ClassNotFoundException cnfex) {
      // Try Jre 9 classpath
      return Class.forName("sun.rmi.transport.GC");
    }
  }
}