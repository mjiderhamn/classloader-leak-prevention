# Classloader Leak Prevention library integration

_This document is about using the Classloader Leak Prevention library in
a non-servlet environment. For general information and use in servlet 
environments, please see the [root README.md](../../README.md)_

Version 2.x of the Classloader Leak Prevention library has been refactored
to allow for use outside a servlet environment, or by all means in a
servlet container (Java EE application server).

# Setting up
What you will want to do is first create a [ClassLoaderLeakPreventorFactory](src/main/java/se/jiderhamn/classloader/leak/prevention/ClassLoaderLeakPreventorFactory.java)
instance, either by using the default constructor that will configure the system
`ClassLoader` (`ClassLoader.getSystemClassLoader()`) to be used for pre-inits,
or provide your own leak safe `ClassLoader` to the constructor.

Make any configurations on the factory instance, i.e. add or remove any
[cleanup](src/main/java/se/jiderhamn/classloader/leak/prevention/ClassLoaderPreMortemCleanUp.java)
or [pre-init](https://github.com/mjiderhamn/classloader-leak-prevention/blob/master/classloader-leak-prevention/classloader-leak-prevention-core/src/main/java/se/jiderhamn/classloader/leak/prevention/PreClassLoaderInitiator.java)
plugins, or change parameters of any of the default plugins.

# Protect ClassLoader
Then for every `ClassLoader` that needs leak protection, create a new
[ClassLoaderLeakPreventor](src/main/java/se/jiderhamn/classloader/leak/prevention/ClassLoaderLeakPreventor.java)
using
```java
classLoaderLeakPreventor = classLoaderLeakPreventorFactory.newLeakPreventor(classLoader);
```
 
Before letting any code execute inside the `ClassLoader` (or at least as
soon as possible), invoke
```java
classLoaderLeakPreventor.runPreClassLoaderInitiators();
```
 
You can reuse the same `ClassLoaderLeakPreventorFactory` for multiple
`ClassLoaders`, but please be aware that any configuration changes made
to plugins of the factory will affect all `ClassLoaderLeakPreventor`s 
created by the factory - both future and existing. If however you add
or remove plugins, that will only affect new `ClassLoaderLeakPreventor`s. 

# Shutting down
When you believe the `ClassLoader` should no longer be used, but be ready
for Garbage Collection, invoke 
```java
classLoaderLeakPreventor.runCleanUps();
```
on the `ClassLoaderLeakPreventor` that corresponds to the `ClassLoader`.

# Example
For an example how to use the framework, feel free to study the
[ClassLoaderLeakPreventorListener](../classloader-leak-prevention-servlet/src/main/java/se/jiderhamn/classloader/leak/prevention/ClassLoaderLeakPreventorListener.java)
in the `classloader-leak-prevention-servlet` module.

# Maven
The module is available in Maven as
```xml
<dependency>
  <groupId>se.jiderhamn.classloader-leak-prevention</groupId>
  <artifactId>classloader-leak-prevention-core</artifactId>
  <version>2.6.1</version>
</dependency>
```