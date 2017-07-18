package se.jiderhamn.classloader.leak.prevention.cleanup;

import javax.xml.parsers.SAXParser;

import com.sun.org.apache.xerces.internal.xni.XNIException;
import com.sun.org.apache.xerces.internal.xni.parser.XMLParseException;
import com.sun.xml.internal.messaging.saaj.soap.EnvelopeFactory;
import com.sun.xml.internal.messaging.saaj.util.ParserPool;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;

import static org.junit.Assert.assertNotNull;

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

    final Object /* com.sun.xml.internal.messaging.saaj.soap.ContextClassloaderLocal */
            parserPool = preventor.getStaticFieldValue(EnvelopeFactory.class, "parserPool");
    final ParserPool currentParserPool = (ParserPool) preventor.findMethod(parserPool.getClass().getSuperclass(), "get").invoke(parserPool);
    assertNotNull(currentParserPool);

    final SAXParser saxParser = currentParserPool.get();
    saxParser.setProperty("http://apache.org/xml/properties/internal/error-handler", 
        new CustomErrorHandler()); // Loaded by protected classloader
    currentParserPool.put(saxParser);
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