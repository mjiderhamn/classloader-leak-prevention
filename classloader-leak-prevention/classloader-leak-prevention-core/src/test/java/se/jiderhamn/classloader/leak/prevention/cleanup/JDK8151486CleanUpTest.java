package se.jiderhamn.classloader.leak.prevention.cleanup;

import org.junit.Ignore;

import java.security.Permission;

/**
 * Test case for {@link JDK8151486CleanUp}
 */
@Ignore
public class JDK8151486CleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<JDK8151486CleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    System.setSecurityManager(new SecurityManager() {
      @Override
      public void checkPermission(final Permission perm) {
      }
    });

    Class.forName("java.lang.String", false, ClassLoader.getSystemClassLoader());

    // leaving the security manager will cause a leak, but not the one we're testing for
    System.setSecurityManager(null);
  }
}