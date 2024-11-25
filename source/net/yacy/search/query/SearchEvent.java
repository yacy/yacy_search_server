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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.responsewriter.OpensearchResponseWriter;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.federate.yacy.Distribution;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.sorting.ScoreMapUpdatesListener;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.Element;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Response;
import net.yacy.data.WorkTables;
import net.yacy.document.LargeNumberCache;
import net.yacy.document.LibraryProvider;
import net.yacy.document.ProbabilisticClassifier;
import net.yacy.document.Tokenizer;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.TermSearch;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.SetTools;
import net.yacy.peers.RemoteSearch;
import net.yacy.peers.SeedDB;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.search.navigator.Navigator;
import net.yacy.search.navigator.NavigatorPlugins;
import net.yacy.search.ranking.ReferenceOrder;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.search.snippet.TextSnippet.ResultClass;

public final class SearchEvent implements ScoreMapUpdatesListener {

    /** Supported protocols to be displayed in the protocol navigator.
     * (Using here a single String constant is faster than a unmodifiable Set instance) */
    private static final String PROTOCOL_NAVIGATOR_SUPPORTED_VALUES = "http,https,smb,ftp,file";

    private static final int max_results_rwi = 3000;
    private static final int max_results_node = 150;

    /*
    private static long noRobinsonLocalRWISearch = 0;
    static {
        try {
            noRobinsonLocalRWISearch = GenericFormatter.FORMAT_SHORT_DAY.parse("20121107").getTime();
        } catch (final ParseException e) {
        }
    }
     */

    public final static ConcurrentLog log = new ConcurrentLog("SEARCH");

    public static final int SNIPPET_MAX_LENGTH = 220;

    /** Default count of words for topicnavigagtor */
    private static final int MAX_TOPWORDS = 12;

    private long eventTime;
    public QueryParams query;
    public final SeedDB peers;
    final WorkTables workTables;
    public final SecondarySearchSuperviser secondarySearchSuperviser;
    public final List<RemoteSearch> primarySearchThreadsL;
    public final List<Thread> nodeSearchThreads;
    public Thread[] secondarySearchThreads;
    public final SortedSet<byte[]> preselectedPeerHashes;
    private final SortedMap<byte[], Integer> IACount;
    private final SortedMap<byte[], String> IAResults;
    private final SortedMap<byte[], HeuristicResult> heuristics;
    private byte[] IAmaxcounthash, IAneardhthash;
    public Thread rwiProcess;
    public Thread localsolrsearch;

    /** Offset of the next local Solr index request
     * Example : last local request with offset=10 and itemsPerPage=20, sets this attribute to 30. */
    private int localsolroffset;

    /** counter for referenced that had been sorted out for other reasons */
    private final AtomicInteger expectedRemoteReferences, maxExpectedRemoteReferences;

    /** a counter for the appearance of location coordinates .*/
    public final ScoreMap<String> locationNavigator;

    /** a counter for protocol types */
    public final ScoreMap<String> protocolNavigator;

    /** a counter for file types */
    public final ConcurrentScoreMap<String> dateNavigator;

    /** counters for Vocabularies; key is metatag.getVocabularyName() */
    public final Map<String, ScoreMap<String>> vocabularyNavigator;

    /** if 0 no topicNavigator, holds expected number of terms for the topicNavigator */
    private final int topicNavigatorCount;

    /** map of search custom/configured search navigators in addition to above standard navigators (which use special handling or display forms) */
    public final Map<String, Navigator> navigatorPlugins;

    /** Holds the total number of successful write operations performed on all the active navigators since their initialization. */
    private final AtomicLong navGeneration = new AtomicLong();

    private final LoaderDispatcher                        loader;

    /** a set of word hashes that are used to match with the snippets */
    private final HandleSet                               snippetFetchWordHashes;
    /** a set of words that are used to match with the snippets */
    private final Set<String>                             snippetFetchWords;
    private final boolean                                 deleteIfSnippetFail;
    private final long                                          urlRetrievalAllTime;
    private final long                                          snippetComputationAllTime;
    private final ConcurrentHashMap<String, LinkedHashSet<String>> snippets;
    private final boolean remote;

    /** add received results to local index (defult=true) */
    public final boolean addResultsToLocalIndex;

    /** Maximum size allowed (in kbytes) for a remote document result to be stored to local index */
    private long remoteStoredDocMaxSize;
    private SortedMap<byte[], ReferenceContainer<WordReference>> localSearchInclusion;

    /** reference score computation for the commonSense heuristic */
    private final ScoreMap<String> ref;
    private final long maxtime;

    /** key = domhash (6 bytes); value = like stack */
    private final ConcurrentHashMap<String, WeakPriorityBlockingQueue<WordReferenceVars>> doubleDomCache;

    /** flag counter */
    private final int[] flagcount;
    private final AtomicInteger feedersAlive, feedersTerminated, snippetFetchAlive;
    private boolean addRunning;
    private final AtomicInteger receivedRemoteReferences;
    private final ReferenceOrder order;

    /** map for double-check; String/Long relation, addresses ranking number (backreference for deletion) */
    private final HandleSet urlhashes;

    /** a map from tagging vocabulary names to tagging predicate uris */
    private final Map<String, String> taggingPredicates;

    /** thats the bag where the RWI search process writes to. Contains both references from both local and remote RWIs. */
    private final WeakPriorityBlockingQueue<WordReferenceVars> rwiStack;

    /** thats the bag where the solr results are written to */
    private final WeakPriorityBlockingQueue<URIMetadataNode> nodeStack;

    /** thats the result list where the actual search result is waiting to be displayed */
    private final WeakPriorityBlockingQueue<URIMetadataNode>  resultList;

    /** if this is true, then every entry in result List is polled immediately to prevent a re-ranking in the resultList. This is usefull if there is only one index source. */
    private final boolean pollImmediately;
    public  final boolean excludeintext_image;

    // the following values are filled during the search process as statistics for the search
    // In the next comments "filtering" is doubles checking and applying eventual search query constraints/modifiers

    /** the number of hits generated/ranked by the local search in rwi index, after filtering */
    public final AtomicInteger local_rwi_available;

    /** the number of existing hits by the local search in rwi index, before any supplementary filtering */
    public final AtomicInteger local_rwi_stored;

    /** the number of hits imported from remote peers (rwi/solr mixed + eventual site heuristics), after filtering */
    public final AtomicInteger remote_rwi_available;

    /** the number of existing hits at remote sites, before any filtering */
    public final AtomicInteger remote_rwi_stored;

    /** the number of peers which contributed to the remote search result */
    public final AtomicInteger remote_rwi_peerCount;

    /** The number of results evicted from local solr results after filtering up to the current query offset */
    public final AtomicInteger local_solr_evicted;

    /** the total number of existing hits by the local search in solr, before any supplementary filtering */
    public final AtomicInteger local_solr_stored;

    /** the number of hits imported from remote peers (rwi/solr mixed), after filtering */
    public final AtomicInteger remote_solr_available;

    /** the number of existing hits at remote site (rwi/solr mixed)*/
    public final AtomicInteger remote_solr_stored;

    /** the number of peers which contributed to the remote search result */
    public final AtomicInteger remote_solr_peerCount;

    /** Ensure only one {@link #resortCachedResults()} operation to be performed on this search event */
    public final Semaphore resortCacheAllowed;

    /**
     * Called when a search navigator has been updated : update the overall
     * navGeneration counter to help then tracking changes and eventually refresh the yacysearchtrailer.
     */
    @Override
    public void updatedScoreMap() {
        this.navGeneration.incrementAndGet();
    }

    /**
     * @return the total number of results currently available and filtered (checking doubles and eventual query constraints/modifiers) from the different data sources
     */
    public int getResultCount() {
        return Math.max(
                this.local_rwi_available.get() + this.remote_rwi_available.get() +
                this.remote_solr_available.get() + Math.max(0, this.local_solr_stored.get() - this.local_solr_evicted.get()),
                this.imageViewed.size() + sizeSpare()
                );
    }

    /**
     * @return the total number of successful write operations performed on all the active navigators since their initialization.
     */
    public long getNavGeneration() {
        return this.navGeneration.get();
    }

    /**
     * Set maximum size allowed (in kbytes) for a remote document result to be stored to local index.
     * @param maxSize document content max size in kbytes. Zero or negative value means no limit.
     */
    public void setRemoteDocStoredMaxSize(long maxSize) {
        this.remoteStoredDocMaxSize = maxSize;
    }

    /**
     * @return maximum size allowed (in kbytes) for a remote document result to be stored to local index.
     * Zero or negative value means no limit.
     */
    public long getRemoteDocStoredMaxSize() {
        return this.remoteStoredDocMaxSize;
    }

    protected SearchEvent(
            final QueryParams query,
            final SeedDB peers,
            final WorkTables workTables,
            final SortedSet<byte[]> preselectedPeerHashes,
            final boolean generateAbstracts,
            final LoaderDispatcher loader,
            final int remote_maxcount,
            final long remote_maxtime,
            final boolean deleteIfSnippetFail,
            final boolean addResultsToLocalIdx) {

        long ab = MemoryControl.available();
        if (ab < 1024 * 1024 * 200) {
            final int eb = SearchEventCache.size();
            SearchEventCache.cleanupEvents(false);
            final int en = SearchEventCache.size();
            if (en < eb) {
                log.info("Cleaned up search event cache (1) " + eb + "->" + en + ", " + (ab - MemoryControl.available()) / 1024 / 1024 + " MB freed");
            }
        }
        ab = MemoryControl.available();
        final int eb = SearchEventCache.size();
        SearchEventCache.cleanupEvents(Math.max(1, (int) (MemoryControl.available() / (1024 * 1024 * 120))));
        final int en = SearchEventCache.size();
        if (en < eb) {
            log.info("Cleaned up search event cache (2) " + eb + "->" + en + ", " + (ab - MemoryControl.available()) / 1024 / 1024 + " MB freed");
        }

        this.eventTime = System.currentTimeMillis(); // for lifetime check
        this.peers = peers;
        this.workTables = workTables;
        this.query = query;
        if(query != null) {
            /* Image counter will eventually grow up faster than offset, but must start first with the same value as query offset */
            this.imagePageCounter = query.offset;
        }
        this.loader = loader;
        this.nodeStack = new WeakPriorityBlockingQueue<>(max_results_node, false);
        this.maxExpectedRemoteReferences = new AtomicInteger(0);
        this.expectedRemoteReferences = new AtomicInteger(0);
        this.excludeintext_image = Switchboard.getSwitchboard().getConfigBool("search.excludeintext.image", true);

        // prepare configured search navigation
        final Set<String> navConfigs = Switchboard.getSwitchboard().getConfigSet("search.navigation");

        boolean locationNavEnabled = false;
        boolean protocolNavEnabled = false;
        boolean topicsNavEnabled = false;
        boolean dateNavEnabled = false;
        for(final String navConfig : navConfigs) {
            final String navName = NavigatorPlugins.getNavName(navConfig);
            if("location".equals(navName)) {
                locationNavEnabled = true;
            } else if("protocol".equals(navName)) {
                protocolNavEnabled = true;
            } else if("topics".equals(navName)) {
                topicsNavEnabled = true;
            } else if("date".equals(navName)) {
                dateNavEnabled = true;
            }
        }

        this.locationNavigator = locationNavEnabled ? new ConcurrentScoreMap<>(this) : null;
        this.protocolNavigator = protocolNavEnabled ? new ConcurrentScoreMap<>(this) : null;
        this.dateNavigator = dateNavEnabled ? new ConcurrentScoreMap<>(this) : null;
        this.topicNavigatorCount = topicsNavEnabled ? MAX_TOPWORDS : 0;
        this.vocabularyNavigator = new TreeMap<>();
        // prepare configured search navigation (plugins)
        this.navigatorPlugins = NavigatorPlugins.initFromCfgStrings(navConfigs);
        if(this.navigatorPlugins != null) {
            for(final Navigator nav : this.navigatorPlugins.values()) {
                nav.setUpdatesListener(this);
            }
        }

        this.snippets = new ConcurrentHashMap<>();
        this.secondarySearchSuperviser = (this.query.getQueryGoal().getIncludeHashes().size() > 1) ? new SecondarySearchSuperviser(this) : null; // generate abstracts only for combined searches
        if (this.secondarySearchSuperviser != null) this.secondarySearchSuperviser.start();
        this.secondarySearchThreads = null;
        this.preselectedPeerHashes = preselectedPeerHashes;
        this.IAResults = new TreeMap<>(Base64Order.enhancedCoder);
        this.IACount = new TreeMap<>(Base64Order.enhancedCoder);
        this.heuristics = new TreeMap<>(Base64Order.enhancedCoder);
        this.IAmaxcounthash = null;
        this.IAneardhthash = null;
        this.remote = (peers != null && peers.sizeConnected() > 0) && (this.query.domType == QueryParams.Searchdom.CLUSTER || (this.query.domType == QueryParams.Searchdom.GLOBAL && Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, false)));
        this.addResultsToLocalIndex = addResultsToLocalIdx;
        /* Défault : no size limit to store remote result documents to local index. Use setter to eventually modify it. */
        this.remoteStoredDocMaxSize = -1;
        this.local_rwi_available  = new AtomicInteger(0); // the number of results in the local peer after filtering
        this.local_rwi_stored     = new AtomicInteger(0);
        this.local_solr_evicted   = new AtomicInteger(0);
        this.local_solr_stored    = new AtomicInteger(0);
        this.remote_rwi_stored    = new AtomicInteger(0);
        this.remote_rwi_available = new AtomicInteger(0); // the number of result contributions from all the remote dht peers
        this.remote_rwi_peerCount = new AtomicInteger(0); // the number of remote dht peers that have contributed
        this.remote_solr_stored   = new AtomicInteger(0);
        this.remote_solr_available= new AtomicInteger(0); // the number of result contributions from all the remote solr peers
        this.remote_solr_peerCount= new AtomicInteger(0); // the number of remote solr peers that have contributed
        this.resortCacheAllowed = new Semaphore(1);
        final long start = System.currentTimeMillis();

