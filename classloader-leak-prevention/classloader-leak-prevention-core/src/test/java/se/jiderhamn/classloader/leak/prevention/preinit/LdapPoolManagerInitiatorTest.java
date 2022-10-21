package se.jiderhamn.classloader.leak.prevention.preinit;

import org.junit.Assume;
import org.junit.Before;

import se.jiderhamn.classloader.leak.prevention.support.JavaVersion;

/**
 * Test cases for {@link LdapPoolManagerInitiator}
 * @author Mattias Jiderhamn
 */
public class LdapPoolManagerInitiatorTest extends PreClassLoaderInitiatorTestBase<LdapPoolManagerInitiator> {
  @Before
  public void setSystemProperty() {
    System.setProperty("com.sun.jndi.ldap.connect.pool.timeout", "1"); // Required to trigger leak
  }

    @Override
    public void firstShouldLeak() throws Exception {
        // Leak does not occur any more with JDK8+
        Assume.assumeTrue(JavaVersion.IS_JAVA_1_6 || JavaVersion.IS_JAVA_1_7);
        super.firstShouldLeak();
    }
}