package se.jiderhamn.classloader.leak.prevention;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(BeanValidatorTest.Preventor.class)
public class BeanValidatorTest {
  
  @Test
  public void  validatorCacheLeaks() {
    javax.validation.Validation.buildDefaultValidatorFactory();
  }
  
  public static class Preventor implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventorListener().fixBeanValidationApiLeak();
    }
  }
}