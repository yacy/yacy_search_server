// SearchEvent.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.10.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.search;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.storage.ScoreMap;
import net.yacy.document.LargeNumberCache;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.EventTracker;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.SetTools;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.data.WorkTables;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.dht.FlatWordPartitionScheme;
import de.anomic.yacy.graphics.ProfilingGraph;

public final class SearchEvent {
    
    public enum Type {
        INITIALIZATION, COLLECTION, JOIN, PRESORT, URLFETCH, NORMALIZING, FINALIZATION,
        REMOTESEARCH_START, REMOTESEARCH_TERMINATE, ABSTRACTS, CLEANUP, SNIPPETFETCH_START, ONERESULT, REFERENCECOLLECTION, RESULTLIST;
    }
    
    public static final int max_results_preparation = 3000;
    
    // class variables that may be implemented with an abstract class
    private long eventTime;
    private QueryParams query;
    private final yacySeedDB peers;
    private final WorkTables workTables;
    private RankingProcess rankingProcess; // ordered search results, grows dynamically as all the query threads enrich this container
    private ResultFetcher resultFetcher;
    
    private final SecondarySearchSuperviser secondarySearchSuperviser; 

    // class variables for remote searches
    private yacySearch[] primarySearchThreads, secondarySearchThreads;
    private final SortedMap<byte[], String> preselectedPeerHashes;
    private final Thread localSearchThread;
    private final SortedMap<byte[], Integer> IACount;
    private final SortedMap<byte[], String> IAResults;
    private final SortedMap<byte[], HeuristicResult> heuristics;
    private byte[] IAmaxcounthash, IAneardhthash;
    private final ReferenceOrder order;
    
