package se.jiderhamn.classloader.leak.prevention;

import org.apache.cxf.transport.http.CXFAuthenticator;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;
import se.jiderhamn.LeakPreventor;

/**
 * Test that the leak caused by CXF custom java.net.Authenticator is cleared.
 * Thanks to Arild Froeland for the report.
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(ApacheCxfTest.Prevent.class)
public class ApacheCxfTest {
  
  @Test
  public void triggerCxfAuthenticatorLeak() throws Exception {
    CXFAuthenticator.addAuthenticator();
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventor() {
        { // Initializer / "Constructor"
          clearDefaultAuthenticator();
        }
      };
    }

  }

}