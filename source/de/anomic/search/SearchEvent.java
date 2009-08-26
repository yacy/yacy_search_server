// SearchEvent.java
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

package de.anomic.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.crawler.ResultURLs;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.Segment;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.SetTools;
import de.anomic.search.RankingProcess.NavigatorEntry;
import de.anomic.server.serverProfiling;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.dht.FlatWordPartitionScheme;
import de.anomic.yacy.logging.Log;
import de.anomic.ymage.ProfilingGraph;

public final class SearchEvent {
    
    public static final String INITIALIZATION = "initialization";
    public static final String COLLECTION = "collection";
    public static final String JOIN = "join";
    public static final String PRESORT = "presort";
    public static final String URLFETCH = "urlfetch";
    public static final String NORMALIZING = "normalizing";
    public static final String FINALIZATION = "finalization";
    
    private static final int max_results_preparation = 1000;
    
    // class variables that may be implemented with an abstract class
    private long eventTime;
    private QueryParams query;
    private final Segment indexSegment;
    private final yacySeedDB peers;
    private RankingProcess rankedCache; // ordered search results, grows dynamically as all the query threads enrich this container
    private SnippetFetcher snippets;
    
    // class variables for search abstracts
    private final IndexAbstracts rcAbstracts; // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation

    // class variables for remote searches
    private yacySearch[] primarySearchThreads, secondarySearchThreads;
    private final TreeMap<byte[], String> preselectedPeerHashes;
    private ResultURLs crawlResults;
    private Thread localSearchThread;
    private TreeMap<byte[], String> IAResults;
    private TreeMap<byte[], Integer> IACount;
    private byte[] IAmaxcounthash, IAneardhthash;
    
   @SuppressWarnings("unchecked") SearchEvent(final QueryParams query,
                             final Segment indexSegment,
                             final yacySeedDB peers,
                             final ResultURLs crawlResults,
                             final TreeMap<byte[], String> preselectedPeerHashes,
                             final boolean generateAbstracts) {
        this.eventTime = System.currentTimeMillis(); // for lifetime check
        this.indexSegment = indexSegment;
        this.peers = peers;
        this.crawlResults = crawlResults;
        this.query = query;
        this.rcAbstracts = (query.queryHashes.size() > 1) ? new IndexAbstracts() : null; // generate abstracts only for combined searches
        this.primarySearchThreads = null;
        this.secondarySearchThreads = null;
        this.preselectedPeerHashes = preselectedPeerHashes;
        this.IAResults = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
        this.IACount = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        this.IAmaxcounthash = null;
        this.IAneardhthash = null;
        this.localSearchThread = null;
        
        final long start = System.currentTimeMillis();
        if ((query.domType == QueryParams.SEARCHDOM_GLOBALDHT) ||
            (query.domType == QueryParams.SEARCHDOM_CLUSTERALL)) {
            
        	// initialize a ranking process that is the target for data
        	// that is generated concurrently from local and global search threads
            this.rankedCache = new RankingProcess(indexSegment, query, max_results_preparation, 16);
            
            // start a local search concurrently
            this.rankedCache.start();
                       
            // start global searches
            final long timer = System.currentTimeMillis();
            final int fetchpeers = 12;
            Log.logFine("SEARCH_EVENT", "STARTING " + fetchpeers + " THREADS TO CATCH EACH " + query.displayResults() + " URLs");
            this.primarySearchThreads = yacySearch.primaryRemoteSearches(
                    QueryParams.hashSet2hashString(query.queryHashes),
                    QueryParams.hashSet2hashString(query.excludeHashes),
                    "",
                    query.prefer,
                    query.urlMask,
                    query.targetlang == null ? "" : query.targetlang,
                    query.sitehash == null ? "" : query.sitehash,
                    query.authorhash == null ? "" : query.authorhash,
                    query.displayResults(),
                    query.maxDistance,
                    indexSegment,
                    peers,
                    crawlResults,
                    rankedCache,
                    rcAbstracts,
                    fetchpeers,
                    Switchboard.urlBlacklist,
                    query.ranking,
                    query.constraint,
                    (query.domType == QueryParams.SEARCHDOM_GLOBALDHT) ? null : preselectedPeerHashes);
            serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "remote search thread start", this.primarySearchThreads.length, System.currentTimeMillis() - timer), false);
            
