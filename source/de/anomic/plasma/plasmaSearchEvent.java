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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.net.URL;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacySeed;

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
    private Object[] references;
    public  TreeMap IAResults, IACount;
    public  String IAmaxcounthash, IAneardhthash;
    
    private plasmaSearchEvent(plasmaSearchQuery query,
                             plasmaSearchRankingProfile ranking,
                             plasmaSearchProcessing localTiming,
                             plasmaSearchProcessing remoteTiming,
                             plasmaWordIndex wordIndex,
                             TreeMap preselectedPeerHashes,
                             boolean generateAbstracts,
                             TreeSet abstractSet) {
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
        this.references = new String[0];
        this.IAResults = new TreeMap();
        this.IACount = new TreeMap();
        this.IAmaxcounthash = null;
        this.IAneardhthash = null;
        
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
            
            if (generateAbstracts) {
                // compute index abstracts
                Iterator ci = searchContainerMaps[0].entrySet().iterator();
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
                    d = yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, wordhash);
                    if (d < mindhtdistance) {
                        // calculate the word hash that is closest to our dht position
                        mindhtdistance = d;
                        IAneardhthash = wordhash;
                    }
                    IACount.put(wordhash, new Integer(container.size()));
                    IAResults.put(wordhash, plasmaURL.compressIndex(container, null, 1000).toString());
                }
            }
            
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
            TreeMap preselectedPeerHashes,
            boolean generateAbstracts,
            TreeSet abstractSet) {
        plasmaSearchEvent event = (plasmaSearchEvent) lastEvents.get(query.id());
        if (event == null) {
            event = new plasmaSearchEvent(query, ranking, localTiming, remoteTiming, wordIndex, preselectedPeerHashes, generateAbstracts, abstractSet);
        } else {
            //re-new the event time for this event, so it is not deleted next time too early
            event.eventTime = System.currentTimeMillis();
        }
        return event;
    }
    
    private indexContainer search() {
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

    public ArrayList computeResults(
            TreeSet blueList,
            boolean overfetch) {
        
        indexContainer pre = search();
        final ArrayList hits = new ArrayList();
        
        // start url-fetch
        final long postorderTime = this.profileLocal.getTargetTime(plasmaSearchProcessing.PROCESS_POSTSORT);
        //System.out.println("DEBUG: postorder-final (urlfetch) maxtime = " + postorderTime);
        final long postorderLimitTime = (postorderTime < 0) ? Long.MAX_VALUE : (System.currentTimeMillis() + postorderTime);
        this.profileLocal.startTimer();
        final plasmaSearchPostOrder acc = new plasmaSearchPostOrder(query, ranking);
        
        indexRWIEntry rwientry;
        indexURLEntry page;
        indexURLEntry.Components comp;
        String pagetitle, pageurl, pageauthor;
        final int minEntries = this.profileLocal.getTargetCount(plasmaSearchProcessing.PROCESS_POSTSORT);
        try {
            ordering: for (int i = 0; i < pre.size(); i++) {
                if ((System.currentTimeMillis() >= postorderLimitTime) || (acc.sizeFetched() >= ((overfetch) ? 4 : 1) * minEntries)) break;
                rwientry = new indexRWIEntry(pre.get(i));
                // load only urls if there was not yet a root url of that hash
                // find the url entry
                page = wordIndex.loadedURL.load(rwientry.urlHash(), rwientry);
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
                        serverLog.logFine("PLASMA", "filtered out " + comp.url().toString());
                        // filter out bad results
                        final Iterator wi = query.queryHashes.iterator();
                        while (wi.hasNext()) wordIndex.removeEntry((String) wi.next(), page.hash());
                    } else if (query.contentdom != plasmaSearchQuery.CONTENTDOM_TEXT) {
                        if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (page.laudio() > 0)) acc.addPage(page);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (page.lvideo() > 0)) acc.addPage(page);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (page.limage() > 0)) acc.addPage(page);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_APP) && (page.lapp() > 0)) acc.addPage(page);
                    } else {
                        acc.addPage(page);
                    }
                }
            }
        } catch (final kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
        this.profileLocal.setYieldTime(plasmaSearchProcessing.PROCESS_URLFETCH);
        this.profileLocal.setYieldCount(plasmaSearchProcessing.PROCESS_URLFETCH, acc.sizeFetched());
        
        // start postsorting
        this.profileLocal.startTimer();
        acc.sortPages(true);
        this.profileLocal.setYieldTime(plasmaSearchProcessing.PROCESS_POSTSORT);
        this.profileLocal.setYieldCount(plasmaSearchProcessing.PROCESS_POSTSORT, acc.sizeOrdered());
        
        
        // apply filter
        this.profileLocal.startTimer();
        acc.removeRedundant();
        this.profileLocal.setYieldTime(plasmaSearchProcessing.PROCESS_FILTER);
        this.profileLocal.setYieldCount(plasmaSearchProcessing.PROCESS_FILTER, acc.sizeOrdered());
        
        // generate references
        this.references = acc.getReferences(16);
        
        // generate Result.Entry objects and optionally fetch snippets
        int i = 0;
        Entry entry;
        final boolean includeSnippets = false;
        while ((acc.hasMoreElements()) && (i < query.wantedResults)) {
            try {
                entry = new Entry(acc.nextElement(), wordIndex);
            } catch (final RuntimeException e) {
                continue;
            }
            // check bluelist again: filter out all links where any
            // bluelisted word
            // appear either in url, url's description or search word
            // the search word was sorted out earlier
            /*
             * String s = descr.toLowerCase() + url.toString().toLowerCase();
             * for (int c = 0; c < blueList.length; c++) { if
             * (s.indexOf(blueList[c]) >= 0) return; }
             */
            if (includeSnippets) {
                entry.setSnippet(plasmaSnippetCache.retrieveTextSnippet(
                        entry.url(), query.queryHashes, false,
                        entry.flags().get(plasmaCondenser.flag_cat_indexof), 260,
                        1000));
                // snippet =
                // snippetCache.retrieveTextSnippet(comp.url(),
                // query.queryHashes, false,
                // urlentry.flags().get(plasmaCondenser.flag_cat_indexof),
                // 260, 1000);
            } else {
                // snippet = null;
                entry.setSnippet(null);
            }
            i++;
            hits.add(entry);
        }

        /*
         * while ((acc.hasMoreElements()) && (((time + timestamp) <
         * System.currentTimeMillis()))) { urlentry = acc.nextElement();
         * urlstring = htmlFilterContentScraper.urlNormalform(urlentry.url());
         * descr = urlentry.descr();
         * 
         * addScoreForked(ref, gs, descr.split(" ")); addScoreForked(ref, gs,
         * urlstring.split("/")); }
         */
        return hits;
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
    
    public Object[] references() {
        return this.references;
    }
    
    public static class Entry {
        private indexURLEntry urlentry;
        private indexURLEntry.Components urlcomps; // buffer for components
        private String alternative_urlstring;
        private String alternative_urlname;
        private plasmaSnippetCache.TextSnippet snippet;
        
        public Entry(indexURLEntry urlentry, plasmaWordIndex wordIndex) {
            this.urlentry = urlentry;
            this.urlcomps = urlentry.comp();
            this.alternative_urlstring = null;
            this.alternative_urlname = null;
            this.snippet = null;
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
        public URL url() {
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
        public void setSnippet(plasmaSnippetCache.TextSnippet snippet) {
            this.snippet = snippet;
        }
        public plasmaSnippetCache.TextSnippet snippet() {
            return this.snippet;
        }
        public Date modified() {
            return urlentry.moddate();
        }
        public int filesize() {
            return urlentry.size();
        }
        public indexRWIEntry word() {
            return urlentry.word();
        }
        public boolean hasSnippet() {
            return false;
        }
        public plasmaSnippetCache.TextSnippet textSnippet() {
            return null;
        }
        public String resource() {
            // generate transport resource
            if ((snippet != null) && (snippet.exists())) {
                return urlentry.toString(snippet.getLineRaw());
            } else {
                return urlentry.toString();
            }
        }
    }
}
