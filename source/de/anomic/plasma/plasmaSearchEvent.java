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
import java.io.IOException;

import de.anomic.kelondro.kelondroException;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverInstantThread;
import de.anomic.yacy.yacySearch;

public final class plasmaSearchEvent {
    
    public static plasmaSearchEvent lastEvent = null;
    
    private serverLog log;
    private plasmaSearchQuery query;
    private plasmaWordIndex wordIndex;
    private plasmaCrawlLURL urlStore;
    private plasmaSnippetCache snippetCache;
    private plasmaWordIndexEntity rcLocal, rcGlobal; // caches for results
    private plasmaSearchProfile profileLocal, profileGlobal;
    private yacySearch[] searchThreads;
    
    public plasmaSearchEvent(plasmaSearchQuery query, serverLog log, plasmaWordIndex wordIndex, plasmaCrawlLURL urlStore, plasmaSnippetCache snippetCache) {
        this.log = log;
        this.wordIndex = wordIndex;
        this.query = query;
        this.urlStore = urlStore;
        this.snippetCache = snippetCache;
        this.rcLocal = new plasmaWordIndexEntity(null);
        this.rcGlobal = new plasmaWordIndexEntity(null);
        if (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) {
            this.profileLocal  = new plasmaSearchProfile(4 * query.maximumTime / 10, query.wantedResults);
            this.profileGlobal = new plasmaSearchProfile(6 * query.maximumTime / 10, query.wantedResults);
        } else {
            this.profileLocal = new plasmaSearchProfile(query.maximumTime, query.wantedResults);
            this.profileGlobal = null;
        }
        this.searchThreads = null;
    }
    
    public plasmaSearchQuery getQuery() {
        return query;
    }
    
    public plasmaSearchProfile getLocalProfile() {
        return profileLocal;
    }
    
    public yacySearch[] getSearchThreads() {
        return searchThreads;
    }
    
