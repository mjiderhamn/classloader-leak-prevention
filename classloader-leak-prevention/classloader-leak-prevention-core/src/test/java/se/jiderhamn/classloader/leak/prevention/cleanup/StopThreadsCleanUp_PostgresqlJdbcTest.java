package se.jiderhamn.classloader.leak.prevention.cleanup;

import org.junit.After;
import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;

/**
 * Test case for leaks caused by Postgresql JDBC driver timer threads are avoided by {@link StopThreadsCleanUp}.  
 * @author Mattias Jiderhamn
 */
@PackagesLoadedOutsideClassLoader(packages = {"org.postgresql", "com.mysql"}, addToDefaults = true)
public class StopThreadsCleanUp_PostgresqlJdbcTest extends ClassLoaderPreMortemCleanUpTestBase<StopThreadsCleanUp> {

  @Override
  protected void triggerLeak() {
    org.postgresql.Driver.getSharedTimer().getTimer();
  }
  
  @After
  public void tearDown() {
    org.postgresql.Driver.getSharedTimer().releaseTimer();
  }

}