            // finished searching
            Log.logFine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO " + primarySearchThreads.length + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        } else {
            // do a local search
            this.rankedCache = new RankingProcess(indexSegment, query, max_results_preparation, 2);
            this.rankedCache.execQuery();
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
                    final ReferenceContainer container = entry.getValue();
                    assert (container.getTermHash().equals(wordhash));
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
                serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "abstract generation", this.rankedCache.searchContainerMap().size(), System.currentTimeMillis() - timer), false);
            }
        }
        
        // start worker threads to fetch urls and snippets
        this.snippets = new SnippetFetcher(rankedCache, query, indexSegment, peers);
        
        // clean up events
        SearchEventCache.cleanupEvents(false);
        serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "event-cleanup", 0, 0), false);
        
        // store this search to a cache so it can be re-used
        if (MemoryControl.available() < 1024 * 1024 * 10) SearchEventCache.cleanupEvents(true);
        SearchEventCache.put(query.id(false), this);
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
   }
   
   public void cleanup() {
       // execute deletion of failed words
       int rw = this.snippets.failedURLs.size();
       if (rw > 0) {
           final TreeSet<byte[]> removeWords = query.queryHashes;
           removeWords.addAll(query.excludeHashes);
           try {
               final Iterator<byte[]> j = removeWords.iterator();
               // remove the same url hashes for multiple words
               while (j.hasNext()) {
                   this.indexSegment.termIndex().remove(j.next(), this.snippets.failedURLs.keySet());
               }                    
           } catch (IOException e) {
               e.printStackTrace();
           }
           Log.logInfo("SearchEvents", "cleaning up event " + query.id(true) + ", removed " + rw + " URL references on " + removeWords.size() + " words");
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
    
    private int countFinishedRemoteSearch() {
        int count = 0;
        // check only primary search threads
        if ((this.primarySearchThreads != null) && (this.primarySearchThreads.length != 0)) {
            for (int i = 0; i < this.primarySearchThreads.length; i++) {
                if ((this.primarySearchThreads[i] == null) || (!(this.primarySearchThreads[i].isAlive()))) count++;
            }
        }
        return count;
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

    public ArrayList<NavigatorEntry> getHostNavigator(int maxentries) {
    	return this.rankedCache.getHostNavigator(maxentries);
    }
    
    public ArrayList<NavigatorEntry> getTopicNavigator(final int maxentries) {
        // returns a set of words that are computed as toplist
        return this.rankedCache.getTopicNavigator(maxentries);
    }
    
    public ArrayList<NavigatorEntry> getAuthorNavigator(final int maxentries) {
        // returns a list of authors so far seen on result set
        return this.rankedCache.getAuthorNavigator(maxentries);
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
            // now wait until as many remote worker threads have finished, as we
            // want to display results
            while (this.primarySearchThreads != null &&
                   this.primarySearchThreads.length > item &&
                   this.snippets.anyWorkerAlive() &&
                   (this.snippets.resultCount() <= item || countFinishedRemoteSearch() <= item)) {
                try {Thread.sleep(item * 50L);} catch (final InterruptedException e) {}
            }
        }
        return this.snippets.oneResult(item);
    }
    
    boolean secondarySearchStartet = false;
    
    void prepareSecondarySearch() {
        if (secondarySearchStartet) return; // don't do this twice
        
        if ((rcAbstracts == null) || (rcAbstracts.size() != query.queryHashes.size())) return; // secondary search not possible (yet)
        this.secondarySearchStartet = true;
        
        /*        
        // catch up index abstracts and join them; then call peers again to submit their urls
        System.out.println("DEBUG-INDEXABSTRACT: " + rcAbstracts.size() + " word references caught, " + query.queryHashes.size() + " needed");

        Iterator i = rcAbstracts.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            System.out.println("DEBUG-INDEXABSTRACT: hash " + (String) entry.getKey() + ": " + ((query.queryHashes.contains((String) entry.getKey())) ? "NEEDED" : "NOT NEEDED") + "; " + ((TreeMap) entry.getValue()).size() + " entries");
        }
         */
        final TreeMap<String, String> abstractJoin = (rcAbstracts.size() == query.queryHashes.size()) ? SetTools.joinConstructive(rcAbstracts.values(), true) : new TreeMap<String, String>();
        if (abstractJoin.size() != 0) {
            //System.out.println("DEBUG-INDEXABSTRACT: index abstracts delivered " + abstractJoin.size() + " additional results for secondary search");
            // generate query for secondary search
            final TreeMap<String, String> secondarySearchURLs = new TreeMap<String, String>(); // a (peerhash:urlhash-liststring) mapping
            Iterator<Map.Entry<String, String>> i1 = abstractJoin.entrySet().iterator();
            Map.Entry<String, String> entry1;
            String url, urls, peer, ps;
            final String mypeerhash = peers.mySeed().hash;
            boolean mypeerinvolved = false;
            int mypeercount;
            while (i1.hasNext()) {
                entry1 = i1.next();
                url = entry1.getKey();
                ps = entry1.getValue();
                //System.out.println("DEBUG-INDEXABSTRACT: url " + url + ": from peers " + peers);
                mypeercount = 0;
                for (int j = 0; j < ps.length(); j = j + 12) {
                    peer = ps.substring(j, j + 12);
                    if ((peer.equals(mypeerhash)) && (mypeercount++ > 1)) continue;
                    //if (peers.indexOf(peer) < j) continue; // avoid doubles that may appear in the abstractJoin
                    urls = secondarySearchURLs.get(peer);
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
                entry1 = i1.next();
                peer = entry1.getKey();
                if (peer.equals(mypeerhash)) continue; // we dont need to ask ourself
                urls = entry1.getValue();
                words = rcAbstracts.wordsFromPeer(peer, urls);
                assert words.length() >= 12 : "words = " + words;
                //System.out.println("DEBUG-INDEXABSTRACT ***: peer " + peer + "   has urls: " + urls);
                //System.out.println("DEBUG-INDEXABSTRACT ***: peer " + peer + " from words: " + words);
                secondarySearchThreads[c++] = yacySearch.secondaryRemoteSearch(
                        words, "", urls, indexSegment, peers, crawlResults, this.rankedCache, peer, Switchboard.urlBlacklist,
                        query.ranking, query.constraint, preselectedPeerHashes);

            }
        //} else {
            //System.out.println("DEBUG-INDEXABSTRACT: no success using index abstracts from remote peers");
        }
    }
    
    public void remove(final String urlhash) {
        // removes the url hash reference from last search result
        /*indexRWIEntry e =*/ this.rankedCache.remove(urlhash);
        //assert e != null;
    }
    
    public SnippetFetcher result() {
        return this.snippets;
    }
    
}
