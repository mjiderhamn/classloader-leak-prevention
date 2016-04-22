package se.jiderhamn.classloader.leak.prevention.cleanup;

import javax.el.*;

import org.junit.Before;

/**
 * Test case for {@link BeanELResolverCleanUp}
 * @author Mattias Jiderhamn
 */
public class BeanELResolverCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<BeanELResolverCleanUp> {

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
  
}