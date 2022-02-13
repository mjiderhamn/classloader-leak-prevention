package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.util.concurrent.ConcurrentHashMap;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clean up for the static caches of {@link java.io.ObjectStreamClass}
 */
public class ObjectStreamClassCleanup implements ClassLoaderPreMortemCleanUp {

    @Override
    public void cleanUp(ClassLoaderLeakPreventor preventor) {
        try {
            final Class<?> cacheClass = preventor.findClass("java.io.ObjectStreamClass$Caches");
            if (cacheClass == null) { return; }

            Object localDescsCache = preventor.getStaticFieldValue(cacheClass, "localDescs");
            clearIfConcurrentHashMap(localDescsCache, preventor);

            Object reflectorsCache = preventor.getStaticFieldValue(cacheClass, "reflectors");
            clearIfConcurrentHashMap(reflectorsCache, preventor);
        }
        catch (Exception e) {
            preventor.error(e);
        }
    }

    protected void clearIfConcurrentHashMap(Object object, ClassLoaderLeakPreventor preventor) {
        if (!(object instanceof ConcurrentHashMap)) { return; }
        ConcurrentHashMap<?,?> map = (ConcurrentHashMap<?,?>) object;
        int nbOfEntries=map.size();
        map.clear();
        preventor.info("Detected and fixed leak situation for java.io.ObjectStreamClass ("+nbOfEntries+" entries were flushed).");
    }
}