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

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import se.jiderhamn.classloader.leak.prevention.cleanup.*;
import se.jiderhamn.classloader.leak.prevention.preinit.*;

import static se.jiderhamn.classloader.leak.prevention.cleanup.ShutdownHookCleanUp.SHUTDOWN_HOOK_WAIT_MS_DEFAULT;

/**
 * This class helps prevent classloader leaks.
 * <h1>Basic setup</h1>
 * <p>Activate protection by adding this class as a context listener
 * in your <code>web.xml</code>, like this:</p>
 * <pre>
 *  &lt;listener&gt;
 *     &lt;listener-class&gt;se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor&lt;/listener-class&gt;
 *  &lt;/listener&gt; 
 * </pre>
 * 
 * You should usually declare this <code>listener</code> before any other listeners, to make it "outermost".
 *
 * <h1>Configuration</h1>
 * The context listener has a number of settings that can be configured with context parameters in <code>web.xml</code>,
 * i.e.:
 * 
 * <pre>
 *   &lt;context-param&gt;
 *     &lt;param-name&gt;ClassLoaderLeakPreventor.stopThreads&lt;/param-name&gt;
 *     &lt;param-value&gt;false&lt;/param-value&gt;
 *   &lt;/context-param&gt;
 * </pre>
 * 
 * The available settings are
 * <table border="1">
 *   <tr>
 *     <th>Parameter name</th>
 *     <th>Default value</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td><code>ClassLoaderLeakPreventor.stopThreads</code></td>
 *     <td><code>true</code></td>
 *     <td>Should threads tied to the web app classloader be forced to stop at application shutdown?</td>
 *   </tr>
 *   <tr>
 *     <td><code>ClassLoaderLeakPreventor.stopTimerThreads</code></td>
 *     <td><code>true</code></td>
 *     <td>Should Timer threads tied to the web app classloader be forced to stop at application shutdown?</td>
 *   </tr>
 *   <tr>
 *     <td><code>ClassLoaderLeakPreventor.executeShutdownHooks</td>
 *     <td><code>true</code></td>
 *     <td>Should shutdown hooks registered from the application be executed at application shutdown?</td>
 *   </tr>
 *   <tr>
 *     <td><code>ClassLoaderLeakPreventor.threadWaitMs</td>
 *     <td><code>5000</code> (5 seconds)</td>
 *     <td>No of milliseconds to wait for threads to finish execution, before stopping them.</td>
 *   </tr>
 *   <tr>
 *     <td><code>ClassLoaderLeakPreventor.shutdownHookWaitMs</code></td>
 *     <td><code>10000</code> (10 seconds)</td>
 *     <td>
 *       No of milliseconds to wait for shutdown hooks to finish execution, before stopping them.
 *       If set to -1 there will be no waiting at all, but Thread is allowed to run until finished.
 *     </td>
 *   </tr>
 * </table>
 * 
 * 
 * <h1>License</h1>
 * <p>This code is licensed under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2</a> license,
 * which allows you to include modified versions of the code in your distributed software, without having to release
 * your source code.</p>
 *
 * <h1>More info</h1> 
 * <p>For more info, see 
 * <a href="http://java.jiderhamn.se/2012/03/04/classloader-leaks-vi-this-means-war-leak-prevention-library/">here</a>.
 * </p>
 * 
 * <h1>Design goals</h1>
 * <p>If you want to help improve this component, you should be aware of the design goals</p>
 * <p>
 *   Primary design goal: Zero dependencies. The component should build and run using nothing but the JDK and the 
 *   Servlet API. Specifically we should <b>not</b> depend on any logging framework, since they are part of the problem.
 *   We also don't want to use any utility libraries, in order not to impose any further dependencies into any project
 *   that just wants to get rid of classloader leaks.
 *   Access to anything outside of the standard JDK (in order to prevent a known leak) should be managed
 *   with reflection.
 * </p>
 * <p>
 *   Secondary design goal: Keep the runtime component in a single <code>.java</code> file. It should be possible to
 *   just add this one <code>.java</code> file into your own source tree.
 * </p>
 * 
 * @author Mattias Jiderhamn, 2012-2013
 */
