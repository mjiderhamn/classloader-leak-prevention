package se.jiderhamn.classloader.leak.prevention;

import org.apache.cxf.transport.http.CXFAuthenticator;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * Test case to verify that org.apache.cxf.transport.http.CXFAuthenticator causes classloader leaks.
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(CXFAuthenticatorTest.Prevent.class)
public class CXFAuthenticatorTest {
  
  @Test
  public void triggerCxfAuthenticatorLeak() {
    CXFAuthenticator.addAuthenticator();
  }
  
  public static class Prevent implements Runnable {
    @Override
    public void run() {
      new ClassLoaderLeakPreventorListener() {
        {
          clearDefaultAuthenticator();
        }
      };

      // This does not help, since WeakReference is not garbage collected until ClassLoader is GC:ed
      /*
      try {
        // By requesting authentication, ReferencingAuthenticator should detect that CXFAuthenticator has been removed
        Authenticator.requestPasswordAuthentication(null, 0, null, null, null);
      }
      catch (Exception e) {
        // CS:IGNORE
      }
      */
    }
  }
}