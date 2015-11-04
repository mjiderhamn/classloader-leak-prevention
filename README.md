# Classloader Leak Prevention library
[![Build Status](https://travis-ci.org/mjiderhamn/classloader-leak-prevention.svg)](http://travis-ci.org/mjiderhamn/classloader-leak-prevention)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.jiderhamn/classloader-leak-prevention/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.jiderhamn/classloader-leak-prevention/)
[![License](https://img.shields.io/badge/license-Apache2-blue.svg?style=flat)](https://github.com/mjiderhamn/classloader-leak-prevention/blob/master/LICENSE.txt)

If you want to avoid the dreaded `java.lang.OutOfMemoryError: Metaspace` / `PermGen space`, just include this library into your Java EE application, and add this context listener in your `web.xml`:

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
  <version>1.15.2</version>
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

The test framework now has its own Maven module and its own documentation, see [classloader-leak-test-framework](classloader-leak-test-framework)

## License

This project is licensed under the [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0), which allows you to include modified versions of the code in your distributed software, without having to release your source code.
