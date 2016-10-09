package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;

/**
 * Test case for {@link PostgresqlJdbcThreadInitiator}
 * @author Mattias Jiderhamn
 */
@PackagesLoadedOutsideClassLoader(packages = "org.postgresql", addToDefaults = true)
public class PostgresqlJdbcThreadInitiatorTest extends PreClassLoaderInitiatorTestBase<PostgresqlJdbcThreadInitiator> {

}