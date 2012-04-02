package se.jiderhamn.classloader.leak.prevention;

import java.beans.PropertyEditorManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;
import se.jiderhamn.LeakPreventor;

/**
 * Test that the leak caused by PropertyEditorManager is cleared.
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(PropertyEditorManagerTest.Prevent.class)
public class PropertyEditorManagerTest {
  
  @Test
  public void triggerBeanELResolverLeak() throws Exception {
    PropertyEditorManager.registerEditor(String[].class, Foo.class);
  }
  
  /** PropertyEditor for testing */
  public static class Foo {
    
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventor() {
        { // Initializer / "Constructor"
          deregisterPropertyEditors();
        }
      };
    }

  }

}