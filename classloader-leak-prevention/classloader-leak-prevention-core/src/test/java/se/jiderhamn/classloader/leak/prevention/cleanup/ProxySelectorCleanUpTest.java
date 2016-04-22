package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/**
 * Test case for {@link ProxySelectorCleanUp}
 * @author Mattias Jiderhamn
 */
public class ProxySelectorCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<ProxySelectorCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
    ProxySelector.setDefault(new MyProxySelector());
  }
  
  /** Custom {@link ProxySelector} to trigger leak */
  private static class MyProxySelector extends ProxySelector {
    @Override
    public List<Proxy> select(URI uri) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
      throw new UnsupportedOperationException();
    }
  }
}