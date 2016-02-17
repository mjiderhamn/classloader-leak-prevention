package se.jiderhamn.classloader.leak.prevention;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.util.Cancellable;
import org.apache.logging.log4j.core.util.ShutdownCallbackRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class Log4j2Test {

  /** Shutdown callback registered from log4j2 */
  private static Runnable shutdownCallback = null;

  @Before
  public void setUp() {
    System.setProperty("log4j.shutdownCallbackRegistry", "se.jiderhamn.classloader.leak.prevention.Log4j2Test$MyShutdownCallbackRegistry");

    // Asynch feature seems to make no difference
    System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
  }
  
  @Test
  @Leaks(true)
  public void log() {
    LogManager.getLogger(this.getClass()).error("foo");
  }

  @Test
  @Leaks(false)
  public void logAndShutDown() {
    LogManager.getLogger(this.getClass()).error("foo");
    
    assertNotNull("ShutdownCallbackRegistry registered", shutdownCallback);
    
    shutdownCallback.run();
  }

  public static class MyShutdownCallbackRegistry implements ShutdownCallbackRegistry {

    @Override
    public Cancellable addShutdownCallback(Runnable runnable) {
      System.out.println("Adding ShutdownCallbackRegistry: " + runnable);
      assertNull("Shutdown callback already registered!", shutdownCallback);
      shutdownCallback = runnable;
      return null;
    }
  }
}