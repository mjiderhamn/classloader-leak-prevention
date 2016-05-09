package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Method;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;
import se.jiderhamn.classloader.leak.prevention.MustBeAfter;

/**
 * Destroy any {@link ThreadGroup}s that are loaded by the protected classloader
 * @author Mattias Jiderhamn
 */
public class ThreadGroupCleanUp implements ClassLoaderPreMortemCleanUp, MustBeAfter {

  @Override
  public Class[] mustBeBeforeMe() {
    return new Class[] {JavaServerFaces2746CleanUp.class};
  }

  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    boolean threadGroupDestroyed = false;
    try {
      ThreadGroup systemThreadGroup = Thread.currentThread().getThreadGroup();
      while(systemThreadGroup.getParent() != null) {
        systemThreadGroup = systemThreadGroup.getParent();
      }
      // systemThreadGroup should now be the topmost ThreadGroup, "system"

      int enumeratedGroups;
      ThreadGroup[] allThreadGroups;
      int noOfGroups = systemThreadGroup.activeGroupCount(); // Estimate no of groups
      do {
        noOfGroups += 10; // Make room for 10 extra
        allThreadGroups = new ThreadGroup[noOfGroups];
        enumeratedGroups = systemThreadGroup.enumerate(allThreadGroups);
      } while(enumeratedGroups >= noOfGroups); // If there was not room for all groups, try again
      
      for(ThreadGroup threadGroup : allThreadGroups) {
        if(preventor.isLoadedInClassLoader(threadGroup) && ! threadGroup.isDestroyed()) {
          preventor.warn("ThreadGroup '" + threadGroup + "' was loaded inside application, needs to be destroyed");
          
          int noOfThreads = threadGroup.activeCount();
          if(noOfThreads > 0) {
            preventor.warn("There seems to be " + noOfThreads + " running in ThreadGroup '" + threadGroup + "'; interrupting");
            try {
              threadGroup.interrupt();
            }
            catch (Exception e) {
              preventor.error(e);
            }
          }

          try {
            threadGroup.destroy();
            threadGroupDestroyed = true;
            preventor.info("ThreadGroup '" + threadGroup + "' successfully destroyed");
          }
          catch (Exception e) {
            preventor.error(e);
          }
        }
      }
    }
    catch (Exception ex) {
      preventor.error(ex);
    }

    try {
      final Object contexts = preventor.getStaticFieldValue("java.beans.ThreadGroupContext", "contexts");
      if(contexts != null) { // Since Java 1.7
        if(threadGroupDestroyed) // At least one ThreadGroup destroyed by this clean up 
          ClassLoaderLeakPreventor.gc(); // Force GC so WeakIdentityMap turns destroyed ThreadGroups into stale entries

        final Method removeStaleEntries = preventor.findMethod("java.beans.WeakIdentityMap", "removeStaleEntries");
        if(removeStaleEntries != null)
          removeStaleEntries.invoke(contexts);
      }
    }
    catch (Throwable t) { // IllegalAccessException, InvocationTargetException 
      preventor.warn(t);
    }
  }
}