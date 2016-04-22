package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;
import se.jiderhamn.classloader.leak.prevention.cleanup.DriverManagerCleanUp;

/**
 * Your JDBC driver will be registered in java.sql.DriverManager, which means that if
 * you include your JDBC driver inside your web application, there will be a reference
 * to your webapps classloader from system classes (see
 * <a href="http://java.jiderhamn.se/2012/01/01/classloader-leaks-ii-find-and-work-around-unwanted-references/">part II</a>).
 * The simple solution is to put JDBC driver on server level instead, but you can also
 * deregister the driver at application shutdown.
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * 
 * TODO {@link DriverManagerCleanUp}
 * @author Mattias Jiderhamn
 */
public class JdbcDriversInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    java.sql.DriverManager.getDrivers(); // Load initial drivers using leak safe classloader
  }
}