package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Workaround for leak caused by Mojarra JSF implementation if included in the container.
 * See <a href="http://java.net/jira/browse/JAVASERVERFACES-2746">JAVASERVERFACES-2746</a>
 * @author Mattias Jiderhamn
 */
public class JavaServerFaces2746CleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    /*
     Note that since a WeakHashMap is used, it is not the map key that is the problem. However the value is a
     Map with java.beans.PropertyDescriptor as value, and java.beans.PropertyDescriptor has a Hashtable in which
     a class is put with "type" as key. This class may have been loaded by the protected ClassLoader.

     One example case is the class org.primefaces.component.menubutton.MenuButton that points to a Map with a key 
     "model" whose PropertyDescriptor.table has key "type" with the class org.primefaces.model.MenuModel as its value.

     For performance reasons however, we'll only look at the top level key and remove any that has been loaded by 
     protected ClassLoader.     
     */
    
    Object o = preventor.getStaticFieldValue("javax.faces.component.UIComponentBase", "descriptors"); // Non-static as of JSF 2.2.5
    if(o instanceof WeakHashMap) {
      WeakHashMap<?, ?> descriptors = (WeakHashMap<?, ?>) o;
      final Set<Class<?>> toRemove = new HashSet<Class<?>>();
      for(Object key : descriptors.keySet()) {
        if(key instanceof Class && preventor.isLoadedByClassLoader((Class<?>)key)) {
          // For performance reasons, remove all classes loaded by protected ClassLoader
          toRemove.add((Class<?>) key);
          
          // This would be more correct, but presumably slower
          /*
          Map<String, PropertyDescriptor> m = (Map<String, PropertyDescriptor>) descriptors.get(key);
          for(Map.Entry<String,PropertyDescriptor> entry : m.entrySet()) {
            Object type = entry.getValue().getValue("type"); // Key constant javax.el.ELResolver.TYPE
            if(type instanceof Class && isLoadedByWebApplication((Class)type)) {
              toRemove.add((Class) key); 
            }
          }
          */
        }
      }
      
      if(! toRemove.isEmpty()) {
        preventor.info("Removing " + toRemove.size() + " classes from Mojarra descriptors cache");
        for(Class<?> clazz : toRemove) {
          descriptors.remove(clazz);
        }
      }
    }    
  }
}