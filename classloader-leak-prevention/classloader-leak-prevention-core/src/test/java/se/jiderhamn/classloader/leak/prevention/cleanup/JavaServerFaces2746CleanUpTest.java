package se.jiderhamn.classloader.leak.prevention.cleanup;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.faces.component.UIComponentBase;

import com.sun.faces.el.ELContextImpl;

/**
 * Test case for {@link JavaServerFaces2746CleanUp}
 * @author Mattias Jiderhamn
 */
public class JavaServerFaces2746CleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<JavaServerFaces2746CleanUp> {

  /** 
   * Trigger leak by explicit call to {@link BeanELResolver#getFeatureDescriptors(javax.el.ELContext, Object)}.
   * With Caucho Resin EL implementation, a call to {@link BeanELResolver#getValue(javax.el.ELContext, Object, Object)},
   * {@link BeanELResolver#setValue(javax.el.ELContext, Object, Object, Object)},
   * {@link BeanELResolver#getType(javax.el.ELContext, Object, Object)} or 
   * {@link BeanELResolver#isReadOnly(javax.el.ELContext, Object, Object)} would render the same result.
   */
  private static void doTriggerLeak() {
    final MyComponent myComponent = new MyComponent();
    final BeanELResolver beanELResolver = new BeanELResolver();
    ELContext elContext = new ELContextImpl(new BeanELResolver()); // Irrelevant, could have been mock
    beanELResolver.getFeatureDescriptors(elContext, myComponent);
  }

  /** Dummy custom component */
  @SuppressWarnings("unused")
  private static class MyComponent extends UIComponentBase {
    @Override
    public String getFamily() {
      throw new UnsupportedOperationException();
    }
    
    /** Getter and setter must use custom attribute */
    public MyAttribute getAttribute() {
      return null;
    }
    
    /** Getter and setter must use custom attribute */
    public void setAttribute(MyAttribute myAttribute) {
      
    }
  }
  
  /** Dummy custom component attribute type */
  private static class MyAttribute {
    
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /** 
   * Since {@link #doTriggerLeak()} also triggers another leak, by {@link java.beans.Introspector#getBeanInfo(java.lang.Class<?>)}
   * invoking {@link java.beans.ThreadGroupContext}, we need to fix that leak as part of the triggering.
   */
  @SuppressWarnings("UnusedAssignment")
  @Override
  protected void triggerLeak() throws Exception {
    doTriggerLeak();

    new ThreadGroupContextCleanUp().cleanUp(getClassLoaderLeakPreventor());
  }
}