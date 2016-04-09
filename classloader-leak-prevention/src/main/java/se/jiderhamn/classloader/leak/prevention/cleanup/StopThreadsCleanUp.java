package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

import static se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor.THREAD_WAIT_MS_DEFAULT;

/**
 * Check if there are threads running within the protected {@link ClassLoader}, or otherwise referencing it,
 * and either warn or stop those threads depending on settings.
 * @author Mattias Jiderhamn
 */
@SuppressWarnings("WeakerAccess")
public class StopThreadsCleanUp implements ClassLoaderPreMortemCleanUp {

  protected static final String JURT_ASYNCHRONOUS_FINALIZER = "com.sun.star.lib.util.AsynchronousFinalizer";
  
  private final boolean stopThreads;
  
  /** 
   * No of milliseconds to wait for threads to finish execution, before stopping them.
   */
  protected int threadWaitMs = THREAD_WAIT_MS_DEFAULT;
  
  /** Should Timer threads tied to the web app classloader be forced to stop at application shutdown? TODO Set https://github.com/mjiderhamn/classloader-leak-prevention/issues/44 */
  protected final boolean stopTimerThreads;
                   
  public StopThreadsCleanUp(boolean stopThreads, boolean stopTimerThreads) {
    this.stopThreads = stopThreads;
    this.stopTimerThreads = stopTimerThreads;
  }

  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    // Force the execution of the cleanup code for JURT; see https://issues.apache.org/ooo/show_bug.cgi?id=122517
    forceStartOpenOfficeJurtCleanup(preventor); // (Do this before stopThreads())
    
    ////////////////////
    // Fix generic leaks
    
