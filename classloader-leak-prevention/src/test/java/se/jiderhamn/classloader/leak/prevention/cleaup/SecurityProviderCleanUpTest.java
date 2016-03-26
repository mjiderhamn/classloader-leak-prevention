package se.jiderhamn.classloader.leak.prevention.cleaup;

import java.security.Provider;

import se.jiderhamn.classloader.leak.prevention.cleanup.SecurityProviderCleanUp;

/**
 * Test case for {@link SecurityProviderCleanUp}
 * @author Mattias Jiderhamn
 */
public class SecurityProviderCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<SecurityProviderCleanUp> {
  private static final Provider customProvider = new Provider("Foo", 1.0, "Bar") {
    // Nothing
  };

  @Override
  protected void triggerLeak() throws Exception {
    java.security.Security.addProvider(customProvider);
  }
}