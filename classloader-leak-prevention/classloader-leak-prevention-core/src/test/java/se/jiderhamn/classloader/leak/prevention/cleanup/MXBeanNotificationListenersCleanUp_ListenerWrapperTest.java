package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

import com.sun.jmx.interceptor.DefaultMBeanServerInterceptor;

/**
 * Test case for {@link MXBeanNotificationListenersCleanUp} when {@link DefaultMBeanServerInterceptor.ListenerWrapper}
 * is used.
 * @author Mattias Jiderhamn
 */
public class MXBeanNotificationListenersCleanUp_ListenerWrapperTest extends ClassLoaderPreMortemCleanUpTestBase<MXBeanNotificationListenersCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    final Class<?> listenerWrapperClass = getClassLoaderLeakPreventor().findClass("com.sun.jmx.interceptor.DefaultMBeanServerInterceptor$ListenerWrapper");
    final Constructor<?> constructor = listenerWrapperClass.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    final NotificationListener wrappedListener = (NotificationListener) constructor.newInstance(
        new MXBeanNotificationListenersCleanUpTest.CustomNotificationListener(), null, null);

    ((NotificationEmitter) ManagementFactory.getMemoryMXBean()).addNotificationListener(
        wrappedListener, null, null);
  }
}