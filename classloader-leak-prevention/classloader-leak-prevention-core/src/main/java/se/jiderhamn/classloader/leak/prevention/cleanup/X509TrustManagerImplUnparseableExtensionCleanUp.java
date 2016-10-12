package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.util.Map;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.X509TrustManager;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * {@link sun.security.ssl.X509TrustManagerImpl} keeps a list set of trusted certs, which may include 
 * {@link sun.security.x509.UnparseableExtension} that in turn may include an {@link Exception} with a backtrace
 * with references to the classloader that we want to protect 
 * @author Mattias Jiderhamn
 */
public class X509TrustManagerImplUnparseableExtensionCleanUp implements ClassLoaderPreMortemCleanUp {

  private static final String SUN_SECURITY_X509_X509_CERT_IMPL = "sun.security.x509.X509CertImpl";

  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final SSLContextSpi sslContext = preventor.getStaticFieldValue("sun.security.ssl.SSLContextImpl$DefaultSSLContext", "defaultImpl");
    if(sslContext != null) {
      final Field trustManagerField = preventor.findFieldOfClass("sun.security.ssl.SSLContextImpl", "trustManager");
      final Method get = preventor.findMethod(SUN_SECURITY_X509_X509_CERT_IMPL, "get", String.class);
      final Method getUnparseableExtensions = preventor.findMethod("sun.security.x509.CertificateExtensions", "getUnparseableExtensions");
      final Field why = preventor.findFieldOfClass("sun.security.x509.UnparseableExtension", "why");

      if(trustManagerField != null && get != null && getUnparseableExtensions != null && why != null) {
        final X509TrustManager/*Impl*/ trustManager = preventor.getFieldValue(trustManagerField, sslContext);
        for(X509Certificate x509Certificate : trustManager.getAcceptedIssuers()) {
          if(SUN_SECURITY_X509_X509_CERT_IMPL.equals(x509Certificate.getClass().getName())) {
            try {
              final /* sun.security.x509.CertificateExtensions*/ Object extensions = get.invoke(x509Certificate, "x509.info.extensions");
              if(extensions != null) {
                Map/*<String, sun.security.x509.Extension>*/ unparseableExtensions = (Map) getUnparseableExtensions.invoke(extensions);
                for(Object unparseableExtension : unparseableExtensions.values()) {
                  if(why.get(unparseableExtension) != null) {
                    preventor.warn(trustManager + " cached X509Certificate that had unparseable extension; removing 'why': " +
                        x509Certificate);
                    why.set(unparseableExtension, null);
                  }
                }
              }
            }
            catch (Exception e) {
              preventor.error(e);
            }
          }
        }
      }
    }
  }
}