package se.jiderhamn.classloader.leak.prevention.cleanup;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.apache.ApacheHttpClient;

/**
 * Test case for leaks caused by {@link com.sun.jersey.client.apache.ApacheHttpClient} failing to 
 * close {@link org.apache.commons.httpclient.MultiThreadedHttpConnectionManager}. 
 * Demonstrates {@link StopThreadsCleanUp} is sufficient, although {@link MultiThreadedHttpConnectionManagerCleanUp} may
 * be used in case you want to avoid {@link StopThreadsCleanUp}.  
 *
 * @author Mattias Jiderhamn
 */
public class StopThreadsCleanUp_MultiThreadedHttpConnectionManagerTest extends ClassLoaderPreMortemCleanUpTestBase<StopThreadsCleanUp> {

  @Override
  protected void triggerLeak() {
    Client client = ApacheHttpClient.create(new DefaultClientConfig());
    try {
      // it doesn't matter where we make our call, we only want to initiate connections to create the leak
      WebResource webResource = client.resource("http://localhost:1234");
      webResource.accept("application/json").get(ClientResponse.class);
    } catch (Throwable ex) {
      //exception thrown for a non existing url, we do not need to call a real url, only to start the relevant leaking classes 
    }
    client.destroy();
  }

}
