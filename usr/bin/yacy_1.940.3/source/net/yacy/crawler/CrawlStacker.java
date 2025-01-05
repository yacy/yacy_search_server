// plasmaCrawlStacker.java
// -----------------------
// part of YaCy
// SPDX-FileCopyrightText: 2005 Michael Peter Christen <mc@yacy.net)>
// SPDX-License-Identifier: GPL-2.0-or-later
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// This file was contributed by Martin Thelian
// ([MC] removed all multithreading and thread pools, this is not necessary here; complete renovation 2007)
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

package net.yacy.crawler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.CrawlQueues;
import net.yacy.crawler.data.NoticedURL;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.document.TextParser;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.kelondro.workflow.WorkflowTask;
import net.yacy.peers.SeedDB;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.FilterEngine;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;

public final class CrawlStacker implements WorkflowTask<Request>{

    public static String ERROR_NO_MATCH_MUST_MATCH_FILTER = "url does not match must-match filter ";
    public static String ERROR_MATCH_WITH_MUST_NOT_MATCH_FILTER = "url matches must-not-match filter ";

    /** Crawl reject reason prefix having specific processing */
    public static final String CRAWL_REJECT_REASON_DOUBLE_IN_PREFIX = "double in";

    private final static ConcurrentLog log = new ConcurrentLog("STACKCRAWL");

    private final RobotsTxt robots;
    private final WorkflowProcessor<Request>  requestQueue;
    public  final CrawlQueues       nextQueue;
    private final CrawlSwitchboard  crawler;
    private final Segment           indexSegment;
    private final SeedDB            peers;
    private final boolean           acceptLocalURLs, acceptGlobalURLs;
    private final FilterEngine      domainList;

    // this is the process that checks url for double-occurrences and for allowance/disallowance by robots.txt

    public CrawlStacker(
            final RobotsTxt robots,
            final CrawlQueues cq,
            final CrawlSwitchboard cs,
            final Segment indexSegment,
            final SeedDB peers,
            final boolean acceptLocalURLs,
            final boolean acceptGlobalURLs,
            final FilterEngine domainList) {
        this.robots = robots;
        this.nextQueue = cq;
        this.crawler = cs;
        this.indexSegment = indexSegment;
        this.peers = peers;
        this.acceptLocalURLs = acceptLocalURLs;
        this.acceptGlobalURLs = acceptGlobalURLs;
        this.domainList = domainList;
        this.requestQueue = new WorkflowProcessor<>("CrawlStacker", "This process checks new urls before they are enqueued into the balancer (proper, double-check, correct domain, filter)", new String[]{"Balancer"}, this, 10000, null, WorkflowProcessor.availableCPU);
        CrawlStacker.log.info("STACKCRAWL thread initialized.");
    }

    public int size() {
        return this.requestQueue.getQueueSize();
    }

    public boolean isEmpty() {
        if (!this.requestQueue.queueIsEmpty()) return false;
        return true;
    }

    public void clear() {
        this.requestQueue.clear();
    }

    public void announceClose() {
        CrawlStacker.log.info("Flushing remaining " + this.size() + " crawl stacker job entries.");
        this.requestQueue.shutdown();
    }

    public synchronized void close() {
        CrawlStacker.log.info("Shutdown. waiting for remaining " + this.size() + " crawl stacker job entries. please wait.");
        this.requestQueue.shutdown();
        
        // busy waiting for the queue to empty
        for (int i = 0; i < 10; i++) {
        	if (this.size() <= 0) break;
        	try {Thread.sleep(1000);} catch (InterruptedException e) {}
        }

        CrawlStacker.log.info("Shutdown. Closing stackCrawl queue.");

        this.clear();
    }

    @Override
    public Request process(final Request entry) {
        // this is the method that is called by the busy thread from outside
        if (entry == null) return null;

        try {
            final String rejectReason = this.stackCrawl(entry);

            // if the url was rejected we store it into the error URL db
            if (rejectReason != null && !rejectReason.startsWith(CRAWL_REJECT_REASON_DOUBLE_IN_PREFIX)) {
                final CrawlProfile profile = this.crawler.get(UTF8.getBytes(entry.profileHandle()));
                this.nextQueue.errorURL.push(entry.url(), entry.depth(), profile, FailCategory.FINAL_LOAD_CONTEXT, rejectReason, -1);
            }
        } catch (final Exception e) {
            CrawlStacker.log.warn("Error while processing stackCrawl entry.\n" + "Entry: " + entry.toString() + "Error: " + e.toString(), e);
            return null;
        }
        return null;
    }

