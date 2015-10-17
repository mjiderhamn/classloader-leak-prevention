package se.jiderhamn.classloader.leak.prevention;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

import static org.junit.Assert.fail;

/**
 * Test that adds a shutdown hook
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(ShutdownHookTest.Prevent.class)
public class ShutdownHookTest {
  
  /** Keep reference to shutdown hook, in order to be able to fix the leak */
  private static ShutdownHookThread shutdownHook;
  
  @Test
  public void nonLeakingMethod() {
    shutdownHook = new ShutdownHookThread();
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }
  
  /** Dummy shutdown hook */
  private class ShutdownHookThread extends Thread {
    
  }
  
  /** Prevent leak by removing shutdown hook */
  public static class Prevent implements Runnable {
    public void run() {
      if(shutdownHook == null)
        fail("No reference to shutdown hook - cannot unregister");
      else {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdownHook = null;
      }
    }
  }
}