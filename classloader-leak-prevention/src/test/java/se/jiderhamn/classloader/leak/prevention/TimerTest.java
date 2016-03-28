package se.jiderhamn.classloader.leak.prevention;

import java.util.Collection;
import java.util.Timer;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(TimerTest.Preventor.class)
public class TimerTest {
  
  // private Timer timer;
  
  /** 
   * Having a custom ThreadLocal with at non-custom value does not leak, since the key in the ThreadLocalMap is weak
   */
  @Test
  public void createTimer() throws IllegalAccessException, NoSuchFieldException {
    new Timer("MyTimer"); // Create new Timer to spawn new TimerThread
  }
  
  public static class Preventor implements Runnable {
    public void run() {
      final TimerThreadLeakPreventor timerThreadLeakPreventor = new TimerThreadLeakPreventor();
      final Collection<Thread> threads = timerThreadLeakPreventor.getAllThreads();
      for(Thread thread : threads) {
        if("java.util.TimerThread".equals(thread.getClass().getName())) {
          System.out.println(thread + " is a TimerThread");
          timerThreadLeakPreventor.stopTimerThread(thread);
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
  
  private static class TimerThreadLeakPreventor extends ClassLoaderLeakPreventorListener {
    public Collection<Thread> getAllThreads() {
      return classLoaderLeakPreventor.getAllThreads();
    }
    
    public void stopTimerThread(Thread thread) {
      super.stopTimerThread(thread);
    }
  }
}