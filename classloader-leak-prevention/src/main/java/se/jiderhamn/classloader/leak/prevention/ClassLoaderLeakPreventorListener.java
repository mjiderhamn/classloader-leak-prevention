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

import java.util.LinkedList;
import java.util.List;
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
      e.printStackTrace(System.err);
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

    final ClassLoader webAppClassLoader = Thread.currentThread().getContextClassLoader();
    info("Settings for " + this.getClass().getName() + " (CL: 0x" +
         Integer.toHexString(System.identityHashCode(webAppClassLoader)) + "):");
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
    classLoaderLeakPreventorFactory.addCleanUp(new MultiThreadedHttpConnectionManagerCleanUp());
    
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
    classLoaderLeakPreventorFactory.addCleanUp(new KeepAliveTimerCacheCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new ResourceBundleCleanUp());
    classLoaderLeakPreventorFactory.addCleanUp(new ApacheCommonsLoggingCleanUp()); // Do this last, in case other shutdown procedures want to log something.

    classLoaderLeakPreventor = classLoaderLeakPreventorFactory.newLeakPreventor(webAppClassLoader);

    classLoaderLeakPreventor.runPreClassLoaderInitiators();

    for(ServletContextListener listener : otherListeners) {
      try {
        listener.contextInitialized(servletContextEvent);
      }
      catch(Exception e){
        classLoaderLeakPreventor.error(e);
      }
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {

    info(getClass().getName() + " shutting down context by removing known leaks (CL: 0x" + 
         Integer.toHexString(System.identityHashCode(classLoaderLeakPreventor.getClassLoader())) + ")");

    for(ServletContextListener listener : otherListeners) {
      try {
        listener.contextDestroyed(servletContextEvent);
      }
      catch(Exception e) {
        classLoaderLeakPreventor.error(e);
      }
    }
    
    classLoaderLeakPreventor.runCleanUps();
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Utility methods
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
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
   * To "turn off" info logging override this method in a subclass and make that subclass method empty.
   */
  protected void info(String s) {
    System.out.println(getLogPrefix() + s);
  }

}
