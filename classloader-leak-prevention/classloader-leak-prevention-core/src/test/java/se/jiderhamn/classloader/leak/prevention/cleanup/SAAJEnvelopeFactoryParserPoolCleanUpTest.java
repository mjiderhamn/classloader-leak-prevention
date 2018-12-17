package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Method;
import javax.xml.parsers.SAXParser;

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

    Class<?> envelopeFactoryClass = preventor.findClass("com.sun.xml.internal.messaging.saaj.soap.EnvelopeFactory");
    if (envelopeFactoryClass == null) {
      // Try the package for the maven dependency if the internal one is not available
      envelopeFactoryClass = preventor.findClass("com.sun.xml.messaging.saaj.soap.EnvelopeFactory");
    }
    
    final Object /* com.sun.xml.internal.messaging.saaj.soap.ContextClassloaderLocal */
            parserPool = preventor.getStaticFieldValue(envelopeFactoryClass, "parserPool");
    final Object currentParserPool = preventor.findMethod(parserPool.getClass().getSuperclass(), "get").invoke(parserPool);
    assertNotNull(currentParserPool);

    final Method getMethod = preventor.findMethod(currentParserPool.getClass(), "get");
    final SAXParser saxParser = (SAXParser) getMethod.invoke(currentParserPool);
    
    saxParser.setProperty("http://apache.org/xml/properties/internal/error-handler", getCustomErrorHandlerInstance(preventor));
    
    final Method putMethod = preventor.findMethod(currentParserPool.getClass(), "put", SAXParser.class);
    putMethod.invoke(currentParserPool, saxParser);
  }

  /*
   * Create a dummy XMLErrorHandler to be loaded by the classloader that sould be garbage collected
   * Create using reflection, since the type is not compile time accessible in newer Java Versions
   */
  public Object getCustomErrorHandlerInstance(final ClassLoaderLeakPreventor preventor) {
    final Class<?> errorHandlerClass = preventor.findClass("com.sun.org.apache.xerces.internal.xni.parser.XMLErrorHandler");
   
    Object instance = java.lang.reflect.Proxy.newProxyInstance(
        this.getClass().getClassLoader(),
        new java.lang.Class[] { errorHandlerClass },
        new java.lang.reflect.InvocationHandler() {

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws java.lang.Throwable {
          // Can be empty since we don't really want to use the handler  
          return null;
        }
    });
    
    return errorHandlerClass.cast(instance);
  }
}