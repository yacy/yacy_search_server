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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.crawler.ResultURLs;
import de.anomic.document.Condenser;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.Segment;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.SetTools;
import de.anomic.kelondro.util.SortStack;
import de.anomic.kelondro.util.SortStore;
import de.anomic.search.RankingProcess.NavigatorEntry;
import de.anomic.search.SnippetCache.MediaSnippet;
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
    
    protected final static int workerThreadCount = 10;
    public static String lastEventID = "";
    private static final int max_results_preparation = 1000;
    
    protected long eventTime;
    protected QueryParams query;
    protected final Segment indexSegment;
    private final yacySeedDB peers;
    protected RankingProcess rankedCache; // ordered search results, grows dynamically as all the query threads enrich this container
    private final IndexAbstracts rcAbstracts; // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation
    private yacySearch[] primarySearchThreads, secondarySearchThreads;
    private Thread localSearchThread;
    private final TreeMap<byte[], String> preselectedPeerHashes;
    //private Object[] references;
    public  TreeMap<byte[], String> IAResults;
    public  TreeMap<byte[], Integer> IACount;
    public  byte[] IAmaxcounthash, IAneardhthash;
    protected SnippetFetcher[] workerThreads;
    protected SortStore<ResultEntry> result;
    protected SortStore<SnippetCache.MediaSnippet> images; // container to sort images by size
    protected HashMap<String, String> failedURLs; // a mapping from a urlhash to a fail reason string
    protected TreeSet<byte[]> snippetFetchWordHashes; // a set of word hashes that are used to match with the snippets
    long urlRetrievalAllTime;
    long snippetComputationAllTime;
    public ResultURLs crawlResults;
    
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
        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.workerThreads = null;
        this.localSearchThread = null;
        this.result = new SortStore<ResultEntry>(-1); // this is the result, enriched with snippets, ranked and ordered by ranking
        this.images = new SortStore<SnippetCache.MediaSnippet>(-1);
        this.failedURLs = new HashMap<String, String>(); // a map of urls to reason strings where a worker thread tried to work on, but failed.
        
        // snippets do not need to match with the complete query hashes,
        // only with the query minus the stopwords which had not been used for the search
        final TreeSet<byte[]> filtered = SetTools.joinConstructive(query.queryHashes, Switchboard.stopwordHashes);
        this.snippetFetchWordHashes = (TreeSet<byte[]>) query.queryHashes.clone();
        if ((filtered != null) && (filtered.size() > 0)) {
            SetTools.excludeDestructive(this.snippetFetchWordHashes, Switchboard.stopwordHashes);
        }
        
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
        this.workerThreads = new SnippetFetcher[(query.onlineSnippetFetch) ? workerThreadCount : 1];
        for (int i = 0; i < this.workerThreads.length; i++) {
            this.workerThreads[i] = new SnippetFetcher(i, 10000, (query.onlineSnippetFetch) ? 2 : 0);
            this.workerThreads[i].start();
        }
        serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), this.workerThreads.length + " online snippet fetch threads started", 0, 0), false);
    
        // clean up events
        SearchEventCache.cleanupEvents(false);
        serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "event-cleanup", 0, 0), false);
        
        // store this search to a cache so it can be re-used
        if (MemoryControl.available() < 1024 * 1024 * 10) SearchEventCache.cleanupEvents(true);
        lastEventID = query.id(false);
        SearchEventCache.lastEvents.put(lastEventID, this);
    }

    ResultEntry obtainResultEntry(final URLMetadataRow page, final int snippetFetchMode) {

        // a search result entry needs some work to produce a result Entry:
        // - check if url entry exists in LURL-db
        // - check exclusions, constraints, masks, media-domains
        // - load snippet (see if page exists) and check if snippet contains searched word

        // Snippet Fetching can has 3 modes:
        // 0 - do not fetch snippets
        // 1 - fetch snippets offline only
        // 2 - online snippet fetch
        
        // load only urls if there was not yet a root url of that hash
        // find the url entry

        long startTime = System.currentTimeMillis();
        final URLMetadataRow.Components metadata = page.metadata();
        final String pagetitle = metadata.dc_title().toLowerCase();
        if (metadata.url() == null) {
            registerFailure(page.hash(), "url corrupted (null)");
            return null; // rare case where the url is corrupted
        }
        final String pageurl = metadata.url().toString().toLowerCase();
        final String pageauthor = metadata.dc_creator().toLowerCase();
        final long dbRetrievalTime = System.currentTimeMillis() - startTime;
        
        // check exclusion
        if ((QueryParams.matches(pagetitle, query.excludeHashes)) ||
            (QueryParams.matches(pageurl, query.excludeHashes)) ||
            (QueryParams.matches(pageauthor, query.excludeHashes))) {
            return null;
        }
            
        // check url mask
        if (!(pageurl.matches(query.urlMask))) {
            return null;
        }
            
        // check constraints
        if ((query.constraint != null) &&
            (query.constraint.get(Condenser.flag_cat_indexof)) &&
            (!(metadata.dc_title().startsWith("Index of")))) {
            final Iterator<byte[]> wi = query.queryHashes.iterator();
            while (wi.hasNext()) try { indexSegment.termIndex().remove(wi.next(), page.hash()); } catch (IOException e) {}
            registerFailure(page.hash(), "index-of constraint not fullfilled");
            return null;
        }
        
        if ((query.contentdom == QueryParams.CONTENTDOM_AUDIO) && (page.laudio() == 0)) {
            registerFailure(page.hash(), "contentdom-audio constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == QueryParams.CONTENTDOM_VIDEO) && (page.lvideo() == 0)) {
            registerFailure(page.hash(), "contentdom-video constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == QueryParams.CONTENTDOM_IMAGE) && (page.limage() == 0)) {
            registerFailure(page.hash(), "contentdom-image constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == QueryParams.CONTENTDOM_APP) && (page.lapp() == 0)) {
            registerFailure(page.hash(), "contentdom-app constraint not fullfilled");
            return null;
        }

        if (snippetFetchMode == 0) {
            return new ResultEntry(page, indexSegment, peers, null, null, dbRetrievalTime, 0); // result without snippet
        }
        
        // load snippet
        if (query.contentdom == QueryParams.CONTENTDOM_TEXT) {
            // attach text snippet
            startTime = System.currentTimeMillis();
            final SnippetCache.TextSnippet snippet = SnippetCache.retrieveTextSnippet(metadata, snippetFetchWordHashes, (snippetFetchMode == 2), ((query.constraint != null) && (query.constraint.get(Condenser.flag_cat_indexof))), 180, (snippetFetchMode == 2) ? Integer.MAX_VALUE : 30000, query.isGlobal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH_EVENT", "text snippet load time for " + metadata.url() + ": " + snippetComputationTime + ", " + ((snippet.getErrorCode() < 11) ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));
            
            if (snippet.getErrorCode() < 11) {
                // we loaded the file and found the snippet
                return new ResultEntry(page, indexSegment, peers, snippet, null, dbRetrievalTime, snippetComputationTime); // result with snippet attached
            } else if (snippetFetchMode == 1) {
                // we did not demand online loading, therefore a failure does not mean that the missing snippet causes a rejection of this result
                // this may happen during a remote search, because snippet loading is omitted to retrieve results faster
                return new ResultEntry(page, indexSegment, peers, null, null, dbRetrievalTime, snippetComputationTime); // result without snippet
            } else {
                // problems with snippet fetch
                registerFailure(page.hash(), "no text snippet for URL " + metadata.url());
                if (!peers.mySeed().isVirgin())
                    try {
                        SnippetCache.failConsequences(snippet, query.id(false));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                return null;
            }
        } else {
            // attach media information
            startTime = System.currentTimeMillis();
            final ArrayList<MediaSnippet> mediaSnippets = SnippetCache.retrieveMediaSnippets(metadata.url(), snippetFetchWordHashes, query.contentdom, (snippetFetchMode == 2), 6000, query.isGlobal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH_EVENT", "media snippet load time for " + metadata.url() + ": " + snippetComputationTime);
            
            if ((mediaSnippets != null) && (mediaSnippets.size() > 0)) {
                // found media snippets, return entry
                return new ResultEntry(page, indexSegment, peers, null, mediaSnippets, dbRetrievalTime, snippetComputationTime);
            } else if (snippetFetchMode == 1) {
                return new ResultEntry(page, indexSegment, peers, null, null, dbRetrievalTime, snippetComputationTime);
            } else {
                // problems with snippet fetch
                registerFailure(page.hash(), "no media snippet for URL " + metadata.url());
                return null;
            }
        }
        // finished, no more actions possible here
    }
    
    boolean anyWorkerAlive() {
        if (this.workerThreads == null) return false;
        for (int i = 0; i < this.workerThreads.length; i++) {
           if ((this.workerThreads[i] != null) &&
        	   (this.workerThreads[i].isAlive()) &&
        	   (this.workerThreads[i].busytime() < 3000)) return true;
        }
        return false;
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
    
    public QueryParams getQuery() {
        return query;
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
    
    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }
    
    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }

    protected class SnippetFetcher extends Thread {
        
        private final long timeout; // the date until this thread should try to work
        private long lastLifeSign; // when the last time the run()-loop was executed
        private final int id;
        private int snippetMode;
        
        public SnippetFetcher(final int id, final long maxlifetime, int snippetMode) {
            this.id = id;
            this.snippetMode = snippetMode;
            this.lastLifeSign = System.currentTimeMillis();
            this.timeout = System.currentTimeMillis() + Math.max(1000, maxlifetime);
        }

        public void run() {

            // start fetching urls and snippets
            URLMetadataRow page;
            final int fetchAhead = snippetMode == 0 ? 0 : 10;
            boolean nav_topics = query.navigators.equals("all") || query.navigators.indexOf("topics") >= 0;
            try {
                while (System.currentTimeMillis() < this.timeout) {
                    this.lastLifeSign = System.currentTimeMillis();
    
                    // check if we have enough
                    if ((query.contentdom == QueryParams.CONTENTDOM_IMAGE) && (images.size() >= query.neededResults() + fetchAhead)) break;
                    if ((query.contentdom != QueryParams.CONTENTDOM_IMAGE) && (result.size() >= query.neededResults() + fetchAhead)) break;
    
                    // get next entry
                    page = rankedCache.bestURL(true);
                    if (page == null) {
                    	if (!anyRemoteSearchAlive()) break; // we cannot expect more results
                        // if we did not get another entry, sleep some time and try again
                        try {Thread.sleep(10);} catch (final InterruptedException e1) {}
                        continue;
                    }
                    if (result.exists(page.hash().hashCode())) continue;
                    if (failedURLs.get(page.hash()) != null) continue;
                    
                    // try secondary search
                    prepareSecondarySearch(); // will be executed only once
                    
                    final ResultEntry resultEntry = obtainResultEntry(page, snippetMode);
                    if (resultEntry == null) continue; // the entry had some problems, cannot be used
                    urlRetrievalAllTime += resultEntry.dbRetrievalTime;
                    snippetComputationAllTime += resultEntry.snippetComputationTime;
                    //System.out.println("+++DEBUG-resultWorker+++ fetched " + resultEntry.urlstring());
                    
                    // place the result to the result vector
                    if (!result.exists(resultEntry)) {
                        result.push(resultEntry, Long.valueOf(rankedCache.getOrder().cardinal(resultEntry.word())));
                        if (nav_topics) rankedCache.addTopics(resultEntry);
                    }
                    //System.out.println("DEBUG SNIPPET_LOADING: thread " + id + " got " + resultEntry.url());
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
            Log.logInfo("SEARCH", "resultWorker thread " + id + " terminated");
        }
        
        public long busytime() {
        	return System.currentTimeMillis() - this.lastLifeSign;
        }
    }
    
    private void registerFailure(final String urlhash, final String reason) {
        this.failedURLs.put(urlhash, reason);
        Log.logInfo("search", "sorted out hash " + urlhash + " during search: " + reason);
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
        // check if we already retrieved this item (happens if a search
        // pages is accessed a second time)
        serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "obtain one result entry - start", 0, 0), false);
        if (this.result.sizeStore() > item) {
            // we have the wanted result already in the result array .. return that
            return this.result.element(item).element;
        }
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
                   anyWorkerAlive() &&
                   (result.size() <= item || countFinishedRemoteSearch() <= item)) {
                try {Thread.sleep(item * 50L);} catch (final InterruptedException e) {}
            }

        }
        // finally wait until enough results are there produced from the
        // snippet fetch process
        while ((anyWorkerAlive()) && (result.size() <= item)) {
            try {Thread.sleep(item * 50L);} catch (final InterruptedException e) {}
        }

        // finally, if there is something, return the result
        if (this.result.size() <= item) return null;
        return this.result.element(item).element;
    }
    
    private int resultCounter = 0;
    public ResultEntry nextResult() {
        final ResultEntry re = oneResult(resultCounter);
        resultCounter++;
        return re;
    }
    
    public SnippetCache.MediaSnippet oneImage(final int item) {
        // check if we already retrieved this item (happens if a search pages is accessed a second time)
        if (this.images.sizeStore() > item) {
            // we have the wanted result already in the result array .. return that
            return this.images.element(item).element;
        }
        
        // feed some results from the result stack into the image stack
        final int count = Math.min(5, Math.max(1, 10 * this.result.size() / (item + 1)));
        for (int i = 0; i < count; i++) {
            // generate result object
            final ResultEntry result = nextResult();
            SnippetCache.MediaSnippet ms;
            if (result != null) {
                // iterate over all images in the result
                final ArrayList<SnippetCache.MediaSnippet> imagemedia = result.mediaSnippets();
                if (imagemedia != null) {
                    for (int j = 0; j < imagemedia.size(); j++) {
                        ms = imagemedia.get(j);
                        images.push(ms, Long.valueOf(ms.ranking));
                    }
                }
            }
        }
        
        // now take the specific item from the image stack
        if (this.images.size() <= item) return null;
        return this.images.element(item).element;
    }
    
    public ArrayList<SortStack<ResultEntry>.stackElement> completeResults(final long waitingtime) {
        final long timeout = System.currentTimeMillis() + waitingtime;
        while ((result.size() < query.neededResults()) && (anyWorkerAlive()) && (System.currentTimeMillis() < timeout)) {
            try {Thread.sleep(100);} catch (final InterruptedException e) {}
            //System.out.println("+++DEBUG-completeResults+++ sleeping " + 200);
        }
        return this.result.list(this.result.size());
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
    
}
