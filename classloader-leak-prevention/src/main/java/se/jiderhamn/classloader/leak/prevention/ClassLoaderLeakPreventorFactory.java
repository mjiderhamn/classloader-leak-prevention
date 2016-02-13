package se.jiderhamn.classloader.leak.prevention;

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

  /** 
   * The {@link Logger} that will be passed on to the different {@link PreClassLoaderInitiator}s and 
   * {@link ClassLoaderPreMortemCleanUp}s 
   */
  private Logger logger = new LoggerImpl();

  /** Set logger */
  public void setLogger(Logger logger) {
    this.logger = logger;
  }
  
  // TODO factory method for Thread.currentThread().getContextClassLoader();
  
  /** Create new {@link ClassLoaderLeakPreventor} used to prevent the provided {@link ClassLoader} from leaking */
  public ClassLoaderLeakPreventor newLeakPreventor(ClassLoader classLoader) {
    return new ClassLoaderLeakPreventor(leakSafeClassLoader, classLoader, logger);
  }

  
}