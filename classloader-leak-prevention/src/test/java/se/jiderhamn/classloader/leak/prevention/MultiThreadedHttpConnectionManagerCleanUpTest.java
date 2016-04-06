package se.jiderhamn.classloader.leak.prevention;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.apache.ApacheHttpClient;

import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;
import se.jiderhamn.classloader.leak.prevention.cleanup.MultiThreadedHttpConnectionManagerCleanUp;

/**
 * Test case for leaks caused by com.sun.jersey.client.apache.ApacheHttpClient failing to close org.apache.commons.httpclient.MultiThreadedHttpConnectionManager
 * 
 * @author Marian Petrik
 */
@RunWith(JUnitClassloaderRunner.class)
public class MultiThreadedHttpConnectionManagerCleanUpTest extends PreventionsTestBase<MultiThreadedHttpConnectionManagerCleanUp>{
  
  @Test
  @Leaks
  public void leak() {
      Client client = ApacheHttpClient.create(new DefaultClientConfig());
      try {
    	  //it doesn't matter where we make our call, we only want to initiate connections to create the leak
	      WebResource webResource = client.resource("http://localhost:1234");
	      webResource.accept("application/json").get(ClientResponse.class);
      } catch (Throwable ex) {
    	  //
      }
      client.destroy();
  }
  
  @Leaks(false)
  @Test
  public void noLeakAfterCleanerRun() throws Exception {
      Client client = ApacheHttpClient.create(new DefaultClientConfig());
      client.destroy();
	  new MultiThreadedHttpConnectionManagerCleanUp().cleanUp(getClassLoaderLeakPreventor());
  }

}