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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySearch;

public final class plasmaSearchEvent {
    
    //public static plasmaSearchEvent lastEvent = null;
    public static String lastEventID = "";
    private static HashMap lastEvents = new HashMap(); // a cache for objects from this class: re-use old search requests
    public static final long eventLifetime = 600000; // the time an event will stay in the cache, 10 Minutes
    
    private long eventTime;
    private plasmaSearchQuery query;
    private plasmaSearchRankingProfile ranking;
    private plasmaWordIndex wordIndex;
    private indexContainer rcLocal; // cache for local results
    private indexContainer rcGlobal; // cache for global results
    private Map rcAbstracts; // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation
    private plasmaSearchProcessing profileLocal, profileGlobal;
    private yacySearch[] primarySearchThreads, secondarySearchThreads;
    private TreeMap preselectedPeerHashes;
    private int localcount, globalcount;
    private indexContainer sortedResults;
    private int lastglobal;
    private int filteredCount;
    private ArrayList display; // an array of url hashes of urls that had been displayed as search result after this search
    
    private plasmaSearchEvent(plasmaSearchQuery query,
                             plasmaSearchRankingProfile ranking,
                             plasmaSearchProcessing localTiming,
                             plasmaSearchProcessing remoteTiming,
                             plasmaWordIndex wordIndex,
                             TreeMap preselectedPeerHashes) {
        this.eventTime = System.currentTimeMillis(); // for lifetime check
        this.wordIndex = wordIndex;
        this.query = query;
        this.ranking = ranking;
        this.rcLocal = null;
        this.rcGlobal = plasmaWordIndex.emptyContainer(null, 0);;
        this.rcAbstracts = (query.queryHashes.size() > 1) ? new TreeMap() : null; // generate abstracts only for combined searches
        this.profileLocal = localTiming;
        this.profileGlobal = remoteTiming;
        this.primarySearchThreads = null;
        this.secondarySearchThreads = null;
        this.preselectedPeerHashes = preselectedPeerHashes;
        this.localcount = 0;
        this.globalcount = 0;
        this.sortedResults = null;
        this.lastglobal = 0;
        this.display = new ArrayList();
        
        long start = System.currentTimeMillis();
        if ((query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) ||
            (query.domType == plasmaSearchQuery.SEARCHDOM_CLUSTERALL)) {
            int fetchpeers = (int) (query.maximumTime / 500L); // number of target peers; means 10 peers in 10 seconds
            if (fetchpeers > 50) fetchpeers = 50;
            if (fetchpeers < 30) fetchpeers = 30;

            // do a global search
            // the result of the fetch is then in the rcGlobal
            serverLog.logFine("SEARCH_EVENT", "STARTING " + fetchpeers + " THREADS TO CATCH EACH " + profileGlobal.getTargetCount(plasmaSearchProcessing.PROCESS_POSTSORT) + " URLs WITHIN " + (profileGlobal.duetime() / 1000) + " SECONDS");
            long secondaryTimeout = System.currentTimeMillis() + profileGlobal.duetime() / 3 * 2;
            long primaryTimeout = System.currentTimeMillis() + profileGlobal.duetime();
            primarySearchThreads = yacySearch.primaryRemoteSearches(
                    plasmaSearchQuery.hashSet2hashString(query.queryHashes),
                    plasmaSearchQuery.hashSet2hashString(query.excludeHashes),
                    "",
                    query.prefer,
                    query.urlMask,
                    query.maxDistance,
                    wordIndex,
                    rcGlobal, 
                    rcAbstracts,
                    fetchpeers,
                    plasmaSwitchboard.urlBlacklist,
                    profileGlobal,
                    ranking,
                    query.constraint,
                    (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) ? null : preselectedPeerHashes);

            // meanwhile do a local search
            Map[] searchContainerMaps = profileLocal.localSearchContainers(query, wordIndex, null);
            
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

            // join and exlcude the local result
            this.rcLocal =
                (searchContainerMaps == null) ?
                  plasmaWordIndex.emptyContainer(null, 0) :
                      profileLocal.localSearchJoinExclude(
                          searchContainerMaps[0].values(),
                          searchContainerMaps[1].values(),
                          (query.queryHashes.size() == 0) ?
                            0 :
                            profileLocal.getTargetTime(plasmaSearchProcessing.PROCESS_JOIN) * query.queryHashes.size() / (query.queryHashes.size() + query.excludeHashes.size()),
                          query.maxDistance);

            // sort the local containers and truncate it to a limited count,
            // so following sortings together with the global results will be fast
            plasmaSearchPreOrder firstsort = new plasmaSearchPreOrder(query, profileLocal, ranking, rcLocal);
            rcLocal = firstsort.strippedContainer(200);
            
            int prefetchIndex = 0;
            HashSet unknownURLs = new HashSet();
            String urlhash;
            
            // while we wait for the first time-out for index abstracts, we fetch urls form the url-db
            while ((System.currentTimeMillis() < secondaryTimeout) && (prefetchIndex < rcLocal.size())) {
                if (yacySearch.remainingWaiting(primarySearchThreads) == 0) break; // all threads have finished
                urlhash = new String(rcLocal.get(prefetchIndex).getColBytes(0));
                if (wordIndex.loadedURL.load(urlhash, null) == null) unknownURLs.add(urlhash);
                prefetchIndex++;
            }
            
            // eventually wait some more time to retrieve index abstracts from primary search
            while (System.currentTimeMillis() < secondaryTimeout) {
                if (yacySearch.remainingWaiting(primarySearchThreads) == 0) break; // all threads have finished
                try {Thread.sleep(100);} catch (InterruptedException e) {}
            }
            
            // evaluate index abstracts and start a secondary search
            if (rcAbstracts != null) prepareSecondarySearch();
            
            // while we wait for the second time-out for index abstracts, we fetch more urls form the url-db
            while ((System.currentTimeMillis() < primaryTimeout) && (prefetchIndex < rcLocal.size())) {
                if (yacySearch.remainingWaiting(primarySearchThreads) == 0) break; // all threads have finished
                urlhash = new String(rcLocal.get(prefetchIndex).getColBytes(0));
                if (wordIndex.loadedURL.load(urlhash, null) == null) unknownURLs.add(urlhash);
                prefetchIndex++;
            }
            
            // when we have found some non-existing urls in the local collection, we delete them now
            wordIndex.removeEntriesMultiple(query.queryHashes, unknownURLs);
            rcLocal.removeEntriesMultiple(query.queryHashes, unknownURLs);
            localcount = rcLocal.size();
            
            // catch up global results:
            // wait until primary timeout passed
            while (System.currentTimeMillis() < primaryTimeout) {
                if ((yacySearch.remainingWaiting(primarySearchThreads) == 0) &&
                    ((secondarySearchThreads == null) || (yacySearch.remainingWaiting(secondarySearchThreads) == 0))) break; // all threads have finished
                try {Thread.sleep(100);} catch (InterruptedException e) {}
            }
            
            // finished searching
            serverLog.logFine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO " + primarySearchThreads.length + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        } else {
            Map[] searchContainerMaps = profileLocal.localSearchContainers(query, wordIndex, null);
            
            rcLocal =
                (searchContainerMaps == null) ?
                  plasmaWordIndex.emptyContainer(null, 0) :
                      profileLocal.localSearchJoinExclude(
                          searchContainerMaps[0].values(),
                          searchContainerMaps[1].values(),
                          (query.queryHashes.size() == 0) ?
                            0 :
                            profileLocal.getTargetTime(plasmaSearchProcessing.PROCESS_JOIN) * query.queryHashes.size() / (query.queryHashes.size() + query.excludeHashes.size()),
                          query.maxDistance);
            this.localcount = rcLocal.size();
        }

        // log the event
        serverLog.logFine("SEARCH_EVENT", "SEARCHRESULT: " + profileLocal.reportToString());
        
        // set link for statistic
        //lastEvent = this;
        
        // remove old events in the event cache
        Iterator i = lastEvents.entrySet().iterator();
        while (i.hasNext()) {
            if (((plasmaSearchEvent) ((Map.Entry) i.next()).getValue()).eventTime + eventLifetime < System.currentTimeMillis()) i.remove();
        }
        
        // store this search to a cache so it can be re-used
        lastEvents.put(query.id(), this);
        lastEventID = query.id();
    }
    
