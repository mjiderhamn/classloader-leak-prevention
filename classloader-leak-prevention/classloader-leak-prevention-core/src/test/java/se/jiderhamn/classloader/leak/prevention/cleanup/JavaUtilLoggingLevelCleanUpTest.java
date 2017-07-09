package se.jiderhamn.classloader.leak.prevention.cleanup;

/**
 * Test cases for {@link JavaUtilLoggingLevelCleanUp}
 * @author Mattias Jiderhamn
 */
public class JavaUtilLoggingLevelCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<JavaUtilLoggingLevelCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
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