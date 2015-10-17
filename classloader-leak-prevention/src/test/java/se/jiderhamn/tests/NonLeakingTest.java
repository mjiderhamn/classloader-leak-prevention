package se.jiderhamn.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;

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