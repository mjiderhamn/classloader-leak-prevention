package se.jiderhamn.classloader.leak.prevention;

import org.geotools.util.WeakCollectionCleaner;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * Test case that prevention for GeoTools leak (https://osgeo-org.atlassian.net/browse/GEOT-2742) is working
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(GeoToolsTest.Preventor.class)
public class GeoToolsTest {
  
  @Test
  public void weakCollectionCleaner() {
    WeakCollectionCleaner.DEFAULT.getReferenceQueue();
  }
  
  public static class Preventor implements Runnable {
    @Override
    public void run() {
      new ClassLoaderLeakPreventorListener() { { // "Constructor" 
        fixGeoToolsLeak();
      }
      };
    }
  }
}