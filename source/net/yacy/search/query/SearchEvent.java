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

package net.yacy.search.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.yacy.Distribution;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.data.WorkTables;
import net.yacy.document.LargeNumberCache;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.SetTools;
import net.yacy.peers.RemoteSearch;
import net.yacy.peers.SeedDB;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.query.SnippetProcess.Worker;
import net.yacy.search.ranking.ReferenceOrder;
import net.yacy.search.snippet.ResultEntry;

public final class SearchEvent {

    public enum Type {
        INITIALIZATION,
        COLLECTION,
        JOIN,
        PRESORT,
        URLFETCH,
        NORMALIZING,
        FINALIZATION,
        REMOTESEARCH_START,
        REMOTESEARCH_TERMINATE,
        ABSTRACTS,
        CLEANUP,
        SNIPPETFETCH_START,
        ONERESULT,
        REFERENCECOLLECTION,
        RESULTLIST;
    }

    // class variables that may be implemented with an abstract class
    private long eventTime;
    private QueryParams query;
    public final SeedDB peers;
    private final WorkTables workTables;
    public  final RWIProcess rankingProcess; // ordered search results, grows dynamically as all the query threads enrich this container
    private final SnippetProcess resultFetcher;

    public final SecondarySearchSuperviser secondarySearchSuperviser;

    // class variables for remote searches
    public final List<RemoteSearch> primarySearchThreadsL;
    private Thread[] secondarySearchThreads;
    public final SortedMap<byte[], String> preselectedPeerHashes;
    private final Thread localSearchThread;
    private final SortedMap<byte[], Integer> IACount;
    private final SortedMap<byte[], String> IAResults;
    private final SortedMap<byte[], HeuristicResult> heuristics;
    private byte[] IAmaxcounthash, IAneardhthash;
    private final ReferenceOrder order;

