package se.jiderhamn;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import org.junit.internal.runners.statements.InvokeMethod;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Mattias Jiderhamn
 */
public class JUnitClassloaderRunner extends BlockJUnit4ClassRunner {
  
  public JUnitClassloaderRunner(Class<?> klass) throws InitializationError {
    super(klass);
    // TODO: Replace testclass here to support @Before, @After - alt throw exception if used
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    final LeakPreventor leakPreventorAnn = method.getMethod().getDeclaringClass().getAnnotation(LeakPreventor.class);
    Class<? extends Runnable> preventorClass = (leakPreventorAnn != null) ? leakPreventorAnn.value() : null;

    return new SeparateClassLoaderInvokeMethod(method, test, preventorClass);
  }
  
  private static class SeparateClassLoaderInvokeMethod extends InvokeMethod {
    
    /** The method to run for triggering potential leak, or verify non-leak */
    private final Method originalMethod;

    /** Is the test method expeced to leak? */
    private final boolean expectedLeak;
    
    /** Class that can be used to remove the leak */
    private Class<? extends Runnable> preventorClass;
    
    private SeparateClassLoaderInvokeMethod(FrameworkMethod testMethod, Object target) {
      this(testMethod, target, null);
    }
    
    private SeparateClassLoaderInvokeMethod(FrameworkMethod testMethod, Object target, Class<? extends Runnable> preventorClass) {
      super(testMethod, target);
      originalMethod = testMethod.getMethod();

      final Leaks leakAnn = testMethod.getAnnotation(Leaks.class);
      this.expectedLeak = (leakAnn == null || leakAnn.value()); // Default to true

      this.preventorClass = preventorClass;
    }

    @Override
    public void evaluate() throws Throwable {
      final Class<?> junitClass = originalMethod.getDeclaringClass();
      final ClassLoader clBefore = Thread.currentThread().getContextClassLoader();

      RedefiningClassLoader myClassLoader = new RedefiningClassLoader(clBefore);
      
      try {
        Thread.currentThread().setContextClassLoader(myClassLoader);
        Class redefinedClass = myClassLoader.loadClass(junitClass.getName());

  System.out.println("JUnit used " + junitClass.getClassLoader()); // TODO turn debugging on/off      
  System.out.println("SeparateClassLoaderInvokeMethod used " + redefinedClass.getClassLoader()); // TODO turn debugging on/off

        Method myMethod = redefinedClass.getDeclaredMethod(originalMethod.getName(), originalMethod.getParameterTypes());
        TestClass myTestClass = new TestClass(redefinedClass);
        
        // super.evaluate(); =
        new FrameworkMethod(myMethod).invokeExplosively(myTestClass.getOnlyConstructor().newInstance());
        
        // Make available to Garbage Collector
        myTestClass = null;
        myMethod = null;
        redefinedClass = null;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      finally {
        Thread.currentThread().setContextClassLoader(clBefore);
        
        // TODO: It's possible that an exception, loaded by the redefining classloader, has been thrown...

        final WeakReference<RedefiningClassLoader> weak = new WeakReference<RedefiningClassLoader>(myClassLoader);
        myClassLoader.markAsZombie();
        myClassLoader = null; // Make available to garbage collector

        System.gc(); // Force Garbage Collector to run

        if(expectedLeak) { // We expect this test to leak classloaders
          RedefiningClassLoader redefiningClassLoader = weak.get();
          assertNotNull("ClassLoader been garbage collected, while test is expected to leak " + weak.get(), redefiningClassLoader);

          if(redefiningClassLoader != null && // Always true, otherwise assertion failure above
             preventorClass != null) {
            try {
              Class<? extends Runnable> preventorInLeakedLoader = (Class<Runnable>) redefiningClassLoader.loadClass(preventorClass.getName());
              Runnable leakPreventor = preventorInLeakedLoader.newInstance();

              leakPreventor.run(); // Try to prevent leak
              
              // Make available for Garbage Collection
              leakPreventor = null;
              preventorInLeakedLoader = null;
              redefiningClassLoader = null;
              
              System.gc();
              
              assertNull("ClassLoader (" + weak.get() + ") has not been garbage collected, " +
                  "despite running the leak preventor " + leakPreventor, weak.get());
            }
            catch (Exception e) {
              throw new RuntimeException("Leak prevention class" + preventorClass + " could not be instantiated!", e);
            }

          }

        }
        else
          assertNull("ClassLoader has not been garbage collected " + weak.get(), weak.get());
      }
    }
  }

}