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

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import de.anomic.index.indexReferenceBlacklist;
import de.anomic.index.indexURLReference;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.serverDomains;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public final class CrawlStacker {
    
    final serverLog log = new serverLog("STACKCRAWL");
    
    private BlockingQueue<CrawlEntry> fastQueue, slowQueue;
    private long                      dnsHit, dnsMiss;
    private CrawlQueues               nextQueue;
    private plasmaWordIndex           wordIndex;
    private boolean                   acceptLocalURLs, acceptGlobalURLs;
    
    // objects for the prefetch task
    private final ArrayList<String> dnsfetchHosts = new ArrayList<String>();    
    
    
    // this is the process that checks url for double-occurrences and for allowance/disallowance by robots.txt
    
    public CrawlStacker(CrawlQueues cq, plasmaWordIndex wordIndex, boolean acceptLocalURLs, boolean acceptGlobalURLs) {
        this.nextQueue = cq;
        this.wordIndex = wordIndex;
        this.dnsHit = 0;
        this.dnsMiss = 0;
        this.acceptLocalURLs = acceptLocalURLs;
        this.acceptGlobalURLs = acceptGlobalURLs;
        
        this.fastQueue = new LinkedBlockingQueue<CrawlEntry>();
        this.slowQueue = new ArrayBlockingQueue<CrawlEntry>(1000);
        this.log.logInfo("STACKCRAWL thread initialized.");
    }

    public int size() {
        return this.fastQueue.size() + this.slowQueue.size();
    }

    public void clear() {
        this.fastQueue.clear();
        this.slowQueue.clear();
    }
    
    public void close() {
        this.log.logInfo("Shutdown. Flushing remaining " + size() + " crawl stacker job entries. please wait.");
        while (size() > 0) {
            if (!job()) break;
        }
        
        this.log.logInfo("Shutdown. Closing stackCrawl queue.");

        clear();
    }

    private boolean prefetchHost(final String host) {
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
    
    public boolean job() {
        if (this.fastQueue.size() > 0 && job(this.fastQueue)) return true;
        if (this.slowQueue.size() == 0) return false;
        return job(this.slowQueue);
    }
    
    private boolean job(BlockingQueue<CrawlEntry> queue) {
        // this is the method that is called by the busy thread from outside
        if (queue.size() == 0) return false;
        
        // get the next entry from the queue
        CrawlEntry entry = queue.poll();
        if (entry == null) return false;

        try {
            final String rejectReason = stackCrawl(entry);

            // if the url was rejected we store it into the error URL db
            if (rejectReason != null) {
                final ZURL.Entry ee = nextQueue.errorURL.newEntry(entry, wordIndex.seedDB.mySeed().hash, new Date(), 1, rejectReason);
                ee.store();
                nextQueue.errorURL.push(ee);
            }
        } catch (final Exception e) {
            CrawlStacker.this.log.logWarning("Error while processing stackCrawl entry.\n" + "Entry: " + entry.toString() + "Error: " + e.toString(), e);
            return false;
        }
        return true;
    }
 
    public void enqueueEntry(final CrawlEntry entry) {
     
        // DEBUG
        if (log.isFinest()) log.logFinest("ENQUEUE "+ entry.url() +", referer="+entry.referrerhash() +", initiator="+entry.initiator() +", name="+entry.name() +", load="+entry.loaddate() +", depth="+entry.depth());

        if (prefetchHost(entry.url().getHost())) {
            try {
                this.fastQueue.put(entry);
                this.dnsHit++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            try {
                this.slowQueue.put(entry);
                this.dnsMiss++; 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public String stackCrawl(final CrawlEntry entry) {
        // stacks a crawl item. The position can also be remote
        // returns null if successful, a reason string if not successful
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");
        
        final long startTime = System.currentTimeMillis();
        String reason = null; // failure reason

        // check if the protocol is supported
        final String urlProtocol = entry.url().getProtocol();
        if (!nextQueue.isSupportedProtocol(urlProtocol)) {
            reason = "unsupported protocol";
            this.log.logSevere("Unsupported protocol in URL '" + entry.url().toString() + "'. " + 
                               "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;            
        }

        // check if ip is local ip address
        final String urlRejectReason = urlInAcceptedDomain(entry.url());
        if (urlRejectReason != null) {
            reason = "denied_(" + urlRejectReason + ")";
            if (this.log.isFine()) this.log.logFine(reason + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;                
        }
        
        // check blacklist
        if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_CRAWLER, entry.url())) {
            reason = "url in blacklist";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is in blacklist. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        final CrawlProfile.entry profile = wordIndex.profilesActiveCrawls.getEntry(entry.profileHandle());
        if (profile == null) {
            final String errorMsg = "LOST STACKER PROFILE HANDLE '" + entry.profileHandle() + "' for URL " + entry.url();
            log.logWarning(errorMsg);
            return errorMsg;
        }
        
        // filter with must-match
        if ((entry.depth() > 0) && !profile.mustMatchPattern().matcher(entry.url().toString()).matches()) {
            reason = "url does not match must-match filter";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' does not match must-match crawling filter '" + profile.mustMatchPattern().toString() + "'. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // filter with must-not-match
        if ((entry.depth() > 0) && profile.mustNotMatchPattern().matcher(entry.url().toString()).matches()) {
            reason = "url matches must-not-match filter";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' does matches do-not-match crawling filter '" + profile.mustNotMatchPattern().toString() + "'. " +
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // deny cgi
        if (entry.url().isCGI())  {
            reason = "cgi url not allowed";

            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is CGI URL. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        // deny post properties
        if (entry.url().isPOST() && !(profile.crawlingQ()))  {
            reason = "post url not allowed";

            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is post URL. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        
        final yacyURL referrerURL = (entry.referrerhash() == null) ? null : nextQueue.getURL(entry.referrerhash());
        
        // add domain to profile domain list
        if ((profile.domFilterDepth() != Integer.MAX_VALUE) || (profile.domMaxPages() != Integer.MAX_VALUE)) {
            profile.domInc(entry.url().getHost(), (referrerURL == null) ? null : referrerURL.getHost().toLowerCase(), entry.depth());
        }

        // deny urls that do not match with the profile domain list
        if (!(profile.grantedDomAppearance(entry.url().getHost()))) {
            reason = "url does not match domain filter";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is not listed in granted domains. " + 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // deny urls that exceed allowed number of occurrences
        if (!(profile.grantedDomCount(entry.url().getHost()))) {
            reason = "domain counter exceeded";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' appeared too often, a maximum of " + profile.domMaxPages() + " is allowed. "+ 
                             "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // check if the url is double registered
        final String dbocc = nextQueue.urlExists(entry.url().hash());
        final indexURLReference oldEntry = wordIndex.getURL(entry.url().hash(), null, 0);
        final boolean recrawl = (oldEntry != null) && (profile.recrawlIfOlder() > oldEntry.loaddate().getTime());
        // do double-check
        if ((dbocc != null) && (!recrawl)) {
            reason = "double " + dbocc + ")";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is double registered in '" + dbocc + "'. " + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }
        if ((oldEntry != null) && (!recrawl)) {
            reason = "double " + "LURL)";
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is double registered in 'LURL'. " + "Stack processing time: " + (System.currentTimeMillis()-startTime) + "ms");
            return reason;
        }

        // show potential re-crawl
        if (recrawl && oldEntry != null) {
            if (this.log.isFine()) this.log.logFine("RE-CRAWL of URL '" + entry.url().toString() + "': this url was crawled " +
                    ((System.currentTimeMillis() - oldEntry.loaddate().getTime()) / 60000 / 60 / 24) + " days ago.");
        }
        
        // store information
        final boolean local = entry.initiator().equals(wordIndex.seedDB.mySeed().hash);
        final boolean proxy = (entry.initiator() == null || entry.initiator().equals("------------")) && profile.handle().equals(wordIndex.defaultProxyProfile.handle());
        final boolean remote = profile.handle().equals(wordIndex.defaultRemoteProfile.handle());
        final boolean global = 
            (profile.remoteIndexing()) /* granted */ &&
            (entry.depth() == profile.depth()) /* leaf node */ && 
            //(initiatorHash.equals(yacyCore.seedDB.mySeed.hash)) /* not proxy */ &&
            (
                    (wordIndex.seedDB.mySeed().isSenior()) ||
                    (wordIndex.seedDB.mySeed().isPrincipal())
            ) /* qualified */;
        
        if (!local && !global && !remote && !proxy) {
            this.log.logSevere("URL '" + entry.url().toString() + "' cannot be crawled. initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
        } else {
            if (global) {
                // it may be possible that global == true and local == true, so do not check an error case against it
                if (proxy) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: global = true, proxy = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
                if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: global = true, remote = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
                nextQueue.noticeURL.push(NoticedURL.STACK_TYPE_LIMIT, entry);
            }
            if (local) {
                if (proxy) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: local = true, proxy = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
                if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: local = true, remote = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
                nextQueue.noticeURL.push(NoticedURL.STACK_TYPE_CORE, entry);
            }
            if (proxy) {
                if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: proxy = true, remote = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
                nextQueue.noticeURL.push(NoticedURL.STACK_TYPE_CORE, entry);
            }
            if (remote) {
                nextQueue.noticeURL.push(NoticedURL.STACK_TYPE_REMOTE, entry);
            }
        }
        
        return null;
    }
    

    /**
     * Test a url if it can be used for crawling/indexing
     * This mainly checks if the url is in the declared domain (local/global)
     * @param url
     * @return null if the url can be accepted, a string containing a rejection reason if the url cannot be accepted
     */
    public String urlInAcceptedDomain(final yacyURL url) {
        // returns true if the url can be accepted accoring to network.unit.domain
        if (url == null) return "url is null";
        final String host = url.getHost();
        if (host == null) return "url.host is null";
        if (this.acceptGlobalURLs && this.acceptLocalURLs) return null; // fast shortcut to avoid dnsResolve
        /*
        InetAddress hostAddress = serverDomains.dnsResolve(host);
        // if we don't know the host, we cannot load that resource anyway.
        // But in case we use a proxy, it is possible that we dont have a DNS service.
        final httpRemoteProxyConfig remoteProxyConfig = httpdProxyHandler.getRemoteProxyConfig();
        if (hostAddress == null) {
            if ((remoteProxyConfig != null) && (remoteProxyConfig.useProxy())) return null; else return "the dns of the host '" + host + "' cannot be resolved";
        }
        */
        // check if this is a local address and we are allowed to index local pages:
        //boolean local = hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress();
        final boolean local = url.isLocal();
        //assert local == yacyURL.isLocalDomain(url.hash()); // TODO: remove the dnsResolve above!
        if ((this.acceptGlobalURLs && !local) || (this.acceptLocalURLs && local)) return null;
        return (local) ?
            ("the host '" + host + "' is local, but local addresses are not accepted") :
            ("the host '" + host + "' is global, but global addresses are not accepted");
    }
    
    public boolean acceptLocalURLs() {
        return this.acceptLocalURLs;
    }
    
    public boolean acceptGlobalURLs() {
        return this.acceptGlobalURLs;
    }
}
