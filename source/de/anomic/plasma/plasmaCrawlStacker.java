// plasmaCrawlStacker.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// This file was contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.data.robotsParser;
import de.anomic.http.httpc;
import de.anomic.index.indexURL;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroFlexTable;
import de.anomic.kelondro.kelondroFlexWidthArray;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.kelondro.kelondroTree;
import de.anomic.net.URL;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverSemaphore;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.bitfield;
import de.anomic.yacy.yacyCore;

public final class plasmaCrawlStacker {
    
    // keys for different database types
    public static final int QUEUE_DB_TYPE_RAM  = 0;
    public static final int QUEUE_DB_TYPE_TREE = 1;
    public static final int QUEUE_DB_TYPE_FLEX = 2;
    
    final WorkerPool theWorkerPool;
    private GenericObjectPool.Config theWorkerPoolConfig = null; 
    final ThreadGroup theWorkerThreadGroup = new ThreadGroup("stackCrawlThreadGroup");
    final serverLog log = new serverLog("STACKCRAWL");
    final plasmaSwitchboard sb;
    //private boolean stopped = false;
    private stackCrawlQueue queue;
    
    public plasmaCrawlStacker(plasmaSwitchboard sb, File dbPath, int dbCacheSize, long preloadTime, int dbtype) {
        this.sb = sb;
        
        this.queue = new stackCrawlQueue(dbPath, dbCacheSize, preloadTime, dbtype);
        this.log.logInfo(this.queue.size() + " entries in the stackCrawl queue.");
        this.log.logInfo("STACKCRAWL thread initialized.");
        
        // configuring the thread pool
        // implementation of session thread pool
        this.theWorkerPoolConfig = new GenericObjectPool.Config();

        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        this.theWorkerPoolConfig.maxActive = Integer.parseInt(sb.getConfig("stacker.MaxActiveThreads","50"));

        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        this.theWorkerPoolConfig.maxIdle = Integer.parseInt(sb.getConfig("stacker.MaxIdleThreads","10"));
        this.theWorkerPoolConfig.minIdle = Integer.parseInt(sb.getConfig("stacker.MinIdleThreads","5"));    

        // block undefinitely 
        this.theWorkerPoolConfig.maxWait = -1; 

        // Action to take in case of an exhausted DBCP statement pool
        // 0 = fail, 1 = block, 2= grow        
        this.theWorkerPoolConfig.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK; 
        this.theWorkerPoolConfig.minEvictableIdleTimeMillis = 30000; 
        //this.theWorkerPoolConfig.timeBetweenEvictionRunsMillis = 30000;
        
        // creating worker pool
        this.theWorkerPool = new WorkerPool(new WorkterFactory(this.theWorkerThreadGroup),this.theWorkerPoolConfig);  
        
    }
    
    public GenericObjectPool.Config getPoolConfig() {
        return this.theWorkerPoolConfig;
    }    
    
    public int getDBType() {
        return this.queue.getDBType();
    }
    
    public void setPoolConfig(GenericObjectPool.Config newConfig) {
        this.theWorkerPool.setConfig(newConfig);
    }    
    
    public void close() {
        try {
            this.log.logFine("Shutdown. Terminating worker threads.");
            if (this.theWorkerPool != null) this.theWorkerPool.close();
        } catch (Exception e1) {
            this.log.logSevere("Unable to shutdown all remaining stackCrawl threads", e1);
        }
        
        try {
            this.log.logFine("Shutdown. Closing stackCrawl queue.");
            if (this.queue != null) this.queue.close();
            this.queue = null;
        } catch (IOException e) {
            this.log.logSevere("DB could not be closed properly.", e);
        }
    }
    
    public int getNumActiveWorker() {
        return this.theWorkerPool.getNumActive();
    }
    
    public int getNumIdleWorker() {
        return this.theWorkerPool.getNumIdle();
    }
    
    public int size() {
        return this.queue.size();
    }

    public int cacheNodeChunkSize() {
        return this.queue.cacheNodeChunkSize();
    }
    
    public int cacheObjectChunkSize() {
        return this.queue.cacheObjectChunkSize();
    }
    
    public int[] cacheNodeStatus() {
        return this.queue.cacheNodeStatus();
    }
    
    public long[] cacheObjectStatus() {
        return this.queue.cacheObjectStatus();
    }
    
