package se.jiderhamn.classloader.leak.prevention;

/**
 * Interface to be implemented by {@link PreClassLoaderInitiator}s and {@link ClassLoaderPreMortemCleanUp}s when order
 * is important. The class implementing this interface will define what other implementations it needs to be invoked
 * *after* for correct behaviour. It is the responsibility of {@link ClassLoaderLeakPreventorFactory} to make sure
 * the implementations are ordered correctly. Currently an {@link IllegalStateException} will be thrown (TODO #51)
 * @param <I> The interface that both this class and the dependent classes implements, 
 * i.e. either {@link PreClassLoaderInitiator} or {@link ClassLoaderPreMortemCleanUp}. 
 * 
 * @author Mattias Jiderhamn
 */
public interface MustBeAfter<I> {
  
  /** 
   * Returns an array of classes that, if part of they or any subclass of them are part of the list of 
   * {@link PreClassLoaderInitiator}s/{@link ClassLoaderPreMortemCleanUp}s, needs to be prior to this element in the list.
   */
  Class<? extends I>[] mustBeBeforeMe();
}
