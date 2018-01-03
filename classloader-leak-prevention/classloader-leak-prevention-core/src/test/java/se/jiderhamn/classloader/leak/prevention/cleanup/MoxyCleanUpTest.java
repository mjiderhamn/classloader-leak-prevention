package se.jiderhamn.classloader.leak.prevention.cleanup;

import org.eclipse.persistence.jaxb.compiler.CompilerHelper;
import se.jiderhamn.classloader.PackagesLoadedOutsideClassLoader;

/**
 * To trigger leak {@link org.eclipse.persistence.jaxb.compiler.CompilerHelper} must be loaded by leaking classloader
 * {@link org.eclipse.persistence.jaxb.javamodel.Helper} and/or {@link org.eclipse.persistence.jaxb.compiler.Property} must not
 * @author Mattias Jiderhamn
 */
@PackagesLoadedOutsideClassLoader(packages = {"org.eclipse.persistence.jaxb.javamodel"}, addToDefaults = true)
public class MoxyCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<MoxyCleanUp> {
  @Override
  protected void triggerLeak() throws Exception {
     final ClassLoader leakSafeCL = Thread.currentThread().getContextClassLoader().getParent();
    // Class.forName("org.eclipse.persistence.jaxb.javamodel.Helper", true, leakSafeCL);
    // Class.forName("org.eclipse.persistence.jaxb.compiler.Property", true, leakSafeCL);
    Class.forName("org.eclipse.persistence.jaxb.compiler.CompilerHelper", true, leakSafeCL);
    
    CompilerHelper.getXmlBindingsModelContext();
    
    // This prevention needs to be run in addition
    new ResourceBundleCleanUp().cleanUp(getClassLoaderLeakPreventor());
  }
}