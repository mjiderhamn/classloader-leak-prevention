package se.jiderhamn.classloader.leak.prevention.preinit;

import javax.swing.*;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * There will be a strong reference from {@link sun.awt.AppContext#contextClassLoader} to the classloader of the calls
 * to {@link sun.awt.AppContext#getAppContext()}. Avoid leak by forcing initialization using system classloader. 
 * Note that Google Web Toolkit (GWT) will trigger this leak via its use of javax.imageio.
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * @author Mattias Jiderhamn
 */
public class SunAwtAppContextInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    try {
      javax.imageio.ImageIO.getCacheDirectory(); // Will call sun.awt.AppContext.getAppContext()
      new JEditorPane("text/plain", "dummy"); // According to GitHub user dany52, the above is not enough
    }
    catch (Throwable t) {
      preventor.error(t);
      preventor.warn("Consider adding -Djava.awt.headless=true to your JVM parameters");
    }
                                      
  }
}