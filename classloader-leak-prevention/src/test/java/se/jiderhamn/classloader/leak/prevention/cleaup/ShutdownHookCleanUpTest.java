package se.jiderhamn.classloader.leak.prevention.cleaup;

import se.jiderhamn.classloader.leak.prevention.cleanup.ShutdownHookCleanUp;

/**
 * Test case for {@link ShutdownHookCleanUp}
 * @author Mattias Jiderhamn
 */
public class ShutdownHookCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<ShutdownHookCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());
  }

  /** Dummy shutdown hook */
  private class ShutdownHookThread extends Thread {
    
  }
}