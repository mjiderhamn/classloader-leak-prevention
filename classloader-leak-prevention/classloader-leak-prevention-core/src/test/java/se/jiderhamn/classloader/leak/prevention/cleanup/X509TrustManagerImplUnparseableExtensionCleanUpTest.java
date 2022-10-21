package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.io.File;
import javax.net.ssl.SSLContext;

import org.junit.Assume;

import se.jiderhamn.classloader.leak.prevention.support.JavaVersion;

/**
 * Test case for {@link X509TrustManagerImplUnparseableExtensionCleanUp}
 * @author Mattias Jiderhamn
 */
public class X509TrustManagerImplUnparseableExtensionCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<X509TrustManagerImplUnparseableExtensionCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    final File keystore = new File(this.getClass().getClassLoader().getResource("./spi-cacert-2008.keystore").toURI());
    System.setProperty("javax.net.ssl.trustStore", keystore.getAbsolutePath());
    SSLContext.getDefault();
  }

    @Override
    public void triggerLeakWithoutCleanup() throws Exception {
        // Leak does not occur any more with JDK17+ (and maybe some versions above JDK11 - not tested) 
        Assume.assumeTrue(JavaVersion.IS_JAVA_16_OR_EARLIER);
        super.triggerLeakWithoutCleanup();
    }

}