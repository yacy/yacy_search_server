// MemoryControl.java
// -------------------------------------------
// (C) 2011 by Sebastian Gaebel
// first published 22.08.2011 on http://yacy.net
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

import net.yacy.cora.util.ConcurrentLog;

public abstract class MemoryStrategy {

	protected final static ConcurrentLog log = new ConcurrentLog("MEMORY");
    protected static long lastGC = 0l;
    protected static boolean error = true;
	protected static String name;

    /**
     * @return if an error has been detected
     */
	protected final static boolean hasError() {
    	return error;
    }

    /**
     * @return an identifying name
     */
    protected final static String getName() {
    	return name;
    }

    /**
     * Runs the garbage collector if last garbage collection is more than last millis ago
     * @param last time which must be passed since lased gc
     * @param info additional info for log
     */
    protected synchronized boolean gc(final int last, final String info) { // thq
    	assert last >= 10000; // too many forced GCs will cause bad execution performance
        final long elapsed = System.currentTimeMillis() - lastGC;
        if (elapsed > last) {
        	System.gc();
            lastGC = System.currentTimeMillis();
            return true;
        }

        if (log.isFinest()) log.finest("[gc] no execute, last run: " + (elapsed / 1000) + " seconds ago, call: " + info);
        return false;
    }

	/**
     * memory that is free without increasing of total memory taken from os
     * @return bytes
     */
	protected abstract long free();

    /**
     * memory that is available including increasing total memory up to maximum
     * @return bytes
     */
	protected abstract long available();

    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
	protected abstract long used();

	/**
	 * currently allocated memory in the Java virtual machine; may vary over time
	 * @return bytes
	 */
	protected abstract long total();

    /**
	 * maximum memory the Java virtual will allocate machine; may vary over time in some cases
	 * @return bytes
	 */
	protected abstract long maxMemory();

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
	protected abstract boolean request(final long size, final boolean force, AtomicBoolean shortStatus);

	/**
     * @return if Memory seams to be in a proper state
     */
    protected abstract boolean properState();

	/**
     * forced enable properState - StandardMemoryStrategy only
     */
    protected void resetProperState() {
    }

    /**
     * set the memory to be available for properState - StandardMemoryStrategy only
     */
    protected void setProperMbyte(@SuppressWarnings("unused") final long mbyte) {
    }
}
