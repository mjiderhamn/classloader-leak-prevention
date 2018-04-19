package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
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
          DriverManager.deregisterDriver(driver);
        } catch (SQLException e) {
          preventor.error(e);
        }
      }
    }
  }

  /**
   * Add getAllDrivers method. DriverManager.getDrivers() only return the drivers
   * which be load by caller(DriverManagerCleanUp.class). For many scenarios the
   * caller is not the same classloader which load the jdbc drivers.
   * @param preventor
   * @return All drivers in DriverManager's registeredDrivers field,or
   *         DriverManager.getDrivers() if exception occurred
   */
  public Enumeration<Driver> getAllDrivers(ClassLoaderLeakPreventor preventor) {
    Vector<Driver> result = new java.util.Vector<Driver>();
    try {
      ArrayList<?> driverinfos = preventor.getStaticFieldValue(DriverManager.class, "registeredDrivers");
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
}
