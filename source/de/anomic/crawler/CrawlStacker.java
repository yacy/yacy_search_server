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

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.repository.Blacklist;
import net.yacy.repository.FilterEngine;

import de.anomic.crawler.ResultURLs.EventOrigin;
import de.anomic.crawler.retrieval.FTPLoader;
import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.crawler.retrieval.Request;
import de.anomic.crawler.retrieval.SMBLoader;
import de.anomic.search.Segment;
import de.anomic.search.Switchboard;
import de.anomic.yacy.yacySeedDB;

public final class CrawlStacker {

    private final Log log = new Log("STACKCRAWL");

    private final WorkflowProcessor<Request>  fastQueue, slowQueue;
    private long                    dnsMiss;
    private final CrawlQueues       nextQueue;
    private final CrawlSwitchboard  crawler;
    private final Segment           indexSegment;
    private final yacySeedDB        peers;
    private final boolean           acceptLocalURLs, acceptGlobalURLs;
    private final FilterEngine      domainList;

    public final static class DomProfile {
        
        public String referrer;
        public int depth, count;
        
        public DomProfile(final String ref, final int d) {
            this.referrer = ref;
            this.depth = d;
            this.count = 1;
        }
        
        public void inc() {
            this.count++;
        }
        
    }
    
    private Map<String, DomProfile> doms;
    
    // this is the process that checks url for double-occurrences and for allowance/disallowance by robots.txt

    public CrawlStacker(
            CrawlQueues cq,
            CrawlSwitchboard cs,
            Segment indexSegment,
            yacySeedDB peers,
            boolean acceptLocalURLs,
            boolean acceptGlobalURLs,
            FilterEngine domainList) {
        this.nextQueue = cq;
        this.crawler = cs;
        this.indexSegment = indexSegment;
        this.peers = peers;
        //this.dnsHit = 0;
        this.dnsMiss = 0;
        this.acceptLocalURLs = acceptLocalURLs;
        this.acceptGlobalURLs = acceptGlobalURLs;
        this.domainList = domainList;

        this.fastQueue = new WorkflowProcessor<Request>("CrawlStackerFast", "This process checks new urls before they are enqueued into the balancer (proper, double-check, correct domain, filter)", new String[]{"Balancer"}, this, "job", 10000, null, 2);
        this.slowQueue = new WorkflowProcessor<Request>("CrawlStackerSlow", "This is like CrawlStackerFast, but does additionaly a DNS lookup. The CrawlStackerFast does not need this because it can use the DNS cache.", new String[]{"Balancer"}, this, "job",  1000, null, 5);
        this.doms = new ConcurrentHashMap<String, DomProfile>();
        this.log.logInfo("STACKCRAWL thread initialized.");
    }

    private void domInc(final String domain, final String referrer, final int depth) {
        final DomProfile dp = doms.get(domain);
        if (dp == null) {
            // new domain
            doms.put(domain, new DomProfile(referrer, depth));
        } else {
            // increase counter
            dp.inc();
        }
    }
    public String domName(final boolean attr, final int index){
        final Iterator<Map.Entry<String, DomProfile>> domnamesi = doms.entrySet().iterator();
        String domname="";
        Map.Entry<String, DomProfile> ey;
        DomProfile dp;
        int i = 0;
        while ((domnamesi.hasNext()) && (i < index)) {
            ey = domnamesi.next();
            i++;
        }
        if (domnamesi.hasNext()) {
            ey = domnamesi.next();
            dp = ey.getValue();
            domname = ey.getKey() + ((attr) ? ("/r=" + dp.referrer + ", d=" + dp.depth + ", c=" + dp.count) : " ");
        }
        return domname;
    }
    
    public int size() {
        return this.fastQueue.queueSize() + this.slowQueue.queueSize();
    }
    public boolean isEmpty() {
        if (!this.fastQueue.queueIsEmpty()) return false;
        if (!this.slowQueue.queueIsEmpty()) return false;
        return true;
    }

    public void clear() {
        this.fastQueue.clear();
        this.slowQueue.clear();
        this.doms.clear();
    }

    public void announceClose() {
        this.log.logInfo("Flushing remaining " + size() + " crawl stacker job entries.");
        this.fastQueue.announceShutdown();
        this.slowQueue.announceShutdown();
    }

