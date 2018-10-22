package se.jiderhamn.classloader.leak.prevention.cleanup;

import javax.xml.parsers.SAXParser;

import com.sun.org.apache.xerces.internal.xni.XNIException;
import com.sun.org.apache.xerces.internal.xni.parser.XMLParseException;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;

import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;

/**
 * Test case for {@link SAAJEnvelopeFactoryParserPoolCleanUp}
 * @author Mattias Jiderhamn
 */
public class SAAJEnvelopeFactoryParserPoolCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<SAAJEnvelopeFactoryParserPoolCleanUp> {

  @Override
  protected void triggerLeak() throws Exception {
    final ClassLoaderLeakPreventor preventor = getClassLoaderLeakPreventor();

    /*
    try {
      EnvelopeFactory.createEnvelope(new StreamSource(), new SOAPPart1_1Impl());
    }
    catch (SOAPException e) { // CS:IGNORE
    }
    */

    Class<?> envelopeFactoryClass = preventor.findClass("com.sun.xml.internal.messaging.saaj.soap.EnvelopeFactory");
    if (envelopeFactoryClass == null) {
      // We are running JDK > 8, so the internal APIs are not available anymore, so use the maven dependency
      envelopeFactoryClass = preventor.findClass("com.sun.xml.messaging.saaj.soap.EnvelopeFactory");
    }
    
    final Object /* com.sun.xml.internal.messaging.saaj.soap.ContextClassloaderLocal */
            parserPool = preventor.getStaticFieldValue(envelopeFactoryClass, "parserPool");
    final Object currentParserPool = preventor.findMethod(parserPool.getClass().getSuperclass(), "get").invoke(parserPool);
    assertNotNull(currentParserPool);

    
    final Method getMethod = preventor.findMethod(currentParserPool.getClass(), "get");
    final SAXParser saxParser = (SAXParser) getMethod.invoke(currentParserPool);
    
    saxParser.setProperty("http://apache.org/xml/properties/internal/error-handler", 
        new CustomErrorHandler()); // Loaded by protected classloader
    
    final Method putMethod = preventor.findMethod(currentParserPool.getClass(), "put", SAXParser.class);
    putMethod.invoke(currentParserPool, saxParser);
}

  /** Dummy XMLErrorHandler loaded by the class loader that should be garbage collected*/
  public static class CustomErrorHandler implements com.sun.org.apache.xerces.internal.xni.parser.XMLErrorHandler {
    @Override
    public void warning(String domain, String key, XMLParseException exception) throws XNIException { }

    @Override
    public void error(String domain, String key, XMLParseException exception) throws XNIException { }

    @Override
    public void fatalError(String domain, String key, XMLParseException exception) throws XNIException { }
  }
}