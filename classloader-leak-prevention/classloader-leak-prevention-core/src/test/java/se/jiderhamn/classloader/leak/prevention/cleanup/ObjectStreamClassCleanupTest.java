package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.io.ObjectStreamClass;
import java.io.Serializable;

/**
 * Test cases for {@link ObjectStreamClassCleanup}
 */
public class ObjectStreamClassCleanupTest extends ClassLoaderPreMortemCleanUpTestBase<ObjectStreamClassCleanup> {

    @Override
    protected void triggerLeak() throws Exception {
        ObjectStreamClass.lookup(SerializableEntity.class);
    }

    protected final class SerializableEntity implements Serializable {

        private static final long serialVersionUID = 1L;

        public int value;
    }
}
