package se.jiderhamn.classloader.leak.prevention;

import javax.imageio.ImageIO;

import org.junit.Test;
import org.junit.runner.RunWith;

import se.jiderhamn.JUnitClassloaderRunner;
import se.jiderhamn.LeakPreventor;

/**
 * @author Thomas Scheffler
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(ImageIOTest.Prevent.class)
public class ImageIOTest {
  
  @Test
  public void triggerImageIOLeak() throws Exception {
    ImageIO.scanForPlugins();
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventor() {
        { // Initializer / "Constructor"
          deregisterIIOServiceProvider();
        }
      };
    }

  }

}