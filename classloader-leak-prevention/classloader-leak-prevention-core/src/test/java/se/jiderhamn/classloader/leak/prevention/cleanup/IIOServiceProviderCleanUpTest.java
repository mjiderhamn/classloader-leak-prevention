package se.jiderhamn.classloader.leak.prevention.cleanup;

import javax.imageio.ImageIO;
import javax.swing.*;

import org.junit.Before;

/**
 * Test case for {@link IIOServiceProviderCleanUp}
 * @author Thomas Scheffler (1.x version)
 * @author Mattias Jiderhamn
 */
public class IIOServiceProviderCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<IIOServiceProviderCleanUp> {
  // ImageIO.scanForPlugins() triggers two different leaks, but the preventor only removes one, so we need to avoid
  // the other one by running some code in parent classloader
  @Before
  public void systemClassLoader() {
    new JEditorPane("text/plain", "dummy"); // java.awt.Toolkit.getDefaultToolkit()
  }

  @Override
  protected void triggerLeak() throws Exception {
    ImageIO.scanForPlugins();

  }
}