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
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.URL;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

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
 * @author Mattias Jiderhamn, 2012
 */
public class ClassLoaderLeakPreventor implements javax.servlet.ServletContextListener {
  
  /** Default no of milliseconds to wait for threads to finish execution */
  public static final int THREAD_WAIT_MS_DEFAULT = 5 * 1000; // 5 seconds

  /** Default no of milliseconds to wait for shutdown hook to finish execution */
  public static final int SHUTDOWN_HOOK_WAIT_MS_DEFAULT = 10 * 1000; // 10 seconds
  
  ///////////
  // Settings
  
  
  /** Should threads tied to the web app classloader be forced to stop at application shutdown? */
  protected boolean stopThreads = true;
  
  /** Should Timer threads tied to the web app classloader be forced to stop at application shutdown? */
  protected boolean stopTimerThreads = true;
  
  /** Should shutdown hooks registered from the application be executed at application shutdown? */
  protected boolean executeShutdownHooks = true;

  /** 
   * No of milliseconds to wait for threads to finish execution, before stopping them.
   */
  protected int threadWaitMs = SHUTDOWN_HOOK_WAIT_MS_DEFAULT;

  /** 
   * No of milliseconds to wait for shutdown hooks to finish execution, before stopping them.
   * If set to -1 there will be no waiting at all, but Thread is allowed to run until finished.
   */
  protected int shutdownHookWaitMs = SHUTDOWN_HOOK_WAIT_MS_DEFAULT;

  /** Is it possible, that we are running under JBoss? */
  private boolean mayBeJBoss = false;

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

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Implement javax.servlet.ServletContextListener 
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    final ServletContext servletContext = servletContextEvent.getServletContext();
    stopThreads = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.stopThreads"));
    stopTimerThreads = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.stopTimerThreads"));
    executeShutdownHooks = ! "false".equals(servletContext.getInitParameter("ClassLoaderLeakPreventor.executeShutdownHooks"));
    threadWaitMs = getIntInitParameter(servletContext, "ClassLoaderLeakPreventor.threadWaitMs", THREAD_WAIT_MS_DEFAULT);
    shutdownHookWaitMs = getIntInitParameter(servletContext, "ClassLoaderLeakPreventor.shutdownHookWaitMs", SHUTDOWN_HOOK_WAIT_MS_DEFAULT);
    
    info("Settings for " + this.getClass().getName() + " (CL: 0x" +
         Integer.toHexString(System.identityHashCode(getWebApplicationClassLoader())) + "):");
    info("  stopThreads = " + stopThreads);
    info("  stopTimerThreads = " + stopTimerThreads);
    info("  executeShutdownHooks = " + executeShutdownHooks);
    info("  threadWaitMs = " + threadWaitMs + " ms");
    info("  shutdownHookWaitMs = " + shutdownHookWaitMs + " ms");
    
    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

    try {
      // If package org.jboss is found, we may be running under JBoss
      mayBeJBoss = (contextClassLoader.getResource("org/jboss") != null);
    }
    catch(Exception ex) {
      // Do nothing
    }
    

    info("Initializing context by loading some known offenders with system classloader");
    
