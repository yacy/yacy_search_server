// MemoryControl.java
// -------------------------------------------
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.09.2005 on http://yacy.net
//
// $LastChangedDate: 2011-08-18 00:24:17 +0200 (Do, 18. Aug 2011) $
// $LastChangedRevision: 7883 $
// $LastChangedBy: orbiter $
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

import java.util.concurrent.atomic.AtomicBoolean;

import net.yacy.cora.util.Memory;

/**
 * Standard implementation to get information about memory usage or try to free some memory
 */
public class StandardMemoryStrategy extends MemoryStrategy {

    private final long[] gcs = new long[5];
    private int gcs_pos = 0;
    private long properMbyte = 0L;
    private long prevTreshold = 0L;
    private int tresholdCount = 0;
    private boolean proper = true;

    public StandardMemoryStrategy() {
    	name = "Standard Memory Strategy";
    	error= false; //since this is the standard implementation we assume always false here
    }

    /**
     * Runs the garbage collector if last garbage collection is more than last millis ago
     * @param last time which must be passed since lased gc
     * @param info additional info for log
     */
    @Override
    protected final synchronized boolean gc(final int last, final String info) { // thq
    	assert last >= 10000; // too many forced GCs will cause bad execution performance
        final long elapsed = System.currentTimeMillis() - lastGC;
        if (elapsed > last) {
            final long before = free();
            final long start = System.currentTimeMillis();
            System.gc();
            lastGC = System.currentTimeMillis();
            final long after = free();
            this.gcs[this.gcs_pos++] = after - before;
            if (this.gcs_pos >= this.gcs.length) this.gcs_pos = 0;

            if (log.isFine()) log.info("[gc] before: " + Formatter.bytesToString(before) +
                                              ", after: " + Formatter.bytesToString(after) +
                                              ", freed: " + Formatter.bytesToString(after - before) +
                                              ", rt: " + (lastGC - start) + " ms, call: " + info);
            return true;
        }

        if (log.isFinest()) log.finest("[gc] no execute, last run: " + (elapsed / 1000) + " seconds ago, call: " + info);
        return false;
    }

    /**
     * This method calculates the average amount of bytes freed by the last GCs forced by this class
     * @return the average amount of freed bytes of the last forced GCs or <code>0</code> if no
     * GC has been run yet
     */
    protected final long getAverageGCFree() {
        long x = 0;
        int y = 0;
        for (final long gc : this.gcs)
            if (gc != 0) {
                x += gc;
                y++;
            }
        return (y == 0) ? 0 : x / y;
    }

    /**
     * memory that is free without increasing of total memory taken from os
     * @return bytes
     */
    @Override
    protected final long free() {
        return Memory.free();
    }

    /**
     * memory that is available including increasing total memory up to maximum
     * @return bytes
     */
    @Override
    protected final long available() {
        return Memory.available();
    }

    /**
	 * maximum memory the Java virtual will allocate machine; may vary over time in some cases
	 * @return bytes
	 */
    @Override
    protected final long maxMemory()
    {
    	return Memory.maxMemory();
    }

	/**
	 * currently allocated memory in the Java virtual machine; may vary over time
	 * @return bytes
	 */
    @Override
    protected final long total()
	{
		return Memory.total();
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
    @Override
    protected boolean request(final long size, final boolean force, AtomicBoolean shortStatus) {
        if (size <= 0) return true;
        final boolean r = request0(size, force);
        shortStatus.set(!r);
        return r;
    }
    private boolean request0(final long size, final boolean force) {
    	final long avg = getAverageGCFree();
    	if (avg >= size) return true;
        long avail = available();
        if (avail >= size) return true;
        if (log.isFine()) {
            final String t = new Throwable("Stack trace").getStackTrace()[1].toString();
            log.fine(t + " requested " + (size >> 10) + " KB, got " + (avail >> 10) + " KB");
        }
        if (force || avg == 0 || avg + avail >= size) {
            // this is only called if we expect that an allocation of <size> bytes would cause the jvm to call the GC anyway

            final long memBefore = avail;
            final boolean performedGC = gc(10000, "serverMemory.runGC(...)");
            avail = available();
            if (performedGC) {
                final long freed = avail - memBefore;
                log.info("performed " + ((force) ? "explicit" : "necessary") + " GC, freed " + (freed >> 10)
                    + " KB (requested/available/average: "
                    + (size >> 10) + " / " + (avail >> 10) + " / " + (avg >> 10) + " KB)");
            }
            checkProper(avail);
            return avail >= size;
        }
        if (log.isFine()) log.fine("former GCs indicate to not be able to free enough memory (requested/available/average: "
                + (size >> 10) + " / " + (avail >> 10) + " / " + (avg >> 10) + " KB)");
        return false;
    }

    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
    @Override
    protected final long used() {
        return Memory.used();
    }

    @Override
    protected boolean properState() {
    	return this.proper;
    }

    @Override
    protected void resetProperState() {
    	this.proper = true;
    	this.tresholdCount = 0;
    }

    /**
     * set the memory to be available
     */
    @Override
    protected void setProperMbyte(final long mbyte) {
    	this.properMbyte = mbyte;
    	this.tresholdCount = 0;
    }

    private void checkProper(final long available) {
    	// disable proper state if memory is less than treshold - 4 times, maximum 11 minutes between each detection
    	if ((available >> 20) < this.properMbyte) {
    		final long t = System.currentTimeMillis();
    		if(this.prevTreshold + 11L /* minutes */ * 60000L > t) {
    			this.tresholdCount++;
    			if(this.tresholdCount > 3 /* occurencies - 1 */) this.proper = false;
    		}
    		else this.tresholdCount = 1;

    		this.prevTreshold = t;

			log.info("checkProper: below treshold; tresholdCount: " + this.tresholdCount + "; proper: " + this.proper);
    	}
    	else if (!this.proper && (available >> 20) > (this.properMbyte * 2L)) // we were wrong!
    		resetProperState();
    }

}
