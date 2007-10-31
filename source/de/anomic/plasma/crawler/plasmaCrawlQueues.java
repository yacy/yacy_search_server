// plasmaCrawlQueues.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 29.10.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.plasma.crawler;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.data.robotsParser;
import de.anomic.index.indexURLEntry;
import de.anomic.plasma.plasmaCrawlEntry;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaCrawlZURL;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.crypt;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class plasmaCrawlQueues {

    private plasmaSwitchboard sb;
    private serverLog log;
    private HashMap workers; // mapping from url hash to Worker thread object
    private plasmaProtocolLoader loader;

    public  plasmaCrawlNURL             noticeURL;
    public  plasmaCrawlZURL             errorURL, delegatedURL;
    
    public plasmaCrawlQueues(plasmaSwitchboard sb, File plasmaPath) {
        this.sb = sb;
        this.log = new serverLog("CRAWLER");
        this.workers = new HashMap();
        this.loader = new plasmaProtocolLoader(sb, log);
        
        // start crawling management
        log.logConfig("Starting Crawling Management");
        noticeURL = new plasmaCrawlNURL(plasmaPath);
        //errorURL = new plasmaCrawlZURL(); // fresh error DB each startup; can be hold in RAM and reduces IO;
        errorURL = new plasmaCrawlZURL(plasmaPath, "urlError1.db", true);
        delegatedURL = new plasmaCrawlZURL(plasmaPath, "urlDelegated1.db", false);
        
    }

    public String urlExists(String hash) {
        // tests if hash occurrs in any database
        // if it exists, the name of the database is returned,
        // if it not exists, null is returned
        if (noticeURL.existsInStack(hash)) return "crawler";
        if (delegatedURL.exists(hash)) return "delegated";
        if (errorURL.exists(hash)) return "errors";
        if (workers.containsKey(new Integer(hash.hashCode()))) return "workers";
        return null;
    }
    
    public void urlRemove(String hash) {
        noticeURL.removeByURLHash(hash);
        delegatedURL.remove(hash);
        errorURL.remove(hash);
    }
    
    public yacyURL getURL(String urlhash) {
        if (urlhash.equals(yacyURL.dummyHash)) return null;
        plasmaCrawlEntry ne = (plasmaCrawlEntry) workers.get(new Integer(urlhash.hashCode()));
        if (ne != null) return ne.url();
        ne = noticeURL.get(urlhash);
        if (ne != null) return ne.url();
        plasmaCrawlZURL.Entry ee = delegatedURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        ee = errorURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        return null;
    }
    
    public void close() {
        // wait for all workers to finish
        Iterator i = workers.values().iterator();
        while (i.hasNext()) ((Thread) i.next()).interrupt();
        // TODO: wait some more time until all threads are finished
    }
    
    public plasmaCrawlEntry[] activeWorker() {
        synchronized (workers) {
            plasmaCrawlEntry[] w = new plasmaCrawlEntry[workers.size()];
            int i = 0;
            Iterator j = workers.values().iterator();
            while (j.hasNext()) {
                w[i++] = ((crawlWorker) j.next()).entry;
            }
            return w;
        }
    }
    
    public boolean isSupportedProtocol(String protocol) {
        return loader.isSupportedProtocol(protocol);
    }
    
    public int coreCrawlJobSize() {
        return noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE);
    }
    
    public boolean coreCrawlJob() {
        if (noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) == 0) {
            //log.logDebug("CoreCrawl: queue is empty");
            return false;
        }
        if (sb.sbQueue.size() >= (int) sb.getConfigLong(plasmaSwitchboard.INDEXER_SLOTS, 30)) {
            log.logFine("CoreCrawl: too many processes in indexing queue, dismissed (" +
            "sbQueueSize=" + sb.sbQueue.size() + ")");
            return false;
        }
        if (this.size() >= sb.getConfigLong(plasmaSwitchboard.CRAWLER_THREADS_ACTIVE_MAX, 10)) {
            log.logFine("CoreCrawl: too many processes in loader queue, dismissed (" +
            "cacheLoader=" + this.size() + ")");
            return false;
        }
        if (sb.onlineCaution()) {
            log.logFine("CoreCrawl: online caution, omitting processing");
            return false;
        }
        // if the server is busy, we do crawling more slowly
        //if (!(cacheManager.idle())) try {Thread.currentThread().sleep(2000);} catch (InterruptedException e) {}
        
        // if crawling was paused we have to wait until we wer notified to continue
        Object[] status = (Object[]) sb.crawlJobsStatus.get(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
        synchronized(status[plasmaSwitchboard.CRAWLJOB_SYNC]) {
            if (((Boolean)status[plasmaSwitchboard.CRAWLJOB_STATUS]).booleanValue()) {
                try {
                    status[plasmaSwitchboard.CRAWLJOB_SYNC].wait();
                }
                catch (InterruptedException e){ return false;}
            }
        }
        
        // do a local crawl        
        plasmaCrawlEntry urlEntry = null;
        while (urlEntry == null && noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) > 0) {
            String stats = "LOCALCRAWL[" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
            try {
                urlEntry = noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_CORE, true);
                String profileHandle = urlEntry.profileHandle();
                // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
                // profileHandle = " + profileHandle + ", urlEntry.url = " + urlEntry.url());
                if (profileHandle == null) {
                    log.logSevere(stats + ": NULL PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                    return true;
                }
                plasmaCrawlProfile.entry profile = sb.profilesActiveCrawls.getEntry(profileHandle);
                if (profile == null) {
                    log.logWarning(stats + ": LOST PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                    return true;
                }
                
                // check if the protocol is supported
                yacyURL url = urlEntry.url();
                String urlProtocol = url.getProtocol();
                if (!this.sb.crawlQueues.isSupportedProtocol(urlProtocol)) {
                    this.log.logSevere("Unsupported protocol in URL '" + url.toString());
                    return true;            
                }
                
                log.logFine("LOCALCRAWL: URL=" + urlEntry.url() + ", initiator=" + urlEntry.initiator() + ", crawlOrder=" + ((profile.remoteIndexing()) ? "true" : "false") + ", depth=" + urlEntry.depth() + ", crawlDepth=" + profile.generalDepth() + ", filter=" + profile.generalFilter()
                        + ", permission=" + ((yacyCore.seedDB == null) ? "undefined" : (((yacyCore.seedDB.mySeed().isSenior()) || (yacyCore.seedDB.mySeed().isPrincipal())) ? "true" : "false")));
                
                processLocalCrawling(urlEntry, stats);
                return true;
            } catch (IOException e) {
                log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
                if (e.getMessage().indexOf("hash is null") > 0) noticeURL.clear(plasmaCrawlNURL.STACK_TYPE_CORE);
            }
        }
        return true;
    }
    

    public int limitCrawlTriggerJobSize() {
        return noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT);
    }
    
    public boolean limitCrawlTriggerJob() {
        if (noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) == 0) {
            //log.logDebug("LimitCrawl: queue is empty");
            return false;
        }
        boolean robinsonPrivateCase = ((sb.isRobinsonMode()) && 
                (!sb.getConfig(plasmaSwitchboard.CLUSTER_MODE, "").equals(plasmaSwitchboard.CLUSTER_MODE_PUBLIC_CLUSTER)) &&
                (!sb.getConfig(plasmaSwitchboard.CLUSTER_MODE, "").equals(plasmaSwitchboard.CLUSTER_MODE_PRIVATE_CLUSTER)));
        
        if ((robinsonPrivateCase) || ((coreCrawlJobSize() <= 20) && (limitCrawlTriggerJobSize() > 10))) {
            // it is not efficient if the core crawl job is empty and we have too much to do
            // move some tasks to the core crawl job
            int toshift = 10; // this cannot be a big number because the balancer makes a forced waiting if it cannot balance
            if (toshift > limitCrawlTriggerJobSize()) toshift = limitCrawlTriggerJobSize();
            for (int i = 0; i < toshift; i++) {
                noticeURL.shift(plasmaCrawlNURL.STACK_TYPE_LIMIT, plasmaCrawlNURL.STACK_TYPE_CORE);
            }
            log.logInfo("shifted " + toshift + " jobs from global crawl to local crawl (coreCrawlJobSize()=" + coreCrawlJobSize() + ", limitCrawlTriggerJobSize()=" + limitCrawlTriggerJobSize() + ", cluster.mode=" + sb.getConfig(plasmaSwitchboard.CLUSTER_MODE, "") + ", robinsonMode=" + ((sb.isRobinsonMode()) ? "on" : "off"));
            if (robinsonPrivateCase) return false;
        }
        
        // check local indexing queues
        // in case the placing of remote crawl fails, there must be space in the local queue to work off the remote crawl
        if (sb.sbQueue.size() >= (int) sb.getConfigLong(plasmaSwitchboard.INDEXER_SLOTS, 30) * 2) {
            log.logFine("LimitCrawl: too many processes in indexing queue, dismissed (" +
            "sbQueueSize=" + sb.sbQueue.size() + ")");
            return false;
        }
        if (this.size() >= sb.getConfigLong(plasmaSwitchboard.CRAWLER_THREADS_ACTIVE_MAX, 10)) {
            log.logFine("LimitCrawl: too many processes in loader queue, dismissed (" +
            "cacheLoader=" + this.size() + ")");
            return false;
        }
        if (sb.onlineCaution()) {
            log.logFine("LimitCrawl: online caution, omitting processing");
            return false;
        }
        
        // if crawling was paused we have to wait until we were notified to continue
        Object[] status = (Object[]) sb.crawlJobsStatus.get(plasmaSwitchboard.CRAWLJOB_GLOBAL_CRAWL_TRIGGER);
        synchronized(status[plasmaSwitchboard.CRAWLJOB_SYNC]) {
            if (((Boolean)status[plasmaSwitchboard.CRAWLJOB_STATUS]).booleanValue()) {
                try {
                    status[plasmaSwitchboard.CRAWLJOB_SYNC].wait();
                }
                catch (InterruptedException e){ return false;}
            }
        }
        
        // start a global crawl, if possible
        String stats = "REMOTECRAWLTRIGGER[" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", "
                        + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
        try {
            plasmaCrawlEntry urlEntry = noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_LIMIT, true);
            String profileHandle = urlEntry.profileHandle();
            // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
            // profileHandle = " + profileHandle + ", urlEntry.url = " + urlEntry.url());
            plasmaCrawlProfile.entry profile = sb.profilesActiveCrawls.getEntry(profileHandle);
            if (profile == null) {
                log.logWarning(stats + ": LOST PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                return true;
            }
            
            // check if the protocol is supported
            yacyURL url = urlEntry.url();
            String urlProtocol = url.getProtocol();
            if (!this.sb.crawlQueues.isSupportedProtocol(urlProtocol)) {
                this.log.logSevere("Unsupported protocol in URL '" + url.toString());
                return true;            
            }
            
            log.logFine("plasmaSwitchboard.limitCrawlTriggerJob: url=" + urlEntry.url() + ", initiator=" + urlEntry.initiator() + ", crawlOrder=" + ((profile.remoteIndexing()) ? "true" : "false") + ", depth=" + urlEntry.depth() + ", crawlDepth=" + profile.generalDepth() + ", filter="
                            + profile.generalFilter() + ", permission=" + ((yacyCore.seedDB == null) ? "undefined" : (((yacyCore.seedDB.mySeed().isSenior()) || (yacyCore.seedDB.mySeed().isPrincipal())) ? "true" : "false")));

            boolean tryRemote = ((noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) != 0) || (sb.sbQueue.size() != 0)) &&
                                 (profile.remoteIndexing()) &&
                                 (urlEntry.initiator() != null) &&
                                // (!(urlEntry.initiator().equals(indexURL.dummyHash))) &&
                                 ((yacyCore.seedDB.mySeed().isSenior()) || (yacyCore.seedDB.mySeed().isPrincipal()));
            if (tryRemote) {
                // checking robots.txt for http(s) resources
                if ((urlProtocol.equals("http") || urlProtocol.equals("https")) && robotsParser.isDisallowed(url)) {
                    this.log.logFine("Crawling of URL '" + url.toString() + "' disallowed by robots.txt.");
                    return true;            
                }
                boolean success = processRemoteCrawlTrigger(urlEntry);
                if (success) return true;
            }

            processLocalCrawling(urlEntry, stats); // emergency case, work off the crawl locally            
            return true;
        } catch (IOException e) {
            log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
            if (e.getMessage().indexOf("hash is null") > 0) noticeURL.clear(plasmaCrawlNURL.STACK_TYPE_LIMIT);
            return true; // if we return a false here we will block everything
        }
    }
    
    public int remoteTriggeredCrawlJobSize() {
        return noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE);
    }
    
    public boolean remoteTriggeredCrawlJob() {
        // work off crawl requests that had been placed by other peers to our crawl stack
        
        // do nothing if either there are private processes to be done
        // or there is no global crawl on the stack
        if (noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) == 0) {
            //log.logDebug("GlobalCrawl: queue is empty");
            return false;
        }
        if (sb.sbQueue.size() >= (int) sb.getConfigLong(plasmaSwitchboard.INDEXER_SLOTS, 30)) {
            log.logFine("GlobalCrawl: too many processes in indexing queue, dismissed (" +
            "sbQueueSize=" + sb.sbQueue.size() + ")");
            return false;
        }
        if (this.size() >= sb.getConfigLong(plasmaSwitchboard.CRAWLER_THREADS_ACTIVE_MAX, 10)) {
            log.logFine("GlobalCrawl: too many processes in loader queue, dismissed (" +
            "cacheLoader=" + this.size() + ")");
            return false;
        }        
        if (sb.onlineCaution()) {
            log.logFine("GlobalCrawl: online caution, omitting processing");
            return false;
        }
        
        // if crawling was paused we have to wait until we wer notified to continue
        Object[] status = (Object[]) sb.crawlJobsStatus.get(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        synchronized(status[plasmaSwitchboard.CRAWLJOB_SYNC]) {
            if (((Boolean)status[plasmaSwitchboard.CRAWLJOB_STATUS]).booleanValue()) {
                try {
                    status[plasmaSwitchboard.CRAWLJOB_SYNC].wait();
                }
                catch (InterruptedException e){ return false;}
            }
        }
        
        // we don't want to crawl a global URL globally, since WE are the global part. (from this point of view)
        String stats = "REMOTETRIGGEREDCRAWL[" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", "
                        + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
        try {
            plasmaCrawlEntry urlEntry = noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_REMOTE, true);
            String profileHandle = urlEntry.profileHandle();
            // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
            // profileHandle = " + profileHandle + ", urlEntry.url = " +
            // urlEntry.url());
            plasmaCrawlProfile.entry profile = sb.profilesActiveCrawls.getEntry(profileHandle);

            if (profile == null) {
                log.logWarning(stats + ": LOST PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                return false;
            }
            
            // check if the protocol is supported
            yacyURL url = urlEntry.url();
            String urlProtocol = url.getProtocol();
            if (!this.sb.crawlQueues.isSupportedProtocol(urlProtocol)) {
                this.log.logSevere("Unsupported protocol in URL '" + url.toString());
                return true;            
            }
            
            log.logFine("plasmaSwitchboard.remoteTriggeredCrawlJob: url=" + urlEntry.url() + ", initiator=" + urlEntry.initiator() + ", crawlOrder=" + ((profile.remoteIndexing()) ? "true" : "false") + ", depth=" + urlEntry.depth() + ", crawlDepth=" + profile.generalDepth() + ", filter="
                        + profile.generalFilter() + ", permission=" + ((yacyCore.seedDB == null) ? "undefined" : (((yacyCore.seedDB.mySeed().isSenior()) || (yacyCore.seedDB.mySeed().isPrincipal())) ? "true" : "false")));

            processLocalCrawling(urlEntry, stats);
            return true;
        } catch (IOException e) {
            log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
            if (e.getMessage().indexOf("hash is null") > 0) noticeURL.clear(plasmaCrawlNURL.STACK_TYPE_REMOTE);
            return true;
        }
    }
    
    private void processLocalCrawling(plasmaCrawlEntry entry, String stats) {
        // work off one Crawl stack entry
        if ((entry == null) || (entry.url() == null)) {
            log.logInfo(stats + ": urlEntry = null");
            return;
        }
        
        synchronized (this.workers) {
            crawlWorker w = new crawlWorker(entry);
            synchronized (workers) {
                workers.put(new Integer(entry.hashCode()), w);
            }
        }
        
        log.logInfo(stats + ": enqueued for load " + entry.url() + " [" + entry.url().hash() + "]");
        return;
    }
    
    private boolean processRemoteCrawlTrigger(plasmaCrawlEntry urlEntry) {
        // if this returns true, then the urlEntry is considered as stored somewhere and the case is finished
        // if this returns false, the urlEntry will be enqueued to the local crawl again
        
        // wrong access
        if (urlEntry == null) {
            log.logInfo("REMOTECRAWLTRIGGER[" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]: urlEntry=null");
            return true; // superfluous request; true correct in this context because the urlEntry shall not be tracked any more
        }
        
        // check url
        if (urlEntry.url() == null) {
            log.logFine("ERROR: plasmaSwitchboard.processRemoteCrawlTrigger - url is null. name=" + urlEntry.name());
            return true; // same case as above: no more consideration
        }
        
        // are we qualified for a remote crawl?
        if ((yacyCore.seedDB.mySeed() == null) || (yacyCore.seedDB.mySeed().isJunior())) {
            log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: no permission");
            return false; // no, we must crawl this page ourselves
        }
        
        // check if peer for remote crawl is available
        yacySeed remoteSeed = ((sb.isPublicRobinson()) && (sb.getConfig("cluster.mode", "").equals("publiccluster"))) ?
            yacyCore.dhtAgent.getPublicClusterCrawlSeed(urlEntry.url().hash(), sb.clusterhashes) :    
            yacyCore.dhtAgent.getGlobalCrawlSeed(urlEntry.url().hash());
        if (remoteSeed == null) {
            log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: no remote crawl seed available");
            return false;
        }
        
        // do the request
        HashMap page = yacyClient.crawlOrder(remoteSeed, urlEntry.url(), sb.getURL(urlEntry.referrerhash()), 6000);
        if (page == null) {
            log.logSevere(plasmaSwitchboard.STR_REMOTECRAWLTRIGGER + remoteSeed.getName() + " FAILED. URL CANNOT BE RETRIEVED from referrer hash: " + urlEntry.referrerhash());
            return false;
        }
        
        // check if we got contact to peer and the peer respondet
        if ((page == null) || (page.get("delay") == null)) {
            log.logInfo("CRAWL: REMOTE CRAWL TO PEER " + remoteSeed.getName() + " FAILED. CAUSE: unknown (URL=" + urlEntry.url().toString() + "). Removed peer.");
            yacyCore.peerActions.peerDeparture(remoteSeed, "remote crawl to peer failed; peer answered unappropriate");
            return false; // no response from peer, we will crawl this ourself
        }
        
        String response = (String) page.get("response");
        log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: remoteSeed="
                + remoteSeed.getName() + ", url=" + urlEntry.url().toString()
                + ", response=" + page.toString()); // DEBUG

        // we received an answer and we are told to wait a specific time until we shall ask again for another crawl
        int newdelay = Integer.parseInt((String) page.get("delay"));
        yacyCore.dhtAgent.setCrawlDelay(remoteSeed.hash, newdelay);
        if (response.equals("stacked")) {
            // success, the remote peer accepted the crawl
            log.logInfo(plasmaSwitchboard.STR_REMOTECRAWLTRIGGER + remoteSeed.getName()
                    + " PLACED URL=" + urlEntry.url().toString()
                    + "; NEW DELAY=" + newdelay);
            // track this remote crawl
            delegatedURL.newEntry(urlEntry, remoteSeed.hash, new Date(), 0, response).store();
            return true;
        }
        
        // check other cases: the remote peer may respond that it already knows that url
        if (response.equals("double")) {
            // in case the peer answers double, it transmits the complete lurl data
            String lurl = (String) page.get("lurl");
            if ((lurl != null) && (lurl.length() != 0)) {
                String propStr = crypt.simpleDecode(lurl, (String) page.get("key"));
                indexURLEntry entry = sb.wordIndex.loadedURL.newEntry(propStr);
                try {
                    sb.wordIndex.loadedURL.store(entry);
                    sb.wordIndex.loadedURL.stack(entry, yacyCore.seedDB.mySeed().hash, remoteSeed.hash, 1); // *** ueberfluessig/doppelt?
                    // noticeURL.remove(entry.hash());
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                
                log.logInfo(plasmaSwitchboard.STR_REMOTECRAWLTRIGGER + remoteSeed.getName()
                        + " SUPERFLUOUS. CAUSE: " + page.get("reason")
                        + " (URL=" + urlEntry.url().toString()
                        + "). URL IS CONSIDERED AS 'LOADED!'");
                return true;
            } else {
                log.logInfo(plasmaSwitchboard.STR_REMOTECRAWLTRIGGER + remoteSeed.getName()
                        + " REJECTED. CAUSE: bad lurl response / " + page.get("reason") + " (URL="
                        + urlEntry.url().toString() + ")");
                remoteSeed.setFlagAcceptRemoteCrawl(false);
                yacyCore.seedDB.update(remoteSeed.hash, remoteSeed);
                return false;
            }
        }

        log.logInfo(plasmaSwitchboard.STR_REMOTECRAWLTRIGGER + remoteSeed.getName()
                + " DENIED. RESPONSE=" + response + ", CAUSE="
                + page.get("reason") + ", URL=" + urlEntry.url().toString());
        remoteSeed.setFlagAcceptRemoteCrawl(false);
        yacyCore.seedDB.update(remoteSeed.hash, remoteSeed);
        return false;
    }
    
    public plasmaHTCache.Entry loadResourceFromWeb(
            yacyURL url, 
            int socketTimeout,
            boolean keepInMemory,
            boolean forText
    ) {
        
        plasmaCrawlEntry centry = new plasmaCrawlEntry(
                yacyCore.seedDB.mySeed().hash, 
                url, 
                null, 
                "", 
                new Date(),
                (forText) ? sb.defaultTextSnippetProfile.handle() : sb.defaultMediaSnippetProfile.handle(), // crawl profile
                0, 
                0, 
                0);
        
        return loader.load(centry);
    }
    
    public int size() {
        return workers.size();
    }
    
    protected class crawlWorker extends Thread {
        
        public plasmaCrawlEntry entry;
        
        public crawlWorker(plasmaCrawlEntry entry) {
            this.entry = entry;
            this.entry.setStatus("worker-initialized");
            this.start();
        }
        
        public void run() {
            try {
                // checking robots.txt for http(s) resources
                this.entry.setStatus("worker-checkingrobots");
                if ((entry.url().getProtocol().equals("http") || entry.url().getProtocol().equals("https")) && robotsParser.isDisallowed(entry.url())) {
                    log.logFine("Crawling of URL '" + entry.url().toString() + "' disallowed by robots.txt.");
                    plasmaCrawlZURL.Entry eentry = errorURL.newEntry(this.entry.url(), "denied by robots.txt");
                    eentry.store();
                    errorURL.push(eentry);         
                } else {
                    // starting a load from the internet
                    this.entry.setStatus("worker-loading");
                    String result = loader.process(this.entry);
                    if (result != null) {
                        plasmaCrawlZURL.Entry eentry = errorURL.newEntry(this.entry.url(), "cannot load: " + result);
                        eentry.store();
                        errorURL.push(eentry);
                    } else {
                        this.entry.setStatus("worker-processed");
                    }
                }
            } catch (Exception e) {
                plasmaCrawlZURL.Entry eentry = errorURL.newEntry(this.entry.url(), e.getMessage() + " - in worker");
                eentry.store();
                errorURL.push(eentry);
                e.printStackTrace();
            } finally {
                synchronized (workers) {
                    workers.remove(new Integer(entry.hashCode()));
                }
                this.entry.setStatus("worker-finalized");
            }
        }
        
    }
    
}
