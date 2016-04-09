package se.jiderhamn.classloader.leak.prevention.cleanup;

import org.geotools.util.WeakCollectionCleaner;

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