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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

public class CrawlQueues {
    
    private final static Request POISON_REQUEST = new Request();
    private final static ConcurrentLog log = new ConcurrentLog("CRAWLER");

    private final Switchboard sb;
    private final Loader[] worker;
    private final ArrayBlockingQueue<Request> workerQueue;
    private final ArrayList<String> remoteCrawlProviderHashes;

    public  NoticedURL noticeURL;
    public  ErrorCache errorURL;
    public Map<String, DigestURL> delegatedURL;

    public CrawlQueues(final Switchboard sb, final File queuePath) {
        this.sb = sb;
        final int maxWorkers = (int) sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10);
        this.worker = new Loader[maxWorkers];
        this.workerQueue = new ArrayBlockingQueue<Request>(200);
        this.remoteCrawlProviderHashes = new ArrayList<String>();

        // start crawling management
        log.config("Starting Crawling Management");
        this.noticeURL = new NoticedURL(queuePath, sb.useTailCache, sb.exceed134217727);
        this.errorURL = new ErrorCache(sb.index.fulltext());
        this.delegatedURL = new ConcurrentHashMap<String, DigestURL>();
    }
    
    /**
     * Relocation is necessary if the user switches the network.
     * Because this object is part of the scheduler we cannot simply close that object and create a new one.
     * Instead, the 'living' content of this object is destroyed.
     * @param newQueuePath
     */
    public void relocate(final File newQueuePath) {
        // removed pending requests
        this.workerQueue.clear();
        this.errorURL.clearCache();
        this.remoteCrawlProviderHashes.clear();
        this.noticeURL.close();
        this.noticeURL = new NoticedURL(newQueuePath, this.sb.useTailCache, this.sb.exceed134217727);
        this.delegatedURL.clear();
    }

    public synchronized void close() {
        // removed pending requests
        this.workerQueue.clear();
        // wait for all workers to finish
        for (int i = 0; i < this.worker.length; i++) {
            try {this.workerQueue.put(POISON_REQUEST);} catch (InterruptedException e) {}
        }
        for (final Loader w: this.worker) {
            if (w != null && w.isAlive()) {
                try {
                    w.join(1000);
                    if (w.isAlive()) w.interrupt();
                } catch (final InterruptedException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }
        this.noticeURL.close();
        this.delegatedURL.clear();
    }

    public void clear() {
        // wait for all workers to finish
        this.workerQueue.clear();
        for (final Loader w: this.worker) if (w != null) w.interrupt();
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
        //} // this is disabled because it prevents proper crawling of smb shares. The cause is unknown
        for (final Request request: activeWorkerEntries().values()) {
            if (Base64Order.enhancedCoder.equal(request.url().hash(), hash)) {
                return HarvestProcess.WORKER;
            }
        }
        return null;
    }
    
    /**
     * count the number of same host names in the worker
     * @param host
     * @return
     */
    public int hostcount(final String host) {
        if (host == null || host.length() == 0) return 0;
        int c = 0;
        for (final DigestURL url: activeWorkerEntries().keySet()) {
            if (host.equals(url.getHost())) {
                c++;
            }
        }
        return c;
    }

    public void removeURL(final byte[] hash) {
        assert hash != null && hash.length == 12;
        this.noticeURL.removeByURLHash(hash);
        this.delegatedURL.remove(hash);
    }
    
    public int removeHosts(final Set<String> hosthashes) {
        return this.noticeURL.removeByHostHash(hosthashes);
        //this.delegatedURL.remove(hash);
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
        for (final DigestURL url: activeWorkerEntries().keySet()) {
            if (Base64Order.enhancedCoder.equal(url.hash(), urlhash)) {
                return url;
            }
        }
        final Request ne = this.noticeURL.get(urlhash);
        if (ne != null) {
            return ne.url();
        }
        return null;
    }

    public void freemem() {
        if ((this.errorURL.stackSize() > 1)) {
            log.warn("freemem: Cleaning Error-URLs report stack, "
                    + this.errorURL.stackSize()
                    + " entries on stack");
            this.errorURL.clearStack();
        }
    }
    
    public Map<DigestURL, Request> activeWorkerEntries() {
        synchronized (this.worker) {
            Map<DigestURL, Request> map = new HashMap<DigestURL, Request>();
            for (final Loader w: this.worker) {
                if (w != null) {
                    Request r = w.loading();
                    if (r != null) map.put(r.url(), r);
                }
            }
            return map;
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
                if (e.getMessage() != null && e.getMessage().indexOf("hash is null",0) > 0) {
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
                    if (!activeWorkerEntries().containsKey(urlEntry.url())) {
                        try {
                            ensureLoaderRunning();
                            this.workerQueue.put(urlEntry);
                        } catch (InterruptedException e) {
                            ConcurrentLog.logException(e);
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
        if (this.noticeURL.isEmpty(stackType)) {
            //log.logDebug("GlobalCrawl: queue is empty");
            return "stack is empty";
        }

        // check again
        if (this.workerQueue.remainingCapacity() == 0) {
            return "too many workers active: " + this.workerQueue.size();
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

        // check again
        if (this.workerQueue.remainingCapacity() == 0) {
            if (CrawlQueues.log.isFine()) {
                CrawlQueues.log.fine("remoteCrawlLoaderJob: too many processes in loader queue, dismissed (" + "workerQueue=" + this.workerQueue.size() + "), httpClients = " + ConnectionInfo.getCount());
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
                        0
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

    private void ensureLoaderRunning() {
        // check if there is at least one loader available
        for (int i = 0; i < this.worker.length; i++) {
            if (this.worker[i] == null || !this.worker[i].isAlive()) {
                this.worker[i] = new Loader();
                this.worker[i].start();
                return;
            }
            if (this.worker[i].loading() == null) return;
        }
    }
    
    private final class Loader extends Thread {

        private Request request = null;
        private Loader() {
        }
        
        public Request loading() {
            return request;
        }

        @Override
        public void run() {
            this.setPriority(Thread.MIN_PRIORITY); // http requests from the crawler should not cause that other functions work worse
            try {
                while ((request = CrawlQueues.this.workerQueue.poll(10, TimeUnit.SECONDS)) != POISON_REQUEST) {
                    if (request == null) break; // we run this only for a specific time and then let the process die to clear up resources
                    request.setStatus("worker-initialized", WorkflowJob.STATUS_INITIATED);
                    this.setName("CrawlQueues.Loader(" + request.url() + ")");
                    CrawlProfile profile = CrawlQueues.this.sb.crawler.get(UTF8.getBytes(request.profileHandle()));
                    try {
                        // checking robots.txt for http(s) resources
                        request.setStatus("worker-checkingrobots", WorkflowJob.STATUS_STARTED);
                        RobotsTxtEntry robotsEntry;
                        if ((request.url().getProtocol().equals("http") || request.url().getProtocol().equals("https")) &&
                            (robotsEntry = CrawlQueues.this.sb.robots.getEntry(request.url(), profile.getAgent())) != null &&
                            robotsEntry.isDisallowed(request.url())) {
                            //if (log.isFine()) log.logFine("Crawling of URL '" + request.url().toString() + "' disallowed by robots.txt.");
                            CrawlQueues.this.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_ROBOTS_RULE, "denied by robots.txt", -1);
                            request.setStatus("worker-disallowed", WorkflowJob.STATUS_FINISHED);
                        } else {
                            // starting a load from the internet
                            request.setStatus("worker-loading", WorkflowJob.STATUS_RUNNING);
                            String error = null;
   
                            // load a resource and push queue entry to switchboard queue
                            // returns null if everything went fine, a fail reason string if a problem occurred
                            try {
                                request.setStatus("loading", WorkflowJob.STATUS_RUNNING);
                                final Response response = CrawlQueues.this.sb.loader.load(request, profile == null ? CacheStrategy.IFEXIST : profile.cacheStrategy(), BlacklistType.CRAWLER, profile.getAgent());
                                if (response == null) {
                                    request.setStatus("error", WorkflowJob.STATUS_FINISHED);
                                    if (CrawlQueues.log.isFine()) {
                                        CrawlQueues.log.fine("problem loading " + request.url().toString() + ": no content (possibly caused by cache policy)");
                                    }
                                    error = "no content (possibly caused by cache policy)";
                                } else {
                                    request.setStatus("loaded", WorkflowJob.STATUS_RUNNING);
                                    final String storedFailMessage = CrawlQueues.this.sb.toIndexer(response);
                                    request.setStatus("enqueued-" + ((storedFailMessage == null) ? "ok" : "fail"), WorkflowJob.STATUS_FINISHED);
                                    error = (storedFailMessage == null) ? null : "not enqueued to indexer: " + storedFailMessage;
                                }
                            } catch (final IOException e) {
                                request.setStatus("error", WorkflowJob.STATUS_FINISHED);
                                if (CrawlQueues.log.isFine()) {
                                    CrawlQueues.log.fine("problem loading " + request.url().toString() + ": " + e.getMessage());
                                }
                                error = "load error - " + e.getMessage();
                            }
   
                            if (error != null) {
                                if (error.endsWith("$")) {
                                    // the "$" mark at the end of the error message means, that the error was already pushed to the error-db by the reporting method
                                    // thus we only push this message if we don't have that mark
                                    error = error.substring(0, error.length() - 1).trim();
                                } else {
                                    CrawlQueues.this.errorURL.push(request.url(), request.depth(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, "cannot load: " + error, -1);
                                }
                                request.setStatus("worker-error", WorkflowJob.STATUS_FINISHED);
                            } else {
                                request.setStatus("worker-processed", WorkflowJob.STATUS_FINISHED);
                            }
                        }
                    } catch (final Exception e) {
                        CrawlQueues.this.errorURL.push(request.url(), request.depth(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, e.getMessage() + " - in worker", -1);
                        ConcurrentLog.logException(e);
                        request.setStatus("worker-exception", WorkflowJob.STATUS_FINISHED);
                    } finally {
                        request = null;
                        this.setName("CrawlQueues.Loader(WAITING)");
                    }
                    profile = null;
                }
            } catch (InterruptedException e2) {
                ConcurrentLog.logException(e2);
            }
        }
    }
}
