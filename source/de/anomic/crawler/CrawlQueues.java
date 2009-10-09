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

package de.anomic.crawler;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.logging.Log;

import de.anomic.content.RSSMessage;
import de.anomic.crawler.retrieval.Request;
import de.anomic.crawler.retrieval.Response;
import de.anomic.document.parser.xml.RSSFeed;
import de.anomic.http.client.Client;
import de.anomic.kelondro.table.SplitTable;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverProcessorJob;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.dht.PeerSelection;

public class CrawlQueues {

    protected Switchboard sb;
    protected Log log;
    protected Map<Integer, crawlWorker> workers; // mapping from url hash to Worker thread object
    private   final ArrayList<String> remoteCrawlProviderHashes;

    public  NoticedURL noticeURL;
    public  ZURL errorURL, delegatedURL;
    
    public CrawlQueues(final Switchboard sb, final File queuePath) {
        this.sb = sb;
        this.log = new Log("CRAWLER");
        this.workers = new ConcurrentHashMap<Integer, crawlWorker>();
        this.remoteCrawlProviderHashes = new ArrayList<String>();
        
        // start crawling management
        log.logConfig("Starting Crawling Management");
        noticeURL = new NoticedURL(queuePath, sb.useTailCache, sb.exceed134217727);
        //errorURL = new plasmaCrawlZURL(); // fresh error DB each startup; can be hold in RAM and reduces IO;
        final File errorDBFile = new File(queuePath, "urlError2.db");
        if (errorDBFile.exists()) {
            // delete the error db to get a fresh each time on startup
            // this is useful because there is currently no re-use of the data in this table.
            if (errorDBFile.isDirectory()) SplitTable.delete(queuePath, "urlError2.db"); else FileUtils.deletedelete(errorDBFile);
        }
        errorURL = new ZURL(queuePath, "urlError3.db", false, sb.useTailCache, sb.exceed134217727);
        delegatedURL = new ZURL(queuePath, "urlDelegated3.db", true, sb.useTailCache, sb.exceed134217727);
    }
    
    public void relocate(final File newQueuePath) {
        this.close();
        
        this.workers = new ConcurrentHashMap<Integer, crawlWorker>();
        this.remoteCrawlProviderHashes.clear();
        
        noticeURL = new NoticedURL(newQueuePath, sb.useTailCache, sb.exceed134217727);
        final File errorDBFile = new File(newQueuePath, "urlError2.db");
        if (errorDBFile.exists()) {
            if (errorDBFile.isDirectory()) SplitTable.delete(newQueuePath, "urlError2.db"); else FileUtils.deletedelete(errorDBFile);
        }
        errorURL = new ZURL(newQueuePath, "urlError3.db", false, sb.useTailCache, sb.exceed134217727);
        delegatedURL = new ZURL(newQueuePath, "urlDelegated3.db", true, sb.useTailCache, sb.exceed134217727);
    }
    
