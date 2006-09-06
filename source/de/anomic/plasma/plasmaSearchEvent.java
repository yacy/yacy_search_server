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

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.io.IOException;

import de.anomic.kelondro.kelondroException;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySearch;
import de.anomic.index.indexContainer;
import de.anomic.index.indexEntry;
import de.anomic.index.indexRowSetContainer;

public final class plasmaSearchEvent extends Thread implements Runnable {
    
    public static plasmaSearchEvent lastEvent = null;

    private static HashSet flushThreads = new HashSet();
    
    private serverLog log;
    private plasmaSearchQuery query;
    private plasmaSearchRankingProfile ranking;
    private plasmaWordIndex wordIndex;
    private plasmaCrawlLURL urlStore;
    private plasmaSnippetCache snippetCache;
    private indexContainer rcGlobal; // cache for results
    private int rcGlobalCount;
    private plasmaSearchTimingProfile profileLocal, profileGlobal;
    private yacySearch[] searchThreads;
    
    public plasmaSearchEvent(plasmaSearchQuery query,
                             plasmaSearchRankingProfile ranking,
                             plasmaSearchTimingProfile localTiming,
                             plasmaSearchTimingProfile remoteTiming,
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
        this.rcGlobal = new indexRowSetContainer(null);
        this.rcGlobalCount = 0;
        this.profileLocal = localTiming;
        this.profileGlobal = remoteTiming;
        this.searchThreads = null;
    }
    
    public plasmaSearchQuery getQuery() {
        return query;
    }
    
    public plasmaSearchTimingProfile getLocalTiming() {
        return profileLocal;
    }
    
    public yacySearch[] getSearchThreads() {
        return searchThreads;
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
                if (fetchpeers < 10) fetchpeers = 10;

                log.logFine("STARTING " + fetchpeers + " THREADS TO CATCH EACH " + profileGlobal.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT) + " URLs WITHIN " + (profileGlobal.duetime() / 1000) + " SECONDS");

                long timeout = System.currentTimeMillis() + profileGlobal.duetime();
                searchThreads = yacySearch.searchHashes(query.queryHashes, query.prefer, query.urlMask, query.maxDistance, urlStore, rcGlobal, fetchpeers, plasmaSwitchboard.urlBlacklist, snippetCache, profileGlobal, ranking);

                // meanwhile do a local search
                indexContainer rcLocal = localSearchJoin(localSearchContainers());
                plasmaSearchResult localResult = orderLocal(rcLocal, timeout);
                
                // catch up global results:
                // wait until wanted delay passed or wanted result appeared
                while (System.currentTimeMillis() < timeout) {
                    // check if all threads have been finished or results so far are enough
                    //if (rcGlobal.size() >= profileGlobal.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT) * 5) break; // we have enough
                    if (yacySearch.remainingWaiting(searchThreads) == 0) break; // we cannot expect more
                    // wait a little time ..
                    try {Thread.sleep(100);} catch (InterruptedException e) {}
                }
                int globalContributions = rcGlobal.size();
                
