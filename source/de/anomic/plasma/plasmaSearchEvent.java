// plasmaSearchEvent.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created: 10.10.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


package de.anomic.plasma;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.index.indexContainer;
import de.anomic.index.indexEntry;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySearch;

public final class plasmaSearchEvent extends Thread implements Runnable {
    
    public static plasmaSearchEvent lastEvent = null;

    private static HashSet flushThreads = new HashSet();
    
    private serverLog log;
    private plasmaSearchQuery query;
    private plasmaSearchRankingProfile ranking;
    private plasmaWordIndex wordIndex;
    private plasmaCrawlLURL urlStore;
    private plasmaSnippetCache snippetCache;
    private indexContainer rcContainers; // cache for results
    private int rcContainerFlushCount;
    private Map rcAbstracts; // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation
    private plasmaSearchTimingProfile profileLocal, profileGlobal;
    private boolean postsort;
    private yacySearch[] primarySearchThreads, secondarySearchThreads;
    
    public plasmaSearchEvent(plasmaSearchQuery query,
                             plasmaSearchRankingProfile ranking,
                             plasmaSearchTimingProfile localTiming,
                             plasmaSearchTimingProfile remoteTiming,
                             boolean postsort,
                             serverLog log,
                             plasmaWordIndex wordIndex,
                             plasmaCrawlLURL urlStore,
                             plasmaSnippetCache snippetCache) {
        this.log = log;
        this.wordIndex = wordIndex;
        this.query = query;
        this.ranking = ranking;
        this.urlStore = urlStore;
        this.snippetCache = snippetCache;
        this.rcContainers = new indexContainer(null);
        this.rcContainerFlushCount = 0;
        this.rcAbstracts = new TreeMap();
        this.profileLocal = localTiming;
        this.profileGlobal = remoteTiming;
        this.postsort = postsort;
        this.primarySearchThreads = null;
        this.secondarySearchThreads = null;
    }
    
    public plasmaSearchQuery getQuery() {
        return query;
    }
    
    public plasmaSearchTimingProfile getLocalTiming() {
        return profileLocal;
    }
    
    public yacySearch[] getPrimarySearchThreads() {
        return primarySearchThreads;
    }
    public yacySearch[] getSecondarySearchThreads() {
        return secondarySearchThreads;
    }
    
