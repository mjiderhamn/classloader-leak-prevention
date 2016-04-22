package se.jiderhamn.classloader.leak.prevention;

import java.util.Collection;
import java.util.Timer;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;
import se.jiderhamn.classloader.leak.prevention.cleanup.StopThreadsCleanUp;

/**
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(StopThreadsCleanUp_TimerTest.Preventor.class)
public class StopThreadsCleanUp_TimerTest {
  
  /** 
   * Having a custom ThreadLocal with at non-custom value does not leak, since the key in the ThreadLocalMap is weak
   */
  @Test
  public void createTimer() throws IllegalAccessException, NoSuchFieldException {
    new Timer("MyTimer"); // Create new Timer to spawn new TimerThread
    Thread.yield(); // Allow the Timer thread to start
  }
  
  public static class Preventor implements Runnable {
    public void run() {
      ClassLoaderLeakPreventorFactory factory = new ClassLoaderLeakPreventorFactory();
      final ClassLoaderLeakPreventor preventor = factory.newLeakPreventor();

      final TimerThreadsCleanUp timerThreadsCleanUp = new TimerThreadsCleanUp();

      final Collection<Thread> threads = preventor.getAllThreads();
      for(Thread thread : threads) {
        if("java.util.TimerThread".equals(thread.getClass().getName())) {
          System.out.println(thread + " is a TimerThread");
          timerThreadsCleanUp.stopTimerThread(preventor, thread);
          try {
            thread.join(10000); // Give thread up to 10 seconds to finish
          }
          catch (InterruptedException e) {
            // Silently ignore
          }
        }
      }
    }
  }
  
  private static class TimerThreadsCleanUp extends StopThreadsCleanUp {
    public TimerThreadsCleanUp() {
      super(true, true);
    }

    /** Change visibility */
    @Override
    public void stopTimerThread(ClassLoaderLeakPreventor preventor, Thread thread) {
      super.stopTimerThread(preventor, thread);
    }
  }

}