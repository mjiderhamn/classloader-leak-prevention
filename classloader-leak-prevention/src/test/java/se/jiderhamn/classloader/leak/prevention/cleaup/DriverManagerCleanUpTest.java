package se.jiderhamn.classloader.leak.prevention.cleaup;

import se.jiderhamn.classloader.leak.prevention.cleanup.DriverManagerCleanUp;

/**
 * Test case for {@link DriverManagerCleanUp}
 * @author Mattias Jiderhamn
 */
public class DriverManagerCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<DriverManagerCleanUp> {

  @Override
  protected void triggerLeak() throws ClassNotFoundException {
    Class.forName("com.mysql.jdbc.Driver");
  }

}