    public void enqueueEntry(final Request entry) {

        // DEBUG
        if (CrawlStacker.log.isFinest()) CrawlStacker.log.finest("ENQUEUE " + entry.url() + ", referer=" + entry.referrerhash() + ", initiator=" + ((entry.initiator() == null) ? "" : ASCII.String(entry.initiator())) + ", name=" + entry.name() + ", appdate=" + entry.appdate() + ", depth=" + entry.depth());
        this.requestQueue.enQueue(entry);
    }

    public void enqueueEntriesAsynchronous(
            final byte[] initiator,
            final String profileHandle,
            final List<AnchorURL> hyperlinks,
            final int timezoneOffset) {
        new Thread("enqueueEntriesAsynchronous") {
            @Override
            public void run() {
                CrawlStacker.this.enqueueEntries(initiator, profileHandle, hyperlinks, true, timezoneOffset);
            }
        }.start();
    }

    /**
     * Enqueue crawl start entries
     * @param initiator Hash of the peer initiating the crawl
     * @param profileHandle name of the active crawl profile
     * @param hyperlinks crawl starting points links to stack
     * @param replace Specify whether old indexed entries should be replaced
     * @param timezoneOffset local time-zone offset
     * @throws IllegalCrawlProfileException when the crawl profile is not active
     */
    public void enqueueEntries(
            final byte[] initiator,
            final String profileHandle,
            final List<AnchorURL> hyperlinks,
            final boolean replace,
            final int timezoneOffset) {
        /* Let's check if the profile is still active before removing any existing entry */
        final byte[] handle = UTF8.getBytes(profileHandle);
        final CrawlProfile profile = this.crawler.get(handle);
        if (profile == null) {
            String error;
            if(hyperlinks.size() == 1) {
                error = "Rejected URL : " + hyperlinks.get(0).toNormalform(false) + ". Reason : LOST STACKER PROFILE HANDLE '" + profileHandle + "'";
            } else {
                error = "Rejected " + hyperlinks.size() + " crawl entries. Reason : LOST STACKER PROFILE HANDLE '" + profileHandle + "'";
            }
            CrawlStacker.log.info(error); // this is NOT an error but a normal behavior when terminating a crawl queue
            /* Throw an exception to signal caller it can stop stacking URLs using this crawl profile */
            throw new IllegalCrawlProfileException("Profile " + profileHandle + " is no more active");
        }
        if (replace) {
            // delete old entries, if exists to force a re-load of the url (thats wanted here)
            final Set<String> hosthashes = new HashSet<>();
            for (final AnchorURL url: hyperlinks) {
                if (url == null) continue;
                hosthashes.add(url.hosthash());
            }
            this.nextQueue.errorURL.removeHosts(hosthashes);
        }
        for (final AnchorURL url: hyperlinks) {
            if (url == null) continue;

            // delete old entry, if exists to force a re-load of the url (thats wanted here)
            final byte[] urlhash = url.hash();
            if (replace) {
                this.indexSegment.fulltext().remove(urlhash);
                String u = url.toNormalform(true);
                if (u.endsWith("/")) {
                    u = u + "index.html";
                } else if (!u.contains(".")) {
                    u = u + "/index.html";
                }
                try {
                    final byte[] uh = new DigestURL(u).hash();
                    this.indexSegment.fulltext().remove(uh);
                    this.nextQueue.noticeURL.removeByURLHash(uh);
                } catch (final MalformedURLException e1) {}
            }

            if (url.getProtocol().equals("ftp")) {
                /* put ftp site entries on the crawl stack,
                 * using the crawl profile depth to control how many children folders of the url are stacked */
                this.enqueueEntriesFTP(initiator, profile, url, replace, timezoneOffset);
            } else {
                // put entry on crawl stack
                this.enqueueEntry(new Request(
                        initiator,
                        url,
                        null,
                        url.getNameProperty(),
                        new Date(),
                        profileHandle,
                        0,
                        timezoneOffset
                        ));
            }
        }
    }

