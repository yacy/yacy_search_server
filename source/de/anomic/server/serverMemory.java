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

/**
 * Use this to get information about memory usage or try to free some memory
 */
public class serverMemory {

    private static final Runtime runtime = Runtime.getRuntime();
    private static final serverLog log = new serverLog("MEMORY");
    
    private static final long[] gcs = new long[5];
    private static int gcs_pos = 0;

    private static long lastGC;

    /**
     * Runs the garbage collector if last garbage collection is more than last millis ago
     * @param last time which must be passed since lased gc
     * @param info additional info for log
     */
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

    /**
     * Tries to free count bytes
     * @param count bytes
     * @return the amount of freed bytes by a forced GC this method performes
     */
    private static long runGC(final boolean count) {
        final long memBefore = available();
        gc(1000, "serverMemory.runGC(...)");
        final long freed = available() - memBefore;
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

    /**
     * memory that is free without increasing of total memory taken from os
     * @return bytes
     */
    public static final long free() {
        return runtime.freeMemory();
    }
    
    /**
     * memory that is available including increasing total memory up to maximum
     * @return bytes
     */
    public static final long available() {
        return max() - total() + free();
    }
    
    /**
     * maximum memory which the vm can use
	 * @return bytes
	 */
	public static final long max()
	{
		return runtime.maxMemory();
	}

	/**
	 * currently allocated memory in the Java virtual machine; may vary over time
	 * @return bytes
	 */
	public static final long total()
	{
		return runtime.totalMemory();
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
                    + " KB (requested/available/average: "
                    + (size >> 10) + " / " + (avail >> 10) + " / " + (avg >> 10) + " KB)");
            return avail >= size;
        } else {
            log.logInfo("former GCs indicate to not be able to free enough memory (requested/available/average: "
                    + (size >> 10) + " / " + (avail >> 10) + " / " + (avg >> 10) + " KB)");
            return false;
        }
    }
    
    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
    public static long used() {
        return total() - free();
    }
    
    /**
     * Formats a number if it are bytes to greatest unit (1024 based)
     * @param byteCount
     * @return formatted String with unit
     */
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
    
    /**
     * main
     * @param args
     */
    public static void main(String[] args) {
        // try this with a jvm 1.4.2 and with a jvm 1.5 and compare results
        int mb = 1024 * 1024;
        System.out.println("vm: " + System.getProperty("java.vm.version"));
        System.out.println("computed max = " + (max() / mb) + " mb");
        int alloc = 10000;
        byte[][] x = new byte[100000][];
        for (int i = 0; i < 100000; i++) {
            x[i] = new byte[alloc];
            if (i % 100 == 0) System.out.println("used = " + (i * alloc / mb) +
                    ", total = " + (total() / mb) +
                    ", free = " + (free() / mb) +
                    ", max = " + (max() / mb) +
                    ", avail = " + (available() / mb));
        }

    }
    
}