    public void close() {
        this.log.logInfo("Shutdown. waiting for remaining " + size() + " crawl stacker job entries. please wait.");
        this.fastQueue.announceShutdown();
        this.slowQueue.announceShutdown();
        this.fastQueue.awaitShutdown(2000);
        this.slowQueue.awaitShutdown(2000);

        this.log.logInfo("Shutdown. Closing stackCrawl queue.");

        clear();
    }

    private boolean prefetchHost(final String host) {
        // returns true when the host was known in the dns cache.
        // If not, the host is stacked on the fetch stack and false is returned
        try {
            if (Domains.dnsResolveFromCache(host) != null) return true; // found entry
        } catch (final UnknownHostException e) {
            // we know that this is unknown
            return false;
        }
        // we just don't know anything about that host
        return false;
    }
    
    public Request job(Request entry) {
        // this is the method that is called by the busy thread from outside
        if (entry == null) return null;

        try {
            final String rejectReason = stackCrawl(entry);

            // if the url was rejected we store it into the error URL db
            if (rejectReason != null) {
                nextQueue.errorURL.push(entry, UTF8.getBytes(peers.mySeed().hash), new Date(), 1, rejectReason, -1);
            }
        } catch (final Exception e) {
            CrawlStacker.this.log.logWarning("Error while processing stackCrawl entry.\n" + "Entry: " + entry.toString() + "Error: " + e.toString(), e);
            return null;
        }
        return null;
    }

    public void enqueueEntry(final Request entry) {

        // DEBUG
        if (log.isFinest()) log.logFinest("ENQUEUE " + entry.url() + ", referer=" + entry.referrerhash() + ", initiator=" + ((entry.initiator() == null) ? "" : UTF8.String(entry.initiator())) + ", name=" + entry.name() + ", appdate=" + entry.appdate() + ", depth=" + entry.depth());

        if (prefetchHost(entry.url().getHost())) {
            try {
                this.fastQueue.enQueue(entry);
                //this.dnsHit++;
            } catch (InterruptedException e) {
                Log.logException(e);
            }
        } else {
            try {
                this.slowQueue.enQueue(entry);
                this.dnsMiss++;
            } catch (InterruptedException e) {
                Log.logException(e);
            }
        }
    }
    public void enqueueEntriesAsynchronous(final byte[] initiator, final String profileHandle, final Map<MultiProtocolURI, Properties> hyperlinks, boolean replace) {
        new Thread() {
            public void run() {
                enqueueEntries(initiator, profileHandle, hyperlinks, true);
            }
        }.start();
    }

    public void enqueueEntries(byte[] initiator, String profileHandle, Map<MultiProtocolURI, Properties> hyperlinks, boolean replace) {
        for (Map.Entry<MultiProtocolURI, Properties> e: hyperlinks.entrySet()) {
            if (e.getKey() == null) continue;
            
            // delete old entry, if exists to force a re-load of the url (thats wanted here)
            final DigestURI url = new DigestURI(e.getKey());
            final byte[] urlhash = url.hash();
            if (replace) {
                indexSegment.urlMetadata().remove(urlhash);
                this.nextQueue.urlRemove(urlhash);
                String u = url.toNormalform(true, true);
                if (u.endsWith("/")) {
                    u = u + "index.html";
                } else if (!u.contains(".")) {
                    u = u + "/index.html";
                }
                try {
                    byte[] uh = new DigestURI(u, null).hash();
                    indexSegment.urlMetadata().remove(uh);
                    this.nextQueue.noticeURL.removeByURLHash(uh);
                    this.nextQueue.errorURL.remove(uh);
                } catch (MalformedURLException e1) {}
            }
            
            if (url.getProtocol().equals("ftp")) {
                // put the whole ftp site on the crawl stack
                enqueueEntriesFTP(initiator, profileHandle, url.getHost(), url.getPort(), replace);
            } else {
                // put entry on crawl stack
                enqueueEntry(new Request(
                        initiator, 
                        url, 
                        null, 
                        e.getValue().getProperty("name", ""), 
                        new Date(),
                        profileHandle,
                        0,
                        0,
                        0,
                        0
                        ));
            }
        }
    }
    
