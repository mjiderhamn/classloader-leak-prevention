// TODO: License
package se.jiderhamn.classloader.leak.prevention;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import javax.servlet.*;

/**
 * TODO: Document
 * @author Mattias Jiderhamn
 */
public class ClassLoaderLeakPreventor implements javax.servlet.ServletContextListener, javax.servlet.Filter {
  
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
    // TODO: Initialize known JRE problems
    // TODO: Create test cases
  }

  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    // TODO: More known leaks
    
    // Deregister JDBC drivers contained in web application
    deregisterJdbcDrivers();
    
    // TODO: Shutdown hooks
    
    // TODO: (JCE providers?)
    
    // TODO: RMI targets???
    
    // TODO: ThreadLocals of all threads
    
    // TODO: Setting to stop threads
    
    // TODO: Setting to stop timer threads

    // TODO org.apache.commons.logging.LogFactory.release(this.getClass().getClassLoader()); // TODO: Reflection. Test.
    
    // TODO: Resource bundle cache?
    
    //////////////////
    // Fix known leaks
    //////////////////
    
    java.beans.Introspector.flushCaches(); // Clear cache of strong references TODO: Create test
    
    fixBeanValidationApiLeak();
    
    // TODO More known offenders
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
      if(isLoadedInWebApplication(driver))
        driversToDeregister.add(driver);
    }
    
    for(Driver driver : driversToDeregister) {
      try {
        DriverManager.deregisterDriver(driver);
      }
      catch (SQLException e) {
        error(e);
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
  
  /** Test if provided object is loaded with web application classloader */ // TODO: Create test
  protected boolean isLoadedInWebApplication(Object o) {
    if(o == null)
      return false;

    final ClassLoader webAppCL = this.getClass().getClassLoader();
    // final ClassLoader webAppCL = Thread.currentThread().getContextClassLoader();
    
    ClassLoader cl = o.getClass().getClassLoader();
    while(cl != null) {
      if(cl == webAppCL)
        return true;
      
      cl = cl.getParent();
    }
    
    return false;
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
    // TODO
//    catch (NoClassDefFoundError e) {
//      // Silently ignore
//      return null;
//    }
//    catch (ClassNotFoundException e) {
//      // Silently ignore
//      return null;
//    }
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
  
  /**
   * Loop ThreadLocals and inheritable ThreadLocals in current Thread
   * and for each found, invoke the callback interface
   * TODO: Create test case
   */
  protected void forEachThreadLocalInCurrentThread(ThreadLocalProcessor threadLocalProcessor) {
    final Thread thread = Thread.currentThread();


    try {
      Class<?> threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
      final Field threadLocalMapTableField = findField(threadLocalMapClass, "table");
      final Field threadLocals = findField(Thread.class, "threadLocals");
      Object threadLocalMap = threadLocals.get(thread);
      if(threadLocalMap != null) {
        final Object[] threadLocalMapTable = (Object[]) threadLocalMapTableField.get(threadLocalMap); // java.lang.ThreadLocal.ThreadLocalMap.Entry[]
        for(Object entry : threadLocalMapTable) {
          if(entry != null) {
            // Key is kept in WeakReference
            Reference reference = (Reference) entry;
            final ThreadLocal<?> threadLocal = (ThreadLocal<?>) reference.get();
  
            final Field valueField = findField(entry.getClass(), "value"); // TODO: Consider looking up in constructor and making protected
            final Object value = valueField.get(entry);
  
            threadLocalProcessor.process(thread, reference, threadLocal, value);
          }
        }
      }

      findField(Thread.class, "inheritableThreadLocals");
      // TODO: Implement
    }
    catch (ClassNotFoundException e) {
      e.printStackTrace();  // TODO
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();  // TODO
    }
  }

  protected interface ThreadLocalProcessor {
    void process(Thread thread, Reference entry, ThreadLocal<?> threadLocal, Object value);
  }

  /** ThreadLocalProcessor that detects and warns about potential leaks */
  protected class WarningThreadLocalProcessor implements ThreadLocalProcessor {
    public final void process(Thread thread, Reference entry, ThreadLocal<?> threadLocal, Object value) {
      // TODO: Consider case when classloader itself is value
      final boolean customThreadLocal = isLoadedInWebApplication(threadLocal); // This is not an actual problem
      final boolean valueLoadedInWebApp = isLoadedInWebApplication(value);
      if(customThreadLocal || valueLoadedInWebApp) {
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
        final Field valueField = findField(entry.getClass(), "value"); // TODO: Consider looking up in constructor and making protected
        try {
          valueField.set(entry, null); // Clear value to avoid circular references
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