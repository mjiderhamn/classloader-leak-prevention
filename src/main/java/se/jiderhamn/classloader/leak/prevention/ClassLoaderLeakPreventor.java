// TODO: License
package se.jiderhamn.classloader.leak.prevention;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
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
      
      if(filterDebugMode || filterParanoidMode) {
        forEachThreadLocalInCurrentThread(new ThreadLocalProcessor() {
          public void process(ThreadLocal<?> threadLocal, Object value) {
            final boolean customThreadLocal = isLoadedInWebApplication(threadLocal);
            final boolean valueLoadedInWebApp = isLoadedInWebApplication(value);
            if(customThreadLocal || valueLoadedInWebApp) {
              // This ThreadLocal is either itself loaded by the web app classloader, or it's value is
              // Let's do something about it
              
              StringBuilder message = new StringBuilder();
              if(customThreadLocal) {
                message.append("custom ");
              }
              message.append("ThreadLocal of type ").append(threadLocal.getClass()).append(": ").append(threadLocal)
                     .append(" with value ").append(value);
              if(value != null) {
                message.append(" of type ").append(value.getClass());
                if(valueLoadedInWebApp)
                  message.append(" that is loaded by web app");
              }
              
              if(filterParanoidMode) {
                threadLocal.remove();
                info("Removed " + message);
              }
              else if(filterDebugMode) {
                warn("Found " + message);
              }
            }
          }
        });
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
  }

  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    // TODO: More known leaks
    
    // TODO: Generic leaks like shutdown hooks and JDBC drivers (JCE providers?)
    
    // Fix known leaks
    fixBeanValidationApiLeak();
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Fix specific leaks
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
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
      ex.printStackTrace(System.err);
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
      ex.printStackTrace(System.err);
      return null;
    }
  }
  
  protected static Object getStaticFieldValue(Field field) {
    try {
      return field.get(null);
    }
    catch (Exception ex) {
      ex.printStackTrace(System.err);
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
    // TODO: Implement
  }
  
  protected interface ThreadLocalProcessor {
    void process(ThreadLocal<?> threadLocal, Object value);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Log methods TODO: Use
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /*
   * Since logging frameworks are part of the problem, we don't want to depend on any of them here.
   * Feel free however to subclass or fork and use a log framework, in case you think you know what you're doing.
   */
  
  protected void debug(String s) {
    System.out.println(s);
  } 

  protected void info(String s) {
    System.out.println(s);
  } 

  protected void warn(String s) {
    System.err.println(s);
  } 

  protected void error(String s) {
    System.err.println(s);
  } 
}