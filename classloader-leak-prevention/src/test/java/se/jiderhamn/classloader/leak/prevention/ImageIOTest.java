package se.jiderhamn.classloader.leak.prevention;

import javax.imageio.ImageIO;
import javax.swing.*;

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

  @Test
  public void triggerImageIOLeak() throws Exception {
    // ImageIO.scanForPlugins() triggers two different leaks, but the preventor only removes one, so we need to avoid
    // the other one
    new ClassLoaderLeakPreventorListener().doInSystemClassLoader(new Runnable() {
      @Override
      public void run() {
        new JEditorPane("text/plain", "dummy");
      }
    });
    
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