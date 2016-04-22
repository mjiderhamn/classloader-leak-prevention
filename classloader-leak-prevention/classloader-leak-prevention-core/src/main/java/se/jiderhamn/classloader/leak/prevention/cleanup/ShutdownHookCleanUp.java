package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.util.ArrayList;
import java.util.Map;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Find and deregister shutdown hooks. Will by default execute the hooks immediately after removing them.
 * @author Mattias Jiderhamn
 */
public class ShutdownHookCleanUp implements ClassLoaderPreMortemCleanUp {

  /** Default no of milliseconds to wait for shutdown hook to finish execution */
  public static final int SHUTDOWN_HOOK_WAIT_MS_DEFAULT = 10 * 1000; // 10 seconds

  /** Should shutdown hooks registered from the application be executed at application shutdown? */
  @SuppressWarnings("WeakerAccess")
  protected boolean executeShutdownHooks = true;

  /** 
   * No of milliseconds to wait for shutdown hooks to finish execution, before stopping them.
   * If set to -1 there will be no waiting at all, but Thread is allowed to run until finished.
   */
  @SuppressWarnings("WeakerAccess")
  protected int shutdownHookWaitMs = SHUTDOWN_HOOK_WAIT_MS_DEFAULT;

  /** Constructor for test case */
  @SuppressWarnings("unused")
  public ShutdownHookCleanUp() {
    this(true, SHUTDOWN_HOOK_WAIT_MS_DEFAULT);
  }

  public ShutdownHookCleanUp(boolean executeShutdownHooks, int shutdownHookWaitMs) {
    this.executeShutdownHooks = executeShutdownHooks;
    this.shutdownHookWaitMs = shutdownHookWaitMs;
  }

  public void setExecuteShutdownHooks(boolean executeShutdownHooks) {
    this.executeShutdownHooks = executeShutdownHooks;
  }

  public void setShutdownHookWaitMs(int shutdownHookWaitMs) {
    this.shutdownHookWaitMs = shutdownHookWaitMs;
  }

  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    
    // We will not remove known shutdown hooks, since loading the owning class of the hook,
    // may register the hook if previously unregistered 
    final Map<Thread, Thread> shutdownHooks = preventor.getStaticFieldValue("java.lang.ApplicationShutdownHooks", "hooks");
    if(shutdownHooks != null) { // Could be null during JVM shutdown, which we already avoid, but be extra precautious
      // Iterate copy to avoid ConcurrentModificationException
      for(Thread shutdownHook : new ArrayList<Thread>(shutdownHooks.keySet())) {
        if(preventor.isThreadInClassLoader(shutdownHook)) { // Planned to run in protected ClassLoader
          removeShutdownHook(preventor, shutdownHook);
        }
      }
    }
  }

  /** Deregister shutdown hook and execute it immediately */
  @SuppressWarnings({"deprecation", "WeakerAccess"})
  protected void removeShutdownHook(ClassLoaderLeakPreventor preventor, Thread shutdownHook) {
    final String displayString = "'" + shutdownHook + "' of type " + shutdownHook.getClass().getName();
    preventor.error("Removing shutdown hook: " + displayString);
    Runtime.getRuntime().removeShutdownHook(shutdownHook);

    if(executeShutdownHooks) { // Shutdown hooks should be executed
      
      preventor.info("Executing shutdown hook now: " + displayString);
      // Make sure it's from protected ClassLoader
      shutdownHook.start(); // Run cleanup immediately
      
      if(shutdownHookWaitMs > 0) { // Wait for shutdown hook to finish
        try {
          shutdownHook.join(shutdownHookWaitMs); // Wait for thread to run
        }
        catch (InterruptedException e) {
          // Do nothing
        }
        if(shutdownHook.isAlive()) {
          preventor.warn(shutdownHook + "still running after " + shutdownHookWaitMs + " ms - Stopping!");
          shutdownHook.stop();
        }
      }
    }
  }

}