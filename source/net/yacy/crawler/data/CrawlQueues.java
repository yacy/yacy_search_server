// CrawlQueues.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 29.10.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package net.yacy.crawler.data;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.Hit;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.HarvestProcess;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.crawler.robots.RobotsTxtEntry;
import net.yacy.kelondro.workflow.WorkflowJob;
import net.yacy.peers.DHTSelection;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.IndexingQueueEntry;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.ErrorCache;
import net.yacy.search.schema.CollectionConfiguration;

public class CrawlQueues {
    
    private final static ConcurrentLog log = new ConcurrentLog("CRAWLER");

    private Switchboard sb;
    private Map<Integer, Loader> workers; // mapping from url hash to Worker thread object
    private final ArrayList<String> remoteCrawlProviderHashes;

    public  NoticedURL noticeURL;
    public  ErrorCache errorURL;
    public Map<String, DigestURL> delegatedURL;

    public CrawlQueues(final Switchboard sb, final File queuePath) {
        this.sb = sb;
        this.workers = new ConcurrentHashMap<Integer, Loader>();
        this.remoteCrawlProviderHashes = new ArrayList<String>();

        // start crawling management
        log.config("Starting Crawling Management");
        this.noticeURL = new NoticedURL(queuePath, sb.useTailCache, sb.exceed134217727);
        this.errorURL = new ErrorCache(sb.index.fulltext());
        this.delegatedURL = new ConcurrentHashMap<String, DigestURL>();
    }

    public void relocate(final File newQueuePath) {
        close();

        this.workers = new ConcurrentHashMap<Integer, Loader>();
        this.remoteCrawlProviderHashes.clear();

        this.noticeURL = new NoticedURL(newQueuePath, this.sb.useTailCache, this.sb.exceed134217727);
        this.errorURL = new ErrorCache(this.sb.index.fulltext());
        this.delegatedURL = new ConcurrentHashMap<String, DigestURL>();
    }

    public synchronized void close() {
        // wait for all workers to finish
        for (final Loader w: this.workers.values()) {
            w.interrupt();
        }
        for (final Loader w: this.workers.values()) {
            try {
                w.join();
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
        }
        this.noticeURL.close();
        this.delegatedURL.clear();
    }

    public void clear() {
        // wait for all workers to finish
        for (final Loader w: this.workers.values()) w.interrupt();
        for (final Loader w: this.workers.values()) try {w.join(10);} catch (final InterruptedException e1) {}
        this.workers.clear();
        this.remoteCrawlProviderHashes.clear();
        this.noticeURL.clear();
        this.delegatedURL.clear();
    }

    /**
     * tests if hash occurs in any database
     * @param hash
     * @return if the hash exists, the name of the database is returned, otherwise null is returned
     */
    public HarvestProcess exists(final byte[] hash) {
        if (this.delegatedURL.containsKey(ASCII.String(hash))) {
            return HarvestProcess.DELEGATED;
        }
        if (this.errorURL.exists(hash)) {
            return HarvestProcess.ERRORS;
        }
        //if (this.noticeURL.existsInStack(hash)) {
        //    return HarvestProcess.CRAWLER;
        //} // this is disabled because it prevents propert crawling of smb shares. The cause is unknown
        for (final Loader worker: this.workers.values()) {
            if (Base64Order.enhancedCoder.equal(worker.request.url().hash(), hash)) {
                return HarvestProcess.WORKER;
            }
        }
        return null;
    }

    public void removeURL(final byte[] hash) {
        assert hash != null && hash.length == 12;
        this.noticeURL.removeByURLHash(hash);
        this.delegatedURL.remove(hash);
    }

    public DigestURL getURL(final byte[] urlhash) {
        assert urlhash != null;
        if (urlhash == null || urlhash.length == 0) {
            return null;
        }
        DigestURL u = this.delegatedURL.get(ASCII.String(urlhash));
        if (u != null) {
            return u;
        }
        CollectionConfiguration.FailDoc ee = this.errorURL.get(ASCII.String(urlhash));
        if (ee != null) {
            return ee.getDigestURL();
        }
        for (final Loader w: this.workers.values()) {
            if (Base64Order.enhancedCoder.equal(w.request.url().hash(), urlhash)) {
                return w.request.url();
            }
        }
        final Request ne = this.noticeURL.get(urlhash);
        if (ne != null) {
            return ne.url();
        }
        return null;
    }

    private void cleanup() {
        // wait for all workers to finish
        final int timeout = (int) this.sb.getConfigLong("crawler.clientTimeout", 10000);
        for (final Loader w: this.workers.values()) {
            if (w.age() > timeout) {
                w.interrupt();
            }
        }
    }

    public Request[] activeWorkerEntries() {
        synchronized (this.workers) {
            final Request[] e = new Request[this.workers.size()];
            int i = 0;
            for (final Loader w: this.workers.values()) {
                if (i >= e.length) {
                    break;
                }
                e[i++] = w.request;
            }
            return e;
        }
    }

    public int coreCrawlJobSize() {
        return this.noticeURL.stackSize(NoticedURL.StackType.LOCAL) + this.noticeURL.stackSize(NoticedURL.StackType.NOLOAD);
    }

    public boolean coreCrawlJob() {
        final boolean robinsonPrivateCase = (this.sb.isRobinsonMode() &&
                !this.sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER));

        if ((robinsonPrivateCase || coreCrawlJobSize() <= 20) && limitCrawlJobSize() > 0) {
            // move some tasks to the core crawl job so we have something to do
            final int toshift = Math.min(10, limitCrawlJobSize()); // this cannot be a big number because the balancer makes a forced waiting if it cannot balance
            for (int i = 0; i < toshift; i++) {
                this.noticeURL.shift(NoticedURL.StackType.GLOBAL, NoticedURL.StackType.LOCAL, this.sb.crawler, this.sb.robots);
            }
            CrawlQueues.log.info("shifted " + toshift + " jobs from global crawl to local crawl (coreCrawlJobSize()=" + coreCrawlJobSize() +
                    ", limitCrawlJobSize()=" + limitCrawlJobSize() + ", cluster.mode=" + this.sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "") +
                    ", robinsonMode=" + ((this.sb.isRobinsonMode()) ? "on" : "off"));
        }

