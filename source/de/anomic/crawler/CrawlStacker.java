// plasmaCrawlStacker.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.crawler;

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
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDomains;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public final class CrawlStacker extends Thread {
    
    private static final int EcoFSBufferSize = 20;
    private static String stackfile = "urlNoticeStacker9.db";
    
    // keys for different database types
    public static final int QUEUE_DB_TYPE_RAM  = 0;
    public static final int QUEUE_DB_TYPE_TREE = 1;
    public static final int QUEUE_DB_TYPE_ECO = 2;
    
    final serverLog log = new serverLog("STACKCRAWL");
    
    private final plasmaSwitchboard sb;
    private final LinkedList<String> urlEntryHashCache;
    private kelondroIndex urlEntryCache;
    private final File cacheStacksPath;
    private final int dbtype;
    private final boolean prequeue;
    private long dnsHit, dnsMiss;
    private int alternateCount;
    
    
    // objects for the prefetch task
    private final ArrayList<String> dnsfetchHosts = new ArrayList<String>();    
    
    public CrawlStacker(final plasmaSwitchboard sb, final File dbPath, final int dbtype, final boolean prequeue) {
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
            final Iterator<kelondroRow.Entry> rows = this.urlEntryCache.rows(true, null);
            kelondroRow.Entry entry;
            while (rows.hasNext()) {
                entry = rows.next();
                if (entry == null) {
                    System.out.println("ERROR! null element found");
                    continue;
                }
                this.urlEntryHashCache.add(entry.getColString(0, null));
            }
        } catch (final kelondroException e) {
            /* if we have an error, we start with a fresh database */
            CrawlStacker.this.log.logSevere("Unable to initialize crawl stacker queue, kelondroException:" + e.getMessage() + ". Reseting DB.\n", e);

            // deleting old db and creating a new db
            try {this.urlEntryCache.close();} catch (final Exception ex) {}
            deleteDB();
            openDB();
        } catch (final IOException e) {
            /* if we have an error, we start with a fresh database */
            CrawlStacker.this.log.logSevere("Unable to initialize crawl stacker queue, IOException:" + e.getMessage() + ". Reseting DB.\n", e);

            // deleting old db and creating a new db
            try {this.urlEntryCache.close();} catch (final Exception ex) {}
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
                    nextHost = dnsfetchHosts.remove(dnsfetchHosts.size() - 1);
                }
                try {
                    serverDomains.dnsResolve(nextHost);
                } catch (final Exception e) {}
            }
        } catch (final InterruptedException e) {}
    }       

    public boolean prefetchHost(final String host) {
        // returns true when the host was known in the dns cache.
        // If not, the host is stacked on the fetch stack and false is returned
        try {
            serverDomains.dnsResolveFromCache(host);
            return true;
        } catch (final UnknownHostException e) {
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
    
    public void clear() throws IOException {
        this.urlEntryHashCache.clear();
        this.urlEntryCache.clear();
    }
    
    public void close() {
        if (this.dbtype == QUEUE_DB_TYPE_RAM) {
            this.log.logInfo("Shutdown. Flushing remaining " + size() + " crawl stacker job entries. please wait.");
            while (size() > 0) {
                if (!job()) break;
            }
        }
        terminateDNSPrefetcher();
        
        this.log.logInfo("Shutdown. Closing stackCrawl queue.");

        // closing the db
        this.urlEntryCache.close();
            
        // clearing the hash list
        this.urlEntryHashCache.clear();
    }
    
    public boolean job() {
        CrawlEntry entry;
        try {
            entry = dequeueEntry();
        } catch (final IOException e) {
            e.printStackTrace();
            return false;
        }
        if (entry == null) return false;

        try {

            final String rejectReason = sb.crawlStacker.stackCrawl(entry);

            // if the url was rejected we store it into the error URL db
            if (rejectReason != null) {
                final ZURL.Entry ee = sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, rejectReason);
                ee.store();
                sb.crawlQueues.errorURL.push(ee);
            }
        } catch (final Exception e) {
            CrawlStacker.this.log.logWarning("Error while processing stackCrawl entry.\n" + "Entry: " + entry.toString() + "Error: " + e.toString(), e);
            return false;
        }
        return true;
    }
    
    public void enqueueEntry(
            final yacyURL nexturl, 
            final String referrerhash, 
            final String initiatorHash, 
            final String name, 
            final Date loadDate, 
            final int currentdepth, 
            final CrawlProfile.entry profile) {
        if (profile == null) return;
        
        // check first before we create a big object
        if (this.urlEntryCache.has(nexturl.hash().getBytes())) return;

        // now create the big object before we enter the synchronized block
        final CrawlEntry newEntry = new CrawlEntry(
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
        final kelondroRow.Entry newEntryRow = newEntry.toRow();
                
        synchronized(this.urlEntryHashCache) {
            kelondroRow.Entry oldValue;
            boolean hostknown = true;
            if (prequeue) hostknown = prefetchHost(nexturl.getHost());
            try {
                oldValue = this.urlEntryCache.put(newEntryRow);
            } catch (final IOException e) {
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
            return;
        }
        if (this.dbtype == QUEUE_DB_TYPE_ECO) {
            new File(cacheStacksPath, stackfile).delete();
            //kelondroFlexWidthArray.delete(cacheStacksPath, stackfile);
        }
        if (this.dbtype == QUEUE_DB_TYPE_TREE) {
            final File cacheFile = new File(cacheStacksPath, stackfile);
            cacheFile.delete();
        }
    }

    private void openDB() {
        if (!(cacheStacksPath.exists())) cacheStacksPath.mkdir(); // make the path

        if (this.dbtype == QUEUE_DB_TYPE_RAM) {
            this.urlEntryCache = new kelondroRowSet(CrawlEntry.rowdef, 0);
        }
        if (this.dbtype == QUEUE_DB_TYPE_ECO) {
            cacheStacksPath.mkdirs();
            final File f = new File(cacheStacksPath, stackfile);
            try {
                this.urlEntryCache = new kelondroEcoTable(f, CrawlEntry.rowdef, kelondroEcoTable.tailCacheUsageAuto, EcoFSBufferSize, 0);
                //this.urlEntryCache = new kelondroCache(new kelondroFlexTable(cacheStacksPath, newCacheName, preloadTime, CrawlEntry.rowdef, 0, true));
            } catch (final Exception e) {
                e.printStackTrace();
                // kill DB and try again
                f.delete();
                //kelondroFlexTable.delete(cacheStacksPath, newCacheName);
                try {
                    this.urlEntryCache = new kelondroEcoTable(f, CrawlEntry.rowdef, kelondroEcoTable.tailCacheUsageAuto, EcoFSBufferSize, 0);
                    //this.urlEntryCache = new kelondroCache(new kelondroFlexTable(cacheStacksPath, newCacheName, preloadTime, CrawlEntry.rowdef, 0, true));
                } catch (final Exception ee) {
                    ee.printStackTrace();
                    System.exit(-1);
                }
            }
        }
        if (this.dbtype == QUEUE_DB_TYPE_TREE) {
            final File cacheFile = new File(cacheStacksPath, stackfile);
            cacheFile.getParentFile().mkdirs();
            this.urlEntryCache = new kelondroCache(kelondroTree.open(cacheFile, true, 0, CrawlEntry.rowdef));
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

    public CrawlEntry dequeueEntry() throws IOException {
        if (this.urlEntryHashCache.size() == 0) return null;
        String urlHash = null;
        kelondroRow.Entry entry = null;
        synchronized (this.urlEntryHashCache) {
            urlHash = this.urlEntryHashCache.removeFirst();
            if (urlHash == null) throw new IOException("urlHash is null");
            entry = this.urlEntryCache.remove(urlHash.getBytes());
        }

        if ((urlHash == null) || (entry == null)) return null;
        return new CrawlEntry(entry);
    }
    
    public String stackCrawl(final yacyURL url, final yacyURL referrer, final String initiatorHash, final String name, final Date loadDate, final int currentdepth, final CrawlProfile.entry profile) {
        // stacks a crawl item. The position can also be remote
        // returns null if successful, a reason string if not successful
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");
        
        // add the url into the crawling queue
        final CrawlEntry entry = new CrawlEntry(
                initiatorHash,                               // initiator, needed for p2p-feedback
                url,                                         // url clear text string
                (referrer == null) ? "" : referrer.hash(),   // last url in crawling queue
                name,                                        // load date
                loadDate,                                    // the anchor name
                (profile == null) ? null : profile.handle(), // profile must not be null!
                currentdepth,                                // depth so far
                0,                                           // anchors, default value
                0                                            // forkfactor, default value
        );
        return stackCrawl(entry);
    }
    
    public String stackCrawl(final CrawlEntry entry) {
        // stacks a crawl item. The position can also be remote
        // returns null if successful, a reason string if not successful
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");
        
        final long startTime = System.currentTimeMillis();
        String reason = null; // failure reason

        // check if the protocol is supported
        final String urlProtocol = entry.url().getProtocol();
        if (!sb.crawlQueues.isSupportedProtocol(urlProtocol)) {
            reason = ErrorURL.DENIED_UNSUPPORTED_PROTOCOL;
            this.log.logSevere("Unsupported protocol in URL '" + entry.url().toString() + "'. " + 
                               "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;            
        }

        // check if ip is local ip address
        final String urlRejectReason = sb.acceptURL(entry.url());
        if (urlRejectReason != null) {
            reason = "denied_(" + urlRejectReason + ")_domain=" + sb.getConfig("network.unit.domain", "unknown");
            if (this.log.isFine()) this.log.logFine(reason + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;                
        }
        
        // check blacklist
        if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_CRAWLER, entry.url())) {
            reason = ErrorURL.DENIED_URL_IN_BLACKLIST;
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is in blacklist. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        final CrawlProfile.entry profile = sb.webIndex.profilesActiveCrawls.getEntry(entry.profileHandle());
        if (profile == null) {
            final String errorMsg = "LOST PROFILE HANDLE '" + entry.profileHandle() + "' for URL " + entry.url();
            log.logWarning(errorMsg);
            return errorMsg;
        } else {
        
        // filter deny
        if ((entry.depth() > 0) && (!(entry.url().toString().matches(profile.generalFilter())))) {
            reason = ErrorURL.DENIED_URL_DOES_NOT_MATCH_FILTER;

            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' does not match crawling filter '" + profile.generalFilter() + "'. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        // deny cgi
        if (entry.url().isCGI())  {
            reason = ErrorURL.DENIED_CGI_URL;

            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is CGI URL. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        // deny post properties
        if (entry.url().isPOST() && !(profile.crawlingQ()))  {
            reason = ErrorURL.DENIED_POST_URL;

            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is post URL. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        final yacyURL referrerURL = (entry.referrerhash() == null) ? null : sb.crawlQueues.getURL(entry.referrerhash());
        
        // add domain to profile domain list
        if ((profile.domFilterDepth() != Integer.MAX_VALUE) || (profile.domMaxPages() != Integer.MAX_VALUE)) {
            profile.domInc(entry.url().getHost(), (referrerURL == null) ? null : referrerURL.getHost().toLowerCase(), entry.depth());
        }

        // deny urls that do not match with the profile domain list
        if (!(profile.grantedDomAppearance(entry.url().getHost()))) {
            reason = ErrorURL.DENIED_NO_MATCH_WITH_DOMAIN_FILTER;
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is not listed in granted domains. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // deny urls that exceed allowed number of occurrences
        if (!(profile.grantedDomCount(entry.url().getHost()))) {
            reason = ErrorURL.DENIED_DOMAIN_COUNT_EXCEEDED;
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' appeared too often, a maximum of " + profile.domMaxPages() + " is allowed. "+ 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // check if the url is double registered
        final String dbocc = sb.crawlQueues.urlExists(entry.url().hash());
        final indexURLReference oldEntry = this.sb.webIndex.getURL(entry.url().hash(), null, 0);
        final boolean recrawl = (oldEntry != null) && ((System.currentTimeMillis() - oldEntry.loaddate().getTime()) > profile.recrawlIfOlder());
        // do double-check
        if ((dbocc != null) && (!recrawl)) {
            reason = ErrorURL.DOUBLE_REGISTERED + dbocc + ")";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is double registered in '" + dbocc + "'. " + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        if ((oldEntry != null) && (!recrawl)) {
            reason = ErrorURL.DOUBLE_REGISTERED + "LURL)";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is double registered in 'LURL'. " + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // show potential re-crawl
        if (recrawl && oldEntry != null) {
            if (this.log.isFine()) this.log.logFine("RE-CRAWL of URL '" + entry.url().toString() + "': this url was crawled " +
                    ((System.currentTimeMillis() - oldEntry.loaddate().getTime()) / 60000 / 60 / 24) + " days ago.");
        }
        
        // store information
        final boolean local = entry.initiator().equals(sb.webIndex.seedDB.mySeed().hash);
        final boolean global = 
            (profile.remoteIndexing()) /* granted */ &&
            (entry.depth() == profile.generalDepth()) /* leaf node */ && 
            //(initiatorHash.equals(yacyCore.seedDB.mySeed.hash)) /* not proxy */ &&
            (
                    (sb.webIndex.seedDB.mySeed().isSenior()) ||
                    (sb.webIndex.seedDB.mySeed().isPrincipal())
            ) /* qualified */;
        
        if (!local && !global && !profile.handle().equals(this.sb.webIndex.defaultRemoteProfile.handle())) {
            this.log.logSevere("URL '" + entry.url().toString() + "' can neither be crawled local nor global.");
        }
        
        // add the url into the crawling queue
        sb.crawlQueues.noticeURL.push(
                ((global) ? NoticedURL.STACK_TYPE_LIMIT :
                ((local) ? NoticedURL.STACK_TYPE_CORE : NoticedURL.STACK_TYPE_REMOTE)) /*local/remote stack*/,
                entry);
        return null;
        }
    }
    
}
