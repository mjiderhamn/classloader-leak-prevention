package se.jiderhamn.classloader.leak.classloader;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;
import se.jiderhamn.classloader.leak.Leaks;

@RunWith(JUnitClassloaderRunner.class)
public class ClassLoaderTest {

    @Test
    @Leaks(false)
    public void getPackage() {
        Package aPackage = com.classloader.test.CustomClass.class.getPackage();
        Assert.assertNotNull("the package of the class is null", aPackage);
        Assert.assertEquals("com.classloader.test", aPackage.getName());
    }
}
