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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

public class G1MemoryStrategy extends MemoryStrategy {
	
	private MemoryPoolMXBean eden, survivor, old;
    private MemoryMXBean heap;
    
    public G1MemoryStrategy() {
    	name = "G1 Memory Strategy";
    	error = initPoolBeans();
    	heap = ManagementFactory.getMemoryMXBean();
    	if (lastGC == 0l) gc(10000, "initial gc - to get proper results"); // this is necessary on some GCs / vm
    	if (error) log.logWarning(name + ": not a generational heap");
    }

    /**
     * memory that is free without increasing of total memory taken from os
     * @return bytes
     */
    protected final long free() {
        return oldUsage(false).getCommitted() - oldUsage(false).getUsed();
    }
    
    /**
     * memory that is available including increasing total memory up to maximum
     * Smallest of both old and young
     * @return bytes
     */
    protected final long available() {
    	return oldAvailable();
    }
    
    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
    protected final long used() {
        return heap.getHeapMemoryUsage().getUsed();
    }

	/**
	 * currently allocated memory in the Java virtual machine; may vary over time
	 * @return bytes
	 */
    protected final long total() {
		return heap.getHeapMemoryUsage().getCommitted();
	}
    
    /**
	 * maximum memory the Java virtual will allocate machine; may vary over time in some cases
	 * @return bytes
	 */
    protected final long maxMemory() {
		return heap.getHeapMemoryUsage().getMax();
    }

	/**
     * checks if a specified amount of bytes are available
     * after the jvm recycled unused objects
     * 
     * @param size the requested amount of free memory in bytes
     * @param force specifies whether ignoring prefered size
     * @return whether enough memory could be freed (or is free) or not
     */
    protected final boolean request(final long size, final boolean force, boolean shortStatus) {
    	debugOut();
    	if (size == 0l) return true; // does not make sense to check - returning true without setting shortStatus (which also doesn't make sense to me)
    	final boolean unknown = size < 0l; // size < 0 indicate an unknown size - maybe from gziped streams
    	final boolean r = unknown? properState() : size < oldAvailable();
        shortStatus = !r;
        return r;
    }
    
    /**
     * @return bytes available to allocate in Tenured Space (Old Generation)
     */
    private final long oldAvailable() {
    	return oldUsage(true).getCommitted() - oldUsage(true).getUsed();
    }
    
    /**
     * G1 works different than traditional generation-stukture.
     * The heap iss segmented which can be young, survivior or old generation.
     * For proper running young and survior need to fit into old space
     * 
     * @return Memory is in proper state
     */
    protected boolean properState() {
    	return (oldUsage(true).getUsed() + survivorUsage(false).getCommitted() + youngUsage(false).getCommitted()) < oldUsage(false).getCommitted();
    }
    
    /**
     * @param collected specifies whether trying to get the memory usage after the jvm recycled unused objects
     * @return MemoryUsage of Eden Space aka Young Gen
     */
    private MemoryUsage youngUsage(final boolean collected) {
    	if (collected) {
    		final MemoryUsage usage = eden.getCollectionUsage();
    		if (usage != null) return usage;
    		error = true;
    		log.logWarning(name + ": no young colletion usage available");
    	}
    	return eden.getUsage();
    }
    
    /**
     * @param collected specifies whether trying to get the memory usage after the jvm recycled unused objects
     * @return MemoryUsage of Survivor
     */
    private MemoryUsage survivorUsage(final boolean collected) {
    	if (collected) {
    		final MemoryUsage usage = survivor.getCollectionUsage();
    		if (usage != null) return usage;
    		error = true;
    		log.logWarning(name + ": no survivior colletion usage available");
    	}
    	return survivor.getUsage();
    }
    
    /**
     * @param collected specifies whether trying to get the memory usage after the jvm recycled unused objects
     * @return MemoryUsage of Old Gen
     */
    private MemoryUsage oldUsage(final boolean collected) {
    	if (collected) {
    		final MemoryUsage usage = old.getCollectionUsage();
    		if (usage != null) return usage;
    		error = true;
    		log.logWarning(name + ": no old colletion usage available");
    	}
    	return old.getUsage();
    }
    
    private boolean initPoolBeans() {
    	for (final MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
    		if (!bean.getName().startsWith("G1")) continue; //this strategy is G1 only
    		if (bean.getName().contains("Eden")) {
    			eden = bean;
    		} else if (bean.getName().contains("Survivor")) {
    			survivor = bean;
    		} else if (bean.getName().contains("Old") || bean.getName().contains("Tenured")) {
    			old = bean;
    		}
    	}
		return eden == null || survivor == null || old == null;
    }
    
    private void debugOut() {
//    	System.out.println("young: " + youngUsage(false).getUsed() + " " + youngUsage(false).getCommitted() + " " + youngUsage(false).getMax());
//    	System.out.println("->: " + youngUsage(true).getUsed() + " " + youngUsage(true).getCommitted() + " " + youngUsage(true).getMax());
//    	System.out.println("survivor: " + survivorUsage(false).getUsed() + " " + survivorUsage(false).getCommitted() + " " + survivorUsage(false).getMax());
//    	System.out.println("->: " + survivorUsage(true).getUsed() + " " + survivorUsage(true).getCommitted() + " " + survivorUsage(true).getMax());
    	System.out.println("old: " + oldUsage(false).getUsed() + " " + oldUsage(false).getCommitted() + " " + oldUsage(false).getMax());
    	System.out.println("->: " + oldUsage(true).getUsed() + " " + oldUsage(true).getCommitted() + " " + oldUsage(true).getMax());
    }
}
