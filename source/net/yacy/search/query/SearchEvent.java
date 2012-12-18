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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.yacy.contentcontrol.ContentControlFilterUpdateThread;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.federate.yacy.Distribution;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.Scanner;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.Element;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.WorkTables;
import net.yacy.document.Condenser;
import net.yacy.document.LargeNumberCache;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.index.RowHandleSet;
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
    
    private static long noRobinsonLocalRWISearch = 0;
    static {
        try {
            noRobinsonLocalRWISearch = GenericFormatter.FORMAT_SHORT_DAY.parse("20121107").getTime();
        } catch (ParseException e) {
        }
    }
    
    public static Log log = new Log("SEARCH");

    private static final long maxWaitPerResult = 30;
    public static final int SNIPPET_MAX_LENGTH = 220;
    private final static int SNIPPET_WORKER_THREADS = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    private long eventTime;
    public QueryParams query;
    public final SeedDB peers;
    final WorkTables workTables;
    public  final RankingProcess rankingProcess; // ordered search results, grows dynamically as all the query threads enrich this container
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
    private final AtomicInteger expectedRemoteReferences, maxExpectedRemoteReferences; // counter for referenced that had been sorted out for other reasons
    public final ScoreMap<String> authorNavigator; // a counter for the appearances of authors
    public final ScoreMap<String> namespaceNavigator; // a counter for name spaces
    public final ScoreMap<String> protocolNavigator; // a counter for protocol types
    public final ScoreMap<String> filetypeNavigator; // a counter for file types
    public final Map<String, ScoreMap<String>> vocabularyNavigator; // counters for Vocabularies; key is metatag.getVocabularyName()
    protected final WeakPriorityBlockingQueue<URIMetadataNode> nodeStack;
    protected final WeakPriorityBlockingQueue<ResultEntry>  result;
    protected final LoaderDispatcher                        loader;
    protected final HandleSet                               snippetFetchWordHashes; // a set of word hashes that are used to match with the snippets
    protected final boolean                                 deleteIfSnippetFail;
    private   SnippetWorker[]                               workerThreads;
    private long                                            urlRetrievalAllTime;
    protected long                                          snippetComputationAllTime;
    protected ConcurrentHashMap<String, String> snippets;
    private final boolean remote;
    private boolean cleanupState;

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
        if (MemoryControl.available() < 1024 * 1024 * 100) SearchEventCache.cleanupEvents(false);
        this.eventTime = System.currentTimeMillis(); // for lifetime check
        this.peers = peers;
        this.workTables = workTables;
        this.query = query;
        this.loader = loader;
        this.nodeStack = new WeakPriorityBlockingQueue<URIMetadataNode>(300, false);
        
        this.maxExpectedRemoteReferences = new AtomicInteger(0);
        this.expectedRemoteReferences = new AtomicInteger(0);
        // prepare configured search navigation
        final String navcfg = Switchboard.getSwitchboard().getConfig("search.navigation", "");
        if (navcfg.contains("authors")) {
            this.authorNavigator = new ConcurrentScoreMap<String>();
        } else {
            this.authorNavigator = null;
        }
        if (navcfg.contains("namespace")) {
            this.namespaceNavigator = new ConcurrentScoreMap<String>();
        } else {
            this.namespaceNavigator = null;
        }
        this.protocolNavigator = new ConcurrentScoreMap<String>();
        this.filetypeNavigator = new ConcurrentScoreMap<String>();
        this.vocabularyNavigator = new ConcurrentHashMap<String, ScoreMap<String>>();
        
        this.snippets = new ConcurrentHashMap<String, String>();
            
        this.secondarySearchSuperviser =
            (this.query.getQueryGoal().getIncludeHashes().size() > 1) ? new SecondarySearchSuperviser(this) : null; // generate abstracts only for combined searches
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
        this.remote = 
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
        if (this.remote || this.peers.mySeed().getBirthdate() < noRobinsonLocalRWISearch) {
            // we start the local search only if this peer is doing a remote search or when it is doing a local search and the peer is old
            this.rankingProcess.start();
        }

        if (this.remote) {
            // start global searches
            final long timer = System.currentTimeMillis();
            if (this.query.getQueryGoal().getIncludeHashes().isEmpty()) {
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
                EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.REMOTESEARCH_START, "", this.primarySearchThreadsL.size(), System.currentTimeMillis() - timer), false);
                // finished searching
                Log.logFine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO " + this.primarySearchThreadsL.size() + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
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
                EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ABSTRACTS, "", this.rankingProcess.searchContainerMap().size(), System.currentTimeMillis() - timer), false);
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
        this.deleteIfSnippetFail = deleteIfSnippetFail;
        this.cleanupState = false;
        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.result = new WeakPriorityBlockingQueue<ResultEntry>(Math.max(1000, 10 * query.itemsPerPage()), true); // this is the result, enriched with snippets, ranked and ordered by ranking

        // snippets do not need to match with the complete query hashes,
        // only with the query minus the stopwords which had not been used for the search
        HandleSet filtered;
        try {
            filtered = RowHandleSet.joinConstructive(query.getQueryGoal().getIncludeHashes(), Switchboard.stopwordHashes);
        } catch (final SpaceExceededException e) {
            Log.logException(e);
            filtered = new RowHandleSet(query.getQueryGoal().getIncludeHashes().keylen(), query.getQueryGoal().getIncludeHashes().comparator(), 0);
        }
        this.snippetFetchWordHashes = query.getQueryGoal().getIncludeHashes().clone();
        if (filtered != null && !filtered.isEmpty()) {
            this.snippetFetchWordHashes.excludeDestructive(Switchboard.stopwordHashes);
        }

        // start worker threads to fetch urls and snippets
        this.workerThreads = null;
        deployWorker(Math.min(SNIPPET_WORKER_THREADS, query.itemsPerPage), query.neededResults());
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(query.id(true), SearchEventType.SNIPPETFETCH_START, ((this.workerThreads == null) ? "no" : this.workerThreads.length) + " online snippet fetch threads started", 0, 0), false);
        
        // clean up events
        SearchEventCache.cleanupEvents(false);
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.CLEANUP, "", 0, 0), false);

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

    protected void cleanup() {
        this.cleanupState = true;

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
        for ( final SnippetWorker w : this.workerThreads ) {
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
        if (this.preselectedPeerHashes != null) this.preselectedPeerHashes.clear();
        if (this.localSearchThread != null && this.localSearchThread.isAlive()) this.localSearchThread.interrupt();
        if (this.IACount != null) this.IACount.clear();
        if (this.IAResults != null) this.IAResults.clear();
        if (this.heuristics != null) this.heuristics.clear();
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

    protected boolean workerAlive() {
        if ( this.workerThreads == null ) {
            return false;
        }
        for ( final SnippetWorker w : this.workerThreads ) {
            if ( w != null && w.isAlive() ) {
                return true;
            }
        }
        return false;
    }
    
    public void add(
        final List<URIMetadataNode> index,
        final Map<String, ReversibleScoreMap<String>> facets, // a map from a field name to scored values
        final Map<String, String> solrsnippets, // a map from urlhash to snippet text
        final boolean local,
        final String resourceName,
        final int fullResource) {

        this.rankingProcess.addBegin();
        this.snippets.putAll(solrsnippets);
        assert (index != null);
        if (index.isEmpty()) return;

        if (local) {
            this.query.local_solr_stored.set(fullResource);
        } else {
            assert fullResource >= 0 : "fullResource = " + fullResource;
            this.query.remote_stored.addAndGet(fullResource);
            this.query.remote_peerCount.incrementAndGet();
        }

        long timer = System.currentTimeMillis();

        // normalize entries
        int is = index.size();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.NORMALIZING, resourceName, is, System.currentTimeMillis() - timer), false);
        if (!local) {
            this.rankingProcess.receivedRemoteReferences.addAndGet(is);
        }

        // iterate over normalized entries and select some that are better than currently stored
        timer = System.currentTimeMillis();

        // collect navigation information
        ReversibleScoreMap<String> fcts = facets.get(YaCySchema.host_s.getSolrFieldName());
        if (fcts != null) this.rankingProcess.hostNavigator.inc(fcts);

        if (this.filetypeNavigator != null) {
            fcts = facets.get(YaCySchema.url_file_ext_s.getSolrFieldName());
            if (fcts != null) this.filetypeNavigator.inc(fcts);
        }

        if (this.protocolNavigator != null) {
            fcts = facets.get(YaCySchema.url_protocol_s.getSolrFieldName());
            if (fcts != null) this.protocolNavigator.inc(fcts);
        }
        //fcts = facets.get(YaCySchema.author.getSolrFieldName());
        //if (fcts != null) this.authorNavigator.inc(fcts);
        
        // get the vocabulary navigation
        for (Tagging v: LibraryProvider.autotagging.getVocabularies()) {
            fcts = facets.get(YaCySchema.VOCABULARY_PREFIX + v.getName() + YaCySchema.VOCABULARY_SUFFIX);
            if (fcts != null) {
                ScoreMap<String> vocNav = this.vocabularyNavigator.get(v.getName());
                if (vocNav == null) {
                    vocNav = new ConcurrentScoreMap<String>();
                    this.vocabularyNavigator.put(v.getName(), vocNav);
                }
                vocNav.inc(fcts);
            }
        }
        
        
        // apply all constraints
        try {
            pollloop: for (URIMetadataNode iEntry: index) {
                
                if ( !this.query.urlMask_isCatchall ) {
                    // check url mask
                    if (!iEntry.matches(this.query.urlMask)) {
                        continue pollloop;
                    }
                }
                
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
                if ( this.query.nav_sitehash == null ) {
                    if (this.query.siteexcludes != null && this.query.siteexcludes.contains(hosthash)) {
                        continue pollloop;
                    }
                } else {
                    // filter out all domains that do not match with the site constraint
                    if (!hosthash.equals(this.query.nav_sitehash)) continue pollloop;
                }

                // check vocabulary constraint
                /*
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
                */
                // add navigators using the triplestore
                /*
                for (Map.Entry<String, String> v: this.rankingProcess.taggingPredicates.entrySet()) {
                    Iterator<RDFNode> ni = JenaTripleStore.getObjects(resource, v.getValue());
                    while (ni.hasNext()) {
                        String[] tags = CommonPattern.COMMA.split(ni.next().toString());
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
                */

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
                if (local) this.query.local_solr_available.incrementAndGet(); else this.query.remote_available.incrementAndGet();
            }
        } catch ( final SpaceExceededException e ) {
        }
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.PRESORT, resourceName, index.size(), System.currentTimeMillis() - timer), false);
    }
    
    private long waitTimeRecommendation() {
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
        if (this.rankingProcess.doubleDomCache.isEmpty()) {
            //Log.logWarning("RWIProcess", "doubleDomCache.isEmpty");
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
            //Log.logWarning("RWIProcess", "bestEntry == null (1)");
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
            //Log.logWarning("RWIProcess", "bestEntry == null (2)");
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
                //Log.logWarning("RWIProcess", "takeRWI returned null");
                return null; // all time was already wasted in takeRWI to get another element
            }

            if ( !this.query.urlMask_isCatchall ) {
                // check url mask
                if ( !page.matches(this.query.urlMask) ) {
                    this.query.misses.add(page.hash());
                    continue;
                }
            }

            // check for more errors
            if ( page.url() == null ) {
                this.query.misses.add(page.hash());
                continue; // rare case where the url is corrupted
            }

            // check content domain
            if (((this.query.contentdom == Classification.ContentDomain.TEXT && page.url().getContentDomain() == Classification.ContentDomain.IMAGE) ||
                (this.query.contentdom == Classification.ContentDomain.IMAGE && page.url().getContentDomain() != Classification.ContentDomain.IMAGE) ||
                (this.query.contentdom == Classification.ContentDomain.AUDIO && page.url().getContentDomain() != Classification.ContentDomain.AUDIO) ||
                (this.query.contentdom == Classification.ContentDomain.VIDEO && page.url().getContentDomain() != Classification.ContentDomain.VIDEO) ||
                (this.query.contentdom == Classification.ContentDomain.APP && page.url().getContentDomain() != Classification.ContentDomain.APP)) && this.query.urlMask_isCatchall) {
                this.query.misses.add(page.hash());
                continue;
            }
            
            // Check for blacklist
            if (Switchboard.urlBlacklist.isListed(BlacklistType.SEARCH, page)) {
                this.query.misses.add(page.hash());
                continue;
            }

			// contentcontrol
			if (Switchboard.getSwitchboard().getConfigBool(
					"contentcontrol.enabled", false) == true) {

				FilterEngine f = ContentControlFilterUpdateThread
						.getNetworkFilter();
				if (f != null) {
					if (!f.isListed(page.url(), null)) {
						this.query.misses.add(page.hash());
						continue;
					}
				}

			}

            final String pageurl = page.url().toNormalform(true);
            final String pageauthor = page.dc_creator();
            final String pagetitle = page.dc_title().toLowerCase();

            // check exclusion
            if ( !this.query.getQueryGoal().getExcludeHashes().isEmpty() &&
                ((QueryParams.anymatch(pagetitle, this.query.getQueryGoal().getExcludeHashes()))
                || (QueryParams.anymatch(pageurl.toLowerCase(), this.query.getQueryGoal().getExcludeHashes()))
                || (QueryParams.anymatch(pageauthor.toLowerCase(), this.query.getQueryGoal().getExcludeHashes())))) {
                this.query.misses.add(page.hash());
                continue;
            }

            // check index-of constraint
            if ((this.query.constraint != null) && (this.query.constraint.get(Condenser.flag_cat_indexof)) && (!(pagetitle.startsWith("index of")))) {
                final Iterator<byte[]> wi = this.query.getQueryGoal().getIncludeHashes().iterator();
                while ( wi.hasNext() ) {
                    this.query.getSegment().termIndex().removeDelayed(wi.next(), page.hash());
                }
                this.query.misses.add(page.hash());
                continue;
            }

            // check location constraint
            if ((this.query.constraint != null) && (this.query.constraint.get(Condenser.flag_cat_haslocation)) && (page.lat() == 0.0f || page.lon() == 0.0f)) {
                this.query.misses.add(page.hash());
                continue;
            }

            // check geo coordinates
            double lat, lon;
            if (this.query.radius > 0.0d && this.query.lat != 0.0d && this.query.lon != 0.0d && (lat = page.lat()) != 0.0d && (lon = page.lon()) != 0.0d) {
                double latDelta = this.query.lat - lat;
                double lonDelta = this.query.lon - lon;
                double distance = Math.sqrt(latDelta * latDelta + lonDelta * lonDelta); // pythagoras
                if (distance > this.query.radius) {
                    this.query.misses.add(page.hash());
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
                    this.query.misses.add(page.hash());
                    continue;
                }

                // add author to the author navigator
                if (this.authorNavigator != null) this.authorNavigator.inc(pageauthor);
            } else if ( this.query.authorhash != null ) {
                this.query.misses.add(page.hash());
                continue;
            }

            // check Scanner
            if ( !Scanner.acceptURL(page.url()) ) {
                this.query.misses.add(page.hash());
                continue;
            }

            // from here: collect navigation information

            // namespace navigation
            if (this.namespaceNavigator != null) {
                String pagepath = page.url().getPath();
                if ((p = pagepath.indexOf(':')) >= 0) {
                    pagepath = pagepath.substring(0, p);
                    p = pagepath.lastIndexOf('/');
                    if (p >= 0) {
                        pagepath = pagepath.substring(p + 1);
                        this.namespaceNavigator.inc(pagepath);
                    }
                }
            }

            return page; // accept url
        }
        Log.logWarning("RWIProcess", "loop terminated");
        return null;
    }
    
    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }

    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }

    public ResultEntry oneResult(final int item, final long timeout) {
        // if there is not yet a worker alive, start one
        if (!anyWorkerAlive()) {
            deployWorker(Math.min(SNIPPET_WORKER_THREADS, this.query.itemsPerPage), this.query.neededResults());
        }
        // wait until local data is there
        while (this.localsearch != null && this.localsearch.isAlive() && this.result.sizeAvailable() < item) try {this.localsearch.join(10);} catch (InterruptedException e) {}
        // check if we already retrieved this item
        // (happens if a search pages is accessed a second time)
        final long finishTime = System.currentTimeMillis() + timeout;
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "started, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
        
        // we must wait some time until the first result page is full to get enough elements for ranking
        final long waittimeout = System.currentTimeMillis() + 300;
        if (this.remote && item < 10 && !this.rankingProcess.feedingIsFinished()) {
            // the first 10 results have a very special timing to get most of the remote results ordered
            // before they are presented on the first lines .. yes sleeps seem to be bad. but how shall we predict how long other
            // peers will take until they respond?
            long sleep = item == 0 ? 400 : (10 - item) * 9; // the first result takes the longest time
            try { Thread.sleep(sleep); } catch (final InterruptedException e1) { Log.logException(e1); }
        }
        int thisRankingQueueSize, lastRankingQueueSize = 0;
        if (item < 10) {
            while (
              ((thisRankingQueueSize = this.rankingProcess.rwiQueueSize()) > 0 || !this.rankingProcess.feedingIsFinished()) &&
               (thisRankingQueueSize > lastRankingQueueSize || this.result.sizeAvailable() < item + 1) &&
               System.currentTimeMillis() < waittimeout &&
               anyWorkerAlive()
              ) {
                // wait a little time to get first results in the search
                lastRankingQueueSize = thisRankingQueueSize;
                try { Thread.sleep(20); } catch (final InterruptedException e1) {}
            }
        }

        if (this.result.sizeAvailable() > item) {
            // we have the wanted result already in the result array .. return that
            final ResultEntry re = this.result.element(item).getElement();
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "prefetched, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
            return re;
        }

        // finally wait until enough results are there produced from the snippet fetch process
        WeakPriorityBlockingQueue.Element<ResultEntry> entry = null;
        while (System.currentTimeMillis() < finishTime) {
            Log.logInfo("SnippetProcess", "item = " + item + "; anyWorkerAlive=" + anyWorkerAlive() + "; this.rankingProcess.isAlive() = " + this.rankingProcess.isAlive() + "; this.rankingProcess.feedingIsFinished() = " + this.rankingProcess.feedingIsFinished() + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.rankingProcess.rwiQueueSize() + ", this.rankingProcess.nodeStack.sizeAvailable() = " + this.nodeStack.sizeAvailable());

            if (!anyWorkerAlive() && !this.rankingProcess.isAlive() && this.result.sizeAvailable() + this.rankingProcess.rwiQueueSize() + this.nodeStack.sizeAvailable() <= item && this.rankingProcess.feedingIsFinished()) {
                break; // the fail case
            }

            // deploy worker to get more results
            if (!anyWorkerAlive()) {
                deployWorker(Math.min(SNIPPET_WORKER_THREADS, this.query.itemsPerPage), this.query.neededResults());
            }

            try {entry = this.result.element(item, 50);} catch (final InterruptedException e) {break;}
            if (entry != null) break;
        }

        // finally, if there is something, return the result
        if (entry == null) {
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "not found, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
            return null;
        }
        final ResultEntry re = entry.getElement();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "retrieved, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
        if (item == this.query.offset + this.query.itemsPerPage - 1) stopAllWorker(); // we don't need more
        return re;
    }

    public ArrayList<WeakPriorityBlockingQueue.Element<ResultEntry>> completeResults(final long waitingtime) {
        final long timeout = System.currentTimeMillis() + waitingtime;
        while (this.result.sizeAvailable() < this.query.neededResults() && anyWorkerAlive() && System.currentTimeMillis() < timeout) {
            try {Thread.sleep(10);} catch (final InterruptedException e) {}
        }
        return this.result.list(Math.min(this.query.neededResults(), this.result.sizeAvailable()));
    }


    private void deployWorker(int deployCount, final int neededResults) {
        if (this.cleanupState ||
            (this.rankingProcess.feedingIsFinished() && this.rankingProcess.rwiQueueSize() == 0 && this.nodeStack.sizeAvailable() == 0) ||
            this.result.sizeAvailable() >= neededResults) {
            return;
        }
        SnippetWorker worker;
        if (this.workerThreads == null) {
            this.workerThreads = new SnippetWorker[deployCount];
            synchronized(this.workerThreads) {try {
                for (int i = 0; i < this.workerThreads.length; i++) {
                    if (this.result.sizeAvailable() >= neededResults ||
                        (this.rankingProcess.feedingIsFinished() && this.rankingProcess.rwiQueueSize() == 0) && this.nodeStack.sizeAvailable() == 0) {
                        break;
                    }
                    worker = new SnippetWorker(this, this.query.maxtime, this.query.snippetCacheStrategy, neededResults);
                    worker.start();
                    this.workerThreads[i] = worker;
                    if (this.expectedRemoteReferences.get() > 0) {
                        long wait = this.waitTimeRecommendation();
                        if (wait > 0) {
                            try {Thread.sleep(wait);} catch ( InterruptedException e ) {}
                        }
                    }
                }
            } catch (OutOfMemoryError e) {}}
        } else {
            // there are still worker threads running, but some may be dead.
            // if we find dead workers, reanimate them
            synchronized(this.workerThreads) {
                for (int i = 0; i < this.workerThreads.length; i++) {
                    if (deployCount <= 0 ||
                        this.result.sizeAvailable() >= neededResults ||
                        (this.rankingProcess.feedingIsFinished() && this.rankingProcess.rwiQueueSize() == 0) && this.nodeStack.sizeAvailable() == 0) {
                        break;
                    }
                    if (this.workerThreads[i] == null || !this.workerThreads[i].isAlive()) {
                        worker = new SnippetWorker(this, this.query.maxtime, this.query.snippetCacheStrategy, neededResults);
                        worker.start();
                        this.workerThreads[i] = worker;
                        deployCount--;
                    }
                    if (this.expectedRemoteReferences.get() > 0) {
                        long wait = this.waitTimeRecommendation();
                        if (wait > 0) {
                            try {Thread.sleep(wait);} catch ( InterruptedException e ) {}
                        }
                    }
                }
            }
        }
    }

    private void stopAllWorker() {
        synchronized(this.workerThreads) {
            for (int i = 0; i < this.workerThreads.length; i++) {
               if (this.workerThreads[i] == null || !this.workerThreads[i].isAlive()) {
                continue;
            }
               this.workerThreads[i].pleaseStop();
               this.workerThreads[i].interrupt();
            }
        }
    }

   private boolean anyWorkerAlive() {
        if (this.workerThreads == null || this.workerThreads.length == 0) {
            return false;
        }
        synchronized(this.workerThreads) {
            for (final SnippetWorker workerThread : this.workerThreads) {
               if ((workerThread != null) && (workerThread.isAlive()) && (workerThread.busytime() < 10000)) return true;
            }
        }
        return false;
    }

    /**
     * delete a specific entry from the search results
     * this is used if the user clicks on a '-' sign beside the search result
     * @param urlhash
     * @return true if an entry was deleted, false otherwise
     */
    protected boolean delete(final String urlhash) {
        final Iterator<Element<ResultEntry>> i = this.result.iterator();
        Element<ResultEntry> entry;
        while (i.hasNext()) {
            entry = i.next();
            if (urlhash.equals(ASCII.String(entry.getElement().url().hash()))) {
                i.remove();
                return true;
            }
        }
        return false;
    }

}