    /**
     * Asynchronously enqueue crawl start entries for a ftp url.
     * @param initiator Hash of the peer initiating the crawl
     * @param profile the active crawl profile
     * @param ftpURL crawl start point URL : protocol must be ftp
     * @param replace Specify whether old indexed entries should be replaced
     * @param timezoneOffset local time-zone offset
     */
    public void enqueueEntriesFTP(
            final byte[] initiator,
            final CrawlProfile profile,
            final DigestURL ftpURL,
            final boolean replace,
            final int timezoneOffset) {
        final CrawlQueues cq = this.nextQueue;
        final String userInfo = ftpURL.getUserInfo();
        final int p = userInfo == null ? -1 : userInfo.indexOf(':');
        final String user = userInfo == null ? FTPClient.ANONYMOUS : userInfo.substring(0, p);
        final String pw = userInfo == null || p == -1 ? "anomic" : userInfo.substring(p + 1);
        final String host = ftpURL.getHost();
        final int port = ftpURL.getPort();
        final int pathParts = ftpURL.getPaths().length;
        new Thread("enqueueEntriesFTP") {
            @Override
            public void run() {
                BlockingQueue<FTPClient.entryInfo> queue;
                try {
                    queue = FTPClient.sitelist(host, port, user, pw, ftpURL.getPath(), profile.depth());
                    FTPClient.entryInfo entry;
                    while ((entry = queue.take()) != FTPClient.POISON_entryInfo) {

                        // delete old entry, if exists to force a re-load of the url (thats wanted here)
                        DigestURL url = null;
                        try {
                            url = new DigestURL("ftp://" + user + ":" + pw + "@" + host + (port == 21 ? "" : ":" + port) + MultiProtocolURL.escape(entry.name));
                        } catch (final MalformedURLException e) {
                            continue;
                        }
                        final byte[] urlhash = url.hash();
                        if (replace) {
                            CrawlStacker.this.indexSegment.fulltext().remove(urlhash);
                            cq.noticeURL.removeByURLHash(urlhash);
                        }

                        /* Each entry is a children resource of the starting ftp URL :
                         * take into account the sub folder depth in the crawl depth control */
                        final int nextDepth = Math.max(0, url.getPaths().length - pathParts);

                        // put entry on crawl stack
                        CrawlStacker.this.enqueueEntry(new Request(
                                initiator,
                                url,
                                null,
                                MultiProtocolURL.unescape(entry.name),
                                entry.date,
                                profile.handle(),
                                nextDepth,
                                timezoneOffset));
                    }
                } catch (final IOException e1) {
                    ConcurrentLog.logException(e1);
                } catch (final InterruptedException e) {
                }
            }
        }.start();
    }

    /**
     * simple method to add one url as crawljob
     * @param url
     * @return null if successfull, a reason string if not successful
     */
    public String stackSimpleCrawl(final DigestURL url) {
        final CrawlProfile pe = this.crawler.defaultSurrogateProfile;
        return this.stackCrawl(new Request(
                this.peers.mySeed().hash.getBytes(),
                url,
                null,
                "CRAWLING-ROOT",
                new Date(),
                pe.handle(),
                0, 0));
    }