    public plasmaSearchQuery getQuery() {
        return query;
    }
    
    public plasmaSearchRankingProfile getRanking() {
        return ranking;
    }
    
    public plasmaSearchProcessing getLocalTiming() {
        return profileLocal;
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
        return this.globalcount;
    }

    public static plasmaSearchEvent getEvent(String eventID) {
        return (plasmaSearchEvent) lastEvents.get(eventID);
    }
    
    public static plasmaSearchEvent getEvent(plasmaSearchQuery query,
            plasmaSearchRankingProfile ranking,
            plasmaSearchProcessing localTiming,
            plasmaSearchProcessing remoteTiming,
            plasmaWordIndex wordIndex,
            TreeMap preselectedPeerHashes) {
        plasmaSearchEvent event = (plasmaSearchEvent) lastEvents.get(query.id());
        if (event == null) {
            event = new plasmaSearchEvent(query, ranking, localTiming, remoteTiming, wordIndex, preselectedPeerHashes);
        } else {
            //re-new the event time for this event, so it is not deleted next time too early
            event.eventTime = System.currentTimeMillis();
        }
        return event;
    }
    
    public indexContainer search() {
        // combine the local and global (if any) result and order
        if ((rcGlobal != null) && (rcGlobal.size() > 0)) {
            globalcount = rcGlobal.size();
            if ((this.sortedResults == null) || (this.lastglobal != globalcount)) {
                indexContainer searchResult = plasmaWordIndex.emptyContainer(null, rcLocal.size() + rcGlobal.size());
                searchResult.addAllUnique(rcLocal);
                searchResult.addAllUnique(rcGlobal);
                searchResult.sort();
                searchResult.uniq(100);
                lastglobal = globalcount;
                plasmaSearchPreOrder pre = new plasmaSearchPreOrder(query, profileLocal, ranking, searchResult);
                this.filteredCount = pre.filteredCount();
                this.sortedResults = pre.strippedContainer(200);
            }
        } else {
            if (this.sortedResults == null) {
                plasmaSearchPreOrder pre = new plasmaSearchPreOrder(query, profileLocal, ranking, rcLocal);
                this.filteredCount = pre.filteredCount();
                this.sortedResults = pre.strippedContainer(200);
            }
        }
        
        return this.sortedResults;
    }
    
    public int filteredCount() {
        return this.filteredCount;
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
                        words, "", urls, wordIndex, rcGlobal, peer, plasmaSwitchboard.urlBlacklist,
                        profileGlobal, ranking, query.constraint, preselectedPeerHashes);

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
        indexRWIEntry e = this.sortedResults.remove(urlhash);
        assert e != null;
        rcLocal.remove(urlhash);
    }
    
    public void displayed(String urlhash, int position) {
        this.display.set(position, urlhash);
    }
    
}
