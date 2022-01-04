package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.ref.SoftReference;

/**
 * Another variant test case for {@link ThreadLocalCleanUp} using chained {@link SoftReference}s to the value,
 * checking that the value is recursively dereferenced.
 */
public class ThreadLocalWithNestedRefValueCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<ThreadLocalCleanUp> {

    private static final ThreadLocal<SoftReference<SoftReference<Value>>> threadLocalWithCustomValue = new ThreadLocal<SoftReference<SoftReference<Value>>>();

    @Override
    protected void triggerLeak() throws Exception {
      threadLocalWithCustomValue.set(new SoftReference<SoftReference<Value>>(new SoftReference<Value>(new Value())));
    }

    /** Custom value class to create leak */
    private static class Value {
      
    }
}