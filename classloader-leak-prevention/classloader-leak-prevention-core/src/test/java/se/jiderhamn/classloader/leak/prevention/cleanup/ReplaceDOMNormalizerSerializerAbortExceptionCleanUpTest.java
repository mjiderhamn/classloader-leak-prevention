package se.jiderhamn.classloader.leak.prevention.cleanup;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;
import se.jiderhamn.classloader.leak.prevention.ReplaceDOMNormalizerSerializerAbortException;
import se.jiderhamn.classloader.leak.prevention.preinit.ReplaceDOMNormalizerSerializerAbortExceptionInitiatorTest;

/**
 * Test cases for {@link ReplaceDOMNormalizerSerializerAbortException} when used as {@link ClassLoaderPreMortemCleanUp}
 * @author Mattias Jiderhamn
 */
public class ReplaceDOMNormalizerSerializerAbortExceptionCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<ReplaceDOMNormalizerSerializerAbortException> {
  @Override
  public void triggerLeak() throws Exception {
    ReplaceDOMNormalizerSerializerAbortExceptionInitiatorTest.triggerLeak();
  }
}