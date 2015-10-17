package se.jiderhamn.classloader.leak.prevention;

import java.security.Provider;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;
import se.jiderhamn.LeakPreventor;

/**
 * Custom security provider leaks; can be fixed
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(SecurityProviderTest.Preventor.class)
public class SecurityProviderTest {
  
  private static final Provider customProvider = new Provider("Foo", 1.0, "Bar") {
    // Nothing
  };
  
  @Test
  public void  customProviderLeaks() {
    java.security.Security.addProvider(customProvider);
  }
  
  public static class Preventor implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventor() {
        { // Initializer / "Constructor"
          super.deregisterSecurityProviders();
        }
      };
    }
  }
}