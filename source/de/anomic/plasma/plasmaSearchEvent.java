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

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.index.indexContainer;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySearch;

public final class plasmaSearchEvent {
    
    public static plasmaSearchEvent lastEvent = null;

    private plasmaSearchQuery query;
    private plasmaSearchRankingProfile ranking;
    private plasmaWordIndex wordIndex;
    private indexContainer rcContainers; // cache for results
    private Map rcAbstracts; // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation
    private plasmaSearchProcessing profileLocal, profileGlobal;
    private yacySearch[] primarySearchThreads, secondarySearchThreads;
    private TreeMap preselectedPeerHashes;
    private int localcount, globalcount;
    
    public plasmaSearchEvent(plasmaSearchQuery query,
                             plasmaSearchRankingProfile ranking,
                             plasmaSearchProcessing localTiming,
                             plasmaSearchProcessing remoteTiming,
                             plasmaWordIndex wordIndex,
                             TreeMap preselectedPeerHashes) {
        this.wordIndex = wordIndex;
        this.query = query;
        this.ranking = ranking;
        this.rcContainers = plasmaWordIndex.emptyContainer(null);
        this.rcAbstracts = (query.queryHashes.size() > 1) ? new TreeMap() : null; // generate abstracts only for combined searches
        this.profileLocal = localTiming;
        this.profileGlobal = remoteTiming;
        this.primarySearchThreads = null;
        this.secondarySearchThreads = null;
        this.preselectedPeerHashes = preselectedPeerHashes;
        this.localcount = 0;
        this.globalcount = 0;
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

    public plasmaSearchPreOrder search() {
        // combine all threads
        
            long start = System.currentTimeMillis();
            plasmaSearchPreOrder pre;
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
                        rcContainers, 
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
                
                // try to pre-fetch some LURLs if there is enough time
                indexContainer rcLocal =
                    (searchContainerMaps == null) ?
                      plasmaWordIndex.emptyContainer(null) :
                          profileLocal.localSearchJoinExclude(
                              searchContainerMaps[0].values(),
                              searchContainerMaps[1].values(),
                              (query.queryHashes.size() == 0) ?
                                0 :
                                profileLocal.getTargetTime(plasmaSearchProcessing.PROCESS_JOIN) * query.queryHashes.size() / (query.queryHashes.size() + query.excludeHashes.size()),
                              query.maxDistance);
                
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
                
                // finished searching
                serverLog.logFine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO " + primarySearchThreads.length + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");

                // combine the result and order
                indexContainer searchResult = plasmaWordIndex.emptyContainer(null);
                searchResult.addAllUnique(rcLocal);
                searchResult.addAllUnique(rcContainers);
                searchResult.sort();
                searchResult.uniq(1000);
                localcount = rcLocal.size();
                globalcount = rcContainers.size();
                pre = new plasmaSearchPreOrder(query, profileLocal, ranking, searchResult);
            } else {
                Map[] searchContainerMaps = profileLocal.localSearchContainers(query, wordIndex, null);
                
                indexContainer rcLocal =
                    (searchContainerMaps == null) ?
                      plasmaWordIndex.emptyContainer(null) :
                          profileLocal.localSearchJoinExclude(
                              searchContainerMaps[0].values(),
                              searchContainerMaps[1].values(),
                              (query.queryHashes.size() == 0) ?
                                0 :
                                profileLocal.getTargetTime(plasmaSearchProcessing.PROCESS_JOIN) * query.queryHashes.size() / (query.queryHashes.size() + query.excludeHashes.size()),
                              query.maxDistance);
                this.localcount = rcLocal.size();
                pre = new plasmaSearchPreOrder(query, profileLocal, ranking, rcLocal);
            }

            // log the event
            serverLog.logFine("SEARCH_EVENT", "SEARCHRESULT: " + profileLocal.reportToString());
            
            // prepare values for statistics
            lastEvent = this;
            
            // return search result
            return pre;
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
                        words, "", urls, wordIndex, rcContainers, peer, plasmaSwitchboard.urlBlacklist,
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
    
}