    protected SearchEvent(
        final QueryParams query,
        final SeedDB peers,
        final WorkTables workTables,
        final SortedMap<byte[], String> preselectedPeerHashes,
        final boolean generateAbstracts,
        final LoaderDispatcher loader,
        final int remote_maxcount,
        final long remote_maxtime,
        final int burstRobinsonPercent,
        final int burstMultiwordPercent,
        final boolean deleteIfSnippetFail) {
        if ( MemoryControl.available() < 1024 * 1024 * 100 ) {
            SearchEventCache.cleanupEvents(false);
        }
        this.eventTime = System.currentTimeMillis(); // for lifetime check
        this.peers = peers;
        this.workTables = workTables;
        this.query = query;
        this.secondarySearchSuperviser =
            (this.query.query_include_hashes.size() > 1) ? new SecondarySearchSuperviser() : null; // generate abstracts only for combined searches
        if ( this.secondarySearchSuperviser != null ) {
            this.secondarySearchSuperviser.start();
        }
        this.secondarySearchThreads = null;
        this.preselectedPeerHashes = preselectedPeerHashes;
        this.IAResults = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
        this.IACount = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        this.heuristics = new TreeMap<byte[], HeuristicResult>(Base64Order.enhancedCoder);
        this.IAmaxcounthash = null;
        this.IAneardhthash = null;
        this.localSearchThread = null;
        this.order = new ReferenceOrder(this.query.ranking, UTF8.getBytes(this.query.targetlang));
        final boolean remote =
            (peers != null && peers.sizeConnected() > 0)
                && (this.query.domType == QueryParams.Searchdom.CLUSTER || (this.query.domType == QueryParams.Searchdom.GLOBAL && peers
                    .mySeed()
                    .getFlagAcceptRemoteIndex()));
        final long start = System.currentTimeMillis();

        // prepare a local RWI search
        // initialize a ranking process that is the target for data
        // that is generated concurrently from local and global search threads
        this.rankingProcess = new RWIProcess(this.query, this.order, remote);

        // start a local solr search
        RemoteSearch.solrRemoteSearch(this, 1000, 1000, null /*this peer*/, Switchboard.urlBlacklist);

        // start a local RWI search concurrently
        this.rankingProcess.start();

        if ( remote ) {
            // start global searches
            final long timer = System.currentTimeMillis();
            if (this.query.query_include_hashes.isEmpty()) {
                this.primarySearchThreadsL = null;
            } else {
                this.primarySearchThreadsL = new ArrayList<RemoteSearch>();
                // start this concurrently because the remote search needs an enumeration
                // of the remote peers which may block in some cases when i.e. DHT is active
                // at the same time.
                new Thread() {
                    @Override
                    public void run() {
                        Thread.currentThread().setName("SearchEvent.primaryRemoteSearches");
                        RemoteSearch.primaryRemoteSearches(
                        	SearchEvent.this,
                            remote_maxcount,
                            remote_maxtime,
                            Switchboard.urlBlacklist,
                            (SearchEvent.this.query.domType == QueryParams.Searchdom.GLOBAL) ? null : preselectedPeerHashes,
                            burstRobinsonPercent,
                            burstMultiwordPercent);
                    }
                }.start();
            }
            if ( this.primarySearchThreadsL != null ) {
                Log.logFine("SEARCH_EVENT", "STARTING "
                    + this.primarySearchThreadsL.size()
                    + " THREADS TO CATCH EACH "
                    + remote_maxcount
                    + " URLs");
                EventTracker.update(
                    EventTracker.EClass.SEARCH,
                    new ProfilingGraph.EventSearch(
                        this.query.id(true),
                        Type.REMOTESEARCH_START,
                        "",
                        this.primarySearchThreadsL.size(),
                        System.currentTimeMillis() - timer),
                    false);
                // finished searching
                Log.logFine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO "
                    + this.primarySearchThreadsL.size()
                    + " PEERS: "
                    + ((System.currentTimeMillis() - start) / 1000)
                    + " seconds");
            } else {
                // no search since query is empty, user might have entered no data or filters have removed all search words
                Log.logFine("SEARCH_EVENT", "NO SEARCH STARTED DUE TO EMPTY SEARCH REQUEST.");
            }
        } else {
            this.primarySearchThreadsL = null;
            if ( generateAbstracts ) {
                // we need the results now
                try {
                    this.rankingProcess.join();
                } catch ( final Throwable e ) {
                }
                // compute index abstracts
                final long timer = System.currentTimeMillis();
                int maxcount = -1;
                long mindhtdistance = Long.MAX_VALUE, l;
                byte[] wordhash;
                assert this.rankingProcess.searchContainerMap() != null;
                for ( final Map.Entry<byte[], ReferenceContainer<WordReference>> entry : this.rankingProcess
                    .searchContainerMap()
                    .entrySet() ) {
                    wordhash = entry.getKey();
                    final ReferenceContainer<WordReference> container = entry.getValue();
                    assert (Base64Order.enhancedCoder.equal(container.getTermHash(), wordhash)) : "container.getTermHash() = "
                        + ASCII.String(container.getTermHash())
                        + ", wordhash = "
                        + ASCII.String(wordhash);
                    if ( container.size() > maxcount ) {
                        this.IAmaxcounthash = wordhash;
                        maxcount = container.size();
                    }
                    l = Distribution.horizontalDHTDistance(wordhash, ASCII.getBytes(peers.mySeed().hash));
                    if ( l < mindhtdistance ) {
                        // calculate the word hash that is closest to our dht position
                        mindhtdistance = l;
                        this.IAneardhthash = wordhash;
                    }
                    this.IACount.put(wordhash, LargeNumberCache.valueOf(container.size()));
                    this.IAResults.put(wordhash, WordReferenceFactory
                        .compressIndex(container, null, 1000)
                        .toString());
                }
                EventTracker.update(
                    EventTracker.EClass.SEARCH,
                    new ProfilingGraph.EventSearch(
                        this.query.id(true),
                        Type.ABSTRACTS,
                        "",
                        this.rankingProcess.searchContainerMap().size(),
                        System.currentTimeMillis() - timer),
                    false);
            } else {
                // give process time to accumulate a certain amount of data
                // before a reading process wants to get results from it
                try {
                    this.rankingProcess.join(100);
                } catch ( final Throwable e ) {
                }
                // this will reduce the maximum waiting time until results are available to 100 milliseconds
                // while we always get a good set of ranked data
            }
        }

        // start worker threads to fetch urls and snippets
        this.resultFetcher =
            new SnippetProcess(
                loader,
                this.rankingProcess,
                this.query,
                this.peers,
                this.workTables,
                deleteIfSnippetFail,
                remote);

