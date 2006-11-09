// AbstractCrawlWorker.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
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


package de.anomic.plasma.crawler;

import java.io.File;
import java.io.IOException;

import de.anomic.index.indexURL;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaCrawlLoaderMessage;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.bitfield;
import de.anomic.yacy.yacyCore;

public abstract class AbstractCrawlWorker extends Thread implements plasmaCrawlWorker {
    
    /**
     * The protocol that is supported by this crawler
     * e.g. <code>http</code>, <code>ftp</code>, etc.
     */
    protected String protocol;
    
    /* ============================================================
     * Variables for thread pool management
     * ============================================================ */
    public boolean destroyed = false;
    protected boolean running = false;
    protected boolean stopped = false;
    /**
     * Specifies that the execution of the current crawl job has finished
     */
    protected boolean done = false;       
    
    
    /* ============================================================
     * Crawl job specific variables
     * ============================================================ */    
    public plasmaCrawlLoaderMessage theMsg;
    protected URL url;
    protected String name;
    protected String refererURLString;
    protected String initiator;
    protected int depth;
    protected long startdate;
    protected plasmaCrawlProfile.entry profile;  
    protected boolean acceptAllContent;
    protected boolean keepInMemory;
    
    protected String errorMessage;
    
    /**
     * The crawler thread pool
     */
    protected final plasmaCrawlerPool myPool;
    
    /**
     * reference to the plasma switchboard
     */
    protected final plasmaSwitchboard sb;
    
    /**
     * reference to the cache manager
     */
    protected final plasmaHTCache   cacheManager;
    
    /**
     * Logging class
     */
    protected final serverLog       log;
    

    /**
     * Constructor of this class
     * @param theTG the crawl worker thread group
     * @param thePool the crawl worker thread pool
     * @param theSb plasma switchboard
     * @param theCacheManager cache manager
     * @param theLog server log
     */
    public AbstractCrawlWorker(
            ThreadGroup theTG,
            plasmaCrawlerPool thePool,
            plasmaSwitchboard theSb,
            plasmaHTCache theCacheManager,
            serverLog theLog
    ) {
        super(theTG,plasmaCrawlWorker.threadBaseName + "_created");

        this.myPool = thePool;
        this.sb = theSb;
        this.cacheManager = theCacheManager;
        this.log = theLog;
    }
        
    public void setNameTrailer(String trailer) {
        this.setName(plasmaCrawlWorker.threadBaseName + trailer);
    }
    
    public plasmaCrawlLoaderMessage getMessage() {
        return this.theMsg;
    }
    
    public abstract void close();
    
    public long getDuration() {
        final long startDate = this.startdate;
        return (startDate != 0) ? System.currentTimeMillis() - startDate : 0;
    }    
    
    public void run() {
        this.running = true;

        try {
            // The thread keeps running.
            while (!this.stopped && !this.isInterrupted()) {
                if (this.done) {  
                    if (this.myPool != null && !this.myPool.isClosed) {
                        synchronized (this) { 
                            // return thread back into pool
                            this.myPool.returnObject(this.protocol,this);

                            // We are waiting for a new task now.
                            if (!this.stopped && !this.destroyed && !this.isInterrupted()) { 
                                this.wait(); 
                            }
                        }
                    } else {
                        this.stopped = true;
                    }
                } else {
                    try {
                        // executing the new task
                        execute();
                    } finally {
                        // free memory
                        reset();
                    }
                }
            }
        } catch (InterruptedException ex) {
            serverLog.logFiner("CRAWLER-POOL","Interruption of thread '" + this.getName() + "' detected."); 
        } finally {
            if (this.myPool != null && !this.destroyed) 
                this.myPool.invalidateObject(this.protocol,this);
        }
    }    
    
    public void execute() {
        
        plasmaHTCache.Entry loadedResource = null;
        try {
            // setting threadname
            this.setName(plasmaCrawlWorker.threadBaseName + "_" + this.url);

            // load some configuration variables
            init();

            // loading resource
            loadedResource = load();
        } catch (IOException e) {
            //throw e;
        } finally {                        
            // setting the error message (if available)
            if (this.errorMessage != null) {
                this.theMsg.setError(this.errorMessage);
            }
            
            // store a reference to the result in the message object
            // this is e.g. needed by the snippet fetcher
            //
            // Note: this is always called, even on empty results.
            //       Otherwise the caller will block forever
            this.theMsg.setResult(loadedResource);            
            
            // signal that this worker thread has finished the job
            this.done = true;
        }
    }    
    
    public void execute(plasmaCrawlLoaderMessage theNewMsg) {
        synchronized (this) {

            this.theMsg = theNewMsg;

            this.url = theNewMsg.url;
            this.name = theNewMsg.name;
            this.refererURLString = theNewMsg.referer;
            this.initiator = theNewMsg.initiator;
            this.depth = theNewMsg.depth;
            this.profile = theNewMsg.profile;
            this.acceptAllContent = theNewMsg.acceptAllContent;
            this.keepInMemory = theNewMsg.keepInMemory;

            this.startdate = System.currentTimeMillis();

            this.done = false;

            if (!this.running) {
                // if the thread is not running until yet, we need to start it now
                this.start();
            }  else {
                // inform the thread about the new crawl job
                this.notifyAll();
            }
        }
    }    
    
    public void setStopped(boolean isStopped) {
        this.stopped = isStopped;           
    }
    
    public void setDestroyed(boolean isDestroyed) {
        this.destroyed = isDestroyed;
    }

    public boolean isRunning() {
        return this.running;
    }
    
    public void reset() {
        this.theMsg = null;
        
        this.url = null;
        this.name = null;
        this.refererURLString = null;
        this.initiator = null;
        this.depth = 0;
        this.startdate = 0;
        this.profile = null;
        this.acceptAllContent = false;
        this.keepInMemory = false;
        
        this.errorMessage = null;
    }    
    
    protected void addURLtoErrorDB(String failreason) { 
        // remember error message
        this.errorMessage = failreason;
        
        // convert the referrer URL into a hash value
        String referrerHash = (this.refererURLString==null)?null:indexURL.urlHash(this.refererURLString);
        
        // create a new errorURL DB entry
        plasmaCrawlEURL.Entry ee = this.sb.urlPool.errorURL.newEntry(
                this.url,
                referrerHash,
                this.initiator,
                yacyCore.seedDB.mySeed.hash,
                this.name,
                (failreason==null)?"Unknown reason":failreason,
                new bitfield()
        );
        
        // store the entry
        ee.store();
        
        // push it onto the stack
        this.sb.urlPool.errorURL.stackPushEntry(ee);
        
        // delete the cache file
        File cacheFile = this.cacheManager.getCachePath(this.url);
        if (cacheFile.exists()) cacheFile.delete();
    }    
}
