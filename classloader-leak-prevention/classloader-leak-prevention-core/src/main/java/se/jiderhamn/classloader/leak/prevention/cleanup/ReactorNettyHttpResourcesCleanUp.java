package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Method;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;


/**
 * Clean up Reactor Netty resources
 * @author Mattias Jiderhamn
 */
public class ReactorNettyHttpResourcesCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Class<?> clazz = preventor.findClass("reactor.ipc.netty.http.HttpResources");
    if(preventor.isLoadedByClassLoader(clazz)) {
      final Method shutdown = preventor.findMethod(clazz, "shutdown");
      if(shutdown != null) {
        try {
          shutdown.invoke(null);
        }
        catch (Throwable e) {
          preventor.warn(e);
        }
      }
    }
  }
}
