/*
   Copyright 2012 Mattias Jiderhamn

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package se.jiderhamn.classloader.leak.prevention;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.*;
import javax.xml.parsers.ParserConfigurationException;

/**
 * TODO: Document
 * @author Mattias Jiderhamn
 */
public class ClassLoaderLeakPreventor implements javax.servlet.ServletContextListener, javax.servlet.Filter {
  
  /** No of ms to wait for shutdown hook to finish execution */
  private static final int TIME_TO_WAIT_FOR_SHUTDOWN_HOOKS_MS = 60 * 1000;

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Implement javax.servlet.Filter
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /** Known offending (uncleared) ThreadLocals */
  private ThreadLocal[] offendingThreadLocals;
  
  /**
   * When filter is set to debug mode, it will look for uncleared ThreadLocals when processing each request and
   * if found, will print them using warning level.
   */
  protected boolean filterDebugMode = false; 

  /**
   * When filter is set to paranoid mode, it will look for uncleared ThreadLocals when processing each request and
   * if found, will clear them.
   */
  protected boolean filterParanoidMode = false;

  protected final Field java_lang_Thread_threadLocals;

  protected final Field java_lang_Thread_inheritableThreadLocals;

  protected final Field java_lang_ThreadLocal$ThreadLocalMap_table;

  protected Field java_lang_ThreadLocal$ThreadLocalMap$Entry_value;

  public ClassLoaderLeakPreventor() {
    // Initialize some reflection variables
    java_lang_Thread_threadLocals = findField(Thread.class, "threadLocals");
    java_lang_Thread_inheritableThreadLocals = findField(Thread.class, "inheritableThreadLocals");
    java_lang_ThreadLocal$ThreadLocalMap_table = findFieldOfClass("java.lang.ThreadLocal$ThreadLocalMap", "table");
    
    if(java_lang_Thread_threadLocals == null)
      error("java.lang.Thread.threadLocals not found; something is seriously wrong!");
    
    if(java_lang_Thread_inheritableThreadLocals == null)
      error("java.lang.Thread.inheritableThreadLocals not found; something is seriously wrong!");

    if(java_lang_ThreadLocal$ThreadLocalMap_table == null)
      error("java.lang.ThreadLocal$ThreadLocalMap.table not found; something is seriously wrong!");
  }

  public void init(FilterConfig filterConfig) throws ServletException {
    filterDebugMode = "true".equals(filterConfig.getInitParameter("debug"));
    filterParanoidMode = "true".equals(filterConfig.getInitParameter("paranoid"));
    
    List<ThreadLocal> threadLocals = new ArrayList<ThreadLocal>();
    Object axisDocumentBuilder = getStaticFieldValue("org.apache.axis.utils.XMLUtils", "documentBuilder");
    if(axisDocumentBuilder instanceof ThreadLocal) {
      threadLocals.add((ThreadLocal)axisDocumentBuilder);
    }
    
    this.offendingThreadLocals = threadLocals.toArray(new ThreadLocal[threadLocals.size()]);
  }