@SuppressWarnings("WeakerAccess")
public class ClassLoaderLeakPreventorListener implements ServletContextListener {

  protected ClassLoaderLeakPreventor classLoaderLeakPreventor;

  /** Other {@link javax.servlet.ServletContextListener}s to use also */
  protected final List<ServletContextListener> otherListeners = new LinkedList<ServletContextListener>();

  public ClassLoaderLeakPreventorListener() {
    try{
      Class<ServletContextListener> introspectorCleanupListenerClass = 
          (Class<ServletContextListener>) Class.forName("org.springframework.web.util.IntrospectorCleanupListener");
      otherListeners.add(introspectorCleanupListenerClass.newInstance());
    }
    catch (ClassNotFoundException e) {
      // Ignore silently - Spring not present on classpath
    }
    catch(Exception e){
      error(e);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Implement javax.servlet.ServletContextListener 
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    final ServletContext servletContext = servletContextEvent.getServletContext();
    
    // Should threads tied to the web app classloader be forced to stop at application shutdown?
    boolean stopThreads = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.stopThreads"));
    
    // Should Timer threads tied to the web app classloader be forced to stop at application shutdown?
    boolean stopTimerThreads = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.stopTimerThreads"));
    
    // Should shutdown hooks registered from the application be executed at application shutdown?
    boolean executeShutdownHooks = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.executeShutdownHooks"));

    /* 
     * Should the {@code oracle.jdbc.driver.OracleTimeoutPollingThread} thread be forced to start with system classloader,
     * in case Oracle JDBC driver is present? This is normally a good idea, but can be disabled in case the Oracle JDBC
     * driver is not used even though it is on the classpath.
     */
    boolean startOracleTimeoutThread = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.startOracleTimeoutThread"));
    
    // No of milliseconds to wait for threads to finish execution, before stopping them.
    int threadWaitMs = getIntInitParameter(servletContext, "ClassLoaderLeakPreventor.threadWaitMs", ClassLoaderLeakPreventor.THREAD_WAIT_MS_DEFAULT);

    /* 
     * No of milliseconds to wait for shutdown hooks to finish execution, before stopping them.
     * If set to -1 there will be no waiting at all, but Thread is allowed to run until finished.
     */
    int shutdownHookWaitMs = getIntInitParameter(servletContext, "ClassLoaderLeakPreventor.shutdownHookWaitMs", SHUTDOWN_HOOK_WAIT_MS_DEFAULT);
    
    info("Settings for " + this.getClass().getName() + " (CL: 0x" +
         Integer.toHexString(System.identityHashCode(getWebApplicationClassLoader())) + "):");
    info("  stopThreads = " + stopThreads);
    info("  stopTimerThreads = " + stopTimerThreads);
    info("  executeShutdownHooks = " + executeShutdownHooks);
    info("  threadWaitMs = " + threadWaitMs + " ms");
    info("  shutdownHookWaitMs = " + shutdownHookWaitMs + " ms");
    
    info("Initializing context by loading some known offenders with system classloader");
    
    final ClassLoaderLeakPreventorFactory classLoaderLeakPreventorFactory = new ClassLoaderLeakPreventorFactory();
    
    // TODO https://github.com/mjiderhamn/classloader-leak-prevention/issues/44 Move to factory
    // This part is heavily inspired by Tomcats JreMemoryLeakPreventionListener  
    // See http://svn.apache.org/viewvc/tomcat/trunk/java/org/apache/catalina/core/JreMemoryLeakPreventionListener.java?view=markup
    classLoaderLeakPreventorFactory.addPreInitiator(new AwtToolkitInitiator());
    // initSecurityProviders()
    classLoaderLeakPreventorFactory.addPreInitiator(new JdbcDriversInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new SunAwtAppContextInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new SecurityPolicyInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new SecurityProvidersInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new DocumentBuilderFactoryInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new ReplaceDOMNormalizerSerializerAbortException());
    classLoaderLeakPreventorFactory.addPreInitiator(new DatatypeConverterImplInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new JavaxSecurityLoginConfigurationInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new JarUrlConnectionInitiator());
    // Load Sun specific classes that may cause leaks
    classLoaderLeakPreventorFactory.addPreInitiator(new LdapPoolManagerInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new Java2dDisposerInitiator());
    classLoaderLeakPreventorFactory.addPreInitiator(new SunGCInitiator());
    if(startOracleTimeoutThread)
      classLoaderLeakPreventorFactory.addPreInitiator(new OracleJdbcThreadInitiator());

    classLoaderLeakPreventorFactory.addCleanUp(new BeanIntrospectorCleanUp());
    // Apache Commons Pool can leave unfinished threads. Anything specific we can do?
    classLoaderLeakPreventorFactory.addCleanUp(new BeanELResolverCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new BeanValidationCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new JavaServerFaces2746CleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new GeoToolsCleanUp());
    // Can we do anything about Google Guice ?
    // Can we do anything about Groovy http://jira.codehaus.org/browse/GROOVY-4154 ?
    classLoaderLeakPreventorFactory.addCleanUp(new IntrospectionUtilsCleanUp());
    // Can we do anything about Logback http://jira.qos.ch/browse/LBCORE-205 ?
    classLoaderLeakPreventorFactory.addCleanUp(new IIOServiceProviderCleanUp()); // clear ImageIO registry
    
    ////////////////////
    // Fix generic leaks
    classLoaderLeakPreventorFactory.addCleanUp(new DriverManagerCleanUp());
    
    classLoaderLeakPreventorFactory.addCleanUp(new DefaultAuthenticatorCleanUp());

    classLoaderLeakPreventorFactory.addCleanUp(new MBeanCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new MXBeanNotificationListenersCleanUp());
    
    classLoaderLeakPreventorFactory.addCleanUp(new ShutdownHookCleanUp(executeShutdownHooks, shutdownHookWaitMs));
    classLoaderLeakPreventorFactory.addCleanUp(new PropertyEditorCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new SecurityProviderCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new ProxySelectorCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new RmiTargetsCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new StopThreadsCleanUp(stopThreads, stopTimerThreads));
    classLoaderLeakPreventorFactory.addCleanUp(new ThreadGroupCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new ThreadLocalCleanUp()); // This must be done after threads have been stopped, or new ThreadLocals may be added by those threads 

    classLoaderLeakPreventor = classLoaderLeakPreventorFactory
        .newLeakPreventor(ClassLoaderLeakPreventorListener.class.getClassLoader());

    classLoaderLeakPreventor.runPreClassLoaderInitiators();

    for(ServletContextListener listener : otherListeners) {
      try {
        listener.contextInitialized(servletContextEvent);
      }
      catch(Exception e){
        error(e);
      }
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {

    final boolean jvmIsShuttingDown = isJvmShuttingDown();
    if(jvmIsShuttingDown) {
      info("JVM is shutting down - skip cleanup");
      return; // Don't do anything more
    }

    info(getClass().getName() + " shutting down context by removing known leaks (CL: 0x" + 
         Integer.toHexString(System.identityHashCode(getWebApplicationClassLoader())) + ")");

    for(ServletContextListener listener : otherListeners) {
      try {
        listener.contextDestroyed(servletContextEvent);
      }
      catch(Exception e) {
        error(e);
      }
    }
    
    classLoaderLeakPreventor.runCleanUps();

    ////////////////////
    // Fix generic leaks
    
    // TODO https://github.com/mjiderhamn/classloader-leak-prevention/issues/44
    unsetCachedKeepAliveTimer();
    
    // TODO https://github.com/mjiderhamn/classloader-leak-prevention/issues/44
    try {
      try { // First try Java 1.6 method
        final Method clearCache16 = ResourceBundle.class.getMethod("clearCache", ClassLoader.class);
        debug("Since Java 1.6+ is used, we can call " + clearCache16);
        clearCache16.invoke(null, getWebApplicationClassLoader());
      }
      catch (NoSuchMethodException e) {
        // Not Java 1.6+, we have to clear manually
        final Map<?,?> cacheList = getStaticFieldValue(ResourceBundle.class, "cacheList"); // Java 5: SoftCache extends AbstractMap
        final Iterator<?> iter = cacheList.keySet().iterator();
        Field loaderRefField = null;
        while(iter.hasNext()) {
          Object key = iter.next(); // CacheKey
          
          if(loaderRefField == null) { // First time
            loaderRefField = key.getClass().getDeclaredField("loaderRef");
            loaderRefField.setAccessible(true);
          }
          WeakReference<ClassLoader> loaderRef = (WeakReference<ClassLoader>) loaderRefField.get(key); // LoaderReference extends WeakReference
          ClassLoader classLoader = loaderRef.get();
          
          if(isWebAppClassLoaderOrChild(classLoader)) {
            info("Removing ResourceBundle from cache: " + key);
            iter.remove();
          }
          
        }
      }
    }
    catch(Exception ex) {
      error(ex);
    }
    
    // (CacheKey of java.util.ResourceBundle.NONEXISTENT_BUNDLE will point to first referring classloader...)
    

    // TODO https://github.com/mjiderhamn/classloader-leak-prevention/issues/44
    // Release this classloader from Apache Commons Logging (ACL) by calling
    //   LogFactory.release(getCurrentClassLoader());
    // Use reflection in case ACL is not present.
    // Do this last, in case other shutdown procedures want to log something.
    
    final Class<?> logFactory = findClass("org.apache.commons.logging.LogFactory");
    if(logFactory != null) { // Apache Commons Logging present
      info("Releasing web app classloader from Apache Commons Logging");
      try {
        logFactory.getMethod("release", java.lang.ClassLoader.class)
            .invoke(null, getWebApplicationClassLoader());
      }
      catch (Exception ex) {
        error(ex);
      }
    }
    
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Fix generic leaks
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /** 
   * Since Keep-Alive-Timer thread may have terminated, but still be referenced, we need to make sure it does not
   * reference this classloader.
   */
  protected void unsetCachedKeepAliveTimer() {
    Object keepAliveCache = getStaticFieldValue("sun.net.www.http.HttpClient", "kac", true);
    if(keepAliveCache != null) {
      final Thread keepAliveTimer = getFieldValue(keepAliveCache, "keepAliveTimer");
      if(keepAliveTimer != null) {
        if(isWebAppClassLoaderOrChild(keepAliveTimer.getContextClassLoader())) {
          keepAliveTimer.setContextClassLoader(getWebApplicationClassLoader().getParent());
          error("ContextClassLoader of sun.net.www.http.HttpClient cached Keep-Alive-Timer set to parent instead");
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Utility methods
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  protected ClassLoader getWebApplicationClassLoader() {
    return ClassLoaderLeakPreventorListener.class.getClassLoader();
    // Alternative return Thread.currentThread().getContextClassLoader();
  }

  /** Test if provided ClassLoader is the classloader of the web application, or a child thereof */
  protected boolean isWebAppClassLoaderOrChild(ClassLoader cl) {
    final ClassLoader webAppCL = getWebApplicationClassLoader();
    // final ClassLoader webAppCL = Thread.currentThread().getContextClassLoader();

    while(cl != null) {
      if(cl == webAppCL)
        return true;
      
      cl = cl.getParent();
    }

    return false;
  }

  protected <E> E getStaticFieldValue(Class<?> clazz, String fieldName) {
    Field staticField = findField(clazz, fieldName);
    return (staticField != null) ? (E) getStaticFieldValue(staticField) : null;
  }

  protected <E> E getStaticFieldValue(String className, String fieldName, boolean trySystemCL) {
    Field staticField = findFieldOfClass(className, fieldName, trySystemCL);
    return (staticField != null) ? (E) getStaticFieldValue(staticField) : null;
  }

  protected Field findFieldOfClass(String className, String fieldName, boolean trySystemCL) {
    Class<?> clazz = findClass(className, trySystemCL);
    if(clazz != null) {
      return findField(clazz, fieldName);
    }
    else
      return null;
  }
  
  protected Class<?> findClass(String className) {
    return findClass(className, false);
  }
  
  protected Class<?> findClass(String className, boolean trySystemCL) {
    return classLoaderLeakPreventor.findClass(className, trySystemCL);
  }
  
  protected Field findField(Class<?> clazz, String fieldName) {
    return classLoaderLeakPreventor.findField(clazz, fieldName);
  }
  
  protected <T> T getStaticFieldValue(Field field) {
    return classLoaderLeakPreventor.getStaticFieldValue(field);
  }
  
  protected <T> T getFieldValue(Object obj, String fieldName) {
    final Field field = findField(obj.getClass(), fieldName);
    return (T) getFieldValue(field, obj);
  }
  
  protected <T> T getFieldValue(Field field, Object obj) {
    try {
      return (T) field.get(obj);
    }
    catch (Exception ex) {
      warn(ex);
      // Silently ignore
      return null;
    }
  }

  /** Is the JVM currently shutting down? */
  protected boolean isJvmShuttingDown() {
    try {
      final Thread dummy = new Thread(); // Will never be started
      Runtime.getRuntime().removeShutdownHook(dummy);
      return false;
    }
    catch (IllegalStateException isex) {
      return true; // Shutting down
    }
    catch (Throwable t) { // Any other Exception, assume we are not shutting down
      return false;
    }
  }

  /** Parse init parameter for integer value, returning default if not found or invalid */
  protected static int getIntInitParameter(ServletContext servletContext, String parameterName, int defaultValue) {
    final String parameterString = servletContext.getInitParameter(parameterName);
    if(parameterString != null && parameterString.trim().length() > 0) {
      try {
        return Integer.parseInt(parameterString);
      }
      catch (NumberFormatException e) {
        // Do nothing, return default value
      }
    }
    return defaultValue;
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Log methods
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /*
   * Since logging frameworks are part of the problem, we don't want to depend on any of them here.
   * Feel free however to subclass or fork and use a log framework, in case you think you know what you're doing.
   */
  
  protected String getLogPrefix() {
    return ClassLoaderLeakPreventorListener.class.getSimpleName() + ": ";
  }
  
  /**
   * To "turn off" debug logging override this method in a subclass and make that subclass method empty.
   */
  protected void debug(String s) {
    System.out.println(getLogPrefix() + s);
  } 

  /**
   * To "turn off" info logging override this method in a subclass and make that subclass method empty.
   */
  protected void info(String s) {
    System.out.println(getLogPrefix() + s);
  } 

  /**
   * To "turn off" warn logging override this method in a subclass and make that subclass method empty.
   * Also turn off {@link #warn(Throwable)}.
   */
  protected void warn(String s) {
    System.err.println(getLogPrefix() + s);
  } 

  /**
   * To "turn off" warn logging override this method in a subclass and make that subclass method empty.
   * Also turn off {@link #warn(String)}.
   */
  protected void warn(Throwable t) {
    t.printStackTrace(System.err);
  } 

  /**
   * To "turn off" error logging override this method in a subclass and make that subclass method empty.
   * Also turn off {@link #error(Throwable)}.
   */
  protected void error(String s) {
    System.err.println(getLogPrefix() + s);
  } 

  /**
   * To "turn off" error logging override this method in a subclass and make that subclass method empty.
   * Also turn off {@link #error(String)}.
   */
  protected void error(Throwable t) {
    t.printStackTrace(System.err);
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
}