        // do a soft commit for fresh results
        //query.getSegment().fulltext().commit(true);

        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        // sortorder: 0 = hash, 1 = url, 2 = ranking
        this.localSearchInclusion = null;
        this.ref = new ConcurrentScoreMap<>(this);
        this.maxtime = query.maxtime;
        this.rwiStack = new WeakPriorityBlockingQueue<>(max_results_rwi, false);
        this.doubleDomCache = new ConcurrentHashMap<>();
        this.flagcount = new int[32];
        for ( int i = 0; i < 32; i++ ) {
            this.flagcount[i] = 0;
        }
        this.feedersAlive = new AtomicInteger(0);
        this.feedersTerminated = new AtomicInteger(0);
        this.snippetFetchAlive = new AtomicInteger(0);
        this.addRunning = true;
        this.receivedRemoteReferences = new AtomicInteger(0);
        this.order = new ReferenceOrder(this.query.ranking, this.query.targetlang);
        this.urlhashes = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 100);
        this.taggingPredicates = new HashMap<>();
        for (final Tagging t: LibraryProvider.autotagging.getVocabularies()) {
            this.taggingPredicates.put(t.getName(), t.getPredicate());
        }

        // start a local solr search
        if (!Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_LOCAL_SOLR_OFF, false)) {
            final boolean useSolrFacets = true;
            this.localsolrsearch = RemoteSearch.solrRemoteSearch(this,
                    this.query.solrQuery(this.query.contentdom, this.query.isStrictContentDom(), useSolrFacets, this.excludeintext_image), this.query.offset,
                    this.query.itemsPerPage, null /* this peer */, 0, Switchboard.urlBlacklist, useSolrFacets, true);
        }
        this.localsolroffset = this.query.offset + this.query.itemsPerPage;

        // start a local RWI search concurrently
        this.rwiProcess = null;
        if (query.getSegment().connectedRWI() && !Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_LOCAL_DHT_OFF, false)) {
            // we start the local search only if this peer is doing a remote search or when it is doing a local search and the peer is old
            this.rwiProcess = new RWIProcess(this.localsolrsearch);
            this.rwiProcess.start();
        }

        if (this.remote) {
            // start global searches
            this.pollImmediately = false;
            final long timer = System.currentTimeMillis();
            if (this.query.getQueryGoal().getIncludeHashes().isEmpty()) {
                this.primarySearchThreadsL = null;
                this.nodeSearchThreads = null;
            } else {
                this.primarySearchThreadsL = new ArrayList<>();
                this.nodeSearchThreads = new ArrayList<>();
                // start this concurrently because the remote search needs an enumeration
                // of the remote peers which may block in some cases when i.e. DHT is active
                // at the same time.
                new Thread("SearchEvent.primaryRemoteSearches") {
                    @Override
                    public void run() {
                        RemoteSearch.primaryRemoteSearches(
                                SearchEvent.this,
                                0, remote_maxcount,
                                remote_maxtime,
                                Switchboard.urlBlacklist,
                                (SearchEvent.this.query.domType == QueryParams.Searchdom.GLOBAL) ? null : preselectedPeerHashes);
                    }
                }.start();
            }
            if ( this.primarySearchThreadsL != null ) {
                ConcurrentLog.fine("SEARCH_EVENT", "STARTING "
                        + this.primarySearchThreadsL.size()
                        + " THREADS TO CATCH EACH "
                        + remote_maxcount
                        + " URLs");
                EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.REMOTESEARCH_START, "", this.primarySearchThreadsL.size(), System.currentTimeMillis() - timer), false);
                // finished searching
                ConcurrentLog.fine("SEARCH_EVENT", "SEARCH TIME AFTER GLOBAL-TRIGGER TO " + this.primarySearchThreadsL.size() + " PEERS: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
            } else {
                // no search since query is empty, user might have entered no data or filters have removed all search words
                ConcurrentLog.fine("SEARCH_EVENT", "NO SEARCH STARTED DUE TO EMPTY SEARCH REQUEST.");
            }
        } else {
            this.primarySearchThreadsL = null;
            this.nodeSearchThreads = null;
            this.pollImmediately = !query.getSegment().connectedRWI() || !Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, false);
            if ( generateAbstracts ) {
                // we need the results now
                try {
                    if (this.rwiProcess != null && query.getSegment().connectedRWI()) this.rwiProcess.join();
                } catch (final Throwable e ) {
                }
                // compute index abstracts
                final long timer = System.currentTimeMillis();
                int maxcount = -1;
                long mindhtdistance = Long.MAX_VALUE, l;
                byte[] wordhash;
                assert !query.getSegment().connectedRWI() || this.searchContainerMap() != null;
                if (this.searchContainerMap() != null) {
                    for (final Map.Entry<byte[], ReferenceContainer<WordReference>> entry : this.searchContainerMap().entrySet()) {
                        wordhash = entry.getKey();
                        final ReferenceContainer<WordReference> container = entry.getValue();
                        assert (Base64Order.enhancedCoder.equal(container.getTermHash(), wordhash)) : "container.getTermHash() = " + ASCII.String(container.getTermHash()) + ", wordhash = " + ASCII.String(wordhash);
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
                        this.IAResults.put(wordhash, WordReferenceFactory.compressIndex(container, null, 1000).toString());
                    }
                }
                EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ABSTRACTS, "", this.searchContainerMap() == null ? 0 : this.searchContainerMap().size(), System.currentTimeMillis() - timer), false);
            } else {
                // give process time to accumulate a certain amount of data
                // before a reading process wants to get results from it
                try {
                    if (this.rwiProcess != null && query.getSegment().connectedRWI() && this.rwiProcess.isAlive()) this.rwiProcess.join(100);
                } catch (final Throwable e ) {
                }
                // this will reduce the maximum waiting time until results are available to 100 milliseconds
                // while we always get a good set of ranked data
            }
        }

        // start worker threads to fetch urls and snippets
        this.deleteIfSnippetFail = deleteIfSnippetFail;
        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.resultList = new WeakPriorityBlockingQueue<>(Math.max(max_results_node, 10 * query.itemsPerPage()), true); // this is the result, enriched with snippets, ranked and ordered by ranking

        // snippets do not need to match with the complete query hashes,
        // only with the query minus the stopwords which had not been used for the search
        boolean filtered = false;
        // check if query contains stopword
        if (Switchboard.stopwordHashes != null) {
            final Iterator<byte[]> it = query.getQueryGoal().getIncludeHashes().iterator();
            while (it.hasNext()) {
                if (Switchboard.stopwordHashes.contains((it.next()))) {
                    filtered = true;
                    break;
                }
            }
        }
        this.snippetFetchWordHashes = query.getQueryGoal().getIncludeHashes().clone();
        if (filtered) { // remove stopwords
            this.snippetFetchWordHashes.excludeDestructive(Switchboard.stopwordHashes);
        }

        this.snippetFetchWords = query.getQueryGoal().getIncludeWordsSet();
        // remove stopwords
        this.snippetFetchWords.removeAll(Switchboard.stopwords);

        // clean up events
        SearchEventCache.cleanupEvents(false);
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.CLEANUP, "", 0, 0), false);

        // store this search to a cache so it can be re-used
        if ( MemoryControl.available() < 1024 * 1024 * 100 ) {
            SearchEventCache.cleanupEvents(false);
        }
        SearchEventCache.put(this.query.id(false), this);
    }

    /**
     * A concurrent task to perform the current search query on the local RWI.
     */
    private class RWIProcess extends Thread {

        final Thread waitForThread;

        public RWIProcess(final Thread waitForThread) {
            super("SearchEvent.RWIProcess(" + (waitForThread != null ? waitForThread.getName() : "") + ")");
            this.waitForThread = waitForThread;
        }

        /**
         * Query the local RWI and feed the search event with the obtained results.
         */
        @Override
        public void run() {

            if (SearchEvent.this.query.getSegment().termIndex() == null) return; // nothing to do; this index is not used

            // do a search
            oneFeederStarted();

            // sort the local containers and truncate it to a limited count,
            // so following sortings together with the global results will be fast
            try {
                final long timer = System.currentTimeMillis();
                TermSearch<WordReference> search =
                        SearchEvent.this.query
                        .getSegment()
                        .termIndex()
                        .query(
                                SearchEvent.this.query.getQueryGoal().getIncludeHashes(),
                                SearchEvent.this.query.getQueryGoal().getExcludeHashes(),
                                null,
                                Segment.wordReferenceFactory,
                                SearchEvent.this.query.maxDistance);
                SearchEvent.this.localSearchInclusion = search.inclusion();
                ReferenceContainer<WordReference> index = search.joined();
                if ( !index.isEmpty() ) {
                    // in case that another thread has priority for their results, wait until this is finished
                    if (this.waitForThread != null && this.waitForThread.isAlive()) {
                        this.waitForThread.join();
                    }

                    // add the index to the result
                    int successcount = addRWIs(index, true, "local index: " + SearchEvent.this.query.getSegment().getLocation(), index.size(), SearchEvent.this.maxtime);
                    if (successcount == 0 &&
                            SearchEvent.this.query.getQueryGoal().getIncludeHashes().has(Segment.catchallHash) &&
                            SearchEvent.this.query.modifier.sitehost != null && SearchEvent.this.query.modifier.sitehost.length() > 0
                            ) {
                        // try again with sitehost
                        final String newGoal = Domains.getSmartSLD(SearchEvent.this.query.modifier.sitehost);
                        search =
                                SearchEvent.this.query
                                .getSegment()
                                .termIndex()
                                .query(
                                        QueryParams.hashes2Set(ASCII.String(Word.word2hash(newGoal))),
                                        SearchEvent.this.query.getQueryGoal().getExcludeHashes(),
                                        null,
                                        Segment.wordReferenceFactory,
                                        SearchEvent.this.query.maxDistance);
                        SearchEvent.this.localSearchInclusion = search.inclusion();
                        index = search.joined();
                        if (!index.isEmpty()) {
                            successcount = addRWIs(index, true, "local index: " + SearchEvent.this.query.getSegment().getLocation(), index.size(), SearchEvent.this.maxtime);
                        }
                    }
                    EventTracker.update(
                            EventTracker.EClass.SEARCH,
                            new ProfilingGraph.EventSearch(
                                    SearchEvent.this.query.id(true),
                                    SearchEventType.JOIN,
                                    SearchEvent.this.query.getQueryGoal().getQueryString(false),
                                    successcount,
                                    System.currentTimeMillis() - timer),
                            false);
                    SearchEvent.this.addFinalize();
                }
            } catch (final Exception e ) {
                ConcurrentLog.logException(e);
            } finally {
                oneFeederTerminated();
            }
        }
    }

    public int addRWIs(
            final ReferenceContainer<WordReference> index,
            final boolean local,
            final String resourceName,
            final int fullResource,
            final long maxtime) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        //Log.logInfo("SearchEvent", "added a container, size = " + index.size());

        this.addRunning = true;
        assert (index != null);
        if (index.isEmpty()) return 0;
        if (local) {
            assert fullResource >= 0 : "fullResource = " + fullResource;
            this.local_rwi_stored.addAndGet(fullResource);
        } else {
            assert fullResource >= 0 : "fullResource = " + fullResource;
            this.remote_rwi_stored.addAndGet(fullResource);
            this.remote_rwi_peerCount.incrementAndGet();
        }
        long timer = System.currentTimeMillis();

        // normalize entries
        final BlockingQueue<WordReferenceVars> decodedEntries = this.order.normalizeWith(index, maxtime, local);
        final int is = index.size();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
                this.query.id(true),
                SearchEventType.NORMALIZING,
                resourceName,
                is,
                System.currentTimeMillis() - timer), false);
        if (!local) this.receivedRemoteReferences.addAndGet(is);

        // iterate over normalized entries and select some that are better than currently stored
        timer = System.currentTimeMillis();

        // apply all constraints
        final long timeout = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        int successcounter = 0;
        try {
            WordReferenceVars iEntry;
            long remaining;
            String acceptableAlternativeSitehash = null;
            if (this.query.modifier.sitehost != null && this.query.modifier.sitehost.length() > 0) try {
                acceptableAlternativeSitehash = DigestURL.hosthash(this.query.modifier.sitehost.startsWith("www.") ? this.query.modifier.sitehost.substring(4) : "www." + this.query.modifier.sitehost, 80);
            } catch (final MalformedURLException e1) {}
            pollloop: while ( true ) {
                remaining = timeout - System.currentTimeMillis();
                if (remaining <= 0) {
                    ConcurrentLog.warn("SearchEvent", "terminated 'add' loop before poll time-out = " + remaining + ", decodedEntries.size = " + decodedEntries.size());
                    break;
                }
                iEntry = decodedEntries.poll(remaining, TimeUnit.MILLISECONDS);
                if (iEntry == null) {
                    ConcurrentLog.warn("SearchEvent", "terminated 'add' loop after poll time-out = " + remaining + ", decodedEntries.size = " + decodedEntries.size());
                    break pollloop;
                }
                if (iEntry == WordReferenceVars.poison) {
                    break pollloop;
                }
                assert (iEntry.urlhash().length == index.row().primaryKeyLength);

                // doublecheck for urls
                if (this.urlhashes.has(iEntry.urlhash())) {
                    if (log.isFine()) log.fine("dropped RWI: doublecheck");
                    continue pollloop;
                }

                // increase flag counts
                final Bitfield flags = iEntry.flags();
                for (int j = 0; j < 32; j++) {
                    if (flags.get(j)) this.flagcount[j]++;
                }

                // check constraints
                if (!this.testFlags(flags)) {
                    if (log.isFine()) log.fine("dropped RWI: flag test failed");
                    continue pollloop;
                }

                // check document domain
                if (this.query.contentdom.getCode() > 0) {
                    boolean domainMatch = true;
                    if(this.query.isStrictContentDom()) {
                        if((this.query.contentdom == ContentDomain.AUDIO && iEntry.getType() != Response.DT_AUDIO) ||
                                (this.query.contentdom == ContentDomain.VIDEO && iEntry.getType() != Response.DT_MOVIE) ||
                                (this.query.contentdom == ContentDomain.IMAGE && iEntry.getType() != Response.DT_IMAGE) ||
                                (this.query.contentdom == ContentDomain.APP && !(flags.get(Tokenizer.flag_cat_hasapp)))) {
                            domainMatch = false;
                        }
                    } else if((this.query.contentdom == ContentDomain.AUDIO && !(flags.get(Tokenizer.flag_cat_hasaudio))) ||
                            (this.query.contentdom == ContentDomain.VIDEO && !(flags.get(Tokenizer.flag_cat_hasvideo))) ||
                            (this.query.contentdom == ContentDomain.IMAGE && !(flags.get(Tokenizer.flag_cat_hasimage))) ||
                            (this.query.contentdom == ContentDomain.APP && !(flags.get(Tokenizer.flag_cat_hasapp)))) {
                        domainMatch = false;
                    }
                    if(!domainMatch) {
                        if (log.isFine()) {
                            log.fine("dropped RWI: contentdom fail");
                        }
                        continue pollloop;
                    }
                }

                // check language
                if (this.query.modifier.language != null && !this.query.modifier.language.isEmpty()
                        && !this.query.modifier.language.equals(iEntry.getLanguageString())) {
                    if (log.isFine()) {
                        log.fine("dropped RWI: language constraint = " + this.query.modifier.language);
                    }
                    continue pollloop;
                }

                // count domZones
                //this.domZones[DigestURI.domDomain(iEntry.metadataHash())]++;

                // check site constraints
                final String hosthash = iEntry.hosthash();
                if ( this.query.modifier.sitehash == null ) {
                    if (this.query.siteexcludes != null && this.query.siteexcludes.contains(hosthash)) {
                        if (log.isFine()) log.fine("dropped RWI: siteexcludes");
                        continue pollloop;
                    }
                } else {
                    // filter out all domains that do not match with the site constraint
                    if (!hosthash.equals(this.query.modifier.sitehash) && (acceptableAlternativeSitehash == null || !hosthash.equals(acceptableAlternativeSitehash))) {
                        if (log.isFine()) log.fine("dropped RWI: modifier.sitehash");
                        continue pollloop;
                    }
                }

                // finally extend the double-check and insert result to stack
                this.urlhashes.putUnique(iEntry.urlhash());
                rankingtryloop: while (true) {
                    try {
                        this.rwiStack.put(new ReverseElement<>(iEntry, this.order.cardinal(iEntry))); // inserts the element and removes the worst (which is smallest)
                        break rankingtryloop;
                    } catch (final ArithmeticException e ) {
                        // this may happen if the concurrent normalizer changes values during cardinal computation
                        if (log.isFine()) log.fine("dropped RWI: arithmetic exception");
                        continue rankingtryloop;
                    }
                }
                // increase counter for statistics
                if (local) this.local_rwi_available.incrementAndGet(); else this.remote_rwi_available.incrementAndGet();

                successcounter++;
            }
            if (System.currentTimeMillis() >= timeout) ConcurrentLog.warn("SearchEvent", "rwi normalization ended with timeout = " + maxtime);

        } catch (final InterruptedException | SpaceExceededException e ) {
        }

        //if ((query.neededResults() > 0) && (container.size() > query.neededResults())) remove(true, true);
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
                this.query.id(true),
                SearchEventType.PRESORT,
                resourceName,
                index.size(),
                System.currentTimeMillis() - timer), false);
        return successcounter;
    }

    public long getEventTime() {
        return this.eventTime;
    }

    protected void resetEventTime() {
        this.eventTime = System.currentTimeMillis();
    }

    protected void cleanup() {

        // stop all threads
        if (this.localsolrsearch != null) {
            if (this.localsolrsearch.isAlive()) synchronized (this.localsolrsearch) {this.localsolrsearch.interrupt();}
        }
        if (this.nodeSearchThreads != null) {
            for (final Thread search : this.nodeSearchThreads) {
                if (search != null) {
                    synchronized (search) {if (search.isAlive()) {search.interrupt();}}
                }
            }
        }
        if (this.primarySearchThreadsL != null) {
            for (final RemoteSearch search : this.primarySearchThreadsL) {
                if (search != null) {
                    synchronized (search) {if (search.isAlive()) {search.interrupt();}}
                }
            }
        }
        if (this.secondarySearchThreads != null) {
            for (final Thread search : this.secondarySearchThreads ) {
                if (search != null) {
                    synchronized (search) {if (search.isAlive()) {search.interrupt();}}
                }
            }
        }

        // clear all data structures
        if (this.preselectedPeerHashes != null) this.preselectedPeerHashes.clear();
        if (this.IACount != null) this.IACount.clear();
        if (this.IAResults != null) this.IAResults.clear();
        if (this.heuristics != null) this.heuristics.clear();
        this.rwiStack.clear();
        this.nodeStack.clear();
        this.resultList.clear();
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

    /**
     * Add result entries to this nodes stack and update eventual navigators counters.
     * @param nodeList a list of entries from a Solr instance
     * @param facets a map from a field name to scored values (aka Solr facet). May be null : in that case the navigators counters are updated one by one when inserting each result in the nodes stack.
     * @param solrsnippets a map from urlhash to snippet text
     * @param local true when the nodeList comes from the local Solr
     * @param resourceName the name of the data source to use for monitoring in the event tracker
     * @param fullResource the full results count
     * @param incrementNavigators when true, increment event navigators either with facet counts or with individual results
     */
    public void addNodes(
            final List<URIMetadataNode> nodeList,
            final Map<String, ReversibleScoreMap<String>> facets,
            final Map<String, LinkedHashSet<String>> solrsnippets,
            final boolean local,
            final String resourceName,
            final int fullResource,
            final boolean incrementNavigators) {

        this.addBegin();

        // check if all results have snippets
        /*
        for (URIMetadataNode node: nodeList) {
            if (!facets.containsKey(ASCII.String(node.hash()))) {
                log.logInfo("no snippet from Solr for " + node.url().toNormalform(true));
            }
        }
         */
        this.snippets.putAll(solrsnippets);
        assert (nodeList != null);
        if (nodeList.isEmpty()) return;

        if (local) {
            this.local_solr_stored.set(fullResource);
        } else {
            assert fullResource >= 0 : "fullResource = " + fullResource;
            this.remote_solr_stored.addAndGet(fullResource);
            this.remote_solr_peerCount.incrementAndGet();
        }

        long timer = System.currentTimeMillis();

        // normalize entries
        final int is = nodeList.size();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.NORMALIZING, resourceName, is, System.currentTimeMillis() - timer), false);
        if (!local) {
            this.receivedRemoteReferences.addAndGet(is);
        }

        // iterate over normalized entries and select some that are better than currently stored
        timer = System.currentTimeMillis();

        // collect navigation information from Solr facets when available
        if(incrementNavigators) {
            incrNavigatorsFromSolrFacets(facets);
        }

        // apply all constraints
        try {
            pollloop: for (final URIMetadataNode iEntry: nodeList) {

                // check url related eventual constraints (protocol, tld, sitehost, and filetype)
                final String matchingResult = QueryParams.matchesURL(this.query.modifier, this.query.tld, iEntry.url());
                if (!matchingResult.isEmpty()) {
                    if (log.isFine()) {
                        log.fine("dropped Node: " + matchingResult);
                    }
                    updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                    continue pollloop;
                }

                if ( !this.query.urlMask_isCatchall && this.query.urlMaskPattern != null) {
                    // check url mask, only when not redundant with query modifier and tld constraints
                    if (!iEntry.matches(this.query.urlMaskPattern)) {
                        if (log.isFine()) log.fine("dropped Node: url mask does not match");
                        updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                        continue pollloop;
                    }
                }

                // doublecheck for urls
                if (this.urlhashes.has(iEntry.hash())) {
                    if (log.isFine()) log.fine("dropped Node: double check");
                    updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                    continue pollloop;
                }

                // increase flag counts
                for ( int j = 0; j < 32; j++ ) {
                    if (iEntry.flags().get(j)) this.flagCount()[j]++;
                }

                // check constraints
                final Bitfield flags = iEntry.flags();
                if (!this.testFlags(flags)) {
                    if (log.isFine()) log.fine("dropped Node: flag test");
                    updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                    continue pollloop;
                }

                // check document domain
                if (this.query.contentdom.getCode() > 0) {
                    boolean domainMatch = true;
                    if(this.query.isStrictContentDom()) {
                        if(this.query.contentdom != iEntry.getContentDomain()) {
                            domainMatch = false;
                        }
                    } else if((this.query.contentdom == ContentDomain.AUDIO && !(flags.get(Tokenizer.flag_cat_hasaudio))) ||
                            (this.query.contentdom == ContentDomain.VIDEO && !(flags.get(Tokenizer.flag_cat_hasvideo))) ||
                            (this.query.contentdom == ContentDomain.IMAGE && !(flags.get(Tokenizer.flag_cat_hasimage))) ||
                            (this.query.contentdom == ContentDomain.APP && !(flags.get(Tokenizer.flag_cat_hasapp)))) {
                        domainMatch = false;
                    }
                    if(!domainMatch) {
                        if (log.isFine()) {
                            log.fine("dropped Node: content domain does not match");
                        }
                        updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                        continue pollloop;
                    }
                }

                // filter out media links in text search, if wanted
                final String ext = MultiProtocolURL.getFileExtension(iEntry.url().getFileName());
                if (this.query.contentdom == ContentDomain.TEXT && Classification.isImageExtension(ext) && this.excludeintext_image) {
                    if (log.isFine()) log.fine("dropped Node: file name domain does not match");
                    updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                    continue pollloop;
                }

                // check site constraints
                final String hosthash = iEntry.hosthash();
                if ( this.query.modifier.sitehash == null ) {
                    if (this.query.siteexcludes != null && this.query.siteexcludes.contains(hosthash)) {
                        if (log.isFine()) log.fine("dropped Node: siteexclude");
                        updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                        continue pollloop;
                    }
                }

                if (this.query.modifier.language != null) {
                    if (!this.query.modifier.language.equals(iEntry.language())) {
                        if (log.isFine()) log.fine("dropped Node: language");
                        updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                        continue pollloop;
                    }
                }

                if (this.query.modifier.author != null) {
                    if (!this.query.modifier.author.equals(iEntry.dc_creator())) {
                        if (log.isFine()) log.fine ("dropped Node: author");
                        updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                        continue pollloop;
                    }
                }

                if (this.query.modifier.keyword != null) {
                    if (iEntry.dc_subject().indexOf(this.query.modifier.keyword) < 0) {
                        if (log.isFine()) log.fine ("dropped Node: keyword");
                        updateCountsOnSolrEntryToEvict(iEntry, facets, local, !incrementNavigators);
                        continue pollloop;
                    }
                }

                // finally extend the double-check and insert result to stack
                this.urlhashes.putUnique(iEntry.hash());
                rankingtryloop: while (true) {
                    try {
                        long score;
                        // determine nodestack ranking (will be altered by postranking)
                        // so far Solr score is used (with abitrary factor to get value similar to rwi ranking values)
                        final Float scorex = (Float) iEntry.getFieldValue("score"); // this is a special field containing the ranking score of a Solr search result
                        if (scorex != null && scorex > 0)
                            score = (long) ((1000000.0f * scorex) - iEntry.urllength()); // we modify the score here since the solr score is equal in many cases and then the order would simply depend on the url hash which would be silly
                        else
                            score = this.order.cardinal(iEntry);
                        this.nodeStack.put(new ReverseElement<>(iEntry, score)); // inserts the element and removes the worst (which is smallest)
                        break rankingtryloop;
                    } catch (final ArithmeticException e ) {
                        // this may happen if the concurrent normalizer changes values during cardinal computation
                        continue rankingtryloop;
                    }
                }
                // increase counter for statistics
                if (!local) {
                    this.remote_solr_available.incrementAndGet();
                }

                // collect navigation information not available in facets
                if(incrementNavigators) {
                    incrNavigatorsFromSingleDocument(iEntry, facets);
                }
            }
        } catch (final SpaceExceededException e ) {
        }
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.PRESORT, resourceName, nodeList.size(), System.currentTimeMillis() - timer), false);
    }

    /**
     * Increment this event eventual navigators with the given facets processed by a Solr instance
     * @param facets facets counts from a Solr instance
     */
    private void incrNavigatorsFromSolrFacets(final Map<String, ReversibleScoreMap<String>> facets) {
        if(facets != null && !facets.isEmpty()) {
            /* Iterate over active navigator plugins to let them update the counters */
            for (final String s : this.navigatorPlugins.keySet()) {
                final Navigator navi = this.navigatorPlugins.get(s);
                if (navi != null) {
                    navi.incFacet(facets);
                }
            }

            ReversibleScoreMap<String> fcts;
            if (this.locationNavigator != null) {
                /* Is is still relevant? It looks like this nav is currently never filled, as a constraint on coordinates
                 * is expressed as a spatial filter not producing facets counts (see QueryParams.getFacetsFilterQueries()). */
                fcts = facets.get(CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName());
                if (fcts != null) {
                    for (final String coordinate: fcts) {
                        final int hc = fcts.get(coordinate);
                        if (hc == 0) continue;
                        this.locationNavigator.inc(coordinate, hc);
                    }
                }
            }

            if (this.dateNavigator != null) {
                fcts = facets.get(CollectionSchema.dates_in_content_dts.getSolrFieldName());
                if (fcts != null) this.dateNavigator.inc(fcts);
            }

            if (this.protocolNavigator != null) {
                fcts = facets.get(CollectionSchema.url_protocol_s.getSolrFieldName());
                if (fcts != null) {
                    // remove all protocols that we don't know
                    final Iterator<String> i = fcts.iterator();
                    while (i.hasNext()) {
                        final String protocol = i.next();
                        if (PROTOCOL_NAVIGATOR_SUPPORTED_VALUES.indexOf(protocol) < 0) {
                            i.remove();
                        }
                    }
                    this.protocolNavigator.inc(fcts);
                }
            }

            // get the vocabulary navigation
            final Set<String> genericFacets = new LinkedHashSet<>();
            for (final Tagging v: LibraryProvider.autotagging.getVocabularies()) genericFacets.add(v.getName());
            genericFacets.addAll(ProbabilisticClassifier.getContextNames());
            for (final String vocName: genericFacets) {
                fcts = facets.get(CollectionSchema.VOCABULARY_PREFIX + vocName + CollectionSchema.VOCABULARY_TERMS_SUFFIX);
                if (fcts != null) {
                    ScoreMap<String> vocNav = this.vocabularyNavigator.get(vocName);
                    if (vocNav == null) {
                        vocNav = new ConcurrentScoreMap<>();
                        this.vocabularyNavigator.put(vocName, vocNav);
                    }
                    vocNav.inc(fcts);
                }
            }
        }
    }

    /**
     * Increment this event eventual navigators with the given entry, only when the concerned field is not present in facets
     * @param doc a document entry from a Solr source
     * @param facets facets counts from a Solr instance
     */
    private void incrNavigatorsFromSingleDocument(final URIMetadataNode doc,
            final Map<String, ReversibleScoreMap<String>> facets) {

        /* Iterate over active navigator plugins to let them update the counters */
        for (final String s : this.navigatorPlugins.keySet()) {
            final Navigator navi = this.navigatorPlugins.get(s);
            if (navi != null && facets == null || !facets.containsKey(navi.getIndexFieldName())) {
                navi.incDoc(doc);
            }
        }

        /* Note : would it be relevant to update here this.locationNavigator ?
		    		It looks like this nav is currently never filled */

        if (this.dateNavigator != null) {
            if (facets == null || !facets.containsKey(CollectionSchema.dates_in_content_dts.getSolrFieldName())) {
                final Date[] dates = doc.datesInContent();
                if (dates != null) {
                    for (final Date date : dates) {
                        if (date != null) {
                            this.dateNavigator.inc(ISO8601Formatter.FORMATTER.format(date));
                        }
                    }
                }
            }
        }

        if (this.protocolNavigator != null) {
            if (facets == null || !facets.containsKey(CollectionSchema.url_protocol_s.getSolrFieldName())) {
                final String protocol = doc.url().getProtocol();
                // include only protocols supported protocols
                if (protocol != null && PROTOCOL_NAVIGATOR_SUPPORTED_VALUES.indexOf(protocol) >= 0) {
                    this.protocolNavigator.inc(protocol);
                }
            }
        }

        // get the vocabulary navigation
        if(this.vocabularyNavigator != null) {
            final Set<String> genericFacets = new LinkedHashSet<>();
            for (final Tagging v : LibraryProvider.autotagging.getVocabularies()) {
                genericFacets.add(v.getName());
            }
            genericFacets.addAll(ProbabilisticClassifier.getContextNames());
            for (final String vocName : genericFacets) {
                final String fieldName = CollectionSchema.VOCABULARY_PREFIX + vocName + CollectionSchema.VOCABULARY_TERMS_SUFFIX;
                if (facets == null || !facets.containsKey(fieldName)) {
                    incrementVocNavigator(doc, vocName, fieldName);
                }
            }
        }
    }

    /**
     * Increment a vocabulary navigator with the given document
     * @param doc a document entry. Must not be null.
     * @param vocName the name of the vocabulary. Must not be null.
     * @param fieldName the name of the field eventually holding the vocabulary information in the document entry
     */
    protected void incrementVocNavigator(final URIMetadataNode doc, final String vocName, final String fieldName) {
        final Object docValue = doc.getFieldValue(fieldName);
        if(docValue instanceof String) {
            ScoreMap<String> vocNav = this.vocabularyNavigator.get(vocName);
            if (vocNav == null) {
                vocNav = new ConcurrentScoreMap<>();
                this.vocabularyNavigator.put(vocName, vocNav);
            }
            vocNav.inc((String)docValue);
        } else if(docValue instanceof Collection) {
            if (!((Collection<?>) docValue).isEmpty()) {
                ScoreMap<String> vocNav = this.vocabularyNavigator.get(vocName);
                if (vocNav == null) {
                    vocNav = new ConcurrentScoreMap<>();
                    this.vocabularyNavigator.put(vocName, vocNav);
                }
                for (final Object singleDocValue : (Collection<?>) docValue) {
                    if (singleDocValue instanceof String) {
                        vocNav.inc((String) singleDocValue);
                    }
                }
            }
        }
    }

    public void addExpectedRemoteReferences(int x) {
        if ( x > 0 ) {
            this.maxExpectedRemoteReferences.addAndGet(x);
        }
        this.expectedRemoteReferences.addAndGet(x);
    }

    /**
     * Take one best entry from the rwiStack and create a node entry out of it.
     * There is no waiting or blocking; if no entry is available this just returns null
     * If the sjupDoubleDom option is selected, only different hosts are returned until no such rwi exists.
     * Then the best entry from domain stacks are returned.
     * @param skipDoubleDom
     * @return a node from a rwi entry if one exist or null if not (with score value set)
     */
    private URIMetadataNode pullOneRWI(final boolean skipDoubleDom) {

        // returns from the current RWI list the best entry and removes this entry from the list
        WeakPriorityBlockingQueue<WordReferenceVars> m;
        WeakPriorityBlockingQueue.Element<WordReferenceVars> rwi = null;

        mainloop: while (true) {
            int c = 0;
            pollloop: while (this.rwiStack.sizeQueue() > 0 && c++ < 10) {
                rwi = this.rwiStack.poll();
                if (rwi == null) return null;
                if (!skipDoubleDom) {
                    final URIMetadataNode node = this.query.getSegment().fulltext().getMetadata(rwi);
                    if (node == null) {
                        decrementCounts(rwi.getElement());
                        continue pollloop;
                    }
                    return node;
                }

                // check doubledom
                final String hosthash = rwi.getElement().hosthash();
                m = this.doubleDomCache.get(hosthash);
                if (m == null) {
                    synchronized ( this.doubleDomCache ) {
                        m = this.doubleDomCache.get(hosthash);
                        if (m == null) {
                            // first appearance of dom. we create an entry to signal that one of that domain was already returned
                            m = new WeakPriorityBlockingQueue<>(max_results_rwi, false);
                            this.doubleDomCache.put(hosthash, m);
                            final URIMetadataNode node = this.query.getSegment().fulltext().getMetadata(rwi);
                            if (node == null) {
                                decrementCounts(rwi.getElement());
                                continue pollloop;
                            }
                            return node;
                        }
                        // second appearances of dom
                        m.put(rwi);
                    }
                } else {
                    m.put(rwi);
                }
            }

            // no more entries in sorted RWI entries. Now take Elements from the doubleDomCache
            if (this.doubleDomCache.isEmpty()) {
                //Log.logWarning("SearchEvent", "doubleDomCache.isEmpty");
                return null;
            }

            // find best entry from all caches
            WeakPriorityBlockingQueue.Element<WordReferenceVars> bestEntry = null;
            WeakPriorityBlockingQueue.Element<WordReferenceVars> o;
            final Iterator<WeakPriorityBlockingQueue<WordReferenceVars>> i = this.doubleDomCache.values().iterator();
            doubleloop: while (i.hasNext()) {
                try {
                    m = i.next();
                } catch (final ConcurrentModificationException e) {
                    ConcurrentLog.logException(e);
                    continue mainloop; // not the best solution...
                }
                if (m == null) continue doubleloop;
                if (m.isEmpty()) continue doubleloop;
                if (bestEntry == null) {
                    bestEntry = m.peek();
                    continue doubleloop;
                }
                o = m.peek();
                if (o == null) continue doubleloop;
                if (o.getWeight() > bestEntry.getWeight()) bestEntry = o;
            }
            if (bestEntry == null) {
                //Log.logWarning("SearchEvent", "bestEntry == null (1)");
                return null;
            }

            // finally remove the best entry from the doubledom cache
            m = this.doubleDomCache.get(bestEntry.getElement().hosthash());
            if (m != null) {
                bestEntry = m.poll();
                if (bestEntry != null && m.sizeAvailable() == 0) {
                    synchronized ( this.doubleDomCache ) {
                        if (m.sizeAvailable() == 0) {
                            this.doubleDomCache.remove(bestEntry.getElement().hosthash());
                        }
                    }
                }
            }
            if (bestEntry == null) {
                //Log.logWarning("SearchEvent", "bestEntry == null (2)");
                return null;
            }
            URIMetadataNode node = null;
            try {
                node = this.query.getSegment().fulltext().getMetadata(bestEntry);
            } catch (final Throwable e) {
                ConcurrentLog.logException(e);
            }
            if (node == null) {
                decrementCounts(bestEntry.getElement());
                if (log.isFine()) log.fine("dropped RWI: hash not in metadata");
                continue mainloop;
            }
            return node;
        }
    }

    /**
     * get one metadata entry from the ranked results. This will be the 'best' entry so far according to the
     * applied ranking. If there are no more entries left or the timeout limit is reached then null is
     * returned. The caller may distinguish the timeout case from the case where there will be no more also in
     * the future by calling this.feedingIsFinished()
     *
     * @param skipDoubleDom should be true if it is wanted that double domain entries are skipped
     * @return a metadata entry for a url (with score value set)
     */
    public URIMetadataNode pullOneFilteredFromRWI(final boolean skipDoubleDom) {
        // returns from the current RWI list the best URL entry and removes this entry from the list
        URIMetadataNode page;
        mainloop: while ((page = pullOneRWI(skipDoubleDom)) != null) {

            // check url related eventual constraints (protocol, tld, sitehost, and filetype)
            final String matchingResult = QueryParams.matchesURL(this.query.modifier, this.query.tld, page.url());
            if (!matchingResult.isEmpty()) {
                if (log.isFine()) {
                    log.fine("dropped RWI: no match on " + matchingResult);
                }
                decrementCounts(page.word());
                continue;
            }

            if (!this.query.urlMask_isCatchall && this.query.urlMaskPattern != null
                    && !page.matches(this.query.urlMaskPattern)) {
                // check url mask, only when not redundant with query modifier and tld constraints
                if (log.isFine()) log.fine("dropped RWI: no match with urlMask");
                decrementCounts(page.word());
                continue;
            }

            // check for more errors
            if (page.url() == null) {
                if (log.isFine()) log.fine("dropped RWI: url == null");
                decrementCounts(page.word());
                continue; // rare case where the url is corrupted
            }

            // check content domain
            final ContentDomain contentDomain = page.getContentDomain();
            if (this.query.contentdom.getCode() > 0 && (
                    (this.query.contentdom == Classification.ContentDomain.IMAGE && contentDomain != Classification.ContentDomain.IMAGE) ||
                    (this.query.contentdom == Classification.ContentDomain.AUDIO && contentDomain != Classification.ContentDomain.AUDIO) ||
                    (this.query.contentdom == Classification.ContentDomain.VIDEO && contentDomain != Classification.ContentDomain.VIDEO) ||
                    (this.query.contentdom == Classification.ContentDomain.APP && contentDomain != Classification.ContentDomain.APP)) && this.query.urlMask_isCatchall) {
                if (log.isFine()) log.fine("dropped RWI: wrong contentdom = " + this.query.contentdom + ", domain = " + contentDomain);
                decrementCounts(page.word());
                continue;
            }

            // filter out media links in text search, if wanted
            final String ext = MultiProtocolURL.getFileExtension(page.url().getFileName());
            if (this.query.contentdom == ContentDomain.TEXT && Classification.isImageExtension(ext) && this.excludeintext_image) {
                if (log.isFine()) log.fine("dropped RWI: file name domain does not match");
                continue;
            }

            // filter query modifiers variables (these are host, filetype, protocol, language, author, collection, dates_in_content(on,from,to,timezone) )

            /* check again modifier constraint (language) with the language in the full metadata,
             * that may differ from the one in the reverse word reference which is already checked in addRWIs()*/
            if (this.query.modifier.language != null && !this.query.modifier.language.equals(page.language())) {
                if (log.isFine()) log.fine("dropped RWI: language constraint = " + this.query.modifier.language);
                decrementCounts(page.word());
                continue;
            }

            // check modifier constraint (author)
            if (this.query.modifier.author != null && !page.dc_creator().toLowerCase().contains(this.query.modifier.author.toLowerCase()) /*!this.query.modifier.author.equalsIgnoreCase(page.dc_creator())*/) {
                if (log.isFine()) log.fine("dropped RWI: author  constraint = " + this.query.modifier.author);
                decrementCounts(page.word());
                continue;
            }

            // check modifier constraint collection
            // this is not available in pure RWI entries (but in local or via solr query received metadate/entries),
            if (this.query.modifier.collection != null) {
                final Collection<Object> docCols = page.getFieldValues(CollectionSchema.collection_sxt.getSolrFieldName()); // get multivalued value
                if (docCols == null) { // no collection info
                    decrementCounts(page.word());
                    continue;
                } else if (!docCols.contains(this.query.modifier.collection)) {
                    decrementCounts(page.word());
                    continue;
                }
            }

            // check modifier constraint (keyword)
            if (this.query.modifier.keyword != null && !page.dc_subject().toLowerCase().contains(this.query.modifier.keyword.toLowerCase())) {
                if (log.isFine()) log.fine("dropped RWI: keyword  constraint = " + this.query.modifier.keyword);
                decrementCounts(page.word());
                continue;
            }

            // Check for blacklist
            if (Switchboard.urlBlacklist.isListed(BlacklistType.SEARCH, page.url())) {
                if (log.isFine()) log.fine("dropped RWI: url is blacklisted in url blacklist");
                decrementCounts(page.word());
                continue;
            }

            final String pageurl = page.url().toNormalform(true);
            final String pageauthor = page.dc_creator();
            final String pagetitle = page.dc_title().toLowerCase();

            // check exclusion
            if (this.query.getQueryGoal().getExcludeSize() != 0 &&
                    ((QueryParams.anymatch(pagetitle, this.query.getQueryGoal().getExcludeWords()))
                            || (QueryParams.anymatch(pageurl.toLowerCase(), this.query.getQueryGoal().getExcludeWords()))
                            || (QueryParams.anymatch(pageauthor.toLowerCase(), this.query.getQueryGoal().getExcludeWords())))) {
                if (log.isFine()) log.fine("dropped RWI: no match with query goal exclusion");
                decrementCounts(page.word());
                continue;
            }

            // check index-of constraint
            if ((this.query.constraint != null) && (this.query.constraint.get(Tokenizer.flag_cat_indexof)) && (!(pagetitle.startsWith("index of")))) {
                final Iterator<byte[]> wi = this.query.getQueryGoal().getIncludeHashes().iterator();
                if (this.query.getSegment().termIndex() != null) {
                    while (wi.hasNext()) {
                        this.query.getSegment().termIndex().removeDelayed(wi.next(), page.hash());
                    }
                }
                if (log.isFine()) log.fine("dropped RWI: url does not match index-of constraint");
                decrementCounts(page.word());
                continue;
            }

            // check location constraint
            if ((this.query.constraint != null) && (this.query.constraint.get(Tokenizer.flag_cat_haslocation)) && (page.lat() == 0.0 || page.lon() == 0.0)) {
                if (log.isFine()) log.fine("dropped RWI: location constraint");
                decrementCounts(page.word());
                continue;
            }

            // check geo coordinates
            double lat, lon;
            if (this.query.radius > 0.0d && this.query.lat != 0.0d && this.query.lon != 0.0d && (lat = page.lat()) != 0.0d && (lon = page.lon()) != 0.0d) {
                final double latDelta = this.query.lat - lat;
                final double lonDelta = this.query.lon - lon;
                final double distance = Math.sqrt(latDelta * latDelta + lonDelta * lonDelta); // pythagoras
                if (distance > this.query.radius) {
                    if (log.isFine()) log.fine("dropped RWI: radius constraint");
                    decrementCounts(page.word());
                    continue;
                }
            }

            // check vocabulary terms (metatags) {only available in Solr index as vocabulary_xxyyzzz_sxt field}
            // TODO: vocabulary is only valid and available in local Solr index (consider to auto-switch to Searchdom.LOCAL)
            if (this.query.metatags != null && !this.query.metatags.isEmpty()) {
                tagloop: for (final Tagging.Metatag tag : this.query.metatags) {
                    final SolrDocument sdoc = page;
                    if (sdoc != null) {
                        final Collection<Object> tagvalues = sdoc.getFieldValues(CollectionSchema.VOCABULARY_PREFIX + tag.getVocabularyName() + CollectionSchema.VOCABULARY_TERMS_SUFFIX);
                        if (tagvalues != null && tagvalues.contains(tag.getObject())) {
                            continue tagloop; // metatag exists check next tag (filter may consist of several tags)
                        }
                    } // if we reach this point the metatag was not found (= drop entry)
                    if (log.isFine()) log.fine("dropped RWI: url not tagged with vocabulary " + tag.getVocabularyName());
                    decrementCounts(page.word());
                    continue mainloop;
                }
            }

            // from here: collect navigation information
            // TODO: it may be a little bit late here, to update navigator counters

            // iterate over active navigator plugins (the rwi metadata may contain the field the plugin counts)
            for (final String s : this.navigatorPlugins.keySet()) {
                final Navigator navi = this.navigatorPlugins.get(s);
                if (navi != null) {
                    navi.incDoc(page);
                }
            }

            if(this.protocolNavigator != null && page.url() != null) {
                final String protocol = page.url().getProtocol();
                if(protocol != null) {
                    this.protocolNavigator.inc(protocol);
                }
            }

            if(this.dateNavigator != null) {
                final Date[] dates = page.datesInContent();
                if (dates != null) {
                    for (final Date date : dates) {
                        if (date != null) {
                            this.dateNavigator.inc(ISO8601Formatter.FORMATTER.format(date));
                        }
                    }
                }
            }

            // handle the vocabulary navigator
            if (this.vocabularyNavigator != null) {
                final Set<String> genericFacets = new LinkedHashSet<>();
                for (final Tagging v : LibraryProvider.autotagging.getVocabularies()) {
                    genericFacets.add(v.getName());
                }
                genericFacets.addAll(ProbabilisticClassifier.getContextNames());
                for (final String vocName : genericFacets) {
                    final String fieldName = CollectionSchema.VOCABULARY_PREFIX + vocName
                            + CollectionSchema.VOCABULARY_TERMS_SUFFIX;
                    incrementVocNavigator(page, vocName, fieldName);
                }
            }

            return page; // accept url
        }
        return null;
    }

    /**
     * Decrement statistics counts for the given RWI entry.
     * @param entry an RWI entry result
     */
    private void decrementCounts(final WordReferenceVars entry) {
        if(entry == null) {
            return;
        }
        if (entry.local()) {
            if(this.local_rwi_available.get() > 0) {
                this.local_rwi_available.decrementAndGet();
            }
        } else {
            if(this.remote_rwi_available.get() > 0) {
                this.remote_rwi_available.decrementAndGet();
            }
        }
    }

    /**
     * Update counters when evicting a Solr entry from results.
     * @param entry a Solr entry result to be evicted
     * @param local when true the entry is coming from the local Solr
     * @param facets facets counts from Solr
     * @param navIncrementedEarlier when true, navigators have been incremented earlier with other facets or individual documents
     */
    private void updateCountsOnSolrEntryToEvict(final URIMetadataNode entry,
            final Map<String, ReversibleScoreMap<String>> facets, final boolean local,
            final boolean navIncrementedEarlier) {
        if (entry == null) {
            return;
        }
        if (local) {
            this.local_solr_evicted.incrementAndGet();
            /*
             * No need to decrement remote_solr_available as this counter is only
             * incremented after all filterings have been applied
             */
        }

        /*
         * Update eventual navigators counters when relevant
         */
        final boolean navIncrementedWithFacets = facets != null && !facets.isEmpty() && !navIncrementedEarlier;
        ReversibleScoreMap<String> fcts;


        /* Iterate over active navigator plugins to let them update the counters */
        for (final String s : this.navigatorPlugins.keySet()) {
            final Navigator navi = this.navigatorPlugins.get(s);
            if (navi != null) {
                if (navIncrementedWithFacets) {
                    fcts = facets.get(navi.getIndexFieldName());
                } else {
                    fcts = null;
                }
                final Object value = entry.getFieldValue(navi.getIndexFieldName());
                if (value != null) {
                    if(value instanceof Collection) {
                        for (final Object singleVal : (Collection<?>) value) {
                            if (singleVal instanceof String) {
                                final String singleStringVal = (String)singleVal;
                                if (navIncrementedEarlier || (fcts != null && fcts.containsKey(singleStringVal))) {
                                    if (navi.get(singleStringVal) > 0) {
                                        navi.dec(singleStringVal);
                                    }
                                }
                            }
                        }
                    } else if(value instanceof String){
                        final String stringValue = (String)value;
                        if (navIncrementedEarlier || (fcts != null && fcts.containsKey(stringValue))) {
                            if (navi.get(stringValue) > 0) {
                                navi.dec(stringValue);
                            }
                        }
                    }
                }
            }
        }


        /* Note : would it be relevant to update here this.locationNavigator ?
         * It looks like this nav is currently never filled, as a constraint on coordinates
         * is expressed as a spatial filter not producing facets counts (see QueryParams.getFacetsFilterQueries())
         */

        if (this.dateNavigator != null) {
            if (navIncrementedWithFacets) {
                fcts = facets.get(CollectionSchema.dates_in_content_dts.getSolrFieldName());
            } else {
                fcts = null;
            }
            final Date[] dates = entry.datesInContent();
            if (dates != null) {
                for (final Date date : dates) {
                    if (date != null) {
                        final String dateStr = ISO8601Formatter.FORMATTER.format(date);
                        if (navIncrementedEarlier || (fcts != null && fcts.containsKey(dateStr))) {
                            if (this.dateNavigator.get(dateStr) > 0) {
                                this.dateNavigator.dec(dateStr);
                            }
                        }
                    }
                }
            }
        }

        if (this.protocolNavigator != null) {
            if (navIncrementedWithFacets) {
                fcts = facets.get(CollectionSchema.url_protocol_s.getSolrFieldName());
            } else {
                fcts = null;
            }
            final String protocol = entry.url().getProtocol();
            if (protocol != null) {
                if (navIncrementedEarlier || (fcts != null && fcts.containsKey(protocol))) {
                    if (this.protocolNavigator.get(protocol) > 0) {
                        this.protocolNavigator.dec(protocol);
                    }
                }
            }
        }

        // get the vocabulary navigation
        if (this.vocabularyNavigator != null) {
            final Set<String> genericFacets = new LinkedHashSet<>();
            for (final Tagging v : LibraryProvider.autotagging.getVocabularies()) {
                genericFacets.add(v.getName());
            }
            genericFacets.addAll(ProbabilisticClassifier.getContextNames());
            for (final String vocName : genericFacets) {
                final String fieldName = CollectionSchema.VOCABULARY_PREFIX + vocName
                        + CollectionSchema.VOCABULARY_TERMS_SUFFIX;
                if (navIncrementedWithFacets) {
                    fcts = facets.get(fieldName);
                } else {
                    fcts = null;
                }
                final Object docValue = entry.getFieldValue(fieldName);
                if (docValue instanceof String) {
                    if (navIncrementedEarlier || (fcts != null && fcts.containsKey((String) docValue))) {
                        final ScoreMap<String> vocNav = this.vocabularyNavigator.get(vocName);
                        if (vocNav != null && vocNav.get((String) docValue) > 0) {
                            vocNav.dec((String) docValue);
                        }
                    }
                } else if(docValue instanceof Collection) {
                    if (!((Collection<?>) docValue).isEmpty()) {
                        for (final Object singleDocValue : (Collection<?>) docValue) {
                            if (singleDocValue instanceof String) {
                                if (navIncrementedEarlier || (fcts != null && fcts.containsKey((String) singleDocValue))) {
                                    final ScoreMap<String> vocNav = this.vocabularyNavigator.get(vocName);
                                    if (vocNav != null && vocNav.get((String) singleDocValue) > 0) {
                                        vocNav.dec((String) singleDocValue);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }

    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }

    /**
     * Get topics in a ScoreMap if config allows topic navigator
     * (the topics are filtered by badwords, stopwords and words included in the query)
     *
     * @param count max number of topics returned
     * @return ScoreMap with max number of topics or null if
     */
    public ScoreMap<String> getTopicNavigator(final int count) {
        if (this.topicNavigatorCount > 0 && count >= 0) { //topicNavigatorCount set during init, 0=no nav
            if (!this.ref.sizeSmaller(2)) {
                ScoreMap<String> result;
                int ic = count != 0 ? count : this.topicNavigatorCount;

                if (this.ref.size() <= ic) { // size matches return map directly
                    result = this.getTopics(/*ic, 500*/);
                } else { // collect top most count topics
                    result = new ConcurrentScoreMap<>();
                    final Iterator<String> it = this.getTopics(/*ic, 500*/).keys(false);
                    while (ic-- > 0 && it.hasNext()) {
                        final String word = it.next();
                        result.set(word, this.ref.get(word));
                    }
                }
                return result;
            }
        }
        return null;
    }

    /**
     * Adds the retrieved results (fulltext & rwi) to the result list and
     * computes the text snippets
     * @param concurrentSnippetFetch when true, allow starting concurrent tasks to fetch snippets when no one are already available
     * @return true on adding entries to resultlist otherwise false
     */
    public boolean drainStacksToResult(boolean concurrentSnippetFetch) {
        // we take one entry from both stacks at the same time
        final boolean solrSuccess = drainSolrStackToResult(concurrentSnippetFetch);
        final boolean rwiSuccess = drainRWIStackToResult(concurrentSnippetFetch);
        return solrSuccess || rwiSuccess;
    }

    /**
     * Adds the retrieved results from local and remotes RWI to the result list and
     * computes the text snippets
     * @param concurrentSnippetFetch when true, allow starting a concurrent task to fetch a snippet when no one is already available
     * @return true when an entry has been effectively added to resultlist otherwise false
     */
    private boolean drainRWIStackToResult(boolean concurrentSnippetFetch) {
        boolean success = false;
        if (SearchEvent.this.snippetFetchAlive.get() >= 10 || MemoryControl.shortStatus() || !concurrentSnippetFetch) {
            // too many concurrent processes
            final URIMetadataNode noderwi = pullOneFilteredFromRWI(true);
            if (noderwi != null) {
                addResult(getSnippet(noderwi, null), noderwi.score());
                success = true;
            }
        } else {
            final Thread t = new Thread("SearchEvent.drainStacksToResult.oneFilteredFromRWI") {
                @Override
                public void run() {
                    SearchEvent.this.oneFeederStarted();
                    try {
                        final URIMetadataNode noderwi = pullOneFilteredFromRWI(true);
                        if (noderwi != null) {
                            SearchEvent.this.snippetFetchAlive.incrementAndGet();
                            try {
                                addResult(getSnippet(noderwi, SearchEvent.this.query.snippetCacheStrategy), noderwi.score());
                            } catch (final Throwable e) {
                                ConcurrentLog.logException(e);
                            } finally {
                                SearchEvent.this.snippetFetchAlive.decrementAndGet();
                            }
                        }
                    } catch (final Throwable e) {} finally {
                        SearchEvent.this.oneFeederTerminated();
                    }
                }
            };
            if (SearchEvent.this.query.snippetCacheStrategy == null) t.run(); else t.start(); //no need for concurrency if there is no latency
        }
        return success;
    }

    /**
     * Adds the retrieved full text results from local and remotes Solr to the result list and
     * computes the text snippets
     * @param concurrentSnippetFetch when true, allow starting a concurrent task to fetch a snippet when no one is already available
     * @return true when an entry has been effectively added to resultlist otherwise false
     */
    private boolean drainSolrStackToResult(boolean concurrentSnippetFetch) {
        boolean success = false;
        final Element<URIMetadataNode> localEntryElement = this.nodeStack.sizeQueue() > 0 ? this.nodeStack.poll() : null;
        final URIMetadataNode node = localEntryElement == null ? null : localEntryElement.getElement();
        if (node != null) {
            final LinkedHashSet<String> solrsnippetlines = this.snippets.remove(ASCII.String(node.hash())); // we can remove this because it's used only once
            if (solrsnippetlines != null && solrsnippetlines.size() > 0) {
                OpensearchResponseWriter.removeSubsumedTitle(solrsnippetlines, node.dc_title());
                final TextSnippet solrsnippet = new TextSnippet(node.url(), OpensearchResponseWriter.getLargestSnippet(solrsnippetlines), true, ResultClass.SOURCE_SOLR, "");
                final TextSnippet yacysnippet = new TextSnippet(this.loader,
                        node,
                        this.query.getQueryGoal().getIncludeWordsSet(),
                        this.query.getQueryGoal().getIncludeHashes(),
                        CacheStrategy.CACHEONLY,
                        false,
                        180,
                        false);
                final String solrsnippetline = solrsnippet.descriptionline(this.getQuery().getQueryGoal());
                final String yacysnippetline = yacysnippet.descriptionline(this.getQuery().getQueryGoal());
                final URIMetadataNode re = node.makeResultEntry(this.query.getSegment(), this.peers, solrsnippetline.length() >  yacysnippetline.length() ? solrsnippet : yacysnippet);
                addResult(re, localEntryElement.getWeight());
                success = true;
            } else {
                // we don't have a snippet from solr, try to get it in our way (by reloading, if necessary)
                if (SearchEvent.this.snippetFetchAlive.get() >= 10 || !concurrentSnippetFetch) {
                    // too many concurrent processes
                    addResult(getSnippet(node, null), localEntryElement.getWeight());
                    success = true;
                } else {

                    new Thread("SearchEvent.drainStacksToResult.getSnippet") {
                        @Override
                        public void run() {
                            SearchEvent.this.oneFeederStarted();
                            try {
                                SearchEvent.this.snippetFetchAlive.incrementAndGet();
                                try {
                                    addResult(getSnippet(node, SearchEvent.this.query.snippetCacheStrategy), localEntryElement.getWeight());
                                } catch (final Throwable e) {} finally {
                                    SearchEvent.this.snippetFetchAlive.decrementAndGet();
                                }
                            } catch (final Throwable e) {} finally {
                                SearchEvent.this.oneFeederTerminated();
                            }
                        }
                    }.start();
                }
            }
        }
        return success;
    }

    /**
     * place the result to the result vector and apply post-ranking
     * post-ranking is added to the current score,
     * @param resultEntry to add
     * @param score current ranking
     */
    public void addResult(URIMetadataNode resultEntry, final long score) {
        if (resultEntry == null) return;
        final long ranking = (score * 128) + postRanking(resultEntry, this.ref /*this.getTopicNavigator(MAX_TOPWORDS)*/);
        // TODO: above was originally using (see below), but getTopicNavigator returns this.ref and possibliy alters this.ref on first call (this.ref.size < 2 -> this.ref.clear)
        // TODO: verify and straighten the use of addTopic, getTopic and getTopicNavigator and related score calculation
        // final long ranking = ((long) (score * 128.f)) + postRanking(resultEntry, this.getTopicNavigator(MAX_TOPWORDS));

        resultEntry.setScore(ranking); // update the score of resultEntry for access by search interface / api
        this.resultList.put(new ReverseElement<>(resultEntry, ranking)); // remove smallest in case of overflow
        if (this.pollImmediately) this.resultList.poll(); // prevent re-ranking in case there is only a single index source which has already ranked entries.
        this.addTopics(resultEntry);
    }

    private long postRanking(final URIMetadataNode rentry, final ScoreMap<String> topwords) {
        long r = 0;

        // for media search: prefer pages with many links
        switch (this.query.contentdom) {
        case IMAGE:
            r += rentry.limage() << this.query.ranking.coeff_cathasimage;
            break;
        case AUDIO:
            r += rentry.laudio() << this.query.ranking.coeff_cathasaudio;
            break;
        case VIDEO:
            r += rentry.lvideo() << this.query.ranking.coeff_cathasvideo;
            break;
        case APP:
            r += rentry.lapp() << this.query.ranking.coeff_cathasapp;
            break;
        default:
            break;
        }

        // apply citation count
        //System.out.println("POSTRANKING CITATION: references = " + rentry.referencesCount() + ", inbound = " + rentry.llocal() + ", outbound = " + rentry.lother());
        if (this.query.getSegment().connectedCitation()) {
            final int referencesCount = this.query.getSegment().urlCitation().count(rentry.hash());
            r += (128 * referencesCount / (1 + 2 * rentry.llocal() + rentry.lother())) << this.query.ranking.coeff_citation;
        }
        // prefer hit with 'prefer' pattern
        if (this.query.prefer.matcher(rentry.url().toNormalform(true)).matches()) r += 255 << this.query.ranking.coeff_prefer;
        if (this.query.prefer.matcher(rentry.title()).matches()) r += 255 << this.query.ranking.coeff_prefer;

        // apply 'common-sense' heuristic using references
        final String urlstring = rentry.url().toNormalform(true);
        final String[] urlcomps = MultiProtocolURL.urlComps(urlstring);
        final String[] descrcomps = MultiProtocolURL.splitpattern.split(rentry.title().toLowerCase());

        // apply query-in-result matching
        final QueryGoal.NormalizedWords urlcompmap = new QueryGoal.NormalizedWords(urlcomps);
        final QueryGoal.NormalizedWords descrcompmap = new QueryGoal.NormalizedWords(descrcomps);
        // the token map is used (instead of urlcomps/descrcomps) to determine appearance in url/title and eliminate double occurances
        // (example Title="News News News News News News - today is party -- News News News News News News" to add one score instead of 12 * score !)
        for (final String urlcomp : urlcompmap) {
            final int tc = topwords.get(urlcomp);
            if (tc > 0) r += tc << this.query.ranking.coeff_urlcompintoplist;
        }
        for (final String descrcomp : descrcompmap) {
            final int tc = topwords.get(descrcomp);
            if (tc > 0) r += tc << this.query.ranking.coeff_descrcompintoplist;
        }

        final Iterator<String> shi = this.query.getQueryGoal().getIncludeWords();
        String queryword;
        while (shi.hasNext()) {
            queryword = shi.next();
            if (urlcompmap.contains(queryword)) r += 255 << this.query.ranking.coeff_appurl;
            if (descrcompmap.contains(queryword)) r += 255 << this.query.ranking.coeff_app_dc_title;
        }
        return r;
    }

    public URIMetadataNode getSnippet(URIMetadataNode page, final CacheStrategy cacheStrategy) {
        if (page == null) return null;

        if (cacheStrategy == null) {
            final TextSnippet snippet = new TextSnippet(
                    null,
                    page,
                    this.snippetFetchWords,
                    this.snippetFetchWordHashes,
                    null,
                    ((this.query.constraint != null) && (this.query.constraint.get(Tokenizer.flag_cat_indexof))),
                    SearchEvent.SNIPPET_MAX_LENGTH,
                    !this.query.isLocal());
            return page.makeResultEntry(this.query.getSegment(), this.peers, snippet); // result without snippet
        }

        // load snippet
        final ContentDomain contentDomain = page.getContentDomain();
        if (contentDomain == Classification.ContentDomain.TEXT || contentDomain == Classification.ContentDomain.ALL) {
            // attach text snippet
            final long startTime = System.currentTimeMillis();
            final TextSnippet snippet = new TextSnippet(
                    this.loader,
                    page,
                    this.snippetFetchWords,
                    this.snippetFetchWordHashes,
                    cacheStrategy,
                    ((this.query.constraint != null) && (this.query.constraint.get(Tokenizer.flag_cat_indexof))),
                    180,
                    !this.query.isLocal());
            SearchEvent.log.info("text snippet load time for " + page.url().toNormalform(true) + ": " + (System.currentTimeMillis() - startTime) + " ms, " + (!snippet.getErrorCode().fail() ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));

            if (!snippet.getErrorCode().fail()) {
                // we loaded the file and found the snippet
                return page.makeResultEntry(this.query.getSegment(), this.peers, snippet); // result with snippet attached
            } else if (cacheStrategy.mustBeOffline()) {
                // we did not demand online loading, therefore a failure does not mean that the missing snippet causes a rejection of this result
                // this may happen during a remote search, because snippet loading is omitted to retrieve results faster
                return page.makeResultEntry(this.query.getSegment(), this.peers, null); // result without snippet
            } else {
                // problems with snippet fetch
                if (this.snippetFetchWords.contains(Segment.catchallString)) {
                    // we accept that because the word cannot be on the page
                    return page.makeResultEntry(this.query.getSegment(), this.peers, null);
                }
                final String reason = "no text snippet; errorCode = " + snippet.getErrorCode();
                if (this.deleteIfSnippetFail) {
                    this.workTables.failURLsRegisterMissingWord(this.query.getSegment().termIndex(), page.url(), this.query.getQueryGoal().getIncludeHashes());
                }
                // to make an exception in case matches are only in the synonym field, we load the synonym field to check if the word is there
                final ArrayList<String> synonyms = page.getSynonyms();
                if (synonyms != null) {
                    Iterator<String> words = this.query.getQueryGoal().getIncludeWords();
                    while (words.hasNext()) {
                        String word = words.next().toLowerCase();
                        for (final String synonym : synonyms) {
                            if (synonym.toLowerCase().equals(word)) {
                                SearchEvent.log.info("accepted url " + page.url().toNormalform(true) + " without snippet: hit in synonyms field");
                                TextSnippet synonym_snippet = new TextSnippet(page.url(), "[Synonym Match]", true, ResultClass.SOURCE_METADATA, "");
                                return page.makeResultEntry(this.query.getSegment(), this.peers, synonym_snippet);
                            }
                        }
                    }
                }
                SearchEvent.log.info("sorted out url " + page.url().toNormalform(true) + " during search: " + reason);
                return null;
            }
        }
        return page.makeResultEntry(this.query.getSegment(), this.peers, null); // result without snippet
    }

    /**
     * This is the access point for the search interface to retrive ranked results.
     * for display.
     *
     * @param item requested result counting number (starting at 0)
     * @param timeout
     * @return
     */
    public URIMetadataNode oneResult(final int item, final long timeout) {
        // check if we already retrieved this item
        // (happens if a search pages is accessed a second time)
        final long finishTime = timeout == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "started, item = " + item + ", available = " + this.getResultCount(), 0, 0), false);

        // wait until a local solr is finished, we must do that to be able to check if we need more
        if (this.localsolrsearch != null && this.localsolrsearch.isAlive()) {
            try {
                this.localsolrsearch.join(100);
            } catch (final InterruptedException e) {
                log.warn("Wait for local solr search was interrupted.");
            }
        }
        if (item >= this.localsolroffset && this.local_solr_stored.get() == 0 && (this.localsolrsearch != null && this.localsolrsearch.isAlive())) {
            try {
                this.localsolrsearch.join();
            } catch (final InterruptedException e) {
                log.warn("Wait for local solr search was interrupted.");
            }
        }
        if (this.remote && item >= this.localsolroffset && this.local_solr_stored.get() > item) {
            /* Request mixing remote and local Solr results : load remaining local solr results now.
             * For local only search, a new SearchEvent should be created, starting directly at the requested offset,
             * thus allowing to handle last pages of large resultsets
             */
            final int nextitems = item - this.localsolroffset + this.query.itemsPerPage; // example: suddenly switch to item 60, just 10 had been shown, 20 loaded.
            if (this.localsolrsearch != null && this.localsolrsearch.isAlive()) {try {this.localsolrsearch.join();} catch (final InterruptedException e) {}}
            if (!Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_LOCAL_SOLR_OFF, false)) {
                // Do not increment again navigators from the local Solr on next local pages retrieval, as facets counts scope is on the total results and should already have been added
                final boolean useSolrFacets = (this.localsolrsearch == null);
                final boolean incrementNavigators = false;
                this.localsolrsearch = RemoteSearch.solrRemoteSearch(this,
                        this.query.solrQuery(this.query.contentdom, this.query.isStrictContentDom(), useSolrFacets, this.excludeintext_image),
                        this.localsolroffset, nextitems, null /* this peer */, 0, Switchboard.urlBlacklist, useSolrFacets, incrementNavigators);
            }
            this.localsolroffset += nextitems;
        }

        // now pull results as long as needed and as long as possible
        if (this.remote && item < 10 && this.resultList.sizeAvailable() <= item) {
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                log.warn("Remote search results wait was interrupted.");
            }
        }

        final int resultListIndex;
        if (this.remote) {
            resultListIndex = item;
        } else {
            resultListIndex = item - (this.localsolroffset - this.query.itemsPerPage);
        }
        while ( this.resultList.sizeAvailable() <= resultListIndex &&
                (this.rwiQueueSize() > 0 || this.nodeStack.sizeQueue() > 0 ||
                        (!this.isFeedingFinished() && System.currentTimeMillis() < finishTime))) {
            if (!drainStacksToResult(true)) {
                try {
                    Thread.sleep(10);
                } catch (final InterruptedException e) {
                    log.warn("Search results wait was interrupted.");
                }
            }
        }

        // check if we have a success
        if (this.resultList.sizeAvailable() > resultListIndex) {
            // we have the wanted result already in the result array .. return that
            final URIMetadataNode re = this.resultList.element(resultListIndex).getElement();
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "fetched, item = " + item + ", available = " + this.getResultCount() + ": " + re.urlstring(), 0, 0), false);

            /*
            if (this.localsolrsearch == null || (!this.localsolrsearch.isAlive() && this.local_solr_stored.get() > this.localsolroffset && (item + 1) % this.query.itemsPerPage == 0)) {
                // at the end of a list, trigger a next solr search
                if (!Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_LOCAL_SOLR_OFF, false)) {
                    this.localsolrsearch = RemoteSearch.solrRemoteSearch(this, this.query.solrQuery(this.query.contentdom, false, this.excludeintext_image), this.localsolroffset, this.query.itemsPerPage, null, 0, Switchboard.urlBlacklist);
                }
                this.localsolroffset += this.query.itemsPerPage;
            }
             */
            return re;
        }

        // no success
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "not found, item = " + item + ", available = " + this.getResultCount(), 0, 0), false);
        return null;
    }

    /** Image results counter */
    private int imagePageCounter = 0;
    private final LinkedHashMap<String, ImageResult> imageViewed = new LinkedHashMap<>();
    private final LinkedHashMap<String, ImageResult> imageSpareGood = new LinkedHashMap<>();
    private final LinkedHashMap<String, ImageResult> imageSpareBad = new LinkedHashMap<>();
    private ImageResult nthImage(int item) {
        final Object o = SetTools.nth(this.imageViewed.values(), item);
        if (o == null) return null;
        return (ImageResult) o;
    }
    private boolean hasSpare() {
        return this.imageSpareGood.size() > 0 || this.imageSpareBad.size() > 0;
    }
    private boolean containsSpare(String id) {
        return this.imageSpareGood.containsKey(id) || this.imageSpareBad.containsKey(id);
    }
    private int sizeSpare() {
        return this.imageSpareGood.size() + this.imageSpareBad.size();
    }
    private ImageResult nextSpare() {
        if (this.imageSpareGood.size() > 0) {
            final Map.Entry<String, ImageResult> next = this.imageSpareGood.entrySet().iterator().next();
            this.imageViewed.put(next.getKey(), next.getValue());
            this.imageSpareGood.remove(next.getKey());
            return next.getValue();
        }
        if (this.imageSpareBad.size() > 0) {
            final Map.Entry<String, ImageResult> next = this.imageSpareBad.entrySet().iterator().next();
            this.imageViewed.put(next.getKey(), next.getValue());
            this.imageSpareBad.remove(next.getKey());
            return next.getValue();
        }
        return null;
    }

    public ImageResult oneImageResult(final int item, final long timeout, final boolean strictContentDom) throws MalformedURLException {
        if (item < this.imageViewed.size()) return nthImage(item);
        if (this.imageSpareGood.size() > 0) return nextSpare(); // first put out all good spare, but no bad spare
        final URIMetadataNode doc = oneResult(this.imagePageCounter++, timeout); // we must use a different counter here because the image counter can be higher when one page filled up several spare
        // check if the match was made in the url or in the image links
        if (doc == null) {
            if (hasSpare()) return nextSpare();
            throw new MalformedURLException("no image url found");
        }
        // try to get more

        // there can be two different kinds of image hits: either the document itself is an image or images are embedded in the links of text documents.

        // boolean fakeImageHost = ms.url().getHost() != null && ms.url().getHost().indexOf("wikipedia") > 0; // pages with image extension from wikipedia do not contain image files but html files... I know this is a bad hack, but many results come from wikipedia and we must handle that
        // generalize above hack (regarding url with file extension but beeing a html (with html mime)
        if (doc.doctype() == Response.DT_IMAGE) {
            /* Icons are not always .ico files and should now be indexed in icons_urlstub_sxt. But this test still makes sense for older indexed documents,
             * or documents coming from previous versions peers */
            if (!doc.url().getFileName().endsWith(".ico")) { // we don't want favicons
                final String id = ASCII.String(doc.hash());
                // check image size
                final Collection<Object> height = doc.getFieldValues(CollectionSchema.images_height_val.getSolrFieldName());
                final Collection<Object> width = doc.getFieldValues(CollectionSchema.images_width_val.getSolrFieldName());
                final int h = height == null ? 0 : (Integer) height.iterator().next(); // might be -1 for unknown
                final int w = width == null ? 0 : (Integer) width.iterator().next();
                if ((h <= 0 || h > 16) && (w <= 0 || w > 16)) { // we don't want too small images (< 16x16)
                    if (!this.imageViewed.containsKey(id) && !containsSpare(id)) this.imageSpareGood.put(id, new ImageResult(doc.url(), doc.url(), doc.mime(), doc.title(), w, h, 0));
                }
            }
        } else if(!strictContentDom) {
            final Collection<Object> altO = doc.getFieldValues(CollectionSchema.images_alt_sxt.getSolrFieldName());
            final Collection<Object> imgO = doc.getFieldValues(CollectionSchema.images_urlstub_sxt.getSolrFieldName());
            if (imgO != null && imgO.size() > 0 && imgO instanceof List<?>) {
                final List<Object> alt = altO == null ? null : (List<Object>) altO;
                final List<Object> img = (List<Object>) imgO;
                final List<String> prt = CollectionConfiguration.indexedList2protocolList(doc.getFieldValues(CollectionSchema.images_protocol_sxt.getSolrFieldName()), img.size());
                final Collection<Object> heightO = doc.getFieldValues(CollectionSchema.images_height_val.getSolrFieldName());
                final Collection<Object> widthO = doc.getFieldValues(CollectionSchema.images_width_val.getSolrFieldName());
                final List<Object> height = heightO == null ? null : (List<Object>) heightO;
                final List<Object> width = widthO == null ? null : (List<Object>) widthO;
                for (int c = 0; c < img.size(); c++) {
                    final String image_urlstub =  (String) img.get(c);
                    /* Icons are not always .ico files and should now be indexed in icons_urlstub_sxt. But this test still makes sense for older indexed documents,
                     * or documents coming from previous versions peers */
                    if (image_urlstub.endsWith(".ico")) continue; // we don't want favicons, makes the result look idiotic
                    try {
                        final int h = height == null ? 0 : (Integer) height.get(c);
                        final int w = width == null ? 0 : (Integer) width.get(c);

                        // check size good for display (parser may init unknown dimension with -1)
                        if (h > 0 && h <= 16) continue; // to small for display
                        if (w > 0 && w <= 16) continue; // to small for display

                        final DigestURL imageUrl = new DigestURL((prt != null && prt.size() > c ? prt.get(c) : "http") + "://" + image_urlstub);
                        final String id = ASCII.String(imageUrl.hash());
                        if (!this.imageViewed.containsKey(id) && !containsSpare(id)) {
                            final String image_alt = (alt != null && alt.size() > c) ? (String) alt.get(c) : "";
                            final ImageResult imageResult = new ImageResult(doc.url(), imageUrl, "", image_alt, w, h, 0);
                            final boolean match = (this.query.getQueryGoal().matches(image_urlstub) || this.query.getQueryGoal().matches(image_alt));
                            if (match) this.imageSpareGood.put(id, imageResult); else this.imageSpareBad.put(id, imageResult);
                        }
                    } catch (final MalformedURLException e) {
                        continue;
                    }
                }
            }
        }
        if (hasSpare()) return nextSpare();
        throw new MalformedURLException("no image url found");
    }

    public class ImageResult {
        public DigestURL imageUrl, sourceUrl;
        public String mimetype = "", imagetext = "";
        public int width = 0, height = 0, fileSize = 0;
        public ImageResult(DigestURL sourceUrl, DigestURL imageUrl, String mimetype, String imagetext, int width, int height, int fileSize) {
            this.sourceUrl = sourceUrl;
            this.imageUrl = imageUrl;
            this.mimetype = mimetype;
            this.imagetext = imagetext.isEmpty() ? imageUrl.getFileName() : imagetext;
            this.width = width;
            this.height = height;
            this.fileSize = fileSize;
        }
        @Override
        public String toString() {
            return this.imageUrl.toNormalform(false);
        }
    }

    public ArrayList<WeakPriorityBlockingQueue.Element<URIMetadataNode>> completeResults(final long waitingtime) {
        final long timeout = waitingtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + waitingtime;
        int i = 0;

        while (this.resultList.sizeAvailable() < this.query.neededResults() && System.currentTimeMillis() < timeout) {
            final URIMetadataNode re = oneResult(i++, timeout - System.currentTimeMillis());
            if (re == null) break;
        }
        return this.resultList.list(Math.min(this.query.neededResults(), this.resultList.sizeAvailable()));
    }

    /**
     * Re-sort results cached in the resultList and eventually include in that list
     * elements with higher ranks from the Solr and RWI stacks.
     */
    public void resortCachedResults() {
        /*
         * If stacks feeding is finished, drain as much as possible elements from stacks
         * while their ranking is higher than the last element in the result list
         */
        if (isFeedingFinished() && this.resortCacheAllowed.tryAcquire()) {
            /*
             * First put all elements of the resultList in its own sorted queue to have a
             * consistent sorting on the whole set
             */
            this.resultList.requeueDrainedElements();

            /*
             * Note : if the resultList is full (its maxSize has been reached) some elements
             * with the lowest ranking may be lost in this next step. Not really a problem
             * because they were not supposed to be here. If really necessary to keep them,
             * growing the maxSize of the resultList should be considered here.
             */
            final WeakPriorityBlockingQueue.Element<URIMetadataNode> initialLastResult = this.resultList.getLastInQueue();

            /*
             * Drain stacks in two steps (Solr, then RWI), because one stack might still
             * contains higher ranked results when only lower ranked remain in the other
             */

            /*
             * Here we do not fetch snippets concurrently as we want to know immediately the
             * drained element position in the final result list
             */
            boolean drained = drainSolrStackToResult(false);
            WeakPriorityBlockingQueue.Element<URIMetadataNode> newLastResult = this.resultList.getLastInQueue();

            /*
             * Loop while at least one element has been added to the results list and is not
             * the last considering its final rank
             */
            while (drained && newLastResult == initialLastResult) {
                drained = drainSolrStackToResult(false);
                newLastResult = this.resultList.getLastInQueue();
            }

            drained = drainRWIStackToResult(false);
            newLastResult = this.resultList.getLastInQueue();

            /*
             * Loop while at least one element has been added to the results list and is not
             * the last considering its final rank
             */
            while (drained && newLastResult == initialLastResult) {
                drained = drainRWIStackToResult(false);
                newLastResult = this.resultList.getLastInQueue();
            }
        }
    }

    /**
     * delete a specific entry from the search results
     * this is used if the user clicks on a '-' sign beside the search result
     * @param urlhash
     * @return true if an entry was deleted, false otherwise
     */
    protected boolean delete(final String urlhash) {
        final Iterator<Element<URIMetadataNode>> i = this.resultList.iterator();
        Element<URIMetadataNode> entry;
        while (i.hasNext()) {
            entry = i.next();
            if (urlhash.equals(ASCII.String(entry.getElement().url().hash()))) {
                i.remove();
                return true;
            }
        }
        return false;
    }

    public ReferenceOrder getOrder() {
        return this.order;
    }

    /**
     * Check whether feeding from all available data sources is finished (remote
     *         RWI and Solr requests, local RWI and Solr requests, Heuristics
     *         requests...)
     * @return true when all available feeders on this search event are terminated
     */
    public boolean isFeedingFinished() {
        return
                this.feedersTerminated.intValue() > (this.remote ? 1 : 0) &&
                this.feedersAlive.get() == 0;
    }

    /**
     * method to signal the incoming stack that one feeder has terminated
     */
    public void oneFeederTerminated() {
        this.feedersTerminated.incrementAndGet();
        final int c = this.feedersAlive.decrementAndGet();
        assert c >= 0 : "feeders = " + c;
    }

    public void oneFeederStarted() {
        this.feedersAlive.incrementAndGet();
    }

    public QueryParams getQuery() {
        return this.query;
    }

    public int[] flagCount() {
        return this.flagcount;
    }

    protected void addBegin() {
        this.addRunning = true;
    }

    public void addFinalize() {
        this.addRunning = false;
    }

    protected boolean addRunning() {
        return this.addRunning;
    }

    public boolean rwiIsEmpty() {
        if ( !this.rwiStack.isEmpty() ) {
            return false;
        }
        for ( final WeakPriorityBlockingQueue<WordReferenceVars> s : this.doubleDomCache.values() ) {
            if ( !s.isEmpty() ) {
                return false;
            }
        }
        return true;
    }

    protected int rwiQueueSize() {
        int c = this.rwiStack.sizeQueue();
        for ( final WeakPriorityBlockingQueue<WordReferenceVars> s : this.doubleDomCache.values() ) {
            c += s.sizeQueue();
        }
        return c;
    }

    protected boolean testFlags(final Bitfield flags) {
        if (this.query.constraint == null) return true;
        // test if ientry matches with filter
        // if all = true: let only entries pass that has all matching bits
        // if all = false: let all entries pass that has at least one matching bit
        if (this.query.allofconstraint) {
            for ( int i = 0; i < 32; i++ ) {
                if ((this.query.constraint.get(i)) && (!flags.get(i))) return false;
            }
            return true;
        }
        for (int i = 0; i < 32; i++) {
            if ((this.query.constraint.get(i)) && (flags.get(i))) return true;
        }
        return false;
    }

    protected Map<byte[], ReferenceContainer<WordReference>> searchContainerMap() {
        // direct access to the result maps is needed for abstract generation
        // this is only available if execQuery() was called before
        return this.localSearchInclusion;
    }

    /**
     * Return the list of words that had been computed by statistics over all
     * words that appeared in the url or the description of all urls
     *
     * @return ScoreMap
     */
    public ScoreMap<String> getTopics(/* final int maxcount, final long maxtime */) {
        /* ---------------------------------- start of rem (2016-09-03)
        // TODO: result map is not used currently, verify if it should and use or delete this code block
        // TODO: as it is not used now - in favour of performance this code block is rem'ed (2016-09-03)

        final ScoreMap<String> result = new ConcurrentScoreMap<String>();
        if ( this.ref.sizeSmaller(2) ) {
            this.ref.clear(); // navigators with one entry are not useful
        }

        final Map<String, Float> counts = new HashMap<String, Float>();
        final Iterator<String> i = this.ref.keys(false);
        String word;
        int c;
        float q, min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        int ic = maxcount;
        long timeout = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        while ( ic-- > 0 && i.hasNext() && System.currentTimeMillis() < timeout) {
            word = i.next();
            if ( word == null ) {
                continue;
            }
            c = this.query.getSegment().getWordCountGuess(word);
            if ( c > 0 ) {
                q = ((float) this.ref.get(word)) / ((float) c);
                min = Math.min(min, q);
                max = Math.max(max, q);
                counts.put(word, q);
            }
        }
        if ( max > min ) {
            for ( final Map.Entry<String, Float> ce : counts.entrySet() ) {
                result.set(ce.getKey(), (int) (((double) maxcount) * (ce.getValue() - min) / (max - min)));
            }
        }
        /* ------------------------------------ end of rem (2016-09-03) */
        return this.ref;
    }

    private final static Pattern lettermatch = Pattern.compile("[a-z]+");

    /**
     * Collects topics in a ScoreMap for words not included in the query words.
     * Words are also filtered by badword blacklist and stopword list.
     * @param words
     */
    public void addTopic(final String[] words) {
        String word;
        for ( final String w : words ) {
            word = w.toLowerCase();
            if ( word.length() > 2
                    && "http_html_php_ftp_www_com_org_net_gov_edu_index_home_page_for_usage_the_and_zum_der_die_das_und_the_zur_bzw_mit_blog_wiki_aus_bei_off"
                    .indexOf(word) < 0
                    && !this.query.getQueryGoal().containsInclude(word)
                    && lettermatch.matcher(word).matches()
                    && !Switchboard.badwords.contains(word)
                    && !Switchboard.stopwords.contains(word) ) {
                this.ref.inc(word);
            }
        }
    }

    /**
     * Ad title words to this searchEvent's topic score map
     * @param resultEntry
     */
    protected void addTopics(final URIMetadataNode resultEntry) {
        // take out relevant information for reference computation
        if ((resultEntry.url() == null) || (resultEntry.title() == null)) return;
        final String[] descrcomps = MultiProtocolURL.splitpattern.split(resultEntry.title()); // words in the description

        // add references
        addTopic(descrcomps);
    }

}