    /**
     * stacks a crawl item. The position can also be remote
     * @param entry
     * @return null if successful, a reason string if not successful
     */
    public String stackCrawl(final Request entry) {
        //this.log.logFinest("stackCrawl: nexturlString='" + nexturlString + "'");

        final byte[] handle = UTF8.getBytes(entry.profileHandle());
        final CrawlProfile profile = this.crawler.get(handle);
        String error;
        if (profile == null) {
            error = "LOST STACKER PROFILE HANDLE '" + entry.profileHandle() + "' for URL " + entry.url().toNormalform(true);
            CrawlStacker.log.info(error); // this is NOT an error but a normal effect when terminating a crawl queue
            return error;
        }

        error = this.checkAcceptanceChangeable(entry.url(), profile, entry.depth());
        if (error != null) return error;
        error = this.checkAcceptanceInitially(entry.url(), profile);
        if (error != null) return error;

        // store information
        final boolean local = Base64Order.enhancedCoder.equal(entry.initiator(), UTF8.getBytes(this.peers.mySeed().hash));
        final boolean proxy = (entry.initiator() == null || entry.initiator().length == 0 || ASCII.String(entry.initiator()).equals("------------")) && profile.handle().equals(this.crawler.defaultProxyProfile.handle());
        final boolean remote = profile.handle().equals(this.crawler.defaultRemoteProfile.handle());
        final boolean global =
                (profile.remoteIndexing()) /* granted */ &&
                (entry.depth() == profile.depth()) /* leaf node */ &&
                //(initiatorHash.equals(yacyCore.seedDB.mySeed.hash)) /* not proxy */ &&
                (
                        (this.peers.mySeed().isSenior()) ||
                        (this.peers.mySeed().isPrincipal())
                        ) /* qualified */;

        if (!local && !global && !remote && !proxy) {
            error = "URL '" + entry.url().toString() + "' cannot be crawled. initiator = " + ((entry.initiator() == null) ? "" : ASCII.String(entry.initiator())) + ", profile.handle = " + profile.handle();
            CrawlStacker.log.severe(error);
            return error;
        }

        String warning = null;
        if (!profile.isCrawlerAlwaysCheckMediaType() && TextParser.supportsExtension(entry.url()) != null) {
            if(profile.isIndexNonParseableUrls()) {
                /* Unsupported file extension and no cross-checking of Media Type : add immediately to the noload stack to index only URL metadata */
                warning = this.nextQueue.noticeURL.push(NoticedURL.StackType.NOLOAD, entry, profile, this.robots);
                if (warning != null && CrawlStacker.log.isFine()) {
                    CrawlStacker.log.fine("CrawlStacker.stackCrawl of URL " + entry.url().toNormalform(true) + " - not pushed to " + NoticedURL.StackType.NOLOAD + " stack : " + warning);
                }
                return null;
            }

            error = "URL '" + entry.url().toString() + "' file extension is not supported and indexing of linked non-parsable documents is disabled.";
            CrawlStacker.log.info(error);
            return error;
        }

        if (global) {
            // it may be possible that global == true and local == true, so do not check an error case against it
            if (proxy) CrawlStacker.log.warn("URL '" + entry.url().toString() + "' has conflicting initiator properties: global = true, proxy = true, initiator = proxy" + ", profile.handle = " + profile.handle());
            if (remote) CrawlStacker.log.warn("URL '" + entry.url().toString() + "' has conflicting initiator properties: global = true, remote = true, initiator = " + ASCII.String(entry.initiator()) + ", profile.handle = " + profile.handle());
            warning = this.nextQueue.noticeURL.push(NoticedURL.StackType.GLOBAL, entry, profile, this.robots);
        } else if (local) {
            if (proxy) CrawlStacker.log.warn("URL '" + entry.url().toString() + "' has conflicting initiator properties: local = true, proxy = true, initiator = proxy" + ", profile.handle = " + profile.handle());
            if (remote) CrawlStacker.log.warn("URL '" + entry.url().toString() + "' has conflicting initiator properties: local = true, remote = true, initiator = " + ASCII.String(entry.initiator()) + ", profile.handle = " + profile.handle());
            warning = this.nextQueue.noticeURL.push(NoticedURL.StackType.LOCAL, entry, profile, this.robots);
        } else if (proxy) {
            if (remote) CrawlStacker.log.warn("URL '" + entry.url().toString() + "' has conflicting initiator properties: proxy = true, remote = true, initiator = " + ASCII.String(entry.initiator()) + ", profile.handle = " + profile.handle());
            warning = this.nextQueue.noticeURL.push(NoticedURL.StackType.LOCAL, entry, profile, this.robots);
        } else if (remote) {
            warning = this.nextQueue.noticeURL.push(NoticedURL.StackType.REMOTE, entry, profile, this.robots);
        }
        if (warning != null && CrawlStacker.log.isFine()) CrawlStacker.log.fine("CrawlStacker.stackCrawl of URL " + entry.url().toNormalform(true) + " - not pushed: " + warning);

        return null;
    }

