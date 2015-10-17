package se.jiderhamn.classloader.leak.prevention;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * Verify that ClassLoaderLeakPreventor can deregister JDBC drivers  
 * 
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(DriverManagerTest.Prevent.class)
public class DriverManagerTest {
  
  @Test
  public void registerDriver() throws Exception {
    Class.forName("com.mysql.jdbc.Driver");
  }

  public static class Prevent implements Runnable {
    public void run() {
      try {
        final ClassLoaderLeakPreventor classLoaderLeakPreventor = new ClassLoaderLeakPreventor();
        classLoaderLeakPreventor.deregisterJdbcDrivers();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

  }
  
}