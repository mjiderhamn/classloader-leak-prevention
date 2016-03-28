package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * See https://github.com/mjiderhamn/classloader-leak-prevention/issues/8
 * and https://github.com/mjiderhamn/classloader-leak-prevention/issues/23
 * and http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * @author Mattias Jiderhamn
 */
public class OracleJdbcThreadInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    // Cause oracle.jdbc.driver.OracleTimeoutPollingThread to be started with contextClassLoader = system classloader  
    try {
      Class.forName("oracle.jdbc.driver.OracleTimeoutThreadPerVM");
    }
    catch (ClassNotFoundException e) {
      // Ignore silently - class not present
    }

    // Cause oracle.jdbc.driver.BlockSource.ThreadedCachingBlockSource.BlockReleaser to be started with contextClassLoader = system classloader  
    try {
      Class.forName("oracle.jdbc.driver.BlockSource$ThreadedCachingBlockSource$BlockReleaser");
    }
    catch (ClassNotFoundException e) {
      // Ignore silently - class not present
    }
  }
}