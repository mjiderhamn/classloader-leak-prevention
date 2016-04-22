package se.jiderhamn.classloader.leak.prevention.cleanup;

/**
 * Test cases for {@link ThreadGroupCleanUp}
 * @author Mattias Jiderhamn
 */
public class ThreadGroupCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<ThreadGroupCleanUp> {

  /** 
   * Having a custom ThreadGroup that is not destroyed will cause a leak
   */
  @Override
  protected void triggerLeak() throws Exception {
    new ThreadGroup("customThreadGroup") {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        System.out.println("Pretend to do something");
        super.uncaughtException(t, e);
      }
    };
    
  }
}