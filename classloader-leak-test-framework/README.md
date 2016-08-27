# Classloader Leak test framework

Stand-alone test framework for detecting and/or verifying the existence or non-existence of Java ClassLoader leaks.
It is also possible to test leak prevention mechanisms to confirm that the leak really is avoided.

The [ClassLoader Leak Prevention library](../README.md) is supposed to be used to avoid the leaks causing problems during runtime.
The ClassLoader Leak Prevention library uses this test framework to confirm the effectiveness of it's leak countermeasures. 

The framework is an built upon [JUnit](http://junit.org/) and supplies a `se.jiderhamn.classloader.leak.JUnitClassloaderRunner`,
which is used to run each test in a separate classloader, that is then attempted to be garbage collected. The default 
assumption is that the test case will cause a classloader leak. A basic example would look like this

```java
package se.jiderhamn.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;

@RunWith(JUnitClassloaderRunner.class)
public class LeakConfirmingTest {
  
  @Test
  public void triggerLeak() {
    // Do something that triggers the suspected leak
  }
}
```

In case the test passes, it means the code inside the tested method does in fact cause classloader leaks.

In order to confirm that a piece of code does *not* cause leaks, add the `se.jiderhamn.classloader.leak.Leaks` annotation,
with a value of `false`.
```java
  @Test
  @Leaks(false) // Should not leak
  public void doesNotTriggerLeak() {
    // Do something that should not trigger any leaks
  }
```
In this case, the test passes only in case a leak isn't triggered.

If you want to execute some code outside the per-test classloader, you can do that in an 
[`@Before`](http://junit.sourceforge.net/javadoc/org/junit/Before.html) annotated method.

## Heap dump
In case you want a heap dump automatically generated when a leak is detected, you can use `@Leaks(dumpHeapOnError = true)` 
and then watch stdout for the name of the heap dump file.

In the heap dump, you can look for instances of `se.jiderhamn.classloader.ZombieMarker` and track their path to GC root.

## Verifying prevention measures

You can also confirm that a leak workaround has the expected effect, by annotating the class with 
`se.jiderhamn.classloader.leak.LeakPreventor`, and set its value to a `Runnable` that fixes the leak.
```java
@RunWith(JUnitClassloaderRunner.class)
@LeakPreventor(LeakThatCanBeFixedTest.Preventor.class)
public class LeakThatCanBeFixedTest {
  
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
In this case, a successful test means two things:
1. the `@Test` method does cause a leak
2. the leak is prevented by the code in the `Preventor`
That is, the test will fail if either there is no leak to begin with, or the leak is still present after executing the `Preventor`.

NOTE: It is not yet determined whether multiple test cases in the same class works, so you should stick to one single `@Test` method per class for now.

### Maven
The test framework of the library is available in Maven Central with the following details:

```xml
<dependency>
  <groupId>se.jiderhamn</groupId>
  <artifactId>classloader-leak-test-framework</artifactId>
  <version>1.1.1</version>
</dependency>
```

## License

This project is licensed under the [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0), which allows you to include modified versions of the code in your distributed software, without having to release your source code.
