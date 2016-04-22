package se.jiderhamn.classloader.leak.prevention.cleanup;

import org.apache.cxf.transport.http.CXFAuthenticator;

/**
 * Test that the leak caused by CXF custom {@link java.net.Authenticator} is cleared.
 * Thanks to Arild Froeland for the report.
 * @author Mattias Jiderhamn
 */
public class DefaultAuthenticatorCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<DefaultAuthenticatorCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    CXFAuthenticator.addAuthenticator();
  }
}