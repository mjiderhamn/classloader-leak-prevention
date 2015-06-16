package se.jiderhamn.classloader.leak.prevention;

import java.lang.management.ManagementFactory;
import javax.management.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;
import se.jiderhamn.LeakPreventor;

/**
 * Verify that custom MBean causes leak that can be prevented
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(MBeanNotificationListenerTest.Prevent.class)
public class MBeanNotificationListenerTest {
  
  @Test
  public void triggerMBeanNotificationListenerLeak() throws Exception {
    System.out.println(this.getClass().toString() + " is loaded by " + this.getClass().getClassLoader()); // TODO
    ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(
        new CustomNotificationListener(), null /* TODO */, null);
  }
  
  public static class CustomNotificationListener implements NotificationListener {
    @Override
    public void handleNotification(Notification notification, Object handback) {
      
    }
  }
  
  public static class Prevent implements Runnable {
    public void run() {
      new ClassLoaderLeakPreventor() {
        { // Initializer / "Constructor"
          unregisterMXBeanNotificationListeners();
        }
      };
    }

  }

}