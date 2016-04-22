package se.jiderhamn.classloader.leak.prevention.preinit;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;
import se.jiderhamn.classloader.leak.prevention.PreventionsTestBase;

/**
 * Base class for testing {@link PreClassLoaderInitiator} implementations
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // Execute tests in name order
public class PreClassLoaderInitiatorTestBase<C extends PreClassLoaderInitiator> extends PreventionsTestBase<C> {
  
  /** First time {@link PreClassLoaderInitiator} is invoked we expect a leak */
  @SuppressWarnings("DefaultAnnotationParam")
  @Leaks(true)
  @Test
  public void firstShouldLeak() throws Exception {
    invokeInitiator();
  }

  /** Second time {@link PreClassLoaderInitiator} is invoked there should be no leak, since only first call is affected */
  @Leaks(false)
  @Test
  public void secondShouldNotLeak() throws Exception {
    invokeInitiator();
  }

  private void invokeInitiator() throws IllegalAccessException, InstantiationException, InterruptedException {
    getTestedImplementation().doOutsideClassLoader(getClassLoaderLeakPreventor());
  }

  /** Use the {@link ClassLoader} of the test as the leak safe classloader, to test leaks. */
  @Override
  protected ClassLoader getLeakSafeClassLoader() {
    return getClass().getClassLoader();
  }
}