package se.jiderhamn.classloader.leak.prevention;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;
import se.jiderhamn.classloader.leak.Leaks;

/**
 * Test case to verify Oracle JDBC threads are handled properly
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(OracleJdbcThreadsTest.Prevent.class)
@Ignore // Comment out this to test Oracle JDBC problems
public class OracleJdbcThreadsTest {
  
  final static ClassLoaderLeakPreventor classLoaderLeakPreventor = new ClassLoaderLeakPreventor();

  @Test
  @Leaks(value = true, dumpHeapOnError = true)
  public void startOracleThreads() throws Exception {
    classLoaderLeakPreventor.doInSystemClassLoader(new Runnable() {
      @Override
      public void run() {
        try {
          Class.forName("oracle.jdbc.driver.OracleTimeoutThreadPerVM");
          Class.forName("oracle.jdbc.driver.BlockSource$ThreadedCachingBlockSource$BlockReleaser");
        }
        catch (ClassNotFoundException e) {
          throw new RuntimeException("Is Oracle JDBC on your classpath?", e);
        }
      }
    });
  }
  
  public static class Prevent implements Runnable {
    @Override
    public void run() {
      classLoaderLeakPreventor.setStopThreads(false);
      classLoaderLeakPreventor.stopThreads();
    }
  }
}