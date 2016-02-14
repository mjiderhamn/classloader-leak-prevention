package se.jiderhamn.classloader.leak.prevention;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TODO Document
 * @author Mattias Jiderhamn
 */
public class ClassLoaderLeakPreventor {
  
  private static final ProtectionDomain[] NO_DOMAINS = new ProtectionDomain[0];

  private static final AccessControlContext NO_DOMAINS_ACCESS_CONTROL_CONTEXT = new AccessControlContext(NO_DOMAINS);
  
  /* TODO
  private final Field java_security_AccessControlContext$combiner;
  
  private final Field java_security_AccessControlContext$parent;
  
  private final Field java_security_AccessControlContext$privilegedContext;
  */

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

  ClassLoaderLeakPreventor(ClassLoader leakSafeClassLoader, ClassLoader classLoader, Logger logger,
                           Collection<PreClassLoaderInitiator> preClassLoaderInitiators,
                           Collection<ClassLoaderPreMortemCleanUp> cleanUps) {
    this.leakSafeClassLoader = leakSafeClassLoader;
    this.classLoader = classLoader;
    this.logger = logger;
    this.preClassLoaderInitiators = preClassLoaderInitiators;
    this.cleanUps = cleanUps;
    
    domainCombiner = createDomainCombiner();
  }
  
  /** Invoke all the registered {@link PreClassLoaderInitiator}s in the {@link #leakSafeClassLoader} */
  public void runPreClassLoaderInitiators() {
    doInLeakSafeClassLoader(new Runnable() {
      @Override
      public void run() {
        for(PreClassLoaderInitiator preClassLoaderInitiator : preClassLoaderInitiators) {
          preClassLoaderInitiator.doOutsideClassLoader(logger);
        }
      }
    });
  }
  
  /**
   * Perform action in the provided ClassLoader (normally system ClassLoader, that may retain references to the 
   * {@link Thread#contextClassLoader}. 
   * The method is package protected so that it can be called from test cases. TODO Still needed?
   * The motive for the custom {@link AccessControlContext} is to avoid spawned threads from inheriting all the 
   * {@link java.security.ProtectionDomain}s of the running code, since that may include the classloader we want to 
   * avoid leaking. This however means the {@link AccessControlContext} will have a {@link DomainCombiner} referencing the 
   * classloader, which will be taken care of in {@link #stopThreads()} TODO!!!.
   */
   public void doInLeakSafeClassLoader(final Runnable runnable) { // TODO Make non-public
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
   protected AccessControlContext createAccessControlContext() {
     final DomainCombiner domainCombiner = createDomainCombiner();
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
       @Override
       public ProtectionDomain[] combine(ProtectionDomain[] currentDomains, ProtectionDomain[] assignedDomains) {
         if(assignedDomains != null && assignedDomains.length > 0) {
           logger.error("Unexpected assignedDomains - please report to developer of this library!");
         }
 
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
     };
   }  
  
  /** 
   * Recursively unset our custom {@link DomainCombiner} (loaded in the web app) from the {@link AccessControlContext} 
   * and any parents or privilegedContext thereof.
   * TODO: Consider extracting to {@link ClassLoaderPreMortemCleanUp}
   */
  /* TODO
  public void removeDomainCombiner(Thread thread, AccessControlContext accessControlContext) {
    if(accessControlContext != null) {
      if(getFieldValue(java_security_AccessControlContext$combiner, accessControlContext) == this.domainCombiner) {
        warn(AccessControlContext.class.getSimpleName() + " of thread " + thread + " used custom combiner - unsetting");
        try {
          java_security_AccessControlContext$combiner.set(accessControlContext, null);
        }
        catch (Exception e) {
          error(e);
        }
      }
      
      // Recurse
      if(java_security_AccessControlContext$parent != null) {
        removeDomainCombiner(thread, (AccessControlContext) getFieldValue(java_security_AccessControlContext$parent, accessControlContext));
      }
      if(java_security_AccessControlContext$privilegedContext != null) {
        removeDomainCombiner(thread, (AccessControlContext) getFieldValue(java_security_AccessControlContext$privilegedContext, accessControlContext));
      }
    }
  }
  */
  
  
  /** Invoke all the registered {@link ClassLoaderPreMortemCleanUp}s */
  public void runCleanUps() {
    for(ClassLoaderPreMortemCleanUp cleanUp : cleanUps) {
      cleanUp.cleanUp(this);
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Utility methods

  public ClassLoader getClassLoader() {
    return classLoader;
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
    while(cl != null) {
      if(cl == classLoader)
        return true;
      
      cl = cl.getParent();
    }

    return false;
  }

  public boolean isThreadInClassLoader(Thread thread) {
    return isLoadedInClassLoader(thread) || // Custom Thread class in classloader
       isClassLoaderOrChild(thread.getContextClassLoader()); // Running in classloader
  }
  
}