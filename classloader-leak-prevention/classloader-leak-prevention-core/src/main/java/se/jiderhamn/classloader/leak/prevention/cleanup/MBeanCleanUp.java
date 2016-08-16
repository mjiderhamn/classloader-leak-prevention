package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Unregister MBeans loaded by the protected class loader
 * @author Mattias Jiderhamn
 * @author rapla
 */
public class MBeanCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    try {
      final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
      final Set<ObjectName> allMBeanNames = mBeanServer.queryNames(new ObjectName("*:*"), null);

      // Special treatment for Jetty, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=423255
      JettyJMXRemover jettyJMXRemover = null;
      if(isJettyWithJMX(preventor)) {
        try {
          jettyJMXRemover = new JettyJMXRemover(preventor);
        }
        catch (Exception ex) {
          preventor.error(ex);
        }
      }
      
      // Look for custom MBeans
      for(ObjectName objectName : allMBeanNames) {
        try {
          if (jettyJMXRemover != null && jettyJMXRemover.unregisterJettyJMXBean(objectName)) {
        	  continue;
          }
          
          final ClassLoader mBeanClassLoader = mBeanServer.getClassLoaderFor(objectName);
          if(preventor.isClassLoaderOrChild(mBeanClassLoader)) { // MBean loaded by protected ClassLoader
            preventor.warn("MBean '" + objectName + "' was loaded by protected ClassLoader; unregistering");
            mBeanServer.unregisterMBean(objectName);
          }
          /* 
          else if(... instanceof NotificationBroadcasterSupport) {
            unregisterNotificationListeners((NotificationBroadcasterSupport) ...);
          }
          */
        }
        catch(Exception e) { // MBeanRegistrationException / InstanceNotFoundException
          preventor.error(e);
        }
      }
    }
    catch (Exception e) { // MalformedObjectNameException
      preventor.error(e);
    }
    
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods and classes for Jetty, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=423255 
  
  /** Are we running in Jetty with JMX enabled? */
  @SuppressWarnings("WeakerAccess")
  protected boolean isJettyWithJMX(ClassLoaderLeakPreventor preventor) {
    final ClassLoader classLoader = preventor.getClassLoader();
    try {
      // If package org.eclipse.jetty is found, we may be running under jetty
      if (classLoader.getResource("org/eclipse/jetty") == null) {
        return false;
      }

      Class.forName("org.eclipse.jetty.jmx.MBeanContainer", false, classLoader.getParent()); // JMX enabled?
      Class.forName("org.eclipse.jetty.webapp.WebAppContext", false, classLoader.getParent());
    }
    catch(Exception ex) { // For example ClassNotFoundException
      return false;
    }
    
    // Seems we are running in Jetty with JMX enabled
    return true;
  }
  
  /** 
   * Inner utility class that helps dealing with Jetty MBeans class.
   * If you enable JMX support in Jetty 8 or 9 some MBeans (e.g. for the ServletHolder or SessionManager) are
   * instantiated in the web application thread and a reference to the WebappClassloader is stored in a private
   * ObjectMBean._loader which is unfortunately not the classloader that loaded the class. Therefore we need to access 
   * the MBeanContainer class of the Jetty container and unregister the MBeans.
   */
  private class JettyJMXRemover {
    
    private final ClassLoaderLeakPreventor preventor;

    /** List of objects that may be wrapped in MBean by Jetty. Should be allowed to contain null. */
    private List<Object> objectsWrappedWithMBean;

    /** The org.eclipse.jetty.jmx.MBeanContainer instance */
    private Object beanContainer;

    /** org.eclipse.jetty.jmx.MBeanContainer.findBean() */
    private Method findBeanMethod;

    /** org.eclipse.jetty.jmx.MBeanContainer.removeBean() */
    private Method removeBeanMethod;

    @SuppressWarnings("WeakerAccess")
    public JettyJMXRemover(ClassLoaderLeakPreventor preventor) throws Exception {
      this.preventor = preventor;
      
      // First we need access to the MBeanContainer to access the beans
      // WebAppContext webappContext = (WebAppContext)servletContext;
      final Object webappContext = findJettyClass("org.eclipse.jetty.webapp.WebAppClassLoader")
              .getMethod("getContext").invoke(preventor.getClassLoader());
      if(webappContext == null)
        return;
      
      // Server server = (Server)webappContext.getServer();
      final Class<?> webAppContextClass = findJettyClass("org.eclipse.jetty.webapp.WebAppContext");
      final Object server = webAppContextClass.getMethod("getServer").invoke(webappContext);
      if(server == null)
        return;

      // MBeanContainer beanContainer = (MBeanContainer)server.getBean(MBeanContainer.class);
	  
      final Class<?> mBeanContainerClass = findJettyClass("org.eclipse.jetty.jmx.MBeanContainer");
      beanContainer = findJettyClass("org.eclipse.jetty.server.Server")
              .getMethod("getBean", Class.class).invoke(server, mBeanContainerClass);
      // Now we store all objects that belong to the web application and that will be wrapped by MBeans in a list
      if (beanContainer != null) {
        findBeanMethod = mBeanContainerClass.getMethod("findBean", ObjectName.class);
        try {
          removeBeanMethod = mBeanContainerClass.getMethod("removeBean", Object.class);
        } catch (NoSuchMethodException e) {
          preventor.warn("MBeanContainer.removeBean() method can not be found. giving up");
          return;
        }

        objectsWrappedWithMBean = new ArrayList<Object>();
        // SessionHandler sessionHandler = webappContext.getSessionHandler();
        final Object sessionHandler = webAppContextClass.getMethod("getSessionHandler").invoke(webappContext);
        if(sessionHandler != null) {
          objectsWrappedWithMBean.add(sessionHandler);
  
          // SessionManager sessionManager = sessionHandler.getSessionManager();
          final Object sessionManager = findJettyClass("org.eclipse.jetty.server.session.SessionHandler")
                  .getMethod("getSessionManager").invoke(sessionHandler);
          if(sessionManager != null) {
            objectsWrappedWithMBean.add(sessionManager);

            // SessionIdManager sessionIdManager = sessionManager.getSessionIdManager();
            final Object sessionIdManager = findJettyClass("org.eclipse.jetty.server.SessionManager")
                    .getMethod("getSessionIdManager").invoke(sessionManager);
            objectsWrappedWithMBean.add(sessionIdManager);
          }
        }

        // SecurityHandler securityHandler = webappContext.getSecurityHandler();
        objectsWrappedWithMBean.add(webAppContextClass.getMethod("getSecurityHandler").invoke(webappContext));

        // ServletHandler servletHandler = webappContext.getServletHandler();
        final Object servletHandler = webAppContextClass.getMethod("getServletHandler").invoke(webappContext);
        if(servletHandler != null) {
          objectsWrappedWithMBean.add(servletHandler);

          final Class<?> servletHandlerClass = findJettyClass("org.eclipse.jetty.servlet.ServletHandler");
          // Object[] servletMappings = servletHandler.getServletMappings();
          objectsWrappedWithMBean.add(Arrays.asList((Object[]) servletHandlerClass.getMethod("getServletMappings").invoke(servletHandler)));

          // Object[] servlets = servletHandler.getServlets();
          objectsWrappedWithMBean.add(Arrays.asList((Object[]) servletHandlerClass.getMethod("getServlets").invoke(servletHandler)));
        }
      }
    }

    /**
     * Test if objectName denotes a wrapping Jetty MBean and if so unregister it.
     * @return {@code true} if Jetty MBean was unregistered, otherwise {@code false}
     */
    boolean unregisterJettyJMXBean(ObjectName objectName) {
      if (objectsWrappedWithMBean == null || ! objectName.getDomain().contains("org.eclipse.jetty")) {
        return false;
      }
      else { // Possibly a Jetty MBean that needs to be unregistered
        try {
		      final Object bean = findBeanMethod.invoke(beanContainer, objectName);
          if(bean == null)
            return false;
          
		      // Search suspect list
		      for (Object wrapped : objectsWrappedWithMBean) {
		        if (wrapped == bean) {
              preventor.warn("Jetty MBean '" + objectName + "' is a suspect in causing memory leaks; unregistering");
			        removeBeanMethod.invoke(beanContainer, bean); // Remove it via the MBeanContainer
			        return true;
            }
		      }
  		  }
        catch (Exception ex)  {
          preventor.error(ex);
		    }
		    return false;
	    }
    }

    Class findJettyClass(String className) throws ClassNotFoundException {
      try {
        return Class.forName(className, false, preventor.getClassLoader());
      } catch (ClassNotFoundException e1) {
        try {
          return Class.forName(className);
        } catch (ClassNotFoundException e2) {
          e2.addSuppressed(e1);
          throw e2;
        }
      }
    }
  }
}