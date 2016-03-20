package se.jiderhamn.classloader.leak.prevention.cleaup;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUpTestBase;
import se.jiderhamn.classloader.leak.prevention.cleanup.BeanValidationCleanUp;

/**
 * Test case for {@link BeanValidationCleanUp}
 * @author Mattias Jiderhamn
 */
public class BeanValidationCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<BeanValidationCleanUp> {

  @Override
  protected void triggerLeak() throws Exception {
    javax.validation.Validation.buildDefaultValidatorFactory();    
  }

}