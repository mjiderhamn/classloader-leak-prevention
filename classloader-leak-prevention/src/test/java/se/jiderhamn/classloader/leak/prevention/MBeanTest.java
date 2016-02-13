package se.jiderhamn.classloader.leak.prevention;

import java.lang.management.ManagementFactory;

import javax.management.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * Verify that custom MBean causes leak that can be prevented
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(MBeanTest.Prevent.class)
public class MBeanTest {
  
  @Test
  public void triggerMBeanLeak() throws Exception {
    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    mBeanServer.registerMBean(new Custom(), new ObjectName("se.jiderhamn:foo=bar"));
  }
  
  public interface CustomMBean {
  }
  
  public static class Custom implements CustomMBean {
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventorListener() {
        { // Initializer / "Constructor"
          unregisterMBeans();
        }
      };
    }

  }

}