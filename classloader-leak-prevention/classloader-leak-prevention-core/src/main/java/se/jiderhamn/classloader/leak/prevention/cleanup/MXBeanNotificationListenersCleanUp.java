package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.management.PlatformManagedObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import javax.management.*;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Unregister MBeans, MXBean {@link NotificationListener}s/{@link NotificationFilter}s/handbacks loaded by the 
 * protected class loader
 * @author Mattias Jiderhamn
 */
public class MXBeanNotificationListenersCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Class<?> platformComponentClass = preventor.findClass("java.lang.management.PlatformComponent");
    final Method getMXBeans = preventor.findMethod(platformComponentClass, "getMXBeans", Class.class);
    if(platformComponentClass != null && getMXBeans != null) { 
      final Class<?> notificationEmitterSupportClass = preventor.findClass("sun.management.NotificationEmitterSupport");
      final Field listenerListField = preventor.findField(notificationEmitterSupportClass, "listenerList");

      final Class<?> listenerInfoClass = preventor.findClass("sun.management.NotificationEmitterSupport$ListenerInfo");
      final Field listenerField = preventor.findField(listenerInfoClass, "listener");
      final Field filterField = preventor.findField(listenerInfoClass, "filter");
      final Field handbackField = preventor.findField(listenerInfoClass, "handback");
      
      final Class<?> listenerWrapperClass = preventor.findClass("com.sun.jmx.interceptor.DefaultMBeanServerInterceptor$ListenerWrapper");

      final boolean canProcessNotificationEmitterSupport =
          listenerListField != null && listenerInfoClass != null && 
          listenerField != null && filterField != null && handbackField != null;

      if(! canProcessNotificationEmitterSupport)
        preventor.warn("Unable to unregister NotificationEmitterSupport listeners, because details could not be found using reflection");

      final Object[] platformComponents = platformComponentClass.getEnumConstants();
      if(platformComponents != null) {
        for(Object platformComponent : platformComponents) {
          List<PlatformManagedObject> mxBeans = null;
          try {
            mxBeans = (List<PlatformManagedObject>) getMXBeans.invoke(platformComponent, (Class<?>) null);
          }
          catch (IllegalAccessException ex) {
            preventor.error(ex);
          }
          catch (InvocationTargetException ex) {
            preventor.error(ex);
          }

          if(mxBeans != null) { // We were able to retrieve MXBeans for this PlatformComponent
            for(PlatformManagedObject mxBean : mxBeans) {
              if(mxBean instanceof NotificationEmitter) { // The MXBean may have NotificationListeners
                if(canProcessNotificationEmitterSupport && notificationEmitterSupportClass.isAssignableFrom(mxBean.getClass())) {
                  final List<? /* NotificationEmitterSupport.ListenerInfo */> listenerList = preventor.getFieldValue(listenerListField, mxBean);
                  if(listenerList != null) {
                    for(Object listenerInfo : listenerList) { // Loop all listeners
                      final NotificationListener listener = preventor.getFieldValue(listenerField, listenerInfo);
                      final NotificationListener rawListener = unwrap(preventor, listenerWrapperClass, listener);
                      final NotificationFilter filter = preventor.getFieldValue(filterField, listenerInfo);
                      final Object handback = preventor.getFieldValue(handbackField, listenerInfo);

                      if(preventor.isLoadedInClassLoader(rawListener) || preventor.isLoadedInClassLoader(filter) || preventor.isLoadedInClassLoader(handback)) {
                        preventor.warn(((listener == rawListener) ? "Listener '" : "Wrapped listener '") + listener + 
                            "' (or its filter or handback) of MXBean " + mxBean + 
                            " of PlatformComponent " + platformComponent + " was loaded in protected ClassLoader; removing");
                        // This is safe, as the implementation (as of this writing) works with a copy, not altering the original
                        try {
                          ((NotificationEmitter) mxBean).removeNotificationListener(listener, filter, handback);
                        }
                        catch (ListenerNotFoundException e) { // Should never happen
                          preventor.error(e);
                        }
                      }
                    }
                  }
                }
                else if(mxBean instanceof NotificationBroadcasterSupport) { // Unlikely case
                  unregisterNotificationListeners(preventor, (NotificationBroadcasterSupport) mxBean, listenerWrapperClass);
                }
              }
            }
          }
        }
      }
    }  
  }

  /** 
   * Unregister {@link NotificationListener}s from subclass of {@link NotificationBroadcasterSupport}, if listener,
   * filter or handback is loaded by the protected ClassLoader.
   */
  protected void unregisterNotificationListeners(ClassLoaderLeakPreventor preventor, NotificationBroadcasterSupport mBean,
                                                 final Class<?> listenerWrapperClass) {
    final Field listenerListField = preventor.findField(NotificationBroadcasterSupport.class, "listenerList");
    if(listenerListField != null) {
      final Class<?> listenerInfoClass = preventor.findClass("javax.management.NotificationBroadcasterSupport$ListenerInfo");

      final List<? /*javax.management.NotificationBroadcasterSupport.ListenerInfo*/> listenerList =
          preventor.getFieldValue(listenerListField, mBean);

      if(listenerList != null) {
        final Field listenerField = preventor.findField(listenerInfoClass, "listener");
        final Field filterField = preventor.findField(listenerInfoClass, "filter");
        final Field handbackField = preventor.findField(listenerInfoClass, "handback");
        for(Object listenerInfo : listenerList) {
          final NotificationListener listener = preventor.getFieldValue(listenerField, listenerInfo);
          final NotificationListener rawListener = unwrap(preventor, listenerWrapperClass, listener);
          final NotificationFilter filter = preventor.getFieldValue(filterField, listenerInfo);
          final Object handback = preventor.getFieldValue(handbackField, listenerInfo);
          
          if(preventor.isLoadedInClassLoader(rawListener) || preventor.isLoadedInClassLoader(filter) || preventor.isLoadedInClassLoader(handback)) {
            preventor.warn(((listener == rawListener) ? "Listener '" : "Wrapped listener '") + listener + 
                "' (or its filter or handback) of MBean " + mBean + 
                " was loaded in protected ClassLoader; removing");
            // This is safe, as the implementation works with a copy, not altering the original
            try {
              mBean.removeNotificationListener(listener, filter, handback);
            }
            catch (ListenerNotFoundException e) { // Should never happen
              preventor.error(e);
            }
          }
        }
      }
    }
  }

  /** Unwrap {@link NotificationListener} wrapped by {@link com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.ListenerWrapper} */
  private NotificationListener unwrap(ClassLoaderLeakPreventor preventor, Class<?> listenerWrapperClass, NotificationListener listener) {
    if(listenerWrapperClass != null && listenerWrapperClass.isInstance(listener)) {
      return preventor.getFieldValue(listener, "listener"); // Unwrap
    }
    else 
      return listener;
  }

}