    public void job() {
        try {
            // getting a new message from the crawler queue
            checkInterruption();
            stackCrawlMessage theMsg = this.queue.waitForMessage();
            
            // getting a free session thread from the pool
            checkInterruption();
            Worker worker = (Worker) this.theWorkerPool.borrowObject();
            
            // processing the new request
            worker.execute(theMsg);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                this.log.logFine("Interruption detected.");
            } else if ((e instanceof IllegalStateException) && 
                       (e.getMessage() != null) && 
                       (e.getMessage().indexOf("Pool not open") >= -1)) {
                this.log.logFine("Pool was closed.");
                
            } else {
                this.log.logSevere("plasmaStackCrawlThread.run/loop", e);
            }
        }
    }
    
    public void enqueue(
            String nexturlString, 
            String referrerString, 
            String initiatorHash, 
            String name, 
            Date loadDate, 
            int currentdepth, 
            plasmaCrawlProfile.entry profile) {
        if (profile != null) try {            
            this.queue.addMessage(new stackCrawlMessage(
                    initiatorHash,
                    nexturlString,
                    referrerString,
                    name,
                    loadDate,
                    profile.handle(),
                    currentdepth,
                    0,
                    0
                    ));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public String dequeue(stackCrawlMessage theMsg) throws InterruptedException {
        
        plasmaCrawlProfile.entry profile = this.sb.profiles.getEntry(theMsg.profileHandle());
        if (profile == null) {
            String errorMsg = "LOST PROFILE HANDLE '" + theMsg.profileHandle() + "' for URL " + theMsg.url();
            this.log.logSevere(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        return stackCrawl(
                theMsg.url().toString(),
                theMsg.referrerHash(),
                theMsg.initiatorHash(),
                theMsg.name(),
                theMsg.loaddate(),
                theMsg.depth(),
                profile);
    }
    
    public void checkInterruption() throws InterruptedException {
        Thread curThread = Thread.currentThread();
        if (curThread.isInterrupted()) throw new InterruptedException("Shutdown in progress ...");
    }
    
    public String stackCrawl(String nexturlString, String referrerString, String initiatorHash, String name, Date loadDate, int currentdepth, plasmaCrawlProfile.entry profile) throws InterruptedException {
        // stacks a crawl item. The position can also be remote
        // returns null if successful, a reason string if not successful
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");
        
        long startTime = System.currentTimeMillis();
        String reason = null; // failure reason

        // getting the initiator peer hash
        if ((initiatorHash == null) || (initiatorHash.length() == 0)) initiatorHash = indexURL.dummyHash;        
        
        // strange errors
        if (nexturlString == null) {
            reason = plasmaCrawlEURL.DENIED_URL_NULL;
            this.log.logSevere("Wrong URL in stackCrawl: url=null");
            return reason;
        }
        
        // getting the referer url and url hash
        URL referrerURL = null;
        if (referrerString != null) {
            try {
                referrerURL = new URL(referrerString);
            } catch (MalformedURLException e) {
                referrerURL = null;
                referrerString = null;
            }
        }
        String referrerHash = (referrerString==null)?null:indexURL.urlHash(referrerString);

        // check for malformed urls
        URL nexturl = null;
        try {
            nexturl = new URL(nexturlString);
        } catch (MalformedURLException e) {
            reason = plasmaCrawlEURL.DENIED_MALFORMED_URL;
            this.log.logSevere("Wrong URL in stackCrawl: " + nexturlString + 
                               ". Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // check if the protocol is supported
        String urlProtocol = nexturl.getProtocol();
        if (!this.sb.cacheLoader.isSupportedProtocol(urlProtocol)) {
            reason = plasmaCrawlEURL.DENIED_UNSUPPORTED_PROTOCOL;
            this.log.logSevere("Unsupported protocol in URL '" + nexturlString + "'. " + 
                               "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;            
        }
        
        // check if ip is local ip address
        checkInterruption();        // TODO: this is protocol specific
        InetAddress hostAddress = httpc.dnsResolve(nexturl.getHost());
        if (hostAddress == null) {
            // if a http proxy is configured name resolution may not work
            if (this.sb.remoteProxyConfig == null || !this.sb.remoteProxyConfig.useProxy()) {
                reason = plasmaCrawlEURL.DENIED_UNKNOWN_HOST;
                this.log.logFine("Unknown host in URL '" + nexturlString + "'. " +
                        "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
                return reason;                
            }
        } else if (hostAddress.isSiteLocalAddress()) {
            reason = plasmaCrawlEURL.DENIED_PRIVATE_IP_ADDRESS;
            this.log.logFine("Host in URL '" + nexturlString + "' has private IP address. " +
                    "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;                
        } else if (hostAddress.isLoopbackAddress()) {
            reason = plasmaCrawlEURL.DENIED_LOOPBACK_IP_ADDRESS;
            this.log.logFine("Host in URL '" + nexturlString + "' has loopback IP address. " + 
                    "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;                  
        }
        
        // check blacklist
        checkInterruption();
        if (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_CRAWLER,nexturl)) {
            reason = plasmaCrawlEURL.DENIED_URL_IN_BLACKLIST;
            this.log.logFine("URL '" + nexturlString + "' is in blacklist. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }        
        
        // filter deny
        if ((currentdepth > 0) && (profile != null) && (!(nexturlString.matches(profile.generalFilter())))) {
            reason = plasmaCrawlEURL.DENIED_URL_DOES_NOT_MATCH_FILTER;

            this.log.logFine("URL '" + nexturlString + "' does not match crawling filter '" + profile.generalFilter() + "'. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        // deny cgi
        if (plasmaHTCache.isCGI(nexturlString))  {
            reason = plasmaCrawlEURL.DENIED_CGI_URL;

            this.log.logFine("URL '" + nexturlString + "' is CGI URL. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        // deny post properties
        if ((plasmaHTCache.isPOST(nexturlString)) && (profile != null) && (!(profile.crawlingQ())))  {
            reason = plasmaCrawlEURL.DENIED_POST_URL;

            this.log.logFine("URL '" + nexturlString + "' is post URL. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        // add domain to profile domain list
        if ((profile.domFilterDepth() != Integer.MAX_VALUE) || (profile.domMaxPages() != Integer.MAX_VALUE)) {
            profile.domInc(nexturl.getHost(), (referrerURL == null) ? null : referrerURL.getHost().toLowerCase(), currentdepth);
        }

        // deny urls that do not match with the profile domain list
        if (!(profile.grantedDomAppearance(nexturl.getHost()))) {
            reason = plasmaCrawlEURL.DENIED_NO_MATCH_WITH_DOMAIN_FILTER;
            this.log.logFine("URL '" + nexturlString + "' is not listed in granted domains. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // deny urls that exceed allowed number of occurrences
        if (!(profile.grantedDomCount(nexturl.getHost()))) {
            reason = plasmaCrawlEURL.DENIED_DOMAIN_COUNT_EXCEEDED;
            this.log.logFine("URL '" + nexturlString + "' appeared too often, a maximum of " + profile.domMaxPages() + " is allowed. "+ 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // check if the url is double registered
        checkInterruption();
        String nexturlhash = indexURL.urlHash(nexturl);
        String dbocc = this.sb.urlPool.exists(nexturlhash);
        plasmaCrawlLURLEntry oldEntry = null;
        oldEntry = this.sb.urlPool.loadedURL.load(nexturlhash, null);
        boolean recrawl = (oldEntry != null) && (((System.currentTimeMillis() - oldEntry.loaddate().getTime()) / 60000) > profile.recrawlIfOlder());
        if ((dbocc != null) && (!(recrawl))) {
            reason = plasmaCrawlEURL.DOUBLE_REGISTERED + dbocc + ")";
            //this.log.logFine("URL '" + nexturlString + "' is double registered in '" + dbocc + "'. " + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // checking robots.txt for http(s) resources
        checkInterruption();
        if ((urlProtocol.equals("http") || urlProtocol.equals("https")) && robotsParser.isDisallowed(nexturl)) {
            reason = plasmaCrawlEURL.DENIED_ROBOTS_TXT;

            this.log.logFine("Crawling of URL '" + nexturlString + "' disallowed by robots.txt. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;            
        }

        // show potential re-crawl
        if (recrawl) {
            this.log.logFine("RE-CRAWL of URL '" + nexturlString + "': this url was crawled " +
                    ((System.currentTimeMillis() - oldEntry.loaddate().getTime()) / 60000 / 60 / 24) + " days ago.");
        }
        
        // store information
        boolean local = ((initiatorHash.equals(indexURL.dummyHash)) || (initiatorHash.equals(yacyCore.seedDB.mySeed.hash)));
        boolean global = 
            (profile != null) &&
            (profile.remoteIndexing()) /* granted */ &&
            (currentdepth == profile.generalDepth()) /* leaf node */ && 
            //(initiatorHash.equals(yacyCore.seedDB.mySeed.hash)) /* not proxy */ &&
            (
                    (yacyCore.seedDB.mySeed.isSenior()) ||
                    (yacyCore.seedDB.mySeed.isPrincipal())
            ) /* qualified */;
        
        if ((!local)&&(!global)&&(!profile.handle().equals(this.sb.defaultRemoteProfile.handle()))) {
            this.log.logSevere("URL '" + nexturlString + "' can neither be crawled local nor global.");
        }
        
        // add the url into the crawling queue
        checkInterruption();
        plasmaCrawlNURL.Entry ne = this.sb.urlPool.noticeURL.newEntry(initiatorHash, /* initiator, needed for p2p-feedback */
                nexturl, /* url clear text string */
                loadDate, /* load date */
                referrerHash, /* last url in crawling queue */
                name, /* the anchor name */
                (profile == null) ? null : profile.handle(),  // profile must not be null!
                currentdepth, /*depth so far*/
                0, /*anchors, default value */
                0  /*forkfactor, default value */
        );
        ne.store();
        this.sb.urlPool.noticeURL.push(
                ((global) ? plasmaCrawlNURL.STACK_TYPE_LIMIT :
                ((local) ? plasmaCrawlNURL.STACK_TYPE_CORE : plasmaCrawlNURL.STACK_TYPE_REMOTE)) /*local/remote stack*/,
                nexturl.getHost(),
                ne.hash());
        return null;
    }
    
    public final class stackCrawlMessage {
        private String   initiator;     // the initiator hash, is NULL or "" if it is the own proxy;
        String   urlHash;          // the url's hash
        private String   referrerHash;      // the url's referrer hash
        private String   url;           // the url as string
        String   name;          // the name of the url, from anchor tag <a>name</a>     
        private Date     loaddate;      // the time when the url was first time appeared
        private String   profileHandle; // the name of the prefetch profile
        private int      depth;         // the prefetch depth so far, starts at 0
        private int      anchors;       // number of anchors of the parent
        private int      forkfactor;    // sum of anchors of all ancestors
        private bitfield flags;
        private int      handle;
        
        // loadParallel(URL url, String referer, String initiator, int depth, plasmaCrawlProfile.entry profile) {
        public stackCrawlMessage(
                String initiator, 
                String urlString, 
                String referrerUrlString, 
                String name, 
                Date loaddate, 
                String profileHandle,
                int depth, 
                int anchors, 
                int forkfactor) {
            try {
                // create new entry and store it into database
                this.urlHash       = indexURL.urlHash(urlString);
                this.initiator     = initiator;
                this.url           = urlString;
                this.referrerHash  = (referrerUrlString == null) ? indexURL.dummyHash : indexURL.urlHash(referrerUrlString);
                this.name          = (name == null) ? "" : name;
                this.loaddate      = (loaddate == null) ? new Date() : loaddate;
                this.profileHandle = profileHandle; // must not be null
                this.depth         = depth;
                this.anchors       = anchors;
                this.forkfactor    = forkfactor;
                this.flags         = new bitfield(indexURL.urlFlagLength);
                this.handle        = 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } 
        
        public stackCrawlMessage(String urlHash, kelondroRow.Entry entry) {
            if (urlHash == null) throw new NullPointerException();
            if (entry == null) throw new NullPointerException();

            try {
                this.urlHash       = urlHash;
                this.initiator     = entry.getColString(1, "UTF-8");
                this.url           = entry.getColString(2, "UTF-8").trim();
                this.referrerHash  = (entry.empty(3)) ? indexURL.dummyHash : entry.getColString(3, "UTF-8");
                this.name          = (entry.empty(4)) ? "" : entry.getColString(4, "UTF-8").trim();
                this.loaddate      = new Date(86400000 * entry.getColLong(5));
                this.profileHandle = (entry.empty(6)) ? null : entry.getColString(6, "UTF-8").trim();
                this.depth         = (int) entry.getColLong(7);
                this.anchors       = (int) entry.getColLong(8);
                this.forkfactor    = (int) entry.getColLong(9);
                this.flags         = new bitfield(entry.getColBytes(10));
                this.handle        = Integer.parseInt(new String(entry.getColBytes(11), "UTF-8"));
            } catch (Exception e) {
                e.printStackTrace();
                throw new IllegalStateException(e.toString());
            }
        }
        
        public String url() {
            return this.url;
        }        
        
        public String referrerHash() {
            return this.referrerHash;
        }
        
        public String initiatorHash() {
            if (this.initiator == null) return null;
            if (this.initiator.length() == 0) return null; 
            return this.initiator;
        }
        
        public Date loaddate() {
            return this.loaddate;
        }

        public String name() {
            return this.name;
        }

        public int depth() {
            return this.depth;
        }

        public String profileHandle() {
            return this.profileHandle;
        }        
        
        public String toString() {
            StringBuffer str = new StringBuffer();
            str.append("urlHash: ").append(urlHash==null ? "null" : urlHash).append(" | ")
               .append("initiator: ").append(initiator==null?"null":initiator).append(" | ")
               .append("url: ").append(url==null?"null":url).append(" | ")
               .append("referrer: ").append((referrerHash == null) ? indexURL.dummyHash : referrerHash).append(" | ")
               .append("name: ").append((name == null) ? "null" : name).append(" | ")
               .append("loaddate: ").append((loaddate == null) ? new Date() : loaddate).append(" | ")
               .append("profile: ").append(profileHandle==null?"null":profileHandle).append(" | ")
               .append("depth: ").append(Integer.toString(depth)).append(" | ")
               .append("forkfactor: ").append(Integer.toString(forkfactor)).append(" | ")
               //.append("flags: ").append((flags==null) ? "null" : flags.toString())
               ;
               return str.toString();
        }                      
        
        public byte[][] getBytes() {
            // stores the values from the object variables into the database
            String loaddatestr = kelondroBase64Order.enhancedCoder.encodeLong(loaddate.getTime() / 86400000, indexURL.urlDateLength);
            // store the hash in the hash cache

            // even if the entry exists, we simply overwrite it
            byte[][] entry = null;
            try {
                entry = new byte[][] { 
                    this.urlHash.getBytes(),
                    (this.initiator == null) ? "".getBytes() : this.initiator.getBytes(),
                    this.url.getBytes(),
                    this.referrerHash.getBytes(),
                    this.name.getBytes("UTF-8"),
                    loaddatestr.getBytes(),
                    (this.profileHandle == null) ? null : this.profileHandle.getBytes(),
                    kelondroBase64Order.enhancedCoder.encodeLong(this.depth, indexURL.urlCrawlDepthLength).getBytes(),
                    kelondroBase64Order.enhancedCoder.encodeLong(this.anchors, indexURL.urlParentBranchesLength).getBytes(),
                    kelondroBase64Order.enhancedCoder.encodeLong(this.forkfactor, indexURL.urlForkFactorLength).getBytes(),
                    this.flags.getBytes(),
                    normalizeHandle(this.handle).getBytes()
            };
            } catch (UnsupportedEncodingException e) { /* ignore this */ }
            return entry;
        }        
        
        private String normalizeHandle(int h) {
            String d = Integer.toHexString(h);
            while (d.length() < indexURL.urlHandleLength) d = "0" + d;
            return d;
        }
    }      
    
    final class stackCrawlQueue {
        
        private final serverSemaphore readSync;
        private final serverSemaphore writeSync;
        private final LinkedList urlEntryHashCache;
        private kelondroIndex urlEntryCache;
        private File cacheStacksPath;
        private int bufferkb;
        private long preloadTime;
        private int dbtype;
        
        public stackCrawlQueue(File cacheStacksPath, int bufferkb, long preloadTime, int dbtype) {
            // init the read semaphore
            this.readSync  = new serverSemaphore (0);
            
            // init the write semaphore
            this.writeSync = new serverSemaphore (1);
            
            // init the message list
            this.urlEntryHashCache = new LinkedList();
            
            // create a stack for newly entered entries
            this.cacheStacksPath = cacheStacksPath;
            this.bufferkb = bufferkb;
            this.preloadTime = preloadTime;
            this.dbtype = dbtype;

            openDB();
            try {
                // loop through the list and fill the messageList with url hashs
                Iterator rows = this.urlEntryCache.rows(true, false, null);
                kelondroRow.Entry entry;
                while (rows.hasNext()) {
                    entry = (kelondroRow.Entry) rows.next();
                    if (entry == null) {
                        System.out.println("ERROR! null element found");
                        continue;
                    }
                    this.urlEntryHashCache.add(entry.getColString(0, null));
                    this.readSync.V();
                }
            } catch (kelondroException e) {
                /* if we have an error, we start with a fresh database */
                plasmaCrawlStacker.this.log.logSevere("Unable to initialize crawl stacker queue, kelondroException:" + e.getMessage() + ". Reseting DB.\n", e);

                // deleting old db and creating a new db
                try {this.urlEntryCache.close();} catch (Exception ex) {}
                deleteDB();
                openDB();
            } catch (IOException e) {
                /* if we have an error, we start with a fresh database */
                plasmaCrawlStacker.this.log.logSevere("Unable to initialize crawl stacker queue, IOException:" + e.getMessage() + ". Reseting DB.\n", e);

                // deleting old db and creating a new db
                try {this.urlEntryCache.close();} catch (Exception ex) {}
                deleteDB();
                openDB();
            }

        }
        
        private void deleteDB() {
            if (this.dbtype == QUEUE_DB_TYPE_RAM) {
                // do nothing..
            } 
            if (this.dbtype == QUEUE_DB_TYPE_FLEX) {
                kelondroFlexWidthArray.delete(cacheStacksPath, "urlPreNotice1.table");
            } 
            if (this.dbtype == QUEUE_DB_TYPE_TREE) {
                File cacheFile = new File(cacheStacksPath, "urlPreNotice.db");
                cacheFile.delete();
            }
        }
        
        private void openDB() {
            if (!(cacheStacksPath.exists())) cacheStacksPath.mkdir(); // make the path
            
            if (this.dbtype == QUEUE_DB_TYPE_RAM) {
                this.urlEntryCache = new kelondroRowSet(plasmaCrawlNURL.rowdef, kelondroBase64Order.enhancedCoder, 0, 0);
            } 
            if (this.dbtype == QUEUE_DB_TYPE_FLEX) {
                String newCacheName = "urlPreNotice1.table";
                cacheStacksPath.mkdirs();
                try {
                    this.urlEntryCache = new kelondroCache(new kelondroFlexTable(cacheStacksPath, newCacheName, bufferkb / 2 * 0x400, preloadTime, plasmaCrawlNURL.rowdef, kelondroBase64Order.enhancedCoder), bufferkb / 2 * 0x400, true, true);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            } 
            if (this.dbtype == QUEUE_DB_TYPE_TREE) {
                File cacheFile = new File(cacheStacksPath, "urlPreNotice.db");
                cacheFile.getParentFile().mkdirs();
                try {
                    this.urlEntryCache = new kelondroCache(kelondroTree.open(cacheFile, bufferkb / 2 * 0x400, preloadTime, plasmaCrawlNURL.rowdef), bufferkb / 2 * 0x400, true, true);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }

        public int cacheNodeChunkSize() {
            return urlEntryCache.cacheNodeChunkSize();
        }
        
        public int[] cacheNodeStatus() {
            return urlEntryCache.cacheNodeStatus();
        }
        
        public int cacheObjectChunkSize() {
            return urlEntryCache.cacheObjectChunkSize();
        }
        
        public long[] cacheObjectStatus() {
            return urlEntryCache.cacheObjectStatus();
        }
        
        public void close() throws IOException {
            // closing the db
            this.urlEntryCache.close();
            
            // clearing the hash list
            this.urlEntryHashCache.clear();            
        }

        public void addMessage(stackCrawlMessage newMessage) 
        throws InterruptedException, IOException {
            if (newMessage == null) throw new NullPointerException();
            
            this.writeSync.P();
            try {
                
                boolean insertionDoneSuccessfully = false;
                synchronized(this.urlEntryHashCache) {                    
                    kelondroRow.Entry oldValue = this.urlEntryCache.put(this.urlEntryCache.row().newEntry(newMessage.getBytes()));                        
                    if (oldValue == null) {
                        insertionDoneSuccessfully = this.urlEntryHashCache.add(newMessage.urlHash);
                    }
                }
                
                if (insertionDoneSuccessfully)  {
                    this.readSync.V();              
                }
            } finally {
                this.writeSync.V();
            }
        }
        
        public int size() {
            synchronized(this.urlEntryHashCache) {
                return this.urlEntryHashCache.size();
            }         
        }
        
        public int getDBType() {
            return this.dbtype;
        }
        
        public stackCrawlMessage waitForMessage() throws InterruptedException, IOException {
            this.readSync.P();         
            this.writeSync.P();
            
            String urlHash = null;
            kelondroRow.Entry entry = null;
            stackCrawlMessage newMessage = null;
            try {
                synchronized(this.urlEntryHashCache) {               
                    urlHash = (String) this.urlEntryHashCache.removeFirst();
                    if (urlHash == null) throw new IOException("urlHash is null");
                    entry = this.urlEntryCache.remove(urlHash.getBytes());                 
                }
            } finally {
                this.writeSync.V();
            }
            
            newMessage = new stackCrawlMessage(urlHash, entry);
            return newMessage;
        }
    }    
    
    public final class WorkterFactory implements org.apache.commons.pool.PoolableObjectFactory {

        final ThreadGroup workerThreadGroup;
        public WorkterFactory(ThreadGroup theWorkerThreadGroup) {
            super();  
            
            if (theWorkerThreadGroup == null)
                throw new IllegalArgumentException("The threadgroup object must not be null.");
            
            this.workerThreadGroup = theWorkerThreadGroup;
}
        
        public Object makeObject() {
            Worker newWorker = new Worker(this.workerThreadGroup);
            newWorker.setPriority(Thread.MAX_PRIORITY);
            return newWorker;
        }
        
         /**
         * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
         */
        public void destroyObject(Object obj) {
            if (obj instanceof Worker) {
                Worker theWorker = (Worker) obj;
                synchronized(theWorker) {
                    theWorker.setName("stackCrawlThread_destroyed");
                    theWorker.destroyed = true;
                    theWorker.setStopped(true);
                    theWorker.interrupt();
                }
            }
        }
        
        /**
         * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
         */
        public boolean validateObject(Object obj) {
            return true;
        }
        
        /**
         * @param obj 
         * 
         */
        public void activateObject(Object obj)  {
            //log.debug(" activateObject...");
        }

        /**
         * @param obj 
         * 
         */
        public void passivateObject(Object obj) { 
            //log.debug(" passivateObject..." + obj);
//            if (obj instanceof Session)  {
//                Session theSession = (Session) obj;              
//            }
        }        
    }
    
    public final class WorkerPool extends GenericObjectPool {
        public boolean isClosed = false;
        
        /**
         * First constructor.
         * @param objFactory
         */        
        public WorkerPool(WorkterFactory objFactory) {
            super(objFactory);
            this.setMaxIdle(10); // Maximum idle threads.
            this.setMaxActive(50); // Maximum active threads.
            this.setMinEvictableIdleTimeMillis(30000); //Evictor runs every 30 secs.
            //this.setMaxWait(1000); // Wait 1 second till a thread is available
        }
        
        public WorkerPool(plasmaCrawlStacker.WorkterFactory objFactory,
                           GenericObjectPool.Config config) {
            super(objFactory, config);
        }
        
        public Object borrowObject() throws Exception  {
           return super.borrowObject();
        }
        
        public void returnObject(Object obj) {
            if (obj == null) return;
            if (obj instanceof  Worker) {
                try {
                    ((Worker)obj).setName("stackCrawlThread_inPool");
                    super.returnObject(obj);
                } catch (Exception e) {
                    ((Worker)obj).setStopped(true);
                    serverLog.logSevere("STACKCRAWL-POOL","Unable to return stackcrawl thread to pool.",e);
                }
            } else {
                serverLog.logSevere("STACKCRAWL-POOL","Object of wront type '" + obj.getClass().getName() +
                                    "' returned to pool.");                
            }
        }        
        
        public void invalidateObject(Object obj) {
            if (obj == null) return;
            if (this.isClosed) return;
            if (obj instanceof Worker) {
                try {
                    ((Worker)obj).setName("stackCrawlThread_invalidated");
                    ((Worker)obj).setStopped(true);
                    super.invalidateObject(obj);
                } catch (Exception e) {
                    serverLog.logSevere("STACKCRAWL-POOL","Unable to invalidate stackcrawl thread.",e);
                }
            }
        }
        
        public synchronized void close() throws Exception {

            /*
             * shutdown all still running session threads ...
             */
            this.isClosed = true;
            
            /* waiting for all threads to finish */
            int threadCount  = theWorkerThreadGroup.activeCount();    
            Thread[] threadList = new Thread[threadCount];     
            threadCount = theWorkerThreadGroup.enumerate(threadList);
            
            try {
                // trying to gracefull stop all still running sessions ...
                log.logInfo("Signaling shutdown to " + threadCount + " remaining stackCrawl threads ...");
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    Thread currentThread = threadList[currentThreadIdx];
                    if (currentThread.isAlive()) {
                        ((Worker)currentThread).setStopped(true);
                    }
                }          

                // waiting a frew ms for the session objects to continue processing
                try { Thread.sleep(500); } catch (InterruptedException ex) {}                
                
                // interrupting all still running or pooled threads ...
                log.logInfo("Sending interruption signal to " + theWorkerThreadGroup.activeCount() + " remaining stackCrawl threads ...");
                theWorkerThreadGroup.interrupt();                
                
                // if there are some sessions that are blocking in IO, we simply close the socket
                log.logFine("Trying to abort " + theWorkerThreadGroup.activeCount() + " remaining stackCrawl threads ...");
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    Thread currentThread = threadList[currentThreadIdx];
                    if (currentThread.isAlive()) {
                        log.logInfo("Trying to shutdown stackCrawl thread '" + currentThread.getName() + "' [" + currentThreadIdx + "].");
                        ((Worker)currentThread).close();
                    }
                }                
                
                // we need to use a timeout here because of missing interruptable session threads ...
                log.logFine("Waiting for " + theWorkerThreadGroup.activeCount() + " remaining stackCrawl threads to finish shutdown ...");
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    Thread currentThread = threadList[currentThreadIdx];
                    if (currentThread.isAlive()) {
                        log.logFine("Waiting for stackCrawl thread '" + currentThread.getName() + "' [" + currentThreadIdx + "] to finish shutdown.");
                        try { currentThread.join(500); } catch (InterruptedException ex) {}
                    }
                }
                
                log.logInfo("Shutdown of remaining stackCrawl threads finished.");
            } catch (Exception e) {
                log.logSevere("Unexpected error while trying to shutdown all remaining stackCrawl threads.",e);
            }
            
            super.close();  
        }
        
    }
    
    public final class Worker extends Thread {  
            boolean destroyed = false;
            private boolean running = false;
            private boolean stopped = false;
            private boolean done = false;
            private stackCrawlMessage theMsg;        
            
            public Worker(ThreadGroup theThreadGroup) {
                super(theThreadGroup,"stackCrawlThread_created");
            }
            
            public void setStopped(boolean stopped) {
                this.stopped = stopped;            
            }
            
            public void close() {
                if (this.isAlive()) {
                    try {
                        // trying to close all still open httpc-Sockets first                    
                        int closedSockets = httpc.closeOpenSockets(this);
                        if (closedSockets > 0) {
                            log.logInfo(closedSockets + " HTTP-client sockets of thread '" + this.getName() + "' closed.");
                        }                    
                    } catch (Exception e) {}
                }            
            }
            
            public synchronized void execute(stackCrawlMessage newMsg) {
                this.theMsg = newMsg;
                this.done = false;
                
                if (!this.running)  {
                   // this.setDaemon(true);
                   this.start();
                }  else {                     
                   this.notifyAll();
                }          
            }
            
            public void reset()  {
                this.done = true;
                this.theMsg = null;
            }   
            
            public boolean isRunning() {
                return this.running;
            }
            
            public void run()  {
                this.running = true;
                
                try {
                    // The thread keeps running.
                    while (!this.stopped && !this.isInterrupted() && !plasmaCrawlStacker.this.theWorkerPool.isClosed) {
                        if (this.done)  {
                            synchronized (this) { 
                                // return thread back into pool
                                plasmaCrawlStacker.this.theWorkerPool.returnObject(this);
                                
                                // We are waiting for a new task now.                            
                                if (!this.stopped && !this.destroyed && !this.isInterrupted()) { 
                                    this.wait(); 
                                }
                            }
                        } else {
                            try  {
                                // executing the new task
                                execute();
                            } finally  {
                                // reset thread
                                reset();
                            }
                        }
                    }
                } catch (InterruptedException ex) {
                    serverLog.logFiner("STACKCRAWL-POOL","Interruption of thread '" + this.getName() + "' detected.");
                } finally {
                    if (plasmaCrawlStacker.this.theWorkerPool != null && !this.destroyed) 
                        plasmaCrawlStacker.this.theWorkerPool.invalidateObject(this);
                }
            }
                
            private void execute() throws InterruptedException {
                try {
                    this.setName("stackCrawlThread_" + this.theMsg.url);
                    String rejectReason = dequeue(this.theMsg);

                    // check for interruption
                    checkInterruption();
                    
                    // if the url was rejected we store it into the error URL db
                    if (rejectReason != null) {
                        plasmaCrawlEURL.Entry ee = sb.urlPool.errorURL.newEntry(
                                new URL(this.theMsg.url()),
                                this.theMsg.referrerHash(),
                                this.theMsg.initiatorHash(),
                                yacyCore.seedDB.mySeed.hash,
                                this.theMsg.name,
                                rejectReason,
                                new bitfield(indexURL.urlFlagLength)
                        );
                        ee.store();
                        sb.urlPool.errorURL.stackPushEntry(ee);
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) throw (InterruptedException) e;
                    plasmaCrawlStacker.this.log.logWarning("Error while processing stackCrawl entry.\n" + 
                                   "Entry: " + this.theMsg.toString() + 
                                   "Error: " + e.toString(),e);
                } finally {
                    this.done = true;
                }

            }
    }

}
