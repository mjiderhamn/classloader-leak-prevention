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

import java.beans.PropertyEditorManager;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.management.RuntimeMXBean;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.net.Authenticator;
import java.net.ProxySelector;
import java.net.URL;
import java.security.*;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.IIOServiceProvider;
import javax.imageio.spi.ServiceRegistry;
import javax.management.*;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.swing.*;

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
public class ClassLoaderLeakPreventor implements ServletContextListener {
  
  /** Default no of milliseconds to wait for threads to finish execution */
  public static final int THREAD_WAIT_MS_DEFAULT = 5 * 1000; // 5 seconds

  /** Default no of milliseconds to wait for shutdown hook to finish execution */
  public static final int SHUTDOWN_HOOK_WAIT_MS_DEFAULT = 10 * 1000; // 10 seconds

  public static final String JURT_ASYNCHRONOUS_FINALIZER = "com.sun.star.lib.util.AsynchronousFinalizer";
  
  /** Class name for per thread transaction in Caucho Resin transaction manager */
  public static final String CAUCHO_TRANSACTION_IMPL = "com.caucho.transaction.TransactionImpl";

  private static final ProtectionDomain[] NO_DOMAINS = new ProtectionDomain[0];

  private static final AccessControlContext NO_DOMAINS_ACCESS_CONTROL_CONTEXT = new AccessControlContext(NO_DOMAINS);

  ///////////
  // Settings
  
  
  /** Should threads tied to the web app classloader be forced to stop at application shutdown? */
  protected boolean stopThreads = true;
  
  /** Should Timer threads tied to the web app classloader be forced to stop at application shutdown? */
  protected boolean stopTimerThreads = true;
  
  /** Should shutdown hooks registered from the application be executed at application shutdown? */
  protected boolean executeShutdownHooks = true;

  /** 
   * Should the {@code oracle.jdbc.driver.OracleTimeoutPollingThread} thread be forced to start with system classloader,
   * in case Oracle JDBC driver is present? This is normally a good idea, but can be disabled in case the Oracle JDBC
   * driver is not used even though it is on the classpath.
   */
  protected boolean startOracleTimeoutThread = true;

  /** 
   * No of milliseconds to wait for threads to finish execution, before stopping them.
   */
  protected int threadWaitMs = THREAD_WAIT_MS_DEFAULT;

  /** 
   * No of milliseconds to wait for shutdown hooks to finish execution, before stopping them.
   * If set to -1 there will be no waiting at all, but Thread is allowed to run until finished.
   */
  protected int shutdownHookWaitMs = SHUTDOWN_HOOK_WAIT_MS_DEFAULT;

  /** Is it possible, that we are running under JBoss? */
  private boolean mayBeJBoss = false;

  /** are we running in the Oracle/Sun Java Runtime Environment? */
  private boolean isOracleJRE;

  protected final Field java_lang_Thread_threadLocals;

  protected final Field java_lang_Thread_inheritableThreadLocals;

  protected final Field java_lang_ThreadLocal$ThreadLocalMap_table;

  protected Field java_lang_ThreadLocal$ThreadLocalMap$Entry_value;

  /** Other {@link javax.servlet.ServletContextListener}s to use also */
  protected final List<ServletContextListener> otherListeners = new LinkedList<ServletContextListener>();

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
    stopThreads = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.stopThreads"));
    stopTimerThreads = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.stopTimerThreads"));
    executeShutdownHooks = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.executeShutdownHooks"));
    startOracleTimeoutThread = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.startOracleTimeoutThread"));
    threadWaitMs = getIntInitParameter(servletContext, "ClassLoaderLeakPreventor.threadWaitMs", THREAD_WAIT_MS_DEFAULT);
    shutdownHookWaitMs = getIntInitParameter(servletContext, "ClassLoaderLeakPreventor.shutdownHookWaitMs", SHUTDOWN_HOOK_WAIT_MS_DEFAULT);
    
    info("Settings for " + this.getClass().getName() + " (CL: 0x" +
         Integer.toHexString(System.identityHashCode(getWebApplicationClassLoader())) + "):");
    info("  stopThreads = " + stopThreads);
    info("  stopTimerThreads = " + stopTimerThreads);
    info("  executeShutdownHooks = " + executeShutdownHooks);
    info("  threadWaitMs = " + threadWaitMs + " ms");
    info("  shutdownHookWaitMs = " + shutdownHookWaitMs + " ms");
    
    mayBeJBoss = isJBoss();
    isOracleJRE = isOracleJRE();
    
    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

    info("Initializing context by loading some known offenders with system classloader");
    
    // This part is heavily inspired by Tomcats JreMemoryLeakPreventionListener  
    // See http://svn.apache.org/viewvc/tomcat/trunk/java/org/apache/catalina/core/JreMemoryLeakPreventionListener.java?view=markup
    try {
      // Switch to system classloader in before we load/call some JRE stuff that will cause 
      // the current classloader to be available for garbage collection
      Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
  
      // Use doPrivileged() not to perform secured actions, but to avoid threads spawned inheriting the 
      // AccessControlContext of the current thread, since among the ProtectionDomains there will be one
      // (the top one) whose classloader is the web app classloader
      AccessController.doPrivileged(new PrivilegedAction<Object>() {
        @Override
        public Object run() {
          initAwt();
    
          initSecurityProviders();
          
          initJdbcDrivers();
    
          initSunAwtAppContext();
    
          initSecurityPolicy();
    
          initDocumentBuilderFactory();
          
          initDatatypeConverterImpl();
    
          initJavaxSecurityLoginConfiguration();
    
          initJarUrlConnection();
    
          /////////////////////////////////////////////////////
          // Load Sun specific classes that may cause leaks
          
          initLdapPoolManager();
          
          initJava2dDisposer();
          
          initSunGC();
          
          initOracleJdbcThread();
          
          return null; // Nothing to return
        }
      }, createAccessControlContext());
    }
    finally {
      // Reset original classloader
      Thread.currentThread().setContextClassLoader(contextClassLoader);
    }

