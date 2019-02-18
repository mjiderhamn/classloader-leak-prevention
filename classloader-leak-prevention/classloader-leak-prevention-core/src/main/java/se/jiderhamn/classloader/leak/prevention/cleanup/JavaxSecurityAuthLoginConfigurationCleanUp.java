package se.jiderhamn.classloader.leak.prevention.cleanup;

import javax.security.auth.login.Configuration;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Cleanup for removing custom {@link javax.security.auth.login.Configuration}s loaded within the protected class loader.
 * @author Nikos Epping
 */
public class JavaxSecurityAuthLoginConfigurationCleanUp implements ClassLoaderPreMortemCleanUp {

  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    if (preventor.isLoadedInClassLoader(Configuration.getConfiguration())) {
      Configuration.setConfiguration(null);
    }
  }
}