    /**
     * Test if an url shall be accepted for crawl using attributes that are consistent for the whole crawl
     * These tests are incomplete and must be followed with an checkAcceptanceChangeable - test.
     * @param url
     * @param profile
     * @return null if the url is accepted, an error string in case if the url is not accepted with an error description
     */
    public String checkAcceptanceInitially(final DigestURL url, final CrawlProfile profile) {

        // check if the url is double registered
        final HarvestProcess dbocc = this.nextQueue.exists(url.hash()); // returns the name of the queue if entry exists
        if (dbocc != null) {
            return CRAWL_REJECT_REASON_DOUBLE_IN_PREFIX + ": " + dbocc.name();
        }
        final String urls = url.toNormalform(false);
        final long oldDate = this.indexSegment.getLoadTime(url.hash());

        // deny urls that exceed allowed number of occurrences
        final int maxAllowedPagesPerDomain = profile.domMaxPages();
        if (maxAllowedPagesPerDomain < Integer.MAX_VALUE && maxAllowedPagesPerDomain > 0) {
            final AtomicInteger dp = profile.getCount(url.getHost());
            if (dp != null && dp.get() >= maxAllowedPagesPerDomain) {
                if (CrawlStacker.log.isFine()) CrawlStacker.log.fine("URL '" + urls + "' appeared too often in crawl stack, a maximum of " + maxAllowedPagesPerDomain + " is allowed.");
                return "crawl stack domain counter exceeded (test by profile)";
            }

            /*
            if (ResultURLs.domainCount(EventOrigin.LOCAL_CRAWLING, url.getHost()) >= maxAllowedPagesPerDomain) {
                if (this.log.isFine()) this.log.fine("URL '" + urlstring + "' appeared too often in result stack, a maximum of " + maxAllowedPagesPerDomain + " is allowed.");
                return "result stack domain counter exceeded (test by domainCount)";
            }
             */
        }

        //final Long oldDate = oldEntry == null ? null : oldEntry.date;
        if (oldDate < 0) {
            return null; // no evidence that we know that url
        }
        final boolean recrawl = profile.recrawlIfOlder() > oldDate;
        final String urlstring = url.toNormalform(false);
        if (recrawl) {
            if (CrawlStacker.log.isFine())
                CrawlStacker.log.fine("RE-CRAWL of URL '" + urlstring + "': this url was crawled " +
                        ((System.currentTimeMillis() - oldDate) / 60000 / 60 / 24) + " days ago.");
        } else {
            return CRAWL_REJECT_REASON_DOUBLE_IN_PREFIX + ": local index, recrawl rejected. Document date = "
                    + ISO8601Formatter.FORMATTER.format(new Date(oldDate)) + " is not older than crawl profile recrawl minimum date = "
                    + ISO8601Formatter.FORMATTER.format(new Date(profile.recrawlIfOlder()));
        }

        return null;
    }

