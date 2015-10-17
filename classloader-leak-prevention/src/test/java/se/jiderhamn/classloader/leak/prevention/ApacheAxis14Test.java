package se.jiderhamn.classloader.leak.prevention;

import org.apache.axis.utils.XMLUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

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
      new ClassLoaderLeakPreventor() {
        { // Initializer / "Constructor"
          clearThreadLocalsOfAllThreads();
        }
      };
    }

  }

}