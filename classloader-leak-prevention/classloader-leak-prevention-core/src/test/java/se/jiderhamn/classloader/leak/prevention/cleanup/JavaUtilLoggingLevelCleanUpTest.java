package se.jiderhamn.classloader.leak.prevention.cleanup;

import org.junit.Assume;

import se.jiderhamn.classloader.leak.prevention.support.JavaVersion;

/**
 * Test cases for {@link JavaUtilLoggingLevelCleanUp}
 * @author Mattias Jiderhamn
 */
public class JavaUtilLoggingLevelCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<JavaUtilLoggingLevelCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
      // Leak does not occur any more with JDK11+
      Assume.assumeTrue(JavaVersion.IS_JAVA_10_OR_EARLIER);

    // TODO Log
    // Logger logger = Logger.getLogger(JavaUtilLoggingLevelCleanUpTest.class.getName());
    // logger.setLevel(
        new CustomLevel();
  }

  /** PropertyEditor for testing */
  public static class CustomLevel extends java.util.logging.Level {
    public CustomLevel() {
      super("Foo", 10);
    }
  }
}