    /**
     * Test if an url shall be accepted using attributes that are defined by a crawl start but can be changed during a crawl.
     * @param url
     * @param profile
     * @param depth
     * @return null if the url is accepted, an error string in case if the url is not accepted with an error description
     */
    public String checkAcceptanceChangeable(final DigestURL url, final CrawlProfile profile, final int depth) {

        // check if the protocol is supported
        final String urlProtocol = url.getProtocol();
        final String urlstring = url.toNormalform(true);
        if (!Switchboard.getSwitchboard().loader.isSupportedProtocol(urlProtocol)) {
            CrawlStacker.log.severe("Unsupported protocol in URL '" + urlstring + "'.");
            return "unsupported protocol";
        }

        // check if ip is local ip address
        final String urlRejectReason = this.urlInAcceptedDomain(url);
        if (urlRejectReason != null) {
            if (CrawlStacker.log.isFine()) CrawlStacker.log.fine("URL not in accepted Domain (" + urlRejectReason + ")");
            return "denied_(" + urlRejectReason + ")";
        }

        // check blacklist
        if (Switchboard.urlBlacklist.isListed(BlacklistType.CRAWLER, url)) {
            CrawlStacker.log.fine("URL '" + urlstring + "' is in blacklist.");
            return "url in blacklist";
        }

        // filter with must-match for URLs
        if ((depth > 0) && !profile.urlMustMatchPattern().matcher(urlstring).matches()) {
            final String patternStr = profile.formattedUrlMustMatchPattern();
            if (CrawlStacker.log.isFine()) {
                CrawlStacker.log.fine("URL '" + urlstring + "' does not match must-match crawling filter '" + patternStr + "'.");
            }
            return ERROR_NO_MATCH_MUST_MATCH_FILTER + patternStr;
        }

        // filter with must-not-match for URLs
        if ((depth > 0) && profile.urlMustNotMatchPattern().matcher(urlstring).matches()) {
            if (CrawlStacker.log.isFine()) CrawlStacker.log.fine("URL '" + urlstring + "' matches must-not-match crawling filter '" + profile.urlMustNotMatchPattern().toString() + "'.");
            return ERROR_MATCH_WITH_MUST_NOT_MATCH_FILTER + profile.urlMustNotMatchPattern().toString();
        }

        // deny cgi
        if (url.isIndividual() && !profile.crawlingQ())  { // TODO: make special property for crawlingIndividual
            if (CrawlStacker.log.isFine()) CrawlStacker.log.fine("URL '" + urlstring + "' is CGI URL.");
            return "individual url (sessionid etc) not wanted";
        }

        // deny post properties
        if (url.isPOST() && !profile.crawlingQ())  {
            if (CrawlStacker.log.isFine()) CrawlStacker.log.fine("URL '" + urlstring + "' is post URL.");
            return "post url not allowed";
        }

        // the following filters use a DNS lookup to check if the url matches with IP filter
        // this is expensive and those filters are check at the end of all other tests

        // filter with must-match for IPs
        if ((depth > 0) && profile.ipMustMatchPattern() != CrawlProfile.MATCH_ALL_PATTERN && url.getHost() != null && !profile.ipMustMatchPattern().matcher(url.getInetAddress().getHostAddress()).matches()) {
            if (CrawlStacker.log.isFine()) CrawlStacker.log.fine("IP " + url.getInetAddress().getHostAddress() + " of URL '" + urlstring + "' does not match must-match crawling filter '" + profile.ipMustMatchPattern().toString() + "'.");
            return "ip " + url.getInetAddress().getHostAddress() + " of url does not match must-match filter";
        }

        // filter with must-not-match for IPs
        if ((depth > 0) && profile.ipMustNotMatchPattern() != CrawlProfile.MATCH_NEVER_PATTERN && url.getHost() != null && profile.ipMustNotMatchPattern().matcher(url.getInetAddress().getHostAddress()).matches()) {
            if (CrawlStacker.log.isFine()) CrawlStacker.log.fine("IP " + url.getInetAddress().getHostAddress() + " of URL '" + urlstring + "' matches must-not-match crawling filter '" + profile.ipMustNotMatchPattern().toString() + "'.");
            return "ip " + url.getInetAddress().getHostAddress() + " of url matches must-not-match filter";
        }

        // filter with must-match for IPs
        final String[] countryMatchList = profile.countryMustMatchList();
        if (depth > 0 && countryMatchList != null && countryMatchList.length > 0) {
            final Locale locale = url.getLocale();
            if (locale != null) {
                final String c0 = locale.getCountry();
                boolean granted = false;
                matchloop: for (final String c: countryMatchList) {
                    if (c0.equals(c)) {
                        granted = true;
                        break matchloop;
                    }
                }
                if (!granted) {
                    if (CrawlStacker.log.isFine()) CrawlStacker.log.fine("IP " + url.getInetAddress().getHostAddress() + " of URL '" + urlstring + "' does not match must-match crawling filter '" + profile.ipMustMatchPattern().toString() + "'.");
                    return "country " + c0 + " of url does not match must-match filter for countries";
                }
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
    public String urlInAcceptedDomain(final DigestURL url) {
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
        if (host == null) return "url.host is null (you must switch to intranet mode to crawl these sources)";
        // check if this is a local address and we are allowed to index local pages:
        //boolean local = hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress();
        //assert local == yacyURL.isLocalDomain(url.hash()); // TODO: remove the dnsResolve above!
        final InetAddress ia = Domains.dnsResolve(host);
        return (local) ?
                ("the host '" + host + "' is local, but local addresses are not accepted: " + ((ia == null) ? "DNS lookup resulted in null (unknown host name)" : ia.getHostAddress())) :
                    ("the host '" + host + "' is global, but global addresses are not accepted: " + ((ia == null) ? "null" : ia.getHostAddress()));
    }

    public String urlInAcceptedDomainHash(final byte[] urlhash) {
        // returns true if the url can be accepted according to network.unit.domain
        if (urlhash == null) return "url is null";
        // check if this is a local address and we are allowed to index local pages:
        @SuppressWarnings("deprecation")
        final boolean local = DigestURL.isLocal(urlhash);
        if (this.acceptLocalURLs && local) return null;
        if (this.acceptGlobalURLs && !local) return null;
        return (local) ?
                ("the urlhash '" + ASCII.String(urlhash) + "' is local, but local addresses are not accepted") :
                    ("the urlhash '" + ASCII.String(urlhash) + "' is global, but global addresses are not accepted");
    }

    public boolean acceptLocalURLs() {
        return this.acceptLocalURLs;
    }

    public boolean acceptGlobalURLs() {
        return this.acceptGlobalURLs;
    }
}
