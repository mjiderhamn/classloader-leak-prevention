package se.jiderhamn.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;
import se.jiderhamn.classloader.leak.Leaks;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;

/**
 * Test case for leaks caused by custom ThreadGroups
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(ThreadGroupTest.Prevent.class)
public class ThreadGroupTest {
  
  /** 
   * Having a custom ThreadGroup that is not destroyed will cause a leak
   */
  @Test
  @Leaks
  public void createCustomThreadGroup() {
    new ThreadGroup("customThreadGroup") {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        System.out.println("Pretend to do something");
        super.uncaughtException(t, e);
      }
    };
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventor().destroyThreadGroups();
    }
  }
  
}