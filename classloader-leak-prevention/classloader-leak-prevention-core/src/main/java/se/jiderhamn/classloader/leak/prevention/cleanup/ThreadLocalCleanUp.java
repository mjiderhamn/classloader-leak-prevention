package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.ref.Reference;
import java.lang.reflect.Field;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;
import se.jiderhamn.classloader.leak.prevention.MustBeAfter;

/**
 * Clear {@link ThreadLocal}s for which {@link ThreadLocal#remove()} has not been called, in case either the 
 * {@link ThreadLocal} is a custom one (subclassed in the protected ClassLoader), or the value is loaded by (or is)
 * the protected ClassLoader.
 * This must be done after threads have been stopped, or new ThreadLocals may be added by those threads.
 * @author Mattias Jiderhamn
 */
@SuppressWarnings("WeakerAccess")
public class ThreadLocalCleanUp implements ClassLoaderPreMortemCleanUp, MustBeAfter<ClassLoaderPreMortemCleanUp> {

  /** Class name for per thread transaction in Caucho Resin transaction manager */
  private static final String CAUCHO_TRANSACTION_IMPL = "com.caucho.transaction.TransactionImpl";
  
  protected Field java_lang_Thread_threadLocals;

  protected Field java_lang_Thread_inheritableThreadLocals;

  protected Field java_lang_ThreadLocal$ThreadLocalMap_table;

  protected Field java_lang_ThreadLocal$ThreadLocalMap$Entry_value;

  /** Needs to be done after {@link StopThreadsCleanUp}, since new {@link ThreadLocal}s may be added when threads are 
   * shutting down. */
  @Override
  public Class<? extends ClassLoaderPreMortemCleanUp>[] mustBeBeforeMe() {
    return new Class[] {StopThreadsCleanUp.class};
  }

  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    initFields(preventor); // Initialize some reflection variables
    
    if(java_lang_Thread_threadLocals == null)
      preventor.error("java.lang.Thread.threadLocals not found; something is seriously wrong!");
    
    if(java_lang_Thread_inheritableThreadLocals == null)
      preventor.error("java.lang.Thread.inheritableThreadLocals not found; something is seriously wrong!");

    if(java_lang_ThreadLocal$ThreadLocalMap_table == null)
      preventor.error("java.lang.ThreadLocal$ThreadLocalMap.table not found; something is seriously wrong!");


