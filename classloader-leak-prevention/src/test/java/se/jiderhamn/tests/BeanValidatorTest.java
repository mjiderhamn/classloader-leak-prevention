package se.jiderhamn.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;

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
      new ClassLoaderLeakPreventor().fixBeanValidationApiLeak();
    }
  }
}