package se.jiderhamn.classloader.leak.prevention;

/**
 * Default implementation of {@link Logger} interface, that uses {@link System#out} and {@link System#err}.
 * This is because log frameworks may themselves cause leaks, so we do not want to depend on them.
 * 
 * To "turn off" a log level, override the corresponding method(s) with an empty implementation.
 * @author Mattias Jiderhamn
 */
public class LoggerImpl implements Logger {
  
  /** Get prefix to use when logging to {@link System#out}/{@link System#err} */
  protected String getLogPrefix() {
    return ClassLoaderLeakPreventor.class.getSimpleName() + ": ";
  }
  
  @Override
  public void debug(String msg) {
    System.out.println(getLogPrefix() + msg);
  } 

  @Override
  public void info(String s) {
    System.out.println(getLogPrefix() + s);
  } 

  @Override
  public void warn(String s) {
    System.err.println(getLogPrefix() + s);
  } 

  @Override
  public void warn(Throwable t) {
    t.printStackTrace(System.err);
  } 

  @Override
  public void error(String s) {
    System.err.println(getLogPrefix() + s);
  } 

  @Override
  public void error(Throwable t) {
    t.printStackTrace(System.err);
  }
  
}