    stopThreads(preventor);
  }
  
  /** 
   * The bug detailed at https://issues.apache.org/ooo/show_bug.cgi?id=122517 is quite tricky. This is a try to 
   * avoid the issues by force starting the threads and it's job queue.
   */
  protected void forceStartOpenOfficeJurtCleanup(ClassLoaderLeakPreventor preventor) {
    if(stopThreads) {
      if(preventor.isLoadedByClassLoader(preventor.findClass(JURT_ASYNCHRONOUS_FINALIZER))) {
        /* 
          The com.sun.star.lib.util.AsynchronousFinalizer class was found and loaded, which means that in case the
          static block that starts the daemon thread had not been started yet, it has been started now.
          
          Now let's force Garbage Collection, with the hopes of having the finalize()ers that put Jobs on the
          AsynchronousFinalizer queue be executed. Then just leave it, and handle the rest in {@link #stopThreads}.
          */
        preventor.info("OpenOffice JURT AsynchronousFinalizer thread started - forcing garbage collection to invoke finalizers");
        ClassLoaderLeakPreventor.gc();
      }
    }
    else {
      // Check for class existence without loading class and thus executing static block
      if(preventor.getClassLoader().getResource("com/sun/star/lib/util/AsynchronousFinalizer.class") != null) {
        preventor.warn("OpenOffice JURT AsynchronousFinalizer thread will not be stopped if started, as stopThreads is false");
        /* 
         By forcing Garbage Collection, we'll hopefully start the thread now, in case it would have been started by
         GC later, so that at least it will appear in the logs. 
         */
        ClassLoaderLeakPreventor.gc();
      }
    }
  }
  
  /**
   * Partially inspired by org.apache.catalina.loader.WebappClassLoader.clearReferencesThreads()
   */
  protected void stopThreads(ClassLoaderLeakPreventor preventor) {
    final Class<?> workerClass = preventor.findClass("java.util.concurrent.ThreadPoolExecutor$Worker");
    final Field oracleTarget = preventor.findField(Thread.class, "target"); // Sun/Oracle JRE
    final Field ibmRunnable = preventor.findField(Thread.class, "runnable"); // IBM JRE
    final Field inheritedAccessControlContext = preventor.findField(Thread.class, "inheritedAccessControlContext");

    final boolean waitForThreads = threadWaitMs > 0;
    for(Thread thread : preventor.getAllThreads()) {
      final Runnable runnable = (oracleTarget != null) ? 
          (Runnable) preventor.getFieldValue(oracleTarget, thread) : // Sun/Oracle JRE  
          (Runnable) preventor.getFieldValue(ibmRunnable, thread);   // IBM JRE

      final boolean runnableLoadedInWebApplication = preventor.isLoadedInClassLoader(runnable);
      if(thread != Thread.currentThread() && // Ignore current thread
         (preventor.isThreadInClassLoader(thread) || runnableLoadedInWebApplication)) {

        if (thread.getClass().getName().startsWith(StopThreadsCleanUp.JURT_ASYNCHRONOUS_FINALIZER)) {
          // Note, the thread group of this thread may be "system" if it is triggered by the Garbage Collector
          // however if triggered by us in forceStartOpenOfficeJurtCleanup() it may depend on the application server
          if(stopThreads) {
            preventor.info("Found JURT thread " + thread.getName() + "; starting " + JURTKiller.class.getSimpleName());
            new JURTKiller(preventor, thread).start();
          }
          else
            preventor.warn("JURT thread " + thread.getName() + " is still running in web app");
        }
        else if(thread.getThreadGroup() != null && 
           ("system".equals(thread.getThreadGroup().getName()) ||  // System thread
            "RMI Runtime".equals(thread.getThreadGroup().getName()))) { // RMI thread (honestly, just copied from Tomcat)
          
          if("Keep-Alive-Timer".equals(thread.getName())) {
            thread.setContextClassLoader(preventor.getLeakSafeClassLoader());
            preventor.debug("Changed contextClassLoader of HTTP keep alive thread");
          }
        }
        else if(thread.isAlive()) { // Non-system, running in web app
        
          if("java.util.TimerThread".equals(thread.getClass().getName())) {
            if(stopTimerThreads) {
              preventor.warn("Stopping Timer thread '" + thread.getName() + "' running in classloader.");
              stopTimerThread(preventor, thread);
            }
            else {
              preventor.info("Timer thread is running in classloader, but will not be stopped");
            }
          }
          else {
            // If threads is running an java.util.concurrent.ThreadPoolExecutor.Worker try shutting down the executor
            if(workerClass != null && workerClass.isInstance(runnable)) {
              if(stopThreads) {
                preventor.warn("Shutting down " + ThreadPoolExecutor.class.getName() + " running within the classloader.");
                try {
                  // java.util.concurrent.ThreadPoolExecutor, introduced in Java 1.5
                  final Field workerExecutor = preventor.findField(workerClass, "this$0");
                  final ThreadPoolExecutor executor = preventor.getFieldValue(workerExecutor, runnable);
                  executor.shutdownNow();
                }
                catch (Exception ex) {
                  preventor.error(ex);
                }
              }
              else 
                preventor.info(ThreadPoolExecutor.class.getName() + " running within the classloader will not be shut down.");
            }

            final String displayString = "'" + thread + "' of type " + thread.getClass().getName();

            if(! preventor.isLoadedInClassLoader(thread) && ! runnableLoadedInWebApplication) { // Not loaded in web app - just running there
              // This would for example be the case with org.apache.tomcat.util.threads.TaskThread
              if(waitForThreads) {
                preventor.warn("Thread " + displayString + " running in web app; waiting " + threadWaitMs);
                preventor.waitForThread(thread, threadWaitMs);
              }
              
              if(thread.isAlive() && preventor.isClassLoaderOrChild(thread.getContextClassLoader())) {
                preventor.warn("Thread " + displayString + (waitForThreads ? " still" : "") + 
                    " running in web app; changing context classloader to system classloader");
                thread.setContextClassLoader(ClassLoader.getSystemClassLoader());
              }
            }
            else if(stopThreads) { // Loaded by web app
              final String waitString = waitForThreads ? "after " + threadWaitMs + " ms " : "";
              preventor.warn("Stopping Thread " + displayString + " running in web app " + waitString);

              preventor.waitForThread(thread, threadWaitMs);

              // Normally threads should not be stopped (method is deprecated), since it may cause an inconsistent state.
              // In this case however, the alternative is a classloader leak, which may or may not be considered worse.
              if(thread.isAlive()) {
                //noinspection deprecation
                thread.stop();
              }
            }
            else {
              preventor.warn("Thread " + displayString + " is still running in web app");
            }
              
          }
        }
      }
      else { // Thread not running in web app - may have been started in contextInitialized() and need fixed ACC
        if(inheritedAccessControlContext != null && preventor.java_security_AccessControlContext$combiner != null) {
          final AccessControlContext accessControlContext = preventor.getFieldValue(inheritedAccessControlContext, thread);
          preventor.removeDomainCombiner(thread, accessControlContext);
        }
      }
    }
  }

  protected void stopTimerThread(ClassLoaderLeakPreventor preventor, Thread thread) {
    // Seems it is not possible to access Timer of TimerThread, so we need to mimic Timer.cancel()
    /** 
    try {
      Timer timer = (Timer) findField(thread.getClass(), "this$0").get(thread); // This does not work!
      warn("Cancelling Timer " + timer + " / TimeThread '" + thread + "'");
      timer.cancel();
    }
    catch (IllegalAccessException iaex) {
      error(iaex);
    }
    */

    try {
      final Field newTasksMayBeScheduled = preventor.findField(thread.getClass(), "newTasksMayBeScheduled");
      final Object queue = preventor.findField(thread.getClass(), "queue").get(thread); // java.lang.TaskQueue
      final Method clear = preventor.findMethod(queue.getClass(), "clear");
      
      // Do what java.util.Timer.cancel() does
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (queue) {
        newTasksMayBeScheduled.set(thread, Boolean.FALSE);
        clear.invoke(queue);
        queue.notify(); // "In case queue was already empty."
      }
      
      // We shouldn't need to join() here, thread will finish soon enough
    }
    catch (Exception ex) {
      preventor.error(ex);
    }
  }
  
  /** 
   * Inner class with the sole task of killing JURT finalizer thread after it is done processing jobs. 
   * We need to postpone the stopping of this thread, since more Jobs may in theory be add()ed when this web application
   * instance is closing down and being garbage collected.
   * See https://issues.apache.org/ooo/show_bug.cgi?id=122517
   * 
   * TODO Extract feature to separate class? Add option? https://github.com/mjiderhamn/classloader-leak-prevention/issues/44
   */
  protected class JURTKiller extends Thread {
    
    private final ClassLoaderLeakPreventor preventor;
    
    private final Thread jurtThread;
    
    private final List<?> jurtQueue;

    public JURTKiller(ClassLoaderLeakPreventor preventor, Thread jurtThread) {
      super("JURTKiller");
      this.preventor = preventor;
      this.jurtThread = jurtThread;
      jurtQueue = preventor.getStaticFieldValue(StopThreadsCleanUp.JURT_ASYNCHRONOUS_FINALIZER, "queue");
    }

    @Override
    public void run() {
      if(jurtQueue == null || jurtThread == null) {
        preventor.error(getName() + ": No queue or thread!?");
        return;
      }
      if(! jurtThread.isAlive()) {
        preventor.warn(getName() + ": " + jurtThread.getName() + " is already dead?");
      }
      
      boolean queueIsEmpty = false;
      while(! queueIsEmpty) {
        try {
          preventor.debug(getName() + " goes to sleep for " + ClassLoaderLeakPreventor.THREAD_WAIT_MS_DEFAULT + " ms");
          Thread.sleep(ClassLoaderLeakPreventor.THREAD_WAIT_MS_DEFAULT);
        }
        catch (InterruptedException e) {
          // Do nothing
        }

        if(State.RUNNABLE != jurtThread.getState()) { // Unless thread is currently executing a Job
          preventor.debug(getName() + " about to force Garbage Collection");
          ClassLoaderLeakPreventor.gc(); // Force garbage collection, which may put new items on queue

          synchronized (jurtQueue) {
            queueIsEmpty = jurtQueue.isEmpty();
            preventor.debug(getName() + ": JURT queue is empty? " + queueIsEmpty);
          }
        }
        else 
          preventor.debug(getName() + ": JURT thread " + jurtThread.getName() + " is executing Job");
      }
      
      preventor.info(getName() + " about to kill " + jurtThread);
      if(jurtThread.isAlive()) {
        //noinspection deprecation
        jurtThread.stop();
      }
    }
  }
  
}