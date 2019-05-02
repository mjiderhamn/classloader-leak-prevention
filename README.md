# Classloader Leak Prevention library
[![Build Status](https://travis-ci.org/mjiderhamn/classloader-leak-prevention.svg?branch=master)](http://travis-ci.org/mjiderhamn/classloader-leak-prevention)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.jiderhamn.classloader-leak-prevention/classloader-leak-prevention-servlet3/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.jiderhamn.classloader-leak-prevention/classloader-leak-prevention-servlet3/)
[![License](https://img.shields.io/badge/license-Apache2-blue.svg?style=flat)](https://github.com/mjiderhamn/classloader-leak-prevention/blob/master/LICENSE.txt)

If you want to avoid the dreaded `java.lang.OutOfMemoryError: Metaspace` / `PermGen space`, 
just include this library into your Java EE application and it should take care of the rest!

To learn more about classloader leaks, their causes, types, ways to find them and known offenders, see blog series here: http://java.jiderhamn.se/category/classloader-leaks/

## Servlet 3.0+
In a Servlet 3.0+ environment, all you need to do is include this Maven 
dependency in your `.war`:
```xml
<dependency>
  <groupId>se.jiderhamn.classloader-leak-prevention</groupId>
  <artifactId>classloader-leak-prevention-servlet3</artifactId>
  <version>2.7.0</version>
</dependency>
```

If you run into problems with the Servlet 3.0 module, try the Servlet 2.5 alternative below.
Since the [Servlet spec does not guarantee the order of `ServletContainerInitializer`s](https://java.net/jira/browse/SERVLET_SPEC-79),
it means this library may not initialize first and clean up last in case you have other Servlet 3.0 dependencies, which
could lead to unexpected behaviour.

## Servlet 2.5 (and earlier)
For Servlet 2.5 (and earlier) environments, you need to use a different
Maven dependency (notice the difference in `artifactId`):
```xml
<dependency>
  <groupId>se.jiderhamn.classloader-leak-prevention</groupId>
  <artifactId>classloader-leak-prevention-servlet</artifactId>
  <version>2.7.0</version>
</dependency>
```

You also have to add this to your `web.xml`:
```xml
<listener>
  <listener-class>se.jiderhamn.classloader.leak.prevention.ClassLoaderLeakPreventorListener</listener-class>
</listener>
```
_Note that the name of the listener class has changed since 1.x!_

It makes sense to keep this listener "outermost" (initializing first, 
destroying last), so you should normally declare it before any other 
listeners in `web.xml`.

## Configuration
The context listener used in both cases has a number of settings that can be configured with context parameters in `web.xml`:
 
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
     <td><code>ClassLoaderLeakPreventor.startOracleTimeoutThread</td>
     <td><code>true</code></td>
     <td>
       Should the <code>oracle.jdbc.driver.OracleTimeoutPollingThread</code> thread be forced to start with system ClassLoader,
       in case Oracle JDBC driver is present? This is normally a good idea, but can be disabled in case the Oracle JDBC
       driver is not used even though it is on the classpath.
     </td>
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

The test framework has its own Maven module and its own documentation, see [classloader-leak-test-framework](classloader-leak-test-framework).

## Integration

For non-servlet environments, please see the documentation for the [core module](classloader-leak-prevention/classloader-leak-prevention-core).

## License

This project is licensed under the [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0), which allows you to include modified versions of the code in your distributed software, without having to release your source code.
