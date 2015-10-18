package se.jiderhamn.classloader.leak.known;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.faces.component.UIComponentBase;

import com.sun.faces.el.ELContextImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;

/**
 * Test case that demonstrates the leak in https://java.net/jira/browse/JAVASERVERFACES-2746
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class UIComponentBaseTest {
  
  /** 
   * Trigger leak by explicit call to {@link BeanELResolver#getFeatureDescriptors(javax.el.ELContext, Object)}.
   * With Caucho Resin EL implementation, a call to {@link BeanELResolver#getValue(javax.el.ELContext, Object, Object)},
   * {@link BeanELResolver#setValue(javax.el.ELContext, Object, Object, Object)},
   * {@link BeanELResolver#getType(javax.el.ELContext, Object, Object)} or 
   * {@link BeanELResolver#isReadOnly(javax.el.ELContext, Object, Object)} would render the same result.
   * */
  @Test
  public void getFeatureDescriptors() {
    final MyComponent myComponent = new MyComponent();
    final BeanELResolver beanELResolver = new BeanELResolver();
    ELContext elContext = new ELContextImpl(new BeanELResolver()); // Irrelevant, could have been mock
    beanELResolver.getFeatureDescriptors(elContext, myComponent);
  }
  
  /** Dummy custom component */
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
}