package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Test case for {@link MBeanCleanUp}
 * @author Mattias Jiderhamn
 */
public class MBeanCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<MBeanCleanUp> {
  
  @Override
  protected void triggerLeak() throws Exception {
    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    mBeanServer.registerMBean(new Custom(), new ObjectName("se.jiderhamn:foo=bar" + System.currentTimeMillis() /* Unique name per test */));
  }
  
  public interface CustomMBean {
  }
  
  public static class Custom implements CustomMBean {
  }
}