    // This part is heavily inspired by Tomcats JreMemoryLeakPreventionListener  
    // See http://svn.apache.org/viewvc/tomcat/trunk/java/org/apache/catalina/core/JreMemoryLeakPreventionListener.java?view=markup
    try {
      // Switch to system classloader in before we load/call some JRE stuff that will cause 
      // the current classloader to be available for garbage collection
      Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
      
      java.awt.Toolkit.getDefaultToolkit(); // Will start a Thread
      
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
      catch (Exception ex) { // Example: ParserConfigurationException
        error(ex);
      }
      
      try {
        Class.forName("javax.xml.bind.DatatypeConverterImpl"); // Since JDK 1.6. May throw java.lang.Error
      }
      catch (ClassNotFoundException e) {
        // Do nothing
      }
      

      try {
        Class.forName("javax.security.auth.login.Configuration", true, ClassLoader.getSystemClassLoader());
      }
      catch (ClassNotFoundException e) {
        // Do nothing
      }

      // This probably does not affect classloaders, but prevents some problems with .jar files
      try {
        // URL needs to be well-formed, but does not need to exist
        new URL("jar:file://dummy.jar!/").openConnection().setDefaultUseCaches(false);
      }
      catch (Exception ex) {
        error(ex);
      }

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
        Class.forName("sun.java2d.Disposer"); // Will start a Thread
      }
      catch (ClassNotFoundException cnfex) {
        if(isSunJRE && ! mayBeJBoss) // JBoss blocks this package/class, so don't warn
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

      // Cause oracle.jdbc.driver.OracleTimeoutPollingThread to be started with contextClassLoader = system classloader  
      try {
        Class.forName("oracle.jdbc.driver.OracleTimeoutThreadPerVM");
      } catch (ClassNotFoundException e) {
        // Ignore silently - class not present
      }
    }
    finally {
      // Reset original classloader
      Thread.currentThread().setContextClassLoader(contextClassLoader);
    }
  }

  public void contextDestroyed(ServletContextEvent servletContextEvent) {

    final boolean jvmIsShuttingDown = isJvmShuttingDown();
    if(jvmIsShuttingDown) {
      info("JVM is shutting down - skip cleanup");
      return; // Don't do anything more
    }

    info(getClass().getName() + " shutting down context by removing known leaks (CL: 0x" + 
         Integer.toHexString(System.identityHashCode(getWebApplicationClassLoader())) + ")");
    
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

    ////////////////////
    // Fix generic leaks
    
    // Deregister JDBC drivers contained in web application
    deregisterJdbcDrivers();
    
    // Unregister MBeans loaded by the web application class loader
    unregisterMBeans();
    
    // Deregister shutdown hooks - execute them immediately
    deregisterShutdownHooks();
    
    deregisterPropertyEditors();

    deregisterSecurityProviders();
    
    clearDefaultAuthenticator();
    
    deregisterRmiTargets();
    
    clearThreadLocalsOfAllThreads();
    
    stopThreads();
    
    destroyThreadGroups();

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
    
    final Class logFactory = findClass("org.apache.commons.logging.LogFactory");
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
      MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
      final Set<ObjectName> allMBeanNames = mBeanServer.queryNames(new ObjectName("*:*"), null);
      for(ObjectName objectName : allMBeanNames) {
        try {
          final ClassLoader mBeanClassLoader = mBeanServer.getClassLoaderFor(objectName);
          if(isWebAppClassLoaderOrChild(mBeanClassLoader)) { // MBean loaded in web application
            warn("MBean '" + objectName + "' was loaded in web application; unregistering");
            mBeanServer.unregisterMBean(objectName);
          }
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

  /** Find and deregister shutdown hooks. Will by default execute the hooks after removing them. */
  protected void deregisterShutdownHooks() {
    // We will not remove known shutdown hooks, since loading the owning class of the hook,
    // may register the hook if previously unregistered 
    Map<Thread, Thread> shutdownHooks = (Map<Thread, Thread>) getStaticFieldValue("java.lang.ApplicationShutdownHooks", "hooks");
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
          final Map<Class<?>, Class<?>> registry = (Map) registryField.get(null);
          if(registry != null) { // Initialized
            final Set<Class> toRemove = new HashSet<Class>();
            
            for(Map.Entry<Class<?>, Class<?>> entry : registry.entrySet()) {
              if(isLoadedByWebApplication(entry.getKey()) ||
                 isLoadedByWebApplication(entry.getValue())) { // More likely
                toRemove.add(entry.getKey());
              }
            }
            
            for(Class clazz : toRemove) {
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
  
  /** Clear the default java.net.Authenticator (in case current one is loaded in web app) */
  protected void clearDefaultAuthenticator() {
    final Authenticator defaultAuthenticator = getStaticFieldValue(Authenticator.class, "theAuthenticator");
    if(defaultAuthenticator == null || // Can both mean not set, or error retrieving, so unset anyway to be safe 
       isLoadedInWebApplication(defaultAuthenticator)) {
      Authenticator.setDefault(null);
    }
  }

  /** This method is heavily inspired by org.apache.catalina.loader.WebappClassLoader.clearReferencesRmiTargets() */
  protected void deregisterRmiTargets() {
    try {
      final Class objectTableClass = findClass("sun.rmi.transport.ObjectTable");
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
    final ThreadLocalProcessor clearingThreadLocalProcessor = new ClearingThreadLocalProcessor();
    for(Thread thread : getAllThreads()) {
      forEachThreadLocalInThread(thread, clearingThreadLocalProcessor);
    }
  }

  /**
   * Partially inspired by org.apache.catalina.loader.WebappClassLoader.clearReferencesThreads()
   */
  @SuppressWarnings("deprecation")
  protected void stopThreads() {
    final Class<?> workerClass = findClass("java.util.concurrent.ThreadPoolExecutor$Worker");
    final Field oracleTarget = findField(Thread.class, "target"); // Sun/Oracle JRE
    final Field ibmRunnable = findField(Thread.class, "runnable"); // IBM JRE

    for(Thread thread : getAllThreads()) {
      @SuppressWarnings("RedundantCast") 
      final Runnable runnable = (oracleTarget != null) ? 
          (Runnable) getFieldValue(oracleTarget, thread) : // Sun/Oracle JRE  
          (Runnable) getFieldValue(ibmRunnable, thread);   // IBM JRE
      if(thread != Thread.currentThread() && // Ignore current thread
         (isThreadInWebApplication(thread) || isLoadedInWebApplication(runnable))) {

        if(thread.getThreadGroup() != null && 
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
              warn("Stopping Timer thread running in classloader.");
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
            
            if(stopThreads) {
              final String waitString = (threadWaitMs > 0) ? "after " + threadWaitMs + " ms " : "";
              warn("Stopping Thread " + displayString + " running in web app " + waitString);

              if(threadWaitMs > 0) {
                try {
                  thread.join(threadWaitMs); // Wait for thread to run
                }
                catch (InterruptedException e) {
                  // Do nothing
                }
              }

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
      final Method clear = queue.getClass().getDeclaredMethod("clear");
      clear.setAccessible(true);

      // Do what java.util.Timer.cancel() does
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (queue) {
        newTasksMayBeScheduled.set(thread, false);
        clear.invoke(queue);
        queue.notify(); // "In case queue was already empty."
      }
      
      // We shouldn't need to join() here, thread will finish soon enough
    }
    catch (Exception ex) {
      error(ex);
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
    final Class beanElResolverClass = findClass("javax.el.BeanELResolver");
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
            final Map properties = (Map) propertiesField.get(null);
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
    Class offendingClass = findClass("javax.validation.Validation$DefaultValidationProviderResolver");
    if(offendingClass != null) { // Class is present on class path
      Field offendingField = findField(offendingClass, "providersPerClassloader");
      if(offendingField != null) {
        final Object providersPerClassloader = getStaticFieldValue(offendingField);
        if(providersPerClassloader instanceof Map) { // Map<ClassLoader, List<ValidationProvider<?>>> in offending code
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
          synchronized (providersPerClassloader) {
            // Fix the leak!
            ((Map)providersPerClassloader).remove(getWebApplicationClassLoader());
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
    
    Object o = getStaticFieldValue("javax.faces.component.UIComponentBase", "descriptors");
    if(o instanceof WeakHashMap) {
      WeakHashMap descriptors = (WeakHashMap) o;
      final Set<Class> toRemove = new HashSet<Class>();
      for(Object key : descriptors.keySet()) {
        if(key instanceof Class && isLoadedByWebApplication((Class)key)) {
          // For performance reasons, remove all classes loaded in web application
          toRemove.add((Class) key);
          
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
        for(Class clazz : toRemove) {
          descriptors.remove(clazz);
        }
      }
    }
  }
  
  /** Shutdown GeoTools cleaner thread as of http://jira.codehaus.org/browse/GEOT-2742 */
  protected void fixGeoToolsLeak() {
    final Class weakCollectionCleanerClass = findClass("org.geotools.util.WeakCollectionCleaner");
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
    final Class tomcatIntrospectionUtils = findClass("org.apache.tomcat.util.IntrospectionUtils");
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
    final Class modelIntrospectionUtils = findClass("org.apache.commons.modeler.util.IntrospectionUtils");
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

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Utility methods
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  protected ClassLoader getWebApplicationClassLoader() {
    return ClassLoaderLeakPreventor.class.getClassLoader();
    // Alternative return Thread.currentThread().getContextClassLoader();
  }
  
  /** Test if provided object is loaded with web application classloader */
  protected boolean isLoadedInWebApplication(Object o) {
    return o != null && isLoadedByWebApplication(o.getClass());
  }

  /** Test if provided class is loaded with web application classloader */
  protected boolean isLoadedByWebApplication(Class clazz) {
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
  
  protected <E> E getStaticFieldValue(Class clazz, String fieldName) {
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
    Class clazz = findClass(className, trySystemCL);
    if(clazz != null) {
      return findField(clazz, fieldName);
    }
    else
      return null;
  }
  
  protected Class findClass(String className) {
    return findClass(className, false);
  }
  
  protected Class findClass(String className, boolean trySystemCL) {
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
  
  protected Field findField(Class clazz, String fieldName) {
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
        info("  Will be made stale for later expunging");
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
    return ClassLoaderLeakPreventor.class.getSimpleName() + ": ";
  }
  
  protected void debug(String s) {
    System.out.println(getLogPrefix() + s);
  } 

  protected void info(String s) {
    System.out.println(getLogPrefix() + s);
  } 

  protected void warn(String s) {
    System.err.println(getLogPrefix() + s);
  } 

  protected void warn(Throwable t) {
    t.printStackTrace(System.err);
  } 

  protected void error(String s) {
    System.err.println(getLogPrefix() + s);
  } 

  protected void error(Throwable t) {
    t.printStackTrace(System.err);
  } 
}