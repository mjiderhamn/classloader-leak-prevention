package se.jiderhamn.classloader.leak.prevention.preinit;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;
import se.jiderhamn.classloader.leak.prevention.PreventionsTestBase;
import se.jiderhamn.classloader.leak.prevention.ReplaceDOMNormalizerSerializerAbortException;

/**
 * Test cases for {@link ReplaceDOMNormalizerSerializerAbortException} when used as {@link PreClassLoaderInitiator}
 * @author Mattias Jiderhamn
 */
@RunWith(JUnitClassloaderRunner.class)
public class ReplaceDOMNormalizerSerializerAbortExceptionInitiatorTest extends PreventionsTestBase<ReplaceDOMNormalizerSerializerAbortException> {
  
  @Leaks(false)
  @Test
  public void noLeakAfterInitiatorRun() throws Exception {
    getTestedImplementation().doOutsideClassLoader(getClassLoaderLeakPreventor());
    triggerLeak();
  }

  /** Invoke code that may trigger leak */
  public static void triggerLeak() throws ParserConfigurationException {
    // Alternative 1
    DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().normalizeDocument();

    // Alternative 2
    Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    DOMImplementationLS implementation = (DOMImplementationLS)document.getImplementation();
    implementation.createLSSerializer().writeToString(document);
  }
}