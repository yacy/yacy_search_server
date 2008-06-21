// resourceObserver.java
// -----------------------
// part of YaCy
// (C) by Detlef Reichl; detlef!reichl()gmx!org
// Pforzheim, Germany, 2008
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.util.HashMap;
import java.util.Map;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.diskUsage;

public final class resourceObserver {
    // The minimal free space on every used volume, for now set to 100 MB.
    // TODO make it configurable
    private final static long MIN_FREE_DISK_SPACE = 100L /* MiB */ * 1024L * 1024L;
    // Unknown for now
    private final static long MIN_FREE_MEMORY = 0;
    // We are called with the cleanup job every five minutes;
    // the disk usage should be checked with every run
    private final int CHECK_DISK_USAGE_FREQ = 1;
    // The memory usage should be checked on every run
    private final int CHECK_MEMORY_USAGE_FREQ = 1;
    
    private final serverLog log = new serverLog("RESOURCE OBSERVER");
    private final diskUsage du;
    private final plasmaSwitchboard sb;

    private int checkDiskUsageCount;
    private int checkMemoryUsageCount;
    private boolean disksOK;
    private boolean memoryOK;
    
    public resourceObserver(final plasmaSwitchboard sb) {
        this.sb = sb;
        this.log.logInfo("initializing the resource observer");
        du = new diskUsage(sb);
        
        if (!du.isUsable ())
            this.log.logWarning("Disk usage returned: " + du.getErrorMessage());
        
        checkDiskUsageCount = 0;
        checkMemoryUsageCount = 0;
        disksOK = true;
        memoryOK = true;
    }

    public void resourceObserverJob() {
        checkDiskUsageCount++;
        checkMemoryUsageCount++;
        boolean tmpDisksOK = true;
        boolean tmpMemoryOK = true;
        if (checkDiskUsageCount >= CHECK_DISK_USAGE_FREQ) {
            checkDiskUsageCount = 0;
            tmpDisksOK = checkDisks();
            disksOK = tmpDisksOK;
        }
        if (checkMemoryUsageCount >= CHECK_MEMORY_USAGE_FREQ) {
            checkMemoryUsageCount = 0;
            tmpMemoryOK = checkMemory();
            memoryOK = tmpMemoryOK;
        }
        
        if (!tmpDisksOK || !tmpMemoryOK) {
            if (!sb.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL)) {
                this.log.logInfo("disabling local crawls");
                sb.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
            }
            if (!sb.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL)) {
                this.log.logInfo("disabling remote triggered crawls");
                sb.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            }
        }
        else {
            if (du.isUsable ())
                this.log.logInfo("run completed; everything in order");
            else
                this.log.logInfo("The observer is out of order");
        }
    }
    
    public boolean getDisksOK () {
        return disksOK;
    }
    
    public boolean getMemoryOK () {
        return memoryOK;
    }
    
    /**
     * @return amount of space that should be kept free
     */
    public long getMinFreeDiskSpace () {
        return MIN_FREE_DISK_SPACE;
    }
    
    /**
     * @return enough disk space availabe?
     */
    private boolean checkDisks() {
        boolean below = false;    
    
        if (!du.isUsable ())
            return true;
        
        final HashMap<String, long[]> usage = du.getDiskUsage();
        long[] val;
        for (Map.Entry<String, long[]> entry: usage.entrySet()) {
            val = entry.getValue();
            if (val[1] < MIN_FREE_DISK_SPACE) {
                this.log.logWarning("Volume " + entry.getKey() + ": free space is too low");
                below = true;
            }
        }
        return !below;
    }
    
    private boolean checkMemory() {
        return true;
    }
}

