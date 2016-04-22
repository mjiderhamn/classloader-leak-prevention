package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.net.ProxySelector;
import java.security.AccessController;
import java.security.PrivilegedAction;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * If default {@link java.net.ProxySelector} is loaded by protected ClassLoader it needs to be unset
 * @author Mattias Jiderhamn
 */
public class ProxySelectorCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(final ClassLoaderLeakPreventor preventor) {
    AccessController.doPrivileged(new PrivilegedAction<Void>() {
      @Override
      public Void run() {
        ProxySelector selector = ProxySelector.getDefault();
        if(preventor.isLoadedInClassLoader(selector)) {
          ProxySelector.setDefault(null);
          preventor.warn("Removing default java.net.ProxySelector: " + selector);
        }
        return null;
      }
    });
    
  }
}