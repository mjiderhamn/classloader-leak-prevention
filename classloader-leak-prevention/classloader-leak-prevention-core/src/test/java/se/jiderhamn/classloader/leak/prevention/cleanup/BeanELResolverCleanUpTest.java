package se.jiderhamn.classloader.leak.prevention.cleanup;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

import org.junit.Before;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;
import se.jiderhamn.classloader.leak.prevention.cleanup.BeanELResolverCleanUpTest.BeanELResolverCombinedCleanUp;

/**
 * Test case for {@link BeanELResolverCleanUp}
 *
 * NOTE: this case also triggers the leak from com.sun.beans.introspect.ClassInfo.CACHE,
 * which should be handled as part of {@link BeanIntrospectorCleanUp}.
 * See https://github.com/mjiderhamn/classloader-leak-prevention/issues/123 for the specifics.
 * A combined (JavaServerFaces2746CleanUp,BeanIntrospectorCleanUp) cleanup is used here to handle this test
 *
 * @author Mattias Jiderhamn
 */
public class BeanELResolverCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<BeanELResolverCombinedCleanUp> {

  @Before
  public void setUp() {
    // Must be done outside test classloader 
    javax.imageio.ImageIO.getCacheDirectory(); // Will call sun.awt.AppContext.getAppContext()
  }

  @Override
  protected void triggerLeak() throws Exception {
    BeanELResolver beanELResolver = new BeanELResolver();
    beanELResolver.getValue(new MyELContext(), new Bean(), "foo"); // Will put class in strong reference cache
    
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /** Bean for testing */
  @SuppressWarnings("unused")
  public static class Bean {
    private String foo;

    public String getFoo() {
      return foo;
    }

    public void setFoo(String foo) {
      this.foo = foo;
    }
  }
  
  /** Dummy ELContext */
  private static class MyELContext extends ELContext {
    @Override
    public ELResolver getELResolver() {
      throw new UnsupportedOperationException("dummy");
    }

    @Override
    public FunctionMapper getFunctionMapper() {
      throw new UnsupportedOperationException("dummy");
    }

    @Override
    public VariableMapper getVariableMapper() {
      throw new UnsupportedOperationException("dummy");
    }
  }

  public static class BeanELResolverCombinedCleanUp implements ClassLoaderPreMortemCleanUp {
      @Override
      public void cleanUp(ClassLoaderLeakPreventor preventor) {
          new BeanELResolverCleanUp().cleanUp(preventor);
          new BeanIntrospectorCleanUp().cleanUp(preventor);
      }
    }

}