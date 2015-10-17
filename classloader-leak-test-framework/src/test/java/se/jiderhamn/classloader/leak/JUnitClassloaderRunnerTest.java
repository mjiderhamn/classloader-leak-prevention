package se.jiderhamn.classloader.leak;

import java.io.FileNotFoundException;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test makes sure that the {@link JUnitClassloaderRunner} does not keep a reference to the classloader in case
 * an exception is thrown
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class JUnitClassloaderRunnerTest {
  
  @Test(expected = Exception.class) // FileNotFoundException wrapped in RuntimeException 
  @Leaks(false)
  public void throwException() throws Exception {
    throw new FileNotFoundException("foo.txt");
  }
}