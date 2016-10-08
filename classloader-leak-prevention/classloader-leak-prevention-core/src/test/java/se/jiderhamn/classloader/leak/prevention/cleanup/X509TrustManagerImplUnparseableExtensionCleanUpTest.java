package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.io.File;
import javax.net.ssl.SSLContext;

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
}