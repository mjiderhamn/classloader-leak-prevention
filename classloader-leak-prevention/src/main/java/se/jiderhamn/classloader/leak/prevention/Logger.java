package se.jiderhamn.classloader.leak.prevention;

/**
 * Interface for logging, with similarities to common logging frameworks. If you want to plug in the leak preventor
 * into an existing architecture (such as an application server), you may want to use a custom implementation of this 
 * interface.
 * 
 * If the {@link ClassLoaderLeakPreventorFactory} is beeing reused, the {@link Logger} implementation may need to be 
 * thread safe.
 * 
 * @author Mattias Jiderhamn
 */
public interface Logger {

  /** Log debug level message */
  void debug(String msg);

  /** Log info level message */
  void info(String msg);

  /** Log a warning message */
  void warn(String msg);

  /** Log a {@link Throwable} as a warning message */
  void warn(Throwable t);

  /** Log an error message */
  void error(String msg);
  
  /** Log a {@link Throwable} as an error message */
  void error(Throwable t);
}
