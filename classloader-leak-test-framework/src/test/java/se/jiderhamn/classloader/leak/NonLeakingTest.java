package se.jiderhamn.classloader.leak;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that isn't supposed to leak, used to test the utility classes.
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class NonLeakingTest {
  
  @Test
  @Leaks(false) // Should not leak
  public void nonLeakingMethod() {
    System.out.println("Hello world!");
  }
}