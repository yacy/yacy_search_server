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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.EventTracker;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.SetTools;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.crawler.ResultURLs;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.dht.FlatWordPartitionScheme;
import de.anomic.yacy.graphics.ProfilingGraph;

public final class SearchEvent {
    
    public static final String INITIALIZATION = "initialization";
    public static final String COLLECTION = "collection";
    public static final String JOIN = "join";
    public static final String PRESORT = "presort";
    public static final String URLFETCH = "urlfetch";
    public static final String NORMALIZING = "normalizing";
    public static final String FINALIZATION = "finalization";
    
    public static final int max_results_preparation = 3000;
    
    // class variables that may be implemented with an abstract class
    private long eventTime;
    private QueryParams query;
    private final yacySeedDB peers;
    private RankingProcess rankedCache; // ordered search results, grows dynamically as all the query threads enrich this container
    private ResultFetcher results;
    
    private final SecondarySearchSuperviser secondarySearchSuperviser; 

    // class variables for remote searches
    private yacySearch[] primarySearchThreads, secondarySearchThreads;
    private final TreeMap<byte[], String> preselectedPeerHashes;
    private final ResultURLs crawlResults;
    private final Thread localSearchThread;
    private final TreeMap<byte[], Integer> IACount;
    private final TreeMap<byte[], String> IAResults;
    private final TreeMap<byte[], HeuristicResult> heuristics;
    private byte[] IAmaxcounthash, IAneardhthash;
    private final ReferenceOrder order;
    
    public SearchEvent(final QueryParams query,
                             final yacySeedDB peers,
                             final ResultURLs crawlResults,
                             final TreeMap<byte[], String> preselectedPeerHashes,
                             final boolean generateAbstracts,
                             final LoaderDispatcher loader) {
        this.eventTime = System.currentTimeMillis(); // for lifetime check
        this.peers = peers;
        this.crawlResults = crawlResults;
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
        this.order = new ReferenceOrder(query.ranking, query.targetlang);
        boolean remote = (query.domType == QueryParams.SEARCHDOM_GLOBALDHT) || (query.domType == QueryParams.SEARCHDOM_CLUSTERALL);
        if (remote && peers.sizeConnected() == 0) remote = false;
        final long start = System.currentTimeMillis();
        if (remote) {
        	final int fetchpeers = 12;
            
        	// initialize a ranking process that is the target for data
        	// that is generated concurrently from local and global search threads
            this.rankedCache = new RankingProcess(this.query, this.order, max_results_preparation, fetchpeers + 1);
            
            // start a local search concurrently
            this.rankedCache.start();
                       
            // start global searches
            final long timer = System.currentTimeMillis();
            Log.logFine("SEARCH_EVENT", "STARTING " + fetchpeers + " THREADS TO CATCH EACH " + query.displayResults() + " URLs");
            this.primarySearchThreads = (query.queryHashes.isEmpty()) ? null : yacySearch.primaryRemoteSearches(
                    QueryParams.hashSet2hashString(query.queryHashes),
                    QueryParams.hashSet2hashString(query.excludeHashes),
                    query.prefer,
                    query.urlMask,
                    query.targetlang == null ? "" : query.targetlang,
                    query.sitehash == null ? "" : query.sitehash,
                    query.authorhash == null ? "" : query.authorhash,
                    query.displayResults(),
                    query.maxDistance,
                    query.getSegment(),
                    peers,
                    crawlResults,
                    rankedCache,
                    secondarySearchSuperviser,
                    fetchpeers,
                    Switchboard.urlBlacklist,
                    query.ranking,
                    query.constraint,
                    (query.domType == QueryParams.SEARCHDOM_GLOBALDHT) ? null : preselectedPeerHashes);
            if (this.primarySearchThreads != null) {
                if (this.primarySearchThreads.length > fetchpeers) this.rankedCache.moreFeeders(this.primarySearchThreads.length - fetchpeers);
                EventTracker.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "remote search thread start", this.primarySearchThreads.length, System.currentTimeMillis() - timer), false, 30000, ProfilingGraph.maxTime);
                // finished searching
                Log.logFine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO " + primarySearchThreads.length + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
            } else {
                // no search since query is empty, user might have entered no data or filters have removed all search words
                Log.logFine("SEARCH_EVENT", "NO SEARCH STARTED DUE TO EMPTY SEARCH REQUEST.");
            }
            
