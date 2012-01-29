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
  
  private ThreadLocal[] offendingThreadLocals;

  public void init(FilterConfig filterConfig) throws ServletException {
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
      // Clean up ThreadLocals
      for(ThreadLocal offendingThreadLocal : offendingThreadLocals) {
        offendingThreadLocal.remove(); // Remove offender from current thread
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
    // TODO
  }

  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    // TODO
    
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
  
  private static Object getStaticFieldValue(String className, String fieldName) {
    Field staticField = findFieldOfClass(className, fieldName);
    return (staticField != null) ? getStaticFieldValue(staticField) : null;
  }
  
  private static Field findFieldOfClass(String className, String fieldName) {
    Class clazz = findClass(className);
    if(clazz != null) {
      return findField(clazz, fieldName);
    }
    else
      return null;
  }
  
  private static Class findClass(String className) {
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
  
  private static Field findField(Class clazz, String fieldName) {
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
  
  private static Object getStaticFieldValue(Field field) {
    try {
      return field.get(null);
    }
    catch (Exception ex) {
      ex.printStackTrace(System.err);
      // Silently ignore
      return null;
    }
  }
}