package se.jiderhamn.classloader.leak.prevention;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;
import se.jiderhamn.LeakPreventor;

/**
 * 
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(ThreadLocalCustomValueCleanupTest.Preventor.class)
public class ThreadLocalCustomValueCleanupTest {
  
  private static final ThreadLocal<Value> threadLocalWithCustomValue = new ThreadLocal<Value>();
  
  @Test
  public void setCustomThreadLocalValue() {
    threadLocalWithCustomValue.set(new Value());
  }
  
  /** 
   * This may - and will - also leak, since the values aren't removed even when the weak referenced key is 
   * garbage collected. See java.lang.ThreadLocal.ThreadLocalMap JavaDoc: "However, since reference queues are not
   * used, stale entries are guaranteed to be removed only when the table starts running out of space."
   */
  @Test
  public void setCustomThreadLocalValueInNonStaticThreadLocal() {
    new ThreadLocal<Value>().set(new Value());
  }

  /** Custom value class to create leak */
  private static class Value {
    
  }
  
  public static class Preventor implements Runnable {
    public void run() {
      new ThreadLocalPrevention().clearThreadLocals();
    }
  }
  
  private static class ThreadLocalPrevention extends ClassLoaderLeakPreventor {
    public void clearThreadLocals() {
      forEachThreadLocalInCurrentThread(new ClearingThreadLocalProcessor());
    }
  }
}