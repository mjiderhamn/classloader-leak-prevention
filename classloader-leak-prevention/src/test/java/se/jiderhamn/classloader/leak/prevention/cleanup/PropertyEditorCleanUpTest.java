package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.beans.PropertyEditorManager;

import org.junit.Ignore;

/**
 * Test cases for {@link PropertyEditorCleanUp}
 * @author Mattias Jiderhamn
 */
@Ignore // No longer leaks in Java 7+
public class PropertyEditorCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<PropertyEditorCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    PropertyEditorManager.registerEditor(String[].class, Foo.class); // Before Java 7, this caused a leak
  }

  /** PropertyEditor for testing */
  public static class Foo {
    
  }
}