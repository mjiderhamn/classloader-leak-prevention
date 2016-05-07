package se.jiderhamn.classloader.leak.prevention;

import java.util.logging.Level;

/**
 * Implementation of {@link Logger} interface, that uses {@link java.util.logging}.
 * 
 * @author Mattias Jiderhamn
 */
public class JULLogger implements Logger {
  
  private static final java.util.logging.Logger LOG = 
      java.util.logging.Logger.getLogger(ClassLoaderLeakPreventor.class.getName());
  
  @Override
  public void debug(String msg) {
    LOG.config(msg);
  } 

  @Override
  public void info(String msg) {
    LOG.info(msg);
  } 

  @Override
  public void warn(String msg) {
    LOG.warning(msg);
  } 

  @Override
  public void warn(Throwable t) {
    LOG.log(Level.WARNING, t.getMessage(), t);
  } 

  @Override
  public void error(String msg) {
    LOG.severe(msg);
  } 

  @Override
  public void error(Throwable t) {
    LOG.log(Level.SEVERE, t.getMessage(), t);
  }
  
}