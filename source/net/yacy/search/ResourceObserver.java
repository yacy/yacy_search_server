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
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.ResultURLs;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.rwi.IndexCell;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.NewsPool;
import net.yacy.search.query.SearchEventCache;

public class ResourceObserver {

    public static final ConcurrentLog log = new ConcurrentLog("RESOURCE OBSERVER");

    // status type for which shows where in the control-circuit model a memory state can be categorized
    public enum Space implements Comparable<Space> {
        EXHAUSTED, // smallest space state, outside of over/undershot
        NOMINAL,   // wanted-space state between steady-state and under/overshot 
        AMPLE;     // largest space state, below steady-state
    }

    private final Switchboard sb;
    private final File path; // path to check

    private Space normalizedDiskFree = Space.AMPLE;
    private Space normalizedDiskUsed = Space.AMPLE;
    private Space normalizedMemoryFree = Space.AMPLE;

    public ResourceObserver(final Switchboard sb) {
        this.sb = sb;
        this.path = sb.getDataPath(SwitchboardConstants.INDEX_PRIMARY_PATH, "").getParentFile();
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
        this.normalizedDiskUsed = getNormalizedDiskUsed(true);
    	this.normalizedMemoryFree = getNormalizedMemoryFree();

    	// take actions if disk space is below AMPLE
        if (this.normalizedDiskFree != Space.AMPLE ||
            this.normalizedDiskUsed != Space.AMPLE ||
            this.normalizedMemoryFree != Space.AMPLE ) {
    	    String reason = "";
            if (this.normalizedDiskFree != Space.AMPLE) reason += " not enough disk space, " + getUsableSpace();
            if (this.normalizedDiskUsed != Space.AMPLE) reason += " too high disk usage, " + getNormalizedDiskUsed(true);
            if (this.normalizedMemoryFree != Space.AMPLE ) reason += " not enough memory space";
			if (!this.sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) {
				log.info("pausing local crawls");
				this.sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL, "resource observer:" + reason);
			}
			if (!this.sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL)) {
				log.info("pausing remote triggered crawls");
				this.sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL, "resource observer:" + reason);
			}

    		if ((this.normalizedDiskFree == Space.EXHAUSTED || this.normalizedMemoryFree != Space.AMPLE) && this.sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false)) {
    			log.info("disabling index receive");
    			this.sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
    			this.sb.peers.mySeed().setFlagAcceptRemoteIndex(false);
    			this.sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, true);
    		}
    	}

        // shrink resources if space is EXHAUSTED
        if ((this.normalizedDiskFree == Space.EXHAUSTED && this.sb.getConfigBool(SwitchboardConstants.RESOURCE_DISK_FREE_AUTOREGULATE, false)) ||
            (this.normalizedDiskUsed == Space.EXHAUSTED && this.sb.getConfigBool(SwitchboardConstants.RESOURCE_DISK_USED_AUTOREGULATE, false))) {
            shrinkmethods: while (true /*this is not a loop, just a construct that we can leave with a break*/) {
                // delete old releases
                //if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                
                // delete fetched snippets
                log.info("DISK SPACE EXHAUSTED - deleting snippet cache");
                sb.tables.clear(WorkTables.TABLE_SEARCH_FAILURE_NAME);
                if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                
                // clear HTCACHE
                log.info("DISK SPACE EXHAUSTED - deleting HTCACHE");
                Cache.clear();
                if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                
                // delete logs
                //if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                
                // delete robots.txt
                log.info("DISK SPACE EXHAUSTED - deleting robots.txt database");
                try {sb.robots.clear();} catch (final IOException e) {}
                if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                
                // delete news
                log.info("DISK SPACE EXHAUSTED - deleting News database");
                sb.peers.newsPool.clear(NewsPool.INCOMING_DB); sb.peers.newsPool.clear(NewsPool.PROCESSED_DB);
                sb.peers.newsPool.clear(NewsPool.OUTGOING_DB); sb.peers.newsPool.clear(NewsPool.PUBLISHED_DB);
                if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                
                // clear citations
                if (sb.index.connectedCitation()) {
                    log.info("DISK SPACE EXHAUSTED - deleting citations");
                    try {sb.index.urlCitation().clear();} catch (final IOException e) {}
                    if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                }
                
                // throw away crawl queues, if they are large
                if (sb.crawlQueues.coreCrawlJobSize() > 1000) {
                    log.info("DISK SPACE EXHAUSTED - deleting crawl queues");
                    sb.crawlQueues.clear();
                    sb.crawlStacker.clear();
                    ResultURLs.clearStacks();
                    if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                }
                
                // cut away too large RWIs
                IndexCell<WordReference> termIndex = sb.index.termIndex();
                try {
                    int shrinkedReferences = termIndex.deleteOld(100, 10000);
                    if (shrinkedReferences > 0) {
                        log.info("DISK SPACE EXHAUSTED - shrinked " + shrinkedReferences + " RWI references to a maximum of 100");
                        if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                    }
                } catch (IOException e) {
                }
                
                // delete too old RWIs
                //if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                
                // delete fulltext from large Solr documents
                //if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                
                // run a solr optimize
                this.sb.index.fulltext().commit(false);
                this.sb.index.fulltext().optimize(1);
                if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break shrinkmethods;
                
                /*
                // delete old Solr documents
                long day = 1000 * 60 * 60 * 24;
                for (int t = 12; t >= 1 ; t --) {
                    log.info("DISK SPACE EXHAUSTED - deleting documents with loaddate > " + t + " months");
                    this.sb.index.fulltext().deleteOldDocuments(t * 30 * day, true);
                    this.sb.index.fulltext().commit(false);
                    this.sb.index.fulltext().optimize(1);
                    if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break shrinkmethods;
                }
                for (int t = 30; t > 3 ; t --) {
                    log.info("DISK SPACE EXHAUSTED - deleting documents with loaddate > " + t + " days");
                    this.sb.index.fulltext().deleteOldDocuments(t * day, true);
                    this.sb.index.fulltext().commit(false);
                    this.sb.index.fulltext().optimize(1);
                    if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break shrinkmethods;
                }
                */
                
                // WE SHOULD NEVER GET UP TO HERE...
                /*
                // delete ALL RWIs
                if (sb.index.termIndex() != null) {
                    try {sb.index.termIndex().clear();} catch (final IOException e) {}
                    //if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                }
                
                // delete full Solr
                try {sb.index.fulltext().clearLocalSolr();} catch (final IOException e) {}
                //if (getNormalizedDiskFree() == Space.AMPLE && getNormalizedDiskUsed(false) == Space.AMPLE) break;
                */
                break; // DO NOT REMOVE THIS, the loop may run forever. It shall run only once.
            }
            this.normalizedDiskFree = getNormalizedDiskFree();
            this.normalizedDiskUsed = getNormalizedDiskUsed(false);
            this.normalizedMemoryFree = getNormalizedMemoryFree();
        }

        // normalize state if the resources are AMPLE
        if (this.normalizedDiskFree == Space.AMPLE && this.normalizedDiskUsed == Space.AMPLE && this.normalizedMemoryFree == Space.AMPLE ) {
            if(this.sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, false)) { // we were wrong!
                log.info("enabling index receive");
                this.sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true);
                this.sb.peers.mySeed().setFlagAcceptRemoteIndex(true);
                this.sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, false);
            }
            log.info("resources ok");
        }
        
    }

    private long sizeOfDirectory_lastCountTime = 0;
    private long sizeOfDirectory_lastCountValue = 0;
    public long getSizeOfDataPath(final boolean cached) {
        if (cached && System.currentTimeMillis() - this.sizeOfDirectory_lastCountTime < 600000) return this.sizeOfDirectory_lastCountValue;
        this.sizeOfDirectory_lastCountTime = System.currentTimeMillis();
        try {
            this.sizeOfDirectory_lastCountValue = FileUtils.sizeOfDirectory(this.path);
        } catch (Throwable e) {} // org.apache.commons.io.FileUtils.sizeOf calls sizes of files which are there temporary and may cause an exception. Thats a bug inside FileUtils
        return this.sizeOfDirectory_lastCountValue;
    }
    
    public long getUsableSpace() {
        return this.path.getUsableSpace();
    }
    
    private Space getNormalizedDiskUsed(final boolean cached) {
        final long currentUsed = getSizeOfDataPath(cached);
        //final long currentSpace = getUsableSpace(this.path);
        if (currentUsed < 1L) return Space.AMPLE;
        Space ret = Space.AMPLE;

        if (currentUsed > getMaxUsedDiskOvershot()) {
            log.warn("Volume " + this.path.toString() + ": used space (" + (currentUsed / 1024 / 1024) + " MB) is too high (> " + (getMaxUsedDiskOvershot() / 1024 / 1024) + " MB)");
            ret = Space.EXHAUSTED;
            return ret;
        }
        if (currentUsed > getMaxUsedDiskSteadystate()) {
            log.info("Volume " + this.path.toString() + ": used space (" + (currentUsed / 1024 / 1024) + " MB) is high, but nominal (> " + (getMaxUsedDiskSteadystate() / 1024 / 1024) + " MB)");
            ret = Space.NOMINAL;
            return ret;
        }
        return ret;
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
        final long currentSpace = getUsableSpace();
    	//final long currentSpace = getUsableSpace(this.path);
    	if (currentSpace < 1L) return Space.AMPLE; // this happens if the function does not work, like on Windows
    	Space ret = Space.AMPLE;

        if (currentSpace < getMinFreeDiskUndershot()) {
            log.warn("Volume " + this.path.toString() + ": free space (" + (currentSpace / 1024 / 1024) + " MB) is too low (< " + (getMinFreeDiskSteadystate() / 1024 / 1024) + " MB)");
            ret = Space.EXHAUSTED;
            return ret;
        }
    	if (currentSpace < getMinFreeDiskSteadystate()) {
    		log.info("Volume " + this.path.toString() + ": free space (" + (currentSpace / 1024 / 1024) + " MB) is low, but nominal (< " + (getMinFreeDiskSteadystate() / 1024 / 1024) + " MB)");
    		ret = Space.NOMINAL;
            return ret;
    	}
    	return ret;
    }

    private Space getNormalizedMemoryFree() {
    	if(MemoryControl.properState()) return Space.AMPLE;
    	
        // clear some caches - @all: are there more of these, we could clear here?
		this.sb.index.clearCaches();
        SearchEventCache.cleanupEvents(true);
        this.sb.trail.clear();
        Switchboard.urlBlacklist.clearblacklistCache();
        WordCache.clearCommonWords();
        Domains.clear();
        
    	return MemoryControl.properState()? Space.AMPLE : Space.EXHAUSTED;
    }

    /**
     * @return <code>true</code> if disk space is available
     */
    public boolean getDiskAvailable() {
        return this.normalizedDiskFree == Space.AMPLE;
    }

    /**
     * @return <code>true</code> if memory is available
     */
    public boolean getMemoryAvailable() {
        return this.normalizedMemoryFree == Space.AMPLE;
    }

    /**
     * @return amount of space (bytes) that should be used in steady state
     */
    public long getMaxUsedDiskSteadystate() {
        return this.sb.getConfigLong(SwitchboardConstants.RESOURCE_DISK_USED_MAX_STEADYSTATE, 524288) /* MB */ * 1024L * 1024L;
    }

    /**
     * @return amount of space (bytes) that should at least be kept free as hard limit; the limit when autoregulation to steady state should start
     */
    public long getMaxUsedDiskOvershot() {
        return this.sb.getConfigLong(SwitchboardConstants.RESOURCE_DISK_USED_MAX_OVERSHOT, 1048576) /* MB */ * 1024L * 1024L;
    }
    
    /**
     * @return amount of space (bytes) that should be kept free as steady state
     */
    public long getMinFreeDiskSteadystate() {
        return this.sb.getConfigLong(SwitchboardConstants.RESOURCE_DISK_FREE_MIN_STEADYSTATE, 2048) /* MB */ * 1024L * 1024L;
    }

    /**
     * @return amount of space (bytes) that should at least be kept free as hard limit; the limit when autoregulation to steady state should start
     */
    public long getMinFreeDiskUndershot() {
        return this.sb.getConfigLong(SwitchboardConstants.RESOURCE_DISK_FREE_MIN_UNDERSHOT, 1024) /* MB */ * 1024L * 1024L;
    }

    /**
     * @return amount of space (MiB) that should at least be free
     */
    public long getMinFreeMemory() {
    	return this.sb.getConfigLong(SwitchboardConstants.MEMORY_ACCEPTDHT, 0);
    }

}
