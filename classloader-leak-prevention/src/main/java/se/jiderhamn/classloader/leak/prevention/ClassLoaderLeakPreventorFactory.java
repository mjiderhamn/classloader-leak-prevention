package se.jiderhamn.classloader.leak.prevention;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

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
  private final ClassLoader leakSafeClassLoader;
  
  /** 
   * The {@link Logger} that will be passed on to the different {@link PreClassLoaderInitiator}s and 
   * {@link ClassLoaderPreMortemCleanUp}s 
   */
  private Logger logger = new LoggerImpl();

  /** 
   * Map from name to {@link PreClassLoaderInitiator}s with all the actions to invoke in the 
   * {@link #leakSafeClassLoader}. Maintains insertion order. Thread safe.
   */
  private final Map<String, PreClassLoaderInitiator> preInitiators =
      synchronizedMap(new LinkedHashMap<String, PreClassLoaderInitiator>());

  /** 
   * Map from name to {@link ClassLoaderPreMortemCleanUp}s with all the actions to invoke to make a 
   * {@link ClassLoader} ready for Garbage Collection. Maintains insertion order. Thread safe.
   */
  private final Map<String, ClassLoaderPreMortemCleanUp> cleanUps = 
      synchronizedMap(new LinkedHashMap<String, ClassLoaderPreMortemCleanUp>());

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Constructors
  
  /** 
   * Create new {@link ClassLoaderLeakPreventorFactory} with {@link ClassLoader#getSystemClassLoader()} as the 
   * {@link #leakSafeClassLoader} 
   */
  public ClassLoaderLeakPreventorFactory() {
    this(ClassLoader.getSystemClassLoader());
  }

  /** 
   * Create new {@link ClassLoaderLeakPreventorFactory} with supplied {@link ClassLoader} as the 
   * {@link #leakSafeClassLoader} 
   */
  public ClassLoaderLeakPreventorFactory(ClassLoader leakSafeClassLoader) {
    this.leakSafeClassLoader = leakSafeClassLoader;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Factory methods
  
  /** 
   * Create new {@link ClassLoaderLeakPreventor} used to prevent the provided {@link Thread#contextClassLoader} of the
   * {@link Thread#currentThread()} from leaking.
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
    this.preInitiators.put(preClassLoaderInitiator.getClass().getName(), preClassLoaderInitiator);
  }

  /** Add a new named {@link PreClassLoaderInitiator} */
  public void addPreInitiator(String name, PreClassLoaderInitiator preClassLoaderInitiator) {
    this.preInitiators.put(name, preClassLoaderInitiator);
  }
  
  /** Add a new {@link ClassLoaderPreMortemCleanUp}, using the class name as name */
  public void addCleanUp(ClassLoaderPreMortemCleanUp classLoaderPreMortemCleanUp) {
    this.cleanUps.put(classLoaderPreMortemCleanUp.getClass().getName(), classLoaderPreMortemCleanUp);
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
}