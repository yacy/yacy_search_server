// MemoryControl.java
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

package net.yacy.kelondro.util;

import net.yacy.kelondro.logging.Log;

/**
 * Use this to get information about memory usage or try to free some memory
 */
public class MemoryControl {

	
    private static final Runtime runtime = Runtime.getRuntime();
    public static long maxMemory = runtime.maxMemory(); // this value does never change during runtime
	private static final Log log = new Log("MEMORY");
    
    private static final long[] gcs = new long[5];
    private static int gcs_pos = 0;
    private static long lastGC = 0l;
    private static long DHTMbyte = 0L;
    private static long prevDHTtreshold = 0L;
    private static int DHTtresholdCount = 0;
    private static boolean allowDHT = true;
    private static boolean shortStatus = false;

    /**
     * Runs the garbage collector if last garbage collection is more than last millis ago
     * @param last time which must be passed since lased gc
     * @param info additional info for log
     */
    public final synchronized static boolean gc(final int last, final String info) { // thq
    	assert last >= 10000; // too many forced GCs will cause bad execution performance
        final long elapsed = System.currentTimeMillis() - lastGC;
        if (elapsed > last) {
            final long before = free();
            final long start = System.currentTimeMillis();
            System.gc();
            lastGC = System.currentTimeMillis();
            final long after = free();
            gcs[gcs_pos++] = after - before;
            if (gcs_pos >= gcs.length) gcs_pos = 0;
            
            if (log.isFine()) log.logInfo("[gc] before: " + Formatter.bytesToString(before) +
                                              ", after: " + Formatter.bytesToString(after) +
                                              ", freed: " + Formatter.bytesToString(after - before) +
                                              ", rt: " + (lastGC - start) + " ms, call: " + info);
            return true;
        }
        
        if (log.isFinest()) log.logFinest("[gc] no execute, last run: " + (elapsed / 1000) + " seconds ago, call: " + info);
        return false;
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
        return maxMemory - total() + free();
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
        boolean r = request0(size, force);
        shortStatus = !r;
        return r;
    }
    private static boolean request0(final long size, final boolean force) {
    	final long avg = getAverageGCFree();
    	if (avg >= size) return true;
        long avail = available();
        if (avail >= size) return true;
        if (log.isFine()) {
            final String t = new Throwable("Stack trace").getStackTrace()[1].toString();
            log.logFine(t + " requested " + (size >> 10) + " KB, got " + (avail >> 10) + " KB");
        } 
        if (force || avg == 0 || avg + avail >= size) {
            // this is only called if we expect that an allocation of <size> bytes would cause the jvm to call the GC anyway
            
            final long memBefore = avail;
            boolean performedGC = gc(10000, "serverMemory.runGC(...)");
            avail = available();
            if (performedGC) {
                final long freed = avail - memBefore;
                log.logInfo("performed " + ((force) ? "explicit" : "necessary") + " GC, freed " + (freed >> 10)
                    + " KB (requested/available/average: "
                    + (size >> 10) + " / " + (avail >> 10) + " / " + (avg >> 10) + " KB)");
            }
            checkDHTrule(avail);
            return avail >= size;
        } else {
            if (log.isFine()) log.logFine("former GCs indicate to not be able to free enough memory (requested/available/average: "
                    + (size >> 10) + " / " + (avail >> 10) + " / " + (avg >> 10) + " KB)");
            return false;
        }
    }
    
    public static boolean shortStatus() {
        return shortStatus;
    }
    
    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
    public static long used() {
        return total() - free();
    }
        
    public static boolean getDHTallowed() {
    	return allowDHT;
    }
    
    public static void setDHTallowed() {
    	allowDHT = true;
    	DHTtresholdCount = 0;
    }
    
    /**
     * set the memory to be available
     */
    public static void setDHTMbyte(final long mbyte) {
    	DHTMbyte = mbyte;
    	DHTtresholdCount = 0;
    }
    
    private static void checkDHTrule(final long available) {
    	// disable dht if memory is less than treshold - 4 times, maximum 11 minutes between each detection
    	if ((available >> 20) < DHTMbyte) {
    		final long t = System.currentTimeMillis();
    		if(prevDHTtreshold + 11L /* minutes */ * 60000L > t) {
    			DHTtresholdCount++;
    			if(DHTtresholdCount > 3 /* occurencies - 1 */) allowDHT = false;
    		}
    		else DHTtresholdCount = 1;
    		
    		prevDHTtreshold = t;
    		
			log.logInfo("checkDHTrule: below treshold; tresholdCount: " + DHTtresholdCount + "; allowDHT: " + allowDHT);
    	}
    	else if (!allowDHT && (available >> 20) > (DHTMbyte * 2L)) // we were wrong!
    		setDHTallowed();
    }

    /**
     * main
     * @param args
     */
    public static void main(final String[] args) {
        // try this with a jvm 1.4.2 and with a jvm 1.5 and compare results
        final int mb = 1024 * 1024;
        System.out.println("vm: " + System.getProperty("java.vm.version"));
        System.out.println("computed max = " + (maxMemory / mb) + " mb");
        final int alloc = 10000;
        final byte[][] x = new byte[100000][];
        for (int i = 0; i < 100000; i++) {
            x[i] = new byte[alloc];
            if (i % 100 == 0) System.out.println("used = " + (i * alloc / mb) +
                    ", total = " + (total() / mb) +
                    ", free = " + (free() / mb) +
                    ", max = " + (maxMemory / mb) +
                    ", avail = " + (available() / mb));
        }

    }
    
}
