package se.jiderhamn.classloader.leak.prevention.support;

/**
 * A few helper functions to use as a condition for JDK-dependant unit tests.
 */
public class JavaVersion {

    public static final String JAVA_SPECIFICATION_VERSION = System.getProperty("java.specification.version");

    /**
     * Is {@code true} if this is Java version 1.6 (also 1.6.x versions).
     */
    public static final boolean IS_JAVA_1_6 = isJavaVersionMatch("1.6");

    /**
     * Is {@code true} if this is Java version 1.7 (also 1.7.x versions).
     */
    public static final boolean IS_JAVA_1_7 = isJavaVersionMatch("1.7");

    /**
     * Is {@code true} if this is Java version 1.8 (also 1.8.x versions).
     */
    public static final boolean IS_JAVA_1_8 = isJavaVersionMatch("1.8");

    /**
     * Is {@code true} if this is Java version 1.8 or earlier (includes 1.8.x versions).
     */
    public static final boolean IS_JAVA_1_8_OR_EARLIER = JavaVersion.IS_JAVA_1_6 || JavaVersion.IS_JAVA_1_7 || JavaVersion.IS_JAVA_1_8;

    /**
     * Is {@code true} if this is Java version 9 (also 9.x versions).
     */
    public static final boolean IS_JAVA_9 = isJavaVersionMatch("9");

    /**
     * Is {@code true} if this is Java version 10 (also 10.x versions).
     */
    public static final boolean IS_JAVA_10 = isJavaVersionMatch("10");

    /**
     * Is {@code true} if this is Java version 10 or earlier (includes 10.x versions).
     */
    public static final boolean IS_JAVA_10_OR_EARLIER = IS_JAVA_1_8_OR_EARLIER || JavaVersion.IS_JAVA_9 || JavaVersion.IS_JAVA_10;

    /**
     * Is {@code true} if this is Java version 11 (also 11.x versions).
     */
    public static final boolean IS_JAVA_11 = isJavaVersionMatch("11");

    /**
     * Is {@code true} if this is Java version 12 (also 12.x versions).
     */
    public static final boolean IS_JAVA_12 = isJavaVersionMatch("12");

    /**
     * Is {@code true} if this is Java version 13 (also 13.x versions).
     */
    public static final boolean IS_JAVA_13 = isJavaVersionMatch("13");

    /**
     * Is {@code true} if this is Java version 14 (also 14.x versions).
     */
    public static final boolean IS_JAVA_14 = isJavaVersionMatch("14");

    /**
     * Is {@code true} if this is Java version 15 (also 15.x versions).
     */
    public static final boolean IS_JAVA_15 = isJavaVersionMatch("15");

    /**
     * Is {@code true} if this is Java version 16 (also 16.x versions).
     */
    public static final boolean IS_JAVA_16 = isJavaVersionMatch("16");

    /**
     * Is {@code true} if this is Java version 16 or earlier (includes 16.x versions).
     */
    public static final boolean IS_JAVA_16_OR_EARLIER =
            IS_JAVA_10_OR_EARLIER
            || JavaVersion.IS_JAVA_11
            || JavaVersion.IS_JAVA_12
            || JavaVersion.IS_JAVA_13
            || JavaVersion.IS_JAVA_14
            || JavaVersion.IS_JAVA_15
            || JavaVersion.IS_JAVA_16;

    /**
     * Is {@code true} if this is Java version 17 (also 17.x versions).
     */
    public static final boolean IS_JAVA_17 = isJavaVersionMatch("17");

    static boolean isJavaVersionMatch(final String versionPrefix) {
        String version = JAVA_SPECIFICATION_VERSION;
        if (version == null) {
            return false;
        }
        return version.startsWith(versionPrefix);
    }
}
