// plasmaCrawlStacker.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// This file was contributed by Martin Thelian
// ([MC] removed all multithreading and thread pools, this is not necessary here; complete renovation 2007)
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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import de.anomic.index.indexReferenceBlacklist;
import de.anomic.index.indexURLReference;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroEcoTable;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.serverDomains;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyURL;

public final class plasmaCrawlStacker extends Thread {
    
    private static final int EcoFSBufferSize = 20;
    private static String stackfile = "urlNoticeStacker9.db";
    
    // keys for different database types
    public static final int QUEUE_DB_TYPE_RAM  = 0;
    public static final int QUEUE_DB_TYPE_TREE = 1;
    public static final int QUEUE_DB_TYPE_ECO = 2;
    
    final serverLog log = new serverLog("STACKCRAWL");
    
    private plasmaSwitchboard sb;
    private final LinkedList<String> urlEntryHashCache;
    private kelondroIndex urlEntryCache;
    private File cacheStacksPath;
    private int dbtype;
    private boolean prequeue;
    private long dnsHit, dnsMiss;
    private int alternateCount;
    
    
    // objects for the prefetch task
    private ArrayList<String> dnsfetchHosts = new ArrayList<String>();    
    
    public plasmaCrawlStacker(plasmaSwitchboard sb, File dbPath, int dbtype, boolean prequeue) {
        this.sb = sb;
        this.prequeue = prequeue;
        this.dnsHit = 0;
        this.dnsMiss = 0;
        this.alternateCount = 0;
        
        // init the message list
        this.urlEntryHashCache = new LinkedList<String>();
        
        // create a stack for newly entered entries
        this.cacheStacksPath = dbPath;
        this.dbtype = dbtype;

        openDB();
        try {
            // loop through the list and fill the messageList with url hashs
            Iterator<kelondroRow.Entry> rows = this.urlEntryCache.rows(true, null);
            kelondroRow.Entry entry;
            while (rows.hasNext()) {
                entry = (kelondroRow.Entry) rows.next();
                if (entry == null) {
                    System.out.println("ERROR! null element found");
                    continue;
                }
                this.urlEntryHashCache.add(entry.getColString(0, null));
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
        this.log.logInfo(size() + " entries in the stackCrawl queue.");
        this.start(); // start the prefetcher thread
        this.log.logInfo("STACKCRAWL thread initialized.");
    }

    public void run() {
        String nextHost;
        try {
            while (!Thread.currentThread().isInterrupted()) { // action loop
                if (dnsfetchHosts.size() == 0) synchronized (this) { wait(); }
                synchronized (dnsfetchHosts) {
                    nextHost = (String) dnsfetchHosts.remove(dnsfetchHosts.size() - 1);
                }
                try {
                    serverDomains.dnsResolve(nextHost);
                } catch (Exception e) {}
            }
        } catch (InterruptedException e) {}
    }       

    public boolean prefetchHost(String host) {
        // returns true when the host was known in the dns cache.
        // If not, the host is stacked on the fetch stack and false is returned
        try {
            serverDomains.dnsResolveFromCache(host);
            return true;
        } catch (UnknownHostException e) {
            synchronized (this) {
                dnsfetchHosts.add(host);
                notifyAll();
            }
            return false;
        }
    }
    
    public void terminateDNSPrefetcher() {
        synchronized (this) {
            interrupt();
        }
    }
    
    public void close() {
        if (this.dbtype == QUEUE_DB_TYPE_RAM) {
            this.log.logFine("Shutdown. Flushing remaining " + size() + " crawl stacker job entries. please wait.");
            while (size() > 0) {
                if (!job()) break;
            }
        }
        terminateDNSPrefetcher();
        
        this.log.logFine("Shutdown. Closing stackCrawl queue.");

        // closing the db
        this.urlEntryCache.close();
            
        // clearing the hash list
        this.urlEntryHashCache.clear();
    }
    
    public boolean job() {
        plasmaCrawlEntry entry;
        try {
            entry = dequeueEntry();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        if (entry == null) return false;

        try {

            String rejectReason = sb.crawlStacker.stackCrawl(entry);

            // if the url was rejected we store it into the error URL db
            if (rejectReason != null) {
                plasmaCrawlZURL.Entry ee = sb.crawlQueues.errorURL.newEntry(entry, yacyCore.seedDB.mySeed().hash, null, 0, rejectReason);
                ee.store();
                sb.crawlQueues.errorURL.push(ee);
            }
        } catch (Exception e) {
            plasmaCrawlStacker.this.log.logWarning("Error while processing stackCrawl entry.\n" + "Entry: " + entry.toString() + "Error: " + e.toString(), e);
            return false;
        }
        return true;
    }
    
    public void enqueueEntry(
            yacyURL nexturl, 
            String referrerhash, 
            String initiatorHash, 
            String name, 
            Date loadDate, 
            int currentdepth, 
            plasmaCrawlProfile.entry profile) {
        if (profile == null) return;
        plasmaCrawlEntry newEntry = new plasmaCrawlEntry(
                    initiatorHash,
                    nexturl,
                    referrerhash,
                    name,
                    loadDate,
                    profile.handle(),
                    currentdepth,
                    0,
                    0
                    );

        if (newEntry == null) return;
                
        synchronized(this.urlEntryHashCache) {                    
            kelondroRow.Entry oldValue;
            boolean hostknown = true;
            if (prequeue) hostknown = prefetchHost(nexturl.getHost());
            try {
                oldValue = this.urlEntryCache.put(newEntry.toRow());
            } catch (IOException e) {
                oldValue = null;
            }                        
            if (oldValue == null) {
                //System.out.println("*** debug crawlStacker dnsHit=" + this.dnsHit + ", dnsMiss=" + this.dnsMiss + ", alternateCount=" + this.alternateCount + ((this.dnsMiss > 0) ? (", Q=" + (this.dnsHit / this.dnsMiss)) : ""));
                if (hostknown) {
                    this.alternateCount++;
                    this.urlEntryHashCache.addFirst(newEntry.url().hash());
                    this.dnsHit++;
                } else {
                    if ((this.dnsMiss > 0) && (this.alternateCount > 2 * this.dnsHit / this.dnsMiss)) {
                        this.urlEntryHashCache.addFirst(newEntry.url().hash());
                        this.alternateCount = 0;
                        //System.out.println("*** debug crawlStacker alternate switch, dnsHit=" + this.dnsHit + ", dnsMiss=" + this.dnsMiss + ", alternateCount=" + this.alternateCount + ", Q=" + (this.dnsHit / this.dnsMiss));
                    } else {
                        this.urlEntryHashCache.addLast(newEntry.url().hash());
                    }
                    this.dnsMiss++; 
                }
            }
        }
    }
    
    private void deleteDB() {
        if (this.dbtype == QUEUE_DB_TYPE_RAM) {
            // do nothing..
        }
        if (this.dbtype == QUEUE_DB_TYPE_ECO) {
            new File(cacheStacksPath, stackfile).delete();
            //kelondroFlexWidthArray.delete(cacheStacksPath, stackfile);
        }
        if (this.dbtype == QUEUE_DB_TYPE_TREE) {
            File cacheFile = new File(cacheStacksPath, stackfile);
            cacheFile.delete();
        }
    }

    private void openDB() {
        if (!(cacheStacksPath.exists())) cacheStacksPath.mkdir(); // make the path

        if (this.dbtype == QUEUE_DB_TYPE_RAM) {
            this.urlEntryCache = new kelondroRowSet(plasmaCrawlEntry.rowdef, 0);
        }
        if (this.dbtype == QUEUE_DB_TYPE_ECO) {
            cacheStacksPath.mkdirs();
            File f = new File(cacheStacksPath, stackfile);
            try {
                this.urlEntryCache = new kelondroEcoTable(f, plasmaCrawlEntry.rowdef, kelondroEcoTable.tailCacheUsageAuto, EcoFSBufferSize, 0);
                //this.urlEntryCache = new kelondroCache(new kelondroFlexTable(cacheStacksPath, newCacheName, preloadTime, plasmaCrawlEntry.rowdef, 0, true));
            } catch (Exception e) {
                e.printStackTrace();
                // kill DB and try again
                f.delete();
                //kelondroFlexTable.delete(cacheStacksPath, newCacheName);
                try {
                    this.urlEntryCache = new kelondroEcoTable(f, plasmaCrawlEntry.rowdef, kelondroEcoTable.tailCacheUsageAuto, EcoFSBufferSize, 0);
                    //this.urlEntryCache = new kelondroCache(new kelondroFlexTable(cacheStacksPath, newCacheName, preloadTime, plasmaCrawlEntry.rowdef, 0, true));
                } catch (Exception ee) {
                    ee.printStackTrace();
                    System.exit(-1);
                }
            }
        }
        if (this.dbtype == QUEUE_DB_TYPE_TREE) {
            File cacheFile = new File(cacheStacksPath, stackfile);
            cacheFile.getParentFile().mkdirs();
            this.urlEntryCache = new kelondroCache(kelondroTree.open(cacheFile, true, 0, plasmaCrawlEntry.rowdef));
        }
    }

    public int size() {
        synchronized (this.urlEntryHashCache) {
            return this.urlEntryHashCache.size();
        }
    }

    public int getDBType() {
        return this.dbtype;
    }

    public plasmaCrawlEntry dequeueEntry() throws IOException {
        if (this.urlEntryHashCache.size() == 0) return null;
        String urlHash = null;
        kelondroRow.Entry entry = null;
        synchronized (this.urlEntryHashCache) {
            urlHash = (String) this.urlEntryHashCache.removeFirst();
            if (urlHash == null) throw new IOException("urlHash is null");
            entry = this.urlEntryCache.remove(urlHash.getBytes(), false);
        }

        if ((urlHash == null) || (entry == null)) return null;
        return new plasmaCrawlEntry(entry);
    }
    
    public String stackCrawl(yacyURL url, yacyURL referrer, String initiatorHash, String name, Date loadDate, int currentdepth, plasmaCrawlProfile.entry profile) {
        // stacks a crawl item. The position can also be remote
        // returns null if successful, a reason string if not successful
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");
        
        // add the url into the crawling queue
        plasmaCrawlEntry entry = new plasmaCrawlEntry(
                initiatorHash,                               // initiator, needed for p2p-feedback
                url,                                         // url clear text string
                (referrer == null) ? null : referrer.hash(), // last url in crawling queue
                name,                                        // load date
                loadDate,                                    // the anchor name
                (profile == null) ? null : profile.handle(), // profile must not be null!
                currentdepth,                                // depth so far
                0,                                           // anchors, default value
                0                                            // forkfactor, default value
        );
        return stackCrawl(entry);
    }
    
    public String stackCrawl(plasmaCrawlEntry entry) {
        // stacks a crawl item. The position can also be remote
        // returns null if successful, a reason string if not successful
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");
        
        long startTime = System.currentTimeMillis();
        String reason = null; // failure reason

        // check if the protocol is supported
        String urlProtocol = entry.url().getProtocol();
        if (!sb.crawlQueues.isSupportedProtocol(urlProtocol)) {
            reason = plasmaCrawlEURL.DENIED_UNSUPPORTED_PROTOCOL;
            this.log.logSevere("Unsupported protocol in URL '" + entry.url().toString() + "'. " + 
                               "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;            
        }

        // check if ip is local ip address
        if (!sb.acceptURL(entry.url())) {
            reason = plasmaCrawlEURL.DENIED_IP_ADDRESS_NOT_IN_DECLARED_DOMAIN + "[" + sb.getConfig("network.unit.domain", "unknown") + "]";
            this.log.logFine("Host in URL '" + entry.url().toString() + "' has IP address outside of declared range (" + sb.getConfig("network.unit.domain", "unknown") + "). " +
                    "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;                
        }
        
        // check blacklist
        if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_CRAWLER, entry.url())) {
            reason = plasmaCrawlEURL.DENIED_URL_IN_BLACKLIST;
            this.log.logFine("URL '" + entry.url().toString() + "' is in blacklist. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        plasmaCrawlProfile.entry profile = sb.profilesActiveCrawls.getEntry(entry.profileHandle());
        if (profile == null) {
            String errorMsg = "LOST PROFILE HANDLE '" + entry.profileHandle() + "' for URL " + entry.url();
            log.logWarning(errorMsg);
            return errorMsg;
        }
        
        // filter deny
        if ((entry.depth() > 0) && (profile != null) && (!(entry.url().toString().matches(profile.generalFilter())))) {
            reason = plasmaCrawlEURL.DENIED_URL_DOES_NOT_MATCH_FILTER;

            this.log.logFine("URL '" + entry.url().toString() + "' does not match crawling filter '" + profile.generalFilter() + "'. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        // deny cgi
        if (entry.url().isCGI())  {
            reason = plasmaCrawlEURL.DENIED_CGI_URL;

            this.log.logFine("URL '" + entry.url().toString() + "' is CGI URL. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        // deny post properties
        if ((entry.url().isPOST()) && (profile != null) && (!(profile.crawlingQ())))  {
            reason = plasmaCrawlEURL.DENIED_POST_URL;

            this.log.logFine("URL '" + entry.url().toString() + "' is post URL. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        yacyURL referrerURL = (entry.referrerhash() == null) ? null : sb.crawlQueues.getURL(entry.referrerhash());
        
        // add domain to profile domain list
        if ((profile.domFilterDepth() != Integer.MAX_VALUE) || (profile.domMaxPages() != Integer.MAX_VALUE)) {
            profile.domInc(entry.url().getHost(), (referrerURL == null) ? null : referrerURL.getHost().toLowerCase(), entry.depth());
        }

        // deny urls that do not match with the profile domain list
        if (!(profile.grantedDomAppearance(entry.url().getHost()))) {
            reason = plasmaCrawlEURL.DENIED_NO_MATCH_WITH_DOMAIN_FILTER;
            this.log.logFine("URL '" + entry.url().toString() + "' is not listed in granted domains. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // deny urls that exceed allowed number of occurrences
        if (!(profile.grantedDomCount(entry.url().getHost()))) {
            reason = plasmaCrawlEURL.DENIED_DOMAIN_COUNT_EXCEEDED;
            this.log.logFine("URL '" + entry.url().toString() + "' appeared too often, a maximum of " + profile.domMaxPages() + " is allowed. "+ 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // check if the url is double registered
        String dbocc = sb.crawlQueues.urlExists(entry.url().hash());
        indexURLReference oldEntry = this.sb.wordIndex.getURL(entry.url().hash(), null, 0);
        boolean recrawl = (oldEntry != null) && ((System.currentTimeMillis() - oldEntry.loaddate().getTime()) > profile.recrawlIfOlder());
        // do double-check
        if ((dbocc != null) && (!recrawl)) {
            reason = plasmaCrawlEURL.DOUBLE_REGISTERED + dbocc + ")";
            this.log.logFine("URL '" + entry.url().toString() + "' is double registered in '" + dbocc + "'. " + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        if ((oldEntry != null) && (!recrawl)) {
            reason = plasmaCrawlEURL.DOUBLE_REGISTERED + "LURL)";
            this.log.logFine("URL '" + entry.url().toString() + "' is double registered in 'LURL'. " + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // show potential re-crawl
        if (recrawl) {
            this.log.logFine("RE-CRAWL of URL '" + entry.url().toString() + "': this url was crawled " +
                    ((System.currentTimeMillis() - oldEntry.loaddate().getTime()) / 60000 / 60 / 24) + " days ago.");
        }
        
        // store information
        boolean local = ((entry.initiator().equals(yacyURL.dummyHash)) || (entry.initiator().equals(yacyCore.seedDB.mySeed().hash)));
        boolean global = 
            (profile != null) &&
            (profile.remoteIndexing()) /* granted */ &&
            (entry.depth() == profile.generalDepth()) /* leaf node */ && 
            //(initiatorHash.equals(yacyCore.seedDB.mySeed.hash)) /* not proxy */ &&
            (
                    (yacyCore.seedDB.mySeed().isSenior()) ||
                    (yacyCore.seedDB.mySeed().isPrincipal())
            ) /* qualified */;
        
        if ((!local)&&(!global)&&(!profile.handle().equals(this.sb.defaultRemoteProfile.handle()))) {
            this.log.logSevere("URL '" + entry.url().toString() + "' can neither be crawled local nor global.");
        }
        
        // add the url into the crawling queue
        sb.crawlQueues.noticeURL.push(
                ((global) ? plasmaCrawlNURL.STACK_TYPE_LIMIT :
                ((local) ? plasmaCrawlNURL.STACK_TYPE_CORE : plasmaCrawlNURL.STACK_TYPE_REMOTE)) /*local/remote stack*/,
                entry);
        return null;
    }
    
}
