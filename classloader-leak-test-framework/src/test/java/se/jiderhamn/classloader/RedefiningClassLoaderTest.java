package se.jiderhamn.classloader;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnitClassloaderRunner.class)
public class RedefiningClassLoaderTest {

    @Test
    @Leaks(false)
    public void getPackage() {
        Package aPackage = com.classloader.test.CustomClass.class.getPackage();
        assertNotNull("Class should have non-null package", aPackage);
        assertEquals("com.classloader.test", aPackage.getName());
    }
}
