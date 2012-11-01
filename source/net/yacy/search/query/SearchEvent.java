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
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.Classification;
import net.yacy.cora.document.Classification.ContentDomain;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.federate.yacy.Distribution;
import net.yacy.cora.lod.JenaTripleStore;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.Scanner;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.WorkTables;
import net.yacy.document.Condenser;
import net.yacy.document.LargeNumberCache;
import net.yacy.interaction.contentcontrol.ContentControlFilterUpdateThread;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.RemoteSearch;
import net.yacy.peers.SeedDB;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.repository.FilterEngine;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.snippet.ResultEntry;

public final class SearchEvent {

    private static final long maxWaitPerResult = 30;
    
    private long eventTime;
    protected QueryParams query;
    public final SeedDB peers;
    private final WorkTables workTables;
    public  final RankingProcess rankingProcess; // ordered search results, grows dynamically as all the query threads enrich this container
    private final SnippetProcess resultFetcher;
    public final SecondarySearchSuperviser secondarySearchSuperviser;
    public final List<RemoteSearch> primarySearchThreadsL;
    protected Thread[] secondarySearchThreads;
    public final SortedMap<byte[], String> preselectedPeerHashes;
    private final Thread localSearchThread;
    private final SortedMap<byte[], Integer> IACount;
    private final SortedMap<byte[], String> IAResults;
    private final SortedMap<byte[], HeuristicResult> heuristics;
    private byte[] IAmaxcounthash, IAneardhthash;
    private final Thread localsearch;
    private final AtomicInteger expectedRemoteReferences, maxExpectedRemoteReferences;
    private int sortout; // counter for referenced that had been sorted out for other reasons
    private final ScoreMap<String> authorNavigator; // a counter for the appearances of authors
    private final ScoreMap<String> namespaceNavigator; // a counter for name spaces
    private final ScoreMap<String> protocolNavigator; // a counter for protocol types
    private final ScoreMap<String> filetypeNavigator; // a counter for file types
    
    protected final WeakPriorityBlockingQueue<URIMetadataNode> nodeStack;
    
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
        this.nodeStack = new WeakPriorityBlockingQueue<URIMetadataNode>(300, false);
        
        this.maxExpectedRemoteReferences = new AtomicInteger(0);
        this.expectedRemoteReferences = new AtomicInteger(0);
        this.sortout = 0;
        this.authorNavigator = new ConcurrentScoreMap<String>();
        this.namespaceNavigator = new ConcurrentScoreMap<String>();
        this.protocolNavigator = new ConcurrentScoreMap<String>();
        this.filetypeNavigator = new ConcurrentScoreMap<String>();
        
        this.secondarySearchSuperviser =
            (this.query.query_include_hashes.size() > 1) ? new SecondarySearchSuperviser(this) : null; // generate abstracts only for combined searches
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
        boolean remote =
            (peers != null && peers.sizeConnected() > 0)
                && (this.query.domType == QueryParams.Searchdom.CLUSTER || (this.query.domType == QueryParams.Searchdom.GLOBAL && peers
                    .mySeed()
                    .getFlagAcceptRemoteIndex()));
        final long start = System.currentTimeMillis();

        // prepare a local RWI search
        // initialize a ranking process that is the target for data
        // that is generated concurrently from local and global search threads
        this.rankingProcess = new RankingProcess(this.query, remote);

