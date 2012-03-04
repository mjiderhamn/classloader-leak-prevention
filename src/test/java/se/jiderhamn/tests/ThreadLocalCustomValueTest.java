package se.jiderhamn.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;

/**
 * ThreadLocals work the same way as WeakHashMaps; from http://docs.oracle.com/javase/6/docs/api/java/util/WeakHashMap.html
 * "Implementation note: The value objects in a WeakHashMap are held by ordinary strong references. Thus care should be 
 * taken to ensure that value objects do not strongly refer to their own keys, either directly or indirectly, 
 * since that will prevent the keys from being discarded."
 * 
 * This means that the reference chain is like this: Thread -> custom value -> custom class ->
 * custom classloader -> class containing ThreadLocal -> static ThreadLocal 
 * 
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class ThreadLocalCustomValueTest {
  
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

  private static class Value {
    
  }
  
}