    protected SearchEvent(final QueryParams query,
                             final yacySeedDB peers,
                             final WorkTables workTables,
                             final SortedMap<byte[], String> preselectedPeerHashes,
                             final boolean generateAbstracts,
                             final LoaderDispatcher loader,
                             final int remote_maxcount,
                             final long remote_maxtime,
                             final int burstRobinsonPercent,
                             final int burstMultiwordPercent,
                             final boolean deleteIfSnippetFail) {
        if (MemoryControl.available() < 1024 * 1024 * 100) SearchEventCache.cleanupEvents(true);
        this.eventTime = System.currentTimeMillis(); // for lifetime check
        this.peers = peers;
        this.workTables = workTables;
        this.query = query;
        this.secondarySearchSuperviser = (query.queryHashes.size() > 1) ? new SecondarySearchSuperviser() : null; // generate abstracts only for combined searches
        if (this.secondarySearchSuperviser != null) this.secondarySearchSuperviser.start();
        this.primarySearchThreads = null;
        this.secondarySearchThreads = null;
        this.preselectedPeerHashes = preselectedPeerHashes;
        this.IAResults = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
        this.IACount = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        this.heuristics = new TreeMap<byte[], HeuristicResult>(Base64Order.enhancedCoder);
        this.IAmaxcounthash = null;
        this.IAneardhthash = null;
        this.localSearchThread = null;
        this.order = new ReferenceOrder(query.ranking, UTF8.getBytes(query.targetlang));
        boolean remote = (query.domType == QueryParams.SEARCHDOM_GLOBALDHT) || (query.domType == QueryParams.SEARCHDOM_CLUSTERALL);
        if (remote && peers.sizeConnected() == 0) remote = false;
        final long start = System.currentTimeMillis();
        if (remote) {
        	// initialize a ranking process that is the target for data
        	// that is generated concurrently from local and global search threads
            this.rankingProcess = new RankingProcess(this.query, this.order, max_results_preparation);
            
            // start a local search concurrently
            this.rankingProcess.start();
                       
            // start global searches
            final long timer = System.currentTimeMillis();
            this.primarySearchThreads = (query.queryHashes.isEmpty()) ? null : yacySearch.primaryRemoteSearches(
                    QueryParams.hashSet2hashString(query.queryHashes),
                    QueryParams.hashSet2hashString(query.excludeHashes),
                    query.prefer,
                    query.urlMask,
                    query.snippetMatcher,
                    query.targetlang == null ? "" : query.targetlang,
                    query.sitehash == null ? "" : query.sitehash,
                    query.authorhash == null ? "" : query.authorhash,
                    remote_maxcount,
                    remote_maxtime,
                    query.maxDistance,
                    query.getSegment(),
                    peers,
                    rankingProcess,
                    secondarySearchSuperviser,
                    Switchboard.urlBlacklist,
                    query.ranking,
                    query.constraint,
                    (query.domType == QueryParams.SEARCHDOM_GLOBALDHT) ? null : preselectedPeerHashes,
                    burstRobinsonPercent,
                    burstMultiwordPercent);
            if (this.primarySearchThreads != null) {
                Log.logFine("SEARCH_EVENT", "STARTING " + this.primarySearchThreads.length + " THREADS TO CATCH EACH " + remote_maxcount + " URLs");
                this.rankingProcess.moreFeeders(this.primarySearchThreads.length);
                EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), Type.REMOTESEARCH_START, "", this.primarySearchThreads.length, System.currentTimeMillis() - timer), false);
                // finished searching
                Log.logFine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO " + primarySearchThreads.length + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
            } else {
                // no search since query is empty, user might have entered no data or filters have removed all search words
                Log.logFine("SEARCH_EVENT", "NO SEARCH STARTED DUE TO EMPTY SEARCH REQUEST.");
            }
            
            // start worker threads to fetch urls and snippets
            this.resultFetcher = new ResultFetcher(loader, this.rankingProcess, query, this.peers, this.workTables, 3000, deleteIfSnippetFail);
        } else {
            // do a local search
            this.rankingProcess = new RankingProcess(this.query, this.order, max_results_preparation);
            
            if (generateAbstracts) {
                this.rankingProcess.run(); // this is not started concurrently here on purpose!
                // compute index abstracts
                final long timer = System.currentTimeMillis();
                int maxcount = -1;
                long mindhtdistance = Long.MAX_VALUE, l;
                byte[] wordhash;
                assert this.rankingProcess.searchContainerMap() != null;
                for (final Map.Entry<byte[], ReferenceContainer<WordReference>> entry : this.rankingProcess.searchContainerMap().entrySet()) {
                    wordhash = entry.getKey();
                    final ReferenceContainer<WordReference> container = entry.getValue();
                    assert (Base64Order.enhancedCoder.equal(container.getTermHash(), wordhash)) : "container.getTermHash() = " + UTF8.String(container.getTermHash()) + ", wordhash = " + UTF8.String(wordhash);
                    if (container.size() > maxcount) {
                        IAmaxcounthash = wordhash;
                        maxcount = container.size();
                    }
                    l = FlatWordPartitionScheme.std.dhtDistance(wordhash, null, peers.mySeed());
                    if (l < mindhtdistance) {
                        // calculate the word hash that is closest to our dht position
                        mindhtdistance = l;
                        IAneardhthash = wordhash;
                    }
                    IACount.put(wordhash, LargeNumberCache.valueOf(container.size()));
                    IAResults.put(wordhash, WordReferenceFactory.compressIndex(container, null, 1000).toString());
                }
                EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), Type.ABSTRACTS, "", this.rankingProcess.searchContainerMap().size(), System.currentTimeMillis() - timer), false);
            } else {
                this.rankingProcess.start(); // start concurrently
                // but give process time to accumulate a certain amount of data
                // before a reading process wants to get results from it
                for (int i = 0; i < 10; i++) {
                    if (!this.rankingProcess.isAlive()) break;
                    try {Thread.sleep(10);} catch (InterruptedException e) {}
                }
                // this will reduce the maximum waiting time until results are available to 100 milliseconds
                // while we always get a good set of ranked data
            }
            
            // start worker threads to fetch urls and snippets
            this.resultFetcher = new ResultFetcher(loader, this.rankingProcess, query, this.peers, this.workTables, 500, deleteIfSnippetFail);
        }
         
        // clean up events
        SearchEventCache.cleanupEvents(false);
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), Type.CLEANUP, "", 0, 0), false);
        
        // store this search to a cache so it can be re-used
        if (MemoryControl.available() < 1024 * 1024 * 100) SearchEventCache.cleanupEvents(true);
        SearchEventCache.put(query.id(false), this);
   }
   
   public ReferenceOrder getOrder() {
       return this.order;
   }
   
   public long getEventTime() {
       return this.eventTime;
   }
   
   public void resetEventTime() {
       this.eventTime = System.currentTimeMillis();
   }
   
   public QueryParams getQuery() {
       return this.query;
   }
   
   public void setQuery(QueryParams query) {
       this.query = query;
       this.resultFetcher.query = query;
   }
   
   public void cleanup() {
       // stop all threads
       if (primarySearchThreads != null) {
           for (final yacySearch search : this.primarySearchThreads) {
               if (search != null) synchronized (search) {
                   if (search.isAlive()) search.interrupt();
               }
           }
       }
       if (secondarySearchThreads != null) {
           for (final yacySearch search : this.secondarySearchThreads) {
               if (search != null) synchronized (search) {
                   if (search.isAlive()) search.interrupt();
               }
           }
       }
       
       // clear all data structures
       if (this.preselectedPeerHashes != null) this.preselectedPeerHashes.clear();
       if (this.localSearchThread != null) if (this.localSearchThread.isAlive()) this.localSearchThread.interrupt();
       if (this.IACount != null) this.IACount.clear();
       if (this.IAResults != null) this.IAResults.clear();
       if (this.heuristics != null) this.heuristics.clear();
   }
   
   public Iterator<Map.Entry<byte[], String>> abstractsString() {
       return this.IAResults.entrySet().iterator();
   }
   
   public String abstractsString(byte[] hash) {
       return this.IAResults.get(hash);
   }
   
   public Iterator<Map.Entry<byte[], Integer>> abstractsCount() {
       return this.IACount.entrySet().iterator();
   }
   
   public int abstractsCount(byte[] hash) {
       Integer i = this.IACount.get(hash);
       if (i == null) return -1;
       return i.intValue();
   }
   
   public byte[] getAbstractsMaxCountHash() {
       return this.IAmaxcounthash;
   }
   
   public byte[] getAbstractsNearDHTHash() {
       return this.IAneardhthash;
   }
   
   boolean anyRemoteSearchAlive() {
        // check primary search threads
        if ((this.primarySearchThreads != null) && (this.primarySearchThreads.length != 0)) {
            for (final yacySearch primarySearchThread : primarySearchThreads) {
                if ((primarySearchThread != null) && (primarySearchThread.isAlive())) return true;
            }
        }
        // maybe a secondary search thread is alive, check this
        if ((this.secondarySearchThreads != null) && (this.secondarySearchThreads.length != 0)) {
            for (final yacySearch secondarySearchThread : this.secondarySearchThreads) {
                if ((secondarySearchThread != null) && (secondarySearchThread.isAlive())) return true;
            }
        }
        return false;
    }
    
    public yacySearch[] getPrimarySearchThreads() {
        return primarySearchThreads;
    }
    
    public yacySearch[] getSecondarySearchThreads() {
        return secondarySearchThreads;
    }
    
    public RankingProcess getRankingResult() {
        return this.rankingProcess;
    }

    public ScoreMap<String> getNamespaceNavigator() {
        return this.rankingProcess.getNamespaceNavigator();
    }
    
    public ScoreMap<String> getHostNavigator() {
        return this.rankingProcess.getHostNavigator();
    }
    
    public ScoreMap<String> getTopicNavigator(int count) {
        // returns a set of words that are computed as toplist
        return this.rankingProcess.getTopicNavigator(count);
    }
    
    public ScoreMap<String> getAuthorNavigator() {
        // returns a list of authors so far seen on result set
        return this.rankingProcess.getAuthorNavigator();
    }
    
    public void addHeuristic(byte[] urlhash, String heuristicName, boolean redundant) {
        synchronized (this.heuristics) {
            this.heuristics.put(urlhash, new HeuristicResult(urlhash, heuristicName, redundant));
        }
    }
    
    public HeuristicResult getHeuristic(byte[] urlhash) {
        synchronized (this.heuristics) {
            return this.heuristics.get(urlhash);
        }
    }
    
    public ResultEntry oneResult(final int item, long timeout) {
        if ((query.domType == QueryParams.SEARCHDOM_GLOBALDHT) ||
             (query.domType == QueryParams.SEARCHDOM_CLUSTERALL)) {
            // this is a search using remote search threads. Also the local
            // search thread is started as background process
            if ((localSearchThread != null) && (localSearchThread.isAlive())) {
                // in case that the local search takes longer than some other
                // remote search requests, wait that the local process terminates first
            	try {localSearchThread.join();} catch (InterruptedException e) {}
            }
        }
        return this.resultFetcher.oneResult(item, timeout);
    }
    
    boolean secondarySearchStartet = false;
    
    public static class HeuristicResult /*implements Comparable<HeuristicResult>*/ {
        public final byte[] urlhash; public final String heuristicName; public final boolean redundant;
        public HeuristicResult(byte[] urlhash, String heuristicName, boolean redundant) {
            this.urlhash = urlhash; this.heuristicName = heuristicName; this.redundant = redundant;
        }/*
        public int compareTo(HeuristicResult o) {
            return Base64Order.enhancedCoder.compare(this.urlhash, o.urlhash);
        }
        public int hashCode() {
            return (int) Base64Order.enhancedCoder.cardinal(this.urlhash);
        }
        public boolean equals(Object o) {
            return Base64Order.enhancedCoder.equal(this.urlhash, ((HeuristicResult) o).urlhash);
        }*/
    }
    
    public class SecondarySearchSuperviser extends Thread {
        
        // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation
        // this relation contains the information where specific urls can be found in specific peers
        SortedMap<String, SortedMap<String, StringBuilder>> abstractsCache;
        SortedSet<String> checkedPeers;
        Semaphore trigger;
        
        public SecondarySearchSuperviser() {
            this.abstractsCache = new TreeMap<String, SortedMap<String, StringBuilder>>();
            this.checkedPeers = new TreeSet<String>();
            this.trigger = new Semaphore(0);
        }
        
        /**
         * add a single abstract to the existing set of abstracts
         * @param wordhash
         * @param singleAbstract // a mapping from url-hashes to a string of peer-hashes
         */
        public void addAbstract(String wordhash, final TreeMap<String, StringBuilder> singleAbstract) {
            final SortedMap<String, StringBuilder> oldAbstract;
            synchronized (abstractsCache) {
                oldAbstract = abstractsCache.get(wordhash);
                if (oldAbstract == null) {
                    // new abstracts in the cache
                    abstractsCache.put(wordhash, singleAbstract);
                    return;
                }
            }
            // extend the abstracts in the cache: join the single abstracts
            new Thread() {
                public void run() {
                    for (final Map.Entry<String, StringBuilder> oneref: singleAbstract.entrySet()) {
                        final String urlhash = oneref.getKey();
                        final StringBuilder peerlistNew = oneref.getValue();
                        synchronized (oldAbstract) {
                            final StringBuilder peerlistOld = oldAbstract.put(urlhash, peerlistNew);
                            if (peerlistOld != null) peerlistOld.append(peerlistNew);
                        }
                    }
                }
            }.start();
            // abstractsCache.put(wordhash, oldAbstract); // put not necessary since it is sufficient to just change the value content (it stays assigned)
        }
        
        public void commitAbstract() {
            this.trigger.release();
        }
        
        private String wordsFromPeer(final String peerhash, final StringBuilder urls) {
            Map.Entry<String, SortedMap<String, StringBuilder>> entry;
            String word, url, wordlist = "";
            StringBuilder peerlist;
            SortedMap<String, StringBuilder> urlPeerlist;
            int p;
            boolean hasURL;
            synchronized (this) {
                final Iterator<Map.Entry <String, SortedMap<String, StringBuilder>>> i = this.abstractsCache.entrySet().iterator();
                while (i.hasNext()) {
                    entry = i.next();
                    word = entry.getKey();
                    urlPeerlist = entry.getValue();
                    hasURL = true;
                    for (int j = 0; j < urls.length(); j = j + 12) {
                        url = urls.substring(j, j + 12);
                        peerlist = urlPeerlist.get(url);
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
        
        @Override
        public void run() {
            try {
                int t = 0;
                while (this.trigger.tryAcquire(10000, TimeUnit.MILLISECONDS)) {
                    // a trigger was released
                    prepareSecondarySearch();
                    t++;
                    if (t > 10) break;
                }
            } catch (InterruptedException e) {
                // the thread was interrupted
                // do nohing
            }
             // the time-out was reached
        }
        
        private void prepareSecondarySearch() {
            if (abstractsCache == null || abstractsCache.size() != query.queryHashes.size()) return; // secondary search not possible (yet)
            
            // catch up index abstracts and join them; then call peers again to submit their urls
            /*
            System.out.println("DEBUG-INDEXABSTRACT: " + abstractsCache.size() + " word references caught, " + query.queryHashes.size() + " needed");
            for (Map.Entry<String, TreeMap<String, String>> entry: abstractsCache.entrySet()) {
                System.out.println("DEBUG-INDEXABSTRACT: hash " + entry.getKey() + ": " + ((query.queryHashes.has(entry.getKey().getBytes()) ? "NEEDED" : "NOT NEEDED") + "; " + entry.getValue().size() + " entries"));
            }
            */
            
            // find out if there are enough references for all words that are searched
            if (abstractsCache.size() != query.queryHashes.size()) return;

            // join all the urlhash:peerlist relations: the resulting map has values with a combined peer-list list
            final SortedMap<String, StringBuilder> abstractJoin = SetTools.joinConstructive(abstractsCache.values(), true);
            if (abstractJoin.isEmpty()) return;
            // the join result is now a urlhash: peer-list relation
            
            // generate a list of peers that have the urls for the joined search result
            final SortedMap<String, StringBuilder> secondarySearchURLs = new TreeMap<String, StringBuilder>(); // a (peerhash:urlhash-liststring) mapping
            String url, peer;
            StringBuilder urls, peerlist;
            final String mypeerhash = peers.mySeed().hash;
            boolean mypeerinvolved = false;
            int mypeercount;
            for (Map.Entry<String, StringBuilder> entry: abstractJoin.entrySet()) {
                url = entry.getKey();
                peerlist = entry.getValue();
                //System.out.println("DEBUG-INDEXABSTRACT: url " + url + ": from peers " + peerlist);
                mypeercount = 0;
                for (int j = 0; j < peerlist.length(); j += 12) {
                    peer = peerlist.substring(j, j + 12);
                    if ((peer.equals(mypeerhash)) && (mypeercount++ > 1)) continue;
                    //if (peers.indexOf(peer) < j) continue; // avoid doubles that may appear in the abstractJoin
                    urls = secondarySearchURLs.get(peer);
                    if (urls == null) {
                        urls = new StringBuilder(24);
                        urls.append(url);
                        secondarySearchURLs.put(peer, urls);
                    } else {
                        urls.append(url);
                    }
                    secondarySearchURLs.put(peer, urls);
                }
                if (mypeercount == 1) mypeerinvolved = true;
            }
            
            // compute words for secondary search and start the secondary searches
            String words;
            secondarySearchThreads = new yacySearch[(mypeerinvolved) ? secondarySearchURLs.size() - 1 : secondarySearchURLs.size()];
            int c = 0;
            for (Map.Entry<String, StringBuilder> entry: secondarySearchURLs.entrySet()) {
                peer = entry.getKey();
                if (peer.equals(mypeerhash)) continue; // we don't need to ask ourself
                if (checkedPeers.contains(peer)) continue; // do not ask a peer again
                urls = entry.getValue();
                words = wordsFromPeer(peer, urls);
                if (words.length() == 0) continue; // ???
                assert words.length() >= 12 : "words = " + words;
                //System.out.println("DEBUG-INDEXABSTRACT ***: peer " + peer + "   has urls: " + urls + " from words: " + words);
                rankingProcess.moreFeeders(1);
                checkedPeers.add(peer);
                secondarySearchThreads[c++] = yacySearch.secondaryRemoteSearch(
                        words, urls.toString(), 6000, query.getSegment(), peers, rankingProcess, peer, Switchboard.urlBlacklist,
                        query.ranking, query.constraint, preselectedPeerHashes);
            }
            
        }
    
    }
    
    public ResultFetcher result() {
        return this.resultFetcher;
    }
    
}
