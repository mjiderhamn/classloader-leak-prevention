package se.jiderhamn;

import java.io.File;
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
  
  /** Number of seconds to halt to allow for heap dump aquirement, if that option is enabled */
  private static final int HALT_TIME_S = 10;

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
    
    /** 
     * Should the thread pause for a couple of seconds before throwing the test failed error?
     * Set this to true to allow some time to aquire a heap dump to track down leaks.
     */
    private final boolean haltBeforeError;
    
    /** Automatically generate a heap dump of classloader could not be garbage collected? */
    private final boolean dumpHeapOnError;
    
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
      this.haltBeforeError = (leakAnn != null && leakAnn.haltBeforeError()); // Default to false
      this.dumpHeapOnError = (leakAnn != null && leakAnn.dumpHeapOnError()); // Default to false

      this.preventorClass = preventorClass;
    }

    @SuppressWarnings("UnusedAssignment")
    @Override
    public void evaluate() throws Throwable {
      final Class<?> junitClass = originalMethod.getDeclaringClass();
      final ClassLoader clBefore = Thread.currentThread().getContextClassLoader();

      final String testName = originalMethod.getDeclaringClass().getName() + '.' + originalMethod.getName();
      RedefiningClassLoader myClassLoader = new RedefiningClassLoader(clBefore, testName);
      
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
        throw new RuntimeException(e.getClass().getName() + ": " + e.getMessage());
      }
      finally {
        Thread.currentThread().setContextClassLoader(clBefore);
        
        // TODO: It's possible that an exception, loaded by the redefining classloader, has been thrown...

        final WeakReference<RedefiningClassLoader> weak = new WeakReference<RedefiningClassLoader>(myClassLoader);
        myClassLoader.markAsZombie();
        myClassLoader = null; // Make available to garbage collector
        
        forceGc();

        if(expectedLeak) { // We expect this test to leak classloaders
          RedefiningClassLoader redefiningClassLoader = weak.get();
          assertNotNull("ClassLoader has been garbage collected, while test is expected to leak", redefiningClassLoader);

          if(redefiningClassLoader != null && // Always true, otherwise assertion failure above
             preventorClass != null) {
            try {
              Thread.currentThread().setContextClassLoader(redefiningClassLoader);
              Class<? extends Runnable> preventorInLeakedLoader = (Class<Runnable>) redefiningClassLoader.loadClass(preventorClass.getName());
              Runnable leakPreventor = preventorInLeakedLoader.newInstance();
              final String leakPreventorName = leakPreventor.toString();

              leakPreventor.run(); // Try to prevent leak
              
              // Make available for Garbage Collection
              leakPreventor = null;
              preventorInLeakedLoader = null;
              redefiningClassLoader = null;
              Thread.currentThread().setContextClassLoader(clBefore);
              
              forceGc();

              performErrorActions(weak, testName);

              assertNull("ClassLoader (" + weak.get() + ") has not been garbage collected, " +
                  "despite running the leak preventor " + leakPreventorName, weak.get());
            }
            catch (Exception e) {
              throw new RuntimeException("Leak prevention class " + preventorClass.getName() + " could not be used!", e);
            }
            finally {
              redefiningClassLoader = null;
              Thread.currentThread().setContextClassLoader(clBefore); // Make sure it is reset, even if there is an error
            }

          }

        }
        else {
          performErrorActions(weak, testName);

          assertNull("ClassLoader has not been garbage collected " + weak.get(), weak.get());
        }
      }
    }

    private void performErrorActions(WeakReference<RedefiningClassLoader> weak, String testName) throws InterruptedException {
      if(weak.get() != null) { // Still not garbage collected
        if(dumpHeapOnError) {
          dumpHeap(testName);
        }

        if(haltBeforeError) {
          waitForHeapDump();
        }
      }
    }

    /** Make sure Garbage Collection has been run */
    private static void forceGc() {
      Object o = new Object();
      WeakReference<Object> ref = new WeakReference<Object>(o);
      o = null; // Make available for garbage collection
      while(ref.get() != null) { // Until garbage collection has actually been run
        System.gc();
      }
    }

    private static void waitForHeapDump() throws InterruptedException {
      System.out.println("Waiting " + HALT_TIME_S + " seconds to allow for heap dump aquirement");
      // TODO: Inform about ZombieMarker
      Thread.sleep(HALT_TIME_S * 1000);
    }
  }

  /** Create heap dump in file with same name as the test */
  private static void dumpHeap(String testName) {
    try {
      File heapDump = new File(testName + ".bin");
      HeapDumper.dumpHeap(heapDump, false);
      System.out.println("Heaped dumped to " + heapDump.getAbsolutePath());
    }
    catch (ClassNotFoundException e) {
      System.out.println("Unable to dump heap - not Sun/Oracle JVM?");
    }
  }

}