  /** In the doFilter() method we have a chance to clean up the thread before it is returned to the thread pool */
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
    try {
      filterChain.doFilter(servletRequest, servletResponse);
    }
    finally {
      // Clean up known offender ThreadLocals
      for(ThreadLocal offendingThreadLocal : offendingThreadLocals) {
        offendingThreadLocal.remove(); // Remove offender from current thread
      }
      
      if(filterParanoidMode) {
        forEachThreadLocalInCurrentThread(new ClearingThreadLocalProcessor());
      }
      else if(filterDebugMode) {
        forEachThreadLocalInCurrentThread(new WarningThreadLocalProcessor());
      }
    }
  }

  public void destroy() {
    offendingThreadLocals = null; // Make available for Garbage Collector
  }


  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Implement javax.servlet.ServletContextListener 
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    
    info(getClass().getName() + " initializing context by loading some known offenders with system classloader");
    
    // This part is heavily inspired by Tomcats JreMemoryLeakPreventionListener  
    // See http://svn.apache.org/viewvc/tomcat/trunk/java/org/apache/catalina/core/JreMemoryLeakPreventionListener.java?view=markup
    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      // TODO: Create test cases
      // Switch to system classloader in before we load/call some JRE stuff that will cause 
      // the current classloader to be available for gerbage collection
      Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
      
      java.awt.Toolkit.getDefaultToolkit(); // TODO: Default unused?
      
      java.security.Security.getProviders();
      
      java.sql.DriverManager.getDrivers(); // Load initial drivers using system classloader

      javax.imageio.ImageIO.getCacheDirectory(); // Will call sun.awt.AppContext.getAppContext()

      try {
        Class.forName("javax.security.auth.Policy")
            .getMethod("getPolicy")
            .invoke(null);
      }
      catch (IllegalAccessException iaex) {
        error(iaex);
      }
      catch (InvocationTargetException itex) {
        error(itex);
      }
      catch (NoSuchMethodException nsmex) {
        error(nsmex);
      }
      catch (ClassNotFoundException e) {
        // Ignore silently - class is deprecated
      }

      try {
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
      }
      catch (ParserConfigurationException pcex) {
        error(pcex);
      }

      try {
        Class.forName("javax.security.auth.login.Configuration", true, ClassLoader.getSystemClassLoader());
      }
      catch (ClassNotFoundException e) {
        // Do nothing
      }

      // TODO: Investigate JarURLConnections 
      
      /////////////////////////////////////////////////////
      // Load Sun specific classes that may cause leaks
      
      final boolean isSunJRE = System.getProperty("java.vendor").startsWith("Sun");
      
      try {
        Class.forName("com.sun.jndi.ldap.LdapPoolManager");
      }
      catch(ClassNotFoundException cnfex) {
        if(isSunJRE)
          error(cnfex);
      }

      try {
        Class.forName("sun.java2d.Disposer"); // TODO: Default to false?
      }
      catch (ClassNotFoundException cnfex) {
        if(isSunJRE)
          error(cnfex);
      }

      try {
        Class<?> gcClass = Class.forName("sun.misc.GC");
        final Method requestLatency = gcClass.getDeclaredMethod("requestLatency", long.class);
        requestLatency.invoke(null, 3600000L);
      }
      catch (ClassNotFoundException cnfex) {
        if(isSunJRE)
          error(cnfex);
      }
      catch (NoSuchMethodException nsmex) {
        error(nsmex);
      }
      catch (IllegalAccessException iaex) {
        error(iaex);
      }
      catch (InvocationTargetException itex) {
        error(itex);
      }
    }
    finally {
      // Reset original classloader
      Thread.currentThread().setContextClassLoader(contextClassLoader);
    }
  }

  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    
    info(getClass().getName() + " shutting down context by removing known leaks");
    
    // Deregister JDBC drivers contained in web application
    deregisterJdbcDrivers();
    
    // Deregister shutdown hooks - execute them immediately
    deregisterShutdownHooks();

    // TODO: (JCE providers?)
    
    // TODO: RMI targets???
    
    clearThreadLocalsOfAllThreads();
    
    stopThreads();
    
    // TODO: Setting to stop timer threads

    java.util.ResourceBundle.clearCache(this.getClass().getClassLoader()); // TODO: Since Java 1.6
    
    //////////////////
    // Fix known leaks
    //////////////////
    
    java.beans.Introspector.flushCaches(); // Clear cache of strong references
    
    fixBeanValidationApiLeak();
    
    // TODO More known offenders

    // Release this classloader from Apache Commons Logging (ACL) by calling
    //   LogFactory.release(this.getClass().getClassLoader());
    // Use reflection in case ACL is not present.
    // Do this last, in case other shutdown procedures want to log something.
    
    final Class logFactory = findClass("org.apache.commons.logging.LogFactory");
    if(logFactory != null) { // Apache Commons Logging present
      info("Releasing web app classloader from Apache Commons Logging");
      try {
        logFactory.getMethod("release", java.lang.ClassLoader.class).invoke(null, this.getClass().getClassLoader());
      }
      catch (IllegalAccessException iaex) {
        error(iaex);
      }
      catch (InvocationTargetException itex) {
        error(itex);
      }
      catch (NoSuchMethodException nsmex) {
        error(nsmex);
      }
    }
    
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Fix specific leaks
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /** Deregister JDBC drivers loaded by web app classloader */
  public void deregisterJdbcDrivers() {
    final List<Driver> driversToDeregister = new ArrayList<Driver>();
    final Enumeration<Driver> allDrivers = DriverManager.getDrivers();
    while(allDrivers.hasMoreElements()) {
      final Driver driver = allDrivers.nextElement();
      if(isLoadedInWebApplication(driver)) // Should be true for all returned by DriverManager.getDrivers()
        driversToDeregister.add(driver);
    }
    
    for(Driver driver : driversToDeregister) {
      try {
        warn("JDBC driver loaded by web app deregistered: " + driver.getClass());
        DriverManager.deregisterDriver(driver);
      }
      catch (SQLException e) {
        error(e);
      }
    }
  }

  /** Find and deregister shutdown hooks. Will by default execute the hooks after removing them. */
  protected void deregisterShutdownHooks() {
    // We will not remove known shutdown hooks, since loading the owning class of the hook,
    // may register the hook if previously unregistered 
    final Field field = findFieldOfClass("java.lang.ApplicationShutdownHooks", "hooks");
    try {
      Map<Thread, Thread> shutdownHooks = (Map<Thread, Thread>) field.get(null);
      // Iterate copy to avoid ConcurrentModificationException
      for(Thread shutdownHook : new ArrayList<Thread>(shutdownHooks.keySet())) {
        if(isThreadInWebApplication(shutdownHook)) { // Planned to run in web app          
          removeShutdownHook(shutdownHook);
        }
      }
    }
    catch (IllegalAccessException iaex) {
      error(iaex);
    }
  }

  /** Deregister shutdown hook and execute it immediately */
  protected void removeShutdownHook(Thread shutdownHook) {
    final String displayString = "'" + shutdownHook + "' of type " + shutdownHook.getClass().getName();
    error("Removing shutdown hook: " + displayString);
    Runtime.getRuntime().removeShutdownHook(shutdownHook);

    info("Executing shutdown hook now: " + displayString);
    // Make sure it's from this web app instance
    shutdownHook.start(); // Run cleanup immediately
    try {
      shutdownHook.join(TIME_TO_WAIT_FOR_SHUTDOWN_HOOKS_MS); // Wait for thread to run TODO: Create setting
    }
    catch (InterruptedException e) {
      // Do nothing
    }
    if(shutdownHook.isAlive())
      error("Still running after " + TIME_TO_WAIT_FOR_SHUTDOWN_HOOKS_MS + " ms! " + shutdownHook);
  }

  protected void clearThreadLocalsOfAllThreads() {
    final ThreadLocalProcessor clearingThreadLocalProcessor = new ClearingThreadLocalProcessor();
    for(Thread thread : getAllThreads()) {
      forEachThreadLocalInThread(thread, clearingThreadLocalProcessor);
    }
  }

  protected void stopThreads() {
    for(Thread thread : getAllThreads()) {
      if(thread != Thread.currentThread() && // Ignore current thread
         isThreadInWebApplication(thread)) {

        if(thread.getThreadGroup() != null && "system".equals(thread.getThreadGroup().getName())) { // Ignore system thread TODO: "RMI Runtime"?
          if("Keep-Alive-Timer".equals(thread.getName())) {
            thread.setContextClassLoader(this.getClass().getClassLoader().getParent());
            debug("Changed contextClassLoader of HTTP keep alive thread");
          }
        }
        else if(thread.isAlive()) { // Non-system, running in web app
        
          if("java.util.TimerThread".equals(thread.getClass().getName())) {
            // TODO: Special treatment
          }
          
          // TODO: Special treatment of threads started by executor
  
          final String displayString = "'" + thread + "' of type " + thread.getClass().getName();
          error("Thread " + displayString + " is still running in web app");
          
          // TODO: Make setting for stopping
          info("Stopping Thread " + displayString);
          // Normally threads should not be stopped (method is deprecated), since it may cause an inconsistent state.
          // In this case however, the alternative is a classloader leak, which may or may not be considered worse.
          // TODO: Give it some time first?
          thread.stop();
        }
      }
    }
  }

  public static void fixBeanValidationApiLeak() {
    Class offendingClass = findClass("javax.validation.Validation$DefaultValidationProviderResolver");
    if(offendingClass != null) { // Class is present on class path
      Field offendingField = findField(offendingClass, "providersPerClassloader");
      if(offendingField != null) {
        final Object providersPerClassloader = getStaticFieldValue(offendingField);
        if(providersPerClassloader instanceof Map) { // Map<ClassLoader, List<ValidationProvider<?>>> in offending code
          // Fix the leak!
          ((Map)providersPerClassloader).remove(ClassLoaderLeakPreventor.class.getClassLoader());
        }
      }
    }
    
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Utility methods
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /** Test if provided object is loaded with web application classloader */
  protected boolean isLoadedInWebApplication(Object o) {
    return o != null && 
        isWebAppClassLoaderOrChild(o.getClass().getClassLoader());
  }

  /** Test if provided ClassLoader is the classloader of the web application, or a child thereof */
  protected boolean isWebAppClassLoaderOrChild(ClassLoader cl) {
    final ClassLoader webAppCL = this.getClass().getClassLoader();
    // final ClassLoader webAppCL = Thread.currentThread().getContextClassLoader();

    while(cl != null) {
      if(cl == webAppCL)
        return true;
      
      cl = cl.getParent();
    }

    return false;
  }

  protected boolean isThreadInWebApplication(Thread thread) {
    return isLoadedInWebApplication(thread) || // Custom Thread class in web app
       isWebAppClassLoaderOrChild(thread.getContextClassLoader()); // Running in web application
  }

  protected static Object getStaticFieldValue(String className, String fieldName) {
    Field staticField = findFieldOfClass(className, fieldName);
    return (staticField != null) ? getStaticFieldValue(staticField) : null;
  }
  
  protected static Field findFieldOfClass(String className, String fieldName) {
    Class clazz = findClass(className);
    if(clazz != null) {
      return findField(clazz, fieldName);
    }
    else
      return null;
  }
  
  protected static Class findClass(String className) {
    try {
      return Class.forName(className);
    }
//    catch (NoClassDefFoundError e) {
//      // Silently ignore
//      return null;
//    }
    catch (ClassNotFoundException e) {
      // Silently ignore
      return null;
    }
    catch (Exception ex) { // Example SecurityException
      warn(ex);
      return null;
    }
  }
  
  protected static Field findField(Class clazz, String fieldName) {
    if(clazz == null)
      return null;

    try {
      final Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true); // (Field is probably private) 
      return field;
    }
    catch (NoSuchFieldException ex) {
      // Silently ignore
      return null;
    }
    catch (Exception ex) { // Example SecurityException
      warn(ex);
      return null;
    }
  }
  
  protected static Object getStaticFieldValue(Field field) {
    try {
      return field.get(null);
    }
    catch (Exception ex) {
      warn(ex);
      // Silently ignore
      return null;
    }
  }
  
  protected Collection<Thread> getAllThreads() {
    return Thread.getAllStackTraces().keySet(); // TODO: Compare performance with ThreadGroup.enumerate()
  }
  
  /**
   * Loop ThreadLocals and inheritable ThreadLocals in current Thread
   * and for each found, invoke the callback interface
   */
  protected void forEachThreadLocalInCurrentThread(ThreadLocalProcessor threadLocalProcessor) {
    final Thread thread = Thread.currentThread();

    forEachThreadLocalInThread(thread, threadLocalProcessor);
  }

  protected void forEachThreadLocalInThread(Thread thread, ThreadLocalProcessor threadLocalProcessor) {
    try {
      if(java_lang_Thread_threadLocals != null) {
        processThreadLocalMap(thread, threadLocalProcessor, java_lang_Thread_threadLocals.get(thread));
      }

      if(java_lang_Thread_inheritableThreadLocals != null) {
        processThreadLocalMap(thread, threadLocalProcessor, java_lang_Thread_inheritableThreadLocals.get(thread));
      }
    }
    catch (IllegalAccessException iaex) {
      error(iaex);
    }
  }

  protected void processThreadLocalMap(Thread thread, ThreadLocalProcessor threadLocalProcessor, Object threadLocalMap) throws IllegalAccessException {
    if(threadLocalMap != null && java_lang_ThreadLocal$ThreadLocalMap_table != null) {
      final Object[] threadLocalMapTable = (Object[]) java_lang_ThreadLocal$ThreadLocalMap_table.get(threadLocalMap); // java.lang.ThreadLocal.ThreadLocalMap.Entry[]
      for(Object entry : threadLocalMapTable) {
        if(entry != null) {
          // Key is kept in WeakReference
          Reference reference = (Reference) entry;
          final ThreadLocal<?> threadLocal = (ThreadLocal<?>) reference.get();

          if(java_lang_ThreadLocal$ThreadLocalMap$Entry_value == null) {
            java_lang_ThreadLocal$ThreadLocalMap$Entry_value = findField(entry.getClass(), "value");
          }
          
          final Object value = java_lang_ThreadLocal$ThreadLocalMap$Entry_value.get(entry);

          threadLocalProcessor.process(thread, reference, threadLocal, value);
        }
      }
    }
  }

  protected interface ThreadLocalProcessor {
    void process(Thread thread, Reference entry, ThreadLocal<?> threadLocal, Object value);
  }

  /** ThreadLocalProcessor that detects and warns about potential leaks */
  protected class WarningThreadLocalProcessor implements ThreadLocalProcessor {
    public final void process(Thread thread, Reference entry, ThreadLocal<?> threadLocal, Object value) {
      final boolean customThreadLocal = isLoadedInWebApplication(threadLocal); // This is not an actual problem
      final boolean valueLoadedInWebApp = isLoadedInWebApplication(value);
      if(customThreadLocal || valueLoadedInWebApp ||
         (value instanceof ClassLoader && isWebAppClassLoaderOrChild((ClassLoader) value))) { // The value is classloader (child) itself
        // This ThreadLocal is either itself loaded by the web app classloader, or it's value is
        // Let's do something about it
        
        StringBuilder message = new StringBuilder();
        if(threadLocal != null) {
          if(customThreadLocal) {
            message.append("Custom ");
          }
          message.append("ThreadLocal of type ").append(threadLocal.getClass().getName()).append(": ").append(threadLocal);
        }
        else {
          message.append("Unknown ThreadLocal");
        }
        message.append(" with value ").append(value);
        if(value != null) {
          message.append(" of type ").append(value.getClass().getName());
          if(valueLoadedInWebApp)
            message.append(" that is loaded by web app");
        }

        warn(message.toString());
        
        processFurther(thread, entry, threadLocal, value); // Allow subclasses to perform further processing
      }
    }
    
    /**
     * After having detected potential ThreadLocal leak and warned about it, this method is called.
     * Subclasses may override this method to perform further processing, such as clean up. 
     */
    protected void processFurther(Thread thread, Reference entry, ThreadLocal<?> threadLocal, Object value) {
      // To be overridden in subclass
    } 
  }
  
  /** ThreadLocalProcessor that not only detects and warns about potential leaks, but also tries to clear them */
  protected class ClearingThreadLocalProcessor extends WarningThreadLocalProcessor {
    public void processFurther(Thread thread, Reference entry, ThreadLocal<?> threadLocal, Object value) {
      if(threadLocal != null && thread == Thread.currentThread()) { // If running for current thread and we have the ThreadLocal ...
        // ... remove properly
        info("  Will be remove()d");
        threadLocal.remove();
      }
      else { // We cannot remove entry properly, so just make it stale
        info("  Will be made stale for later clearing");
        entry.clear(); // Clear the key

        if(java_lang_ThreadLocal$ThreadLocalMap$Entry_value == null) {
          java_lang_ThreadLocal$ThreadLocalMap$Entry_value = findField(entry.getClass(), "value");
        }

        try {
          java_lang_ThreadLocal$ThreadLocalMap$Entry_value.set(entry, null); // Clear value to avoid circular references
        }
        catch (IllegalAccessException iaex) {
          error(iaex);
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Log methods
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /*
   * Since logging frameworks are part of the problem, we don't want to depend on any of them here.
   * Feel free however to subclass or fork and use a log framework, in case you think you know what you're doing.
   */
  
  protected static void debug(String s) {
    System.out.println(s);
  } 

  protected static void info(String s) {
    System.out.println(s);
  } 

  protected static void warn(String s) {
    System.err.println(s);
  } 

  protected static void warn(Throwable t) {
    t.printStackTrace(System.err);
  } 

  protected static void error(String s) {
    System.err.println(s);
  } 

  protected static void error(Throwable t) {
    t.printStackTrace(System.err);
  } 
}