        // start a local solr search
        this.localsearch = RemoteSearch.solrRemoteSearch(this, 100, null /*this peer*/, Switchboard.urlBlacklist);

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
                        SearchEventType.REMOTESEARCH_START,
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
                        SearchEventType.ABSTRACTS,
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
                this,
                loader,
                this.query,
                this.peers,
                this.workTables,
                deleteIfSnippetFail,
                remote);

        // clean up events
        SearchEventCache.cleanupEvents(false);
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
            this.query.id(true),
            SearchEventType.CLEANUP,
            "",
            0,
            0), false);

        // store this search to a cache so it can be re-used
        if ( MemoryControl.available() < 1024 * 1024 * 100 ) {
            SearchEventCache.cleanupEvents(false);
        }
        SearchEventCache.put(this.query.id(false), this);
    }

    public long getEventTime() {
        return this.eventTime;
    }

    protected void resetEventTime() {
        this.eventTime = System.currentTimeMillis();
    }

    public QueryParams getQuery() {
        return this.query;
    }

    public void setQuery(final QueryParams query) {
        this.query = query;
        this.resultFetcher.query = query;
    }

    protected void cleanup() {
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
        for ( final SnippetWorker w : this.resultFetcher.workerThreads ) {
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

    public List<RemoteSearch> getPrimarySearchThreads() {
        return this.primarySearchThreadsL;
    }

    public Thread[] getSecondarySearchThreads() {
        return this.secondarySearchThreads;
    }

    public RankingProcess getRankingResult() {
        return this.rankingProcess;
    }

    public ScoreMap<String> getHostNavigator() {
        return this.rankingProcess.getHostNavigator();
    }

    public ScoreMap<String> getTopicNavigator(final int count) {
        // returns a set of words that are computed as toplist
        return this.rankingProcess.getTopicNavigator(count);
    }

    public ScoreMap<String> getNamespaceNavigator() {
        if ( !this.query.navigators.equals("all") && this.query.navigators.indexOf("namespace", 0) < 0 ) {
            return new ClusteredScoreMap<String>();
        }
        return this.namespaceNavigator;
    }
    
    public ScoreMap<String> getProtocolNavigator() {
        if ( !this.query.navigators.equals("all") && this.query.navigators.indexOf("protocol", 0) < 0 ) {
            return new ClusteredScoreMap<String>();
        }
        return this.protocolNavigator;
    }

    public ScoreMap<String> getFiletypeNavigator() {
        if ( !this.query.navigators.equals("all") && this.query.navigators.indexOf("filetype", 0) < 0 ) {
            return new ClusteredScoreMap<String>();
        }
        return this.filetypeNavigator;
    }

    public ScoreMap<String> getAuthorNavigator() {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        if ( !this.query.navigators.equals("all") && this.query.navigators.indexOf("authors", 0) < 0 ) {
            return new ConcurrentScoreMap<String>();
        }
        return this.authorNavigator;
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
        if (this.localsearch != null && this.localsearch.isAlive()) try {this.localsearch.join();} catch (InterruptedException e) {}
        return this.resultFetcher.oneResult(item, timeout);
    }

    public SnippetProcess result() {
        return this.resultFetcher;
    }

    protected boolean workerAlive() {
        if ( this.resultFetcher == null || this.resultFetcher.workerThreads == null ) {
            return false;
        }
        for ( final SnippetWorker w : this.resultFetcher.workerThreads ) {
            if ( w != null && w.isAlive() ) {
                return true;
            }
        }
        return false;
    }
    
    public void add(
        final List<URIMetadataNode> index,
        final boolean local,
        final String resourceName,
        final int fullResource) {

        this.rankingProcess.addBegin();
        assert (index != null);
        if (index.isEmpty()) return;

        if (!local) {
            assert fullResource >= 0 : "fullResource = " + fullResource;
            this.rankingProcess.remote_resourceSize += fullResource;
            this.rankingProcess.remote_peerCount++;
        }

        long timer = System.currentTimeMillis();

        // normalize entries
        int is = index.size();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
            this.query.id(true),
            SearchEventType.NORMALIZING,
            resourceName,
            is,
            System.currentTimeMillis() - timer), false);
        if (!local) {
            this.rankingProcess.receivedRemoteReferences.addAndGet(is);
        }

        // iterate over normalized entries and select some that are better than currently stored
        timer = System.currentTimeMillis();
        final boolean nav_hosts = this.query.navigators.equals("all") || this.query.navigators.indexOf("hosts", 0) >= 0;

        // apply all constraints
        try {
            final String pattern = this.query.urlMask.pattern();
            final boolean httpPattern = pattern.equals("http://.*");
            final boolean noHttpButProtocolPattern = pattern.equals("https://.*") || pattern.equals("ftp://.*") || pattern.equals("smb://.*") || pattern.equals("file://.*");
            pollloop: for (URIMetadataNode iEntry: index) {

                // doublecheck for urls
                if (this.rankingProcess.urlhashes.has(iEntry.hash())) {
                    continue pollloop;
                }

                // increase flag counts
                for ( int j = 0; j < 32; j++ ) {
                    if (iEntry.flags().get(j)) this.rankingProcess.flagCount()[j]++;
                }

                // check constraints
                Bitfield flags = iEntry.flags();
                if (!this.rankingProcess.testFlags(flags)) continue pollloop;

                // check document domain
                if (this.query.contentdom.getCode() > 0 &&
                    ((this.query.contentdom == ContentDomain.AUDIO && !(flags.get(Condenser.flag_cat_hasaudio))) || 
                     (this.query.contentdom == ContentDomain.VIDEO && !(flags.get(Condenser.flag_cat_hasvideo))) ||
                     (this.query.contentdom == ContentDomain.IMAGE && !(flags.get(Condenser.flag_cat_hasimage))) ||
                     (this.query.contentdom == ContentDomain.APP && !(flags.get(Condenser.flag_cat_hasapp))))) {
                    continue pollloop;
                }

                // check site constraints
                final String hosthash = iEntry.hosthash();
                if ( this.query.sitehash == null ) {
                    if (this.query.siteexcludes != null && this.query.siteexcludes.contains(hosthash)) {
                        continue pollloop;
                    }
                } else {
                    // filter out all domains that do not match with the site constraint
                    if (!hosthash.equals(this.query.sitehash)) continue pollloop;
                }

                // collect host navigation information (even if we have only one; this is to provide a switch-off button)
                if (this.query.navigators.isEmpty() && (nav_hosts || this.query.urlMask_isCatchall)) {
                    this.rankingProcess.hostNavigator.inc(hosthash);
                    this.rankingProcess.hostResolver.put(hosthash, iEntry.hash());
                }

                // check protocol
                if ( !this.query.urlMask_isCatchall ) {
                    final boolean httpFlagSet = DigestURI.flag4HTTPset(iEntry.hash());
                    if ( httpPattern && !httpFlagSet ) {
                        continue pollloop;
                    }
                    if ( noHttpButProtocolPattern && httpFlagSet ) {
                        continue pollloop;
                    }
                }

                // check vocabulary constraint
                String subject = YaCyMetadata.hashURI(iEntry.hash());
                Resource resource = JenaTripleStore.getResource(subject);
                if (this.query.metatags != null && !this.query.metatags.isEmpty()) {
                    // all metatags must appear in the tags list
                    for (Tagging.Metatag metatag: this.query.metatags) {
                        Iterator<RDFNode> ni = JenaTripleStore.getObjects(resource, metatag.getPredicate());
                        if (!ni.hasNext()) continue pollloop;
                        String tags = ni.next().toString();
                        if (tags.indexOf(metatag.getObject()) < 0) continue pollloop;
                    }
                }

                // add navigators using the triplestore
                for (Map.Entry<String, String> v: this.rankingProcess.taggingPredicates.entrySet()) {
                    Iterator<RDFNode> ni = JenaTripleStore.getObjects(resource, v.getValue());
                    while (ni.hasNext()) {
                        String[] tags = ni.next().toString().split(",");
                        for (String tag: tags) {
                            ScoreMap<String> voc = this.rankingProcess.vocabularyNavigator.get(v.getKey());
                            if (voc == null) {
                                voc = new ConcurrentScoreMap<String>();
                                this.rankingProcess.vocabularyNavigator.put(v.getKey(), voc);
                            }
                            voc.inc(tag);
                        }
                    }
                }

                // finally extend the double-check and insert result to stack
                this.rankingProcess.urlhashes.putUnique(iEntry.hash());
                rankingtryloop: while (true) {
                    try {
                        this.nodeStack.put(new ReverseElement<URIMetadataNode>(iEntry, this.rankingProcess.order.cardinal(iEntry))); // inserts the element and removes the worst (which is smallest)
                        break rankingtryloop;
                    } catch ( final ArithmeticException e ) {
                        // this may happen if the concurrent normalizer changes values during cardinal computation
                        continue rankingtryloop;
                    }
                }
                // increase counter for statistics
                if (local) this.rankingProcess.local_indexCount++; else this.rankingProcess.remote_indexCount++;
            }
        } catch ( final SpaceExceededException e ) {
        }

        //if ((query.neededResults() > 0) && (container.size() > query.neededResults())) remove(true, true);
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
            this.query.id(true),
            SearchEventType.PRESORT,
            resourceName,
            index.size(),
            System.currentTimeMillis() - timer), false);
    }
    
    protected long waitTimeRecommendation() {
        return this.maxExpectedRemoteReferences.get() == 0 ? 0 :
                Math.min(maxWaitPerResult,
                    Math.min(
                        maxWaitPerResult * this.expectedRemoteReferences.get() / this.maxExpectedRemoteReferences.get(),
                        maxWaitPerResult * (100 - Math.min(100, this.rankingProcess.receivedRemoteReferences.get())) / 100));
    }
    
    public void addExpectedRemoteReferences(int x) {
        if ( x > 0 ) {
            this.maxExpectedRemoteReferences.addAndGet(x);
        }
        this.expectedRemoteReferences.addAndGet(x);
    }

    protected boolean expectMoreRemoteReferences() {
        return this.expectedRemoteReferences.get() > 0;
    }
    
    public int getSortOutCount() {
        return this.sortout;
    }

    private URIMetadataNode takeRWI(final boolean skipDoubleDom, final long waitingtime) {

        // returns from the current RWI list the best entry and removes this entry from the list
        WeakPriorityBlockingQueue<WordReferenceVars> m;
        WeakPriorityBlockingQueue.Element<WordReferenceVars> rwi = null;
        WeakPriorityBlockingQueue.Element<URIMetadataNode> page;

        // take one entry from the stack if there are entries on that stack or the feeding is not yet finished
        try {
            int loops = 0; // a loop counter to terminate the reading if all the results are from the same domain
            // wait some time if we did not get so much remote results so far to get a better ranking over remote results
            // we wait at most 30 milliseconds to get a maximum total waiting time of 300 milliseconds for 10 results
            long wait = waitTimeRecommendation();
            if ( wait > 0 ) {
                //System.out.println("*** RWIProcess extra wait: " + wait + "ms; expectedRemoteReferences = " + this.expectedRemoteReferences.get() + ", receivedRemoteReferences = " + this.receivedRemoteReferences.get() + ", initialExpectedRemoteReferences = " + this.maxExpectedRemoteReferences.get());
                Thread.sleep(wait);
            }
            // loop as long as we can expect that we should get more results
            final long timeout = System.currentTimeMillis() + waitingtime;
            while (((!this.rankingProcess.feedingIsFinished() && this.rankingProcess.addRunning()) || this.nodeStack.sizeQueue() > 0 || this.rankingProcess.rwiQueueSize() > 0) &&
                   (this.query.itemsPerPage < 1 || loops++ < this.query.itemsPerPage || (loops > 1000 && !this.rankingProcess.doubleDomCache.isEmpty()))) {
                page = null;
                rwi = null;
                if ( waitingtime <= 0 ) {
                    page = this.rankingProcess.addRunning() ? this.nodeStack.poll(waitingtime) : this.nodeStack.poll();
                    if (page == null) rwi = this.rankingProcess.addRunning() ? this.rankingProcess.rwiStack.poll(waitingtime) : this.rankingProcess.rwiStack.poll();
                } else {
                    timeoutloop: while ( System.currentTimeMillis() < timeout ) {
                        //System.out.println("### RWIProcess feedingIsFinished() = " + feedingIsFinished() + ", this.nodeStack.sizeQueue() = " + this.nodeStack.sizeQueue());
                        if (this.rankingProcess.feedingIsFinished() && this.rankingProcess.rwiQueueSize() == 0 && this.nodeStack.sizeQueue() == 0) break timeoutloop;
                        page = this.nodeStack.poll(50);
                        if (page != null) break timeoutloop;
                        rwi = this.rankingProcess.rwiStack.poll(50);
                        if (rwi != null) break timeoutloop;
                    }
                }
                if (page != null) return page.getElement();
                if (rwi == null) break;
                if (!skipDoubleDom) {
                    return this.query.getSegment().fulltext().getMetadata(rwi.getElement(), rwi.getWeight());
                }

                // check doubledom
                final String hosthash = rwi.getElement().hosthash();
                m = this.rankingProcess.doubleDomCache.get(hosthash);
                if (m == null) {
                    synchronized ( this.rankingProcess.doubleDomCache ) {
                        m = this.rankingProcess.doubleDomCache.get(hosthash);
                        if ( m == null ) {
                            // first appearance of dom. we create an entry to signal that one of that domain was already returned
                            m = new WeakPriorityBlockingQueue<WordReferenceVars>(this.query.snippetCacheStrategy == null || this.query.snippetCacheStrategy == CacheStrategy.CACHEONLY ? RankingProcess.max_results_preparation_special : RankingProcess.max_results_preparation, false);
                            this.rankingProcess.doubleDomCache.put(hosthash, m);
                            return this.query.getSegment().fulltext().getMetadata(rwi.getElement(), rwi.getWeight());
                        }
                        // second appearances of dom
                        m.put(rwi);
                    }
                } else {
                    m.put(rwi);
                }
            }
        } catch ( final InterruptedException e1 ) {
        }
        //Log.logWarning("RWIProcess", "feedingIsFinished() = " + feedingIsFinished());
        //Log.logWarning("RWIProcess", "this.addRunning = " + this.addRunning);
        //Log.logWarning("RWIProcess", "this.nodeStack.sizeQueue() = " + this.nodeStack.sizeQueue());
        //Log.logWarning("RWIProcess", "this.stack.sizeQueue() = " + this.rwiStack.sizeQueue());
        //Log.logWarning("RWIProcess", "this.doubleDomCachee.size() = " + this.doubleDomCache.size());
        if (this.rankingProcess.doubleDomCache.isEmpty()) {
            Log.logWarning("RWIProcess", "doubleDomCache.isEmpty");
            return null;
        }

        // no more entries in sorted RWI entries. Now take Elements from the doubleDomCache
        // find best entry from all caches
        WeakPriorityBlockingQueue.Element<WordReferenceVars> bestEntry = null;
        WeakPriorityBlockingQueue.Element<WordReferenceVars> o;
        final Iterator<WeakPriorityBlockingQueue<WordReferenceVars>> i = this.rankingProcess.doubleDomCache.values().iterator();
        while ( i.hasNext() ) {
            try {
                m = i.next();
            } catch ( final ConcurrentModificationException e ) {
                Log.logException(e);
                continue; // not the best solution...
            }
            if (m == null) continue;
            if (m.isEmpty()) continue;
            if (bestEntry == null) {
                bestEntry = m.peek();
                continue;
            }
            o = m.peek();
            if (o == null) continue;
            if (o.getWeight() < bestEntry.getWeight()) bestEntry = o;
        }
        if (bestEntry == null) {
            Log.logWarning("RWIProcess", "bestEntry == null (1)");
            return null;
        }

        // finally remove the best entry from the doubledom cache
        m = this.rankingProcess.doubleDomCache.get(bestEntry.getElement().hosthash());
        if (m != null) {
            bestEntry = m.poll();
            if (bestEntry != null && m.sizeAvailable() == 0) {
                synchronized ( this.rankingProcess.doubleDomCache ) {
                    if (m.sizeAvailable() == 0) {
                        this.rankingProcess.doubleDomCache.remove(bestEntry.getElement().hosthash());
                    }
                }
            }
        }
        if (bestEntry == null) {
            Log.logWarning("RWIProcess", "bestEntry == null (2)");
            return null;
        }
        return this.query.getSegment().fulltext().getMetadata(bestEntry.getElement(), bestEntry.getWeight());
    }

    /**
     * get one metadata entry from the ranked results. This will be the 'best' entry so far according to the
     * applied ranking. If there are no more entries left or the timeout limit is reached then null is
     * returned. The caller may distinguish the timeout case from the case where there will be no more also in
     * the future by calling this.feedingIsFinished()
     *
     * @param skipDoubleDom should be true if it is wanted that double domain entries are skipped
     * @param waitingtime the time this method may take for a result computation
     * @return a metadata entry for a url
     */
    public URIMetadataNode takeURL(final boolean skipDoubleDom, final long waitingtime) {
        // returns from the current RWI list the best URL entry and removes this entry from the list
        final long timeout = System.currentTimeMillis() + Math.max(10, waitingtime);
        int p = -1;
        long timeleft;
        while ( (timeleft = timeout - System.currentTimeMillis()) > 0 ) {
            //System.out.println("timeleft = " + timeleft);
            final URIMetadataNode page = takeRWI(skipDoubleDom, timeleft);
            if (page == null) {
                Log.logWarning("RWIProcess", "takeRWI returned null");
                return null; // all time was already wasted in takeRWI to get another element
            }

            if ( !this.query.urlMask_isCatchall ) {
                // check url mask
                if ( !page.matches(this.query.urlMask) ) {
                    this.sortout++;
                    continue;
                }
            }

            // check for more errors
            if ( page.url() == null ) {
                this.sortout++;
                continue; // rare case where the url is corrupted
            }

            // check content domain
            if ((this.query.contentdom.getCode() > 0 && page.url().getContentDomain() != this.query.contentdom) ||
                (this.query.contentdom == Classification.ContentDomain.TEXT && page.url().getContentDomain().getCode() > 0)) {
                this.sortout++;
                continue;
            }

            // Check for blacklist
            if (Switchboard.urlBlacklist.isListed(BlacklistType.SEARCH, page)) {
                this.sortout++;
                continue;
            }

            // content control

            if (Switchboard.getSwitchboard().getConfigBool("contentcontrol.enabled", false) == true) {

                // check global network filter from bookmark list
                if (!Switchboard.getSwitchboard()
                        .getConfig("contentcontrol.mandatoryfilterlist", "")
                        .equals("")) {

                    FilterEngine f = ContentControlFilterUpdateThread.getNetworkFilter();
                    if (f != null) {
                        if (!f.isListed(page.url(), null)) {
                            this.sortout++;
                            continue;
                        }
                    }
                }
            }

            final String pageurl = page.url().toNormalform(true);
            final String pageauthor = page.dc_creator();
            final String pagetitle = page.dc_title().toLowerCase();

            // check exclusion
            if ( this.query.query_exclude_hashes != null && !this.query.query_exclude_hashes.isEmpty() &&
                ((QueryParams.anymatch(pagetitle, this.query.query_exclude_hashes))
                || (QueryParams.anymatch(pageurl.toLowerCase(), this.query.query_exclude_hashes))
                || (QueryParams.anymatch(pageauthor.toLowerCase(), this.query.query_exclude_hashes)))) {
                this.sortout++;
                continue;
            }

            // check index-of constraint
            if ( (this.query.constraint != null)
                && (this.query.constraint.get(Condenser.flag_cat_indexof))
                && (!(pagetitle.startsWith("index of"))) ) {
                final Iterator<byte[]> wi = this.query.query_include_hashes.iterator();
                while ( wi.hasNext() ) {
                    this.query.getSegment().termIndex().removeDelayed(wi.next(), page.hash());
                }
                this.sortout++;
                continue;
            }

            // check location constraint
            if ( (this.query.constraint != null)
                && (this.query.constraint.get(Condenser.flag_cat_haslocation))
                && (page.lat() == 0.0f || page.lon() == 0.0f) ) {
                this.sortout++;
                continue;
            }

            // check geo coordinates
            double lat, lon;
            if (this.query.radius > 0.0d && this.query.lat != 0.0d && this.query.lon != 0.0d && (lat = page.lat()) != 0.0d && (lon = page.lon()) != 0.0d) {
                double latDelta = this.query.lat - lat;
                double lonDelta = this.query.lon - lon;
                double distance = Math.sqrt(latDelta * latDelta + lonDelta * lonDelta); // pythagoras
                if (distance > this.query.radius) {
                    this.sortout++;
                    continue;
                }
            }

            // evaluate information of metadata for navigation
            // author navigation:
            if ( pageauthor != null && pageauthor.length() > 0 ) {
                // add author to the author navigator
                final String authorhash = ASCII.String(Word.word2hash(pageauthor));

                // check if we already are filtering for authors
                if ( this.query.authorhash != null && !this.query.authorhash.equals(authorhash) ) {
                    this.sortout++;
                    continue;
                }

                // add author to the author navigator
                this.authorNavigator.inc(pageauthor);
            } else if ( this.query.authorhash != null ) {
                this.sortout++;
                continue;
            }

            // check Scanner
            if ( !Scanner.acceptURL(page.url()) ) {
                this.sortout++;
                continue;
            }

            // from here: collect navigation information

            // collect host navigation information (even if we have only one; this is to provide a switch-off button)
            if (!this.query.navigators.isEmpty() && (this.query.urlMask_isCatchall || this.query.navigators.equals("all") || this.query.navigators.indexOf("hosts", 0) >= 0)) {
                final String hosthash = page.hosthash();
                this.rankingProcess.hostNavigator.inc(hosthash);
                this.rankingProcess.hostResolver.put(hosthash, page.hash());
            }

            // namespace navigation
            String pagepath = page.url().getPath();
            if ( (p = pagepath.indexOf(':')) >= 0 ) {
                pagepath = pagepath.substring(0, p);
                p = pagepath.lastIndexOf('/');
                if ( p >= 0 ) {
                    pagepath = pagepath.substring(p + 1);
                    this.namespaceNavigator.inc(pagepath);
                }
            }

            // protocol navigation
            final String protocol = page.url().getProtocol();
            this.protocolNavigator.inc(protocol);

            // file type navigation
            final String fileext = page.url().getFileExtension();
            if ( fileext.length() > 0 ) {
                this.filetypeNavigator.inc(fileext);
            }

            // accept url
            return page;
        }
        Log.logWarning("RWIProcess", "loop terminated");
        return null;
    }

}
