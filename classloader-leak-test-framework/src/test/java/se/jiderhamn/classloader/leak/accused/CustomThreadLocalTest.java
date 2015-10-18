package se.jiderhamn.classloader.leak.accused;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;

/**
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class CustomThreadLocalTest {
  
  private static final ThreadLocal<Integer> customThreadLocal = new ThreadLocal<Integer>() {
    /** Override method to create an anonymous subclass loaded by our classloader */
    @Override
    protected Integer initialValue() {
      return Integer.MAX_VALUE;
    }
  };
  
  /** 
   * Having a custom ThreadLocal with at non-custom value does not leak, since the key in the ThreadLocalMap is weak
   */
  @Test
  @Leaks(false)
  public void setValueOfCustomThreadLocal() {
    customThreadLocal.set(1);
  }
}