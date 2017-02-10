package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

import java.lang.reflect.Method;

/**
 * Using the class sun.java2d.opengl.OGLRenderQueue will spawn a new QueueFlusher thread with the same contextClassLoader.
 */
public class Java2dRenderQueueInitiator implements PreClassLoaderInitiator {
    @Override
    public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
        try {
            Method getInstance = preventor.findMethod("sun.java2d.opengl.OGLRenderQueue", "getInstance");
            if (getInstance != null) {
                getInstance.invoke(null);
            }
        } catch (Throwable e) {
            preventor.warn(e);
        }
    }
}
