package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Shutdown GeoTools cleaner thread as of https://osgeo-org.atlassian.net/browse/GEOT-2742
 * @author Mattias Jiderhamn
 */
public class GeoToolsCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Class<?> weakCollectionCleanerClass = preventor.findClass("org.geotools.util.WeakCollectionCleaner");
    if(weakCollectionCleanerClass != null) {
      try {
        final Field DEFAULT = preventor.findField(weakCollectionCleanerClass, "DEFAULT");
        weakCollectionCleanerClass.getMethod("exit").invoke(DEFAULT.get(null));
      }
      catch (Exception ex) {
        preventor.error(ex);
      }
    }
    
  }
}