    public void enqueueEntriesFTP(final byte[] initiator, final String profileHandle, final String host, final int port, final boolean replace) {
        final CrawlQueues cq = this.nextQueue;
        new Thread() {
            public void run() {
                BlockingQueue<FTPClient.entryInfo> queue;
                try {
                    queue = FTPClient.sitelist(host, port);
                    FTPClient.entryInfo entry;
                    while ((entry = queue.take()) != FTPClient.POISON_entryInfo) {
                        
                        // delete old entry, if exists to force a re-load of the url (thats wanted here)
                        DigestURI url = null;
                        try {
                            url = new DigestURI("ftp://" + host + (port == 21 ? "" : ":" + port) + MultiProtocolURI.escape(entry.name));
                        } catch (MalformedURLException e) {
                            continue;
                        }
                        final byte[] urlhash = url.hash();
                        if (replace) {
                            indexSegment.urlMetadata().remove(urlhash);
                            cq.noticeURL.removeByURLHash(urlhash);
                            cq.errorURL.remove(urlhash);
                        }
                        
                        // put entry on crawl stack
                        enqueueEntry(new Request(
                                initiator, 
                                url, 
                                null, 
                                MultiProtocolURI.unescape(entry.name), 
                                entry.date,
                                profileHandle,
                                0,
                                0,
                                0,
                                entry.size
                                ));
                    }
                } catch (IOException e1) {
                } catch (InterruptedException e) {
                }
            }
        }.start();
    }
    
    /**
     * simple method to add one url as crawljob
     * @param url
     * @return null if successfull, a reason string if not successful
     */
    public String stackSimpleCrawl(final DigestURI url) {
    	CrawlProfile pe = this.crawler.defaultSurrogateProfile;
    	return stackCrawl(new Request(
                peers.mySeed().hash.getBytes(),
                url,
                null,
                "CRAWLING-ROOT",
                new Date(),
                pe.handle(),
                0,
                0,
                0,
                0
                ));
    }
    
    /**
     * stacks a crawl item. The position can also be remote
     * @param entry
     * @return null if successful, a reason string if not successful
     */
    public String stackCrawl(final Request entry) {
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");
       
        final CrawlProfile profile = crawler.getActive(UTF8.getBytes(entry.profileHandle()));
        String error;
        if (profile == null) {
            error = "LOST STACKER PROFILE HANDLE '" + entry.profileHandle() + "' for URL " + entry.url();
            log.logWarning(error);
            return error;
        }
        
        error = checkAcceptance(entry.url(), profile, entry.depth());
        if (error != null) return error;
        
        // store information
        final boolean local = Base64Order.enhancedCoder.equal(entry.initiator(), UTF8.getBytes(peers.mySeed().hash));
        final boolean proxy = (entry.initiator() == null || entry.initiator().length == 0 || UTF8.String(entry.initiator()).equals("------------")) && profile.handle().equals(crawler.defaultProxyProfile.handle());
        final boolean remote = profile.handle().equals(crawler.defaultRemoteProfile.handle());
        final boolean global =
            (profile.remoteIndexing()) /* granted */ &&
            (entry.depth() == profile.depth()) /* leaf node */ &&
            //(initiatorHash.equals(yacyCore.seedDB.mySeed.hash)) /* not proxy */ &&
            (
                    (peers.mySeed().isSenior()) ||
                    (peers.mySeed().isPrincipal())
            ) /* qualified */;

        if (!local && !global && !remote && !proxy) {
            error = "URL '" + entry.url().toString() + "' cannot be crawled. initiator = " + ((entry.initiator() == null) ? "" : UTF8.String(entry.initiator())) + ", profile.handle = " + profile.handle();
            this.log.logSevere(error);
            return error;
        }
        
        long maxFileSize = Long.MAX_VALUE;
        if (entry.size() > 0) {
            String protocol = entry.url().getProtocol();
            if (protocol.equals("http") || protocol.equals("https")) maxFileSize = Switchboard.getSwitchboard().getConfigLong("crawler.http.maxFileSize", HTTPLoader.DEFAULT_MAXFILESIZE);
            if (protocol.equals("ftp")) maxFileSize = Switchboard.getSwitchboard().getConfigLong("crawler.ftp.maxFileSize", FTPLoader.DEFAULT_MAXFILESIZE);
            if (protocol.equals("smb")) maxFileSize = Switchboard.getSwitchboard().getConfigLong("crawler.smb.maxFileSize", SMBLoader.DEFAULT_MAXFILESIZE);
        }

        // check availability of parser and maxfilesize
        String warning = null;
        if (entry.size() > maxFileSize ||
            (entry.url().getFileExtension().length() > 0 && TextParser.supports(entry.url(), null) != null)
            ) {
            warning = nextQueue.noticeURL.push(NoticedURL.StackType.NOLOAD, entry);
            if (warning != null) this.log.logWarning("CrawlStacker.stackCrawl of URL " + entry.url().toNormalform(true, false) + " - not pushed: " + warning);
            return null;
        }
        
        final DigestURI referrerURL = (entry.referrerhash() == null || entry.referrerhash().length == 0) ? null : nextQueue.getURL(entry.referrerhash());

        // add domain to profile domain list
        if (profile.domMaxPages() != Integer.MAX_VALUE) {
            domInc(entry.url().getHost(), (referrerURL == null) ? null : referrerURL.getHost().toLowerCase(), entry.depth());
        }

        if (global) {
            // it may be possible that global == true and local == true, so do not check an error case against it
            if (proxy) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: global = true, proxy = true, initiator = proxy" + ", profile.handle = " + profile.handle());
            if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: global = true, remote = true, initiator = " + UTF8.String(entry.initiator()) + ", profile.handle = " + profile.handle());
            warning = nextQueue.noticeURL.push(NoticedURL.StackType.LIMIT, entry);
        } else if (local) {
            if (proxy) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: local = true, proxy = true, initiator = proxy" + ", profile.handle = " + profile.handle());
            if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: local = true, remote = true, initiator = " + UTF8.String(entry.initiator()) + ", profile.handle = " + profile.handle());
            warning = nextQueue.noticeURL.push(NoticedURL.StackType.CORE, entry);
        } else if (proxy) {
            if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: proxy = true, remote = true, initiator = " + UTF8.String(entry.initiator()) + ", profile.handle = " + profile.handle());
            warning = nextQueue.noticeURL.push(NoticedURL.StackType.CORE, entry);
        } else if (remote) {
            warning = nextQueue.noticeURL.push(NoticedURL.StackType.REMOTE, entry);
        }
        if (warning != null) this.log.logWarning("CrawlStacker.stackCrawl of URL " + entry.url().toNormalform(true, false) + " - not pushed: " + warning);

        return null;
    }

