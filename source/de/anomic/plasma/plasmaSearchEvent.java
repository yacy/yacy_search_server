// plasmaSearchEvent.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.10.2005 on http://yacy.net
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

package de.anomic.plasma;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.server.serverProfiling;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public final class plasmaSearchEvent {
    
    public static final String COLLECTION = "collection";
    public static final String JOIN = "join";
    public static final String PRESORT = "presort";
    public static final String URLFETCH = "urlfetch";
    public static final String NORMALIZING = "normalizing";
    
    public static int workerThreadCount = 8;
    public static String lastEventID = "";
    private static HashMap lastEvents = new HashMap(); // a cache for objects from this class: re-use old search requests
    public static final long eventLifetime = 600000; // the time an event will stay in the cache, 10 Minutes
    private static final int max_results_preparation = 200;
    
    private long eventTime;
    private plasmaSearchQuery query;
    private plasmaSearchRankingProfile ranking;
    private plasmaWordIndex wordIndex;
    private plasmaSearchRankingProcess rankedCache; // ordered search results, grows dynamically as all the query threads enrich this container
    private Map rcAbstracts; // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation
    private yacySearch[] primarySearchThreads, secondarySearchThreads;
    private Thread localSearchThread;
    private TreeMap preselectedPeerHashes;
    //private Object[] references;
    public  TreeMap IAResults, IACount;
    public  String IAmaxcounthash, IAneardhthash;
    private int localcount;
    private resultWorker[] workerThreads;
    private ArrayList resultList; // list of this.Entry objects
    //private int resultListLock; // a pointer that shows that all elements below this pointer are fixed and may not be changed again
    private HashMap failedURLs; // a mapping from a urlhash to a fail reason string
    TreeSet snippetFetchWordHashes; // a set of word hashes that are used to match with the snippets
    private long urlRetrievalAllTime;
    private long snippetComputationAllTime;
    
    private plasmaSearchEvent(plasmaSearchQuery query,
                             plasmaSearchRankingProfile ranking,
                             plasmaWordIndex wordIndex,
                             TreeMap preselectedPeerHashes,
                             boolean generateAbstracts,
                             TreeSet abstractSet) {
        this.eventTime = System.currentTimeMillis(); // for lifetime check
        this.wordIndex = wordIndex;
        this.query = query;
        this.ranking = ranking;
        this.rcAbstracts = (query.queryHashes.size() > 1) ? new TreeMap() : null; // generate abstracts only for combined searches
        this.primarySearchThreads = null;
        this.secondarySearchThreads = null;
        this.preselectedPeerHashes = preselectedPeerHashes;
        this.IAResults = new TreeMap();
        this.IACount = new TreeMap();
        this.IAmaxcounthash = null;
        this.IAneardhthash = null;
        this.localcount = 0;
        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.workerThreads = null;
        this.resultList = new ArrayList(10); // this is the result set which is filled up with search results, enriched with snippets
        //this.resultListLock = 0; // no locked elements until now
        this.failedURLs = new HashMap(); // a map of urls to reason strings where a worker thread tried to work on, but failed.
        
        // snippets do not need to match with the complete query hashes,
        // only with the query minus the stopwords which had not been used for the search
        final TreeSet filtered = kelondroMSetTools.joinConstructive(query.queryHashes, plasmaSwitchboard.stopwords);
        this.snippetFetchWordHashes = (TreeSet) query.queryHashes.clone();
        if ((filtered != null) && (filtered.size() > 0)) {
            kelondroMSetTools.excludeDestructive(this.snippetFetchWordHashes, plasmaSwitchboard.stopwords);
        }
        
        long start = System.currentTimeMillis();
        if ((query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) ||
            (query.domType == plasmaSearchQuery.SEARCHDOM_CLUSTERALL)) {
            // do a global search
            this.rankedCache = new plasmaSearchRankingProcess(wordIndex, query, ranking, 2, max_results_preparation);
            
            int fetchpeers = (int) (query.maximumTime / 500L); // number of target peers; means 10 peers in 10 seconds
            if (fetchpeers > 50) fetchpeers = 50;
            if (fetchpeers < 30) fetchpeers = 30;

            // the result of the fetch is then in the rcGlobal
            long timer = System.currentTimeMillis();
            serverLog.logFine("SEARCH_EVENT", "STARTING " + fetchpeers + " THREADS TO CATCH EACH " + query.displayResults() + " URLs");
            this.primarySearchThreads = yacySearch.primaryRemoteSearches(
                    plasmaSearchQuery.hashSet2hashString(query.queryHashes),
                    plasmaSearchQuery.hashSet2hashString(query.excludeHashes),
                    "",
                    query.prefer,
                    query.urlMask,
                    query.displayResults(),
                    query.maxDistance,
                    wordIndex,
                    rankedCache, 
                    rcAbstracts,
                    fetchpeers,
                    plasmaSwitchboard.urlBlacklist,
                    ranking,
                    query.constraint,
                    (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) ? null : preselectedPeerHashes);
            serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), "remote search thread start", this.primarySearchThreads.length, System.currentTimeMillis() - timer));
            
            // meanwhile do a local search
            localSearchThread = new localSearchProcess();
            localSearchThread.start();
           
            // finished searching
            serverLog.logFine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO " + primarySearchThreads.length + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        } else {
            // do a local search
            this.rankedCache = new plasmaSearchRankingProcess(wordIndex, query, ranking, 2, max_results_preparation);
            this.rankedCache.execQuery(true);
            this.localcount = this.rankedCache.filteredCount();
            //plasmaWordIndex.Finding finding = wordIndex.retrieveURLs(query, false, 2, ranking, process);
            
            if (generateAbstracts) {
                // compute index abstracts
                long timer = System.currentTimeMillis();
                Iterator ci = this.rankedCache.searchContainerMaps()[0].entrySet().iterator();
                Map.Entry entry;
                int maxcount = -1;
                double mindhtdistance = 1.1, d;
                String wordhash;
                while (ci.hasNext()) {
                    entry = (Map.Entry) ci.next();
                    wordhash = (String) entry.getKey();
                    indexContainer container = (indexContainer) entry.getValue();
                    assert (container.getWordHash().equals(wordhash));
                    if (container.size() > maxcount) {
                        IAmaxcounthash = wordhash;
                        maxcount = container.size();
                    }
                    d = yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed().hash, wordhash);
                    if (d < mindhtdistance) {
                        // calculate the word hash that is closest to our dht position
                        mindhtdistance = d;
                        IAneardhthash = wordhash;
                    }
                    IACount.put(wordhash, new Integer(container.size()));
                    IAResults.put(wordhash, indexContainer.compressIndex(container, null, 1000).toString());
                }
                serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), "abstract generation", this.rankedCache.searchContainerMaps()[0].size(), System.currentTimeMillis() - timer));
            }
            
        }
        
        if (query.onlineSnippetFetch) {
            // start worker threads to fetch urls and snippets
            this.workerThreads = new resultWorker[workerThreadCount];
            for (int i = 0; i < workerThreadCount; i++) {
                this.workerThreads[i] = new resultWorker(i, query.maximumTime * 3);
                this.workerThreads[i].start();
            }
        } else {
            // prepare result vector directly without worker threads
            long timer = System.currentTimeMillis();
            indexURLEntry uentry;
            ResultEntry resultEntry;
            yacyURL url;
            synchronized (rankedCache) {
                while ((rankedCache.size() > 0) && ((uentry = rankedCache.bestURL(true)) != null) && (resultList.size() < (query.neededResults()))) {
                    url = uentry.comp().url();
                    if (url == null) continue;
                    //System.out.println("***DEBUG*** SEARCH RESULT URL=" + url.toNormalform(false, false));
                
                    resultEntry = obtainResultEntry(uentry, (snippetComputationAllTime < 300) ? 1 : 0);
                    if (resultEntry == null) continue; // the entry had some problems, cannot be used
                    urlRetrievalAllTime += resultEntry.dbRetrievalTime;
                    snippetComputationAllTime += resultEntry.snippetComputationTime;
                
                    // place the result to the result vector
                    synchronized (resultList) {
                        resultList.add(resultEntry);
                    }

                    // add references
                    synchronized (rankedCache) {
                        rankedCache.addReferences(resultEntry);
                    }
                }
            }
            serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), "offline snippet fetch", resultList.size(), System.currentTimeMillis() - timer));
        }
        
        // clean up events
        cleanupEvents(false);
        
        // store this search to a cache so it can be re-used
        lastEvents.put(query.id(false), this);
        lastEventID = query.id(false);
    }

    private class localSearchProcess extends Thread {
        
        public localSearchProcess() {
        }
        
        public void run() {
            // do a local search
            
            // sort the local containers and truncate it to a limited count,
            // so following sortings together with the global results will be fast
            synchronized (rankedCache) {
                rankedCache.execQuery(true);
                localcount = rankedCache.filteredCount();
            }
        }
    }

    public static void cleanupEvents(boolean all) {
        // remove old events in the event cache
        Iterator i = lastEvents.entrySet().iterator();
        plasmaSearchEvent cleanEvent;
        while (i.hasNext()) {
            cleanEvent = (plasmaSearchEvent) ((Map.Entry) i.next()).getValue();
            if ((all) || (cleanEvent.eventTime + eventLifetime < System.currentTimeMillis())) {
                // execute deletion of failed words
                Set removeWords = cleanEvent.query.queryHashes;
                removeWords.addAll(cleanEvent.query.excludeHashes);
                cleanEvent.wordIndex.removeEntriesMultiple(removeWords, cleanEvent.failedURLs.keySet());
                serverLog.logInfo("SearchEvents", "cleaning up event " + cleanEvent.query.id(true) + ", removed " + cleanEvent.failedURLs.size() + " URL references on " + removeWords.size() + " words");
                
                // remove the event
                i.remove();
            }
        }
    }
    
    private ResultEntry obtainResultEntry(indexURLEntry page, int snippetFetchMode) {

        // a search result entry needs some work to produce a result Entry:
        // - check if url entry exists in LURL-db
        // - check exclusions, constraints, masks, media-domains
        // - load snippet (see if page exists) and check if snippet contains searched word

        // Snippet Fetching can has 3 modes:
        // 0 - do not fetch snippets
        // 1 - fetch snippets offline only
        // 2 - online snippet fetch
        
        // load only urls if there was not yet a root url of that hash
        // find the url entry
        
        long startTime = System.currentTimeMillis();
        indexURLEntry.Components comp = page.comp();
        String pagetitle = comp.title().toLowerCase();
        if (comp.url() == null) {
            registerFailure(page.hash(), "url corrupted (null)");
            return null; // rare case where the url is corrupted
        }
        String pageurl = comp.url().toString().toLowerCase();
        String pageauthor = comp.author().toLowerCase();
        long dbRetrievalTime = System.currentTimeMillis() - startTime;
        
        // check exclusion
        if ((plasmaSearchQuery.matches(pagetitle, query.excludeHashes)) ||
            (plasmaSearchQuery.matches(pageurl, query.excludeHashes)) ||
            (plasmaSearchQuery.matches(pageauthor, query.excludeHashes))) {
            return null;
        }
            
        // check url mask
        if (!(pageurl.matches(query.urlMask))) {
            return null;
        }
            
        // check constraints
        if ((query.constraint != null) &&
            (query.constraint.get(plasmaCondenser.flag_cat_indexof)) &&
            (!(comp.title().startsWith("Index of")))) {
            final Iterator wi = query.queryHashes.iterator();
            while (wi.hasNext()) wordIndex.removeEntry((String) wi.next(), page.hash());
            registerFailure(page.hash(), "index-of constraint not fullfilled");
            return null;
        }
        
        if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (page.laudio() == 0)) {
            registerFailure(page.hash(), "contentdom-audio constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (page.lvideo() == 0)) {
            registerFailure(page.hash(), "contentdom-video constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (page.limage() == 0)) {
            registerFailure(page.hash(), "contentdom-image constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_APP) && (page.lapp() == 0)) {
            registerFailure(page.hash(), "contentdom-app constraint not fullfilled");
            return null;
        }

        if (snippetFetchMode == 0) {
            return new ResultEntry(page, wordIndex, null, null, dbRetrievalTime, 0); // result without snippet
        }
        
        // load snippet
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_TEXT) {
            // attach text snippet
            startTime = System.currentTimeMillis();
            plasmaSnippetCache.TextSnippet snippet = plasmaSnippetCache.retrieveTextSnippet(comp.url(), snippetFetchWordHashes, (snippetFetchMode == 2), ((query.constraint != null) && (query.constraint.get(plasmaCondenser.flag_cat_indexof))), 180, 3000, (snippetFetchMode == 2) ? Integer.MAX_VALUE : 100000);
            long snippetComputationTime = System.currentTimeMillis() - startTime;
            serverLog.logInfo("SEARCH_EVENT", "text snippet load time for " + comp.url() + ": " + snippetComputationTime + ", " + ((snippet.getErrorCode() < 11) ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));
            
            if (snippet.getErrorCode() < 11) {
                // we loaded the file and found the snippet
                return new ResultEntry(page, wordIndex, snippet, null, dbRetrievalTime, snippetComputationTime); // result with snippet attached
            } else if (snippetFetchMode == 1) {
                // we did not demand online loading, therefore a failure does not mean that the missing snippet causes a rejection of this result
                // this may happen during a remote search, because snippet loading is omitted to retrieve results faster
                return new ResultEntry(page, wordIndex, null, null, dbRetrievalTime, snippetComputationTime); // result without snippet
            } else {
                // problems with snippet fetch
                registerFailure(page.hash(), "no text snippet for URL " + comp.url());
                plasmaSnippetCache.failConsequences(snippet, query.id(false));
                return null;
            }
        } else {
            // attach media information
            startTime = System.currentTimeMillis();
            ArrayList mediaSnippets = plasmaSnippetCache.retrieveMediaSnippets(comp.url(), snippetFetchWordHashes, query.contentdom, (snippetFetchMode == 2), 6000);
            long snippetComputationTime = System.currentTimeMillis() - startTime;
            serverLog.logInfo("SEARCH_EVENT", "media snippet load time for " + comp.url() + ": " + snippetComputationTime);
            
            if ((mediaSnippets != null) && (mediaSnippets.size() > 0)) {
                // found media snippets, return entry
                return new ResultEntry(page, wordIndex, null, mediaSnippets, dbRetrievalTime, snippetComputationTime);
            } else if (snippetFetchMode == 1) {
                return new ResultEntry(page, wordIndex, null, null, dbRetrievalTime, snippetComputationTime);
            } else {
                // problems with snippet fetch
                registerFailure(page.hash(), "no media snippet for URL " + comp.url());
                return null;
            }
        }
        // finished, no more actions possible here
    }
    
    private boolean anyWorkerAlive() {
        if (this.workerThreads == null) return false;
        for (int i = 0; i < workerThreadCount; i++) {
           if ((this.workerThreads[i] != null) &&
        	   (this.workerThreads[i].isAlive()) &&
        	   (this.workerThreads[i].busytime() < 3000)) return true;
        }
        return false;
    }
    
    private boolean anyRemoteSearchAlive() {
        // check primary search threads
        if ((this.primarySearchThreads != null) && (this.primarySearchThreads.length != 0)) {
            for (int i = 0; i < this.primarySearchThreads.length; i++) {
                if ((this.primarySearchThreads[i] != null) && (this.primarySearchThreads[i].isAlive())) return true;
            }
        }
        // maybe a secondary search thread is alive, check this
        if ((this.secondarySearchThreads != null) && (this.secondarySearchThreads.length != 0)) {
            for (int i = 0; i < this.secondarySearchThreads.length; i++) {
                if ((this.secondarySearchThreads[i] != null) && (this.secondarySearchThreads[i].isAlive())) return true;
            }
        }
        return false;
    }
    
    public plasmaSearchQuery getQuery() {
        return query;
    }
    
    public plasmaSearchRankingProfile getRanking() {
        return ranking;
    }
    
    public yacySearch[] getPrimarySearchThreads() {
        return primarySearchThreads;
    }
    
    public yacySearch[] getSecondarySearchThreads() {
        return secondarySearchThreads;
    }
    
    public int getLocalCount() {
        return this.localcount;
    }
    
    public int getGlobalCount() {
        return this.rankedCache.getGlobalCount();
    }
    
    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }
    
    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }

    public static plasmaSearchEvent getEvent(String eventID) {
        synchronized (lastEvents) {
            return (plasmaSearchEvent) lastEvents.get(eventID);
        }
    }
    
    public static plasmaSearchEvent getEvent(plasmaSearchQuery query,
            plasmaSearchRankingProfile ranking,
            plasmaWordIndex wordIndex,
            TreeMap preselectedPeerHashes,
            boolean generateAbstracts,
            TreeSet abstractSet) {
        synchronized (lastEvents) {
            plasmaSearchEvent event = (plasmaSearchEvent) lastEvents.get(query.id(false));
            if (event == null) {
                event = new plasmaSearchEvent(query, ranking, wordIndex, preselectedPeerHashes, generateAbstracts, abstractSet);
            } else {
                //re-new the event time for this event, so it is not deleted next time too early
                event.eventTime = System.currentTimeMillis();
                // replace the query, because this contains the current result offset
                event.query = query;
            }
        
            // if worker threads had been alive, but did not succeed, start them again to fetch missing links
            if ((query.onlineSnippetFetch) &&
                (!event.anyWorkerAlive()) &&
                (event.resultList.size() < query.neededResults() + 10) &&
                ((event.getLocalCount() + event.getGlobalCount()) > event.resultList.size())) {
                // set new timeout
                event.eventTime = System.currentTimeMillis();
                // start worker threads to fetch urls and snippets
                event.workerThreads = new resultWorker[workerThreadCount];
                for (int i = 0; i < workerThreadCount; i++) {
                    event.workerThreads[i] = event.deployWorker(i, 3 * query.maximumTime);
                }
            }
        
            return event;
        }
        
    }
    
    private resultWorker deployWorker(int id, long lifetime) {
        resultWorker worker = new resultWorker(id, lifetime);
        worker.start();
        return worker;
    }

    private class resultWorker extends Thread {
        
        private long timeout; // the date until this thread should try to work
        private long sleeptime; // the sleeptime of this thread at the beginning of its life
        private long lastLifeSign; // when the last time the run()-loop was executed
        private int id;
        
        public resultWorker(int id, long maxlifetime) {
            this.id = id;
            this.lastLifeSign = System.currentTimeMillis();
            this.timeout = System.currentTimeMillis() + maxlifetime;
            this.sleeptime = Math.min(300, maxlifetime / 10 * id);
        }

        public void run() {

            // start fetching urls and snippets
            indexURLEntry page;
            while (System.currentTimeMillis() < this.timeout) {
                this.lastLifeSign = System.currentTimeMillis();

                if (resultList.size() >= query.neededResults() + query.displayResults()) break; // we have enough

                // get next entry
                page = rankedCache.bestURL(true);
                if (page == null) {
                	if (!anyRemoteSearchAlive()) break; // we cannot expect more results
                    // if we did not get another entry, sleep some time and try again
                    try {Thread.sleep(100);} catch (InterruptedException e1) {}
                    continue;
                }
                if (anyResultWith(page.hash())) continue;
                if (anyFailureWith(page.hash())) continue;
                
                // try secondary search
                prepareSecondarySearch(); // will be executed only once
                
                ResultEntry resultEntry = obtainResultEntry(page, 2);
                if (resultEntry == null) continue; // the entry had some problems, cannot be used
                urlRetrievalAllTime += resultEntry.dbRetrievalTime;
                snippetComputationAllTime += resultEntry.snippetComputationTime;
                //System.out.println("+++DEBUG-resultWorker+++ fetched " + resultEntry.urlstring());
                
                // place the result to the result vector
                synchronized (resultList) {
                    resultList.add(resultEntry);
                }

                // add references
                synchronized (rankedCache) {
                    rankedCache.addReferences(resultEntry);
                }
                //System.out.println("DEBUG SNIPPET_LOADING: thread " + id + " got " + resultEntry.url());

                if (resultList.size() >= query.neededResults() + query.displayResults()) break; // we have enough

                // sleep first to give remote loading threads a chance to fetch entries
                if (anyRemoteSearchAlive()) try {Thread.sleep(this.sleeptime);} catch (InterruptedException e1) {}
            }
            serverLog.logInfo("SEARCH", "resultWorker thread " + id + " terminated");
        }
        
        private boolean anyResultWith(String urlhash) {
            for (int i = 0; i < resultList.size(); i++) {
                if (((ResultEntry) resultList.get(i)).urlentry.hash().equals(urlhash)) return true;
            }
            return false;
        }
        
        private boolean anyFailureWith(String urlhash) {
            return (failedURLs.get(urlhash) != null);
        }
        
        public long busytime() {
        	return System.currentTimeMillis() - this.lastLifeSign;
        }
    }
    
    private void registerFailure(String urlhash, String reason) {
        this.failedURLs.put(urlhash, reason);
        serverLog.logInfo("search", "sorted out hash " + urlhash + " during search: " + reason);
    }
    
    public ResultEntry oneResult(int item) {
        // first sleep a while to give accumulation threads a chance to work
        if (anyWorkerAlive()) {
        	long sleeptime = Math.min(600, this.eventTime + (this.query.maximumTime / this.query.displayResults() * ((item % this.query.displayResults()) + 1)) - System.currentTimeMillis());
        	if (this.resultList.size() <= item + 10) sleeptime = Math.min(sleeptime + 300, 600);
        	if (sleeptime > 0) try {Thread.sleep(sleeptime);} catch (InterruptedException e) {}
            System.out.println("+++DEBUG-oneResult+++ (1) sleeping " + sleeptime);
            
            // then sleep until any result is available (that should not happen)
            while ((this.resultList.size() <= item) && (anyWorkerAlive())) {
                try {Thread.sleep(100);} catch (InterruptedException e) {}
                System.out.println("+++DEBUG-oneResult+++ (2) sleeping " + 100);
            }
            
        }
        // finally, if there is something, return the result
        synchronized (this.resultList) {
            // check if we have enough entries
            if (this.resultList.size() <= item) return null;
            
            // fetch the best entry from the resultList, not the entry from item position
            // whenever a specific entry was switched in its position and was returned here
            // a moving pointer is set to assign that item position as not changeable
            int bestpick = item; //postRankingFavourite(item);
            if (bestpick != item) {
                // switch the elements
                ResultEntry buf = (ResultEntry) this.resultList.get(bestpick);
                serverLog.logInfo("SEARCH_POSTRANKING", "prefering [" + bestpick + "] " + buf.urlstring() + " over [" + item + "] " + ((ResultEntry) this.resultList.get(item)).urlstring());
                this.resultList.set(bestpick, (ResultEntry) this.resultList.get(item));
                this.resultList.set(item, buf);
            }
            
            //this.resultListLock = item; // lock the element; be prepared to return it
            return (ResultEntry) this.resultList.get(item);
        }
    }
    
    public ArrayList completeResults(long waitingtime) {
        long timeout = System.currentTimeMillis() + waitingtime;
        while ((this.resultList.size() < query.neededResults()) && (anyWorkerAlive()) && (System.currentTimeMillis() < timeout)) {
            try {Thread.sleep(200);} catch (InterruptedException e) {}
            //System.out.println("+++DEBUG-completeResults+++ sleeping " + 200);
        }
        return this.resultList;
    }
    
    boolean secondarySearchStartet = false;
    
    private void prepareSecondarySearch() {
        if (secondarySearchStartet) return; // dont do this twice
        
        if ((rcAbstracts == null) || (rcAbstracts.size() != query.queryHashes.size())) return; // secondary search not possible (yet)
        this.secondarySearchStartet = true;
        
        // catch up index abstracts and join them; then call peers again to submit their urls
        System.out.println("DEBUG-INDEXABSTRACT: " + rcAbstracts.size() + " word references catched, " + query.queryHashes.size() + " needed");

        Iterator i = rcAbstracts.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            System.out.println("DEBUG-INDEXABSTRACT: hash " + (String) entry.getKey() + ": " + ((query.queryHashes.contains((String) entry.getKey())) ? "NEEDED" : "NOT NEEDED") + "; " + ((TreeMap) entry.getValue()).size() + " entries");
        }
        
        TreeMap abstractJoin = (rcAbstracts.size() == query.queryHashes.size()) ? kelondroMSetTools.joinConstructive(rcAbstracts.values(), true) : new TreeMap();
        if (abstractJoin.size() == 0) {
            System.out.println("DEBUG-INDEXABSTRACT: no success using index abstracts from remote peers");
        } else {
            System.out.println("DEBUG-INDEXABSTRACT: index abstracts delivered " + abstractJoin.size() + " additional results for secondary search");
            // generate query for secondary search
            TreeMap secondarySearchURLs = new TreeMap(); // a (peerhash:urlhash-liststring) mapping
            Iterator i1 = abstractJoin.entrySet().iterator();
            Map.Entry entry1;
            String url, urls, peer, peers;
            String mypeerhash = yacyCore.seedDB.mySeed().hash;
            boolean mypeerinvolved = false;
            int mypeercount;
            while (i1.hasNext()) {
                entry1 = (Map.Entry) i1.next();
                url = (String) entry1.getKey();
                peers = (String) entry1.getValue();
                System.out.println("DEBUG-INDEXABSTRACT: url " + url + ": from peers " + peers);
                mypeercount = 0;
                for (int j = 0; j < peers.length(); j = j + 12) {
                    peer = peers.substring(j, j + 12);
                    if ((peer.equals(mypeerhash)) && (mypeercount++ > 1)) continue;
                    //if (peers.indexOf(peer) < j) continue; // avoid doubles that may appear in the abstractJoin
                    urls = (String) secondarySearchURLs.get(peer);
                    urls = (urls == null) ? url : urls + url;
                    secondarySearchURLs.put(peer, urls);
                }
                if (mypeercount == 1) mypeerinvolved = true;
            }
            
            // compute words for secondary search and start the secondary searches
            i1 = secondarySearchURLs.entrySet().iterator();
            String words;
            secondarySearchThreads = new yacySearch[(mypeerinvolved) ? secondarySearchURLs.size() - 1 : secondarySearchURLs.size()];
            int c = 0;
            while (i1.hasNext()) {
                entry1 = (Map.Entry) i1.next();
                peer = (String) entry1.getKey();
                if (peer.equals(mypeerhash)) continue; // we dont need to ask ourself
                urls = (String) entry1.getValue();
                words = wordsFromPeer(peer, urls);
                System.out.println("DEBUG-INDEXABSTRACT ***: peer " + peer + "   has urls: " + urls);
                System.out.println("DEBUG-INDEXABSTRACT ***: peer " + peer + " from words: " + words);
                secondarySearchThreads[c++] = yacySearch.secondaryRemoteSearch(
                        words, "", urls, wordIndex, this.rankedCache, peer, plasmaSwitchboard.urlBlacklist,
                        ranking, query.constraint, preselectedPeerHashes);

            }
        }
    }
    
    private String wordsFromPeer(String peerhash, String urls) {
        Map.Entry entry;
        String word, peerlist, url, wordlist = "";
        TreeMap urlPeerlist;
        int p;
        boolean hasURL;
        synchronized (rcAbstracts) {
            Iterator i = rcAbstracts.entrySet().iterator();
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                word = (String) entry.getKey();
                urlPeerlist = (TreeMap) entry.getValue();
                hasURL = true;
                for (int j = 0; j < urls.length(); j = j + 12) {
                    url = urls.substring(j, j + 12);
                    peerlist = (String) urlPeerlist.get(url);
                    p = (peerlist == null) ? -1 : peerlist.indexOf(peerhash);
                    if ((p < 0) || (p % 12 != 0)) {
                        hasURL = false;
                        break;
                    }
                }
                if (hasURL) wordlist += word;
            }
        }
        return wordlist;
    }
 
    public void remove(String urlhash) {
        // removes the url hash reference from last search result
        /*indexRWIEntry e =*/ this.rankedCache.remove(urlhash);
        //assert e != null;
    }
    
    public Set references(int count) {
        // returns a set of words that are computed as toplist
        return this.rankedCache.getReferences(count);
    }
    
    public static class ResultEntry {
        // payload objects
        private indexURLEntry urlentry;
        private indexURLEntry.Components urlcomps; // buffer for components
        private String alternative_urlstring;
        private String alternative_urlname;
        private plasmaSnippetCache.TextSnippet textSnippet;
        private ArrayList /* of plasmaSnippetCache.MediaSnippet */ mediaSnippets;
        
        // statistic objects
        public long dbRetrievalTime, snippetComputationTime;
        
        public ResultEntry(indexURLEntry urlentry, plasmaWordIndex wordIndex, plasmaSnippetCache.TextSnippet textSnippet, ArrayList mediaSnippets,
                           long dbRetrievalTime, long snippetComputationTime) {
            this.urlentry = urlentry;
            this.urlcomps = urlentry.comp();
            this.alternative_urlstring = null;
            this.alternative_urlname = null;
            this.textSnippet = textSnippet;
            this.mediaSnippets = mediaSnippets;
            this.dbRetrievalTime = dbRetrievalTime;
            this.snippetComputationTime = snippetComputationTime;
            String host = urlcomps.url().getHost();
            if (host.endsWith(".yacyh")) {
                // translate host into current IP
                int p = host.indexOf(".");
                String hash = yacySeed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
                yacySeed seed = yacyCore.seedDB.getConnected(hash);
                String filename = urlcomps.url().getFile();
                String address = null;
                if ((seed == null) || ((address = seed.getPublicAddress()) == null)) {
                    // seed is not known from here
                    try {
                        wordIndex.removeWordReferences(
                            plasmaCondenser.getWords(
                                ("yacyshare " +
                                 filename.replace('?', ' ') +
                                 " " +
                                 urlcomps.title()).getBytes(), "UTF-8").keySet(),
                                 urlentry.hash());
                        wordIndex.loadedURL.remove(urlentry.hash()); // clean up
                        throw new RuntimeException("index void");
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("parser failed: " + e.getMessage());
                    }
                }
                alternative_urlstring = "http://" + address + "/" + host.substring(0, p) + filename;
                alternative_urlname = "http://share." + seed.getName() + ".yacy" + filename;
                if ((p = alternative_urlname.indexOf("?")) > 0) alternative_urlname = alternative_urlname.substring(0, p);
            }
        }
        
        public String hash() {
            return urlentry.hash();
        }
        public yacyURL url() {
            return urlcomps.url();
        }
        public kelondroBitfield flags() {
            return urlentry.flags();
        }
        public String urlstring() {
            return (alternative_urlstring == null) ? urlcomps.url().toNormalform(false, true) : alternative_urlstring;
        }
        public String urlname() {
            return (alternative_urlname == null) ? urlcomps.url().toNormalform(false, true) : alternative_urlname;
        }
        public String title() {
            return urlcomps.title();
        }
        public plasmaSnippetCache.TextSnippet textSnippet() {
            return this.textSnippet;
        }
        public ArrayList /* of plasmaSnippetCache.MediaSnippet */ mediaSnippets() {
            return this.mediaSnippets;
        }
        public Date modified() {
            return urlentry.moddate();
        }
        public int filesize() {
            return urlentry.size();
        }
        public int limage() {
            return urlentry.limage();
        }
        public int laudio() {
            return urlentry.laudio();
        }
        public int lvideo() {
            return urlentry.lvideo();
        }
        public int lapp() {
            return urlentry.lapp();
        }
        public indexRWIEntry word() {
            return urlentry.word();
        }
        public boolean hasTextSnippet() {
            return (this.textSnippet != null) && (this.textSnippet.getErrorCode() < 11);
        }
        public boolean hasMediaSnippets() {
            return (this.mediaSnippets != null) && (this.mediaSnippets.size() > 0);
        }
        public String resource() {
            // generate transport resource
            if ((textSnippet != null) && (textSnippet.exists())) {
                return urlentry.toString(textSnippet.getLineRaw());
            } else {
                return urlentry.toString();
            }
        }
    }
}
