package se.jiderhamn.classloader.leak.prevention.cleanup;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;
import se.jiderhamn.classloader.leak.prevention.PreventionsTestBase;

/**
 * Abstract base class for testing {@link ClassLoaderPreMortemCleanUp} implementations.
 * TODO Move this to test framework(?)
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public abstract class ClassLoaderPreMortemCleanUpTestBase<C extends ClassLoaderPreMortemCleanUp> extends PreventionsTestBase<C> {
  
  /** 
   * Test case that verifies that {@link #triggerLeak()} indeed does cause a leak, in case the
   * {@link ClassLoaderPreMortemCleanUp} is not executed.
   */
  @SuppressWarnings("DefaultAnnotationParam")
  @Test
  @Leaks(true) // Without the cleanup we should expect a leak
  public void triggerLeakWithoutCleanup() throws Exception {
    triggerLeak();
  }
  
  @Test
  @Leaks(false) // After having run the cleanup, there should be no leak
  public void cleanUpAfterTriggeringLeak() throws Exception {
    triggerLeak();
    getClassLoaderPreMortemCleanUp().cleanUp(getClassLoaderLeakPreventor());
  }
  
  /** Concrete tests should implement this method to trigger the leak */
  protected abstract void triggerLeak() throws Exception;
  
  /** 
   * Concrete tests may override this method to return a {@link ClassLoaderPreMortemCleanUp} 
   * that will clean up after the leak triggered by {@link #triggerLeak()}, so that {@link ClassLoader} can be
   * garbage collected.
   * The default implementation will use the generics parameter type information.
   */
  @SuppressWarnings("WeakerAccess")
  protected C getClassLoaderPreMortemCleanUp() throws IllegalAccessException, InstantiationException {
    return getTestedImplementation();
  }

}