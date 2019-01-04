package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Deregister JDBC drivers loaded by classloader
 *
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
   * DriverManager.getDrivers() only returns the drivers that are loaded by
   * the classloader of the caller. For many scenarios the callers classloader is not the
   * same classloader which load the jdbc drivers.
   * @return All drivers in DriverManager's registeredDrivers field,or
   *         DriverManager.getDrivers() if exception occurred
   */
  public Enumeration<Driver> getAllDrivers(ClassLoaderLeakPreventor preventor) {
	List<Driver> result = new ArrayList<Driver>();
	try {
	  List<?> driverinfos = preventor.getStaticFieldValue(DriverManager.class, "registeredDrivers");
	  for (Object driverinfo : driverinfos) {
		Driver driver = (Driver) preventor.getFieldValue(driverinfo, "driver");
		if (driver == null)
		  throw new NullPointerException("Driver missing in DriverInfo!");
		else
		  result.add(driver);
	  }
	} catch (Exception e) {
	  preventor.error("Error getting registered drivers from DriverManager");
	  return DriverManager.getDrivers();
	}
	return Collections.enumeration(result);
  }

  /**
   * Do the work as DriverManager.deregisterDriver,but this method don't check the
   * Security of the caller's Classloader.If Exception occur, invoke DriverManager.deregisterDriver(driver)
   */
  private void deregisterDriver(ClassLoaderLeakPreventor preventor, final Driver driver) throws Exception {
	synchronized (DriverManager.class) {
	  if (driver == null) {
		return;
	  }
	  try {
		List<?> registeredDrivers = preventor.getStaticFieldValue(DriverManager.class, "registeredDrivers");
		Class<?> innerClass = Class.forName("java.sql.DriverInfo");
		Method actionMethod = null;
		Object innerInstance = null;
		// DriverInfo is Changed in JDK8. So the Code must be backward compatible
		try {
		  actionMethod = innerClass.getDeclaredMethod("action", new Class[0]);
		} catch (NoSuchMethodException e) {
		   // No DriverInfo.action() means we're running JDK prior to 8
 		}
		Constructor<?> ctor = null;
		if (actionMethod != null) {
		  ctor = innerClass.getDeclaredConstructor(Driver.class, actionMethod.getReturnType());
		  ctor.setAccessible(true);
		  innerInstance = ctor.newInstance(driver, null);
		  // Find DriverInfo with DriverAction in list by leveraging DriverInfo.equals()
		  Object driverInfo = registeredDrivers.get(registeredDrivers.indexOf(innerInstance));
		  actionMethod.setAccessible(true);
		  Object driverAction = actionMethod.invoke(driverInfo);
		  if (driverAction != null) {
			Method deregisterMethod = driverAction.getClass().getDeclaredMethod("deregister", new Class[0]);
			if (deregisterMethod != null)
			  deregisterMethod.invoke(driverAction);
		  }
		} else {
		  ctor = innerClass.getDeclaredConstructor(Driver.class);
		  ctor.setAccessible(true);
		  innerInstance = ctor.newInstance(driver);
		}
		registeredDrivers.remove(innerInstance);
	  } catch (Exception e) {
		preventor.error("reflection to deregisterDriver error,invoke the orgin DriverManager.deregisterDriver()");
		DriverManager.deregisterDriver(driver);
	  }
	}
  }
}
