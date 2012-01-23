package se.jiderhamn;

import org.apache.bcel.classfile.JavaClass;

/** Classloader that redefines classes even if existing in parent */
class RedefiningClassLoader extends org.apache.bcel.util.ClassLoader {
  
  /** Override parents default and include  */
  public static final String[] DEFAULT_IGNORED_PACKAGES = {
          "java.", "javax.", "sun.", "org.junit.", "junit."
  };
  
  /** Set to non-null to indicate it should be ready for garbage collection */
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private ZombieMarker zombieMarker = null;
      

  RedefiningClassLoader(ClassLoader parent) {
    super(parent, DEFAULT_IGNORED_PACKAGES);
  }

  RedefiningClassLoader() {
    super(DEFAULT_IGNORED_PACKAGES);
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
  
  // TODO: Add finalizer that reports garbage collection

  // TODO: Add name
}