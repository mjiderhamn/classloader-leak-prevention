package se.jiderhamn.classloader.leak.prevention;

import java.util.Set;
import javax.servlet.*;

/**
 * Servlet 3.0 {@link ServletContainerInitializer} that will run the {@link PreClassLoaderInitiator}s (delegated to 
 * {@link ClassLoaderLeakPreventorListener} and register a {@link ServletContextListener} that will run 
 * {@link ClassLoaderPreMortemCleanUp}s on {@link ServletContextListener#contextDestroyed(ServletContextEvent)} (delegated
 * to the same {@link ClassLoaderLeakPreventorListener}).
 * @author Mattias Jiderhamn
 */
public class ClassLoaderLeakPreventionContainerInitializer implements javax.servlet.ServletContainerInitializer {
  // TODO Order cannot be guaranteed https://java.net/jira/browse/SERVLET_SPEC-79
  
  @Override
  public void onStartup(Set<Class<?>> set, ServletContext servletContext) throws ServletException {
    final ClassLoaderLeakPreventorListener classLoaderLeakPreventorListener = new ClassLoaderLeakPreventorListener();
    try {
      classLoaderLeakPreventorListener.contextInitialized(servletContext); // Initialize immediately

      servletContext.addListener(new ServletContextListener() {
        @Override
        public void contextInitialized(ServletContextEvent servletContextEvent) {
          // Do nothing, already done above
        }

        @Override
        public void contextDestroyed(ServletContextEvent servletContextEvent) {
          classLoaderLeakPreventorListener.contextDestroyed(servletContextEvent);
        }
      });
    }
    catch (Throwable t) { // (Shouldn't really be needed)
      t.printStackTrace(System.err);
    }

    for(ServletContextListener otherListener : ClassLoaderLeakPreventorListener.getDefaultOtherListeners()) {
      servletContext.addListener(otherListener);
    }
  }
}