package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.security.Provider;

/**
 * Test that the leak caused by {@link javax.crypto.JceSecurity} is cleared.
 * Thanks to Paul Kiman for the report.
 * @author Mattias Jiderhamn
 */
public class JceSecurityCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<JceSecurityCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    try {
      // Alternative classes: KeyAgreement, KeyGenerator, ExemptionMechanism
      MyProvider myProvider = new MyProvider("foo", 1.0, "bar");
      javax.crypto.Mac.getInstance("baz", myProvider);
    }
    catch (SecurityException e) { // CS:IGNORE
    }
  }
  
  /** Custom {@link Provider}, to be put in {@link javax.crypto.JceSecurity} caches */
  public static class MyProvider extends Provider {
    public MyProvider(String name, double version, String info) {
      super(name, version, info);
    }

    @Override
    public synchronized Service getService(String type, String algorithm) {
      return new Service(this, "type", "algorithm", "className", null, null);
    }
  }
}