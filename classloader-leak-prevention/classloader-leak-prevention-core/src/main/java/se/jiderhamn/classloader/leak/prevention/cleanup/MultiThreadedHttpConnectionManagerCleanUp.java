package se.jiderhamn.classloader.leak.prevention.cleanup;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Invokes static method org.apache.commons.httpclient.MultiThreadedHttpConnectionManager.shutdownAll() to close connections left out by com.sun.jersey.client.apache.ApacheHttpClient.  
 *
 * @author Marian Petrik
 */
public class MultiThreadedHttpConnectionManagerCleanUp implements ClassLoaderPreMortemCleanUp {

	@Override
	public void cleanUp(ClassLoaderLeakPreventor preventor) {
		final Class<?> connManager = preventor.findClass("org.apache.commons.httpclient.MultiThreadedHttpConnectionManager");
		if(connManager != null && preventor.isLoadedByClassLoader(connManager)) {
			try {
				connManager.getMethod("shutdownAll").invoke(null);
			}
			catch (Throwable t) {
				preventor.warn(t);
			}
		}
	}

}