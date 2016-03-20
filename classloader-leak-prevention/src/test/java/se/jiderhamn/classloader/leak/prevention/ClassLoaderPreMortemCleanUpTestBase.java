package se.jiderhamn.classloader.leak.prevention;

import java.util.Collections;

import org.junit.Test;
import se.jiderhamn.classloader.leak.Leaks;

/**
 * Abstract base class for testing {@link ClassLoaderPreMortemCleanUp} implementations.
 * TODO Move this to test framework(?)
 * @author Mattias Jiderhamn
 */
public abstract class ClassLoaderPreMortemCleanUpTestBase {
  
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
   * Concrete tests should implement this method to return a {@link ClassLoaderPreMortemCleanUp} 
   * that will clean up after the leak triggered by {@link #triggerLeak()}, so that {@link ClassLoader} can be
   * garbage collected.
   */
  protected abstract ClassLoaderPreMortemCleanUp getClassLoaderPreMortemCleanUp();

  /**
   * Concrete tests may override this method, in case they to provide a specific {@link ClassLoaderLeakPreventor}
   * to the {@link ClassLoaderPreMortemCleanUp}.
   * @return
   */
  @SuppressWarnings("WeakerAccess")
  protected ClassLoaderLeakPreventor getClassLoaderLeakPreventor() {
    return new ClassLoaderLeakPreventor(getClass().getClassLoader().getParent(),
        getClass().getClassLoader(),
        new LoggerImpl(), 
        Collections.<PreClassLoaderInitiator>emptyList(),
        Collections.<ClassLoaderPreMortemCleanUp>emptyList());
  }
  
}