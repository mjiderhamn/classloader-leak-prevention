package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Authenticator;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Clear the default {@link java.net.Authenticator} (in case current one is loaded by protected ClassLoader). 
 * Includes special workaround for CXF issue https://issues.apache.org/jira/browse/CXF-5442
 * @author Mattias Jiderhamn
 */
public class DefaultAuthenticatorCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Authenticator defaultAuthenticator = getDefaultAuthenticator(preventor);
    if(defaultAuthenticator == null || // Can both mean not set, or error retrieving, so unset anyway to be safe 
        preventor.isLoadedInClassLoader(defaultAuthenticator)) {
      if(defaultAuthenticator != null) // Log warning only if a default was actually found
        preventor.warn("Unsetting default " + Authenticator.class.getName() + ": " + defaultAuthenticator);
      Authenticator.setDefault(null);
    }
    else {
      if("org.apache.cxf.transport.http.ReferencingAuthenticator".equals(defaultAuthenticator.getClass().getName())) {
        /*
         Needed since org.apache.cxf.transport.http.ReferencingAuthenticator is loaded by dummy classloader that
         references protected classloader via AccessControlContext + ProtectionDomain.
         See https://issues.apache.org/jira/browse/CXF-5442
        */

        final Class<?> cxfAuthenticator = preventor.findClass("org.apache.cxf.transport.http.CXFAuthenticator");
        if(cxfAuthenticator != null && preventor.isLoadedByClassLoader(cxfAuthenticator)) { // CXF loaded in this application
          final Object cxfAuthenticator$instance = preventor.getStaticFieldValue(cxfAuthenticator, "instance");
          if(cxfAuthenticator$instance != null) { // CXF authenticator has been initialized in protected ClassLoader
            final Object authReference = preventor.getFieldValue(defaultAuthenticator, "auth");
            if(authReference instanceof Reference) { // WeakReference 
              final Reference<?> reference = (Reference<?>) authReference;
              final Object referencedAuth = reference.get();
              if(referencedAuth == cxfAuthenticator$instance) { // References CXFAuthenticator of this classloader 
                preventor.warn("Default " + Authenticator.class.getName() + " was " + defaultAuthenticator + " that referenced " +
                    cxfAuthenticator$instance + " loaded by protected ClassLoader");

                // Let CXF unwrap in it's own way (in case there are multiple CXF webapps in the container)
                reference.clear(); // Remove the weak reference before calling check()
                try {
                  final Method check = defaultAuthenticator.getClass().getMethod("check");
                  check.setAccessible(true);
                  check.invoke(defaultAuthenticator);
                }
                catch (Exception e) {
                  preventor.error(e);
                }
              }
            }
          }
        }
      }
      
      removeWrappedAuthenticators(preventor, defaultAuthenticator);
      
      preventor.info("Default " + Authenticator.class.getName() + " not loaded by protected ClassLoader: " + defaultAuthenticator);
    }
  }
  
  /** Find default {@link Authenticator} */
  @SuppressWarnings("WeakerAccess")
  protected Authenticator getDefaultAuthenticator(ClassLoaderLeakPreventor preventor) {
    // Normally Corresponds to getStaticFieldValue(Authenticator.class, "theAuthenticator");
    for(final Field f : Authenticator.class.getDeclaredFields()) {
      if (f.getType().equals(Authenticator.class)) { // Supposedly "theAuthenticator"
        try {
          f.setAccessible(true);
          return (Authenticator)f.get(null);
        } catch (Exception e) {
          preventor.error(e);
        }
      }
    }
    return null;
  }

  /**
   * Recursively removed wrapped {@link Authenticator} loaded in protected ClassLoader.
   * May be needed in case there are multiple CXF applications within the same container.
   */
  @SuppressWarnings("WeakerAccess")
  protected void removeWrappedAuthenticators(final ClassLoaderLeakPreventor preventor,
                                             final Authenticator authenticator) {
    if(authenticator == null)
      return;

    try {
      Class<?> authenticatorClass = authenticator.getClass();
      do {
        for(final Field f : authenticator.getClass().getDeclaredFields()) {
          if(Authenticator.class.isAssignableFrom(f.getType())) {
            try {
              final boolean isStatic = Modifier.isStatic(f.getModifiers()); // In CXF case this should be false
              final Authenticator owner = isStatic ? null : authenticator;
              f.setAccessible(true);
              final Authenticator wrapped = (Authenticator)f.get(owner);
              if(preventor.isLoadedInClassLoader(wrapped)) {
                preventor.warn(Authenticator.class.getName() + ": " + wrapped + ", wrapped by " + authenticator + 
                    ", is loaded by protected ClassLoader");
                f.set(owner, null); // For added safety
              }
              else {
                removeWrappedAuthenticators(preventor, wrapped); // Recurse
              }
            } catch (Exception e) {
              preventor.error(e);
            }
          }
        }
        authenticatorClass = authenticatorClass.getSuperclass();
      } while (authenticatorClass != null && authenticatorClass != Object.class);
    }
    catch (Exception e) { // Should never happen
      preventor.error(e);
    }
  }
}