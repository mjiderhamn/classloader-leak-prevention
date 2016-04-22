package se.jiderhamn.classloader.leak.prevention.cleanup;

import org.apache.axis.utils.XMLUtils;
import org.junit.Ignore;

/**
 * Test case for {@link BeanValidationCleanUp}
 * @author Mattias Jiderhamn
 */
public class BeanValidationCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<BeanValidationCleanUp> {

  @Override
  protected void triggerLeak() throws Exception {
    javax.validation.Validation.buildDefaultValidatorFactory();    
  }

  /**
   * Test case that {@link ThreadLocalCleanUp} fixes leak caused by Axis 1.4
   * @author Mattias Jiderhamn
   */
  @Ignore // Fixed in newer versions of Java???
  public static class ThreadLocalCleanUp_ApacheAxis14Test extends ClassLoaderPreMortemCleanUpTestBase<ThreadLocalCleanUp> {
  
    @Override
    protected void triggerLeak() throws Exception {
      // Trigger leak of Axis 1.4
      XMLUtils.getDocumentBuilder();
    }
  
  }
}