package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * The classloader of the first thread to call DocumentBuilderFactory.newInstance().newDocumentBuilder()
 * seems to be unable to garbage collection. Is it believed this is caused by some JVM internal bug.
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * @author Mattias Jiderhamn
 */
public class DocumentBuilderFactoryInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    try {
      javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }
    catch (Exception ex) { // Example: ParserConfigurationException
      preventor.error(ex);
    }
                                            
  }
}