package se.jiderhamn.classloader.leak.prevention;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class helps prevent classloader leaks.
 * @author Mattias Jiderhamn
 */
@SuppressWarnings("WeakerAccess")
public class ClassLoaderLeakPreventor {

  /** Default no of milliseconds to wait for threads to finish execution */
  public static final int THREAD_WAIT_MS_DEFAULT = 5 * 1000; // 5 seconds
  
  private static final ProtectionDomain[] NO_DOMAINS = new ProtectionDomain[0];

  private static final AccessControlContext NO_DOMAINS_ACCESS_CONTROL_CONTEXT = new AccessControlContext(NO_DOMAINS);

  /** {@link ClassLoader#isAncestor(ClassLoader)} */
  private final Method java_lang_ClassLoader_isAncestor;
  
  /** {@link ClassLoader#isAncestorOf(ClassLoader)} of IBM JRE */
  private final Method java_lang_ClassLoader_isAncestorOf;
  
  private final Field java_security_AccessControlContext$combiner;
  
  private final Field java_security_AccessControlContext$parent;
  
  private final Field java_security_AccessControlContext$privilegedContext;

  /** 
   * {@link ClassLoader} to be used when invoking the {@link PreClassLoaderInitiator}s.
   * This will normally be the {@link ClassLoader#getSystemClassLoader()}, but could be any other framework or 
   * app server classloader. Normally, but not necessarily, a parent of {@link #classLoader}.
   */
  private final ClassLoader leakSafeClassLoader;
  
  /** The {@link ClassLoader} we want to avoid leaking */
  private final ClassLoader classLoader;
  
  private final Logger logger;
  
  private final Collection<PreClassLoaderInitiator> preClassLoaderInitiators;
  
  private final Collection<ClassLoaderPreMortemCleanUp> cleanUps;
  
  /** {@link DomainCombiner} that filters any {@link ProtectionDomain}s loaded by our classloader */
  private final DomainCombiner domainCombiner;

  public ClassLoaderLeakPreventor(ClassLoader leakSafeClassLoader, ClassLoader classLoader, Logger logger,
                           Collection<PreClassLoaderInitiator> preClassLoaderInitiators,
                           Collection<ClassLoaderPreMortemCleanUp> cleanUps) {
    this.leakSafeClassLoader = leakSafeClassLoader;
    this.classLoader = classLoader;
    this.logger = logger;
    this.preClassLoaderInitiators = preClassLoaderInitiators;
    this.cleanUps = cleanUps;

    final String javaVendor = System.getProperty("java.vendor");
    if(javaVendor != null && javaVendor.startsWith("IBM")) { // IBM
      java_lang_ClassLoader_isAncestor = null;
      java_lang_ClassLoader_isAncestorOf = findMethod(ClassLoader.class, "isAncestorOf", ClassLoader.class);
    }
    else { // Oracle
      java_lang_ClassLoader_isAncestor = findMethod(ClassLoader.class, "isAncestor", ClassLoader.class);
      java_lang_ClassLoader_isAncestorOf = null;
    }
    NestedProtectionDomainCombinerException.class.getName(); // Should be loaded before switching to leak safe classloader
    
    this.domainCombiner = createDomainCombiner();

    // Reflection inits
    java_security_AccessControlContext$combiner = findField(AccessControlContext.class, "combiner");
    java_security_AccessControlContext$parent = findField(AccessControlContext.class, "parent");
    java_security_AccessControlContext$privilegedContext = findField(AccessControlContext.class, "privilegedContext");

  }
  
  /** Invoke all the registered {@link PreClassLoaderInitiator}s in the {@link #leakSafeClassLoader} */
  public void runPreClassLoaderInitiators() {
    info("Initializing by loading some known offenders with leak safe classloader"); 
    
    doInLeakSafeClassLoader(new Runnable() {
      @Override
      public void run() {
        for(PreClassLoaderInitiator preClassLoaderInitiator : preClassLoaderInitiators) {
          preClassLoaderInitiator.doOutsideClassLoader(ClassLoaderLeakPreventor.this);
        }
      }
    });
  }
  
