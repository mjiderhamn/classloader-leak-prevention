package se.jiderhamn.classloader.leak.prevention.preinit;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;
import se.jiderhamn.classloader.leak.prevention.ReplaceDOMNormalizerSerializerAbortException;

/**
 * See https://github.com/mjiderhamn/classloader-leak-prevention/issues/8
 * and https://github.com/mjiderhamn/classloader-leak-prevention/issues/23
 * and https://github.com/mjiderhamn/classloader-leak-prevention/issues/69
 * and http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * @author Mattias Jiderhamn
 */
public class OracleJdbcThreadInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    // Cause oracle.jdbc.driver.OracleTimeoutPollingThread to be started with contextClassLoader = system classloader  
    try {
      Class.forName("oracle.jdbc.driver.OracleTimeoutThreadPerVM");
    }
    catch (ClassNotFoundException e) {
      // Ignore silently - class not present
    }

    // Cause oracle.jdbc.driver.BlockSource.ThreadedCachingBlockSource.BlockReleaser to be started with contextClassLoader = system classloader  
    try {
      Class.forName("oracle.jdbc.driver.BlockSource$ThreadedCachingBlockSource$BlockReleaser");
    }
    catch (ClassNotFoundException e) {
      // Ignore silently - class not present
    }

    // Cause TimerThread to be started with contextClassLoader = safe classloader   
    try {
      Class.forName("oracle.net.nt.TimeoutInterruptHandler");
    }
    catch (ClassNotFoundException e) {
      // Ignore silently - class not present
    }

    // Cause instance to be created and in turn Timer and TimeThread to be created with contextClassLoader = safe classloader   
    try {
      Class.forName("oracle.jdbc.driver.NoSupportHAManager");
    }
    catch (ClassNotFoundException e) {
      // Ignore silently - class not present
    }

    // Avoid stack trace with trace elements being referenced from MBean   
    try {
      Class.forName("oracle.jdbc.driver.OracleDriver"); // Cause oracle.jdbc.driver.OracleDiagnosabilityMBean to be registered
      final Class<?> oracleDiagnosabilityMBeanClass = Class.forName("oracle.jdbc.driver.OracleDiagnosabilityMBean");
      
      final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
      final Set<ObjectName> mBeanNames = mBeanServer.queryNames(new ObjectName("com.oracle.jdbc:type=diagnosability,*"), null);
      for(ObjectName mBeanName : mBeanNames) {
        final  /* oracle.jdbc.driver.OracleDiagnosabilityMBean */ Object oracleDiagnosabilityMBean = oracleDiagnosabilityMBeanClass.newInstance();
        final /* oracle.jdbc.logging.runtime.TraceControllerImpl */ Object traceController =
            preventor.getFieldValue(oracleDiagnosabilityMBean, "tc");
        if(traceController != null) {
          final Field reSuspendedField = preventor.findField(traceController.getClass(), "reSuspended");
          if(reSuspendedField != null) {
            final Object oldValue = reSuspendedField.get(traceController);
            reSuspendedField.set(traceController, 
                ReplaceDOMNormalizerSerializerAbortException.constructRuntimeExceptionWithoutStackTrace(preventor,
                    (oldValue instanceof Exception) ? ((Exception)oldValue).getMessage() : "trace controller is currently suspended",
                    null));
            preventor.info("Replacing MBean " + mBeanName + " with " + reSuspendedField.getName() + " field of " +
                traceController + " replaced to avoid backtrace references.");

            // Replace MBean
            mBeanServer.unregisterMBean(mBeanName);
            mBeanServer.registerMBean(oracleDiagnosabilityMBean, mBeanName);
          }
          else
            preventor.warn("Unable to find 'reSuspended' field of " + traceController);
        }
        else
          preventor.warn("Found " + oracleDiagnosabilityMBeanClass + " but it has no 'tm' TraceController attribute");
      }
    }
    catch (Exception e) {
      // Ignore silently
    }

  }
}