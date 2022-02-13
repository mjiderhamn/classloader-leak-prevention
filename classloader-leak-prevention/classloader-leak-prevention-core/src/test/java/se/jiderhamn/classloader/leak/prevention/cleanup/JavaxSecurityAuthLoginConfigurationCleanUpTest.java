package se.jiderhamn.classloader.leak.prevention.cleanup;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

/**
 * Test case for {@link JavaxSecurityAuthLoginConfigurationCleanUp}
 * @author Nikos Epping
 */
public class JavaxSecurityAuthLoginConfigurationCleanUpTest
    extends ClassLoaderPreMortemCleanUpTestBase<JavaxSecurityAuthLoginConfigurationCleanUp> {

  @Override
  protected void triggerLeak() throws Exception {
    Configuration.setConfiguration(new MockConfiguration());
  }

  private class MockConfiguration extends Configuration {
    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
      return null;
    }
  }
}
