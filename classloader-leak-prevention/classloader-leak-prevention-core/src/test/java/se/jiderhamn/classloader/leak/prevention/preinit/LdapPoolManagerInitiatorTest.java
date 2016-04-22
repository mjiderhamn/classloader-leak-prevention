package se.jiderhamn.classloader.leak.prevention.preinit;

import org.junit.Before;

/**
 * Test cases for {@link LdapPoolManagerInitiator}
 * @author Mattias Jiderhamn
 */
public class LdapPoolManagerInitiatorTest extends PreClassLoaderInitiatorTestBase<LdapPoolManagerInitiator> {
  @Before
  public void setSystemProperty() {
    System.setProperty("com.sun.jndi.ldap.connect.pool.timeout", "1"); // Required to trigger leak
  }
}