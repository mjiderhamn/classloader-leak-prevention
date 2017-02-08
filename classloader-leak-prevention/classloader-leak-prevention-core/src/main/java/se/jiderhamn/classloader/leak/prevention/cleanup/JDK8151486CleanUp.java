package se.jiderhamn.classloader.leak.prevention.cleanup;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * Clear the "domains" field of the parent ClassLoader.
 *
 * See <a href="https://bugs.openjdk.java.net/browse/JDK-8151486">JDK-8151486</a>
 */
public class JDK8151486CleanUp implements ClassLoaderPreMortemCleanUp {
    @Override
    public void cleanUp(ClassLoaderLeakPreventor preventor) {
        Field field = preventor.findField(ClassLoader.class, "domains");
        if (field == null) {
            // field only exists in JDK versions [8u25, 9u140)
            return;
        }

        for (ClassLoader cl = preventor.getClassLoader().getParent(); cl != null; cl = cl.getParent()) {
            Set<?> domains = preventor.getFieldValue(field, cl);
            if (domains != null) {
                domains.clear();
            }
        }
    }
}
