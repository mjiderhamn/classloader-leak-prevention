package se.jiderhamn.classloader.leak.prevention;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import se.jiderhamn.classloader.leak.prevention.cleanup.*;
import se.jiderhamn.classloader.leak.prevention.preinit.*;

import static java.util.Collections.synchronizedMap;

/**
 * Orchestrator class responsible for invoking the preventative and cleanup measures.
 * Contains the configuration and can be reused for multiple classloaders (assume it is not itself loaded by the
 * classloader which we want to avoid leaking). In that case, the {@link #logger} may need to be thread safe.
 * @author Mattias Jiderhamn
 */
public class ClassLoaderLeakPreventorFactory {
  
  /** 
   * {@link ClassLoader} to be used when invoking the {@link PreClassLoaderInitiator}s.
   * Defaults to {@link ClassLoader#getSystemClassLoader()}, but could be any other framework or 
   * app server classloader.
   */
  protected final ClassLoader leakSafeClassLoader;
  
  /** 
   * The {@link Logger} that will be passed on to the different {@link PreClassLoaderInitiator}s and 
   * {@link ClassLoaderPreMortemCleanUp}s 
   */
  protected Logger logger = new JULLogger();

  /** 
   * Map from name to {@link PreClassLoaderInitiator}s with all the actions to invoke in the 
   * {@link #leakSafeClassLoader}. Maintains insertion order. Thread safe.
   */
  protected final Map<String, PreClassLoaderInitiator> preInitiators =
      synchronizedMap(new LinkedHashMap<String, PreClassLoaderInitiator>());

  /** 
   * Map from name to {@link ClassLoaderPreMortemCleanUp}s with all the actions to invoke to make a 
   * {@link ClassLoader} ready for Garbage Collection. Maintains insertion order. Thread safe.
   */
  protected final Map<String, ClassLoaderPreMortemCleanUp> cleanUps = 
      synchronizedMap(new LinkedHashMap<String, ClassLoaderPreMortemCleanUp>());

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Constructors
  
  /** 
   * Create new {@link ClassLoaderLeakPreventorFactory} with {@link ClassLoader#getSystemClassLoader()} as the 
   * {@link #leakSafeClassLoader} and default {@link PreClassLoaderInitiator}s and {@link ClassLoaderPreMortemCleanUp}s. 
   */
  public ClassLoaderLeakPreventorFactory() {
    this(ClassLoader.getSystemClassLoader());
  }

  /** 
   * Create new {@link ClassLoaderLeakPreventorFactory} with supplied {@link ClassLoader} as the 
   * {@link #leakSafeClassLoader} and default {@link PreClassLoaderInitiator}s and {@link ClassLoaderPreMortemCleanUp}s.  
   */
  public ClassLoaderLeakPreventorFactory(ClassLoader leakSafeClassLoader) {
    this.leakSafeClassLoader = leakSafeClassLoader;
    configureDefaults();
  }
  