    public plasmaSearchResult search() {
        // combine all threads
        
        // we synchronize with flushThreads to allow only one local search at a time,
        // so all search tasks are queued
        synchronized (flushThreads) {

            if (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) {
                int fetchpeers = (int) (query.maximumTime / 500L); // number of target peers; means 10 peers in 10 seconds
                if (fetchpeers > 50) fetchpeers = 50;
                if (fetchpeers < 30) fetchpeers = 30;

                // remember time
                long start = System.currentTimeMillis();

                // do a global search
                // the result of the fetch is then in the rcGlobal
                log.logFine("STARTING " + fetchpeers + " THREADS TO CATCH EACH " + profileGlobal.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT) + " URLs WITHIN " + (profileGlobal.duetime() / 1000) + " SECONDS");
                long secondaryTimeout = System.currentTimeMillis() + profileGlobal.duetime() / 2;
                long primaryTimeout = System.currentTimeMillis() + profileGlobal.duetime();
                primarySearchThreads = yacySearch.primaryRemoteSearches(plasmaSearchQuery.hashSet2hashString(query.queryHashes), "",
                        query.prefer, query.urlMask, query.maxDistance, urlStore, rcContainers, rcAbstracts,
                        fetchpeers, plasmaSwitchboard.urlBlacklist, snippetCache, profileGlobal, ranking);

                // meanwhile do a local search
                Map searchContainerMap = localSearchContainers(null);
                
                // use the search containers to fill up rcAbstracts locally
                if (searchContainerMap != null) {
                    Iterator i, ci = searchContainerMap.entrySet().iterator();
                    Map.Entry entry;
                    String wordhash;
                    indexContainer container;
                    TreeMap singleAbstract;
                    String mypeerhash = yacyCore.seedDB.mySeed.hash;
                    while (ci.hasNext()) {
                        entry = (Map.Entry) ci.next();
                        wordhash = (String) entry.getKey();
                        container = (indexContainer) entry.getValue();
                        // collect all urlhashes from the container
                        synchronized (rcAbstracts) {
                            singleAbstract = (TreeMap) rcAbstracts.get(wordhash); // a mapping from url-hashes to a string of peer-hashes
                            if (singleAbstract == null) singleAbstract = new TreeMap();
                            i = container.entries();
                            while (i.hasNext()) singleAbstract.put(((indexEntry) i.next()).urlHash(), mypeerhash);
                            rcAbstracts.put(wordhash, singleAbstract);
                        }
                    }
                }
                
                // try to pre-fetch some LURLs if there is enough time
                indexContainer rcLocal = localSearchJoin((searchContainerMap == null) ? null : searchContainerMap.values());
                prefetchLocal(rcLocal, secondaryTimeout);
                
                // evaluate index abstracts and start a secondary search
                // this is temporary debugging code to learn that the index abstracts are fetched correctly
                while (System.currentTimeMillis() < secondaryTimeout + 10000) {
                    if (yacySearch.remainingWaiting(primarySearchThreads) == 0) break; // all threads have finished
                    try {Thread.sleep(100);} catch (InterruptedException e) {}
                }
                if (query.size() > 1) prepareSecondarySearch();
                
                // catch up global results:
                // wait until primary timeout passed
                while (System.currentTimeMillis() < primaryTimeout) {
                    if ((yacySearch.remainingWaiting(primarySearchThreads) == 0) &&
                        ((secondarySearchThreads == null) || (yacySearch.remainingWaiting(secondarySearchThreads) == 0))) break; // all threads have finished
                    try {Thread.sleep(100);} catch (InterruptedException e) {}
                }
                int globalContributions = rcContainers.size();
                
                // finished searching
                log.logFine("SEARCH TIME AFTER GLOBAL-TRIGGER TO " + fetchpeers + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");

                // combine the result and order
                plasmaSearchResult result = orderFinal(rcLocal);
                result.globalContributions = globalContributions;
                result.localContributions = rcLocal.size();
                
                // flush results in a separate thread
                this.start(); // start to flush results

                // return search result
                log.logFine("SEARCHRESULT: " + profileLocal.reportToString());
                lastEvent = this;
                return result;
            } else {
                Map searchContainerMap = localSearchContainers(null);
                indexContainer rcLocal = localSearchJoin((searchContainerMap == null) ? null : searchContainerMap.values());
                plasmaSearchResult result = orderFinal(rcLocal);
                result.localContributions = rcLocal.size();

                // return search result
                log.logFine("SEARCHRESULT: " + profileLocal.reportToString());
                lastEvent = this;
                return result;
            }
        }
    }

    private void prepareSecondarySearch() {
        // catch up index abstracts and join them; then call peers again to submit their urls
        System.out.println("DEBUG-INDEXABSTRACT: " + rcAbstracts.size() + " word references catched, " + query.size() + " needed");
        
        if (rcAbstracts.size() != query.size()) return; // secondary search not possible
        
        Iterator i = rcAbstracts.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            System.out.println("DEBUG-INDEXABSTRACT: hash " + (String) entry.getKey() + ": " + ((query.queryHashes.contains((String) entry.getKey())) ? "NEEDED" : "NOT NEEDED") + "; " + ((TreeMap) entry.getValue()).size() + " entries");
        }
        
