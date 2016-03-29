package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;

/**
 * Test cases for {@link OracleJdbcThreadInitiator}
 * @author Mattias Jiderhamn
 */
@PackagesLoadedOutsideClassLoader(packages = "oracle.", addToDefaults = true)
public class OracleJdbcThreadInitiatorTest extends PreClassLoaderInitiatorTestBase<OracleJdbcThreadInitiator> {
  
}