// ResourceObserver.java
// -----------------------
// part of YaCy
// (C) by Detlef Reichl; detlef!reichl()gmx!org
// Pforzheim, Germany, 2008
//
// changes by David Wieditz
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


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.diskUsage;

public final class ResourceObserver {
    // Unknown for now
    //private final static long MIN_FREE_MEMORY = 0;
    // We are called with the cleanup job every five minutes;
    // the disk usage should be checked with every run
    private static final int CHECK_DISK_USAGE_FREQ = 1;
    // The memory usage should be checked on every run
    private static final int CHECK_MEMORY_USAGE_FREQ = 1;
    
    // return values for available disk/memory
    private static final int LOW = 0;
    private static final int MEDIUM = 1;
    private static final int HIGH = 2;
    
    private final serverLog log = new serverLog("RESOURCE OBSERVER");
    private final plasmaSwitchboard sb;

    private int checkDiskUsageCount;
    private int checkMemoryUsageCount;
    private int disksFree;
    private int memoryFree;
    
    /**
     * The ResourceObserver checks the resources
     * and pauses crawls if necessary
     * @param sb the plasmaSwitchboard
     */
    public ResourceObserver(final plasmaSwitchboard sb) {
        this.sb = sb;
        this.log.logInfo("initializing the resource observer");

        final ArrayList<String> pathsToCheck = new ArrayList<String>();
        //  FIXME whats about the secondary path???
        //   = (getConfig(plasmaSwitchboard.INDEX_SECONDARY_PATH, "");
        final String[] pathes =  {plasmaSwitchboardConstants.HTDOCS_PATH,        
                            plasmaSwitchboardConstants.INDEX_PRIMARY_PATH,
                            plasmaSwitchboardConstants.LISTS_PATH,
                            plasmaSwitchboardConstants.PLASMA_PATH,
                            plasmaSwitchboardConstants.RANKING_PATH,
                            plasmaSwitchboardConstants.WORK_PATH};
        String path;
        for (final String element : pathes) {
            try {
                path = sb.getConfigPath(element, "").getCanonicalPath();
                if (path.length() > 0) pathsToCheck.add(path);
            } catch (final IOException e) {}
        }
        
        diskUsage.init(pathsToCheck);
        
        if (!diskUsage.isUsable())
            this.log.logWarning("Disk usage returned: " + diskUsage.getErrorMessage());
        
        checkDiskUsageCount = 0;
        checkMemoryUsageCount = 0;
        disksFree = HIGH;
        memoryFree = HIGH;
    }

    /**
     * checks the resources and pauses crawls if necessary
     */
    public void resourceObserverJob() {
        checkDiskUsageCount++;
        checkMemoryUsageCount++;
        int tmpDisksFree = HIGH;
        int tmpMemoryFree = HIGH;
        if (checkDiskUsageCount >= CHECK_DISK_USAGE_FREQ) {
            checkDiskUsageCount = 0;
            tmpDisksFree = checkDisks();
            disksFree = tmpDisksFree;
        }
        if (checkMemoryUsageCount >= CHECK_MEMORY_USAGE_FREQ) {
            checkMemoryUsageCount = 0;
            tmpMemoryFree = checkMemory();
            memoryFree = tmpMemoryFree;
        }
        
        if (tmpDisksFree < HIGH || tmpMemoryFree < HIGH) {
            if (!sb.crawlJobIsPaused(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) {
                this.log.logInfo("pausing local crawls");
                sb.pauseCrawlJob(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
            }
            if (!sb.crawlJobIsPaused(plasmaSwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL)) {
                this.log.logInfo("pausing remote triggered crawls");
                sb.pauseCrawlJob(plasmaSwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            }
            if (tmpDisksFree == LOW && sb.getConfigBool(plasmaSwitchboardConstants.INDEX_RECEIVE_ALLOW, false)) {
            	this.log.logInfo("disabling index receive");
                sb.setConfig(plasmaSwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
                sb.webIndex.seedDB.mySeed().setFlagAcceptRemoteIndex(false);
            }
        }
        else {
            if (diskUsage.isUsable())
                this.log.logInfo("run completed; everything in order");
            else
                this.log.logInfo("The observer is out of order: " + diskUsage.getErrorMessage());
        }
    }
    
    /**
     * @return <code>true</code> if disk space is available
     */
    public boolean getDisksOK () {
        return disksFree == HIGH;
    }
    
    /**
     * @return <code>true</code> if memory is available
     */
    public boolean getMemoryOK () {
        return memoryFree == HIGH;
    }
    
    /**
     * @return amount of space (MiB) that should be kept free
     */
    public long getMinFreeDiskSpace () {
        return sb.getConfigLong("disk.free", 3000) /* MiB */ * 1024L * 1024L;
    }
    
    /**
     * returns the amount of disk space available
     * @return <ul>
     * <li><code>HIGH</code> if disk space is available</li>
     * <li><code>MEDIUM</code> if low disk space is available</li>
     * <li><code>LOW</code> if lower than 100MB or 1/5 disk space is available</li>
     * </ul>
     */
    private int checkDisks() {
        int ret = HIGH;   
    
        if (!diskUsage.isUsable ())
            return HIGH;
        
        final HashMap<String, long[]> usage = diskUsage.getDiskUsage();
        long[] val;
        for (final Map.Entry<String, long[]> entry: usage.entrySet()) {
            val = entry.getValue();
            this.log.logInfo("df of Volume " + entry.getKey() + ": " + (val[1] / 1024 / 1024) + " MB");
            if (val[1] < getMinFreeDiskSpace()) {
                this.log.logWarning("Volume " + entry.getKey() + ": free space (" + (val[1] / 1024 / 1024) + " MB) is too low (< " + (getMinFreeDiskSpace() / 1024 / 1024) + " MB)");
                ret = MEDIUM;
            }
            if (val[1] < Math.min(getMinFreeDiskSpace() / 5L, 100L)) {
            	ret = LOW;
            }
        }
        return ret;
    }
    
    private int checkMemory() {
        return HIGH;
    }
}

