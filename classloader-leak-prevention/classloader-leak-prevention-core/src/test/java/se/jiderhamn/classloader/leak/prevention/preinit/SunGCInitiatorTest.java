package se.jiderhamn.classloader.leak.prevention.preinit;

import org.junit.Assume;

import se.jiderhamn.classloader.leak.prevention.support.JavaVersion;

/**
 * Test cases for {@link SunGCInitiator}
 * @author Mattias Jiderhamn
 */
public class SunGCInitiatorTest extends PreClassLoaderInitiatorTestBase<SunGCInitiator> {

    @Override
    public void firstShouldLeak() throws Exception {
        // Leak does not occur any more with JDK11+ (and maybe 9 and 10 - not tested)
        Assume.assumeTrue(JavaVersion.IS_JAVA_10_OR_EARLIER);
        super.firstShouldLeak();
    }

}