        final String queueCheckCore = loadIsPossible(NoticedURL.StackType.LOCAL);
        final String queueCheckNoload = loadIsPossible(NoticedURL.StackType.NOLOAD);
        if (queueCheckCore != null && queueCheckNoload != null) {
            if (CrawlQueues.log.isFine()) {
                CrawlQueues.log.fine("omitting de-queue/local: " + queueCheckCore + ":" + queueCheckNoload);
            }
            return false;
        }

        if (isPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) {
            if (CrawlQueues.log.isFine()) {
                CrawlQueues.log.fine("omitting de-queue/local: paused");
            }
            return false;
        }

        // do a local crawl
        Request urlEntry;
        while (this.noticeURL.stackSize(NoticedURL.StackType.LOCAL) > 0 || this.noticeURL.stackSize(NoticedURL.StackType.NOLOAD) > 0) {
            final String stats = "LOCALCRAWL[" +
                this.noticeURL.stackSize(NoticedURL.StackType.NOLOAD) + ", " +
                this.noticeURL.stackSize(NoticedURL.StackType.LOCAL) + ", " +
                this.noticeURL.stackSize(NoticedURL.StackType.GLOBAL) + 
                ", " + this.noticeURL.stackSize(NoticedURL.StackType.REMOTE) + "]";
            try {
                if (this.noticeURL.stackSize(NoticedURL.StackType.NOLOAD) > 0) {
                    // get one entry that will not be loaded, just indexed
                    urlEntry = this.noticeURL.pop(NoticedURL.StackType.NOLOAD, true, this.sb.crawler, this.sb.robots);
                    if (urlEntry == null) {
                        continue;
                    }
                    final String profileHandle = urlEntry.profileHandle();
                    if (profileHandle == null) {
                        CrawlQueues.log.severe(stats + ": NULL PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                        return true;
                    }
                    final CrawlProfile profile = this.sb.crawler.get(ASCII.getBytes(profileHandle));
                    if (profile == null) {
                        CrawlQueues.log.severe(stats + ": NULL PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                        return true;
                    }
                    this.sb.indexingDocumentProcessor.enQueue(new IndexingQueueEntry(new Response(urlEntry, profile), null, null));
                    ConcurrentLog.info("CrawlQueues", "placed NOLOAD URL on indexing queue: " + urlEntry.url().toNormalform(true));
                    return true;
                }

                urlEntry = this.noticeURL.pop(NoticedURL.StackType.LOCAL, true, this.sb.crawler, this.sb.robots);
                if (urlEntry == null) {
                    continue;
                }
                // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
                // profileHandle = " + profileHandle + ", urlEntry.url = " + urlEntry.url());
                if (urlEntry.profileHandle() == null) {
                    CrawlQueues.log.severe(stats + ": NULL PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                    return true;
                }
                load(urlEntry, stats);
                return true;
            } catch (final IOException e) {
                CrawlQueues.log.severe(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
                if (e.getMessage().indexOf("hash is null",0) > 0) {
                    this.noticeURL.clear(NoticedURL.StackType.LOCAL);
                }
            }
        }
        return true;
    }

    /**
     * Make some checks if crawl is valid and start it
     *
     * @param urlEntry
     * @param profileHandle
     * @param stats String for log prefixing
     * @return
     */
    private void load(final Request urlEntry, final String stats) {
        final CrawlProfile profile = this.sb.crawler.get(UTF8.getBytes(urlEntry.profileHandle()));
        if (profile != null) {

            // check if the protocol is supported
            final DigestURL url = urlEntry.url();
            final String urlProtocol = url.getProtocol();
            if (this.sb.loader.isSupportedProtocol(urlProtocol)) {
                if (CrawlQueues.log.isFine()) {
                    CrawlQueues.log.fine(stats + ": URL=" + urlEntry.url()
                            + ", initiator=" + ((urlEntry.initiator() == null) ? "" : ASCII.String(urlEntry.initiator()))
                            + ", crawlOrder=" + ((profile.remoteIndexing()) ? "true" : "false")
                            + ", depth=" + urlEntry.depth()
                            + ", crawlDepth=" + profile.depth()
                            + ", must-match=" + profile.urlMustMatchPattern().toString()
                            + ", must-not-match=" + profile.urlMustNotMatchPattern().toString()
                            + ", permission=" + ((this.sb.peers == null) ? "undefined" : (((this.sb.peers.mySeed().isSenior()) || (this.sb.peers.mySeed().isPrincipal())) ? "true" : "false")));
                }

                // work off one Crawl stack entry
                if (urlEntry == null || urlEntry.url() == null) {
                    CrawlQueues.log.info(stats + ": urlEntry = null");
                } else {
                    if (!this.workers.containsKey(Integer.valueOf(urlEntry.hashCode()))) {
                        Loader loader = new Loader(urlEntry);
                        this.workers.put(loader.code, loader);
                        try {
                            loader.start();
                        } catch (final OutOfMemoryError e) {
                            ConcurrentLog.warn("CrawlQueues", "crawlWorker sequential fail-over: " + e.getMessage());
                            loader.run();
                        }
                    }
                }

            } else {
                CrawlQueues.log.severe("Unsupported protocol in URL '" + url.toString());
            }
        } else {
            if (CrawlQueues.log.isFine()) CrawlQueues.log.fine(stats + ": LOST PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
        }
    }

    /**
     * if crawling was paused we have to wait until we were notified to continue
     * blocks until pause is ended
     * @param crawljob
     * @return
     */
    private boolean isPaused(final String crawljob) {
        final Object[] status = this.sb.crawlJobsStatus.get(crawljob);
        boolean pauseEnded = false;
        synchronized(status[SwitchboardConstants.CRAWLJOB_SYNC]) {
            if (((Boolean)status[SwitchboardConstants.CRAWLJOB_STATUS]).booleanValue()) {
                try {
                    status[SwitchboardConstants.CRAWLJOB_SYNC].wait();
                }
                catch (final InterruptedException e) { pauseEnded = true;}
            }
        }
        return pauseEnded;
    }

    /**
     * Checks if crawl queue has elements and new crawl will not exceed thread-limit
     * @param stackType
     * @return
     */
    private String loadIsPossible(final StackType stackType) {
        //System.out.println("stacksize = " + noticeURL.stackSize(stackType));
        if (this.noticeURL.stackSize(stackType) == 0) {
            //log.logDebug("GlobalCrawl: queue is empty");
            return "stack is empty";
        }

        // check the worker threads
        final int maxWorkers = (int) this.sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10);
        if (this.workers.size() >= maxWorkers) {
            // too many worker threads, try a cleanup
            cleanup();
        }
        // check again
        if (this.workers.size() >= maxWorkers) {
            return "too many workers active: " + this.workers.size();
        }

        final String cautionCause = this.sb.onlineCaution();
        if (cautionCause != null) {
            return "online caution: " + cautionCause;
        }
        return null;
    }

    public boolean remoteCrawlLoaderJob() {
        // check if we are allowed to crawl urls provided by other peers
        if (!this.sb.peers.mySeed().getFlagAcceptRemoteCrawl()) {
            //this.log.logInfo("remoteCrawlLoaderJob: not done, we are not allowed to do that");
            return false;
        }

        // check if we are a senior peer
        if (!this.sb.peers.mySeed().isActive()) {
            //this.log.logInfo("remoteCrawlLoaderJob: not done, this should be a senior or principal peer");
            return false;
        }

        if (this.workers.size() >= this.sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 20)) {
            // try a cleanup
            cleanup();
        }
        // check again
        if (this.workers.size() >= this.sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 20)) {
            if (CrawlQueues.log.isFine()) {
                CrawlQueues.log.fine("remoteCrawlLoaderJob: too many processes in loader queue, dismissed (" + "cacheLoader=" + this.workers.size() + "), httpClients = " + ConnectionInfo.getCount());
            }
            return false;
        }

        final String cautionCause = this.sb.onlineCaution();
        if (cautionCause != null) {
            if (CrawlQueues.log.isFine()) {
                CrawlQueues.log.fine("remoteCrawlLoaderJob: online caution for " + cautionCause + ", omitting processing");
            }
            return false;
        }

        if (remoteTriggeredCrawlJobSize() > 200) {
            if (CrawlQueues.log.isFine()) {
                CrawlQueues.log.fine("remoteCrawlLoaderJob: the remote-triggered crawl job queue is filled, omitting processing");
            }
            return false;
        }

        if (coreCrawlJobSize() > 0 /*&& sb.indexingStorageProcessor.queueSize() > 0*/) {
            if (CrawlQueues.log.isFine()) {
                CrawlQueues.log.fine("remoteCrawlLoaderJob: a local crawl is running, omitting processing");
            }
            return false;
        }

        // check if we have an entry in the provider list, otherwise fill the list
        Seed seed;
        if (this.remoteCrawlProviderHashes.isEmpty()) {
            if (this.sb.peers != null && this.sb.peers.sizeConnected() > 0) {
                final Iterator<Seed> e = DHTSelection.getProvidesRemoteCrawlURLs(this.sb.peers);
                while (e.hasNext()) {
                    seed = e.next();
                    if (seed != null) {
                        this.remoteCrawlProviderHashes.add(seed.hash);
                    }
                }
            }
        }
        if (this.remoteCrawlProviderHashes.isEmpty()) {
            return false;
        }

        // take one entry from the provider list and load the entries from the remote peer
        seed = null;
        String hash = null;
        while (seed == null && !this.remoteCrawlProviderHashes.isEmpty()) {
            hash = this.remoteCrawlProviderHashes.remove(this.remoteCrawlProviderHashes.size() - 1);
            if (hash == null) {
                continue;
            }
            seed = this.sb.peers.get(hash);
            if (seed == null) {
                continue;
            }
            // check if the peer is inside our cluster
            if ((this.sb.isRobinsonMode()) && (!this.sb.isInMyCluster(seed))) {
                seed = null;
                continue;
            }
        }
        if (seed == null) {
            return false;
        }

        // we know a peer which should provide remote crawl entries. load them now.
        final RSSFeed feed = Protocol.queryRemoteCrawlURLs(this.sb.peers, seed, 60, 10000);
        if (feed == null || feed.isEmpty()) {
            // something is wrong with this provider. To prevent that we get not stuck with this peer
            // we remove it from the peer list
            this.sb.peers.peerActions.peerDeparture(seed, "no results from provided remote crawls");
            // try again and ask another peer
            return remoteCrawlLoaderJob();
        }

        // parse the rss
        DigestURL url, referrer;
        Date loaddate;
        for (final Hit item: feed) {
            //System.out.println("URL=" + item.getLink() + ", desc=" + item.getDescription() + ", pubDate=" + item.getPubDate());

            // put url on remote crawl stack
            try {
                url = new DigestURL(item.getLink());
            } catch (final MalformedURLException e) {
                continue;
            }
            try {
                referrer = new DigestURL(item.getReferrer());
            } catch (final MalformedURLException e) {
                referrer = null;
            }
            loaddate = item.getPubDate();
            final String urlRejectReason = this.sb.crawlStacker.urlInAcceptedDomain(url);
            if (urlRejectReason == null) {
                // stack url
                if (this.sb.getLog().isFinest()) {
                    this.sb.getLog().finest("crawlOrder: stack: url='" + url + "'");
                }
                this.sb.crawlStacker.enqueueEntry(new Request(
                        ASCII.getBytes(hash),
                        url,
                        (referrer == null) ? null : referrer.hash(),
                        item.getDescriptions().size() > 0 ? item.getDescriptions().get(0) : "",
                        loaddate,
                        this.sb.crawler.defaultRemoteProfile.handle(),
                        0,
                        0,
                        0,
                        item.getSize()
                ));
            } else {
                CrawlQueues.log.warn("crawlOrder: Rejected URL '" + urlToString(url) + "': " + urlRejectReason);
            }
        }
        return true;
    }

    /**
     * @param url
     * @return
     */
    private static String urlToString(final DigestURL url) {
        return (url == null ? "null" : url.toNormalform(true));
    }

    public int limitCrawlJobSize() {
        return this.noticeURL.stackSize(NoticedURL.StackType.GLOBAL);
    }

    public int noloadCrawlJobSize() {
        return this.noticeURL.stackSize(NoticedURL.StackType.NOLOAD);
    }

    public int remoteTriggeredCrawlJobSize() {
        return this.noticeURL.stackSize(NoticedURL.StackType.REMOTE);
    }

    public boolean remoteTriggeredCrawlJob() {
        // work off crawl requests that had been placed by other peers to our crawl stack

        // do nothing if either there are private processes to be done
        // or there is no global crawl on the stack
        final String queueCheck = loadIsPossible(NoticedURL.StackType.REMOTE);
        if (queueCheck != null) {
            if (CrawlQueues.log.isFinest()) {
                CrawlQueues.log.finest("omitting de-queue/remote: " + queueCheck);
            }
            return false;
        }

        if (isPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL)) {
            if (CrawlQueues.log.isFinest()) {
                CrawlQueues.log.finest("omitting de-queue/remote: paused");
            }
            return false;
        }

        // we don't want to crawl a global URL globally, since WE are the global part. (from this point of view)
        final String stats = "REMOTETRIGGEREDCRAWL[" + this.noticeURL.stackSize(NoticedURL.StackType.LOCAL) + ", " + this.noticeURL.stackSize(NoticedURL.StackType.GLOBAL) + ", "
                        + this.noticeURL.stackSize(NoticedURL.StackType.REMOTE) + "]";
        try {
            final Request urlEntry = this.noticeURL.pop(NoticedURL.StackType.REMOTE, true, this.sb.crawler, this.sb.robots);
            if (urlEntry == null) return false;
            load(urlEntry, stats);
            return true;
        } catch (final IOException e) {
            CrawlQueues.log.severe(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
            if (e.getMessage().indexOf("hash is null",0) > 0) {
                this.noticeURL.clear(NoticedURL.StackType.REMOTE);
            }
            return true;
        }
    }

    public int workerSize() {
        return this.workers.size();
    }

    private final class Loader extends Thread {

        private Request request;
        private final Integer code;
        private final long start;
        private final CrawlProfile profile;

        private Loader(final Request entry) {
            this.start = System.currentTimeMillis();
            this.request = entry;
            this.request.setStatus("worker-initialized", WorkflowJob.STATUS_INITIATED);
            this.code = Integer.valueOf(entry.hashCode());
            this.setPriority(Thread.MIN_PRIORITY); // http requests from the crawler should not cause that other functions work worse
            this.profile = CrawlQueues.this.sb.crawler.get(UTF8.getBytes(this.request.profileHandle()));
        }

        private long age() {
            return System.currentTimeMillis() - this.start;
        }

        @Override
        public void run() {
            try {
                // checking robots.txt for http(s) resources
                this.request.setStatus("worker-checkingrobots", WorkflowJob.STATUS_STARTED);
                RobotsTxtEntry robotsEntry;
                if ((this.request.url().getProtocol().equals("http") || this.request.url().getProtocol().equals("https")) &&
                    (robotsEntry = CrawlQueues.this.sb.robots.getEntry(this.request.url(), this.profile.getAgent())) != null &&
                    robotsEntry.isDisallowed(this.request.url())) {
                    //if (log.isFine()) log.logFine("Crawling of URL '" + request.url().toString() + "' disallowed by robots.txt.");
                    CrawlQueues.this.errorURL.push(this.request.url(), profile, FailCategory.FINAL_ROBOTS_RULE, "denied by robots.txt", -1);
                    this.request.setStatus("worker-disallowed", WorkflowJob.STATUS_FINISHED);
                } else {
                    // starting a load from the internet
                    this.request.setStatus("worker-loading", WorkflowJob.STATUS_RUNNING);
                    String result = null;

                    // load a resource and push queue entry to switchboard queue
                    // returns null if everything went fine, a fail reason string if a problem occurred
                    try {
                        this.request.setStatus("loading", WorkflowJob.STATUS_RUNNING);
                        final Response response = CrawlQueues.this.sb.loader.load(this.request, profile == null ? CacheStrategy.IFEXIST : profile.cacheStrategy(), BlacklistType.CRAWLER, this.profile.getAgent());
                        if (response == null) {
                            this.request.setStatus("error", WorkflowJob.STATUS_FINISHED);
                            if (CrawlQueues.log.isFine()) {
                                CrawlQueues.log.fine("problem loading " + this.request.url().toString() + ": no content (possibly caused by cache policy)");
                            }
                            result = "no content (possibly caused by cache policy)";
                        } else {
                            this.request.setStatus("loaded", WorkflowJob.STATUS_RUNNING);
                            final String storedFailMessage = CrawlQueues.this.sb.toIndexer(response);
                            this.request.setStatus("enqueued-" + ((storedFailMessage == null) ? "ok" : "fail"), WorkflowJob.STATUS_FINISHED);
                            result = (storedFailMessage == null) ? null : "not enqueued to indexer: " + storedFailMessage;
                        }
                    } catch (final IOException e) {
                        this.request.setStatus("error", WorkflowJob.STATUS_FINISHED);
                        if (CrawlQueues.log.isFine()) {
                            CrawlQueues.log.fine("problem loading " + this.request.url().toString() + ": " + e.getMessage());
                        }
                        result = "load error - " + e.getMessage();
                    }

                    if (result != null) {
                        CrawlQueues.this.errorURL.push(this.request.url(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, "cannot load: " + result, -1);
                        this.request.setStatus("worker-error", WorkflowJob.STATUS_FINISHED);
                    } else {
                        this.request.setStatus("worker-processed", WorkflowJob.STATUS_FINISHED);
                    }
                }
            } catch (final Exception e) {
                CrawlQueues.this.errorURL.push(this.request.url(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, e.getMessage() + " - in worker", -1);
                ConcurrentLog.logException(e);
                this.request.setStatus("worker-exception", WorkflowJob.STATUS_FINISHED);
            } finally {
                CrawlQueues.this.workers.remove(this.code);
            }
        }
    }
}
