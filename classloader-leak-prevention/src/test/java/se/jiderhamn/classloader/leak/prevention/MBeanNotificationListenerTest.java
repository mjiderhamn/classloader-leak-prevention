package se.jiderhamn.classloader.leak.prevention;

import java.lang.management.ManagementFactory;
import javax.management.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.LeakPreventor;

/**
 * Verify that custom MBean NotificationListener causes leak that can be prevented
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(MBeanNotificationListenerTest.Prevent.class)
public class MBeanNotificationListenerTest {
  
  @Test
  public void triggerMBeanNotificationListenerLeak() throws Exception {
    ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(
        new CustomNotificationListener(), null, null);
  }
  
  public static class CustomNotificationListener implements NotificationListener {
    @Override
    public void handleNotification(Notification notification, Object handback) {
      
    }
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventorListener() {
        { // Initializer / "Constructor"
          unregisterMXBeanNotificationListeners();
        }
      };
    }

  }

}