        TreeMap abstractJoin = (rcAbstracts.size() == query.size()) ? kelondroMSetTools.joinConstructive(rcAbstracts.values(), true) : new TreeMap();
        if (abstractJoin.size() == 0) {
            System.out.println("DEBUG-INDEXABSTRACT: no success using index abstracts from remote peers");
        } else {
            System.out.println("DEBUG-INDEXABSTRACT: index abstracts delivered " + abstractJoin.size() + " additional results for secondary search");
            // generate query for secondary search
            TreeMap secondarySearchURLs = new TreeMap(); // a (peerhash:urlhash-liststring) mapping
            Iterator i1 = abstractJoin.entrySet().iterator();
            Map.Entry entry1;
            String url, urls, peer, peers;
            String mypeerhash = yacyCore.seedDB.mySeed.hash;
            boolean mypeerinvolved = false;
            while (i1.hasNext()) {
                entry1 = (Map.Entry) i1.next();
                url = (String) entry1.getKey();
                peers = (String) entry1.getValue();
                System.out.println("DEBUG-INDEXABSTRACT: url " + url + ": from peers " + peers);
                for (int j = 0; j < peers.length(); j = j + 12) {
                    peer = peers.substring(j, j + 12);
                    if (peers.indexOf(peer) < j) continue; // avoid doubles that may appear in the abstractJoin
                    urls = (String) secondarySearchURLs.get(peer);
                    urls = (urls == null) ? url : urls + url;
                    secondarySearchURLs.put(peer, urls);
                    if (peer.equals(mypeerhash)) mypeerinvolved = true;
                }
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
                System.out.println("DEBUG-INDEXABSTRACT: peer " + peer + "   has urls: " + urls);
                System.out.println("DEBUG-INDEXABSTRACT: peer " + peer + " from words: " + words);
                secondarySearchThreads[c++] = yacySearch.secondaryRemoteSearch(
                        words, urls, urlStore, rcContainers, peer, plasmaSwitchboard.urlBlacklist, snippetCache,
                        profileGlobal, ranking);

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
    
    public Map localSearchContainers(Set urlselection) {
        // search for the set of hashes and return a map of of wordhash:indexContainer containing the seach result

        // retrieve entities that belong to the hashes
        profileLocal.startTimer();
        Map containers = wordIndex.getContainers(
                        query.queryHashes,
                        urlselection,
                        true,
                        true,
                        profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_COLLECTION));
        if (containers.size() < query.size()) containers = null; // prevent that only a subset is returned
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_COLLECTION);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_COLLECTION, (containers == null) ? 0 : containers.size());

