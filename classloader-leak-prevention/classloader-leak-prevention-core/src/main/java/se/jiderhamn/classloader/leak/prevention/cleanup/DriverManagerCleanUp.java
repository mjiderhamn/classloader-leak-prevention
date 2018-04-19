package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor;
import se.jiderhamn.classloader.leak.prevention.ClassLoaderPreMortemCleanUp;

/**
 * Deregister JDBC drivers loaded by classloader
 *
 *
 * @author Mattias Jiderhamn
 * @author SONGQQ 2018-4-19
 *
 */
public class DriverManagerCleanUp implements ClassLoaderPreMortemCleanUp {
    @Override
    public void cleanUp(ClassLoaderLeakPreventor preventor) {
        final List<Driver> driversToDeregister = new ArrayList<Driver>();
        final Enumeration<Driver> allDrivers = getAllDrivers(preventor);
        while (allDrivers.hasMoreElements()) {
            final Driver driver = allDrivers.nextElement();
            if (preventor.isLoadedInClassLoader(driver)) // Should be true for all returned by DriverManager.getDrivers()
                driversToDeregister.add(driver);
        }

        for (Driver driver : driversToDeregister) {
            try {
                preventor.warn("JDBC driver loaded by protected ClassLoader deregistered: " + driver.getClass());
                DriverManager.deregisterDriver(driver);
            }
            catch (SQLException e) {
                preventor.error(e);
            }
        }
    }

    /**
     * add getAllDrivers method. DriverManager.getDrivers() only return the drivers which be load by caller(DriverManagerCleanUp.class).
     * For many scenarios the Caller is not the same classloader which load the jdbc Drivers
     *
     * @param preventor
     * @return
     */
    public java.util.Enumeration<Driver> getAllDrivers(ClassLoaderLeakPreventor preventor) {
        java.util.Vector<Driver> result = new java.util.Vector<Driver>();
        try {
            CopyOnWriteArrayList<?> driverinfos = preventor.getStaticFieldValue(DriverManager.class, "registeredDrivers");
            for (Object driverinfo : driverinfos) {
                Driver driver = (Driver) preventor.getFieldValue(driverinfo, "driver");
                result.addElement(driver);
            }
        }
        catch (Exception e) {
            preventor.warn("get All registeredDrivers Exception ");
            preventor.error(e);

        }
        return (result.elements());

    }
}
