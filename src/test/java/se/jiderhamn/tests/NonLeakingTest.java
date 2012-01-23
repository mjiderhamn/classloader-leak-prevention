package se.jiderhamn.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;

/**
 * Test that isn't supposed to leak, used to test the utility classes.
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class NonLeakingTest {
  
  @Test
  public void nonLeakingMethod() {
    System.out.println("Hello world!");
  }
}