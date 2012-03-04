package se.jiderhamn.classloader.leak.prevention;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Mock implementation of javax.servlet.FilterChain used for tests. Mocking frameworks
 * should not be used, since they may affect garbage collection.
* @author Mattias Jiderhamn
*/
class MockFilterChain implements FilterChain {
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) throws IOException, ServletException {
    // Do nothing, just mock
  }
}