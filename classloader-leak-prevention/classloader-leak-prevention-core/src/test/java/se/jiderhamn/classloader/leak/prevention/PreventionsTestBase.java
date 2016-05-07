package se.jiderhamn.classloader.leak.prevention;

import java.lang.reflect.ParameterizedType;
import java.util.Collections;

/**
 * Base class for test cases testing {@link PreClassLoaderInitiator} and {@link ClassLoaderPreMortemCleanUp} implementations.
 * @author Mattias Jiderhamn
 */
public abstract class PreventionsTestBase<C> {

  /** 
   * Get an instance of the implementation under test. Will use the generics parameter type information.
   */
  protected C getTestedImplementation() throws IllegalAccessException, InstantiationException {
    ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();
    Object actualType = genericSuperclass.getActualTypeArguments()[genericSuperclass.getActualTypeArguments().length - 1];
    
    Class<C> cleanUpImplClass = (actualType instanceof ParameterizedType) ? 
        (Class<C>) ((ParameterizedType)actualType).getRawType() :
        (Class<C>) actualType;
      
    return cleanUpImplClass.newInstance();
  }

  /**
   * Concrete tests may override this method, in case they to provide a specific {@link ClassLoaderLeakPreventor}
   * to the {@link ClassLoaderPreMortemCleanUp}.
   * @return
   */
  protected ClassLoaderLeakPreventor getClassLoaderLeakPreventor() {
    return new ClassLoaderLeakPreventor(getLeakSafeClassLoader(),
        getClass().getClassLoader(),
        new StdLogger(), 
        Collections.<PreClassLoaderInitiator>emptyList(),
        Collections.<ClassLoaderPreMortemCleanUp>emptyList());
  }

  /** 
   * Get {@link ClassLoader} to be used as the {@link ClassLoaderLeakPreventor#leakSafeClassLoader} of the 
   * {@link ClassLoaderLeakPreventor}. This is normally the parent of the classloader of the test class.
   */
  protected ClassLoader getLeakSafeClassLoader() {
    return getClass().getClassLoader().getParent();
  }

}