  /**
   * Perform action in the provided ClassLoader (normally system ClassLoader, that may retain references to the 
   * {@link Thread#contextClassLoader}. 
   * The motive for the custom {@link AccessControlContext} is to avoid spawned threads from inheriting all the 
   * {@link java.security.ProtectionDomain}s of the running code, since that may include the classloader we want to 
   * avoid leaking. This however means the {@link AccessControlContext} will have a {@link DomainCombiner} referencing the 
   * classloader, which will be taken care of in {@link #runCleanUps()}.
   */
   protected void doInLeakSafeClassLoader(final Runnable runnable) {
     final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
     
     try {
       Thread.currentThread().setContextClassLoader(leakSafeClassLoader);
 
       // Use doPrivileged() not to perform secured actions, but to avoid threads spawned inheriting the 
       // AccessControlContext of the current thread, since among the ProtectionDomains there will be one
       // (the top one) whose classloader is the web app classloader
       AccessController.doPrivileged(new PrivilegedAction<Object>() {
         @Override
         public Object run() {
           runnable.run();
           return null; // Nothing to return
         }
       }, createAccessControlContext());
     }
     finally {
       // Reset original classloader
       Thread.currentThread().setContextClassLoader(contextClassLoader);
     }
   }
   
   /** 
    * Create {@link AccessControlContext} that is used in {@link #doInLeakSafeClassLoader(Runnable)}.
    * The motive is to avoid spawned threads from inheriting all the {@link java.security.ProtectionDomain}s of the 
    * running code, since that will include the web app classloader.
    */
   public AccessControlContext createAccessControlContext() {
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
         logger.error("createAccessControlContext not granted and AccessControlContext could not be created via reflection");
         return AccessController.getContext();
       }
     }
   } 
   
   /** {@link DomainCombiner} that filters any {@link ProtectionDomain}s loaded by our classloader */
   private DomainCombiner createDomainCombiner() {
     return new DomainCombiner() {
       
       /** Flag to detected recursive calls */
       private final ThreadLocal<Boolean> isExecuting = new ThreadLocal<Boolean>();
       
       @Override
       public ProtectionDomain[] combine(ProtectionDomain[] currentDomains, ProtectionDomain[] assignedDomains) {
         if(assignedDomains != null && assignedDomains.length > 0) {
           logger.error("Unexpected assignedDomains - please report to developer of this library!");
         }
 
         if(isExecuting.get() == Boolean.TRUE)
           throw new NestedProtectionDomainCombinerException();
           
         try {
           isExecuting.set(Boolean.TRUE); // Throw NestedProtectionDomainCombinerException on nested calls

           // Keep all ProtectionDomain not involving the web app classloader 
           final List<ProtectionDomain> output = new ArrayList<ProtectionDomain>();
           for(ProtectionDomain protectionDomain : currentDomains) {
             if(protectionDomain.getClassLoader() == null ||
                 ! isClassLoaderOrChild(protectionDomain.getClassLoader())) {
               output.add(protectionDomain);
             }
           }
           return output.toArray(new ProtectionDomain[output.size()]);
         }
         finally {
           isExecuting.remove();
         }
       }
     };
   }  
  
  /** 
   * Recursively unset our custom {@link DomainCombiner} (loaded in the web app) from the {@link AccessControlContext} 
   * and any parents or privilegedContext thereof.
   */
  @Deprecated
  public void removeDomainCombiner(Thread thread, AccessControlContext accessControlContext) {
    removeDomainCombiner("thread " + thread, accessControlContext);
  }
  
  /** 
   * Recursively unset our custom {@link DomainCombiner} (loaded in the web app) from the {@link AccessControlContext} 
   * and any parents or privilegedContext thereof.
   */
  public void removeDomainCombiner(String owner, AccessControlContext accessControlContext) {
    if(accessControlContext != null && java_security_AccessControlContext$combiner != null) {
      if(getFieldValue(java_security_AccessControlContext$combiner, accessControlContext) == this.domainCombiner) {
        warn(AccessControlContext.class.getSimpleName() + " of " + owner + " used custom combiner - unsetting");
        try {
          java_security_AccessControlContext$combiner.set(accessControlContext, null);
        }
        catch (Exception e) {
          error(e);
        }
      }
      
      // Recurse
      if(java_security_AccessControlContext$parent != null) {
        removeDomainCombiner(owner, (AccessControlContext) getFieldValue(java_security_AccessControlContext$parent, accessControlContext));
      }
      if(java_security_AccessControlContext$privilegedContext != null) {
        removeDomainCombiner(owner, (AccessControlContext) getFieldValue(java_security_AccessControlContext$privilegedContext, accessControlContext));
      }
    }
  }
  
  
  /** Invoke all the registered {@link ClassLoaderPreMortemCleanUp}s */
  public void runCleanUps() {
    if(isJvmShuttingDown()) {
      info("JVM is shutting down - skip cleanup");
      // Don't do anything more
    }
    else {
      final Field inheritedAccessControlContext = this.findField(Thread.class, "inheritedAccessControlContext");
      if(inheritedAccessControlContext != null) {
        // Check if threads have been started in doInLeakSafeClassLoader() and need fixed ACC
        for(Thread thread : getAllThreads()) { // (We actually only need to do this for threads not running in web app, as per StopThreadsCleanUp) 
          final AccessControlContext accessControlContext = getFieldValue(inheritedAccessControlContext, thread);
          removeDomainCombiner("thread " + thread , accessControlContext);
        }
      }
      
      for(ClassLoaderPreMortemCleanUp cleanUp : cleanUps) {
        cleanUp.cleanUp(this);
      }
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Utility methods

  public ClassLoader getClassLoader() {
    return classLoader;
  }

  /**
   * Get {@link ClassLoader} to be used when invoking the {@link PreClassLoaderInitiator}s.
   * This will normally be the {@link ClassLoader#getSystemClassLoader()}, but could be any other framework or 
   * app server classloader. Normally, but not necessarily, a parent of {@link #classLoader}.
   */
  public ClassLoader getLeakSafeClassLoader() {
    return leakSafeClassLoader;
  }

  /** Test if provided object is loaded by {@link #classLoader} */
  public boolean isLoadedInClassLoader(Object o) {
    return (o instanceof Class) && isLoadedByClassLoader((Class<?>)o) || // Object is a java.lang.Class instance 
        o != null && isLoadedByClassLoader(o.getClass());
  }

  /** Test if provided class is loaded wby {@link #classLoader} */
  public boolean isLoadedByClassLoader(Class<?> clazz) {
    return clazz != null && isClassLoaderOrChild(clazz.getClassLoader());
  }

  /** Test if provided ClassLoader is the {@link #classLoader}, or a child thereof */
  public boolean isClassLoaderOrChild(ClassLoader cl) {
    if(cl == null) {
      return false;
    }
    else if(cl == classLoader) {
      return true;
    }
    else { // It could be a child of the webapp classloader
      if(java_lang_ClassLoader_isAncestor != null) { // Primarily use ClassLoader.isAncestor()
        try {
          return (Boolean) java_lang_ClassLoader_isAncestor.invoke(cl, classLoader);
        }
        catch (Exception e) {
          error(e);
        }
      }

      if(java_lang_ClassLoader_isAncestorOf != null) { // Secondarily use IBM ClassLoader.isAncestorOf()
        try {
          return (Boolean) java_lang_ClassLoader_isAncestorOf.invoke(classLoader, cl);
        }
        catch (Exception e) {
          error(e);
        }
      }

      // We were unable to use ClassLoader.isAncestor() or isAncestorOf()
      try {
        while(cl != null) {
          if(cl == classLoader)
            return true;

          cl = cl.getParent();
        }
      }
      catch (NestedProtectionDomainCombinerException e) {
        return false; // Since we needed permission to call getParent(), it is unlikely it is a descendant
      }
      return false;
    }
  }

  /**
   * Is the {@link Thread} ties do the protected classloader, either by being a custom {@link Thread} class, having a 
   * custom {@link ThreadGroup} or having the protected classloader as its {@link Thread#contextClassLoader}?
   */
  public boolean isThreadInClassLoader(Thread thread) {
    return isLoadedInClassLoader(thread) || // Custom Thread class in classloader
       isLoadedInClassLoader(thread.getThreadGroup()) || // Custom ThreadGroup class in classloader 
       isClassLoaderOrChild(thread.getContextClassLoader()); // Running in classloader
  }
  
  /**
   * Make the provided Thread stop sleep(), wait() or join() and then give it the provided no of milliseconds to finish
   * executing. 
   * @param thread The thread to wake up and wait for
   * @param waitMs The no of milliseconds to wait. If <= 0 this method does nothing.
   * @param interrupt Should {@link Thread#interrupt()} be called first, to make thread stop sleep(), wait() or join()?               
   */
  public void waitForThread(Thread thread, long waitMs, boolean interrupt) {
    if(waitMs > 0) {
      if(interrupt) {
        try {
          thread.interrupt(); // Make Thread stop waiting in sleep(), wait() or join()
        }
        catch (SecurityException e) {
          error(e);
        }
      }

      try {
        thread.join(waitMs); // Wait for thread to run
      }
      catch (InterruptedException e) {
        // Do nothing
      }
    }
  }
  
  /** Get current stack trace or provided thread as string. Returns {@code "unavailable"} if stack trace could not be acquired. */
  public String getStackTrace(Thread thread) {
    try {
      final StackTraceElement[] stackTrace = thread.getStackTrace();
      if(stackTrace.length == 0)
        return "Thread state: " + thread.getState();

      final StringBuilder output = new StringBuilder("Thread stack trace: ");
      for(StackTraceElement stackTraceElement : stackTrace) {
        // if(output.length() > 0) // Except first
          output.append("\n\tat ");
        output.append(stackTraceElement.toString());
      }
      return output.toString().trim(); // 
    }
    catch (Throwable t) { // SecurityException
      return "Thread details unavailable";
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  public <E> E getStaticFieldValue(Class<?> clazz, String fieldName) {
    Field staticField = findField(clazz, fieldName);
    return (staticField != null) ? (E) getStaticFieldValue(staticField) : null;
  }

  public <E> E getStaticFieldValue(String className, String fieldName) {
    return (E) getStaticFieldValue(className, fieldName, false);
  }
  
  public <E> E getStaticFieldValue(String className, String fieldName, boolean trySystemCL) {
    Field staticField = findFieldOfClass(className, fieldName, trySystemCL);
    return (staticField != null) ? (E) getStaticFieldValue(staticField) : null;
  }
  
  public Field findFieldOfClass(String className, String fieldName) {
    return findFieldOfClass(className, fieldName, false);
  }
  
  public Field findFieldOfClass(String className, String fieldName, boolean trySystemCL) {
    Class<?> clazz = findClass(className, trySystemCL);
    if(clazz != null) {
      return findField(clazz, fieldName);
    }
    else
      return null;
  }
  
  public Class<?> findClass(String className) {
    return findClass(className, false);
  }
  
  public Class<?> findClass(String className, boolean trySystemCL) {
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
  
  public Field findField(Class<?> clazz, String fieldName) {
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
  
  public <T> T getStaticFieldValue(Field field) {
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
  
  public <T> T getFieldValue(Object obj, String fieldName) {
    final Field field = findField(obj.getClass(), fieldName);
    return (T) getFieldValue(field, obj);
  }
  
  public <T> T getFieldValue(Field field, Object obj) {
    try {
      return (T) field.get(obj);
    }
    catch (Exception ex) {
      warn(ex);
      // Silently ignore
      return null;
    }
  }
  
  public void setFinalStaticField(Field field, Object newValue) {
    // Allow modification of final field 
    try {
      Field modifiersField = Field.class.getDeclaredField("modifiers");
      modifiersField.setAccessible(true);
      modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }
    catch (NoSuchFieldException e) {
      warn("Unable to get 'modifiers' field of java.lang.Field");
    }
    catch (IllegalAccessException e) {
      warn("Unable to set 'modifiers' field of java.lang.Field");
    }
    catch (Throwable t) {
      warn(t);
    }

    // Update the field
    try {
      field.set(null, newValue);
    }
    catch (Throwable e) {
      error("Error setting value of " + field + " to " + newValue);
    }
  }
  
  public Method findMethod(String className, String methodName, Class... parameterTypes) {
    Class<?> clazz = findClass(className);
    if(clazz != null) {
      return findMethod(clazz, methodName, parameterTypes);
    }
    else 
      return null;
  }
  
  public Method findMethod(Class<?> clazz, String methodName, Class... parameterTypes) {
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

  /** Get a Collection with all Threads. 
   * This method is heavily inspired by org.apache.catalina.loader.WebappClassLoader.getThreads() */
  public Collection<Thread> getAllThreads() {
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
   * Override this method if you want to customize how we determine if we're running in
   * JBoss WildFly (a.k.a JBoss AS).
   */
  public boolean isJBoss() {
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
   * Are we running in the Oracle/Sun Java Runtime Environment?
   * Override this method if you want to customize how we determine if this is a Oracle/Sun
   * Java Runtime Environment.
   */
  public boolean isOracleJRE() {
    String javaVendor = System.getProperty("java.vendor");
    
    return javaVendor.startsWith("Oracle") || javaVendor.startsWith("Sun");
  }

  /**
   * Unlike <code>{@link System#gc()}</code> this method guarantees that garbage collection has been performed before
   * returning.
   */
  public static void gc() {
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

  /** Is the JVM currently shutting down? */
  public boolean isJvmShuttingDown() {
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

  /**
   * Exception thrown when {@link DomainCombiner#combine(ProtectionDomain[], ProtectionDomain[])} is called recursively
   * during the execution of that same method.
   */
  private static class NestedProtectionDomainCombinerException extends RuntimeException {

  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Delegate methods for Logger


  public void debug(String msg) {
    logger.debug(msg);
  }

  public void warn(Throwable t) {
    logger.warn(t);
  }

  public void error(Throwable t) {
    logger.error(t);
  }

  public void warn(String msg) {
    logger.warn(msg);
  }

  public void error(String msg) {
    logger.error(msg);
  }

  public void info(String msg) {
    logger.info(msg);
  }
}