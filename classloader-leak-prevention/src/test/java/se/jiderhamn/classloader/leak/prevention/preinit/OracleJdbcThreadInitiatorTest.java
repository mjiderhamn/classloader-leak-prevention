package se.jiderhamn.classloader.leak.prevention.preinit;

/**
 * Test cases for {@link OracleJdbcThreadInitiator}
 * @author Mattias Jiderhamn
 */
public class OracleJdbcThreadInitiatorTest extends PreClassLoaderInitiatorTestBase<OracleJdbcThreadInitiator> {
  // TODO Needs oracle.* to be excluded from RedefiningClassLoader https://github.com/mjiderhamn/classloader-leak-prevention/issues/44
}