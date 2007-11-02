// serverMemory.java
// -------------------------------------------
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.09.2005 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.server;

import de.anomic.server.logging.serverLog;
import de.anomic.tools.yFormatter;

public class serverMemory {

    public static boolean vm14 = System.getProperty("java.vm.version").startsWith("1.4");
    public static final long max = (vm14) ? computedMaxMemory() : Runtime.getRuntime().maxMemory() ; // patch for maxMemory bug in Java 1.4.2
    private static final Runtime runtime = Runtime.getRuntime();
    private static final serverLog log = new serverLog("MEMORY");
    
    private static final long[] gcs = new long[5];
    private static int gcs_pos = 0;

    private static long lastGC;

    public final synchronized static void gc(int last, String info) { // thq
        long elapsed = System.currentTimeMillis() - lastGC;
        if (elapsed > last) {
            long free = free();
            System.gc();
            lastGC = System.currentTimeMillis();
            if (log.isFine()) log.logInfo("[gc] before: " + bytesToString(free) + ", after: " + bytesToString(free()) + ", call: " + info);
        } else if (log.isFine()) {
            log.logFinest("[gc] no execute, last run: " + (elapsed / 1000) + " seconds ago, call: " + info);
        }
    }

    /** @return the amount of freed bytes by a forced GC this method performes */
    private static long runGC(final boolean count) {
        final long memnow = available();
        gc(1000, "serverMemory.runGC(...)");
        final long freed = available() - memnow;
        if (count) {
            gcs[gcs_pos] = freed;
            gcs_pos = (gcs_pos + 1) % gcs.length;
        }
        return freed;
    }
    
    /**
     * This method calculates the average amount of bytes freed by the last GCs forced by this class
     * @return the average amount of freed bytes of the last forced GCs or <code>0</code> if no
     * GC has been run yet
     */
    public static long getAverageGCFree() {
        long x = 0;
        int y = 0;
        for (int i=0; i<gcs.length; i++)
            if (gcs[i] != 0) {
                x += gcs[i];
                y++;
            }
        return (y == 0) ? 0 : x / y;
    }

    public static long free() {
        // memory that is free without increasing of total memory taken from os
        return runtime.freeMemory();
    }
    
    /**
     * Tries to free a specified amount of bytes. If the currently available memory is enough, the
     * method returns <code>true</code> without performing additional steps. Otherwise - if
     * <code>gciffail</code> is set - a Full GC is run, if <code>gciffail</code> is set to
     * <code>false</code> and not enough memory is available, this method returns <code>false</code>.
     * 
     * @see serverMemory#request(long, boolean) for another implementation
     * @param memory amount of bytes to be assured to be free
     * @param gciffail if not enough memory is available, this parameter specifies whether to perform
     * a Full GC to free enough RAM
     * @return whether enough RAM is available
     * @deprecated use {@link serverMemory#request(long, boolean)} instead to enable the collection of
     * heuristics about explicit usage of Full GCs.
     */
    public static boolean available(long memory, boolean gciffail) {
        if (available() >= memory) return true;
        if (!gciffail) return false;
        gc(4000, "serverMemory.available(...)");
        return (available() >= memory);
    }
    
    public static long available() {
        // memory that is available including increasing total memory up to maximum
        return max - runtime.totalMemory() + runtime.freeMemory();
    }
    
    /**
     * <p>Tries to free a specified amount of bytes.</p>
     * <p>
     *   If the currently available memory is enough, the method returns <code>true</code> without
     *   performing additional steps. If not, the behaviour depends on the parameter <code>force</code>.
     *   If <code>false</code>, a Full GC is only performed if former GCs indicated that a GC should
     *   provide enough free memory. If former GCs didn't but <code>force</code> is set to <code>true</code>
     *   a Full GC is performed nevertheless.
     * </p>
     * <p>
     *   Setting the <code>force</code> parameter to false doesn't necessarily mean, that no GC may be
     *   performed by this method, if the currently available memory doesn't suffice!
     * </p>
     * <p><em>Be careful with this method as GCs should always be the last measure to take</em></p>
     * 
     * @param size the requested amount of free memory in bytes
     * @param force specifies whether a GC should be run even in case former GCs didn't provide enough memory
     * @return whether enough memory could be freed (or is free) or not
     */
    public static boolean request(final long size, final boolean force) {
        long avail = available();
        if (avail >= size) return true;
        if (log.isFine()) {
            String t = new Throwable("Stack trace").getStackTrace()[1].toString();
            log.logFine(t + " requested " + (size >> 10) + " KB, got " + (avail >> 10) + " KB");
        } 
        final long avg = getAverageGCFree();
        if (force || avg == 0 || avg + avail >= size) {
            // this is only called if we expect that an allocation of <size> bytes would cause the jvm to call the GC anyway
            final long freed = runGC(!force);
            avail = available();
            log.logInfo("performed " + ((force) ? "explicit" : "necessary") + " GC, freed " + (freed >> 10)
                    + " KB (requested/available/average: " + (size >> 10) + " / "
                    + (avail >> 10) + " / " + (avg >> 10) + " KB)");
            return avail >= size;
        } else {
            log.logInfo("former GCs indicate to not be able to free enough memory (requested/available/average: "
                    + (size >> 10) + " / " + (avail >> 10) + " / " + (avg >> 10) + " KB)");
            return false;
        }
    }
    
    public static long used() {
        // memory that is currently bound in objects
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    public static String bytesToString(long byteCount) {
        try {
            final StringBuffer byteString = new StringBuffer();

            if (byteCount > 1073741824) {
                byteString.append(yFormatter.number((double)byteCount / (double)1073741824 ))
                          .append(" GB");
            } else if (byteCount > 1048576) {
                byteString.append(yFormatter.number((double)byteCount / (double)1048576))
                          .append(" MB");
            } else if (byteCount > 1024) {
                byteString.append(yFormatter.number((double)byteCount / (double)1024))
                          .append(" KB");
            } else {
                byteString.append(Long.toString(byteCount))
                .append(" Bytes");
            }

            return byteString.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private static int computedMaxMemory() {
        // there is a bug in java 1.4.2 for maxMemory()
        // see for bug description:
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4686462
        // to get the correct maxMemory, we force a OutOfMemoryError here to measure the 'real' maxMemory()
        int mb = 1024 * 1024;
        byte[][] x = new byte[2048][];
        for (int i = 0; i < x.length; i++) {
            try {
                x[i] = new byte[mb];
            } catch (OutOfMemoryError e) {
                x = null; // free memory
                //System.out.println("* computed maxMemory = " + i + " mb");
                return (int) Math.max(i * mb, Runtime.getRuntime().totalMemory());
            }
        }
        return 2048 * mb;
    }
    
    public static void main(String[] args) {
        // try this with a jvm 1.4.2 and with a jvm 1.5 and compare results
        int mb = 1024 * 1024;
        System.out.println("vm: " + System.getProperty("java.vm.version"));
        System.out.println("computed max = " + (computedMaxMemory() / mb) + " mb");
        int alloc = 10000;
        Runtime rt = Runtime.getRuntime();
        byte[][] x = new byte[100000][];
        for (int i = 0; i < 100000; i++) {
            x[i] = new byte[alloc];
            if (i % 100 == 0) System.out.println("used = " + (i * alloc / mb) +
                    ", total = " + (rt.totalMemory() / mb) +
                    ", free = " + (rt.freeMemory() / mb) +
                    ", max = " + (rt.maxMemory() / mb) +
                    ", avail = " + ((rt.maxMemory() - rt.totalMemory() + rt.freeMemory()) / mb));
        }

    }
    
}
