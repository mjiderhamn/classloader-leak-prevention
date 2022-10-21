package se.jiderhamn.classloader.leak.prevention.cleanup;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;
import se.jiderhamn.classloader.leak.prevention.ReplaceDOMNormalizerSerializerAbortException;
import se.jiderhamn.classloader.leak.prevention.preinit.ReplaceDOMNormalizerSerializerAbortExceptionInitiatorTest;
import se.jiderhamn.classloader.leak.prevention.support.JavaVersion;

import org.junit.Assume;

/**
 * Test cases for {@link ReplaceDOMNormalizerSerializerAbortException} when used as {@link ClassLoaderPreMortemCleanUp}
 * @author Mattias Jiderhamn
 */
public class ReplaceDOMNormalizerSerializerAbortExceptionCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<ReplaceDOMNormalizerSerializerAbortException> {
  @Override
  public void triggerLeak() throws Exception {
    ReplaceDOMNormalizerSerializerAbortExceptionInitiatorTest.triggerLeak();
  }

    @Override
    public void triggerLeakWithoutCleanup() throws Exception {
        // Leak does not occur any more with JDK8+
        Assume.assumeTrue(JavaVersion.IS_JAVA_1_6 || JavaVersion.IS_JAVA_1_7);
        super.triggerLeakWithoutCleanup();
    }

}