package se.jiderhamn;

import java.io.File;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;

import com.sun.management.HotSpotDiagnosticMXBean;

/**
 * Class that helps programatically dumping the heap.
 * Heavily inspired by https://blogs.oracle.com/sundararajan/entry/programmatically_dumping_heap_from_java
 * @author Mattias Jiderhamn
 */
public class HeapDumper {

  /** The name of the HotSpot Diagnostic MBean */
  private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
  
  /** Filename extension for heap dumps */
  public static final String HEAP_DUMP_EXTENSION = ".hprof";

  /** HotSpot diagnostic MBean */
  private static volatile HotSpotDiagnosticMXBean hotSpotDiagnosticMBean;

  /**
   * Dump the heap snapshot into a file.
   * @param file The file in which the dump will be stored
   * @param live Dump only live objects?
   */
  public static void dumpHeap(File file, boolean live) throws ClassNotFoundException {
    if(file.exists()) {
      System.err.println("Cannot dump heap to '" + file + "' - file exists!");
      return;
    }

    try {
      getHotSpotDiagnosticMBean().dumpHeap(file.getAbsolutePath(), live);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Get HotSpot diagnostic MBean */
  private static HotSpotDiagnosticMXBean getHotSpotDiagnosticMBean() {
    if (hotSpotDiagnosticMBean == null) {
      // synchronized (HeapDumper.class) {
      //  if (hotspotMBean == null) {
          try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            hotSpotDiagnosticMBean = ManagementFactory.newPlatformMXBeanProxy(server,
                    HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
      //   }
      // }
    }
    
    return hotSpotDiagnosticMBean;
  }

}