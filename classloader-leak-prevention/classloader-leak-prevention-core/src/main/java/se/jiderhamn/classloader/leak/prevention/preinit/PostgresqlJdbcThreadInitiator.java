package se.jiderhamn.classloader.leak.prevention.preinit;

import java.lang.reflect.Method;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * {@link PreClassLoaderInitiator} that starts 'PostgreSQL-JDBC-SharedTimer-*' threads
 * outside the classloader we want to protect. 
 * See https://github.com/mjiderhamn/classloader-leak-prevention/issues/60
 * @author Mattias Jiderhamn
 */
public class PostgresqlJdbcThreadInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    // Invoke org.postgresql.Driver.getSharedTimer().getTimer() if Postgresql JDBC is on the classpath
    final Method getSharedTimer = preventor.findMethod("org.postgresql.Driver", "getSharedTimer");
    if(getSharedTimer != null) { // Postgresql seems to be on the classpath
      try {
        getSharedTimer.invoke(null);
      }
      catch (Exception e) {
        preventor.error(e);
      }
    }
  }
}