package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.IIOServiceProvider;
import javax.imageio.spi.ServiceRegistry;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Unregister ImageIO Service Provider loaded by the protected ClassLoader
 * @author Thomas Scheffler (1.x version)
 * @author Mattias Jiderhamn
 */
public class IIOServiceProviderCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(final ClassLoaderLeakPreventor preventor) {
    IIORegistry registry = IIORegistry.getDefaultInstance();
    Iterator<Class<?>> categories = registry.getCategories();
    ServiceRegistry.Filter classLoaderFilter = new ServiceRegistry.Filter() {
      @Override
      public boolean filter(Object provider) {
        //remove all service provider loaded by the current ClassLoader
        return preventor.isLoadedInClassLoader(provider);
      }
    };
    while (categories.hasNext()) {
      @SuppressWarnings("unchecked")
      Class<IIOServiceProvider> category = (Class<IIOServiceProvider>) categories.next();
      Iterator<IIOServiceProvider> serviceProviders = registry.getServiceProviders(
          category,
          classLoaderFilter, true);
      if (serviceProviders.hasNext()) {
        //copy to list
        List<IIOServiceProvider> serviceProviderList = new ArrayList<IIOServiceProvider>();
        while (serviceProviders.hasNext()) {
          serviceProviderList.add(serviceProviders.next());
        }
        for (IIOServiceProvider serviceProvider : serviceProviderList) {
          preventor.warn("ImageIO " + category.getSimpleName() + " service provider deregistered: "
            + serviceProvider.getDescription(Locale.ROOT));
          registry.deregisterServiceProvider(serviceProvider);
        }
      }
    }
    
  }
}