            // start worker threads to fetch urls and snippets
            this.results = new ResultFetcher(loader, rankedCache, query, peers, 3000);
        } else {
            // do a local search
            this.rankedCache = new RankingProcess(this.query, this.order, max_results_preparation, 2);
            this.rankedCache.run();
            //CrawlSwitchboard.Finding finding = wordIndex.retrieveURLs(query, false, 2, ranking, process);
            
            if (generateAbstracts) {
                // compute index abstracts
                final long timer = System.currentTimeMillis();
                int maxcount = -1;
                long mindhtdistance = Long.MAX_VALUE, l;
                byte[] wordhash;
                assert this.rankedCache.searchContainerMap() != null;
                for (Map.Entry<byte[], ReferenceContainer<WordReference>> entry : this.rankedCache.searchContainerMap().entrySet()) {
                    wordhash = entry.getKey();
                    final ReferenceContainer<WordReference> container = entry.getValue();
                    assert (Base64Order.enhancedCoder.equal(container.getTermHash(), wordhash)) : "container.getTermHash() = " + new String(container.getTermHash()) + ", wordhash = " + new String(wordhash);
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
                    IACount.put(wordhash, Integer.valueOf(container.size()));
                    IAResults.put(wordhash, ReferenceContainer.compressIndex(container, null, 1000).toString());
                }
                EventTracker.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "abstract generation", this.rankedCache.searchContainerMap().size(), System.currentTimeMillis() - timer), false, 30000, ProfilingGraph.maxTime);
            }
            
            // start worker threads to fetch urls and snippets
            this.results = new ResultFetcher(loader, rankedCache, query, peers, 300);
        }
         
        // clean up events
        SearchEventCache.cleanupEvents(false);
        EventTracker.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "event-cleanup", 0, 0), false, 30000, ProfilingGraph.maxTime);
        
        // store this search to a cache so it can be re-used
        if (MemoryControl.available() < 1024 * 1024 * 10) SearchEventCache.cleanupEvents(true);
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
       this.results.query = query;
   }
   
   public void cleanup() {
       // stop all threads
       if (primarySearchThreads != null) {
           for (yacySearch search : this.primarySearchThreads) {
               if (search.isAlive()) search.interrupt();
           }
       }
       if (secondarySearchThreads != null) {
           for (yacySearch search : this.secondarySearchThreads) {
               if (search.isAlive()) search.interrupt();
           }
       }
       
       // clear all data structures
       if (this.preselectedPeerHashes != null) this.preselectedPeerHashes.clear();
       if (this.localSearchThread != null) if (this.localSearchThread.isAlive()) this.localSearchThread.interrupt();
       if (this.IACount != null) this.IACount.clear();
       if (this.IAResults != null) this.IAResults.clear();
       if (this.heuristics != null) this.heuristics.clear();
       
       // execute deletion of failed words
       int rw = this.results.failedURLs.size();
       if (rw > 0) {
           long start = System.currentTimeMillis();
           final HandleSet removeWords = query.queryHashes;
           try {
               removeWords.putAll(query.excludeHashes);
           } catch (RowSpaceExceededException e1) {
               Log.logException(e1);
           }
           try {
               final Iterator<byte[]> j = removeWords.iterator();
               // remove the same url hashes for multiple words
               while (j.hasNext()) {
                   this.query.getSegment().termIndex().remove(j.next(), this.results.failedURLs);
               }                    
           } catch (IOException e) {
               Log.logException(e);
           }
           Log.logInfo("SearchEvents", "cleaning up event " + query.id(true) + ", removed " + rw + " URL references on " + removeWords.size() + " words in " + (System.currentTimeMillis() - start) + " milliseconds");
       }
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
    
    public yacySearch[] getPrimarySearchThreads() {
        return primarySearchThreads;
    }
    
    public yacySearch[] getSecondarySearchThreads() {
        return secondarySearchThreads;
    }
    
    public RankingProcess getRankingResult() {
        return this.rankedCache;
    }

    public ArrayList<Navigator.Item> getNamespaceNavigator(int maxentries) {
        return this.rankedCache.getNamespaceNavigator(maxentries);
    }
    
    public List<Navigator.Item> getHostNavigator(int maxentries) {
        return this.rankedCache.getHostNavigator(maxentries);
    }
    
    public List<Navigator.Item> getTopicNavigator(final int maxentries) {
        // returns a set of words that are computed as toplist
        return this.rankedCache.getTopicNavigator(maxentries);
    }
    
    public List<Navigator.Item> getAuthorNavigator(final int maxentries) {
        // returns a list of authors so far seen on result set
        return this.rankedCache.getAuthorNavigator(maxentries);
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
    
    public ResultEntry oneResult(final int item) {
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
        return this.results.oneResult(item);
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
        TreeMap<String, TreeMap<String, String>> abstractsCache;
        TreeSet<String> checkedPeers;
        Semaphore trigger;
        
        public SecondarySearchSuperviser() {
            this.abstractsCache = new TreeMap<String, TreeMap<String, String>>();
            this.checkedPeers = new TreeSet<String>();
            this.trigger = new Semaphore(0);
        }
        
        /**
         * add a single abstract to the existing set of abstracts
         * @param wordhash
         * @param singleAbstract // a mapping from url-hashes to a string of peer-hashes
         */
        public void addAbstract(String wordhash, TreeMap<String, String> singleAbstract) {
            synchronized (abstractsCache) {
                TreeMap<String, String> oldAbstract = abstractsCache.get(wordhash); 
                if (oldAbstract == null) {
                    // new abstracts in the cache
                    abstractsCache.put(wordhash, singleAbstract);
                } else synchronized (oldAbstract) {
                    // extend the abstracts in the cache: join the single abstracts
                    for (Map.Entry<String, String> oneref: singleAbstract.entrySet()) {
                        String urlhash = oneref.getKey();
                        String peerlistNew = oneref.getValue();
                        String peerlistOld = oldAbstract.get(urlhash);
                        if (peerlistOld == null) {
                            oldAbstract.put(urlhash, peerlistNew);
                        } else {
                            oldAbstract.put(urlhash, peerlistOld + peerlistNew);
                        }
                    }
                    // abstractsCache.put(wordhash, oldAbstract);
                }
            }
        }
        
        public void commitAbstract() {
            this.trigger.release();
        }
        
        private String wordsFromPeer(final String peerhash, final String urls) {
            Map.Entry<String, TreeMap<String, String>> entry;
            String word, peerlist, url, wordlist = "";
            TreeMap<String, String> urlPeerlist;
            int p;
            boolean hasURL;
            synchronized (this) {
                final Iterator<Map.Entry <String, TreeMap<String, String>>> i = this.abstractsCache.entrySet().iterator();
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
        
        public void run() {
            try {
                while (this.trigger.tryAcquire(10000, TimeUnit.MILLISECONDS)) {
                    // a trigger was released
                    prepareSecondarySearch();
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
            final TreeMap<String, String> abstractJoin = SetTools.joinConstructive(abstractsCache.values(), true);
            if (abstractJoin.isEmpty()) return;
            // the join result is now a urlhash: peer-list relation
            
            // generate a list of peers that have the urls for the joined search result
            final TreeMap<String, String> secondarySearchURLs = new TreeMap<String, String>(); // a (peerhash:urlhash-liststring) mapping
            String url, urls, peer, peerlist;
            final String mypeerhash = peers.mySeed().hash;
            boolean mypeerinvolved = false;
            int mypeercount;
            for (Map.Entry<String, String> entry: abstractJoin.entrySet()) {
                url = entry.getKey();
                peerlist = entry.getValue();
                //System.out.println("DEBUG-INDEXABSTRACT: url " + url + ": from peers " + peerlist);
                mypeercount = 0;
                for (int j = 0; j < peerlist.length(); j += 12) {
                    peer = peerlist.substring(j, j + 12);
                    if ((peer.equals(mypeerhash)) && (mypeercount++ > 1)) continue;
                    //if (peers.indexOf(peer) < j) continue; // avoid doubles that may appear in the abstractJoin
                    urls = secondarySearchURLs.get(peer);
                    urls = (urls == null) ? url : urls + url;
                    secondarySearchURLs.put(peer, urls);
                }
                if (mypeercount == 1) mypeerinvolved = true;
            }
            
            // compute words for secondary search and start the secondary searches
            String words;
            secondarySearchThreads = new yacySearch[(mypeerinvolved) ? secondarySearchURLs.size() - 1 : secondarySearchURLs.size()];
            int c = 0;
            for (Map.Entry<String, String> entry: secondarySearchURLs.entrySet()) {
                peer = entry.getKey();
                if (peer.equals(mypeerhash)) continue; // we don't need to ask ourself
                if (checkedPeers.contains(peer)) continue; // do not ask a peer again
                urls = entry.getValue();
                words = wordsFromPeer(peer, urls);
                assert words.length() >= 12 : "words = " + words;
                //System.out.println("DEBUG-INDEXABSTRACT ***: peer " + peer + "   has urls: " + urls + " from words: " + words);
                rankedCache.moreFeeders(1);
                checkedPeers.add(peer);
                secondarySearchThreads[c++] = yacySearch.secondaryRemoteSearch(
                        words, urls, query.getSegment(), peers, crawlResults, rankedCache, peer, Switchboard.urlBlacklist,
                        query.ranking, query.constraint, preselectedPeerHashes);
            }
            
        }
    
    }
    
    public ResultFetcher result() {
        return this.results;
    }
    
}
