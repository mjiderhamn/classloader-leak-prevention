package se.jiderhamn.classloader.leak.prevention;

import java.beans.PropertyEditorManager;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * Test that the leak caused by PropertyEditorManager is cleared.
 * This has been fixed in Java 7.
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(PropertyEditorManagerTest.Prevent.class)
public class PropertyEditorManagerTest {
  
  @Ignore
  @Test
  public void triggerBeanELResolverLeak() throws Exception {
    PropertyEditorManager.registerEditor(String[].class, Foo.class);
  }
  
  /** PropertyEditor for testing */
  public static class Foo {
    
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventorListener() {
        { // Initializer / "Constructor"
          deregisterPropertyEditors();
        }
      };
    }

  }

}