package se.jiderhamn.classloader.leak.prevention.cleanup;

import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;

/**
 * Test case for {@link DriverManagerCleanUp}
 * @author Mattias Jiderhamn
 */
@PackagesLoadedOutsideClassLoader(packages = "org.postgresql", addToDefaults = true) // Postgresql driver not part of test
public class DriverManagerCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<DriverManagerCleanUp> {

  @Override
  protected void triggerLeak() throws ClassNotFoundException {
    Class.forName("com.mysql.jdbc.Driver");
  }

}