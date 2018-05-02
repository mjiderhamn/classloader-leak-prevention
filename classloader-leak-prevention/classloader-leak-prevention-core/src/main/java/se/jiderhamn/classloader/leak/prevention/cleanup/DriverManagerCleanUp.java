package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Driver;
import java.sql.DriverAction;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Deregister JDBC drivers loaded by classloader
 * @author Mattias Jiderhamn
 * @author SONGQQ 2018-4-19
 */
public class DriverManagerCleanUp implements ClassLoaderPreMortemCleanUp {
  @Override
  public void cleanUp(ClassLoaderLeakPreventor preventor) {
    final Enumeration<Driver> allDrivers = getAllDrivers(preventor);
    while (allDrivers.hasMoreElements()) {
      final Driver driver = allDrivers.nextElement();
      if (preventor.isLoadedInClassLoader(driver)) {
        try {
          preventor.warn("JDBC driver loaded by protected ClassLoader deregistered: " + driver.getClass());
  		  deregisterDriver(preventor, driver);
        } catch (Exception e) {
          preventor.error(e);
        }
      }
    }
  }

  /**
   * DriverManager.getDrivers() only return the drivers
   * which be load by caller(DriverManagerCleanUp.class). For many scenarios the
   * caller is not the same classloader which load the jdbc drivers.
   * @param preventor
   * @return All drivers in DriverManager's registeredDrivers field,or
   *         DriverManager.getDrivers() if exception occurred
   */
  public Enumeration<Driver> getAllDrivers(ClassLoaderLeakPreventor preventor) {
    Vector<Driver> result = new java.util.Vector<Driver>();
    try {
      List<?> driverinfos = preventor.getStaticFieldValue(DriverManager.class, "registeredDrivers");
      for (Object driverinfo : driverinfos) {
        Driver driver = (Driver) preventor.getFieldValue(driverinfo, "driver");
        if (driver == null)
          throw new NullPointerException();
        else
          result.addElement(driver);
      }
    } catch (Exception e) {
      preventor.warn("get All registeredDrivers Exception");
      return DriverManager.getDrivers();
    }
    return result.elements();
  }

  /**
   *Do the work as DriverManager.deregisterDriver,but this method don't check the Security of the caller's Classloader
   */
  private void deregisterDriver(ClassLoaderLeakPreventor preventor, final Driver driver) throws Exception {
	synchronized (DriverManager.class) {
	  if (driver == null) {
		return;
	  }
	  try {
		List<?> registeredDrivers = preventor.getStaticFieldValue(DriverManager.class, "registeredDrivers");
		Class<?> innerClass = Class.forName("java.sql.DriverInfo");
		Constructor<?> ctor = innerClass.getDeclaredConstructor(Driver.class, DriverAction.class);
		ctor.setAccessible(true);
		Object innerInstance = ctor.newInstance(driver, null);
		Object di = registeredDrivers.get(registeredDrivers.indexOf(innerInstance));
		Method actionMethod = innerClass.getDeclaredMethod("action", new Class[0]);
		actionMethod.setAccessible(true);
		DriverAction da = (DriverAction) actionMethod.invoke(di);
		if (da != null) {
		  da.deregister();
		}
		registeredDrivers.remove(innerInstance);
	  } catch (Exception e) {
		preventor.error("deregisterDriver error");
		throw e;
	  }
	}
  }
}