        // clean up events
        SearchEventCache.cleanupEvents(false);
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
            this.query.id(true),
            Type.CLEANUP,
            "",
            0,
            0), false);

        // store this search to a cache so it can be re-used
        if ( MemoryControl.available() < 1024 * 1024 * 100 ) {
            SearchEventCache.cleanupEvents(false);
        }
        SearchEventCache.put(this.query.id(false), this);
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

    public void setQuery(final QueryParams query) {
        this.query = query;
        this.resultFetcher.query = query;
    }

    public void cleanup() {
        this.resultFetcher.setCleanupState();

        // stop all threads
        if ( this.primarySearchThreadsL != null ) {
            for ( final RemoteSearch search : this.primarySearchThreadsL ) {
                if ( search != null ) {
                    synchronized ( search ) {
                        if ( search.isAlive() ) {
                            search.interrupt();
                        }
                    }
                }
            }
        }
        if ( this.secondarySearchThreads != null ) {
            for ( final Thread search : this.secondarySearchThreads ) {
                if ( search != null ) {
                    synchronized ( search ) {
                        if ( search.isAlive() ) {
                            search.interrupt();
                        }
                    }
                }
            }
        }

        // call the worker threads and ask them to stop
        for ( final Worker w : this.resultFetcher.workerThreads ) {
            if ( w != null && w.isAlive() ) {
                w.pleaseStop();
                w.interrupt();
                // the interrupt may occur during a MD5 computation which is resistant against interruption
                // therefore set some more interrupts on the process
                int ic = 10;
                while ( ic-- > 0 & w.isAlive() ) {
                    w.interrupt();
                }
            }
        }

        // clear all data structures
        if ( this.preselectedPeerHashes != null ) {
            this.preselectedPeerHashes.clear();
        }
        if ( this.localSearchThread != null ) {
            if ( this.localSearchThread.isAlive() ) {
                this.localSearchThread.interrupt();
            }
        }
        if ( this.IACount != null ) {
            this.IACount.clear();
        }
        if ( this.IAResults != null ) {
            this.IAResults.clear();
        }
        if ( this.heuristics != null ) {
            this.heuristics.clear();
        }
    }

    public Iterator<Map.Entry<byte[], String>> abstractsString() {
        return this.IAResults.entrySet().iterator();
    }

    public String abstractsString(final byte[] hash) {
        return this.IAResults.get(hash);
    }

    public Iterator<Map.Entry<byte[], Integer>> abstractsCount() {
        return this.IACount.entrySet().iterator();
    }

    public int abstractsCount(final byte[] hash) {
        final Integer i = this.IACount.get(hash);
        if ( i == null ) {
            return -1;
        }
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
        if ( (this.primarySearchThreadsL != null) && (this.primarySearchThreadsL.size() != 0) ) {
            for ( final RemoteSearch primarySearchThread : this.primarySearchThreadsL ) {
                if ( (primarySearchThread != null) && (primarySearchThread.isAlive()) ) {
                    return true;
                }
            }
        }
        // maybe a secondary search thread is alive, check this
        if ( (this.secondarySearchThreads != null) && (this.secondarySearchThreads.length != 0) ) {
            for ( final Thread secondarySearchThread : this.secondarySearchThreads ) {
                if ( (secondarySearchThread != null) && (secondarySearchThread.isAlive()) ) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<RemoteSearch> getPrimarySearchThreads() {
        return this.primarySearchThreadsL;
    }

    public Thread[] getSecondarySearchThreads() {
        return this.secondarySearchThreads;
    }

    public RWIProcess getRankingResult() {
        return this.rankingProcess;
    }

    public ScoreMap<String> getNamespaceNavigator() {
        return this.rankingProcess.getNamespaceNavigator();
    }

    public ScoreMap<String> getHostNavigator() {
        return this.rankingProcess.getHostNavigator();
    }

    public ScoreMap<String> getTopicNavigator(final int count) {
        // returns a set of words that are computed as toplist
        return this.rankingProcess.getTopicNavigator(count);
    }

    public ScoreMap<String> getAuthorNavigator() {
        // returns a list of authors so far seen on result set
        return this.rankingProcess.getAuthorNavigator();
    }

    public ScoreMap<String> getProtocolNavigator() {
        return this.rankingProcess.getProtocolNavigator();
    }

    public ScoreMap<String> getFiletypeNavigator() {
        return this.rankingProcess.getFiletypeNavigator();
    }

    public Map<String,ScoreMap<String>> getVocabularyNavigators() {
        return this.rankingProcess.getVocabularyNavigators();
    }

    public void addHeuristic(final byte[] urlhash, final String heuristicName, final boolean redundant) {
        synchronized ( this.heuristics ) {
            this.heuristics.put(urlhash, new HeuristicResult(urlhash, heuristicName, redundant));
        }
    }

    public HeuristicResult getHeuristic(final byte[] urlhash) {
        synchronized ( this.heuristics ) {
            return this.heuristics.get(urlhash);
        }
    }

    public ResultEntry oneResult(final int item, final long timeout) {
        return this.resultFetcher.oneResult(item, timeout);
    }

    //boolean secondarySearchStartet = false;

    public static class HeuristicResult /*implements Comparable<HeuristicResult>*/
    {
        private final byte[] urlhash;
        public final String heuristicName;
        public final boolean redundant;

        private HeuristicResult(final byte[] urlhash, final String heuristicName, final boolean redundant) {
            this.urlhash = urlhash;
            this.heuristicName = heuristicName;
            this.redundant = redundant;
        }

        public int compareTo(HeuristicResult o) {
            return Base64Order.enhancedCoder.compare(this.urlhash, o.urlhash);
         }

        @Override
        public int hashCode() {
            return (int) Base64Order.enhancedCoder.cardinal(this.urlhash);
        }

        @Override
        public boolean equals(Object o) {
            return Base64Order.enhancedCoder.equal(this.urlhash, ((HeuristicResult) o).urlhash);
        }
    }

    public class SecondarySearchSuperviser extends Thread {

        // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation
        // this relation contains the information where specific urls can be found in specific peers
        private final SortedMap<String, SortedMap<String, Set<String>>> abstractsCache;
        private final SortedSet<String> checkedPeers;
        private final Semaphore trigger;

        public SecondarySearchSuperviser() {
            this.abstractsCache = Collections.synchronizedSortedMap(new TreeMap<String, SortedMap<String, Set<String>>>());
            this.checkedPeers = Collections.synchronizedSortedSet(new TreeSet<String>());
            this.trigger = new Semaphore(0);
        }

        /**
         * add a single abstract to the existing set of abstracts
         *
         * @param wordhash
         * @param singleAbstract // a mapping from url-hashes to a string of peer-hashes
         */
        public void addAbstract(final String wordhash, final SortedMap<String, Set<String>> singleAbstract) {
            final SortedMap<String, Set<String>> oldAbstract = this.abstractsCache.get(wordhash);
            if ( oldAbstract == null ) {
                // new abstracts in the cache
                this.abstractsCache.put(wordhash, singleAbstract);
                return;
            }
            // extend the abstracts in the cache: join the single abstracts
            new Thread() {
                @Override
                public void run() {
                    Thread.currentThread().setName("SearchEvent.addAbstract:" + wordhash);
                    for ( final Map.Entry<String, Set<String>> oneref : singleAbstract.entrySet() ) {
                        final String urlhash = oneref.getKey();
                        final Set<String> peerlistNew = oneref.getValue();
                        final Set<String> peerlistOld = oldAbstract.put(urlhash, peerlistNew);
                        if ( peerlistOld != null ) {
                            peerlistOld.addAll(peerlistNew);
                        }
                    }
                }
            }.start();
            // abstractsCache.put(wordhash, oldAbstract); // put not necessary since it is sufficient to just change the value content (it stays assigned)
        }

        public void commitAbstract() {
            this.trigger.release();
        }

        private Set<String> wordsFromPeer(final String peerhash, final Set<String> urls) {
            Set<String> wordlist = new HashSet<String>();
            String word;
            Set<String> peerlist;
            SortedMap<String, Set<String>> urlPeerlist; // urlhash:peerlist
            for ( Map.Entry<String, SortedMap<String, Set<String>>> entry: this.abstractsCache.entrySet()) {
                word = entry.getKey();
                urlPeerlist = entry.getValue();
                for (String url: urls) {
                    peerlist = urlPeerlist.get(url);
                    if (peerlist != null && peerlist.contains(peerhash)) {
                        wordlist.add(word);
                        break;
                    }
                }
            }
            return wordlist;
        }

        @Override
        public void run() {
            try {
                boolean aquired;
                while ( (aquired = this.trigger.tryAcquire(3000, TimeUnit.MILLISECONDS)) == true ) { // compare to true to remove warning: "Possible accidental assignement"
                    if ( !aquired || MemoryControl.shortStatus()) {
                        break;
                    }
                    // a trigger was released
                    prepareSecondarySearch();
                }
            } catch ( final InterruptedException e ) {
                // the thread was interrupted
                // do nothing
            }
            // the time-out was reached:
            // as we will never again prepare another secondary search, we can flush all cached data
            this.abstractsCache.clear();
            this.checkedPeers.clear();
        }

        private void prepareSecondarySearch() {
            if ( this.abstractsCache == null || this.abstractsCache.size() != SearchEvent.this.query.query_include_hashes.size() ) {
                return; // secondary search not possible (yet)
            }

            // catch up index abstracts and join them; then call peers again to submit their urls
            /*
            System.out.println("DEBUG-INDEXABSTRACT: " + this.abstractsCache.size() + " word references caught, " + SearchEvent.this.query.queryHashes.size() + " needed");
            for (final Map.Entry<String, SortedMap<String, Set<String>>> entry: this.abstractsCache.entrySet()) {
                System.out.println("DEBUG-INDEXABSTRACT: hash " + entry.getKey() + ": " + ((SearchEvent.this.query.queryHashes.has(entry.getKey().getBytes()) ? "NEEDED" : "NOT NEEDED") + "; " + entry.getValue().size() + " entries"));
            }
            */

            // find out if there are enough references for all words that are searched
            if ( this.abstractsCache.size() != SearchEvent.this.query.query_include_hashes.size() ) {
                return;
            }

            // join all the urlhash:peerlist relations: the resulting map has values with a combined peer-list list
            final SortedMap<String, Set<String>> abstractJoin = SetTools.joinConstructive(this.abstractsCache.values(), true);
            if ( abstractJoin.isEmpty() ) {
                return;
                // the join result is now a urlhash: peer-list relation
            }

            // generate a list of peers that have the urls for the joined search result
            final SortedMap<String, Set<String>> secondarySearchURLs = new TreeMap<String, Set<String>>(); // a (peerhash:urlhash-liststring) mapping
            String url;
            Set<String> urls;
            Set<String> peerlist;
            final String mypeerhash = SearchEvent.this.peers.mySeed().hash;
            boolean mypeerinvolved = false;
            int mypeercount;
            for ( final Map.Entry<String, Set<String>> entry : abstractJoin.entrySet() ) {
                url = entry.getKey();
                peerlist = entry.getValue();
                //System.out.println("DEBUG-INDEXABSTRACT: url " + url + ": from peers " + peerlist);
                mypeercount = 0;
                for (String peer: peerlist) {
                    if ( (peer.equals(mypeerhash)) && (mypeercount++ > 1) ) {
                        continue;
                    }
                    //if (peers.indexOf(peer) < j) continue; // avoid doubles that may appear in the abstractJoin
                    urls = secondarySearchURLs.get(peer);
                    if ( urls == null ) {
                        urls = new HashSet<String>();
                        urls.add(url);
                        secondarySearchURLs.put(peer, urls);
                    } else {
                        urls.add(url);
                    }
                    secondarySearchURLs.put(peer, urls);
                }
                if ( mypeercount == 1 ) {
                    mypeerinvolved = true;
                }
            }

            // compute words for secondary search and start the secondary searches
            Set<String> words;
            SearchEvent.this.secondarySearchThreads = new Thread[(mypeerinvolved) ? secondarySearchURLs.size() - 1 : secondarySearchURLs.size()];
            int c = 0;
            for ( final Map.Entry<String, Set<String>> entry : secondarySearchURLs.entrySet() ) {
                String peer = entry.getKey();
                if ( peer.equals(mypeerhash) ) {
                    continue; // we don't need to ask ourself
                }
                if ( this.checkedPeers.contains(peer) ) {
                    continue; // do not ask a peer again
                }
                urls = entry.getValue();
                words = wordsFromPeer(peer, urls);
                if ( words.isEmpty() ) {
                    continue; // ???
                }
                Log.logInfo("SearchEvent.SecondarySearchSuperviser", "asking peer " + peer + " for urls: " + urls + " from words: " + words);
                this.checkedPeers.add(peer);
                SearchEvent.this.secondarySearchThreads[c++] =
                    RemoteSearch.secondaryRemoteSearch(
                    	SearchEvent.this,
                        words,
                        urls.toString(),
                        6000,
                        peer,
                        Switchboard.urlBlacklist);
            }
        }
    }

    public SnippetProcess result() {
        return this.resultFetcher;
    }

    public boolean workerAlive() {
        if ( this.resultFetcher == null || this.resultFetcher.workerThreads == null ) {
            return false;
        }
        for ( final Worker w : this.resultFetcher.workerThreads ) {
            if ( w != null && w.isAlive() ) {
                return true;
            }
        }
        return false;
    }

}