    public plasmaSearchResult search() {
        // combine all threads
        
        if (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) {
            int fetchpeers = (int) (query.maximumTime / 1000L); // number of target peers; means 10 peers in 10 seconds
            if (fetchpeers > 10) fetchpeers = 10;
            
            // remember time
            long start = System.currentTimeMillis();
            
            // first trigger a local search within a separate thread
            serverInstantThread.oneTimeJob(this, "localSearch", log, 0);
        
            // do a global search
            int globalContributions = globalSearch(fetchpeers);
            log.logFine("SEARCH TIME AFTER GLOBAL-TRIGGER TO " + fetchpeers + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
            
            try {
                // combine the result and order
                plasmaSearchResult result = order();
                result.globalContributions = globalContributions;
                result.localContributions = rcLocal.size();
                
                // flush results in a separate thread
                serverInstantThread.oneTimeJob(this, "flushResults", log, 0);
                
                // clean up
                if ((rcLocal != null) && (!(rcLocal.isTMPEntity()))) rcLocal.close();
                rcLocal = null;
                
                // return search result
                log.logFine("SEARCHRESULT: " + profileLocal.reportToString());
                lastEvent = this;
                return result;
            } catch (IOException e) {
                return null;
            }
        } else {
            // do a local search
            //long start = System.currentTimeMillis();
            try {
                localSearch();
                plasmaSearchResult result = order();
                result.localContributions = rcLocal.size();
                
                // clean up
                if ((rcLocal != null) && (!(rcLocal.isTMPEntity()))) rcLocal.close();
                rcLocal = null;
                
                // return search result
                log.logFine("SEARCHRESULT: " + profileLocal.reportToString());
                lastEvent = this;
                return result;
            } catch (IOException e) {
                return null;
            }
        }
    }
    
    public int localSearch() throws IOException {
        // search for the set of hashes and return an array of urlEntry elements
        
        // retrieve entities that belong to the hashes
        profileLocal.startTimer();
        Set entities = wordIndex.getEntities(query.queryHashes, true, true, profileLocal.getTargetTime(plasmaSearchProfile.PROCESS_COLLECTION));
        profileLocal.setYieldTime(plasmaSearchProfile.PROCESS_COLLECTION);
        profileLocal.setYieldCount(plasmaSearchProfile.PROCESS_COLLECTION, (entities == null) ? 0 : entities.size());
        
        // since this is a conjunction we return an empty entity if any word is not known
        if (entities == null) {
            rcLocal = new plasmaWordIndexEntity(null);
            return 0;
        }
        
        // join the result
        profileLocal.startTimer();
        rcLocal = plasmaWordIndexEntity.joinEntities(entities, profileLocal.getTargetTime(plasmaSearchProfile.PROCESS_JOIN));
        profileLocal.setYieldTime(plasmaSearchProfile.PROCESS_JOIN);
        profileLocal.setYieldCount(plasmaSearchProfile.PROCESS_JOIN, rcLocal.size());
        
        return rcLocal.size();
    }
    
    public int globalSearch(int fetchpeers) {
        // do global fetching
        // the result of the fetch is then in the rcGlobal
        if (fetchpeers < 10) fetchpeers = 10;

        log.logFine("STARTING " + fetchpeers + " THREADS TO CATCH EACH " + profileGlobal.getTargetCount(plasmaSearchProfile.PROCESS_POSTSORT) + " URLs WITHIN " + (profileGlobal.duetime() / 1000) + " SECONDS");
        
        long timeout = System.currentTimeMillis() + profileGlobal.duetime() + 4000;
        searchThreads = yacySearch.searchHashes(query.queryHashes, urlStore, rcGlobal, fetchpeers, plasmaSwitchboard.urlBlacklist, snippetCache, profileGlobal);
        
        // wait until wanted delay passed or wanted result appeared
        while (System.currentTimeMillis() < timeout) {
            // check if all threads have been finished or results so far are enough
            if (rcGlobal.size() >= profileGlobal.getTargetCount(plasmaSearchProfile.PROCESS_POSTSORT) * 3) break; // we have enough
            if (yacySearch.remainingWaiting(searchThreads) == 0) break; // we cannot expect more
            // wait a little time ..
            try {Thread.sleep(100);} catch (InterruptedException e) {}
        }
        
        return rcGlobal.size();
    }
    
    public plasmaSearchResult order() throws IOException {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        plasmaWordIndexEntity searchResult = new plasmaWordIndexEntity(null);
        searchResult.merge(rcLocal, -1);
        searchResult.merge(rcGlobal, -1);
        
        long preorderTime = profileLocal.getTargetTime(plasmaSearchProfile.PROCESS_PRESORT);
        long postorderTime = profileLocal.getTargetTime(plasmaSearchProfile.PROCESS_POSTSORT);
        
        profileLocal.startTimer();
        plasmaSearchPreOrder preorder = new plasmaSearchPreOrder(query);
        preorder.addEntity(searchResult, preorderTime);
        profileLocal.setYieldTime(plasmaSearchProfile.PROCESS_PRESORT);
        profileLocal.setYieldCount(plasmaSearchProfile.PROCESS_PRESORT, rcLocal.size());
        
        profileLocal.startTimer();
        plasmaSearchResult acc = new plasmaSearchResult(query);
        if (searchResult == null) return acc; // strange case where searchResult is not proper: acc is then empty
        if (searchResult.size() == 0) return acc; // case that we have nothing to do
        
        // start url-fetch
        plasmaWordIndexEntry entry;
        long postorderLimitTime = (postorderTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + postorderTime;
        plasmaCrawlLURL.Entry page;
        int minEntries = profileLocal.getTargetCount(plasmaSearchProfile.PROCESS_POSTSORT);
        try {
            while (preorder.hasNext()) {
                if ((acc.sizeFetched() >= minEntries) && (System.currentTimeMillis() >= postorderLimitTime)) break;
                entry = preorder.next();
                // find the url entry
                try {
                    page = urlStore.getEntry(entry.getUrlHash());
                    // add a result
                    acc.addResult(entry, page);
                } catch (IOException e) {
                    // result was not found
                }
            }
        } catch (kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
        profileLocal.setYieldTime(plasmaSearchProfile.PROCESS_URLFETCH);
        profileLocal.setYieldCount(plasmaSearchProfile.PROCESS_URLFETCH, acc.sizeFetched());

        // start postsorting
        profileLocal.startTimer();
        acc.sortResults();
        profileLocal.setYieldTime(plasmaSearchProfile.PROCESS_POSTSORT);
        profileLocal.setYieldCount(plasmaSearchProfile.PROCESS_POSTSORT, acc.sizeOrdered());
        
        // apply filter
        profileLocal.startTimer();
        acc.removeDoubleDom();
        //acc.removeRedundant();
        profileLocal.setYieldTime(plasmaSearchProfile.PROCESS_FILTER);
        profileLocal.setYieldCount(plasmaSearchProfile.PROCESS_FILTER, acc.sizeOrdered());
        
        return acc;
    }
    
    public void flushResults() {
        // put all new results into wordIndex
        // this must be called after search results had been computed
        // it is wise to call this within a separate thread because this method waits untill all 
        if (searchThreads == null) return;

        // wait until all threads are finished
        int remaining;
        int count = 0;
        String wordHash;
        long starttime = System.currentTimeMillis();
        while ((remaining = yacySearch.remainingWaiting(searchThreads)) > 0) {
            // flush the rcGlobal as much as is there so far
            synchronized (rcGlobal) {
                Iterator hashi = query.queryHashes.iterator();
                while (hashi.hasNext()) {
                    wordHash = (String) hashi.next();
                    Iterator i = rcGlobal.elements(true);
                    plasmaWordIndexEntry entry;
                    while (i.hasNext()) {
                        entry = (plasmaWordIndexEntry) i.next();
                        wordIndex.addEntries(plasmaWordIndexEntryContainer.instantContainer(wordHash, System.currentTimeMillis(), entry), false);
                    }
                }
                // the rcGlobal was flushed, empty it
                count += rcGlobal.size();
                rcGlobal.deleteComplete();
            }    
            // wait a little bit before trying again
            try {Thread.sleep(3000);} catch (InterruptedException e) {}
            if (System.currentTimeMillis() - starttime > 90000) {
                yacySearch.interruptAlive(searchThreads);
                serverLog.logFine("PLASMA", "SEARCH FLUSH: " + remaining + " PEERS STILL BUSY; ABANDONED; SEARCH WAS " + query.queryWords);
                break;
            }
        }
        
        serverLog.logFine("PLASMA", "FINISHED FLUSHING " + count + " GLOBAL SEARCH RESULTS FOR SEARCH " + query.queryWords);
	        
        // finally delete the temporary index
        rcGlobal = null;
    }
    
}
