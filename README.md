# Classloader leak prevention library

If you want to avoid the dreaded `java.lang.OutOfMemoryError: PermGen space`, just include this library into your Java EE application, and add this context listener in your `web.xml`:

```xml
<listener>
  <listener-class>se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventor</listener-class>
</listener>
```

It makes sense to keep this listener "outermost" (initializing first, destroying last), so you should normally declare it before any other listeners in web.xml.

To learn more about classloader leaks, their causes, types, ways to find them and known offenders, see blog series here: http://java.jiderhamn.se/category/classloader-leaks/

## Maven
The library is available in Maven Central with the following details:

```xml
<dependency>
  <groupId>se.jiderhamn</groupId>
  <artifactId>classloader-leak-prevention</artifactId>
  <version>1.6.0</version>
</dependency>
```

## Configuration
The context listener has a number of settings that can be configured with context parameters in <code>web.xml</code>:
 
```xml
<context-param>
  <param-name>ClassLoaderLeakPreventor.stopThreads</param-name>
  <param-value>false</param-value>
</context-param>
```
 
 The available settings are
 <table border="1">
   <tr>
     <th>Parameter name</th>
     <th>Default value</th>
     <th>Description</th>
   </tr>
   <tr>
     <td><code>ClassLoaderLeakPreventor.stopThreads</code></td>
     <td><code>true</code></td>
     <td>Should threads tied to the web app classloader be forced to stop at application shutdown?</td>
   </tr>
   <tr>
     <td><code>ClassLoaderLeakPreventor.stopTimerThreads</code></td>
     <td><code>true</code></td>
     <td>Should Timer threads tied to the web app classloader be forced to stop at application shutdown?</td>
   </tr>
   <tr>
     <td><code>ClassLoaderLeakPreventor.executeShutdownHooks</td>
     <td><code>true</code></td>
     <td>Should shutdown hooks registered from the application be executed at application shutdown?</td>
   </tr>
   <tr>
     <td><code>ClassLoaderLeakPreventor.threadWaitMs</td>
     <td nowrap="nowrap"><code>5000</code><br />(5 seconds)</td>
     <td>No of milliseconds to wait for threads to finish execution, before stopping them.</td>
   </tr>
   <tr>
     <td><code>ClassLoaderLeakPreventor.shutdownHookWaitMs</code></td>
     <td nowrap="nowrap"><code>10000</code><br />(10 seconds)</td>
     <td>
       No of milliseconds to wait for shutdown hooks to finish execution, before stopping them.
       If set to -1 there will be no waiting at all, but Thread is allowed to run until finished.
     </td>
   </tr>
 </table>

## Classloader leak detection / test framework

Another part of the project, is a framework that allows the creation of <a href="http://junit.org/">JUnit</a> tests, that confirms classloader leaks in third party APIs. It is also possible to test leak prevention mechanisms to confirm that the leak really is avoided.

For this purpose, I have create a JUnit runner, <code>se.jiderhamn.JUnitClassloaderRunner</code>, which is used to run each test in a separate classloader, that is then attempted to be garbage collected. The default assumption is that the test case will create a leak. A basic example would look like this

```java
package se.jiderhamn.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.JUnitClassloaderRunner;

@RunWith(JUnitClassloaderRunner.class)
public class LeakConfirmingTest {
  
  @Test
  public void triggerLeak() {
    // Do something that triggers the suspected leak
  }
}
```

In case the test passes, it means the code inside the tested method does in fact cause classloader leaks.

In order to confirm that a piece of code does <strong>not</strong> cause leaks, add the <code>se.jiderhamn.Leaks</code> annotation, with a value of `false`.
```java
  @Test
  @Leaks(false) // Should not leak
  public void doesNotTriggerLeak() {
    // Do something that should not trigger any leaks
  }
```
In this case, the test passes only in case a leak isn't triggered.

You can also confirm that a leak workaround has the expected effect, by annotating the class with <code>se.jiderhamn.LeakPreventor</code>, and set its value to a <code>Runnable</code> that fixes the leak.
```java
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(LeakThatCanBeFixedText.Preventor.class)
public class LeakThatCanBeFixedText {
  
  @Test
  public void triggerLeak() {
    // Do something that triggers the suspected leak
  }
  
  public static class Preventor implements Runnable {
    public void run() {
      // Run cleanup code, that fixed the leak and allows the classloader to be GC:ed
    }
  }
}
```
In this case, a successfull test means two things: 1) the <code>@Test</code> method does cause a leak and 2) the leak is prevented by the code in the <code>Preventor</code>. That is, the test will fail if either there is no leak to begin with, or the leak is still present after executing the <code>Preventor</code>.

NOTE: It is not yet determined whether multiple test cases in the same class works, so you should stick to one single <code>@Test</code> method per class for now.

NOTE: The test framework is not included in the runtime JAR - you need to grab it <a href="https://github.com/mjiderhamn/classloader-leak-prevention">from GitHub</a>.

## License

This project is licensed under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2 license</a>, which allows you to include modified versions of the code in your distributed software, without having to release your source code.