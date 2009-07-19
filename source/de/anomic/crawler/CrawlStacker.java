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
import java.util.Date;

import de.anomic.crawler.retrieval.Request;
import de.anomic.data.Blacklist;
import de.anomic.kelondro.text.Segment;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.search.Switchboard;
import de.anomic.server.serverDomains;
import de.anomic.server.serverProcessor;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public final class CrawlStacker {

    private Log log = new Log("STACKCRAWL");

    private serverProcessor<Request> fastQueue, slowQueue;
    private long                      dnsHit, dnsMiss;
    private CrawlQueues               nextQueue;
    private CrawlSwitchboard          crawler;
    private Segment                   indexSegment;
    private yacySeedDB                peers;
    private boolean                   acceptLocalURLs, acceptGlobalURLs;

    // this is the process that checks url for double-occurrences and for allowance/disallowance by robots.txt

    public CrawlStacker(
            CrawlQueues cq,
            CrawlSwitchboard cs,
            Segment indexSegment,
            yacySeedDB peers,
            boolean acceptLocalURLs,
            boolean acceptGlobalURLs) {
        this.nextQueue = cq;
        this.crawler = cs;
        this.indexSegment = indexSegment;
        this.peers = peers;
        this.dnsHit = 0;
        this.dnsMiss = 0;
        this.acceptLocalURLs = acceptLocalURLs;
        this.acceptGlobalURLs = acceptGlobalURLs;

        this.fastQueue = new serverProcessor<Request>("CrawlStackerFast", "This process checks new urls before they are enqueued into the balancer (proper, double-check, correct domain, filter)", new String[]{"Balancer"}, this, "job", 10000, null, 2);
        this.slowQueue = new serverProcessor<Request>("CrawlStackerSlow", "This is like CrawlStackerFast, but does additionaly a DNS lookup. The CrawlStackerFast does not need this because it can use the DNS cache.", new String[]{"Balancer"}, this, "job",  1000, null, 5);

        this.log.logInfo("STACKCRAWL thread initialized.");
    }

    public int size() {
        return this.fastQueue.queueSize() + this.slowQueue.queueSize();
    }

    public void clear() {
        this.fastQueue.clear();
        this.slowQueue.clear();
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
            if (serverDomains.dnsResolveFromCache(host) != null) return true; // found entry
        } catch (final UnknownHostException e) {
            // we know that this is unknown
            return false;
        }
        // we just don't know anything about that host
        return false;
    }

    /*
    public boolean job() {
        if (this.fastQueue.queueSize() > 0 && job(this.fastQueue)) return true;
        if (this.slowQueue.queueSize() == 0) return false;
        return job(this.slowQueue);
    }
    */

    public Request job(Request entry) {
        // this is the method that is called by the busy thread from outside
        if (entry == null) return null;

        try {
            final String rejectReason = stackCrawl(entry);

            // if the url was rejected we store it into the error URL db
            if (rejectReason != null) {
                final ZURL.Entry ee = nextQueue.errorURL.newEntry(entry, peers.mySeed().hash, new Date(), 1, rejectReason);
                ee.store();
                nextQueue.errorURL.push(ee);
            }
        } catch (final Exception e) {
            CrawlStacker.this.log.logWarning("Error while processing stackCrawl entry.\n" + "Entry: " + entry.toString() + "Error: " + e.toString(), e);
            return null;
        }
        return null;
    }

    public void enqueueEntry(final Request entry) {

        // DEBUG
        if (log.isFinest()) log.logFinest("ENQUEUE " + entry.url() + ", referer=" + entry.referrerhash() + ", initiator=" + entry.initiator() + ", name=" + entry.name() + ", load=" + entry.loaddate() + ", depth=" + entry.depth());

        if (prefetchHost(entry.url().getHost())) {
            try {
                this.fastQueue.enQueue(entry);
                this.dnsHit++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            try {
                this.slowQueue.enQueue(entry);
                this.dnsMiss++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public String stackCrawl(final Request entry) {
        // stacks a crawl item. The position can also be remote
        // returns null if successful, a reason string if not successful
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");

        final long startTime = System.currentTimeMillis();

        // check if the protocol is supported
        final String urlProtocol = entry.url().getProtocol();
        if (!nextQueue.isSupportedProtocol(urlProtocol)) {
            this.log.logSevere("Unsupported protocol in URL '" + entry.url().toString() + "'. " +
                               "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "unsupported protocol";
        }

        // check if ip is local ip address
        final String urlRejectReason = urlInAcceptedDomain(entry.url());
        if (urlRejectReason != null) {
            if (this.log.isFine()) this.log.logFine("denied_(" + urlRejectReason + ") Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "denied_(" + urlRejectReason + ")";
        }

        // check blacklist
        if (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, entry.url())) {
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is in blacklist. " +
                             "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "url in blacklist";
        }

        final CrawlProfile.entry profile = crawler.profilesActiveCrawls.getEntry(entry.profileHandle());
        if (profile == null) {
            final String errorMsg = "LOST STACKER PROFILE HANDLE '" + entry.profileHandle() + "' for URL " + entry.url();
            log.logWarning(errorMsg);
            return errorMsg;
        }

        // filter with must-match
        if ((entry.depth() > 0) && !profile.mustMatchPattern().matcher(entry.url().toString()).matches()) {
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' does not match must-match crawling filter '" + profile.mustMatchPattern().toString() + "'. " +
                             "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "url does not match must-match filter";
        }

        // filter with must-not-match
        if ((entry.depth() > 0) && profile.mustNotMatchPattern().matcher(entry.url().toString()).matches()) {
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' does matches do-not-match crawling filter '" + profile.mustNotMatchPattern().toString() + "'. " +
                             "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "url matches must-not-match filter";
        }

        // deny cgi
        if (entry.url().isIndividual())  {
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is CGI URL. " +
                             "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "cgi url not allowed";
        }

        // deny post properties
        if (entry.url().isPOST() && !(profile.crawlingQ()))  {
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is post URL. " +
                             "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "post url not allowed";
        }

        final yacyURL referrerURL = (entry.referrerhash() == null) ? null : nextQueue.getURL(entry.referrerhash());

        // add domain to profile domain list
        if ((profile.domFilterDepth() != Integer.MAX_VALUE) || (profile.domMaxPages() != Integer.MAX_VALUE)) {
            profile.domInc(entry.url().getHost(), (referrerURL == null) ? null : referrerURL.getHost().toLowerCase(), entry.depth());
        }

        // deny urls that do not match with the profile domain list
        if (!(profile.grantedDomAppearance(entry.url().getHost()))) {
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is not listed in granted domains. " +
                             "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "url does not match domain filter";
        }

        // deny urls that exceed allowed number of occurrences
        if (!(profile.grantedDomCount(entry.url().getHost()))) {
            if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' appeared too often, a maximum of " + profile.domMaxPages() + " is allowed. " +
                             "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
            return "domain counter exceeded";
        }

        // check if the url is double registered
        final String dbocc = nextQueue.urlExists(entry.url().hash());
        if (dbocc != null || indexSegment.urlMetadata().exists(entry.url().hash())) {
            final URLMetadataRow oldEntry = indexSegment.urlMetadata().load(entry.url().hash(), null, 0);
            final boolean recrawl = (oldEntry != null) && (profile.recrawlIfOlder() > oldEntry.loaddate().getTime());
            // do double-check
            if ((dbocc != null) && (!recrawl)) {
                if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is double registered in '" + dbocc + "'. " + "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
                return "double " + dbocc;
            }
            if ((oldEntry != null) && (!recrawl)) {
                if (this.log.isFine()) this.log.logFine("URL '" + entry.url().toString() + "' is double registered in 'LURL'. " + "Stack processing time: " + (System.currentTimeMillis() - startTime) + "ms");
                return "double LURL";
            }

            // show potential re-crawl
            if (recrawl && oldEntry != null) {
                if (this.log.isFine()) this.log.logFine("RE-CRAWL of URL '" + entry.url().toString() + "': this url was crawled " +
                        ((System.currentTimeMillis() - oldEntry.loaddate().getTime()) / 60000 / 60 / 24) + " days ago.");
            }
        }

        // store information
        final boolean local = entry.initiator().equals(peers.mySeed().hash);
        final boolean proxy = (entry.initiator() == null || entry.initiator().length() == 0 || entry.initiator().equals("------------")) && profile.handle().equals(crawler.defaultProxyProfile.handle());
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
            String error = "URL '" + entry.url().toString() + "' cannot be crawled. initiator = " + entry.initiator() + ", profile.handle = " + profile.handle();
            this.log.logSevere(error);
            return error;
        }
        
        if (global) {
            // it may be possible that global == true and local == true, so do not check an error case against it
            if (proxy) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: global = true, proxy = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
            if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: global = true, remote = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
            //int b = nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT);
            nextQueue.noticeURL.push(NoticedURL.STACK_TYPE_LIMIT, entry);
            //assert b < nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT);
            //this.log.logInfo("stacked/global: " + entry.url().toString() + ", stacksize = " + nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT));
        } else if (local) {
            if (proxy) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: local = true, proxy = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
            if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: local = true, remote = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
            //int b = nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE);
            nextQueue.noticeURL.push(NoticedURL.STACK_TYPE_CORE, entry);
            //assert b < nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE);
            //this.log.logInfo("stacked/local: " + entry.url().toString() + ", stacksize = " + nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE));
        } else if (proxy) {
            if (remote) this.log.logWarning("URL '" + entry.url().toString() + "' has conflicting initiator properties: proxy = true, remote = true, initiator = " + entry.initiator() + ", profile.handle = " + profile.handle());
            //int b = nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE);
            nextQueue.noticeURL.push(NoticedURL.STACK_TYPE_CORE, entry);
            //assert b < nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE);
            //this.log.logInfo("stacked/proxy: " + entry.url().toString() + ", stacksize = " + nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE));
        } else if (remote) {
            //int b = nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_REMOTE);
            nextQueue.noticeURL.push(NoticedURL.STACK_TYPE_REMOTE, entry);
            //assert b < nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_REMOTE);
            //this.log.logInfo("stacked/remote: " + entry.url().toString() + ", stacksize = " + nextQueue.noticeURL.stackSize(NoticedURL.STACK_TYPE_REMOTE));
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
        // check if this is a local address and we are allowed to index local pages:
        //boolean local = hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress();
        final boolean local = url.isLocal();
        //assert local == yacyURL.isLocalDomain(url.hash()); // TODO: remove the dnsResolve above!
        if ((this.acceptGlobalURLs && !local) || (this.acceptLocalURLs && local)) return null;
        return (local) ?
            ("the host '" + host + "' is local, but local addresses are not accepted") :
            ("the host '" + host + "' is global, but global addresses are not accepted");
    }
    
    public String urlInAcceptedDomainHash(final String urlhash) {
        // returns true if the url can be accepted accoring to network.unit.domain
        if (urlhash == null) return "url is null";
        if (this.acceptGlobalURLs && this.acceptLocalURLs) return null; // fast shortcut to avoid dnsResolve
        // check if this is a local address and we are allowed to index local pages:
        //boolean local = hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress();
        final boolean local = yacyURL.isLocal(urlhash);
        //assert local == yacyURL.isLocalDomain(url.hash()); // TODO: remove the dnsResolve above!
        if ((this.acceptGlobalURLs && !local) || (this.acceptLocalURLs && local)) return null;
        return (local) ?
            ("the urlhash '" + urlhash + "' is local, but local addresses are not accepted") :
            ("the urlhash '" + urlhash + "' is global, but global addresses are not accepted");
    }

    public boolean acceptLocalURLs() {
        return this.acceptLocalURLs;
    }

    public boolean acceptGlobalURLs() {
        return this.acceptGlobalURLs;
    }
}
