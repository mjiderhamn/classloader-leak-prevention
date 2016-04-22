package se.jiderhamn.classloader.leak.prevention;

/**
 * Interface for cleanup actions that should be performed as part of the preparations to make a {@link ClassLoader} available
 * for garbage collection.
 * @author Mattias Jiderhamn
 */
public interface ClassLoaderPreMortemCleanUp {
  
  /** 
   * Perform cleanup actions needed to make provided {@link ClassLoaderLeakPreventor#classLoader} 
   * ready for garbage collection.
   */
  void cleanUp(ClassLoaderLeakPreventor preventor);
  
}