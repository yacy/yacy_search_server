// ResourceObserver.java
// -----------------------
// (c) David Wieditz; lotus at mail.berlios.de
// first published 6.2.2010
//
// based on the former code (c) by Detlef Reichl; detlef!reichl()gmx!org
// Pforzheim, Germany, 2008
//
// part of YaCy
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

package net.yacy.search;

import java.io.File;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.query.SearchEventCache;

public class ResourceObserver {

    public static final ConcurrentLog log = new ConcurrentLog("RESOURCE OBSERVER");

    // return values for available disk/memory
    public enum Space implements Comparable<Space> {
        LOW, MEDIUM, HIGH; // according to the order of the definition, LOW is smaller than MEDIUM and MEDIUM is smaller than HIGH
    }

    private final Switchboard sb;
    private final File path; // path to check

    private Space normalizedDiskFree = Space.HIGH;
    private Space normalizedMemoryFree = Space.HIGH;

    public ResourceObserver(final Switchboard sb) {
        this.sb = sb;
        this.path = sb.getDataPath(SwitchboardConstants.INDEX_PRIMARY_PATH, "");
        log.info("path for disc space measurement: " + this.path);
    }

    public static void initThread() {
    	final Switchboard sb = Switchboard.getSwitchboard();
    	sb.observer =  new ResourceObserver(Switchboard.getSwitchboard());
    	sb.observer.resourceObserverJob();
    }

    /**
     * checks the resources and pauses crawls if necessary
     */
    public void resourceObserverJob() {
    	MemoryControl.setProperMbyte(getMinFreeMemory());

    	this.normalizedDiskFree = getNormalizedDiskFree();
    	this.normalizedMemoryFree = getNormalizedMemoryFree();

    	if (this.normalizedDiskFree.compareTo(Space.HIGH) < 0 || this.normalizedMemoryFree.compareTo(Space.HIGH) < 0 ) {
    	    String reason = "";
            if (this.normalizedDiskFree.compareTo(Space.HIGH) < 0) reason += " not enough disk space, " + this.path.getUsableSpace();
            if (this.normalizedMemoryFree.compareTo(Space.HIGH) < 0 ) reason += " not enough memory space";
			if (!this.sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) {
				log.info("pausing local crawls");
				this.sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL, "resource observer:" + reason);
			}
			if (!this.sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL)) {
				log.info("pausing remote triggered crawls");
				this.sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL, "resource observer:" + reason);
			}

    		if ((this.normalizedDiskFree == Space.LOW || this.normalizedMemoryFree.compareTo(Space.HIGH) < 0) && this.sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false)) {
    			log.info("disabling index receive");
    			this.sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
    			this.sb.peers.mySeed().setFlagAcceptRemoteIndex(false);
    			this.sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, true);
    		}
    	}

    	else {
    		if(this.sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, false)) { // we were wrong!
    			log.info("enabling index receive");
    			this.sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true);
    			this.sb.peers.mySeed().setFlagAcceptRemoteIndex(true);
    			this.sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, false);
    		}
    		log.info("resources ok");
    	}
    }

    /**
     * returns the amount of disk space available
     * @return <ul>
     * <li><code>HIGH</code> if disk space is available</li>
     * <li><code>MEDIUM</code> if low disk space is available</li>
     * <li><code>LOW</code> if lower than hardlimit disk space is available</li>
     * </ul>
     */
    private Space getNormalizedDiskFree() {
        final long currentSpace = this.path.getUsableSpace();
    	//final long currentSpace = getUsableSpace(this.path);
    	if (currentSpace < 1L) return Space.HIGH;
    	Space ret = Space.HIGH;

    	if (currentSpace < getMinFreeDiskSpace()) {
    		log.warn("Volume " + this.path.toString() + ": free space (" + (currentSpace / 1024 / 1024) + " MB) is low (< " + (getMinFreeDiskSpace() / 1024 / 1024) + " MB)");
    		ret = Space.MEDIUM;
    	}
    	if (currentSpace < getMinFreeDiskSpace_hardlimit()) {
            log.warn("Volume " + this.path.toString() + ": free space (" + (currentSpace / 1024 / 1024) + " MB) is too low (< " + (getMinFreeDiskSpace() / 1024 / 1024) + " MB)");
    		ret = Space.LOW;
    	}
    	return ret;
    }

    private Space getNormalizedMemoryFree() {
    	if(MemoryControl.properState()) return Space.HIGH;
    	
        // clear some caches - @all: are there more of these, we could clear here?
		this.sb.index.clearCaches();
        SearchEventCache.cleanupEvents(true);
        this.sb.trail.clear();
        Switchboard.urlBlacklist.clearblacklistCache();
        WordCache.clearCommonWords();
        Domains.clear();
        
    	return MemoryControl.properState()? Space.HIGH : Space.LOW;
    }

    /**
     * @return <code>true</code> if disk space is available
     */
    public boolean getDiskAvailable() {
        return this.normalizedDiskFree == Space.HIGH;
    }

    /**
     * @return <code>true</code> if memory is available
     */
    public boolean getMemoryAvailable() {
        return this.normalizedMemoryFree == Space.HIGH;
    }

    /**
     * @return amount of space (bytes) that should be kept free
     */
    public long getMinFreeDiskSpace() {
        return this.sb.getConfigLong(SwitchboardConstants.DISK_FREE, 3000) /* MiB */ * 1024L * 1024L;
    }

    /**
     * @return amount of space (bytes) that should at least be kept free
     */
    public long getMinFreeDiskSpace_hardlimit() {
        return this.sb.getConfigLong(SwitchboardConstants.DISK_FREE_HARDLIMIT, 100) /* MiB */ * 1024L * 1024L;
    }

    /**
     * @return amount of space (MiB) that should at least be free
     */
    public long getMinFreeMemory() {
    	return this.sb.getConfigLong(SwitchboardConstants.MEMORY_ACCEPTDHT, 0);
    }


	/**
	 * This method calls File.getUsableSpace() from Java 6.
	 * @param file the path to be checked
	 * @return "The number of available bytes on the partition or 0L  if the abstract pathname does not name a partition." -1L on error.
	 * @author lotus at mail.berlios.de
	 */
    /**
	public static long getUsableSpace(final File file) {
		 try {
			final Class<?> File6 = Class.forName("java.io.File");
			final Class<?>[] param = {File.class, String.class };
			final Constructor<?> File6Constructor = File6.getConstructor(param);
			final Object file6 = File6Constructor.newInstance(file, "");
			final Method getFreeSpace = file6.getClass().getMethod("getUsableSpace", (Class[])null);
			final Object space = getFreeSpace.invoke(file6, (Object[])null);
			return Long.parseLong(space.toString());
		} catch (final Throwable e) {
			return -1L;
		}
	}
    */
}
