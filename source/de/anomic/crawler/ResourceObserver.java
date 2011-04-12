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

package de.anomic.crawler;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;

public class ResourceObserver {

    public static final Log log = new Log("RESOURCE OBSERVER");
    
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
        log.logInfo("path for disc space measurement: " + this.path);
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
    	MemoryControl.setDHTMbyte(getMinFreeMemory());

    	normalizedDiskFree = getNormalizedDiskFree();
    	normalizedMemoryFree = getNormalizedMemoryFree();

    	if (normalizedDiskFree.compareTo(Space.HIGH) < 0 || normalizedMemoryFree.compareTo(Space.HIGH) < 0 ) {

    		if (normalizedDiskFree.compareTo(Space.HIGH) < 0 ) { // pause crawls
    			if (!sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) {
    				log.logInfo("pausing local crawls");
    				sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
    			}
    			if (!sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL)) {
    				log.logInfo("pausing remote triggered crawls");
    				sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
    			}
    		}

    		if ((normalizedDiskFree == Space.LOW || normalizedMemoryFree.compareTo(Space.HIGH) < 0) && sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false)) {
    			log.logInfo("disabling index receive");
    			sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
    			sb.peers.mySeed().setFlagAcceptRemoteIndex(false);
    			sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, true);
    		}
    	}
    	
    	else {
    		if(sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, false)) { // we were wrong!
    			log.logInfo("enabling index receive");
    			sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true);
    			sb.peers.mySeed().setFlagAcceptRemoteIndex(true);
    			sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, false);
    		}
    		log.logInfo("resources ok");
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
    	final long currentSpace = getUsableSpace(this.path);
    	if (currentSpace < 1L) return Space.HIGH;
    	Space ret = Space.HIGH;
    	
    	if (currentSpace < getMinFreeDiskSpace()) {
    		log.logWarning("Volume " + this.path.toString() + ": free space (" + (currentSpace / 1024 / 1024) + " MB) is too low (< " + (getMinFreeDiskSpace() / 1024 / 1024) + " MB)");
    		ret = Space.MEDIUM;
    	}
    	if (currentSpace < getMinFreeDiskSpace_hardlimit()) {
    		ret = Space.LOW;
    	}
    	return ret;
    }
    
    private Space getNormalizedMemoryFree() {
    	if(!MemoryControl.getDHTallowed()) return Space.LOW;
        return Space.HIGH;
    }
    
    /**
     * @return <code>true</code> if disk space is available
     */
    public boolean getDiskAvailable() {
        return normalizedDiskFree == Space.HIGH;
    }
    
    /**
     * @return <code>true</code> if memory is available
     */
    public boolean getMemoryAvailable() {
        return normalizedMemoryFree == Space.HIGH;
    }
    
    /**
     * @return amount of space (bytes) that should be kept free
     */
    public long getMinFreeDiskSpace() {
        return sb.getConfigLong(SwitchboardConstants.DISK_FREE, 3000) /* MiB */ * 1024L * 1024L;
    }
    
    /**
     * @return amount of space (bytes) that should at least be kept free
     */
    public long getMinFreeDiskSpace_hardlimit() {
        return sb.getConfigLong(SwitchboardConstants.DISK_FREE_HARDLIMIT, 100) /* MiB */ * 1024L * 1024L;
    }
    
    /**
     * @return amount of space (MiB) that should at least be free
     */
    public long getMinFreeMemory() {
    	return sb.getConfigLong(SwitchboardConstants.MEMORY_ACCEPTDHT, 0);
    }
    
    
	/**
	 * This method calls File.getUsableSpace() from Java 6.
	 * @param file the path to be checked
	 * @return "The number of available bytes on the partition or 0L  if the abstract pathname does not name a partition." -1L on error.
	 * @author lotus at mail.berlios.de
	 */
	public static long getUsableSpace(final File file) {
		 try {
			final Class<?> File6 = Class.forName("java.io.File");
			final Class<?>[] param = {File.class, String.class };
			final Constructor<?> File6Constructor = File6.getConstructor(param);
			final Object file6 = File6Constructor.newInstance(file, "");
			final Method getFreeSpace = file6.getClass().getMethod("getUsableSpace", (Class[])null);
			final Object space = getFreeSpace.invoke(file6, (Object[])null);
			return Long.parseLong(space.toString());
		} catch (Throwable e) {
			return -1L;
		}
	}
    
}