    public String checkAcceptance(final DigestURI url, final CrawlProfile profile, int depth) {
        
        // check if the protocol is supported
        final String urlProtocol = url.getProtocol();
        if (!Switchboard.getSwitchboard().loader.isSupportedProtocol(urlProtocol)) {
            this.log.logSevere("Unsupported protocol in URL '" + url.toString() + "'.");
            return "unsupported protocol";
        }

        // check if ip is local ip address
        final String urlRejectReason = urlInAcceptedDomain(url);
        if (urlRejectReason != null) {
            if (this.log.isFine()) this.log.logFine("denied_(" + urlRejectReason + ")");
            return "denied_(" + urlRejectReason + ")";
        }

        // check blacklist
        if (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, url)) {
            if (this.log.isFine()) this.log.logFine("URL '" + url.toString() + "' is in blacklist.");
            return "url in blacklist";
        }

        // filter with must-match
        if ((depth > 0) && !profile.mustMatchPattern().matcher(url.toString()).matches()) {
            if (this.log.isFine()) this.log.logFine("URL '" + url.toString() + "' does not match must-match crawling filter '" + profile.mustMatchPattern().toString() + "'.");
            return "url does not match must-match filter";
        }

        // filter with must-not-match
        if ((depth > 0) && profile.mustNotMatchPattern().matcher(url.toString()).matches()) {
            if (this.log.isFine()) this.log.logFine("URL '" + url.toString() + "' does matches do-not-match crawling filter '" + profile.mustNotMatchPattern().toString() + "'.");
            return "url matches must-not-match filter";
        }

        // deny cgi
        if (url.isIndividual() && !(profile.crawlingQ()))  { // TODO: make special property for crawlingIndividual
            if (this.log.isFine()) this.log.logFine("URL '" + url.toString() + "' is CGI URL.");
            return "individual url (sessionid etc) not wanted";
        }

        // deny post properties
        if (url.isPOST() && !(profile.crawlingQ()))  {
            if (this.log.isFine()) this.log.logFine("URL '" + url.toString() + "' is post URL.");
            return "post url not allowed";
        }

