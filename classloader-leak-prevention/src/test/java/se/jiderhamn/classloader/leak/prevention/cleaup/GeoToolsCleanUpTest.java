package se.jiderhamn.classloader.leak.prevention.cleaup;

import org.geotools.util.WeakCollectionCleaner;
import se.jiderhamn.classloader.leak.prevention.cleanup.GeoToolsCleanUp;

/**
 * Test case for {@link GeoToolsCleanUp}
 * @author Mattias Jiderhamn
 */
public class GeoToolsCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<GeoToolsCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {    
    WeakCollectionCleaner.DEFAULT.getReferenceQueue();
  }
}