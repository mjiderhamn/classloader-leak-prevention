package se.jiderhamn.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;

/**
 * Test that adds a shutdown hook
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class ShutdownHookTest {
  
  @Test
  public void nonLeakingMethod() {
    Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());
  }
  
  private class ShutdownHookThread extends Thread {
    
  }
}