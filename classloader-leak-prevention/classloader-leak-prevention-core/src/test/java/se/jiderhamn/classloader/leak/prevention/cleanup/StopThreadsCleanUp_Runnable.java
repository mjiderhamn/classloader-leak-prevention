package se.jiderhamn.classloader.leak.prevention.cleanup;

/**
 * Test case for leaks caused by {@link Runnable} of thread being loaded by classloader. 
 *
 * @author Mattias Jiderhamn
 */
public class StopThreadsCleanUp_Runnable extends ClassLoaderPreMortemCleanUpTestBase<StopThreadsCleanUp> {

  @Override
  protected void triggerLeak() {
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(5000L);
        }
        catch (InterruptedException e) {
          System.out.println("Thread with custom Runnable was interrupted from sleeping. Going back to sleep.");
        }
      }
    });
    t.setContextClassLoader(ClassLoader.getSystemClassLoader());
    t.start();
  }

}
