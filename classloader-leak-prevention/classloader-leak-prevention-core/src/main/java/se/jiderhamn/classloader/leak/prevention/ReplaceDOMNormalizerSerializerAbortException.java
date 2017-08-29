package se.jiderhamn.classloader.leak.prevention;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * As reported at https://github.com/mjiderhamn/classloader-leak-prevention/issues/36, invoking
 * {@code DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().normalizeDocument();} or 
 * <code>
 *   Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
 *   DOMImplementationLS implementation = (DOMImplementationLS)document.getImplementation();
 *   implementation.createLSSerializer().writeToString(document);
 * </code> may trigger leaks caused by the static fields {@link com.sun.org.apache.xerces.internal.dom.DOMNormalizer#abort} and
 * {@link com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl#abort} respectively keeping stacktraces/backtraces
 * that may include references to classes loaded by our web application.
 * 
 * Since the {@link java.lang.Throwable#backtrace} itself cannot be accessed via reflection (see 
 * http://bugs.java.com/view_bug.do?bug_id=4496456) we need to replace the with new one without any stack trace.
 * 
 * This can be done either as a {@link PreClassLoaderInitiator} (recommended) or {@link ClassLoaderPreMortemCleanUp}.
 * 
 * @author Mattias Jiderhamn
 */
public class ReplaceDOMNormalizerSerializerAbortException implements PreClassLoaderInitiator, ClassLoaderPreMortemCleanUp {

  @Override
  public void doOutsideClassLoader(ClassLoaderLeakPreventor preventor) {
    replaceDOMNormalizerSerializerAbortException(preventor);
  }

  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    replaceDOMNormalizerSerializerAbortException(preventor);
  }

  @SuppressWarnings("WeakerAccess")
  protected void replaceDOMNormalizerSerializerAbortException(ClassLoaderLeakPreventor preventor) {
    final RuntimeException abort = constructRuntimeExceptionWithoutStackTrace(preventor, "abort", null);
    if(abort != null) {
      final Field normalizerAbort = preventor.findFieldOfClass("com.sun.org.apache.xerces.internal.dom.DOMNormalizer", "abort");
      if(normalizerAbort != null)
        preventor.setFinalStaticField(normalizerAbort, abort);

      final Field serializerAbort = preventor.findFieldOfClass("com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl", "abort");
      if(serializerAbort != null)
        preventor.setFinalStaticField(serializerAbort, abort);
    }
  }

  /** Construct a new {@link RuntimeException} without any stack trace, in order to avoid any references back to this class */
  @SuppressWarnings("WeakerAccess")
  public static RuntimeException constructRuntimeExceptionWithoutStackTrace(ClassLoaderLeakPreventor preventor,
                                                                        String message, Throwable cause) {
    try {
      final Constructor<RuntimeException> constructor = 
          RuntimeException.class.getDeclaredConstructor(String.class, Throwable.class, Boolean.TYPE, Boolean.TYPE);
      constructor.setAccessible(true);
      return constructor.newInstance(message, cause, true, false /* disable stack trace */);
    }
    catch (Throwable e) { // InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
      preventor.warn("Unable to construct RuntimeException without stack trace. The likely reason is that you are using Java <= 1.6. " +
          "No worries, except there might be some leaks you're not protected from (https://github.com/mjiderhamn/classloader-leak-prevention/issues/36 , " +
          "https://github.com/mjiderhamn/classloader-leak-prevention/issues/69). " + 
          "If you are already on Java 1.7+, please report issue to developer of this library!");
      preventor.warn(e);
      return null;
    }
  }
  
}