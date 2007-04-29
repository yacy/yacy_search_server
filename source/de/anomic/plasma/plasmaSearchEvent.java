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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
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
    private long searchtime;
    private int searchcount;
    private TreeMap preselectedPeerHashes;
    
    public plasmaSearchEvent(plasmaSearchQuery query,
                             plasmaSearchRankingProfile ranking,
                             plasmaSearchTimingProfile localTiming,
                             plasmaSearchTimingProfile remoteTiming,
                             boolean postsort,
                             serverLog log,
                             plasmaWordIndex wordIndex,
                             plasmaCrawlLURL urlStore,
                             plasmaSnippetCache snippetCache,
                             TreeMap preselectedPeerHashes) {
        this.log = log;
        this.wordIndex = wordIndex;
        this.query = query;
        this.ranking = ranking;
        this.urlStore = urlStore;
        this.snippetCache = snippetCache;
        this.rcContainers = wordIndex.emptyContainer(null);
        this.rcContainerFlushCount = 0;
        this.rcAbstracts = (query.queryHashes.size() > 1) ? new TreeMap() : null; // generate abstracts only for combined searches
        this.profileLocal = localTiming;
        this.profileGlobal = remoteTiming;
        this.postsort = postsort;
        this.primarySearchThreads = null;
        this.secondarySearchThreads = null;
        this.searchtime = -1;
        this.searchcount = -1;
        this.preselectedPeerHashes = preselectedPeerHashes;
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
    
    public HashMap resultProfile() {
        // generate statistics about search: query, time, etc
        HashMap r = new HashMap();
        r.put("queryhashes", query.queryHashes);
        r.put("querystring", query.queryString);
        r.put("querycount", new Integer(query.wantedResults));
        r.put("querytime", new Long(query.maximumTime));
        r.put("resultcount", new Integer(this.searchcount));
        r.put("resulttime", new Long(this.searchtime));
        return r;
    }
    
    public plasmaSearchPostOrder search() {
        // combine all threads
        
        // we synchronize with flushThreads to allow only one local search at a time,
        // so all search tasks are queued
        synchronized (flushThreads) {
            long start = System.currentTimeMillis();
            plasmaSearchPostOrder result;
            if ((query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) ||
                (query.domType == plasmaSearchQuery.SEARCHDOM_CLUSTERALL)) {
                int fetchpeers = (int) (query.maximumTime / 500L); // number of target peers; means 10 peers in 10 seconds
                if (fetchpeers > 50) fetchpeers = 50;
                if (fetchpeers < 30) fetchpeers = 30;

                // do a global search
                // the result of the fetch is then in the rcGlobal
                log.logFine("STARTING " + fetchpeers + " THREADS TO CATCH EACH " + profileGlobal.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT) + " URLs WITHIN " + (profileGlobal.duetime() / 1000) + " SECONDS");
                long secondaryTimeout = System.currentTimeMillis() + profileGlobal.duetime() / 3 * 2;
                long primaryTimeout = System.currentTimeMillis() + profileGlobal.duetime();
                primarySearchThreads = yacySearch.primaryRemoteSearches(plasmaSearchQuery.hashSet2hashString(query.queryHashes), plasmaSearchQuery.hashSet2hashString(query.excludeHashes), "",
                        query.prefer, query.urlMask, query.maxDistance, urlStore, wordIndex, rcContainers, rcAbstracts,
                        fetchpeers, plasmaSwitchboard.urlBlacklist, snippetCache, profileGlobal, ranking, query.constraint,
                        (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) ? null : preselectedPeerHashes);

                // meanwhile do a local search
                Map[] searchContainerMaps = localSearchContainers(null);
                
                // use the search containers to fill up rcAbstracts locally
                /*
                if ((rcAbstracts != null) && (searchContainerMap != null)) {
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
                */
                
                // try to pre-fetch some LURLs if there is enough time
                indexContainer rcLocal = localSearchJoinExclude(searchContainerMaps[0].values(), searchContainerMaps[1].values());
                prefetchLocal(rcLocal, secondaryTimeout);
                
                // this is temporary debugging code to learn that the index abstracts are fetched correctly
                while (System.currentTimeMillis() < secondaryTimeout) {
                    if (yacySearch.remainingWaiting(primarySearchThreads) == 0) break; // all threads have finished
                    try {Thread.sleep(100);} catch (InterruptedException e) {}
                }
                // evaluate index abstracts and start a secondary search
                if (rcAbstracts != null) prepareSecondarySearch();
                
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
                result = orderFinal(rcLocal);
                if (result != null) {
                    result.globalContributions = globalContributions;

                    // flush results in a separate thread
                    this.start(); // start to flush results
                }
            } else {
                Map[] searchContainerMaps = localSearchContainers(null);
                indexContainer rcLocal = (searchContainerMaps == null) ? wordIndex.emptyContainer(null) : localSearchJoinExclude(searchContainerMaps[0].values(), searchContainerMaps[1].values());
                result = orderFinal(rcLocal);
                result.globalContributions = 0;
            }

            // log the event
            log.logFine("SEARCHRESULT: " + profileLocal.reportToString());
            
            // prepare values for statistics
            lastEvent = this;
            this.searchtime = System.currentTimeMillis() - start;
            this.searchcount = result.filteredResults;
            
            // return search result
            return result;
        }
    }

    private void prepareSecondarySearch() {
        // catch up index abstracts and join them; then call peers again to submit their urls
        System.out.println("DEBUG-INDEXABSTRACT: " + rcAbstracts.size() + " word references catched, " + query.queryHashes.size() + " needed");
        
        if (rcAbstracts.size() != query.queryHashes.size()) return; // secondary search not possible
        
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
            String mypeerhash = yacyCore.seedDB.mySeed.hash;
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
                        words, "", urls, urlStore, wordIndex, rcContainers, peer, plasmaSwitchboard.urlBlacklist, snippetCache,
                        profileGlobal, ranking, query.constraint);

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
    
    public Map[] localSearchContainers(Set urlselection) {
        // search for the set of hashes and return a map of of wordhash:indexContainer containing the seach result

        // retrieve entities that belong to the hashes
        profileLocal.startTimer();
        long start = System.currentTimeMillis();
        Map inclusionContainers = wordIndex.getContainers(
                        query.queryHashes,
                        urlselection,
                        true,
                        true,
                        profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_COLLECTION) * query.queryHashes.size() / (query.queryHashes.size() + query.excludeHashes.size()));
        if ((inclusionContainers.size() != 0) && (inclusionContainers.size() < query.queryHashes.size())) inclusionContainers = new HashMap(); // prevent that only a subset is returned
        long remaintime =  profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_COLLECTION) - System.currentTimeMillis() + start;
        Map exclusionContainers = ((inclusionContainers == null) || (inclusionContainers.size() == 0) || (remaintime <= 0)) ? new HashMap() : wordIndex.getContainers(
                query.excludeHashes,
                urlselection,
                true,
                true,
                remaintime);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_COLLECTION);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_COLLECTION, inclusionContainers.size());

        return new Map[]{inclusionContainers, exclusionContainers};
    }
    
    public indexContainer localSearchJoinExclude(Collection includeContainers, Collection excludeContainers) {
        // join a search result and return the joincount (number of pages after join)

        // since this is a conjunction we return an empty entity if any word is not known
        if (includeContainers == null) return wordIndex.emptyContainer(null);

        // join the result
        profileLocal.startTimer();
        long start = System.currentTimeMillis();
        indexContainer rcLocal = indexContainer.joinContainers(includeContainers,
                profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_JOIN) * query.queryHashes.size() / (query.queryHashes.size() + query.excludeHashes.size()),
                query.maxDistance);
        long remaining = profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_JOIN) - System.currentTimeMillis() + start;
        if ((rcLocal != null) && (remaining > 0)) {
        	indexContainer.excludeContainers(rcLocal, excludeContainers, remaining);
        }
        if (rcLocal == null) rcLocal = wordIndex.emptyContainer(null);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_JOIN);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_JOIN, rcLocal.size());

        return rcLocal;
    }
    
    public plasmaSearchPostOrder orderFinal(indexContainer rcLocal) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        assert (rcLocal != null);
        
        indexContainer searchResult = wordIndex.emptyContainer(null);
        long preorderTime = profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_PRESORT);
        
        profileLocal.startTimer();
        long pst = System.currentTimeMillis();
        searchResult.addAllUnique(rcLocal);
        searchResult.addAllUnique(rcContainers);
        searchResult.sort();
        searchResult.uniq(1000);
        preorderTime = preorderTime - (System.currentTimeMillis() - pst);
        if (preorderTime < 0) preorderTime = 200;
        plasmaSearchPreOrder preorder = new plasmaSearchPreOrder(query, ranking, searchResult, preorderTime);
        if (searchResult.size() > query.wantedResults) preorder.remove(true, true);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_PRESORT);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_PRESORT, rcLocal.size());
        
        // start url-fetch
        long postorderTime = profileLocal.getTargetTime(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        System.out.println("DEBUG: postorder-final (urlfetch) maxtime = " + postorderTime);
        long postorderLimitTime = (postorderTime < 0) ? Long.MAX_VALUE : (System.currentTimeMillis() + postorderTime);
        profileLocal.startTimer();
        plasmaSearchPostOrder acc = new plasmaSearchPostOrder(query, ranking);
        
        indexRWIEntry entry;
        indexURLEntry page;
        Long preranking;
        Object[] preorderEntry;
        indexURLEntry.Components comp;
        String pagetitle, pageurl, pageauthor;
        int minEntries = profileLocal.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        try {
            ordering: while (preorder.hasNext()) {
                if ((System.currentTimeMillis() >= postorderLimitTime) && (acc.sizeFetched() >= minEntries)) break;
                preorderEntry = preorder.next();
                entry = (indexRWIEntry) preorderEntry[0];
                // load only urls if there was not yet a root url of that hash
                preranking = (Long) preorderEntry[1];
                // find the url entry
                page = urlStore.load(entry.urlHash(), entry);
                if (page != null) {
                	comp = page.comp();
                	pagetitle = comp.title().toLowerCase();
                    if (comp.url() == null) continue ordering; // rare case where the url is corrupted
                	pageurl = comp.url().toString().toLowerCase();
                	pageauthor = comp.author().toLowerCase();
                	
                	// check exclusion
                	if (plasmaSearchQuery.matches(pagetitle, query.excludeHashes)) continue ordering;
                	if (plasmaSearchQuery.matches(pageurl, query.excludeHashes)) continue ordering;
                	if (plasmaSearchQuery.matches(pageauthor, query.excludeHashes)) continue ordering;
                	
                	// check url mask
                	if (!(pageurl.matches(query.urlMask))) continue ordering;
                	
                	// check constraints
                	if ((!(query.constraint.equals(plasmaSearchQuery.catchall_constraint))) &&
                        (query.constraint.get(plasmaCondenser.flag_cat_indexof)) &&
                        (!(comp.title().startsWith("Index of")))) {
                        log.logFine("filtered out " + comp.url().toString());
                        // filter out bad results
                        Iterator wi = query.queryHashes.iterator();
                        while (wi.hasNext()) wordIndex.removeEntry((String) wi.next(), page.hash());
                    } else if (query.contentdom != plasmaSearchQuery.CONTENTDOM_TEXT) {
                        if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (page.laudio() > 0)) acc.addPage(page, preranking);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (page.lvideo() > 0)) acc.addPage(page, preranking);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (page.limage() > 0)) acc.addPage(page, preranking);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_APP) && (page.lapp() > 0)) acc.addPage(page, preranking);
                    } else {
                        acc.addPage(page, preranking);
                    }
                }
            }
        } catch (kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_URLFETCH);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_URLFETCH, acc.sizeFetched());

        // start postsorting
        profileLocal.startTimer();
        acc.sortPages(postsort);
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_POSTSORT);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_POSTSORT, acc.sizeOrdered());
        
        // apply filter
        profileLocal.startTimer();
        acc.removeRedundant();
        profileLocal.setYieldTime(plasmaSearchTimingProfile.PROCESS_FILTER);
        profileLocal.setYieldCount(plasmaSearchTimingProfile.PROCESS_FILTER, acc.sizeOrdered());
        
        acc.localContributions = (rcLocal == null) ? 0 : rcLocal.size();
        acc.filteredResults = preorder.filteredCount();
        return acc;
    }
    
    private void prefetchLocal(indexContainer rcLocal, long timeout) {
        // pre-fetch some urls to fill LURL ram cache

        if (rcLocal == null) return;
        plasmaSearchPreOrder preorder = new plasmaSearchPreOrder(query, ranking, rcLocal, timeout - System.currentTimeMillis());
        if (preorder.filteredCount() > query.wantedResults) preorder.remove(true, true);
        
        // start url-fetch
        indexRWIEntry entry;
        try {
            while (preorder.hasNext()) {
                if (System.currentTimeMillis() >= timeout) break;
                entry = (indexRWIEntry) (preorder.next()[0]);
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
        serverLog.logFine("PLASMA", "STARTED FLUSHING GLOBAL SEARCH RESULTS FOR SEARCH " + query.queryString);
        
        int remaining = 0;
        if (primarySearchThreads == null) return;
        long starttime = System.currentTimeMillis();
        while (true) {
            flushGlobalResults(); // must be flushed before first check of remaining threads, othervise it is possible that NO results are flushed at all
            
            remaining = yacySearch.remainingWaiting(primarySearchThreads);
            if (secondarySearchThreads != null) remaining += yacySearch.remainingWaiting(secondarySearchThreads);
            if (remaining == 0) break;
  
            // wait a little bit before trying again
            try {Thread.sleep(1000);} catch (InterruptedException e) {}
            if (System.currentTimeMillis() - starttime > 90000) {
                yacySearch.interruptAlive(primarySearchThreads);
                if (secondarySearchThreads != null) yacySearch.interruptAlive(secondarySearchThreads);
                log.logFine("SEARCH FLUSH: " + remaining + " PEERS STILL BUSY; ABANDONED; SEARCH WAS " + query.queryString);
                break;
            }
            //log.logFine("FINISHED FLUSH RESULTS PROCESS for query " + query.hashes(","));
        }
        
        serverLog.logFine("PLASMA", "FINISHED FLUSHING " + rcContainerFlushCount + " GLOBAL SEARCH RESULTS FOR SEARCH " + query.queryString);
            
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
