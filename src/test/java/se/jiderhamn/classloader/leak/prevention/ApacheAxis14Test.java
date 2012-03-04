package se.jiderhamn.classloader.leak.prevention;

import org.apache.axis.utils.XMLUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;
import se.jiderhamn.LeakPreventor;

/**
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(ApacheAxis14Test.Prevent.class)
public class ApacheAxis14Test {
  
  @Test
  public void triggerAxis14Leak() throws Exception {
    XMLUtils.getDocumentBuilder();
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      try {
        final ClassLoaderLeakPreventor classLoaderLeakPreventor = new ClassLoaderLeakPreventor();
        classLoaderLeakPreventor.init(null); // TODO: Init before test
        classLoaderLeakPreventor.doFilter(null, null, new MockFilterChain());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

  }

}