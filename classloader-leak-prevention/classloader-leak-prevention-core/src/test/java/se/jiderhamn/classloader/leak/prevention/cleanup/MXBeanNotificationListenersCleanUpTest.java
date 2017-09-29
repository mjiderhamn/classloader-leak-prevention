package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.management.ManagementFactory;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

/**
 * Test case for {@link MXBeanNotificationListenersCleanUp}
 * @author Mattias Jiderhamn
 */
public class MXBeanNotificationListenersCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<MXBeanNotificationListenersCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    ((NotificationEmitter) ManagementFactory.getMemoryMXBean()).addNotificationListener(
        new CustomNotificationListener(), null, null);
  }

  static class CustomNotificationListener implements NotificationListener {
    @Override
    public void handleNotification(Notification notification, Object handback) {
      
    }
  }
}