    public void close() {
        // wait for all workers to finish
        for (final crawlWorker w: workers.values()) {
            w.interrupt();
        }
        for (final crawlWorker w: workers.values()) {
            try {
                w.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        noticeURL.close();
        errorURL.close();
        delegatedURL.close();
    }
    
    public void clear() {
        // wait for all workers to finish
        for (final crawlWorker w: workers.values()) {
            w.interrupt();
        }
        // TODO: wait some more time until all threads are finished
        workers.clear();
        remoteCrawlProviderHashes.clear();
        noticeURL.clear();
        try {
            errorURL.clear();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        try {
            delegatedURL.clear();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * tests if hash occurrs in any database
     * @param hash
     * @return if the hash exists, the name of the database is returned, otherwise null is returned
     */
    public String urlExists(final String hash) {
        if (delegatedURL.exists(hash)) return "delegated";
        if (errorURL.exists(hash)) return "errors";
        for (final crawlWorker worker: workers.values()) {
            if (worker.request.url().hash().equals(hash)) return "worker";
        }
        if (noticeURL.existsInStack(hash)) return "crawler";
        return null;
    }
    
    public void urlRemove(final String hash) {
        noticeURL.removeByURLHash(hash);
        delegatedURL.remove(hash);
        errorURL.remove(hash);
    }
    
    public yacyURL getURL(final String urlhash) {
        assert urlhash != null;
        if (urlhash == null || urlhash.length() == 0) return null;
        ZURL.Entry ee = delegatedURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        ee = errorURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        for (final crawlWorker w: workers.values()) {
            if (w.request.url().hash().equals(urlhash)) return w.request.url();
        }
        final Request ne = noticeURL.get(urlhash);
        if (ne != null) return ne.url();
        return null;
    }
    
    public void cleanup() {
        // wait for all workers to finish
        int timeout = (int) sb.getConfigLong("crawler.clientTimeout", 10000);
        for (final crawlWorker w: workers.values()) {
            if (w.age() > timeout) w.interrupt();
        }
    }
    
    public Request[] activeWorkerEntries() {
        synchronized (workers) {
            final Request[] e = new Request[workers.size()];
            int i = 0;
            for (final crawlWorker w: workers.values()) e[i++] = w.request;
            return e;
        }
    }
    
    public int coreCrawlJobSize() {
        return noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE);
    }
    
    public boolean coreCrawlJob() {
        
        final boolean robinsonPrivateCase = ((sb.isRobinsonMode()) && 
                (!sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER)) &&
                (!sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PRIVATE_CLUSTER)));
        
        if (((robinsonPrivateCase) || (coreCrawlJobSize() <= 20)) && (limitCrawlJobSize() > 0)) {
            // move some tasks to the core crawl job so we have something to do
            final int toshift = Math.min(10, limitCrawlJobSize()); // this cannot be a big number because the balancer makes a forced waiting if it cannot balance
            for (int i = 0; i < toshift; i++) {
                noticeURL.shift(NoticedURL.STACK_TYPE_LIMIT, NoticedURL.STACK_TYPE_CORE, sb.crawler.profilesActiveCrawls);
            }
            log.logInfo("shifted " + toshift + " jobs from global crawl to local crawl (coreCrawlJobSize()=" + coreCrawlJobSize() +
                    ", limitCrawlJobSize()=" + limitCrawlJobSize() + ", cluster.mode=" + sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "") +
                    ", robinsonMode=" + ((sb.isRobinsonMode()) ? "on" : "off"));
        }
        
        String queueCheck = crawlIsPossible(NoticedURL.STACK_TYPE_CORE, "Core");
        if (queueCheck != null) {
            log.logInfo("omitting de-queue/local: " + queueCheck);
            return false;
        }
        
        if (isPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) {
            log.logInfo("omitting de-queue/local: paused");
            return false;
        }
        
        // do a local crawl        
        Request urlEntry = null;
        while (urlEntry == null && noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE) > 0) {
            final String stats = "LOCALCRAWL[" + noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT) + ", " + noticeURL.stackSize(NoticedURL.STACK_TYPE_OVERHANG) + ", " + noticeURL.stackSize(NoticedURL.STACK_TYPE_REMOTE) + "]";
            try {
                urlEntry = noticeURL.pop(NoticedURL.STACK_TYPE_CORE, true, sb.crawler.profilesActiveCrawls);
                if (urlEntry == null) continue;
                final String profileHandle = urlEntry.profileHandle();
                // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
                // profileHandle = " + profileHandle + ", urlEntry.url = " + urlEntry.url());
                if (profileHandle == null) {
                    log.logSevere(stats + ": NULL PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                    return true;
                }
                generateCrawl(urlEntry, stats, profileHandle);
                return true;
            } catch (final IOException e) {
                log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
                if (e.getMessage().indexOf("hash is null") > 0) noticeURL.clear(NoticedURL.STACK_TYPE_CORE);
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
    private void generateCrawl(Request urlEntry, final String stats, final String profileHandle) {
        final CrawlProfile.entry profile = sb.crawler.profilesActiveCrawls.getEntry(profileHandle);
        if (profile != null) {

            // check if the protocol is supported
            final yacyURL url = urlEntry.url();
            final String urlProtocol = url.getProtocol();
            if (sb.loader.isSupportedProtocol(urlProtocol)) {

                if (this.log.isFine())
                    log.logFine(stats + ": URL=" + urlEntry.url()
                            + ", initiator=" + urlEntry.initiator()
                            + ", crawlOrder=" + ((profile.remoteIndexing()) ? "true" : "false")
                            + ", depth=" + urlEntry.depth()
                            + ", crawlDepth=" + profile.depth()
                            + ", must-match=" + profile.mustMatchPattern().toString()
                            + ", must-not-match=" + profile.mustNotMatchPattern().toString()
                            + ", permission=" + ((sb.peers == null) ? "undefined" : (((sb.peers.mySeed().isSenior()) || (sb.peers.mySeed().isPrincipal())) ? "true" : "false")));

                // work off one Crawl stack entry
                if ((urlEntry == null) || (urlEntry.url() == null)) {
                    log.logInfo(stats + ": urlEntry = null");
                } else {
                	new crawlWorker(urlEntry);
                }
                
            } else {
                this.log.logSevere("Unsupported protocol in URL '" + url.toString());
            }
        } else {
            log.logWarning(stats + ": LOST PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
        }
    }

    /**
     * if crawling was paused we have to wait until we were notified to continue
     * blocks until pause is ended
     * @param crawljob
     * @return
     */
    private boolean isPaused(String crawljob) {
        final Object[] status = sb.crawlJobsStatus.get(crawljob);
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
     * @param type
     * @return
     */
    private String crawlIsPossible(int stackType, final String type) {
        //System.out.println("stacksize = " + noticeURL.stackSize(stackType));
        if (noticeURL.stackSize(stackType) == 0) {
            //log.logDebug("GlobalCrawl: queue is empty");
            return "stack is empty";
        }
        
        // check the worker threads
        int maxWorkers = (int) sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10);
        if (this.workers.size() >= maxWorkers) {
            // too many worker threads, try a cleanup
            this.cleanup();
        }
        // check again
        if (this.workers.size() >= maxWorkers) {
            return "too many workers active: " + this.workers.size();
        }

        String cautionCause = sb.onlineCaution();
        if (cautionCause != null) {
            return "online caution: " + cautionCause;
        }
        return null;
    }

    public boolean remoteCrawlLoaderJob() {
        // check if we are allowed to crawl urls provided by other peers
        if (!sb.peers.mySeed().getFlagAcceptRemoteCrawl()) {
            //this.log.logInfo("remoteCrawlLoaderJob: not done, we are not allowed to do that");
            return false;
        }
        
        // check if we are a senior peer
        if (!sb.peers.mySeed().isActive()) {
            //this.log.logInfo("remoteCrawlLoaderJob: not done, this should be a senior or principal peer");
            return false;
        }
        
        if (this.size() >= sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10)) {
            // try a cleanup
            cleanup();
        }
        // check again
        if (this.size() >= sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10)) {
            if (this.log.isFine()) log.logFine("remoteCrawlLoaderJob: too many processes in loader queue, dismissed (" + "cacheLoader=" + this.size() + "), httpClients = " + Client.connectionCount());
            return false;
        }
        
        String cautionCause = sb.onlineCaution();
        if (cautionCause != null) {
            if (this.log.isFine()) log.logFine("remoteCrawlLoaderJob: online caution for " + cautionCause + ", omitting processing");
            return false;
        }
        
        if (remoteTriggeredCrawlJobSize() > 100) {
            if (this.log.isFine()) log.logFine("remoteCrawlLoaderJob: the remote-triggered crawl job queue is filled, omitting processing");
            return false;
        }
        
        if (coreCrawlJobSize() > 0 && sb.indexingStorageProcessor.queueSize() > 0) {
            if (this.log.isFine()) log.logFine("remoteCrawlLoaderJob: a local crawl is running, omitting processing");
            return false;
        }
        
        // check if we have an entry in the provider list, otherwise fill the list
        yacySeed seed;
        if (remoteCrawlProviderHashes.size() == 0) {
            if (sb.peers != null && sb.peers.sizeConnected() > 0) {
                final Iterator<yacySeed> e = PeerSelection.getProvidesRemoteCrawlURLs(sb.peers);
                while (e.hasNext()) {
                    seed = e.next();
                    if (seed != null) {
                        remoteCrawlProviderHashes.add(seed.hash);
                    }
                }
            }
        }
        if (remoteCrawlProviderHashes.size() == 0) return false;
        
        // take one entry from the provider list and load the entries from the remote peer
        seed = null;
        String hash = null;
        while ((seed == null) && (remoteCrawlProviderHashes.size() > 0)) {
            hash = remoteCrawlProviderHashes.remove(remoteCrawlProviderHashes.size() - 1);
            if (hash == null) continue;
            seed = sb.peers.get(hash);
            if (seed == null) continue;
            // check if the peer is inside our cluster
            if ((sb.isRobinsonMode()) && (!sb.isInMyCluster(seed))) {
                seed = null;
                continue;
            }
        }
        if (seed == null) return false;
        
        // we know a peer which should provide remote crawl entries. load them now.
        final RSSFeed feed = yacyClient.queryRemoteCrawlURLs(sb.peers, seed, 30, 60000);
        if (feed == null || feed.size() == 0) {
            // something is wrong with this provider. To prevent that we get not stuck with this peer
            // we remove it from the peer list
            sb.peers.peerActions.peerDeparture(seed, "no results from provided remote crawls");
            // ask another peer
            return remoteCrawlLoaderJob();
        }
        
        // parse the rss
        yacyURL url, referrer;
        Date loaddate;
        for (final RSSMessage item: feed) {
            //System.out.println("URL=" + item.getLink() + ", desc=" + item.getDescription() + ", pubDate=" + item.getPubDate());
            
            // put url on remote crawl stack
            try {
                url = new yacyURL(item.getLink(), null);
            } catch (final MalformedURLException e) {
                url = null;
            }
            try {
                referrer = new yacyURL(item.getReferrer(), null);
            } catch (final MalformedURLException e) {
                referrer = null;
            }
            try {
                loaddate = DateFormatter.parseShortSecond(item.getPubDate());
            } catch (final ParseException e) {
                loaddate = new Date();
            }
            final String urlRejectReason = sb.crawlStacker.urlInAcceptedDomain(url);
            if (urlRejectReason == null) {
                // stack url
                if (sb.getLog().isFinest()) sb.getLog().logFinest("crawlOrder: stack: url='" + url + "'");
                sb.crawlStacker.enqueueEntry(new Request(
                        hash,
                        url,
                        (referrer == null) ? null : referrer.hash(),
                        item.getDescription(),
                        null,
                        loaddate,
                        sb.crawler.defaultRemoteProfile.handle(),
                        0,
                        0,
                        0
                ));
            } else {
                log.logWarning("crawlOrder: Rejected URL '" + urlToString(url) + "': " + urlRejectReason);
            }
        }
        return true;
    }

    /**
     * @param url
     * @return
     */
    private String urlToString(final yacyURL url) {
        return (url == null ? "null" : url.toNormalform(true, false));
    }
    
    public int limitCrawlJobSize() {
        return noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT);
    }
    
    public int remoteTriggeredCrawlJobSize() {
        return noticeURL.stackSize(NoticedURL.STACK_TYPE_REMOTE);
    }
    
    public boolean remoteTriggeredCrawlJob() {
        // work off crawl requests that had been placed by other peers to our crawl stack
        
        // do nothing if either there are private processes to be done
        // or there is no global crawl on the stack
        String queueCheck = crawlIsPossible(NoticedURL.STACK_TYPE_REMOTE, "Global");
        if (queueCheck != null) {
            if (log.isFinest()) log.logFinest("omitting de-queue/remote: " + queueCheck);
            return false;
        }

        if (isPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL)) {
            if (log.isFinest()) log.logFinest("omitting de-queue/remote: paused");
            return false;
        }
        
        // we don't want to crawl a global URL globally, since WE are the global part. (from this point of view)
        final String stats = "REMOTETRIGGEREDCRAWL[" + noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT) + ", " + noticeURL.stackSize(NoticedURL.STACK_TYPE_OVERHANG) + ", "
                        + noticeURL.stackSize(NoticedURL.STACK_TYPE_REMOTE) + "]";
        try {
            final Request urlEntry = noticeURL.pop(NoticedURL.STACK_TYPE_REMOTE, true, sb.crawler.profilesActiveCrawls);
            final String profileHandle = urlEntry.profileHandle();
            // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
            // profileHandle = " + profileHandle + ", urlEntry.url = " +
            // urlEntry.url());
            generateCrawl(urlEntry, stats, profileHandle);
            return true;
        } catch (final IOException e) {
            log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
            if (e.getMessage().indexOf("hash is null") > 0) noticeURL.clear(NoticedURL.STACK_TYPE_REMOTE);
            return true;
        }
    }
    
    public int size() {
        return workers.size();
    }
    
    protected final class crawlWorker extends Thread {
        
        protected Request request;
        private final Integer code;
        private long start;
        
        public crawlWorker(final Request entry) {
            this.start = System.currentTimeMillis();
            this.request = entry;
            this.request.setStatus("worker-initialized", serverProcessorJob.STATUS_INITIATED);
            this.code = Integer.valueOf(entry.hashCode());
            if (!workers.containsKey(code)) {
                workers.put(code, this);
                this.start();
            }
        }
        
        public long age() {
            return System.currentTimeMillis() - start;
        }
        
        public void run() {
            try {
                // checking robots.txt for http(s) resources
                this.request.setStatus("worker-checkingrobots", serverProcessorJob.STATUS_STARTED);
                if ((request.url().getProtocol().equals("http") || request.url().getProtocol().equals("https")) && sb.robots.isDisallowed(request.url())) {
                    if (log.isFine()) log.logFine("Crawling of URL '" + request.url().toString() + "' disallowed by robots.txt.");
                    final ZURL.Entry eentry = errorURL.newEntry(
                            this.request,
                            sb.peers.mySeed().hash,
                            new Date(),
                            1,
                            "denied by robots.txt");
                    eentry.store();
                    errorURL.push(eentry);
                    this.request.setStatus("worker-disallowed", serverProcessorJob.STATUS_FINISHED);
                } else {
                    // starting a load from the internet
                    this.request.setStatus("worker-loading", serverProcessorJob.STATUS_RUNNING);
                    String result = null;
                    
                    // load a resource and push queue entry to switchboard queue
                    // returns null if everything went fine, a fail reason string if a problem occurred
                    try {
                        request.setStatus("loading", serverProcessorJob.STATUS_RUNNING);
                        Response response = sb.loader.load(request, true);
                        if (response == null) {
                            request.setStatus("error", serverProcessorJob.STATUS_FINISHED);
                            if (log.isFine()) log.logFine("problem loading " + request.url().toString() + ": no content (possibly caused by cache policy)");
                            result = "no content (possibly caused by cache policy)";
                        } else {
                            request.setStatus("loaded", serverProcessorJob.STATUS_RUNNING);
                            final String storedFailMessage = sb.toIndexer(response);
                            request.setStatus("enqueued-" + ((storedFailMessage == null) ? "ok" : "fail"), serverProcessorJob.STATUS_FINISHED);
                            result = (storedFailMessage == null) ? null : "not enqueued to indexer: " + storedFailMessage;
                        }
                    } catch (IOException e) {
                        request.setStatus("error", serverProcessorJob.STATUS_FINISHED);
                        if (log.isFine()) log.logFine("problem loading " + request.url().toString() + ": " + e.getMessage());
                        result = "load error - " + e.getMessage();
                    }
                    
                    if (result != null) {
                        final ZURL.Entry eentry = errorURL.newEntry(
                                this.request,
                                sb.peers.mySeed().hash,
                                new Date(),
                                1,
                                "cannot load: " + result);
                        eentry.store();
                        errorURL.push(eentry);
                        this.request.setStatus("worker-error", serverProcessorJob.STATUS_FINISHED);
                    } else {
                        this.request.setStatus("worker-processed", serverProcessorJob.STATUS_FINISHED);
                    }
                }
            } catch (final Exception e) {
                final ZURL.Entry eentry = errorURL.newEntry(
                        this.request,
                        sb.peers.mySeed().hash,
                        new Date(),
                        1,
                        e.getMessage() + " - in worker");
                eentry.store();
                errorURL.push(eentry);
                e.printStackTrace();
                Client.initConnectionManager();
                this.request.setStatus("worker-exception", serverProcessorJob.STATUS_FINISHED);
            } finally {
                crawlWorker w = workers.remove(code);
                assert w != null;
            }
        }
        
    }
    
}
