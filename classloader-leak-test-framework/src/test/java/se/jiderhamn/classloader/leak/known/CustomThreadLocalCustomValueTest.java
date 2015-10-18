package se.jiderhamn.classloader.leak.known;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;

/**
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class CustomThreadLocalCustomValueTest {
  
  private static final ThreadLocal<Value> customThreadLocal = new ThreadLocal<Value>() {
    /** Override method to create an anonymous subclass loaded by our classloader */
    @Override
    protected Value initialValue() {
      return new Value();
    }
  };
  
  @Test
  public void setCustomThreadLocalValue() {
    customThreadLocal.set(new Value());
  }

  /** Custom value class, that will prevent garbage collection */
  private static class Value {
    
  }
  
  // TODO: Preventor
}