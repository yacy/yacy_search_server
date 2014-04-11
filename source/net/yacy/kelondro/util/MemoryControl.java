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

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Use this to get information about memory usage or try to free some memory
 */
public class MemoryControl {

    private static AtomicBoolean shortStatus = new AtomicBoolean(false);
    private static boolean simulatedShortStatus = false, usingStandardStrategy = true;
    private static MemoryStrategy strategy;

    private static MemoryStrategy getStrategy() {
    	if (strategy == null || MemoryStrategy.hasError()) {
    		if (!usingStandardStrategy) {
    			strategy = new GenerationMemoryStrategy();
//    			if (strategy.hasError()) { // perhaps we do have a G1
//    				strategy = new G1MemoryStrategy();
//    			}
    	    	// fall back if error detected
    	    	if (MemoryStrategy.hasError()) {
    	    		usingStandardStrategy = true;
    	    		strategy = new StandardMemoryStrategy();
    	    	}
    		} else {
    			strategy = new StandardMemoryStrategy();
    		}
    	}
    	return strategy;
    }

    public final static void setStandardStrategy(final boolean std) {
        if (usingStandardStrategy != std) {
    		usingStandardStrategy = std;
    		strategy = null;
    	}
    }

    /**
     * @return the name of the used strategy
     */
    public final static String getStrategyName() {
    	getStrategy();
        return MemoryStrategy.getName();
    }

    /**
     * Runs the garbage collector if last garbage collection is more than last millis ago
     * @param last time which must be passed since lased gc
     * @param info additional info for log
     */
    public final synchronized static boolean gc(final int last, final String info) { // thq
    	return getStrategy().gc(last, info);
    }

    /**
     * memory that is free without increasing of total memory taken from os
     * @return bytes
     */
    public static final long free() {
        return getStrategy().free();
    }

    /**
     * memory that is available including increasing total memory up to maximum
     * @return bytes
     */
    public static final long available() {
        return getStrategy().available();
    }

    /**
	 * maximum memory the Java virtual will allocate machine; may vary over time in some cases
	 * @return bytes
	 */
	public static final long maxMemory()
    {
    	return getStrategy().maxMemory();
    }

	/**
	 * currently allocated memory in the Java virtual machine; may vary over time
	 * @return bytes
	 */
	public static final long total()
	{
		return getStrategy().total();
	}

	/**
     * check for a specified amount of bytes
     *
     * @param size the requested amount of free memory in bytes
     * @param force specifies whether risk an expensive GC
     * @return whether enough memory could be freed (or is free) or not
     */
    public static boolean request(final long size, final boolean force) {
        if (size < 1024) return true; // to speed up things. If this would fail, it would be much too late to check this.
        return getStrategy().request(size, force, shortStatus);
    }

    /**
     * the simulated short status can be set to find out if the short status has effects to the system
     * @param status
     */
    public static void setSimulatedShortStatus(final boolean status) {
        simulatedShortStatus = status;
    }

    /**
     * the simulated short status can be retrieved to show that option in online interfaces
     * @return
     */
    public static boolean getSimulatedShortStatus() {
        return simulatedShortStatus;
    }

    /**
     * @return if last request failed
     */
    public static boolean shortStatus() {
        //if (shortStatus) System.out.println("**** SHORT MEMORY ****");
        return simulatedShortStatus || shortStatus.get();
    }

    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
    public static long used() {
        return getStrategy().used();
    }

    /**
     * @return if Memory seams to be in a proper state
     */
    public static boolean properState() {
    	return getStrategy().properState();
    }

    /**
     * forced enable properState - StandardMemoryStrategy only
     */
    public static void resetProperState() {
    	getStrategy().resetProperState();
    }

    /**
     * set the memory to be available for properState - StandardMemoryStrategy only
     */
    public static void setProperMbyte(final long mbyte) {
    	getStrategy().setProperMbyte(mbyte);
    }

    /**
     * main
     * @param args use 'force' to request by force, use 'std' / 'gen' to specify strategy
     */
    public static void main(final String[] args) {
        // try this with different strategy and compare results
        final int mb = 1024 * 1024;
        boolean force = false;
        for (final String arg : args) {
        	if (arg.equals("force")) force = true;
        	if (arg.equalsIgnoreCase("gen")) usingStandardStrategy = false;
        	if (arg.equalsIgnoreCase("std")) usingStandardStrategy = true;
        }
        System.out.println("vm: " + System.getProperty("java.vm.version"));
        System.out.println("computed max = " + (maxMemory() / mb) + " mb");
        System.out.println("using " + getStrategyName());
        final byte[][] x = new byte[100000][];

        for (int i = 0; i < 100000; i++) {
        	if (request(mb, force))
        	{
	            x[i] = new byte[mb];
	            System.out.println("used = " + i + " / " + (used() /mb) +
	                    ", total = " + (total() / mb) +
	                    ", free = " + (free() / mb) +
	                    ", max = " + (maxMemory() / mb) +
	                    ", avail = " + (available() / mb) +
	                    (usingStandardStrategy? ", averageGC = " + ((StandardMemoryStrategy)getStrategy()).getAverageGCFree() : ""));
        	} else System.exit(0);
        }

    }

}