        return containers;
    }
    
    public indexContainer localSearchJoin(Collection containers) {
        // join a search result and return the joincount (number of pages after join)

        // since this is a conjunction we return an empty entity if any word is not known
        if (containers == null) {
            return new indexContainer(null);
        }

        // join the result
        profileLocal.startTimer();
        indexContainer rcLocal = indexContainer.joinContainer(containers,
                profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_JOIN),
                query.maxDistance);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_JOIN);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_JOIN, rcLocal.size());

        return rcLocal;
    }
    
    public plasmaSearchResult orderFinal(indexContainer rcLocal) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        indexContainer searchResult = new indexContainer(null);
        long preorderTime = profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_PRESORT);
        
        profileLocal.startTimer();
        long pst = System.currentTimeMillis();
        searchResult.add(rcLocal, preorderTime);
        searchResult.add(rcContainers, preorderTime);
        preorderTime = preorderTime - (System.currentTimeMillis() - pst);
        if (preorderTime < 0) preorderTime = 200;
        plasmaSearchPreOrder preorder = new plasmaSearchPreOrder(query, ranking, searchResult, preorderTime);
        preorder.remove(true, true);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_PRESORT);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_PRESORT, rcLocal.size());
        
        // start url-fetch
        long postorderTime = profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        System.out.println("DEBUG: postorder-final (urlfetch) maxtime = " + postorderTime);
        long postorderLimitTime = (postorderTime < 0) ? Long.MAX_VALUE : (System.currentTimeMillis() + postorderTime);
        profileLocal.startTimer();
        plasmaSearchResult acc = new plasmaSearchResult(query, ranking);
        //if (searchResult == null) return acc; // strange case where searchResult is not proper: acc is then empty
        //if (searchResult.size() == 0) return acc; // case that we have nothing to do

        indexEntry entry;
        plasmaCrawlLURL.Entry page;
        Long preranking;
        Object[] preorderEntry;
        int minEntries = profileLocal.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        try {
            while (preorder.hasNext()) {
                if ((System.currentTimeMillis() >= postorderLimitTime) && (acc.sizeFetched() >= minEntries)) break;
                preorderEntry = preorder.next();
                entry = (indexEntry) preorderEntry[0];
                // load only urls if there was not yet a root url of that hash
                preranking = (Long) preorderEntry[1];
                // find the url entry
                page = urlStore.load(entry.urlHash(), entry);
                // add a result
                if (page != null) acc.addResult(page, preranking);
            }
        } catch (kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_URLFETCH);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_URLFETCH, acc.sizeFetched());

        // start postsorting
        profileLocal.startTimer();
        acc.sortResults(postsort);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_POSTSORT, acc.sizeOrdered());
        
        // apply filter
        profileLocal.startTimer();
        acc.removeRedundant();
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_FILTER);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_FILTER, acc.sizeOrdered());
        
        return acc;
    }
    
    private void prefetchLocal(indexContainer rcLocal, long timeout) {
        // pre-fetch some urls to fill LURL ram cache

        plasmaSearchPreOrder preorder = new plasmaSearchPreOrder(query, ranking, rcLocal, timeout - System.currentTimeMillis());
        preorder.remove(true, true);
        
        // start url-fetch
        indexEntry entry;
        try {
            while (preorder.hasNext()) {
                if (System.currentTimeMillis() >= timeout) break;
                entry = (indexEntry) (preorder.next()[0]);
                // find and fetch the url entry
                urlStore.load(entry.urlHash(), entry);
            }
        } catch (kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
    }
    
    public void run() {
        flushThreads.add(this); // this will care that the search event object is referenced from somewhere while it is still alive

        // put all new results into wordIndex
        // this must be called after search results had been computed
        // it is wise to call this within a separate thread because
        // this method waits until all threads are finished
        serverLog.logFine("PLASMA", "STARTED FLUSHING GLOBAL SEARCH RESULTS FOR SEARCH " + query.queryWords);
        
        int remaining = 0;
        if (primarySearchThreads == null) return;
        long starttime = System.currentTimeMillis();
        while (true) {
            remaining = yacySearch.remainingWaiting(primarySearchThreads);
            if (secondarySearchThreads != null) remaining += yacySearch.remainingWaiting(secondarySearchThreads);
            if (remaining == 0) break;

            flushGlobalResults();
  
            // wait a little bit before trying again
            try {Thread.sleep(1000);} catch (InterruptedException e) {}
            if (System.currentTimeMillis() - starttime > 90000) {
                yacySearch.interruptAlive(primarySearchThreads);
                if (secondarySearchThreads != null) yacySearch.interruptAlive(secondarySearchThreads);
                log.logFine("SEARCH FLUSH: " + remaining + " PEERS STILL BUSY; ABANDONED; SEARCH WAS " + query.queryWords);
                break;
            }
            //log.logFine("FINISHED FLUSH RESULTS PROCESS for query " + query.hashes(","));
        }
        
        serverLog.logFine("PLASMA", "FINISHED FLUSHING " + rcContainerFlushCount + " GLOBAL SEARCH RESULTS FOR SEARCH " + query.queryWords);
            
        // finally delete the temporary index
        rcContainers = null;
        
        flushThreads.remove(this);
    }
    
    public void flushGlobalResults() {
        // flush the rcGlobal as much as is there so far
        // this must be called sometime after search results had been computed
        int count = 0;
        if ((rcContainers != null) && (rcContainers.size() > 0)) {
            synchronized (rcContainers) {
                String wordHash;
                Iterator hashi = query.queryHashes.iterator();
                while (hashi.hasNext()) {
                    wordHash = (String) hashi.next();
                    rcContainers.setWordHash(wordHash);
                    wordIndex.addEntries(rcContainers, System.currentTimeMillis(), true);
                    log.logFine("FLUSHED " + wordHash + ": " + rcContainers.size() + " url entries");
                }
                // the rcGlobal was flushed, empty it
                count += rcContainers.size();
                rcContainers.clear();
            }
        }
        rcContainerFlushCount += count;
    }
    
}
