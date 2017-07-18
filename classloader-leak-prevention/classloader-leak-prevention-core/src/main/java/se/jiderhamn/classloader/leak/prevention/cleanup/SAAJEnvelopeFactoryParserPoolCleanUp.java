package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;
import java.util.Map;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clean up leak caused by {@link javax.xml.parsers.SAXParser} attribute/property being loaded by protected class loader
 * and cached in {@link com.sun.xml.internal.messaging.saaj.soap.EnvelopeFactory#parserPool}.
 * See <a href="https://issues.apache.org/jira/browse/XALANJ-2600">here</a>.
 * @author Mattias Jiderhamn
 */
public class SAAJEnvelopeFactoryParserPoolCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Object /*com.sun.xml.internal.messaging.saaj.soap.ContextClassloaderLocal*/
        parserPool = preventor.getStaticFieldValue("com.sun.xml.internal.messaging.saaj.soap.EnvelopeFactory",
        "parserPool");
    if(parserPool != null) {
      final Field CACHE = preventor.findField(parserPool.getClass().getSuperclass(), "CACHE");
      if(CACHE != null) {
        final Object cache = preventor.getFieldValue(CACHE, parserPool);
        if(cache instanceof Map) { // WeakHashMap
          ((Map) cache).remove(preventor.getClassLoader());
        }
      }
    }
  }
}