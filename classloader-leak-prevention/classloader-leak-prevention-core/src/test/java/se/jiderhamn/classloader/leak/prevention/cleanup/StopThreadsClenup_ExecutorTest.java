package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Test case for leaks caused by shared ThreadPool creating new Threads
 * 
 * Treads are started by ExecutorService would have Protection Domain of inheritedAccessControlContext loaded by classLoader
 * 
 * @author Vlad Skarzhevsky
 */
public class StopThreadsClenup_ExecutorTest extends ClassLoaderPreMortemCleanUpTestBase<StopThreadsCleanUp> {

    private static final ExecutorService executor = createSharedExecutor();

    private static ExecutorService createSharedExecutor() {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // initialize executor with one thread
        if (true) {
            executor.submit(new Runnable() {

                @Override
                public void run() {
                }
            });
        }

        return executor;
    }

    @Override
    protected void triggerLeak() throws Exception {
        // Start multiple threads to be sure that ThreadPool created new threads
        for (int i = 0; i < 1; i++) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                }
            });
        }
    }

}
