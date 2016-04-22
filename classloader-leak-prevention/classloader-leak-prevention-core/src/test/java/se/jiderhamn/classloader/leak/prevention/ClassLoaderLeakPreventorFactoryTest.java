package se.jiderhamn.classloader.leak.prevention;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

/**
 * Test cases for {@link ClassLoaderLeakPreventorFactory}
 * @author Mattias Jiderhamn
 */
public class ClassLoaderLeakPreventorFactoryTest {
  
  /** Test that {@link MustBeAfter} has expected effect on {@link ClassLoaderPreMortemCleanUp}s */
  @Test(expected = IllegalStateException.class) // TODO #51
  public void cleanUpMustBeAfter() {
    final List<ClassLoaderPreMortemCleanUp> executionOrder = new ArrayList<ClassLoaderPreMortemCleanUp>();
    
    final RecordingCleanUp unordered1 = new RecordingCleanUp(executionOrder) { };
    final RecordingCleanUp unordered2 = new RecordingCleanUp(executionOrder) { };
    final Foo foo = new Foo(executionOrder);
    final Bar bar = new Bar(executionOrder);
    final AfterFoo afterFoo1 = new AfterFoo(executionOrder) { };
    final AfterFoo afterFoo2 = new AfterFoo(executionOrder) { };
    final AfterBar afterBar = new AfterBar(executionOrder);
    final AfterFooAndBar afterFooAndBar = new AfterFooAndBar(executionOrder);
    
    ClassLoaderLeakPreventorFactory factory = new ClassLoaderLeakPreventorFactory();
    factory.addCleanUp(unordered2);
    factory.addCleanUp(bar);
    factory.addCleanUp(afterFooAndBar);
    factory.addCleanUp(afterBar);
    factory.addCleanUp(unordered1);
    factory.addCleanUp(afterFoo2);
    factory.addCleanUp(afterFoo1);
    factory.addCleanUp(foo);
    
    // TODO #51
    
    factory.newLeakPreventor().runCleanUps();
    
    // Make sure imposed order is achieved, while retaining order among moved elements
    assertThat(executionOrder, contains((ClassLoaderPreMortemCleanUp) 
        unordered2, 
        bar, 
        afterBar, 
        unordered1, 
        foo, 
        afterFooAndBar, // Moved 
        afterFoo2,      // Moved  
        afterFoo1));    // Moved
    
    ///////////
    // Subclass
    
    executionOrder.clear();
    
    final Foo fooSubClass = new Foo(executionOrder) { };
    
    factory.addCleanUp(fooSubClass);

    factory.newLeakPreventor().runCleanUps();

    // Make sure imposed order is achieved, while retaining order among moved elements
    assertThat(executionOrder, contains((ClassLoaderPreMortemCleanUp) 
        unordered2, 
        bar, 
        afterBar, 
        unordered1, 
        foo,
        fooSubClass,    // Inserted
        afterFooAndBar,
        afterFoo2,  
        afterFoo1));
  }
  
  @Test(expected = IllegalStateException.class)
  public void circularMustBeAfter() {
    ClassLoaderLeakPreventorFactory factory = new ClassLoaderLeakPreventorFactory();
    factory.addCleanUp(new Circle1());
    factory.addCleanUp(new Circle2());
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /** Base class for {@link ClassLoaderPreMortemCleanUp}s that will record their execution */
  private abstract static class RecordingCleanUp implements ClassLoaderPreMortemCleanUp {
    
    private final List<ClassLoaderPreMortemCleanUp> cleanUps;

    RecordingCleanUp(List<ClassLoaderPreMortemCleanUp> cleanUps) {
      this.cleanUps = cleanUps;
    }

    @Override
    public void cleanUp(ClassLoaderLeakPreventor preventor) {
      cleanUps.add(this);
    }

    @Override
    public String toString() {
      return this.getClass().getName() + "@" + System.identityHashCode(this);
    }
  }
  
  private static class Foo extends RecordingCleanUp {
    Foo(List<ClassLoaderPreMortemCleanUp> cleanUps) {
      super(cleanUps);
    }
  }

  private static class Bar extends RecordingCleanUp {
    Bar(List<ClassLoaderPreMortemCleanUp> cleanUps) {
      super(cleanUps);
    }
  }

  private static class AfterFoo extends RecordingCleanUp implements MustBeAfter<ClassLoaderPreMortemCleanUp> {
    AfterFoo(List<ClassLoaderPreMortemCleanUp> cleanUps) {
      super(cleanUps);
    }

    @Override
    public Class<? extends ClassLoaderPreMortemCleanUp>[] mustBeBeforeMe() {
      return new Class[] {Foo.class};
    }
  }

  private static class AfterBar extends RecordingCleanUp implements MustBeAfter<ClassLoaderPreMortemCleanUp> {
    AfterBar(List<ClassLoaderPreMortemCleanUp> cleanUps) {
      super(cleanUps);
    }

    @Override
    public Class<? extends ClassLoaderPreMortemCleanUp>[] mustBeBeforeMe() {
      return new Class[] {Bar.class};
    }
  }

  private static class AfterFooAndBar extends RecordingCleanUp implements MustBeAfter<ClassLoaderPreMortemCleanUp> {
    AfterFooAndBar(List<ClassLoaderPreMortemCleanUp> cleanUps) {
      super(cleanUps);
    }

    @Override
    public Class<? extends ClassLoaderPreMortemCleanUp>[] mustBeBeforeMe() {
      return new Class[] {Foo.class, Bar.class};
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  private static class Circle1 extends RecordingCleanUp implements MustBeAfter<ClassLoaderPreMortemCleanUp> {
    Circle1() {
      super(null);
    }

    @Override
    public Class<? extends ClassLoaderPreMortemCleanUp>[] mustBeBeforeMe() {
      return new Class[] {Circle2.class};
    }
  }
  
  private static class Circle2 extends RecordingCleanUp implements MustBeAfter<ClassLoaderPreMortemCleanUp> {
    Circle2() {
      super(null);
    }

    @Override
    public Class<? extends ClassLoaderPreMortemCleanUp>[] mustBeBeforeMe() {
      return new Class[] {Circle2.class};
    }
  }
  
}