  /** Configure default {@link PreClassLoaderInitiator}s and {@link ClassLoaderPreMortemCleanUp}s */
  public void configureDefaults() {
    // The pre-initiators part is heavily inspired by Tomcats JreMemoryLeakPreventionListener  
    // See http://svn.apache.org/viewvc/tomcat/trunk/java/org/apache/catalina/core/JreMemoryLeakPreventionListener.java?view=markup
    this.addPreInitiator(new AwtToolkitInitiator());
    // initSecurityProviders()
    this.addPreInitiator(new JdbcDriversInitiator());
    this.addPreInitiator(new SunAwtAppContextInitiator());
    this.addPreInitiator(new SecurityPolicyInitiator());
    this.addPreInitiator(new SecurityProvidersInitiator());
    this.addPreInitiator(new DocumentBuilderFactoryInitiator());
    this.addPreInitiator(new ReplaceDOMNormalizerSerializerAbortException());
    this.addPreInitiator(new DatatypeConverterImplInitiator());
    this.addPreInitiator(new JavaxSecurityLoginConfigurationInitiator());
    this.addPreInitiator(new JarUrlConnectionInitiator());
    // Load Sun specific classes that may cause leaks
    this.addPreInitiator(new LdapPoolManagerInitiator());
    this.addPreInitiator(new Java2dDisposerInitiator());
    this.addPreInitiator(new Java2dRenderQueueInitiator());
    this.addPreInitiator(new SunGCInitiator());
    this.addPreInitiator(new OracleJdbcThreadInitiator());

    this.addCleanUp(new BeanIntrospectorCleanUp());
    
    // Apache Commons Pool can leave unfinished threads. Anything specific we can do?
    this.addCleanUp(new BeanELResolverCleanUp());
    this.addCleanUp(new BeanValidationCleanUp());
    this.addCleanUp(new JacksonCleanUp());
    this.addCleanUp(new JavaServerFaces2746CleanUp());
    this.addCleanUp(new GeoToolsCleanUp());
    // Can we do anything about Google Guice ?
    // Can we do anything about Groovy http://jira.codehaus.org/browse/GROOVY-4154 ?
    this.addCleanUp(new IntrospectionUtilsCleanUp());
    // Can we do anything about Logback http://jira.qos.ch/browse/LBCORE-205 ?
    this.addCleanUp(new IIOServiceProviderCleanUp()); // clear ImageIO registry
    this.addCleanUp(new MoxyCleanUp());
    this.addCleanUp(new ThreadGroupContextCleanUp());
    this.addCleanUp(new X509TrustManagerImplUnparseableExtensionCleanUp());
    this.addCleanUp(new SAAJEnvelopeFactoryParserPoolCleanUp());
    
    ////////////////////
    // Fix generic leaks
    this.addCleanUp(new DriverManagerCleanUp());
    
    this.addCleanUp(new DefaultAuthenticatorCleanUp());

    this.addCleanUp(new MBeanCleanUp());
    this.addCleanUp(new MXBeanNotificationListenersCleanUp());
    
    this.addCleanUp(new ShutdownHookCleanUp());
    this.addCleanUp(new PropertyEditorCleanUp());
    this.addCleanUp(new SecurityProviderCleanUp());
    this.addCleanUp(new JceSecurityCleanUp()); // (Probably best to do after deregistering the providers)
    this.addCleanUp(new ProxySelectorCleanUp());
    this.addCleanUp(new RmiTargetsCleanUp());
    this.addCleanUp(new StopThreadsCleanUp());
    this.addCleanUp(new ThreadGroupCleanUp());
    this.addCleanUp(new ThreadLocalCleanUp()); // This must be done after threads have been stopped, or new ThreadLocals may be added by those threads
    this.addCleanUp(new KeepAliveTimerCacheCleanUp());
    this.addCleanUp(new ResourceBundleCleanUp());
    this.addCleanUp(new JDK8151486CleanUp());
    this.addCleanUp(new JavaUtilLoggingLevelCleanUp()); // Do this last, in case other shutdown procedures want to log something.
    this.addCleanUp(new ApacheCommonsLoggingCleanUp()); // Do this last, in case other shutdown procedures want to log something.
    
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Factory methods
  
  /** 
   * Create new {@link ClassLoaderLeakPreventor} used to prevent the provided {@link Thread#contextClassLoader} of the
   * {@link Thread#currentThread()} from leaking.
   * 
   * Please be aware that {@link ClassLoaderLeakPreventor}s created by the same factory share the same 
   * {@link PreClassLoaderInitiator} and {@link ClassLoaderPreMortemCleanUp} instances, in case their config is changed. 
   */
  public ClassLoaderLeakPreventor newLeakPreventor() {
    return newLeakPreventor(Thread.currentThread().getContextClassLoader());
  }
  
  /** Create new {@link ClassLoaderLeakPreventor} used to prevent the provided {@link ClassLoader} from leaking */
  public ClassLoaderLeakPreventor newLeakPreventor(ClassLoader classLoader) {
    return new ClassLoaderLeakPreventor(leakSafeClassLoader, classLoader, logger,
        new ArrayList<PreClassLoaderInitiator>(preInitiators.values()), // Snapshot
        new ArrayList<ClassLoaderPreMortemCleanUp>(cleanUps.values())); // Snapshot
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for configuring the factory 
  
  /** Set logger */
  public void setLogger(Logger logger) {
    this.logger = logger;
  }
  
  /** Add a new {@link PreClassLoaderInitiator}, using the class name as name */
  public void addPreInitiator(PreClassLoaderInitiator preClassLoaderInitiator) {
    addConsideringOrder(this.preInitiators, preClassLoaderInitiator);
  }

  /** Add a new {@link ClassLoaderPreMortemCleanUp}, using the class name as name */
  public void addCleanUp(ClassLoaderPreMortemCleanUp classLoaderPreMortemCleanUp) {
    addConsideringOrder(this.cleanUps, classLoaderPreMortemCleanUp);
  }
  
  /** Add new {@link I} entry to {@code map}, taking {@link MustBeAfter} into account */
  private <I> void addConsideringOrder(Map<String, I> map, I newEntry) {
    for(Map.Entry<String, I> entry : map.entrySet()) {
      if(entry.getValue() instanceof MustBeAfter<?>) {
        final Class<? extends ClassLoaderPreMortemCleanUp>[] existingMustBeAfter = 
            ((MustBeAfter<ClassLoaderPreMortemCleanUp>)entry.getValue()).mustBeBeforeMe();
        for(Class<? extends ClassLoaderPreMortemCleanUp> clazz : existingMustBeAfter) {
          if(clazz.isAssignableFrom(newEntry.getClass())) { // Entry needs to be after new entry
            // TODO Resolve order automatically #51
            throw new IllegalStateException(clazz.getName() + " must be added after " + newEntry.getClass());
          }
        }
      }
    }
    
    map.put(newEntry.getClass().getName(), newEntry);
  }

  /** Add a new named {@link ClassLoaderPreMortemCleanUp} */
  public void addCleanUp(String name, ClassLoaderPreMortemCleanUp classLoaderPreMortemCleanUp) {
    this.cleanUps.put(name, classLoaderPreMortemCleanUp);
  }
  
  /** Remove all the currently configured {@link PreClassLoaderInitiator}s */
  public void clearPreInitiators() {
    this.cleanUps.clear();
  }

  /** Remove all the currently configured {@link ClassLoaderPreMortemCleanUp}s */
  public void clearCleanUps() {
    this.cleanUps.clear();
  }
  
  /** 
   * Get instance of {@link PreClassLoaderInitiator} for further configuring.
   * 
   * Please be aware that {@link ClassLoaderLeakPreventor}s created by the same factory share the same 
   * {@link PreClassLoaderInitiator} and {@link ClassLoaderPreMortemCleanUp} instances, in case their config is changed. 
   */
  public <C extends PreClassLoaderInitiator> C getPreInitiator(Class<C> clazz) {
    return (C) this.preInitiators.get(clazz.getName());
  }

  /** 
   * Get instance of {@link ClassLoaderPreMortemCleanUp} for further configuring.
   * 
   * Please be aware that {@link ClassLoaderLeakPreventor}s created by the same factory share the same 
   * {@link PreClassLoaderInitiator} and {@link ClassLoaderPreMortemCleanUp} instances, in case their config is changed. 
   */
  public <C extends ClassLoaderPreMortemCleanUp> C getCleanUp(Class<C> clazz) {
    return (C) this.cleanUps.get(clazz.getName());
  }

  /** Get instance of {@link PreClassLoaderInitiator} for further configuring */
  public <C extends PreClassLoaderInitiator> void removePreInitiator(Class<C> clazz) {
    this.preInitiators.remove(clazz.getName());
  }

  /** Get instance of {@link ClassLoaderPreMortemCleanUp} for further configuring */
  public <C extends ClassLoaderPreMortemCleanUp> void removeCleanUp(Class<C> clazz) {
    this.cleanUps.remove(clazz.getName());
  }
}