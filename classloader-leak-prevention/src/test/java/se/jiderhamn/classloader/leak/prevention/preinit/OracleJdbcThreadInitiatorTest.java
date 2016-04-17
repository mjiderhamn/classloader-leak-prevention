package se.jiderhamn.classloader.leak.prevention.preinit;

import org.junit.Ignore;
import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;

/**
 * Test cases for {@link OracleJdbcThreadInitiator}
 * @author Mattias Jiderhamn
 */
@Ignore // Oracle JDBC driver needs to be available for this test
@PackagesLoadedOutsideClassLoader(packages = "oracle.", addToDefaults = true)
public class OracleJdbcThreadInitiatorTest extends PreClassLoaderInitiatorTestBase<OracleJdbcThreadInitiator> {
  
}