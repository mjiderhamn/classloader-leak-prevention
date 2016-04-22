package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.security.Provider;

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