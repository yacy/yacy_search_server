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
import java.util.concurrent.atomic.AtomicBoolean;

public class GenerationMemoryStrategy extends MemoryStrategy {
	
	private final static long M = 1024l*1024l;
	private MemoryPoolMXBean eden, survivor, old;
    private MemoryMXBean heap;
    
    public GenerationMemoryStrategy() {
    	name = "Generation Memory Strategy";
    	error = initPoolBeans();
    	heap = ManagementFactory.getMemoryMXBean();
    	if (lastGC == 0l) gc(10000, "initial gc - to get proper results"); // this is necessary on some GCs / vm
    	if (error) log.warn(name + ": not a generational heap");
    }

    /**
     * memory that is free without increasing of total memory taken from os
     * @return bytes
     */
    @Override
    protected final long free() {
        return getUsage(eden, false).getCommitted() - getUsage(eden, false).getUsed();
    }
    
    /**
     * memory that is available including increasing total memory up to maximum
     * Smallest of both old and young
     * @return bytes
     */
    @Override
    protected final long available() {
    	return available(true);
    }
    
    /**
     * memory that is available including increasing total memory up to maximum
     * @param force specifies whether ignoring prefered size
     * @return bytes
     */
    private final long available(final boolean force) {
    	return force & properState(force) ? Math.max(youngAvailable(), oldAvailable()) : Math.min(youngAvailable(), Math.max(M, oldAvailable()));
    }
    
    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
    @Override
    protected final long used() {
        return heap.getHeapMemoryUsage().getUsed();
    }

	/**
	 * currently allocated memory in the Java virtual machine; may vary over time
	 * @return bytes
	 */
    @Override
    protected final long total() {
		return heap.getHeapMemoryUsage().getCommitted();
	}
    
    /**
	 * maximum memory the Java virtual will allocate machine; may vary over time in some cases
	 * @return bytes
	 */
    @Override
    protected final long maxMemory() {
		return heap.getHeapMemoryUsage().getMax();
    }

	/**
     * checks if a specified amount of bytes are available
     * after the jvm recycled unused objects
     * 
     * @param size the requested amount of free memory in bytes
     * @param force specifies whether ignoring preferred size
     * @return whether enough memory could be freed (or is free) or not
     */
    @Override
    protected final boolean request(final long size, final boolean force, AtomicBoolean shortStatus) {
    	if (size == 0l) return true; // does not make sense to check - returning true without setting shortStatus (which also doesn't make sense to me)
    	final boolean unknown = size < 0l; // size < 0 indicate an unknown size - maybe from gziped streams
    	final boolean r = unknown ? properState(force) : size < available(force);
        shortStatus.set(!r);
        return r;
    }
    
    /**
     * use this to check for temporary space
     * @return bytes available to allocate in Eden Space (Young Generation)
     */
    private final long youngAvailable() {
    	final MemoryUsage usage = getUsage(eden, true);
    	return usage.getCommitted() - usage.getUsed();
    }
    
    /**
     * @return bytes available to allocate in Tenured Space (Old Generation)
     */
    private final long oldAvailable() {
    	final MemoryUsage usage = getUsage(old, true);
    	return Math.max(usage.getMax(), usage.getCommitted()) - usage.getUsed();
    }
    
    /**
     * alive objects get 'moved' on gc from eden space to survior and from survior to old gen
     * in a worse case (all objects of survivor alive) all objects get 'moved' from suvivor to old gen
     * this method checks if there is is space left in old gen for that 
     * 
     * @return Memory is in proper state
     */
    @Override
    protected boolean properState() {
    	return properState(false);
    }
    
    /**
     * @param force whether using used or committed survivor usage
     * @return if survivor fits into old space
     */
    private boolean properState(final boolean force) {
    	final long surv = force? Math.max(M, getUsage(survivor, false).getUsed()) : getUsage(survivor, false).getCommitted();
    	return surv < oldAvailable();
    }
    
    /**
     * based on my research for a proper running jvm, this gives a guidance value for the  heap size
     * 
     * @return bytes recommend for heap size
     */
    protected long recommendHeapSize() {
    	// the heap/old-ration is jvm-specific and can be changed by parameter - using this + 20% buffer
    	final double factor = 1.2 * heap.getHeapMemoryUsage().getMax() / getUsage(old, false).getMax();
    	// current needed space in old gen
    	final long neededOld = getUsage(old, true).getUsed() + getUsage(survivor, false).getMax();
    	return (long) (neededOld * factor);
    }
    
    /**
     * @param collected specifies whether trying to get the memory usage after the jvm recycled unused objects
     * @return MemoryUsage of given MemoryPoolMXBean
     */
    private MemoryUsage getUsage(final MemoryPoolMXBean bean, final boolean collected) {
    	if (collected) {
    		try {
				final MemoryUsage usage = bean.getCollectionUsage();
				if (usage != null) return usage;
			} catch (final IllegalArgumentException e) {
				log.warn(name + ": ", e);
			}
    		error = true;
    		log.warn(name + ": no colletion usage available at " + bean.getName());
    	}
    	return bean.getUsage();
    }
    
    private boolean initPoolBeans() {
    	for (final MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
    		if (bean.getName().startsWith("G1")) break; //this strategy will not run on G1
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
}