    for(ServletContextListener listener : otherListeners) {
      try {
        listener.contextInitialized(servletContextEvent);
      }
      catch(Exception e){
        error(e);
      }
    }
  }
  
  /** 
   * Create {@link AccessControlContext} that is used in {@link #contextInitialized(javax.servlet.ServletContextEvent)}.
   * The motive is to avoid spawned threads from inheriting all the {@link java.security.ProtectionDomain}s of the 
   * running code, since that will include the web app classloader.
   */
  protected AccessControlContext createAccessControlContext() {

    final DomainCombiner domainCombiner = new DomainCombiner() {
      @Override
      public ProtectionDomain[] combine(ProtectionDomain[] currentDomains, ProtectionDomain[] assignedDomains) {
        if(assignedDomains != null && assignedDomains.length > 0) {
          error("Unexpected assignedDomains - please report to developer of this library!");
        }
        
        // Keep all ProtectionDomain not involving the web app classloader 
        final List<ProtectionDomain> output = new ArrayList<ProtectionDomain>();
        for(ProtectionDomain protectionDomain : currentDomains) {
          if(protectionDomain.getClassLoader() == null || 
             ! isWebAppClassLoaderOrChild(protectionDomain.getClassLoader())) {
            output.add(protectionDomain);
          }
        }
        return output.toArray(new ProtectionDomain[output.size()]);
      }
    };

    try { // Try the normal way
      return new AccessControlContext(NO_DOMAINS_ACCESS_CONTROL_CONTEXT, domainCombiner);
    }
    catch (SecurityException e) { // createAccessControlContext not granted
      try { // Try reflection
        Constructor<AccessControlContext> constructor = 
            AccessControlContext.class.getDeclaredConstructor(ProtectionDomain[].class, DomainCombiner.class);
        constructor.setAccessible(true);
        return constructor.newInstance(NO_DOMAINS, domainCombiner);
      }
      catch (Exception e1) {
        error("createAccessControlContext not granted and AccessControlContext could not be created via reflection");
        return AccessController.getContext();
      }
    }
  } 

  /**
   * Override this method if you want to customize how we determine if we're running in
   * JBoss WildFly (a.k.a JBoss AS).
   */
  protected boolean isJBoss() {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    
    try {
      // If package org.jboss is found, we may be running under JBoss
      return (contextClassLoader.getResource("org/jboss") != null);
    }
    catch(Exception ex) {
      return false;
    }
  }
  
  /**
   * Override this method if you want to customize how we determine if this is a Oracle/Sun
   * Java Runtime Environment.
   */
  protected boolean isOracleJRE() {
    String javaVendor = System.getProperty("java.vendor");
    
    return javaVendor.startsWith("Oracle") || javaVendor.startsWith("Sun");
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * The first call to java.awt.Toolkit.getDefaultToolkit() will spawn a new thread with the
   * same contextClassLoader as the caller.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initAwt() {
    try {
      java.awt.Toolkit.getDefaultToolkit(); // Will start a Thread
    }
    catch (Throwable t) {
      error(t);
      warn("Consider adding -Djava.awt.headless=true to your JVM parameters");
    }
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * Custom java.security.Provider loaded in your web application and registered with
   * java.security.Security.addProvider() must be unregistered with java.security.Security.removeProvider()
   * at application shutdown, or it will cause leaks.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initSecurityProviders() {
    java.security.Security.getProviders();
  }
  
  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * Your JDBC driver will be registered in java.sql.DriverManager, which means that if
   * you include your JDBC driver inside your web application, there will be a reference
   * to your webapps classloader from system classes (see
   * <a href="http://java.jiderhamn.se/2012/01/01/classloader-leaks-ii-find-and-work-around-unwanted-references/">part II</a>).
   * The simple solution is to put JDBC driver on server level instead, but you can also
   * deregister the driver at application shutdown.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initJdbcDrivers() {
    java.sql.DriverManager.getDrivers(); // Load initial drivers using system classloader
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * There will be a strong reference from {@link sun.awt.AppContext#contextClassLoader} to the classloader of the calls
   * to {@link sun.awt.AppContext#getAppContext()}. Avoid leak by forcing initialization using system classloader. 
   * Note that Google Web Toolkit (GWT) will trigger this leak via its use of javax.imageio.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initSunAwtAppContext() {
    try {
      javax.imageio.ImageIO.getCacheDirectory(); // Will call sun.awt.AppContext.getAppContext()
      new JEditorPane("text/plain", "dummy"); // According to GitHub user dany52, the above is not enough
    }
    catch (Throwable t) {
      error(t);
      warn("Consider adding -Djava.awt.headless=true to your JVM parameters");
    }
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * javax.security.auth.Policy.getPolicy() will keep a strong static reference to
   * the contextClassLoader of the first calling thread.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initSecurityPolicy() {
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
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * The classloader of the first thread to call DocumentBuilderFactory.newInstance().newDocumentBuilder()
   * seems to be unable to garbage collection. Is it believed this is caused by some JVM internal bug.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initDocumentBuilderFactory() {
    try {
      javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }
    catch (Exception ex) { // Example: ParserConfigurationException
      error(ex);
    }
  }
  
  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * javax.xml.bind.DatatypeConverterImpl in the JAXB Reference Implementation shipped with JDK 1.6+ will
   * keep a static reference (datatypeFactory) to a concrete subclass of javax.xml.datatype.DatatypeFactory,
   * that is resolved when the class is loaded (which I believe happens if you have custom bindings that
   * reference the static methods in javax.xml.bind.DatatypeConverter). It seems that if for example you
   * have a version of Xerces inside your application, the factory method may resolve
   * org.apache.xerces.jaxp.datatype.DatatypeFactoryImpl as the implementation to use (rather than
   * com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl shipped with the JDK), which
   * means there will a reference from javax.xml.bind.DatatypeConverterImpl to your classloader.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initDatatypeConverterImpl() {
    try {
      Class.forName("javax.xml.bind.DatatypeConverterImpl"); // Since JDK 1.6. May throw java.lang.Error
    }
    catch (ClassNotFoundException e) {
      // Do nothing
    }
    catch (Throwable t) {
      warn(t);
    }
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * The class javax.security.auth.login.Configuration will keep a strong static reference to the
   * contextClassLoader of Thread from which the class is loaded.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initJavaxSecurityLoginConfiguration() {
    try {
      Class.forName("javax.security.auth.login.Configuration", true, ClassLoader.getSystemClassLoader());
    }
    catch (ClassNotFoundException e) {
      // Do nothing
    }
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * The caching mechanism of JarURLConnection can prevent JAR files to be reloaded. See
   * <a href="http://bugs.sun.com/bugdatabase/view_bug.do;jsessionid=b8cdbff5fd7a2482996ac1c7f708?bug_id=4405789">this bug report</a>.
   * It is not entirely clear whether this will actually leak classloaders.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initJarUrlConnection() {
    // This probably does not affect classloaders, but prevents some problems with .jar files
    try {
      // URL needs to be well-formed, but does not need to exist
      new URL("jar:file://dummy.jar!/").openConnection().setDefaultUseCaches(false);
    }
    catch (Exception ex) {
      error(ex);
    }
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * The contextClassLoader of the thread loading the com.sun.jndi.ldap.LdapPoolManager class may be kept
   * from being garbage collected, since it will start a new thread if the system property.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initLdapPoolManager() {
    try {
      Class.forName("com.sun.jndi.ldap.LdapPoolManager");
    }
    catch(ClassNotFoundException cnfex) {
      if(isOracleJRE)
        error(cnfex);
    }
  }
  
  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * Loading the class sun.java2d.Disposer will spawn a new thread with the same contextClassLoader.
   * <a href="https://issues.apache.org/bugzilla/show_bug.cgi?id=51687">More info</a>.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initJava2dDisposer() {
    try {
      Class.forName("sun.java2d.Disposer"); // Will start a Thread
    }
    catch (ClassNotFoundException cnfex) {
      if(isOracleJRE && ! mayBeJBoss) // JBoss blocks this package/class, so don't warn
        error(cnfex);
    }
  }

  /**
   * To skip this step override this method in a subclass and make that subclass method empty.
   * 
   * sun.misc.GC.requestLatency(long), which is known to be called from
   * javax.management.remote.rmi.RMIConnectorServer.start(), will cause the current
   * contextClassLoader to be unavailable for garbage collection.
   * 
   * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initSunGC() {
    try {
      Class<?> gcClass = Class.forName("sun.misc.GC");
      final Method requestLatency = gcClass.getDeclaredMethod("requestLatency", long.class);
      requestLatency.invoke(null, 3600000L);
    }
    catch (ClassNotFoundException cnfex) {
      if(isOracleJRE)
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

  /**
   * See https://github.com/mjiderhamn/classloader-leak-prevention/issues/8
   * and https://github.com/mjiderhamn/classloader-leak-prevention/issues/23
   * and http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
   */
  protected void initOracleJdbcThread() {
    if(startOracleTimeoutThread) {
      // Cause oracle.jdbc.driver.OracleTimeoutPollingThread to be started with contextClassLoader = system classloader  
      try {
        Class.forName("oracle.jdbc.driver.OracleTimeoutThreadPerVM");
      }
      catch (ClassNotFoundException e) {
        // Ignore silently - class not present
      }

      // Cause oracle.jdbc.driver.BlockSource.ThreadedCachingBlockSource.BlockReleaser to be started with contextClassLoader = system classloader  
      try {
        Class.forName("oracle.jdbc.driver.BlockSource$ThreadedCachingBlockSource$BlockReleaser");
      }
      catch (ClassNotFoundException e) {
        // Ignore silently - class not present
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

    //////////////////
    // Fix known leaks
    //////////////////
    
    java.beans.Introspector.flushCaches(); // Clear cache of strong references
    
    // Apache Commons Pool can leave unfinished threads. Anything specific we can do?
    
    clearBeanELResolverCache();

    fixBeanValidationApiLeak();
    
    fixJsfLeak();
    
    fixGeoToolsLeak();
    
    // Can we do anything about Google Guice ?
    
    // Can we do anything about Groovy http://jira.codehaus.org/browse/GROOVY-4154 ?

    clearIntrospectionUtilsCache();

    // Can we do anything about Logback http://jira.qos.ch/browse/LBCORE-205 ?
    
    // Force the execution of the cleanup code for JURT; see https://issues.apache.org/ooo/show_bug.cgi?id=122517
    forceStartOpenOfficeJurtCleanup();
    
    // clear ImageIO registry
    deregisterIIOServiceProvider();

    ////////////////////
    // Fix generic leaks
    
    // Deregister JDBC drivers contained in web application
    deregisterJdbcDrivers();
    
    // Unregister MBeans loaded by the web application class loader
    unregisterMBeans();
    
    // Unregister MXBean NotificationListeners/NotificationFilters/handbacks loaded by the web application class loader
    unregisterMXBeanNotificationListeners();
    
    // Deregister shutdown hooks - execute them immediately
    deregisterShutdownHooks();
    
    deregisterPropertyEditors();

    deregisterSecurityProviders();
    
    clearDefaultAuthenticator();
    
    clearDefaultProxySelector();
    
    deregisterRmiTargets();
    
    stopThreads();
    
    destroyThreadGroups();

    // This must be done after threads have been stopped, or new ThreadLocals may be added by those threads
    clearThreadLocalsOfAllThreads();

    unsetCachedKeepAliveTimer();
    
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
  
  /** Unregister ImageIO Service Provider loaded by the web application class loader */
  protected void deregisterIIOServiceProvider() {
    IIORegistry registry = IIORegistry.getDefaultInstance();
    Iterator<Class<?>> categories = registry.getCategories();
    ServiceRegistry.Filter classLoaderFilter = new ServiceRegistry.Filter() {
      @Override
      public boolean filter(Object provider) {
        //remove all service provider loaded by the current ClassLoader
        return isLoadedInWebApplication(provider);
      }
    };
    while (categories.hasNext()) {
      @SuppressWarnings("unchecked")
      Class<IIOServiceProvider> category = (Class<IIOServiceProvider>) categories.next();
      Iterator<IIOServiceProvider> serviceProviders = registry.getServiceProviders(
          category,
          classLoaderFilter, true);
      if (serviceProviders.hasNext()) {
        //copy to list
        List<IIOServiceProvider> serviceProviderList = new ArrayList<IIOServiceProvider>();
        while (serviceProviders.hasNext()) {
          serviceProviderList.add(serviceProviders.next());
        }
        for (IIOServiceProvider serviceProvider : serviceProviderList) {
          warn("ImageIO " + category.getSimpleName() + " service provider deregistered: "
            + serviceProvider.getDescription(Locale.ROOT));
          registry.deregisterServiceProvider(serviceProvider);
        }
      }
    }
  }

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

  /** Unregister MBeans loaded by the web application class loader */
  protected void unregisterMBeans() {
    try {
      final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
      final Set<ObjectName> allMBeanNames = mBeanServer.queryNames(new ObjectName("*:*"), null);

      // Special treatment for Jetty, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=423255
      JettyJMXRemover jettyJMXRemover = null;
      if(isJettyWithJMX()) {
        try {
          jettyJMXRemover = new JettyJMXRemover(getWebApplicationClassLoader());
        }
        catch (Exception ex) {
          error(ex);
        }
      }
      
      // Look for custom MBeans
      for(ObjectName objectName : allMBeanNames) {
        try {
          if (jettyJMXRemover != null && jettyJMXRemover.unregisterJettyJMXBean(objectName)) {
        	  continue;
          }
          
          final ClassLoader mBeanClassLoader = mBeanServer.getClassLoaderFor(objectName);
          if(isWebAppClassLoaderOrChild(mBeanClassLoader)) { // MBean loaded in web application
            warn("MBean '" + objectName + "' was loaded in web application; unregistering");
            mBeanServer.unregisterMBean(objectName);
          }
          /* 
          else if(... instanceof NotificationBroadcasterSupport) {
            unregisterNotificationListeners((NotificationBroadcasterSupport) ...);
          }
          */
        }
        catch(Exception e) { // MBeanRegistrationException / InstanceNotFoundException
          error(e);
        }
      }
    }
    catch (Exception e) { // MalformedObjectNameException
      error(e);
    }
  }

  /** 
     * Unregister MBeans loaded by the web application class loader,
     * and MBean {@link NotificationListener}s loaded by the web application class loader
     */
  protected void unregisterMXBeanNotificationListeners() {
    final Class<?> platformComponentClass = findClass("java.lang.management.PlatformComponent");
    final Method getMXBeans = findMethod(platformComponentClass, "getMXBeans", Class.class);
    if(platformComponentClass != null && getMXBeans != null) { 
      final Class<?> notificationEmitterSupportClass = findClass("sun.management.NotificationEmitterSupport");
      final Field listenerListField = findField(notificationEmitterSupportClass, "listenerList");

      final Class<?> listenerInfoClass = findClass("sun.management.NotificationEmitterSupport$ListenerInfo");
      final Field listenerField = findField(listenerInfoClass, "listener");
      final Field filterField = findField(listenerInfoClass, "filter");
      final Field handbackField = findField(listenerInfoClass, "handback");

      final boolean canProcessNotificationEmitterSupport =
          listenerListField != null && listenerInfoClass != null && 
          listenerField != null && filterField != null && handbackField != null;

      if(! canProcessNotificationEmitterSupport)
        warn("Unable to unregister NotificationEmitterSupport listeners, because details could not be found using reflection");

      final Object[] platformComponents = platformComponentClass.getEnumConstants();
      if(platformComponents != null) {
        for(Object platformComponent : platformComponents) {
          List<PlatformManagedObject> mxBeans = null;
          try {
            mxBeans = (List<PlatformManagedObject>) getMXBeans.invoke(platformComponent, (Class<?>) null);
          }
          catch (IllegalAccessException ex) {
            error(ex);
          }
          catch (InvocationTargetException ex) {
            error(ex);
          }

          if(mxBeans != null) { // We were able to retrieve MXBeans for this PlatformComponent
            for(PlatformManagedObject mxBean : mxBeans) {
              if(mxBean instanceof NotificationEmitter) { // The MXBean may have NotificationListeners
                if(canProcessNotificationEmitterSupport && notificationEmitterSupportClass.isAssignableFrom(mxBean.getClass())) {
                  final List<? /* NotificationEmitterSupport.ListenerInfo */> listenerList = getFieldValue(listenerListField, mxBean);
                  if(listenerList != null) {
                    for(Object listenerInfo : listenerList) { // Loop all listeners
                      final NotificationListener listener = getFieldValue(listenerField, listenerInfo);
                      final NotificationFilter filter = getFieldValue(filterField, listenerInfo);
                      final Objects handback = getFieldValue(handbackField, listenerInfo);

                      if(isLoadedInWebApplication(listener) || isLoadedInWebApplication(filter) || isLoadedInWebApplication(handback)) {
                        warn("Listener '" + listener + "' (or its filter or handback) of MXBean " + mxBean + 
                            " of PlatformComponent " + platformComponents + " was loaded in web application; removing");
                        // This is safe, as the implementation (as of this writing) works with a copy, not altering the original
                        try {
                          ((NotificationEmitter) mxBean).removeNotificationListener(listener, filter, handback);
                        }
                        catch (ListenerNotFoundException e) { // Should never happen
                          error(e);
                        }
                      }
                    }
                  }
                }
                else if(mxBean instanceof NotificationBroadcasterSupport) { // Unlikely case
                  unregisterNotificationListeners((NotificationBroadcasterSupport) mxBean);
                }
              }
            }
          }
        }
      }
    }
  }

  /** 
   * Unregister {@link NotificationListener}s from subclass of {@link NotificationBroadcasterSupport}, if listener,
   * filter or handback is loaded by the web app classloader.
   */
  protected void unregisterNotificationListeners(NotificationBroadcasterSupport mBean) {
    final Field listenerListField = findField(NotificationBroadcasterSupport.class, "listenerList");
    if(listenerListField != null) {
      final Class<?> listenerInfoClass = findClass("javax.management.NotificationBroadcasterSupport$ListenerInfo");

      final List<? /*javax.management.NotificationBroadcasterSupport.ListenerInfo*/> listenerList =
          getFieldValue(listenerListField, mBean);

      if(listenerList != null) {
        final Field listenerField = findField(listenerInfoClass, "listener");
        final Field filterField = findField(listenerInfoClass, "filter");
        final Field handbackField = findField(listenerInfoClass, "handback");
        for(Object listenerInfo : listenerList) {
          final NotificationListener listener = getFieldValue(listenerField, listenerInfo);
          final NotificationFilter filter = getFieldValue(filterField, listenerInfo);
          final Objects handback = getFieldValue(handbackField, listenerInfo);

          if(isLoadedInWebApplication(listener) || isLoadedInWebApplication(filter) || isLoadedInWebApplication(handback)) {
            warn("Listener '" + listener + "' (or its filter or handback) of MBean " + mBean + 
                " was loaded in web application; removing");
            // This is safe, as the implementation works with a copy, not altering the original
            try {
              mBean.removeNotificationListener(listener, filter, handback);
            }
            catch (ListenerNotFoundException e) { // Should never happen
              error(e);
            }
          }
        }
      }
    }
  }

  /** Find and deregister shutdown hooks. Will by default execute the hooks after removing them. */
  protected void deregisterShutdownHooks() {
    // We will not remove known shutdown hooks, since loading the owning class of the hook,
    // may register the hook if previously unregistered 
    Map<Thread, Thread> shutdownHooks = getStaticFieldValue("java.lang.ApplicationShutdownHooks", "hooks");
    if(shutdownHooks != null) { // Could be null during JVM shutdown, which we already avoid, but be extra precautious
      // Iterate copy to avoid ConcurrentModificationException
      for(Thread shutdownHook : new ArrayList<Thread>(shutdownHooks.keySet())) {
        if(isThreadInWebApplication(shutdownHook)) { // Planned to run in web app          
          removeShutdownHook(shutdownHook);
        }
      }
    }
  }

  /** Deregister shutdown hook and execute it immediately */
  @SuppressWarnings("deprecation")
  protected void removeShutdownHook(Thread shutdownHook) {
    final String displayString = "'" + shutdownHook + "' of type " + shutdownHook.getClass().getName();
    error("Removing shutdown hook: " + displayString);
    Runtime.getRuntime().removeShutdownHook(shutdownHook);

    if(executeShutdownHooks) { // Shutdown hooks should be executed
      
      info("Executing shutdown hook now: " + displayString);
      // Make sure it's from this web app instance
      shutdownHook.start(); // Run cleanup immediately
      
      if(shutdownHookWaitMs > 0) { // Wait for shutdown hook to finish
        try {
          shutdownHook.join(shutdownHookWaitMs); // Wait for thread to run
        }
        catch (InterruptedException e) {
          // Do nothing
        }
        if(shutdownHook.isAlive()) {
          warn(shutdownHook + "still running after " + shutdownHookWaitMs + " ms - Stopping!");
          shutdownHook.stop();
        }
      }
    }
  }

  /** Deregister custom property editors */
  protected void deregisterPropertyEditors() {
    final Field registryField = findField(PropertyEditorManager.class, "registry");
    if(registryField == null) {
      error("Internal registry of " + PropertyEditorManager.class.getName() + " not found");
    }
    else {
      try {
        synchronized (PropertyEditorManager.class) {
          final Map<Class<?>, Class<?>> registry = (Map<Class<?>, Class<?>>) registryField.get(null);
          if(registry != null) { // Initialized
            final Set<Class<?>> toRemove = new HashSet<Class<?>>();
            
            for(Map.Entry<Class<?>, Class<?>> entry : registry.entrySet()) {
              if(isLoadedByWebApplication(entry.getKey()) ||
                 isLoadedByWebApplication(entry.getValue())) { // More likely
                toRemove.add(entry.getKey());
              }
            }
            
            for(Class<?> clazz : toRemove) {
              warn("Property editor for type " + clazz +  " = " + registry.get(clazz) + " needs to be deregistered");
              PropertyEditorManager.registerEditor(clazz, null); // Deregister
            }
          }
        }
      }
      catch (Exception e) { // Such as IllegalAccessException
        error(e);
      }
    }
  }
  
  /** Deregister custom security providers */
  protected void deregisterSecurityProviders() {
    final Set<String> providersToRemove = new HashSet<String>();
    for(java.security.Provider provider : java.security.Security.getProviders()) {
      if(isLoadedInWebApplication(provider)) {
        providersToRemove.add(provider.getName());
      }
    }
    
    if(! providersToRemove.isEmpty()) {
      warn("Removing security providers loaded in web app: " + providersToRemove);
      for(String providerName : providersToRemove) {
        java.security.Security.removeProvider(providerName);
      }
    }
  }
  
  /**
   * Clear the default java.net.Authenticator (in case current one is loaded in web app). 
   * Includes special workaround for CXF issue https://issues.apache.org/jira/browse/CXF-5442
   */
  protected void clearDefaultAuthenticator() {
    final Authenticator defaultAuthenticator = getDefaultAuthenticator();
    if(defaultAuthenticator == null || // Can both mean not set, or error retrieving, so unset anyway to be safe 
        isLoadedInWebApplication(defaultAuthenticator)) {
      if(defaultAuthenticator != null) // Log warning only if a default was actually found
        warn("Unsetting default " + Authenticator.class.getName() + ": " + defaultAuthenticator);
      Authenticator.setDefault(null);
    }
    else {
      if("org.apache.cxf.transport.http.ReferencingAuthenticator".equals(defaultAuthenticator.getClass().getName())) {
        /*
         Needed since org.apache.cxf.transport.http.ReferencingAuthenticator is loaded by dummy classloader that
         references webapp classloader via AccessControlContext + ProtectionDomain.
         See https://issues.apache.org/jira/browse/CXF-5442
        */

        final Class<?> cxfAuthenticator = findClass("org.apache.cxf.transport.http.CXFAuthenticator");
        if(cxfAuthenticator != null && isLoadedByWebApplication(cxfAuthenticator)) { // CXF loaded in this application
          final Object cxfAuthenticator$instance = getStaticFieldValue(cxfAuthenticator, "instance");
          if(cxfAuthenticator$instance != null) { // CXF authenticator has been initialized in this webapp
            final Object authReference = getFieldValue(defaultAuthenticator, "auth");
            if(authReference instanceof Reference) { // WeakReference 
              final Reference<?> reference = (Reference<?>) authReference;
              final Object referencedAuth = reference.get();
              if(referencedAuth == cxfAuthenticator$instance) { // References CXFAuthenticator of this classloader 
                warn("Default " + Authenticator.class.getName() + " was " + defaultAuthenticator + " that referenced " +
                    cxfAuthenticator$instance + " loaded by webapp");

                // Let CXF unwrap in it's own way (in case there are multiple CXF webapps in the container)
                reference.clear(); // Remove the weak reference before calling check()
                try {
                  final Method check = defaultAuthenticator.getClass().getMethod("check");
                  check.setAccessible(true);
                  check.invoke(defaultAuthenticator);
                }
                catch (Exception e) {
                  error(e);
                }
              }
            }
          }
        }
      }
      
      removeWrappedAuthenticators(defaultAuthenticator);
      
      info("Default " + Authenticator.class.getName() + " not loaded in webapp: " + defaultAuthenticator);
    }
  }

  /** Find default {@link Authenticator} */
  protected Authenticator getDefaultAuthenticator() {
    // Normally Corresponds to getStaticFieldValue(Authenticator.class, "theAuthenticator");
    for(final Field f : Authenticator.class.getDeclaredFields()) {
      if (f.getType().equals(Authenticator.class)) { // Supposedly "theAuthenticator"
        try {
          f.setAccessible(true);
          return (Authenticator)f.get(null);
        } catch (Exception e) {
          error(e);
        }
      }
    }
    return null;
  }

  /**
   * Recursively removed wrapped {@link Authenticator} loaded in this webapp. May be needed in case there are multiple CXF
   * applications within the same container.
   */
  protected void removeWrappedAuthenticators(final Authenticator authenticator) {
    if(authenticator == null)
      return;

    try {
      Class<?> authenticatorClass = authenticator.getClass();
      do {
        for(final Field f : authenticator.getClass().getDeclaredFields()) {
          if(Authenticator.class.isAssignableFrom(f.getType())) {
            try {
              final boolean isStatic = Modifier.isStatic(f.getModifiers()); // In CXF case this should be false
              final Authenticator owner = isStatic ? null : authenticator;
              f.setAccessible(true);
              final Authenticator wrapped = (Authenticator)f.get(owner);
              if(isLoadedInWebApplication(wrapped)) {
                warn(Authenticator.class.getName() + ": " + wrapped + ", wrapped by " + authenticator + 
                    ", is loaded by webapp classloader");
                f.set(owner, null); // For added safety
              }
              else {
                removeWrappedAuthenticators(wrapped); // Recurse
              }
            } catch (Exception e) {
              error(e);
            }
          }
        }
        authenticatorClass = authenticatorClass.getSuperclass();
      } while (authenticatorClass != null && authenticatorClass != Object.class);
    }
    catch (Exception e) { // Should never happen
      error(e);
    }
  }
  
  /** If default {@link java.net.ProxySelector} is loaded by web application it needs to be unset */
  protected void clearDefaultProxySelector() {
    AccessController.doPrivileged(new PrivilegedAction<Void>() {
      @Override
      public Void run() {
        ProxySelector selector = ProxySelector.getDefault();
        if(isLoadedInWebApplication(selector)) {
          ProxySelector.setDefault(null);
          warn("Removing default java.net.ProxySelector: " + selector);
        }
        return null;
      }
    });
  }

  /** This method is heavily inspired by org.apache.catalina.loader.WebappClassLoader.clearReferencesRmiTargets() */
  protected void deregisterRmiTargets() {
    try {
      final Class<?> objectTableClass = findClass("sun.rmi.transport.ObjectTable");
      if(objectTableClass != null) {
        clearRmiTargetsMap((Map<?, ?>) getStaticFieldValue(objectTableClass, "objTable"));
        clearRmiTargetsMap((Map<?, ?>) getStaticFieldValue(objectTableClass, "implTable"));
      }
    }
    catch (Exception ex) {
      error(ex);
    }
  }
  
  /** Iterate RMI Targets Map and remove entries loaded by web app classloader */
  protected void clearRmiTargetsMap(Map<?, ?> rmiTargetsMap) {
    try {
      final Field cclField = findFieldOfClass("sun.rmi.transport.Target", "ccl");
      debug("Looping " + rmiTargetsMap.size() + " RMI Targets to find leaks");
      for(Iterator<?> iter = rmiTargetsMap.values().iterator(); iter.hasNext(); ) {
        Object target = iter.next(); // sun.rmi.transport.Target
        ClassLoader ccl = (ClassLoader) cclField.get(target);
        if(isWebAppClassLoaderOrChild(ccl)) {
          warn("Removing RMI Target: " + target);
          iter.remove();
        }
      }
    }
    catch (Exception ex) {
      error(ex);
    }
  }

  protected void clearThreadLocalsOfAllThreads() {
    final ThreadLocalProcessor clearingThreadLocalProcessor = getThreadLocalProcessor();
    for(Thread thread : getAllThreads()) {
      forEachThreadLocalInThread(thread, clearingThreadLocalProcessor);
    }
  }
  
  /** Get {@link ThreadLocalProcessor} to be used. Override to customize {@link ThreadLocal} processing. */
  protected ThreadLocalProcessor getThreadLocalProcessor() {
    return new ClearingThreadLocalProcessor();
  }

  /**
   * Partially inspired by org.apache.catalina.loader.WebappClassLoader.clearReferencesThreads()
   */
  @SuppressWarnings("deprecation")
  protected void stopThreads() {
    final Class<?> workerClass = findClass("java.util.concurrent.ThreadPoolExecutor$Worker");
    final Field oracleTarget = findField(Thread.class, "target"); // Sun/Oracle JRE
    final Field ibmRunnable = findField(Thread.class, "runnable"); // IBM JRE

    final boolean waitForThreads = threadWaitMs > 0;
    for(Thread thread : getAllThreads()) {
      final Runnable runnable = (oracleTarget != null) ? 
          (Runnable) getFieldValue(oracleTarget, thread) : // Sun/Oracle JRE  
          (Runnable) getFieldValue(ibmRunnable, thread);   // IBM JRE

      final boolean runnableLoadedInWebApplication = isLoadedInWebApplication(runnable);
      if(thread != Thread.currentThread() && // Ignore current thread
         (isThreadInWebApplication(thread) || runnableLoadedInWebApplication)) {

        if (thread.getClass().getName().startsWith(JURT_ASYNCHRONOUS_FINALIZER)) {
          // Note, the thread group of this thread may be "system" if it is triggered by the Garbage Collector
          // however if triggered by us in forceStartOpenOfficeJurtCleanup() it may depend on the application server
          if(stopThreads) {
            info("Found JURT thread " + thread.getName() + "; starting " + JURTKiller.class.getSimpleName());
            new JURTKiller(thread).start();
          }
          else
            warn("JURT thread " + thread.getName() + " is still running in web app");
        }
        else if(thread.getThreadGroup() != null && 
           ("system".equals(thread.getThreadGroup().getName()) ||  // System thread
            "RMI Runtime".equals(thread.getThreadGroup().getName()))) { // RMI thread (honestly, just copied from Tomcat)
          
          if("Keep-Alive-Timer".equals(thread.getName())) {
            thread.setContextClassLoader(getWebApplicationClassLoader().getParent());
            debug("Changed contextClassLoader of HTTP keep alive thread");
          }
        }
        else if(thread.isAlive()) { // Non-system, running in web app
        
          if("java.util.TimerThread".equals(thread.getClass().getName())) {
            if(stopTimerThreads) {
              warn("Stopping Timer thread '" + thread.getName() + "' running in classloader.");
              stopTimerThread(thread);
            }
            else {
              info("Timer thread is running in classloader, but will not be stopped");
            }
          }
          else {
            // If threads is running an java.util.concurrent.ThreadPoolExecutor.Worker try shutting down the executor
            if(workerClass != null && workerClass.isInstance(runnable)) {
              if(stopThreads) {
                warn("Shutting down " + ThreadPoolExecutor.class.getName() + " running within the classloader.");
                try {
                  // java.util.concurrent.ThreadPoolExecutor, introduced in Java 1.5
                  final Field workerExecutor = findField(workerClass, "this$0");
                  final ThreadPoolExecutor executor = getFieldValue(workerExecutor, runnable);
                  executor.shutdownNow();
                }
                catch (Exception ex) {
                  error(ex);
                }
              }
              else 
                info(ThreadPoolExecutor.class.getName() + " running within the classloader will not be shut down.");
            }

            final String displayString = "'" + thread + "' of type " + thread.getClass().getName();

            if(! isLoadedInWebApplication(thread) && ! runnableLoadedInWebApplication) { // Not loaded in web app - just running there
              // This would for example be the case with org.apache.tomcat.util.threads.TaskThread
              if(waitForThreads) {
                warn("Thread " + displayString + " running in web app; waiting " + threadWaitMs);
                waitForThread(thread, threadWaitMs);
              }
              
              if(thread.isAlive() && isWebAppClassLoaderOrChild(thread.getContextClassLoader())) {
                warn("Thread " + displayString + (waitForThreads ? " still" : "") + 
                    " running in web app; changing context classloader to system classloader");
                thread.setContextClassLoader(ClassLoader.getSystemClassLoader());
              }
            }
            else if(stopThreads) { // Loaded by web app
              final String waitString = waitForThreads ? "after " + threadWaitMs + " ms " : "";
              warn("Stopping Thread " + displayString + " running in web app " + waitString);

              waitForThread(thread, threadWaitMs);

              // Normally threads should not be stopped (method is deprecated), since it may cause an inconsistent state.
              // In this case however, the alternative is a classloader leak, which may or may not be considered worse.
              if(thread.isAlive())
                thread.stop();
            }
            else {
              warn("Thread " + displayString + " is still running in web app");
            }
              
          }
        }
      }
    }
  }

  protected void stopTimerThread(Thread thread) {
    // Seems it is not possible to access Timer of TimerThread, so we need to mimic Timer.cancel()
    /** 
    try {
      Timer timer = (Timer) findField(thread.getClass(), "this$0").get(thread); // This does not work!
      warn("Cancelling Timer " + timer + " / TimeThread '" + thread + "'");
      timer.cancel();
    }
    catch (IllegalAccessException iaex) {
      error(iaex);
    }
    */

    try {
      final Field newTasksMayBeScheduled = findField(thread.getClass(), "newTasksMayBeScheduled");
      final Object queue = findField(thread.getClass(), "queue").get(thread); // java.lang.TaskQueue
      final Method clear = findMethod(queue.getClass(), "clear");
      
      // Do what java.util.Timer.cancel() does
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (queue) {
        newTasksMayBeScheduled.set(thread, Boolean.FALSE);
        clear.invoke(queue);
        queue.notify(); // "In case queue was already empty."
      }
      
      // We shouldn't need to join() here, thread will finish soon enough
    }
    catch (Exception ex) {
      error(ex);
    }
  }
  
  /**
   * Make the provided Thread stop sleep(), wait() or join() and then give it the provided no of milliseconds to finish
   * executing. 
   * @param thread The thread to wake up and wait for
   * @param waitMs The no of milliseconds to wait. If <= 0 this method does nothing.
   */
  protected void waitForThread(Thread thread, long waitMs) {
    if(waitMs > 0) {
      try {
        thread.interrupt(); // Make Thread stop waiting in sleep(), wait() or join()
      }
      catch (SecurityException e) {
        error(e);
      }

      try {
        thread.join(waitMs); // Wait for thread to run
      }
      catch (InterruptedException e) {
        // Do nothing
      }
    }
  }

  /** Destroy any ThreadGroups that are loaded by the application classloader */
  public void destroyThreadGroups() {
    try {
      ThreadGroup systemThreadGroup = Thread.currentThread().getThreadGroup();
      while(systemThreadGroup.getParent() != null) {
        systemThreadGroup = systemThreadGroup.getParent();
      }
      // systemThreadGroup should now be the topmost ThreadGroup, "system"

      int enumeratedGroups;
      ThreadGroup[] allThreadGroups;
      int noOfGroups = systemThreadGroup.activeGroupCount(); // Estimate no of groups
      do {
        noOfGroups += 10; // Make room for 10 extra
        allThreadGroups = new ThreadGroup[noOfGroups];
        enumeratedGroups = systemThreadGroup.enumerate(allThreadGroups);
      } while(enumeratedGroups >= noOfGroups); // If there was not room for all groups, try again
      
      for(ThreadGroup threadGroup : allThreadGroups) {
        if(isLoadedInWebApplication(threadGroup) && ! threadGroup.isDestroyed()) {
          warn("ThreadGroup '" + threadGroup + "' was loaded inside application, needs to be destroyed");
          
          int noOfThreads = threadGroup.activeCount();
          if(noOfThreads > 0) {
            warn("There seems to be " + noOfThreads + " running in ThreadGroup '" + threadGroup + "'; interrupting");
            try {
              threadGroup.interrupt();
            }
            catch (Exception e) {
              error(e);
            }
          }

          try {
            threadGroup.destroy();
            info("ThreadGroup '" + threadGroup + "' successfully destroyed");
          }
          catch (Exception e) {
            error(e);
          }
        }
      }
    }
    catch (Exception ex) {
      error(ex);
    }
  }
  
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
  // Fix specific leaks
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /** Clean the cache of BeanELResolver */
  protected void clearBeanELResolverCache() {
    final Class<?> beanElResolverClass = findClass("javax.el.BeanELResolver");
    if(beanElResolverClass != null) {
      boolean cleared = false;
      try {
        final Method purgeBeanClasses = beanElResolverClass.getDeclaredMethod("purgeBeanClasses", ClassLoader.class);
        purgeBeanClasses.setAccessible(true);
        purgeBeanClasses.invoke(beanElResolverClass.newInstance(), getWebApplicationClassLoader());
        cleared = true;
      }
      catch (NoSuchMethodException e) {
        // Version of javax.el probably > 2.2; no real need to clear
      }
      catch (Exception e) {
        error(e);
      }
      
      if(! cleared) {
        // Fallback, if purgeBeanClasses() could not be called
        final Field propertiesField = findField(beanElResolverClass, "properties");
        if(propertiesField != null) {
          try {
            final Map<?, ?> properties = (Map<?, ?>) propertiesField.get(null);
            properties.clear();
          }
          catch (Exception e) {
            error(e);
          }
        }
      }
    }
  }
  
  public void fixBeanValidationApiLeak() {
    Class<?> offendingClass = findClass("javax.validation.Validation$DefaultValidationProviderResolver");
    if(offendingClass != null) { // Class is present on class path
      Field offendingField = findField(offendingClass, "providersPerClassloader");
      if(offendingField != null) {
        final Object providersPerClassloader = getStaticFieldValue(offendingField);
        if(providersPerClassloader instanceof Map) { // Map<ClassLoader, List<ValidationProvider<?>>> in offending code
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
          synchronized (providersPerClassloader) {
            // Fix the leak!
            ((Map<?, ?>)providersPerClassloader).remove(getWebApplicationClassLoader());
          }
        }
      }
    }
    
  }
  
  /** 
   * Workaround for leak caused by Mojarra JSF implementation if included in the container.
   * See <a href="http://java.net/jira/browse/JAVASERVERFACES-2746">JAVASERVERFACES-2746</a>
   */
  protected void fixJsfLeak() {
    /*
     Note that since a WeakHashMap is used, it is not the map key that is the problem. However the value is a
     Map with java.beans.PropertyDescriptor as value, and java.beans.PropertyDescriptor has a Hashtable in which
     a class is put with "type" as key. This class may have been loaded by the web application.

     One example case is the class org.primefaces.component.menubutton.MenuButton that points to a Map with a key 
     "model" whose PropertyDescriptor.table has key "type" with the class org.primefaces.model.MenuModel as its value.

     For performance reasons however, we'll only look at the top level key and remove any that has been loaded by the
     web application.     
     */
    
    Object o = getStaticFieldValue("javax.faces.component.UIComponentBase", "descriptors"); // Non-static as of JSF 2.2.5
    if(o instanceof WeakHashMap) {
      WeakHashMap<?, ?> descriptors = (WeakHashMap<?, ?>) o;
      final Set<Class<?>> toRemove = new HashSet<Class<?>>();
      for(Object key : descriptors.keySet()) {
        if(key instanceof Class && isLoadedByWebApplication((Class<?>)key)) {
          // For performance reasons, remove all classes loaded in web application
          toRemove.add((Class<?>) key);
          
          // This would be more correct, but presumably slower
          /*
          Map<String, PropertyDescriptor> m = (Map<String, PropertyDescriptor>) descriptors.get(key);
          for(Map.Entry<String,PropertyDescriptor> entry : m.entrySet()) {
            Object type = entry.getValue().getValue("type"); // Key constant javax.el.ELResolver.TYPE
            if(type instanceof Class && isLoadedByWebApplication((Class)type)) {
              toRemove.add((Class) key); 
            }
          }
          */
        }
      }
      
      if(! toRemove.isEmpty()) {
        info("Removing " + toRemove.size() + " classes from Mojarra descriptors cache");
        for(Class<?> clazz : toRemove) {
          descriptors.remove(clazz);
        }
      }
    }
  }
  
  /** Shutdown GeoTools cleaner thread as of http://jira.codehaus.org/browse/GEOT-2742 */
  protected void fixGeoToolsLeak() {
    final Class<?> weakCollectionCleanerClass = findClass("org.geotools.util.WeakCollectionCleaner");
    if(weakCollectionCleanerClass != null) {
      try {
        weakCollectionCleanerClass.getMethod("exit").invoke(null);
      }
      catch (Exception ex) {
        error(ex);
      }
    }
  }

  /** Clear IntrospectionUtils caches of Tomcat and Apache Commons Modeler */
  protected void clearIntrospectionUtilsCache() {
    // Tomcat
    final Class<?> tomcatIntrospectionUtils = findClass("org.apache.tomcat.util.IntrospectionUtils");
    if(tomcatIntrospectionUtils != null) {
      try {
        tomcatIntrospectionUtils.getMethod("clear").invoke(null);
      }
      catch (Exception ex) {
        if(! mayBeJBoss) // JBoss includes this class, but no cache and no clear() method
          error(ex);
      }
    }

    // Apache Commons Modeler
    final Class<?> modelIntrospectionUtils = findClass("org.apache.commons.modeler.util.IntrospectionUtils");
    if(modelIntrospectionUtils != null && ! isWebAppClassLoaderOrChild(modelIntrospectionUtils.getClassLoader())) { // Loaded outside web app
      try {
        modelIntrospectionUtils.getMethod("clear").invoke(null);
      }
      catch (Exception ex) {
        warn("org.apache.commons.modeler.util.IntrospectionUtils needs to be cleared but there was an error, " +
            "consider upgrading Apache Commons Modeler");
        error(ex);
      }
    }
  }
  
  /** 
   * The bug detailed at https://issues.apache.org/ooo/show_bug.cgi?id=122517 is quite tricky. This is a try to 
   * avoid the issues by force starting the threads and it's job queue.
   */
  protected void forceStartOpenOfficeJurtCleanup() {
    if(stopThreads) {
      if(isLoadedByWebApplication(findClass(JURT_ASYNCHRONOUS_FINALIZER))) {
        /* 
          The com.sun.star.lib.util.AsynchronousFinalizer class was found and loaded, which means that in case the
          static block that starts the daemon thread had not been started yet, it has been started now.
          
          Now let's force Garbage Collection, with the hopes of having the finalize()ers that put Jobs on the
          AsynchronousFinalizer queue be executed. Then just leave it, and handle the rest in {@link #stopThreads}.
          */
        info("OpenOffice JURT AsynchronousFinalizer thread started - forcing garbage collection to invoke finalizers");
        gc();
      }
    }
    else {
      // Check for class existence without loading class and thus executing static block
      if(getWebApplicationClassLoader().getResource("com/sun/star/lib/util/AsynchronousFinalizer.class") != null) {
        warn("OpenOffice JURT AsynchronousFinalizer thread will not be stopped if started, as stopThreads is false");
        /* 
         By forcing Garbage Collection, we'll hopefully start the thread now, in case it would have been started by
         GC later, so that at least it will appear in the logs. 
         */
        gc();
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Utility methods
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  protected ClassLoader getWebApplicationClassLoader() {
    return ClassLoaderLeakPreventor.class.getClassLoader();
    // Alternative return Thread.currentThread().getContextClassLoader();
  }
  
  /** Test if provided object is loaded with web application classloader */
  protected boolean isLoadedInWebApplication(Object o) {
    return (o instanceof Class) && isLoadedByWebApplication((Class<?>)o) || // Object is a java.lang.Class instance 
        o != null && isLoadedByWebApplication(o.getClass());
  }

  /** Test if provided class is loaded with web application classloader */
  protected boolean isLoadedByWebApplication(Class<?> clazz) {
    return clazz != null && isWebAppClassLoaderOrChild(clazz.getClassLoader());
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

  protected boolean isThreadInWebApplication(Thread thread) {
    return isLoadedInWebApplication(thread) || // Custom Thread class in web app
       isWebAppClassLoaderOrChild(thread.getContextClassLoader()); // Running in web application
  }
  
  protected <E> E getStaticFieldValue(Class<?> clazz, String fieldName) {
    Field staticField = findField(clazz, fieldName);
    return (staticField != null) ? (E) getStaticFieldValue(staticField) : null;
  }

  protected <E> E getStaticFieldValue(String className, String fieldName) {
    return (E) getStaticFieldValue(className, fieldName, false);
  }
  
  protected <E> E getStaticFieldValue(String className, String fieldName, boolean trySystemCL) {
    Field staticField = findFieldOfClass(className, fieldName, trySystemCL);
    return (staticField != null) ? (E) getStaticFieldValue(staticField) : null;
  }
  
  protected Field findFieldOfClass(String className, String fieldName) {
    return findFieldOfClass(className, fieldName, false);
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
    try {
      return Class.forName(className);
    }
//    catch (NoClassDefFoundError e) {
//      // Silently ignore
//      return null;
//    }
    catch (ClassNotFoundException e) {
      if (trySystemCL) {
        try {
          return Class.forName(className, true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e1) {
          // Silently ignore
          return null;
        }
      }
      // Silently ignore
      return null;
    }
    catch (Exception ex) { // Example SecurityException
      warn(ex);
      return null;
    }
  }
  
  protected Field findField(Class<?> clazz, String fieldName) {
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
  
  protected <T> T getStaticFieldValue(Field field) {
    try {
      if(! Modifier.isStatic(field.getModifiers())) {
        warn(field.toString() + " is not static");
        return null;
      }
      
      return (T) field.get(null);
    }
    catch (Exception ex) {
      warn(ex);
      // Silently ignore
      return null;
    }
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
  
  protected Method findMethod(Class<?> clazz, String methodName, Class... parameterTypes) {
    if(clazz == null)
      return null;

    try {
      final Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
      method.setAccessible(true);
      return method;
    }
    catch (NoSuchMethodException ex) {
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

  /** Get a Collection with all Threads. 
   * This method is heavily inspired by org.apache.catalina.loader.WebappClassLoader.getThreads() */
  protected Collection<Thread> getAllThreads() {
    // This is some orders of magnitude slower...
    // return Thread.getAllStackTraces().keySet();
    
    // Find root ThreadGroup
    ThreadGroup tg = Thread.currentThread().getThreadGroup();
    while(tg.getParent() != null)
      tg = tg.getParent();
    
    // Note that ThreadGroup.enumerate() silently ignores all threads that does not fit into array
    int guessThreadCount = tg.activeCount() + 50;
    Thread[] threads = new Thread[guessThreadCount];
    int actualThreadCount = tg.enumerate(threads);
    while(actualThreadCount == guessThreadCount) { // Map was filled, there may be more
      guessThreadCount *= 2;
      threads = new Thread[guessThreadCount];
      actualThreadCount = tg.enumerate(threads);
    }
    
    // Filter out nulls
    final List<Thread> output = new ArrayList<Thread>();
    for(Thread t : threads) {
      if(t != null) {
        output.add(t);
      }
    }
    return output;
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
    catch (/*IllegalAccess*/Exception ex) {
      error(ex);
    }
  }

  protected void processThreadLocalMap(Thread thread, ThreadLocalProcessor threadLocalProcessor, Object threadLocalMap) throws IllegalAccessException {
    if(threadLocalMap != null && java_lang_ThreadLocal$ThreadLocalMap_table != null) {
      Field resin_suspendState = null;
      Field resin_isSuspended = null;
      final Object[] threadLocalMapTable = (Object[]) java_lang_ThreadLocal$ThreadLocalMap_table.get(threadLocalMap); // java.lang.ThreadLocal.ThreadLocalMap.Entry[]
      for(Object entry : threadLocalMapTable) {
        if(entry != null) {
          // Key is kept in WeakReference
          Reference<?> reference = (Reference<?>) entry;
          final ThreadLocal<?> threadLocal = (ThreadLocal<?>) reference.get();

          if(java_lang_ThreadLocal$ThreadLocalMap$Entry_value == null) {
            java_lang_ThreadLocal$ThreadLocalMap$Entry_value = findField(entry.getClass(), "value");
          }
          
          final Object value = java_lang_ThreadLocal$ThreadLocalMap$Entry_value.get(entry);
          
          // Workaround for http://bugs.caucho.com/view.php?id=5647
          if(value != null && CAUCHO_TRANSACTION_IMPL.equals(value.getClass().getName())) { // Resin transaction
            if(resin_suspendState == null && resin_isSuspended == null) { // First thread with Resin transaction, look up fields
              resin_suspendState = findField(value.getClass(), "_suspendState");
              resin_isSuspended = findField(value.getClass(), "_isSuspended");
            }

            if(resin_suspendState != null && resin_isSuspended != null) { // Both fields exist (as per version 4.0.37)
              if(getFieldValue(resin_suspendState, value) != null) { // There is a suspended state that may cause leaks
                // In theory a new transaction can be started and suspended between where we read and write the state,
                // and flag, therefore we suspend the thread meanwhile.
                try {
                  thread.suspend(); // Suspend the thread
                  if(getFieldValue(resin_suspendState, value) != null) { // Re-read suspend state when thread is suspended
                    final Object isSuspended = getFieldValue(resin_isSuspended, value);
                    if(! (isSuspended instanceof Boolean)) {
                      error(thread.toString() + " has " + CAUCHO_TRANSACTION_IMPL + " but _isSuspended is not boolean: " + isSuspended);
                    }
                    else if((Boolean)isSuspended) { // Is currently suspended - suspend state is correct
                      debug(thread.toString() + " has " + CAUCHO_TRANSACTION_IMPL + " that is suspended");
                    }
                    else { // Is not suspended, and thus should not have suspend state
                      resin_suspendState.set(value, null);
                      error(thread.toString() + " had " + CAUCHO_TRANSACTION_IMPL + " with unused _suspendState that was removed");
                    }
                  }
                }
                catch (Throwable t) { // Such as SecurityException
                  error(t);
                }
                finally {
                  thread.resume();
                }
              }
            }
          }

          threadLocalProcessor.process(thread, reference, threadLocal, value);
        }
      }
    }
  }

  protected interface ThreadLocalProcessor {
    void process(Thread thread, Reference<?> entry, ThreadLocal<?> threadLocal, Object value);
  }

  /** ThreadLocalProcessor that detects and warns about potential leaks */
  protected class WarningThreadLocalProcessor implements ThreadLocalProcessor {
    @Override
    public final void process(Thread thread, Reference<?> entry, ThreadLocal<?> threadLocal, Object value) {
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

        
        // Process the detected potential leak
        processLeak(thread, entry, threadLocal, value, message.toString());
      }
    }
    
    /**
     * After having detected potential ThreadLocal leak, this method is called. Default implementation only issues
     * a warning. Subclasses may override this method to perform further processing, such as clean up. 
     */
    protected void processLeak(Thread thread, Reference<?> entry, ThreadLocal<?> threadLocal, Object value, String message) {
      warn(message);
    } 
  }
  
  /** ThreadLocalProcessor that not only detects and warns about potential leaks, but also tries to clear them */
  protected class ClearingThreadLocalProcessor extends WarningThreadLocalProcessor {
    @Override
    protected void processLeak(Thread thread, Reference<?> entry, ThreadLocal<?> threadLocal, Object value, String message) {
      if(threadLocal != null && thread == Thread.currentThread()) { // If running for current thread and we have the ThreadLocal ...
        // ... remove properly
        info(message + " will be remove()d from " + thread);
        threadLocal.remove();
      }
      else { // We cannot remove entry properly, so just make it stale
        info(message + " will be made stale for later expunging from " + thread);
      }

      // It seems like remove() doesn't really do the job, so play it safe and remove references from entry either way
      // (Example problem org.infinispan.context.SingleKeyNonTxInvocationContext) 
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

  /**
   * Unlike <code>{@link System#gc()}</code> this method guarantees that garbage collection has been performed before
   * returning.
   */
  protected static void gc() {
    if (isDisableExplicitGCEnabled()) {
      System.err.println(ClassLoaderLeakPreventor.class.getSimpleName() + ": "
          + "Skipping GC call since -XX:+DisableExplicitGC is supplied as VM option.");
      return;
    }
    
    Object obj = new Object();
    WeakReference<Object> ref = new WeakReference<Object>(obj);
    //noinspection UnusedAssignment
    obj = null;
    while(ref.get() != null) {
      System.gc();
    }
  }
  
  /**
   * Check is "-XX:+DisableExplicitGC" enabled.
   *
   * @return true is "-XX:+DisableExplicitGC" is set als vm argument, false otherwise.
   */
  private static boolean isDisableExplicitGCEnabled() {
    RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
    List<String> aList = bean.getInputArguments();

    return aList.contains("-XX:+DisableExplicitGC");
  }  
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Log methods
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /*
   * Since logging frameworks are part of the problem, we don't want to depend on any of them here.
   * Feel free however to subclass or fork and use a log framework, in case you think you know what you're doing.
   */
  
  protected String getLogPrefix() {
    return ClassLoaderLeakPreventor.class.getSimpleName() + ": ";
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
  
  /** 
   * Inner class with the sole task of killing JURT finalizer thread after it is done processing jobs. 
   * We need to postpone the stopping of this thread, since more Jobs may in theory be add()ed when this web application
   * instance is closing down and being garbage collected.
   * See https://issues.apache.org/ooo/show_bug.cgi?id=122517
   */
  protected class JURTKiller extends Thread {
    
    private final Thread jurtThread;
    
    private final List<?> jurtQueue;

    public JURTKiller(Thread jurtThread) {
      super("JURTKiller");
      this.jurtThread = jurtThread;
      jurtQueue = getStaticFieldValue(JURT_ASYNCHRONOUS_FINALIZER, "queue");
    }

    @Override
    public void run() {
      if(jurtQueue == null || jurtThread == null) {
        error(getName() + ": No queue or thread!?");
        return;
      }
      if(! jurtThread.isAlive()) {
        warn(getName() + ": " + jurtThread.getName() + " is already dead?");
      }
      
      boolean queueIsEmpty = false;
      while(! queueIsEmpty) {
        try {
          debug(getName() + " goes to sleep for " + THREAD_WAIT_MS_DEFAULT + " ms");
          Thread.sleep(THREAD_WAIT_MS_DEFAULT);
        }
        catch (InterruptedException e) {
          // Do nothing
        }

        if(State.RUNNABLE != jurtThread.getState()) { // Unless thread is currently executing a Job
          debug(getName() + " about to force Garbage Collection");
          gc(); // Force garbage collection, which may put new items on queue

          synchronized (jurtQueue) {
            queueIsEmpty = jurtQueue.isEmpty();
            debug(getName() + ": JURT queue is empty? " + queueIsEmpty);
          }
        }
        else 
          debug(getName() + ": JURT thread " + jurtThread.getName() + " is executing Job");
      }
      
      info(getName() + " about to kill " + jurtThread);
      if(jurtThread.isAlive())
        jurtThread.stop();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods and classes for Jetty, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=423255 
  
  /** Are we running in Jetty with JMX enabled? */
  protected boolean isJettyWithJMX() {
    final ClassLoader classLoader = getWebApplicationClassLoader();
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

    /** List of objects that may be wrapped in MBean by Jetty. Should be allowed to contain null. */
    private List<Object> objectsWrappedWithMBean;

    /** The org.eclipse.jetty.jmx.MBeanContainer instance */
    private Object beanContainer;

    /** org.eclipse.jetty.jmx.MBeanContainer.findBean() */
    private Method findBeanMethod;

    /** org.eclipse.jetty.jmx.MBeanContainer.removeBean() */
    private Method removeBeanMethod;

    public JettyJMXRemover(ClassLoader classLoader) throws Exception {
      // First we need access to the MBeanContainer to access the beans
      // WebAppContext webappContext = (WebAppContext)servletContext;
      final Object webappContext = Class.forName("org.eclipse.jetty.webapp.WebAppClassLoader")
          .getMethod("getContext").invoke(classLoader);
      if(webappContext == null)
        return;
      
      // Server server = (Server)webappContext.getServer();
      final Class<?> webAppContextClass = Class.forName("org.eclipse.jetty.webapp.WebAppContext");
      final Object server = webAppContextClass.getMethod("getServer").invoke(webappContext);
      if(server == null)
        return;

      // MBeanContainer beanContainer = (MBeanContainer)server.getBean(MBeanContainer.class);
      final Class<?> mBeanContainerClass = Class.forName("org.eclipse.jetty.jmx.MBeanContainer");
      beanContainer = Class.forName("org.eclipse.jetty.server.Server")
          .getMethod("getBean", Class.class).invoke(server, mBeanContainerClass);
	  
      // Now we store all objects that belong to the web application and that will be wrapped by MBeans in a list
      if (beanContainer != null) {
        findBeanMethod = mBeanContainerClass.getMethod("findBean", ObjectName.class);
        removeBeanMethod = mBeanContainerClass.getMethod("removeBean", Object.class);

        objectsWrappedWithMBean = new ArrayList<Object>();
        // SessionHandler sessionHandler = webappContext.getSessionHandler();
        final Object sessionHandler = webAppContextClass.getMethod("getSessionHandler").invoke(webappContext);
        if(sessionHandler != null) {
          objectsWrappedWithMBean.add(sessionHandler);
  
          // SessionManager sessionManager = sessionHandler.getSessionManager();
          final Object sessionManager = Class.forName("org.eclipse.jetty.server.session.SessionHandler")
              .getMethod("getSessionManager").invoke(sessionHandler);
          if(sessionManager != null) {
            objectsWrappedWithMBean.add(sessionManager);

            // SessionIdManager sessionIdManager = sessionManager.getSessionIdManager();
            final Object sessionIdManager = Class.forName("org.eclipse.jetty.server.SessionManager")
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
  
          final Class<?> servletHandlerClass = Class.forName("org.eclipse.jetty.servlet.ServletHandler");
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
			        warn("Jetty MBean '" + objectName + "' is a suspect in causing memory leaks; unregistering");
			        removeBeanMethod.invoke(beanContainer, bean); // Remove it via the MBeanContainer
			        return true;
            }
		      }
  		  }
        catch (Exception ex)  {
			    error(ex);
		    }
		    return false;
	    }
    }
  }  
}
