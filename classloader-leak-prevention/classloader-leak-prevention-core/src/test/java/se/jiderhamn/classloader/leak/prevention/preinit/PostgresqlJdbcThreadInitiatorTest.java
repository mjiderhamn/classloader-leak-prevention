package se.jiderhamn.classloader.leak.prevention.preinit;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

import org.junit.AfterClass;
import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;

/**
 * Test case for {@link PostgresqlJdbcThreadInitiator}
 * @author Mattias Jiderhamn
 */
@PackagesLoadedOutsideClassLoader(packages = "org.postgresql", addToDefaults = true)
public class PostgresqlJdbcThreadInitiatorTest extends PreClassLoaderInitiatorTestBase<PostgresqlJdbcThreadInitiator> {

  /** When done, deregister driver to avoid interfering with other tests */
  @AfterClass
  public static void tearDown() throws SQLException {
    final Enumeration<Driver> e = DriverManager.getDrivers();
    while(e.hasMoreElements()) {
      final Driver driver = e.nextElement();
      DriverManager.deregisterDriver(driver);
    }
  }
  
}