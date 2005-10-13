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
import java.util.TreeMap;
import java.util.Enumeration;
import java.io.IOException;

import de.anomic.kelondro.kelondroException;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverCodings;
import de.anomic.server.serverInstantThread;
import de.anomic.yacy.yacySearch;

public final class plasmaSearchEvent {
    
    private serverLog log;
    private plasmaSearchQuery query;
    private plasmaWordIndex wordIndex;
    private plasmaCrawlLURL urlStore;
    private plasmaSnippetCache snippetCache;
    private plasmaWordIndexEntity rcLocal, rcGlobal; // caches for results
    private yacySearch[] searchThreads;
    
    public plasmaSearchEvent(plasmaSearchQuery query, serverLog log, plasmaWordIndex wordIndex, plasmaCrawlLURL urlStore, plasmaSnippetCache snippetCache) {
        this.log = log;
        this.wordIndex = wordIndex;
        this.query = query;
        this.urlStore = urlStore;
        this.snippetCache = snippetCache;
        this.rcLocal = new plasmaWordIndexEntity(null);
        this.rcGlobal = new plasmaWordIndexEntity(null);
        this.searchThreads = null;
    }
    
    public plasmaSearchResult search() {
        // combine all threads
        
        if (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) {
            int fetchcount = ((int) (query.maximumTime / 1000L)) * 5; // number of wanted results until break in search
            int fetchpeers = ((int) (query.maximumTime / 1000L)) * 2; // number of target peers; means 30 peers in 10 seconds
            long fetchtime = query.maximumTime * 6 / 10;           // time to waste
            
            // remember time
            long start = System.currentTimeMillis();
            
            // first trigger a local search within a separate thread
            serverInstantThread.oneTimeJob(this, "localSearch", log, 0);
        
            // do a global search
            int globalContributions = globalSearch(fetchcount, fetchpeers, fetchtime);
            log.logFine("SEARCH TIME AFTER GLOBAL-TRIGGER TO " + fetchpeers + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
            
            try {
                // combine the result and order
                long remainingTime = query.maximumTime - (System.currentTimeMillis() - start);
                if (remainingTime < 500) remainingTime = 500;
                if (remainingTime > 3000) remainingTime = 3000;
            
                plasmaSearchResult result = order(remainingTime, query.wantedResults);
                result.globalContributions = globalContributions;
                result.localContributions = rcLocal.size();
                
                // flush results in a separate thread
                serverInstantThread.oneTimeJob(this, "flushResults", log, 0);
                
                // clean up
                if ((rcLocal != null) && (!(rcLocal.isTMPEntity()))) rcLocal.close();
                rcLocal = null;
                
                // return search result
                return result;
            } catch (IOException e) {
                return null;
            }
        } else {
            // do a local search
            long start = System.currentTimeMillis();
            try {
                localSearch(query.maximumTime);
                plasmaSearchResult result = order(query.maximumTime - (System.currentTimeMillis() - start), query.wantedResults);
                result.localContributions = rcLocal.size();
                
                // clean up
                if ((rcLocal != null) && (!(rcLocal.isTMPEntity()))) rcLocal.close();
                rcLocal = null;
                
                return result;
            } catch (IOException e) {
                return null;
            }
        }
    }
    
    
    public void localSearch() throws IOException {
        // method called by a one-time
        localSearch(query.maximumTime * 6 / 10);
    }
    
    public int localSearch(long time) throws IOException {
        // search for the set of hashes and return an array of urlEntry elements
        
        long stamp = System.currentTimeMillis();
        
        // retrieve entities that belong to the hashes
        Set entities = wordIndex.getEntities(query.queryHashes, true, true);
        
        // since this is a conjunction we return an empty entity if any word is not known
        if (entities == null) {
            rcLocal = new plasmaWordIndexEntity(null);
            return 0;
        }
        
        // join the result
        long remainingTime = time - (System.currentTimeMillis() - stamp);
        if (remainingTime < 1000) remainingTime = 1000;
        rcLocal = plasmaWordIndexEntity.joinEntities(entities, remainingTime);
        log.logFine("SEARCH TIME FOR FINDING " + rcLocal.size() + " ELEMENTS: " + ((System.currentTimeMillis() - stamp) / 1000) + " seconds");
            
        return rcLocal.size();
    }
    
    public int globalSearch(int fetchcount, int fetchpeers, long timelimit) {
        // do global fetching
        // the result of the fetch is then in the rcGlobal
        if (fetchpeers < 10) fetchpeers = 10;
        if (fetchcount > query.wantedResults * 10) fetchcount = query.wantedResults * 10;
        
        // set a duetime for clients
        long duetime = timelimit - 4000; // subtract network traffic overhead, guessed 4 seconds
        if (duetime < 1000) { duetime = 1000; }
        
        long timeout = System.currentTimeMillis() + timelimit;
        searchThreads = yacySearch.searchHashes(query.queryHashes, urlStore, rcGlobal, fetchcount, fetchpeers, plasmaSwitchboard.urlBlacklist, snippetCache, duetime);
        
        // wait until wanted delay passed or wanted result appeared
        while (System.currentTimeMillis() < timeout) {
            // check if all threads have been finished or results so far are enough
            if (rcGlobal.size() >= fetchcount * 3) break; // we have enough
            if (yacySearch.remainingWaiting(searchThreads) == 0) break; // we cannot expect more
            // wait a little time ..
            try {Thread.currentThread().sleep(100);} catch (InterruptedException e) {}
        }
        
        return rcGlobal.size();
    }
    
    public plasmaSearchResult order(long maxTime, int minEntries) throws IOException {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        plasmaWordIndexEntity searchResult = new plasmaWordIndexEntity(null);
        searchResult.merge(rcLocal, -1);
        searchResult.merge(rcGlobal, -1);
        
	plasmaSearchResult acc = new plasmaSearchResult(query);
	if (searchResult == null) return acc; // strange case where searchResult is not proper: acc is then empty
        if (searchResult.size() == 0) return acc; // case that we have nothing to do
        
	Iterator e = searchResult.elements(true);
	plasmaWordIndexEntry entry;
        long startCreateTime = System.currentTimeMillis();
        plasmaCrawlLURL.Entry page;
	try {
	    while (e.hasNext()) {
                if ((acc.sizeFetched() >= minEntries) &&
                    (System.currentTimeMillis() - startCreateTime >= maxTime)) break;
                entry = (plasmaWordIndexEntry) e.next();
                // find the url entry
                page = urlStore.getEntry(entry.getUrlHash());
                // add a result
		acc.addResult(entry, page);
	    }
	} catch (kelondroException ee) {
	    serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
	}
        long startSortTime = System.currentTimeMillis();
        acc.sortResults();
        serverLog.logFine("PLASMA", "plasmaSearchEvent.order: minEntries = " + minEntries + ", effectiveEntries = " + acc.sizeOrdered() + ", demanded Time = " + maxTime + ", effectiveTime = " + (System.currentTimeMillis() - startCreateTime) + ", createTime = " + (startSortTime - startCreateTime) + ", sortTime = " + (System.currentTimeMillis() - startSortTime));
	return acc;
    }
    
    public void flushResults() {
        // put all new results into wordIndex
        // this must be called after search results had been computed
        // it is wise to call this within a separate thread because this method waits untill all 
        if (searchThreads == null) return;
        
        // wait untill all threads are finished
        int remaining;
        long starttime = System.currentTimeMillis();
        while ((remaining = yacySearch.remainingWaiting(searchThreads)) > 0) {
            try {Thread.currentThread().sleep(5000);} catch (InterruptedException e) {}
            if (System.currentTimeMillis() - starttime > 90000) {
                yacySearch.interruptAlive(searchThreads);
                serverLog.logFine("PLASMA", "SEARCH FLUSH: " + remaining + " PEERS STILL BUSY; ABANDONED");
                break;
            }
        }
        
        // now flush the rcGlobal into wordIndex
        Iterator hashi = query.queryHashes.iterator();
        String wordHash;
        while (hashi.hasNext()) {
            wordHash = (String) hashi.next();
            Iterator i = rcGlobal.elements(true);
            plasmaWordIndexEntry entry;
            while (i.hasNext()) {
                entry = (plasmaWordIndexEntry) i.next();
                wordIndex.addEntries(plasmaWordIndexEntryContainer.instantContainer(wordHash, System.currentTimeMillis(), entry), false);
            }
        }
        serverLog.logFine("PLASMA", "FINISHED FLUSHING " + rcGlobal.size() + " GLOBAL SEARCH RESULTS");
	        
        // finally delete the temporary index
        rcGlobal = null;
    }
    
    
    /*
    public void preSearch() {
        plasmaWordIndexEntity idx = null;
        try {
            // search the database locally
            log.logFine("presearch: started job");
            idx = searchHashes(query.queryHashes, time);
            log.logFine("presearch: found " + idx.size() + " results");
            plasmaSearchResult acc = order(idx, queryhashes, order, time, searchcount);
            if (acc == null) return;
            log.logFine("presearch: ordered results, now " + acc.sizeOrdered() + " URLs ready for fetch");
            
            // take some elements and fetch the snippets
            snippetCache.fetch(acc, queryhashes, urlmask, fetchcount);
        } catch (IOException e) {
            log.logSevere("presearch: failed", e);
        } finally {
            if (idx != null) try { idx.close(); } catch (Exception e){}
        }
        log.logFine("presearch: job terminated");
    }
    */
}
