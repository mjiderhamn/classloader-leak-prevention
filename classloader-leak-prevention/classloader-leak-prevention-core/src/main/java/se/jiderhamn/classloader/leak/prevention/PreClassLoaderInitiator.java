package se.jiderhamn.classloader.leak.prevention;

/**
 * Interface for preventative actions that should be executed in the system (or other parent) classloader before they 
 * may be triggered within the classloader that is about to be launched, and thereby may trigger leaks.
 * @author Mattias Jiderhamn
 */
public interface PreClassLoaderInitiator {
  
  /** 
   * Perform action that needs to be done outside the leak susceptible classloader, i.e. in the system or other parent
   * classloader. Assume that the system or parent classloader is the {@link Thread#contextClassLoader} of the current 
   * thread when method is invoked.
   * Must NOT have modified {@link Thread#contextClassLoader} of the current thread when returning.
   */
  void doOutsideClassLoader(ClassLoaderLeakPreventor preventor);
  
}
