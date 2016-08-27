package se.jiderhamn.classloader.leak;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.URL;

import org.junit.Assert;
import org.junit.internal.runners.statements.InvokeMethod;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;
import se.jiderhamn.HeapDumper;
import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;
import se.jiderhamn.classloader.RedefiningClassLoader;
import se.jiderhamn.classloader.ZombieMarker;

import static org.junit.Assert.fail;
import static se.jiderhamn.HeapDumper.HEAP_DUMP_EXTENSION;

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

    return new SeparateClassLoaderInvokeMethod(method, test, preventorClass, 
        test.getClass().getAnnotation(PackagesLoadedOutsideClassLoader.class));
  }
  
  private class SeparateClassLoaderInvokeMethod extends InvokeMethod {
    
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
    
    /** Packages to be ignored by {@link RedefiningClassLoader}. If null, will use defaults. */
    private final String[] ignoredPackages;
    
    private SeparateClassLoaderInvokeMethod(FrameworkMethod testMethod, Object target) {
      this(testMethod, target, null, null);
    }
    
    private SeparateClassLoaderInvokeMethod(FrameworkMethod testMethod, Object target, 
                                            Class<? extends Runnable> preventorClass,
                                            PackagesLoadedOutsideClassLoader packagesLoadedOutsideClassLoader) {
      super(testMethod, target);
      originalMethod = testMethod.getMethod();

      final Leaks leakAnn = testMethod.getAnnotation(Leaks.class);
      this.expectedLeak = (leakAnn == null || leakAnn.value()); // Default to true
      this.haltBeforeError = (leakAnn != null && leakAnn.haltBeforeError()); // Default to false
      this.dumpHeapOnError = (leakAnn != null && leakAnn.dumpHeapOnError()); // Default to false

      this.preventorClass = preventorClass;
      this.ignoredPackages = (packagesLoadedOutsideClassLoader == null) ? null :
          packagesLoadedOutsideClassLoader.addToDefaults() ? 
              appendArrays(RedefiningClassLoader.DEFAULT_IGNORED_PACKAGES, packagesLoadedOutsideClassLoader.packages()) :
          packagesLoadedOutsideClassLoader.packages();
    }

    @SuppressWarnings("UnusedAssignment")
    @Override
    public void evaluate() throws Throwable {
      final ClassLoader clBefore = Thread.currentThread().getContextClassLoader();

      final String testName =  getTestClass().getName() + '.' + originalMethod.getName();
      RedefiningClassLoader myClassLoader = (ignoredPackages != null) ? 
          new RedefiningClassLoader(clBefore, testName, ignoredPackages): 
          new RedefiningClassLoader(clBefore, testName);
      
      try {
        Thread.currentThread().setContextClassLoader(myClassLoader);

        // Load test class in our RedefiningClassLoader
        TestClass myTestClass = new TestClass(myClassLoader.loadClass(getTestClass().getName()));

        // Get test method in our RedefiningClassLoader (NOTE! can be in base class to test class)
        Method myMethod = myClassLoader.loadClass(originalMethod.getDeclaringClass().getName())
            .getDeclaredMethod(originalMethod.getName(), originalMethod.getParameterTypes());

        System.out.println("JUnit used " + getTestClass().getJavaClass().getClassLoader()); // TODO turn debugging on/off
        System.out.println("SeparateClassLoaderInvokeMethod used " + myTestClass.getJavaClass().getClassLoader()); // TODO turn debugging on/off

        // super.evaluate(); =
        new FrameworkMethod(myMethod).invokeExplosively(myTestClass.getOnlyConstructor().newInstance());
        
        // Make available to Garbage Collector
        myTestClass = null;
        myMethod = null;
      }
      catch (Exception e) {
        e.printStackTrace(System.err); // Print here in case other exception is thrown in finally block
        throw new RuntimeException(e.getClass().getName() + ": " + e.getMessage());
      }
      finally {
        Thread.currentThread().setContextClassLoader(clBefore);
        
        // TODO: It's possible that an exception, loaded by the redefining classloader, has been thrown...

        final WeakReference<RedefiningClassLoader> weak = new WeakReference<RedefiningClassLoader>(myClassLoader);
        myClassLoader.markAsZombie();
        myClassLoader = null; // Make available to garbage collector
        
        forceGc(3);

        if(expectedLeak) { // We expect this test to leak classloaders
          RedefiningClassLoader redefiningClassLoader = weak.get();
          Assert.assertNotNull("ClassLoader has been garbage collected, while test is expected to leak", redefiningClassLoader);

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
              
              forceGc(3);

              final boolean leak = (weak.get() != null); // Still not garbage collected
              if(leak) {
                final String message = "ClassLoader (" + weak.get() + ") has not been garbage collected, " +
                    "despite running the leak preventor " + leakPreventorName;
                weak.clear(); // Avoid including this reference in the heap dump
                performErrorActions(testName);

                fail(message);
              }
            }
            catch (Exception e) {
              throw new RuntimeException("Leak prevention class " + preventorClass.getName() + " could not be used!", e);
            }
            finally {
              redefiningClassLoader = null;
              Thread.currentThread().setContextClassLoader(clBefore); // Make sure it is reset, even if there is an error
            }

          }
          else { // Leak was expected, but we had no prevention mechanism
            final boolean leak = (weak.get() != null); // Still not garbage collected
            if(leak) {
              redefiningClassLoader = null; // Avoid including this reference in the heap dump 
              weak.clear(); // Avoid including this reference in the heap dump
              performErrorActions(testName);
            }
          }

        }
        else { // We did not expect a leak
          final boolean leak = (weak.get() != null); // Still not garbage collected
          if(leak) {
            final String message = "ClassLoader has not been garbage collected " + weak.get();
            weak.clear(); // Avoid including this reference in the heap dump
            performErrorActions(testName);
            fail(message);
          }
        }
      }
    }

    /** Call only if there is a leak */
    private void performErrorActions(String testName) throws InterruptedException {
      if(dumpHeapOnError) {
        dumpHeap(testName);
      }

      if(haltBeforeError) {
        waitForHeapDump();
      }
    }
  }

  /** Make sure Garbage Collection has been run N no of times */
  public static void forceGc(int n) {
    for(int i = 0; i < n; i++) {
      forceGc();
    }
  }
  
  /** Make sure Garbage Collection has been run */
  public static void forceGc() {
    WeakReference<Object> ref = new WeakReference<Object>(new Object());
    while(ref.get() != null) { // Until garbage collection has actually been run
      System.gc();
    }
  }

  private static void waitForHeapDump() throws InterruptedException {
    System.out.println("Waiting " + HALT_TIME_S + " seconds to allow for heap dump acquirement");
    System.out.println("Tip: You can search for " + ZombieMarker.class.getName() + " in the dump");
    Thread.sleep(HALT_TIME_S * 1000);
  }

  /** Create heap dump in file with same name as the test */
  private void dumpHeap(String testName) {
    final File surefireReports = getSurefireReportsDirectory();
    try {
      File heapDump = (surefireReports != null) ? new File(surefireReports, testName + HEAP_DUMP_EXTENSION) : 
          new File(testName + HEAP_DUMP_EXTENSION);
      HeapDumper.dumpHeap(heapDump, false);
      System.out.println("Heaped dumped to " + heapDump.getAbsolutePath());
    }
    catch (ClassNotFoundException e) {
      System.out.println("Unable to dump heap - not Sun/Oracle JVM?");
    }
  }

  /** 
   * Try to find "target/surefire-reports" directory, assuming this is a Maven build. Returns null it not found,
   * not writable or other error. */
  private File getSurefireReportsDirectory() {
    return getSurefireReportsDirectory(getTestClass().getJavaClass());
  }
  
  /** 
   * Try to find "target/surefire-reports" directory, assuming this is a Maven build. Returns null it not found,
   * not writable or other error. */
  private static File getSurefireReportsDirectory(final Class<?> clazz) {
    try {
      final String absolutePath = clazz.getResource(clazz.getSimpleName() + ".class").toString();
      final String relativePath = clazz.getName().replace('.', '/') + ".class";
      final String classPath = absolutePath.substring(0, absolutePath.length() - relativePath.length());
      
     // Handle JAR files
    if(classPath.startsWith("jar:")) {
      return null;
    }
      final File dir = new File(new URL(classPath).toURI());
      final File sureFireReports = new File(dir.getParent(), "surefire-reports");
      if(! sureFireReports.exists() && "test-classes".equals(dir.getName()) && "target".equals(dir.getParentFile().getName())) {
        // Seems likely this is a Maven build, but surefire-reports have not been created yet (probably first test case)
        try {
          //noinspection ResultOfMethodCallIgnored
          sureFireReports.mkdirs();
        }
        catch (Exception ignored) {
          // Do nothing
        }
      }
      
      return sureFireReports.exists() && sureFireReports.isDirectory() && sureFireReports.canWrite() ? sureFireReports :
          null;
    }
    catch (Exception e) {
      return null;
    }
  }
  
  /** Append two arrays */
  private <T> T[] appendArrays(T[] arr1, T[] arr2) {
    T[] output = (T[]) Array.newInstance(arr1.getClass().getComponentType(), arr1.length + arr2.length);
    System.arraycopy(arr1, 0, output, 0, arr1.length);
    System.arraycopy(arr2, 0, output, arr1.length, arr2.length);
    return output;
  }

}