                // finished searching
                log.logFine("SEARCH TIME AFTER GLOBAL-TRIGGER TO " + fetchpeers + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");

                // combine the result and order
                plasmaSearchResult result = ((globalContributions == 0) && (localResult.sizeOrdered() != 0)) ? localResult : order(rcLocal);
                result.globalContributions = globalContributions;
                result.localContributions = rcLocal.size();
                
                // flush results in a separate thread
                this.start(); // start to flush results

                // return search result
                log.logFine("SEARCHRESULT: " + profileLocal.reportToString());
                lastEvent = this;
                return result;
            } else {
                indexContainer rcLocal = localSearchJoin(localSearchContainers());
                plasmaSearchResult result = order(rcLocal);
                result.localContributions = rcLocal.size();

                // return search result
                log.logFine("SEARCHRESULT: " + profileLocal.reportToString());
                lastEvent = this;
                return result;
            }
        }
    }

    public Set localSearchContainers() {
        // search for the set of hashes and return the set of containers containing the seach result

        // retrieve entities that belong to the hashes
        profileLocal.startTimer();
        Set containers = wordIndex.getContainers(
                        query.queryHashes,
                        true,
                        true,
                        profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_COLLECTION));
        if (containers.size() < query.size()) containers = null; // prevent that only a subset is returned
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_COLLECTION);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_COLLECTION, (containers == null) ? 0 : containers.size());

        return containers;
    }
    
    public indexContainer localSearchJoin(Set containers) {
        // join a search result and return the joincount (number of pages after join)

        // since this is a conjunction we return an empty entity if any word is not known
        if (containers == null) {
            return new indexRowSetContainer(null);
        }

        // join the result
        profileLocal.startTimer();
        indexContainer rcLocal = indexRowSetContainer.joinContainer(containers,
                profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_JOIN),
                query.maxDistance);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_JOIN);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_JOIN, rcLocal.size());

        return rcLocal;
    }
    
    public plasmaSearchResult order(indexContainer rcLocal) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        indexContainer searchResult = new indexRowSetContainer(null);
        long preorderTime = profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_PRESORT);
        
        profileLocal.startTimer();
        long pst = System.currentTimeMillis();
        searchResult.add(rcLocal, preorderTime);
        searchResult.add(rcGlobal, preorderTime);
        preorderTime = preorderTime - (System.currentTimeMillis() - pst);
        if (preorderTime < 0) preorderTime = 200;
        plasmaSearchPreOrder preorder = new plasmaSearchPreOrder(query, ranking);
        preorder.addContainer(searchResult, preorderTime);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_PRESORT);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_PRESORT, rcLocal.size());
        
        // start url-fetch
        long postorderTime = profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        long postorderLimitTime = (postorderTime < 0) ? Long.MAX_VALUE : (System.currentTimeMillis() + postorderTime);
        profileLocal.startTimer();
        plasmaSearchResult acc = new plasmaSearchResult(query, ranking);
        //if (searchResult == null) return acc; // strange case where searchResult is not proper: acc is then empty
        //if (searchResult.size() == 0) return acc; // case that we have nothing to do

        indexEntry entry;
        plasmaCrawlLURL.Entry page;
        int minEntries = profileLocal.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        try {
            while (preorder.hasNext()) {
                //if ((acc.sizeFetched() >= 50) && ((acc.sizeFetched() >= minEntries) || (System.currentTimeMillis() >= postorderLimitTime))) break;
                if (acc.sizeFetched() >= minEntries) break;
                if (System.currentTimeMillis() >= postorderLimitTime) break;
                entry = preorder.next();
                // find the url entry
                try {
                    page = urlStore.getEntry(entry.urlHash(), entry);
                    // add a result
                    acc.addResult(entry, page);
                } catch (IOException e) {
                    // result was not found
                }
            }
        } catch (kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_URLFETCH);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_URLFETCH, acc.sizeFetched());

        // start postsorting
        profileLocal.startTimer();
        acc.sortResults();
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_POSTSORT, acc.sizeOrdered());
        
        // apply filter
        profileLocal.startTimer();
        //acc.removeRedundant();
        acc.removeDoubleDom();
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_FILTER);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_FILTER, acc.sizeOrdered());
        
        return acc;
    }
    
    private plasmaSearchResult orderLocal(indexContainer rcLocal, long maxtime) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        profileLocal.startTimer();
        if (maxtime < 0) maxtime = 200;
        plasmaSearchPreOrder preorder = new plasmaSearchPreOrder(query, ranking);
        preorder.addContainer(rcLocal, maxtime);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_PRESORT);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_PRESORT, rcLocal.size());
        
        // start url-fetch
        maxtime = Math.max(200, maxtime - profileLocal.getYieldTime(plasmaSearchTimingProfile.PROCESS_PRESORT));
        long postorderLimitTime = System.currentTimeMillis() + maxtime;
        profileLocal.startTimer();
        plasmaSearchResult acc = new plasmaSearchResult(query, ranking);

        indexEntry entry;
        plasmaCrawlLURL.Entry page;
        int minEntries = profileLocal.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        try {
            while (preorder.hasNext()) {
                //if ((acc.sizeFetched() >= 50) && ((acc.sizeFetched() >= minEntries) || (System.currentTimeMillis() >= postorderLimitTime))) break;
                if (acc.sizeFetched() >= minEntries) break;
                if (System.currentTimeMillis() >= postorderLimitTime) break;
                entry = preorder.next();
                // find the url entry
                try {
                    page = urlStore.getEntry(entry.urlHash(), entry);
                    // add a result
                    acc.addResult(entry, page);
                } catch (IOException e) {
                    // result was not found
                }
            }
        } catch (kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_URLFETCH);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_URLFETCH, acc.sizeFetched());

        // start postsorting
        profileLocal.startTimer();
        acc.sortResults();
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_POSTSORT, acc.sizeOrdered());
        
        // apply filter
        profileLocal.startTimer();
        //acc.removeRedundant();
        acc.removeDoubleDom();
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_FILTER);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_FILTER, acc.sizeOrdered());
        
        return acc;
    }
    
    public void run() {
        flushThreads.add(this); // this will care that the search event object is referenced from somewhere while it is still alive

        // put all new results into wordIndex
        // this must be called after search results had been computed
        // it is wise to call this within a separate thread because
        // this method waits until all threads are finished

        int remaining;
        long starttime = System.currentTimeMillis();
        while ((searchThreads != null) && ((remaining = yacySearch.remainingWaiting(searchThreads)) > 0)) {
            flushGlobalResults();
  
            // wait a little bit before trying again
            try {Thread.sleep(3000);} catch (InterruptedException e) {}
            if (System.currentTimeMillis() - starttime > 90000) {
                yacySearch.interruptAlive(searchThreads);
                log.logFine("SEARCH FLUSH: " + remaining + " PEERS STILL BUSY; ABANDONED; SEARCH WAS " + query.queryWords);
                break;
            }
            //log.logFine("FINISHED FLUSH RESULTS PROCESS for query " + query.hashes(","));
        }
        
        serverLog.logFine("PLASMA", "FINISHED FLUSHING " + rcGlobalCount + " GLOBAL SEARCH RESULTS FOR SEARCH " + query.queryWords);
            
        // finally delete the temporary index
        rcGlobal = null;
        
        flushThreads.remove(this);
    }
    
    public void flushGlobalResults() {
        // flush the rcGlobal as much as is there so far
        // this must be called sometime after search results had been computed
        int count = 0;
        if ((rcGlobal != null) && (rcGlobal.size() > 0)) {
            synchronized (rcGlobal) {
                String wordHash;
                Iterator hashi = query.queryHashes.iterator();
                boolean dhtCache = false;
                while (hashi.hasNext()) {
                    wordHash = (String) hashi.next();
                    rcGlobal.setWordHash(wordHash);
                    dhtCache = dhtCache | wordIndex.busyCacheFlush;
                    wordIndex.addEntries(rcGlobal, System.currentTimeMillis(), dhtCache);
                    log.logFine("FLUSHED " + wordHash + ": " + rcGlobal.size() + " url entries to " + ((dhtCache) ? "DHT cache" : "word cache"));
                }
                // the rcGlobal was flushed, empty it
                count += rcGlobal.size();
                rcGlobal.clear();
            }
        }
        rcGlobalCount += count;
    }
    
}
