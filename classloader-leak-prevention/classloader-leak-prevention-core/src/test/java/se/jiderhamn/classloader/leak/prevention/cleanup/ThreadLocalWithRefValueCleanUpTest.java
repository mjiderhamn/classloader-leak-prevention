package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

/**
 * Variant test case for {@link ThreadLocalCleanUp} using a {@link Reference} instead of a direct, strong reference to the value.
 * 
 * All {@link Reference} implementations are using the bootstrap classloader, so it's required to dereference the value
 * to check which classloader was used for the value held by the Reference instance.
 * 
 * Also check {@link ThreadLocalWithNestedRefValueCleanUpTest}
 */
public class ThreadLocalWithRefValueCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<ThreadLocalCleanUp> {

    private static final ThreadLocal<SoftReference<Value>> threadLocalWithCustomValue = new ThreadLocal<SoftReference<Value>>();

    @Override
    protected void triggerLeak() throws Exception {
      threadLocalWithCustomValue.set(new SoftReference<Value>(new Value()));
    }

    /** Custom value class to create leak */
    private static class Value {
      
    }
}