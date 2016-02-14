package se.jiderhamn.classloader.leak.prevention;

import javax.imageio.ImageIO;
import javax.swing.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * @author Thomas Scheffler
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(ImageIOTest.Prevent.class)
public class ImageIOTest {
  
  // ImageIO.scanForPlugins() triggers two different leaks, but the preventor only removes one, so we need to avoid
  // the other one by running some code in parent classloader
  @Before
  public void systemClassLoader() {
    new JEditorPane("text/plain", "dummy"); // java.awt.Toolkit.getDefaultToolkit()
  }

  @Test
  public void triggerImageIOLeak() throws Exception {
    ImageIO.scanForPlugins();
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventorListener() {
        { // Initializer / "Constructor"
          deregisterIIOServiceProvider();
        }
      };
    }

  }

}