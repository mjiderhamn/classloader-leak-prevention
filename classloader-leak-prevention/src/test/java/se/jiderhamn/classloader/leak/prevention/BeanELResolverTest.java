package se.jiderhamn.classloader.leak.prevention;

import javax.el.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * Test that the leak caused by BeanELResolver is cleared.
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(BeanELResolverTest.Prevent.class)
public class BeanELResolverTest {
  
  @Before
  public void setUp() {
    // Must be done outside test classloader 
    javax.imageio.ImageIO.getCacheDirectory(); // Will call sun.awt.AppContext.getAppContext()
  }
  
  @Test
  public void triggerBeanELResolverLeak() throws Exception {
    BeanELResolver beanELResolver = new BeanELResolver();
    beanELResolver.getValue(new MyELContext(), new Bean(), "foo"); // Will put class in strong reference cache
  }
  
  /** Bean for testing */
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
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventorListener() {
        { // Initializer / "Constructor"
          java.beans.Introspector.flushCaches(); // This must also be done          

          clearBeanELResolverCache();
        }
      };
    }

  }

}