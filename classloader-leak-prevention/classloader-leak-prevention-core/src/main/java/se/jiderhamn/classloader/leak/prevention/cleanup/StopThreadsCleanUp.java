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

  /** Thread {@link Runnable} for Sun/Oracle JRE i.e. java.lang.Thread.target */
  private Field oracleTarget;
  
  /** Thread {@link Runnable} for IBM JRE i.e. java.lang.Thread.runnable */
  private Field ibmRunnable;

  protected boolean stopThreads;

  /**
   * No of milliseconds to wait for threads to finish execution, before stopping them.
   */
  protected int threadWaitMs = THREAD_WAIT_MS_DEFAULT;
  
  /** Should Timer threads tied to the protected ClassLoader classloader be forced to stop at application shutdown? */
  protected boolean stopTimerThreads;

  /** Default constructor with {@link #stopThreads} = true and {@link #stopTimerThreads} = true */
  @SuppressWarnings("unused")
  public StopThreadsCleanUp() {
    this(true, true);
  }

  public StopThreadsCleanUp(boolean stopThreads, boolean stopTimerThreads) {
    this.stopThreads = stopThreads;
    this.stopTimerThreads = stopTimerThreads;
  }

  public void setStopThreads(boolean stopThreads) {
    this.stopThreads = stopThreads;
  }

  public void setStopTimerThreads(boolean stopTimerThreads) {
    this.stopTimerThreads = stopTimerThreads;
  }

  public void setThreadWaitMs(int threadWaitMs) {
    this.threadWaitMs = threadWaitMs;
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

    final boolean waitForThreads = threadWaitMs > 0;
    for(Thread thread : preventor.getAllThreads()) {
      final Runnable runnable = getRunnable(preventor, thread);

      final boolean threadLoadedByClassLoader = preventor.isLoadedInClassLoader(thread);
      final boolean threadGroupLoadedByClassLoader = preventor.isLoadedInClassLoader(thread.getThreadGroup());
      final boolean runnableLoadedByClassLoader = preventor.isLoadedInClassLoader(runnable);
      final boolean hasContextClassLoader = preventor.isClassLoaderOrChild(thread.getContextClassLoader());
      if(thread != Thread.currentThread() && // Ignore current thread
         (threadLoadedByClassLoader || threadGroupLoadedByClassLoader || hasContextClassLoader || // = preventor.isThreadInClassLoader(thread) 
          runnableLoadedByClassLoader)) {

        if (thread.getClass().getName().startsWith(StopThreadsCleanUp.JURT_ASYNCHRONOUS_FINALIZER)) {
          // Note, the thread group of this thread may be "system" if it is triggered by the Garbage Collector
          // however if triggered by us in forceStartOpenOfficeJurtCleanup() it may depend on the application server
          if(stopThreads) {
            preventor.info("Found JURT thread " + thread.getName() + "; starting " + JURTKiller.class.getSimpleName());
            new JURTKiller(preventor, thread).start();
          }
          else
            preventor.warn("JURT thread " + thread.getName() + " is still running in protected ClassLoader");
        }
        else if(thread.getThreadGroup() != null && 
           ("system".equals(thread.getThreadGroup().getName()) ||  // System thread
            "RMI Runtime".equals(thread.getThreadGroup().getName()))) { // RMI thread (honestly, just copied from Tomcat)
          
          if("Keep-Alive-Timer".equals(thread.getName())) {
            thread.setContextClassLoader(preventor.getLeakSafeClassLoader());
            preventor.debug("Changed contextClassLoader of HTTP keep alive thread");
          }
        }
        else if(thread.isAlive()) { // Non-system, running in protected ClassLoader

          if(thread.getClass().getName().startsWith("java.util.Timer")) { // Sun/Oracle = "java.util.TimerThread"; IBM = "java.util.Timer$TimerImpl"
            if(thread.getName() != null && thread.getName().startsWith("PostgreSQL-JDBC-SharedTimer-")) { // Postgresql JDBC timer thread
              // Replace contextClassLoader, if needed
              if(hasContextClassLoader) {
                final Class<?> postgresqlDriver = preventor.findClass("org.postgresql.Driver");
                final ClassLoader postgresqlCL = (postgresqlDriver != null && ! preventor.isLoadedByClassLoader(postgresqlDriver)) ?
                    postgresqlDriver.getClassLoader() : // Postgresql driver loaded by other classloader than we want to protect
                    preventor.getLeakSafeClassLoader();
                thread.setContextClassLoader(postgresqlCL);
                preventor.warn("Changing contextClassLoader of " + thread + " to " + postgresqlCL);
              }

              // Replace AccessControlContext
              final Field inheritedAccessControlContext = preventor.findField(Thread.class, "inheritedAccessControlContext");
              if(inheritedAccessControlContext != null) {
                try {
                  final AccessControlContext acc = preventor.createAccessControlContext();
                  inheritedAccessControlContext.set(thread, acc);
                  preventor.removeDomainCombiner("thread " + thread, acc);
                }
                catch (Exception e) {
                  preventor.error(e);
                }
              }
            }
            else if(stopTimerThreads) {
              preventor.warn("Stopping Timer thread '" + thread.getName() + "' running in protected ClassLoader. " +
                  preventor.getStackTrace(thread));
              stopTimerThread(preventor, thread);
            }
            else {
              preventor.info("Timer thread is running in protected ClassLoader, but will not be stopped. " + 
                  preventor.getStackTrace(thread));
            }
          }
          else {
            final String displayString = "Thread '" + thread + "'" + 
                (threadLoadedByClassLoader ? " of type " + thread.getClass().getName() + " loaded by protected ClassLoader" : "") +
                (runnableLoadedByClassLoader ? " with Runnable of type " + runnable.getClass().getName() + " loaded by protected ClassLoader" : "") +
                (threadGroupLoadedByClassLoader ? " with ThreadGroup of type " + thread.getThreadGroup().getClass().getName() + " loaded by protected ClassLoader" : "") +
                (hasContextClassLoader ? " with contextClassLoader = protected ClassLoader or child" : "");

            // If threads is running an java.util.concurrent.ThreadPoolExecutor.Worker try shutting down the executor
            if(workerClass != null && workerClass.isInstance(runnable)) {
              try {
                // java.util.concurrent.ThreadPoolExecutor, introduced in Java 1.5
                final Field workerExecutor = preventor.findField(workerClass, "this$0");
                final ThreadPoolExecutor executor = preventor.getFieldValue(workerExecutor, runnable);
                if(executor != null) {
                  if("org.apache.tomcat.util.threads.ThreadPoolExecutor".equals(executor.getClass().getName())) {
                    // Tomcat pooled thread
                    preventor.debug(displayString + " is worker of " + executor.getClass().getName());
                  }
                  else if(preventor.isLoadedInClassLoader(executor) || preventor.isLoadedInClassLoader(executor.getThreadFactory())) {
                    if(stopThreads) {
                      preventor.warn("Shutting down ThreadPoolExecutor of type " + executor.getClass().getName());
                      executor.shutdownNow();
                    }
                    else {
                      preventor.warn("ThreadPoolExecutor of type " + executor.getClass().getName() +
                          " should be shut down.");
                    }
                  }
                  else {
                    preventor.info(displayString + " is a ThreadPoolExecutor.Worker of " + executor.getClass().getName() +
                        " but found no reason to shut down ThreadPoolExecutor.");
                  }
                }
              }
              catch (Exception ex) {
                preventor.error(ex);
              }
            }

            if(! threadLoadedByClassLoader && ! runnableLoadedByClassLoader && ! threadGroupLoadedByClassLoader) { // Not loaded in protected ClassLoader - just running there
              // This would for example be the case with org.apache.tomcat.util.threads.TaskThread
              if(waitForThreads) {
                preventor.warn(displayString + "; waiting " + threadWaitMs + 
                    " ms. " + preventor.getStackTrace(thread));
                preventor.waitForThread(thread, threadWaitMs, false /* No interrupt */);
              }
              
              if(thread.isAlive() && preventor.isClassLoaderOrChild(thread.getContextClassLoader())) { // Still running in ClassLoader
                preventor.warn(displayString + (waitForThreads ? " still" : "") + 
                    " alive; changing context ClassLoader to leak safe (" + 
                    preventor.getLeakSafeClassLoader() + "). " + preventor.getStackTrace(thread));
                thread.setContextClassLoader(preventor.getLeakSafeClassLoader());
              }
            }
            else if(stopThreads) { // Thread/Runnable/ThreadGroup loaded by protected ClassLoader
              if(waitForThreads) {
                preventor.warn("Waiting for " + displayString + " for " + threadWaitMs + " ms. " +
                    preventor.getStackTrace(thread));

                preventor.waitForThread(thread, threadWaitMs, true /* Interrupt if needed */);
              }

              // Normally threads should not be stopped (method is deprecated), since it may cause an inconsistent state.
              // In this case however, the alternative is a classloader leak, which may or may not be considered worse.
              if(thread.isAlive()) {
                preventor.warn("Stopping " + displayString + ". " + preventor.getStackTrace(thread));
                //noinspection deprecation
                thread.stop();
              }
              else {
                preventor.info(displayString + " no longer alive - no action needed.");
              }
            }
            else {
              preventor.warn(displayString + " would cause leak. " + preventor.getStackTrace(thread));
            }
              
          }
        }
      }
    }
  }
  
  /** Get {@link Runnable} of given thread, if any */
  private Runnable getRunnable(ClassLoaderLeakPreventor preventor, Thread thread) {
    if(oracleTarget == null && ibmRunnable == null) { // Not yet initialized
      oracleTarget = preventor.findField(Thread.class, "target"); // Sun/Oracle JRE
      ibmRunnable = preventor.findField(Thread.class, "runnable"); // IBM JRE       
    }

    return (oracleTarget != null) ? (Runnable) preventor.getFieldValue(oracleTarget, thread) : // Sun/Oracle JRE  
        (Runnable) preventor.getFieldValue(ibmRunnable, thread);   // IBM JRE
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
   * We need to postpone the stopping of this thread, since more Jobs may in theory be add()ed when the protected 
   * ClassLoader is closing down and being garbage collected.
   * See https://issues.apache.org/ooo/show_bug.cgi?id=122517
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