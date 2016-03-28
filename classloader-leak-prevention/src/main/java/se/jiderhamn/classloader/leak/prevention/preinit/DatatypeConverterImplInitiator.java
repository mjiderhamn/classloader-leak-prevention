package se.jiderhamn.classloader.leak.prevention.preinit;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.PreClassLoaderInitiator;

/**
 * {@link javax.xml.bind.DatatypeConverterImpl} in the JAXB Reference Implementation shipped with JDK 1.6+ will
 * keep a static reference ({@link javax.xml.bind.DatatypeConverterImpl#datatypeFactory}) to a concrete subclass of 
 * {@link javax.xml.datatype.DatatypeFactory}, that is resolved when the class is loaded (which I believe happens if you
 * have custom bindings that reference the static methods in {@link javax.xml.bind.DatatypeConverter}). It seems that if 
 * for example you have a version of Xerces inside your application, the factory method may resolve {@code 
 * org.apache.xerces.jaxp.datatype.DatatypeFactoryImpl} as the implementation to use (rather than
 * {@code com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl} shipped with the JDK), which
 * means there will a reference from {@link javax.xml.bind.DatatypeConverterImpl} to your classloader.
 * 
 * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
 * 
 * @author Mattias Jiderhamn
 */
public class DatatypeConverterImplInitiator implements PreClassLoaderInitiator {
  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    try {
      Class.forName("javax.xml.bind.DatatypeConverterImpl"); // Since JDK 1.6. May throw java.lang.Error
    }
    catch (ClassNotFoundException e) {
      // Do nothing
    }
    catch (Throwable t) {
      preventor.warn(t);
    }
    
  }
}