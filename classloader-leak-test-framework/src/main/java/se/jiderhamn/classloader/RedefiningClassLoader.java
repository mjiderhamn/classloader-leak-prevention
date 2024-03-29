package se.jiderhamn.classloader;

import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.JavaClass;

/** Classloader that redefines classes even if existing in parent */
public class RedefiningClassLoader extends org.apache.bcel.util.ClassLoader {

  private static final String DEBUG_SYSTEM_PROPERTY = "ClassLoaderLeakTestFramework.debug";

  /** Override parents default and include  */
  public static final String[] DEFAULT_IGNORED_PACKAGES = {
          "java.", "javax.", "jdk.", "com.sun.", "sun.", "org.w3c", "org.junit.", "junit.",
          "com.apple.eawt.", "com.apple.eio.", "com.apple.laf." // Apple OpenJDK
  };

  /** Set to non-null to indicate it should be ready for garbage collection */
  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  private ZombieMarker zombieMarker = null;
  
  private final String name;

  private final boolean logRedefinitions;

  public RedefiningClassLoader(ClassLoader parent) {
    this(parent, null);
  }

  public RedefiningClassLoader() {
    this((String) null);
  }

  public RedefiningClassLoader(ClassLoader parent, String name) {
    this(parent, name, DEFAULT_IGNORED_PACKAGES);
  }

  RedefiningClassLoader(String name) {
    this(name, DEFAULT_IGNORED_PACKAGES);
  }

  public RedefiningClassLoader(ClassLoader parent, String name, String[] ignoredPackages) {
    super(parent, ignoredPackages);
    this.name = name;
    this.logRedefinitions = isDebugLoggingEnabled();
  }

  RedefiningClassLoader(String name, String[] ignoredPackages) {
    super(ignoredPackages);
    this.name = name;
    this.logRedefinitions = isDebugLoggingEnabled();
  }

  public static boolean isDebugLoggingEnabled() {
    return "true".equals(System.getProperty(DEBUG_SYSTEM_PROPERTY));
  }

  @Override
  protected JavaClass modifyClass(JavaClass clazz) {
    if (logRedefinitions) {
      System.out.println("Loading " + clazz.getClassName() + " in " + this);
    }
    return super.modifyClass(clazz);
  }
  
  /** Mark this class loader as being ready for garbage collection */
  public void markAsZombie() {
    this.zombieMarker = new ZombieMarker();
  }

  @Override
  public String toString() {
    return (name != null) ? (this.getClass().getName() + '[' + name + "]@" + Integer.toHexString(System.identityHashCode(this))) :  
        super.toString();
  }

  @Override
  protected Class<?> loadClass(String class_name, boolean resolve) throws ClassNotFoundException {
    try {
      int i = class_name.lastIndexOf('.');
      if (i != -1) {
        String pkgName = class_name.substring(0, i);
        if (getPackage(pkgName) == null) {
          super.definePackage(pkgName, null, null, null, null,
                  null, null, null);
        }
      }
      return super.loadClass(class_name, resolve);
    }
    catch (ClassFormatException e) {
      throw new RuntimeException("Unable to load class " + class_name, e);
    }
  }
  
}