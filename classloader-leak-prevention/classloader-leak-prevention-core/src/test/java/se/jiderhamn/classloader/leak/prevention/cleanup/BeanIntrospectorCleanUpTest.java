package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.beans.Introspector;

/**
 * Test case for {@link BeanIntrospectorCleanUp}
 */
public class BeanIntrospectorCleanUpTest extends ClassLoaderPreMortemCleanUpTestBase<BeanIntrospectorCleanUp> {

    @Override
    protected void triggerLeak() throws Exception {
        Introspector.getBeanInfo(Bean.class);
    }

    protected class Bean {
        private int dummyField;

        public int getDummyField() {
            return dummyField;
        }

        public void setDummyField(int dummyField) {
            this.dummyField = dummyField;
        }
    }
}