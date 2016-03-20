package se.jiderhamn.classloader.leak.prevention.cleaup;

import org.apache.cxf.transport.http.CXFAuthenticator;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUpTestBase;
import se.jiderhamn.classloader.leak.prevention.cleanup.DefaultAuthenticatorCleanUp;

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