package se.jiderhamn.classloader.leak.prevention.cleanup;

import com.fasterxml.jackson.databind.type.TypeFactory;
import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;

/**
 * Test cases for {@link JacksonCleanUp}
 * @author Mattias Jiderhamn
 */
@PackagesLoadedOutsideClassLoader(packages = {"com.fasterxml.jackson.databind"}, addToDefaults = true)
public class JacksonCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<JacksonCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    TypeFactory.defaultInstance().constructSimpleType(JacksonCleanUpTest.class, null);
  }

}