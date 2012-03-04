package se.jiderhamn.classloader.leak.prevention;

import java.util.Enumeration;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

import static java.util.Collections.emptyList;
import static java.util.Collections.enumeration;

/**
 * Mock implementation of javax.servlet.FilterConfig used for tests. Mocking frameworks
  * should not be used, since they may affect garbage collection.
 * @author Mattias Jiderhamn
 */
public class MockFilterConfig implements FilterConfig {
  public String getFilterName() {
    return "mock-filter";
  }

  public ServletContext getServletContext() {
    return null;
  }

  public String getInitParameter(String parameterName) {
    return null;
  }

  public Enumeration getInitParameterNames() {
    return enumeration(emptyList());
  }
}