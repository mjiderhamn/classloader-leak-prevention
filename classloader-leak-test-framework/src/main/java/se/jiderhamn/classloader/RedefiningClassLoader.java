package se.jiderhamn.classloader;

import org.apache.bcel.classfile.JavaClass;

/** Classloader that redefines classes even if existing in parent */
public class RedefiningClassLoader extends org.apache.bcel.util.ClassLoader {
  
  /** Override parents default and include  */
  public static final String[] DEFAULT_IGNORED_PACKAGES = {
          "java.", "javax.", "com.sun.", "sun.", "org.w3c", "org.junit.", "junit."
  };
  
  /** Set to non-null to indicate it should be ready for garbage collection */
  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  private ZombieMarker zombieMarker = null;
  
  private final String name;

  public RedefiningClassLoader(ClassLoader parent) {
    this(parent, null);
  }

  public RedefiningClassLoader() {
    this((String) null);
  }

  public RedefiningClassLoader(ClassLoader parent, String name) {
    super(parent, DEFAULT_IGNORED_PACKAGES);
    this.name = name;
  }

  RedefiningClassLoader(String name) {
    super(DEFAULT_IGNORED_PACKAGES);
    this.name = name;
  }

  @Override
  protected JavaClass modifyClass(JavaClass clazz) {
    System.out.println("Loading " + clazz.getClassName() + " in " + this); // TODO: turn debugging on/off
    return super.modifyClass(clazz);
  }
  
  /** Mark this class loader as being ready for garbage collection */
  public void markAsZombie() {
    this.zombieMarker = new ZombieMarker();
  }

  @Override
  protected void finalize() throws Throwable {
    System.out.println(this + " is being finalized");
    
    // TODO: Report?
  }

  @Override
  public String toString() {
    return (name != null) ? (this.getClass().getName() + '[' + name + "]@" + Integer.toHexString(System.identityHashCode(this))) :  
        super.toString();
  }
}