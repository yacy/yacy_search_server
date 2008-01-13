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
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.data.robotsParser;
import de.anomic.plasma.plasmaCrawlEntry;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaCrawlZURL;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.logging.serverLog;
import de.anomic.xml.rssReader;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class plasmaCrawlQueues {

    private plasmaSwitchboard sb;
    private serverLog log;
    private HashMap<String, crawlWorker> workers; // mapping from url hash to Worker thread object
    private plasmaProtocolLoader loader;
    private ArrayList<String> remoteCrawlProviderHashes;

    public  plasmaCrawlNURL             noticeURL;
    public  plasmaCrawlZURL             errorURL, delegatedURL;
    
    public plasmaCrawlQueues(plasmaSwitchboard sb, File plasmaPath) {
        this.sb = sb;
        this.log = new serverLog("CRAWLER");
        this.workers = new HashMap<String, crawlWorker>();
        this.loader = new plasmaProtocolLoader(sb, log);
        this.remoteCrawlProviderHashes = new ArrayList<String>();
        
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
        if (workers.containsKey(hash)) return "workers";
        return null;
    }
    
    public void urlRemove(String hash) {
        noticeURL.removeByURLHash(hash);
        delegatedURL.remove(hash);
        errorURL.remove(hash);
    }
    
    public yacyURL getURL(String urlhash) {
        if (urlhash.equals(yacyURL.dummyHash)) return null;
        crawlWorker w = workers.get(urlhash);
        if (w != null) return w.entry.url();
        plasmaCrawlEntry ne = noticeURL.get(urlhash);
        if (ne != null) return ne.url();
        plasmaCrawlZURL.Entry ee = delegatedURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        ee = errorURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        return null;
    }
    
    public void close() {
        // wait for all workers to finish
        Iterator<crawlWorker> i = workers.values().iterator();
        while (i.hasNext()) ((Thread) i.next()).interrupt();
        // TODO: wait some more time until all threads are finished
        noticeURL.close();
        errorURL.close();
        delegatedURL.close();
    }
    
    public plasmaCrawlEntry[] activeWorker() {
        synchronized (workers) {
            plasmaCrawlEntry[] w = new plasmaCrawlEntry[workers.size()];
            int i = 0;
            Iterator<crawlWorker> j = workers.values().iterator();
            while (j.hasNext()) {
                w[i++] = j.next().entry;
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
        
        boolean robinsonPrivateCase = ((sb.isRobinsonMode()) && 
                (!sb.getConfig(plasmaSwitchboard.CLUSTER_MODE, "").equals(plasmaSwitchboard.CLUSTER_MODE_PUBLIC_CLUSTER)) &&
                (!sb.getConfig(plasmaSwitchboard.CLUSTER_MODE, "").equals(plasmaSwitchboard.CLUSTER_MODE_PRIVATE_CLUSTER)));
        
        if (((robinsonPrivateCase) || (coreCrawlJobSize() <= 20)) && (limitCrawlJobSize() > 0)) {
            // move some tasks to the core crawl job so we have something to do
            int toshift = Math.min(10, limitCrawlJobSize()); // this cannot be a big number because the balancer makes a forced waiting if it cannot balance
            for (int i = 0; i < toshift; i++) {
                noticeURL.shift(plasmaCrawlNURL.STACK_TYPE_LIMIT, plasmaCrawlNURL.STACK_TYPE_CORE);
            }
            log.logInfo("shifted " + toshift + " jobs from global crawl to local crawl (coreCrawlJobSize()=" + coreCrawlJobSize() +
                    ", limitCrawlJobSize()=" + limitCrawlJobSize() + ", cluster.mode=" + sb.getConfig(plasmaSwitchboard.CLUSTER_MODE, "") +
                    ", robinsonMode=" + ((sb.isRobinsonMode()) ? "on" : "off"));
        }
        
        if (noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) == 0) {
            //log.logDebug("CoreCrawl: queue is empty");
            return false;
        }
        if (sb.sbQueue.size() >= (int) sb.getConfigLong(plasmaSwitchboard.INDEXER_SLOTS, 30)) {
            log.logFine("CoreCrawl: too many processes in indexing queue, dismissed (" + "sbQueueSize=" + sb.sbQueue.size() + ")");
            return false;
        }
        if (this.size() >= sb.getConfigLong(plasmaSwitchboard.CRAWLER_THREADS_ACTIVE_MAX, 10)) {
            log.logFine("CoreCrawl: too many processes in loader queue, dismissed (" + "cacheLoader=" + this.size() + ")");
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
                catch (InterruptedException e) {return false;}
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
    
    public boolean remoteCrawlLoaderJob() {
        // check if we are allowed to crawl urls provided by other peers
        if (!yacyCore.seedDB.mySeed().getFlagAcceptRemoteCrawl()) {
            //this.log.logInfo("remoteCrawlLoaderJob: not done, we are not allowed to do that");
            return false;
        }
        
        // check if we are a senior peer
        if (!yacyCore.seedDB.mySeed().isActive()) {
            //this.log.logInfo("remoteCrawlLoaderJob: not done, this should be a senior or principal peer");
            return false;
        }
        
        if (sb.sbQueue.size() >= (int) sb.getConfigLong(plasmaSwitchboard.INDEXER_SLOTS, 30)) {
            log.logFine("remoteCrawlLoaderJob: too many processes in indexing queue, dismissed (" + "sbQueueSize=" + sb.sbQueue.size() + ")");
            return false;
        }
        
        if (this.size() >= sb.getConfigLong(plasmaSwitchboard.CRAWLER_THREADS_ACTIVE_MAX, 10)) {
            log.logFine("remoteCrawlLoaderJob: too many processes in loader queue, dismissed (" + "cacheLoader=" + this.size() + ")");
            return false;
        }
        
        if (sb.onlineCaution()) {
            log.logFine("remoteCrawlLoaderJob: online caution, omitting processing");
            return false;
        }
        
        // check if we have an entry in the provider list, otherwise fill the list
        yacySeed seed;
        if ((remoteCrawlProviderHashes.size() == 0) &&
            (coreCrawlJobSize() == 0) &&
            (remoteTriggeredCrawlJobSize() == 0) &&
            (sb.queueSize() < 10)) {
            if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) {
                Iterator<yacySeed> e = yacyCore.dhtAgent.getProvidesRemoteCrawlURLs();
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
            hash = (String) remoteCrawlProviderHashes.remove(remoteCrawlProviderHashes.size() - 1);
            if (hash == null) continue;
            seed = yacyCore.seedDB.get(hash);
            if (seed == null) continue;
            // check if the peer is inside our cluster
            if ((sb.isRobinsonMode()) && (!sb.isInMyCluster(seed))) {
                seed = null;
                continue;
            }
        }
        if (seed == null) return false;
        
        // we know a peer which should provide remote crawl entries. load them now.
        rssReader reader = (seed == null) ? null : yacyClient.queryRemoteCrawlURLs(seed, 10);
        if (reader == null) return true;
        // parse the rss
        rssReader.Item item;
        yacyURL url, referrer;
        Date loaddate;
        for (int i = 0; i < reader.items(); i++) {
            item = reader.getItem(i);
            //System.out.println("URL=" + item.getLink() + ", desc=" + item.getDescription() + ", pubDate=" + item.getPubDate());
            
            // put url on remote crawl stack
            try {
                url = new yacyURL(item.getLink(), null);
            } catch (MalformedURLException e) {
                url = null;
            }
            try {
                referrer = new yacyURL(item.getReferrer(), null);
            } catch (MalformedURLException e) {
                referrer = null;
            }
            try {
                loaddate = serverDate.parseShortSecond(item.getPubDate());
            } catch (ParseException e) {
                loaddate = new Date();
            }
            if (sb.acceptURL(url)) {
                // stack url
                sb.getLog().logFinest("crawlOrder: stack: url='" + url + "'");
                String reasonString = sb.crawlStacker.stackCrawl(url, referrer, hash, item.getDescription(), loaddate, 0, sb.defaultRemoteProfile);

                if (reasonString == null) {
                    // done
                    log.logInfo("crawlOrder: added remote crawl url: " + url.toNormalform(true, false));
                } else if (reasonString.startsWith("double")) {
                    // case where we have already the url loaded;
                    log.logInfo("crawlOrder: ignored double remote crawl url: " + url.toNormalform(true, false));
                } else {
                    log.logInfo("crawlOrder: ignored [" + reasonString + "] remote crawl url: " + url.toNormalform(true, false));
                }
            } else {
                log.logWarning("crawlOrder: Received URL outside of our domain: " + url.toNormalform(true, false));
            }
        }
        return true;
    }
    
    public int limitCrawlJobSize() {
        return noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT);
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
            log.logFine("GlobalCrawl: too many processes in indexing queue, dismissed (" + "sbQueueSize=" + sb.sbQueue.size() + ")");
            return false;
        }
        if (this.size() >= sb.getConfigLong(plasmaSwitchboard.CRAWLER_THREADS_ACTIVE_MAX, 10)) {
            log.logFine("GlobalCrawl: too many processes in loader queue, dismissed (" + "cacheLoader=" + this.size() + ")");
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
                workers.put(entry.url().hash(), w);
            }
        }
        
        log.logInfo(stats + ": enqueued for load " + entry.url() + " [" + entry.url().hash() + "]");
        return;
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
        
        return loader.load(centry, (forText) ? plasmaParser.PARSER_MODE_CRAWLER : plasmaParser.PARSER_MODE_IMAGE);
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
                    String result = loader.process(this.entry, plasmaParser.PARSER_MODE_CRAWLER);
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
                    workers.remove(entry.url().hash());
                }
                this.entry.setStatus("worker-finalized");
            }
        }
        
    }
    
}