        // check if the url is double registered
        final String dbocc = nextQueue.urlExists(url.hash()); // returns the name of the queue if entry exists
        URIMetadataRow oldEntry = indexSegment.urlMetadata().load(url.hash());
        if (oldEntry == null) {
            if (dbocc != null) {
                // do double-check
                if (this.log.isFine()) this.log.logFine("URL '" + url.toString() + "' is double registered in '" + dbocc + "'.");
                if (dbocc.equals("errors")) {
                    ZURL.Entry errorEntry = nextQueue.errorURL.get(url.hash());
                    return "double in: errors (" + errorEntry.anycause() + ")";
                } else {
                    return "double in: " + dbocc;
                }
            }
        } else {
            final boolean recrawl = profile.recrawlIfOlder() > oldEntry.loaddate().getTime();
            if (recrawl) {
                if (this.log.isInfo()) 
                    this.log.logInfo("RE-CRAWL of URL '" + url.toString() + "': this url was crawled " +
                        ((System.currentTimeMillis() - oldEntry.loaddate().getTime()) / 60000 / 60 / 24) + " days ago.");
            } else {
                if (dbocc == null) {
                    return "double in: LURL-DB";
                } else {
                    if (this.log.isInfo()) this.log.logInfo("URL '" + url.toString() + "' is double registered in '" + dbocc + "'. " + "Stack processing time:");
                    if (dbocc.equals("errors")) {
                        ZURL.Entry errorEntry = nextQueue.errorURL.get(url.hash());
                        return "double in: errors (" + errorEntry.anycause() + ")";
                    } else {
                        return "double in: " + dbocc;
                    }
                }
            }
        }

        // deny urls that exceed allowed number of occurrences
        final int maxAllowedPagesPerDomain = profile.domMaxPages();
        if (maxAllowedPagesPerDomain < Integer.MAX_VALUE) {
            final DomProfile dp = doms.get(url.getHost());
            if (dp != null && dp.count >= maxAllowedPagesPerDomain) {
                if (this.log.isFine()) this.log.logFine("URL '" + url.toString() + "' appeared too often in crawl stack, a maximum of " + profile.domMaxPages() + " is allowed.");
                return "crawl stack domain counter exceeded";
            }
            
            if (ResultURLs.domainCount(EventOrigin.LOCAL_CRAWLING, url.getHost()) >= profile.domMaxPages()) {
                if (this.log.isFine()) this.log.logFine("URL '" + url.toString() + "' appeared too often in result stack, a maximum of " + profile.domMaxPages() + " is allowed.");
                return "result stack domain counter exceeded";
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
    public String urlInAcceptedDomain(final DigestURI url) {
        // returns true if the url can be accepted according to network.unit.domain
        if (url == null) return "url is null";
        // check domainList from network-definition
        if(this.domainList != null) {
        	if(!this.domainList.isListed(url, null)) {
        		return "the url '" + url + "' is not in domainList of this network";
        	}
        }
        final boolean local = url.isLocal();
        if (this.acceptLocalURLs && local) return null;
        if (this.acceptGlobalURLs && !local) return null;
        final String host = url.getHost();
        if (host == null) return "url.host is null";
        // check if this is a local address and we are allowed to index local pages:
        //boolean local = hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress();
        //assert local == yacyURL.isLocalDomain(url.hash()); // TODO: remove the dnsResolve above!
        InetAddress ia = Domains.dnsResolve(host);
        return (local) ?
            ("the host '" + host + "' is local, but local addresses are not accepted: " + ((ia == null) ? "null" : ia.getHostAddress())) :
            ("the host '" + host + "' is global, but global addresses are not accepted: " + ((ia == null) ? "null" : ia.getHostAddress()));
    }
    
    public String urlInAcceptedDomainHash(final byte[] urlhash) {
        // returns true if the url can be accepted according to network.unit.domain
        if (urlhash == null) return "url is null";
        // check if this is a local address and we are allowed to index local pages:
        final boolean local = DigestURI.isLocal(urlhash);
        if (this.acceptLocalURLs && local) return null;
        if (this.acceptGlobalURLs && !local) return null;
        return (local) ?
            ("the urlhash '" + UTF8.String(urlhash) + "' is local, but local addresses are not accepted") :
            ("the urlhash '" + UTF8.String(urlhash) + "' is global, but global addresses are not accepted");
    }

    public boolean acceptLocalURLs() {
        return this.acceptLocalURLs;
    }

    public boolean acceptGlobalURLs() {
        return this.acceptGlobalURLs;
    }
}
