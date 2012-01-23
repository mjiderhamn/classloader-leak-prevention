package se.jiderhamn;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import org.junit.internal.runners.statements.InvokeMethod;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import static org.junit.Assert.assertNull;

/**
 * @author Mattias Jiderhamn
 */
public class JUnitClassloaderRunner extends BlockJUnit4ClassRunner {
  
  /** No of times to run Garbage Collector for trying to get rid of the class loader. Value is not scientific... */
  private final static int GC_RUNS = 10;
  
  /** No of milliseconds to wait between each Garbage Collection invocation. Value is not scientific... */
  private final static long TIME_BETWEEN_GC = 2000;
  
  public JUnitClassloaderRunner(Class<?> klass) throws InitializationError {
    super(klass);
    // TODO: Replace testclass here to support @Before, @After - alt throw exception if used
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    return new SeparateClassLoaderInvokeMethod(method, test);
  }
  
  private static class SeparateClassLoaderInvokeMethod extends InvokeMethod {
    
    private final Method originalMethod;
    
    private SeparateClassLoaderInvokeMethod(FrameworkMethod testMethod, Object target) {
      super(testMethod, target);
      originalMethod = testMethod.getMethod();
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

        /*
        for(int i = 0; i < GC_RUNS; i++) {
          System.out.println("Running GC"); // TODO turn debugging on/off
          System.gc();
          if(weak.get() == null) // Garbage collected!
            break;
          Thread.sleep(TIME_BETWEEN_GC);
          if(weak.get() == null) // Garbage collected!
            break;
        }
        */

        assertNull("ClassLoader has not been garbage collected " + weak.get(), weak.get());
      }
    }
  }

}