    for(Thread thread : preventor.getAllThreads()) {
      forEachThreadLocalInThread(preventor, thread);
    }
  }

  /** Make sure fields are initialized */
  private void initFields(ClassLoaderLeakPreventor preventor) {
    if(java_lang_Thread_threadLocals == null) { // First invokation of this preventor
      java_lang_Thread_threadLocals = preventor.findField(Thread.class, "threadLocals");
      java_lang_Thread_inheritableThreadLocals = preventor.findField(Thread.class, "inheritableThreadLocals");
      java_lang_ThreadLocal$ThreadLocalMap_table = preventor.findFieldOfClass("java.lang.ThreadLocal$ThreadLocalMap", "table");
    }
  }

  protected void forEachThreadLocalInThread(ClassLoaderLeakPreventor preventor, Thread thread) {
    try {
      if(java_lang_Thread_threadLocals != null) {
        processThreadLocalMap(preventor, thread, java_lang_Thread_threadLocals.get(thread));
      }

      if(java_lang_Thread_inheritableThreadLocals != null) {
        processThreadLocalMap(preventor, thread, java_lang_Thread_inheritableThreadLocals.get(thread));
      }
    }
    catch (/*IllegalAccess*/Exception ex) {
      preventor.error(ex);
    }
  }

  protected void processThreadLocalMap(ClassLoaderLeakPreventor preventor,
                                       Thread thread, Object threadLocalMap) throws IllegalAccessException {
    if(threadLocalMap != null && java_lang_ThreadLocal$ThreadLocalMap_table != null) {
      Field resin_suspendState = null;
      Field resin_isSuspended = null;
      final Object[] threadLocalMapTable = (Object[]) java_lang_ThreadLocal$ThreadLocalMap_table.get(threadLocalMap); // java.lang.ThreadLocal.ThreadLocalMap.Entry[]
      for(Object entry : threadLocalMapTable) {
        if(entry != null) {
          // Key is kept in WeakReference
          Reference<?> reference = (Reference<?>) entry;
          final ThreadLocal<?> threadLocal = (ThreadLocal<?>) reference.get();

          if(java_lang_ThreadLocal$ThreadLocalMap$Entry_value == null) {
            java_lang_ThreadLocal$ThreadLocalMap$Entry_value = preventor.findField(entry.getClass(), "value");
          }

          final Object value = java_lang_ThreadLocal$ThreadLocalMap$Entry_value.get(entry);

          // Workaround for http://bugs.caucho.com/view.php?id=5647
          if(value != null && CAUCHO_TRANSACTION_IMPL.equals(value.getClass().getName())) { // Resin transaction
            if(resin_suspendState == null && resin_isSuspended == null) { // First thread with Resin transaction, look up fields
              resin_suspendState = preventor.findField(value.getClass(), "_suspendState");
              resin_isSuspended = preventor.findField(value.getClass(), "_isSuspended");
            }

            if(resin_suspendState != null && resin_isSuspended != null) { // Both fields exist (as per version 4.0.37)
              if(preventor.getFieldValue(resin_suspendState, value) != null) { // There is a suspended state that may cause leaks
                // In theory a new transaction can be started and suspended between where we read and write the state,
                // and flag, therefore we suspend the thread meanwhile.
                try {
                  //noinspection deprecation
                  thread.suspend(); // Suspend the thread
                  if(preventor.getFieldValue(resin_suspendState, value) != null) { // Re-read suspend state when thread is suspended
                    final Object isSuspended = preventor.getFieldValue(resin_isSuspended, value);
                    if(!(isSuspended instanceof Boolean)) {
                      preventor.error(thread.toString() + " has " + CAUCHO_TRANSACTION_IMPL + " but _isSuspended is not boolean: " + isSuspended);
                    }
                    else if((Boolean) isSuspended) { // Is currently suspended - suspend state is correct
                      preventor.debug(thread.toString() + " has " + CAUCHO_TRANSACTION_IMPL + " that is suspended");
                    }
                    else { // Is not suspended, and thus should not have suspend state
                      resin_suspendState.set(value, null);
                      preventor.error(thread.toString() + " had " + CAUCHO_TRANSACTION_IMPL + " with unused _suspendState that was removed");
                    }
                  }
                }
                catch (Throwable t) { // Such as SecurityException
                  preventor.error(t);
                }
                finally {
                  //noinspection deprecation
                  thread.resume();
                }
              }
            }
          }

          final boolean customThreadLocal = preventor.isLoadedInClassLoader(threadLocal); // This is not an actual problem
          final boolean valueLoadedInWebApp = preventor.isLoadedInClassLoader(value);
          if(customThreadLocal || valueLoadedInWebApp ||
              (value instanceof ClassLoader && preventor.isClassLoaderOrChild((ClassLoader) value))) { // The value is classloader (child) itself
            // This ThreadLocal is either itself loaded by the web app classloader, or it's value is
            // Let's do something about it

            StringBuilder message = new StringBuilder();
            if(threadLocal != null) {
              if(customThreadLocal) {
                message.append("Custom ");
              }
              message.append("ThreadLocal of type ").append(threadLocal.getClass().getName()).append(": ").append(threadLocal);
            }
            else {
              message.append("Unknown ThreadLocal");
            }
            message.append(" with value ").append(value);
            if(value != null) {
              message.append(" of type ").append(value.getClass().getName());
              if(valueLoadedInWebApp)
                message.append(" that is loaded by web app");
            }


            // Process the detected potential leak
            processLeak(preventor, thread, reference, threadLocal, value, message.toString());
          }
        }
      }
    }
  }

  /**
   * After having detected potential ThreadLocal leak, this method is called.
   * Default implementation tries to clear the entry to avoid a leak.
   */
  protected void processLeak(ClassLoaderLeakPreventor preventor, Thread thread, Reference<?> entry,
                             ThreadLocal<?> threadLocal, Object value, String message) {
    if(threadLocal != null && thread == Thread.currentThread()) { // If running for current thread and we have the ThreadLocal ...
      // ... remove properly
      preventor.info(message + " will be remove()d from " + thread);
      threadLocal.remove();
    }
    else { // We cannot remove entry properly, so just make it stale
      preventor.info(message + " will be made stale for later expunging from " + thread);
    }

    // It seems like remove() doesn't really do the job, so play it safe and remove references from entry either way
    // (Example problem org.infinispan.context.SingleKeyNonTxInvocationContext) 
    entry.clear(); // Clear the key

    if(java_lang_ThreadLocal$ThreadLocalMap$Entry_value == null) {
      java_lang_ThreadLocal$ThreadLocalMap$Entry_value = preventor.findField(entry.getClass(), "value");
    }

    try {
      java_lang_ThreadLocal$ThreadLocalMap$Entry_value.set(entry, null); // Clear value to avoid circular references
    }
    catch (IllegalAccessException iaex) {
      preventor.error(iaex);
    }
  }
}
