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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.storage.WeakPriorityBlockingQueue;
import net.yacy.cora.storage.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.EventTracker;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.crawler.CrawlProfile;
import de.anomic.search.MediaSnippet;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.graphics.ProfilingGraph;

public class ResultFetcher {
    
    // input values
    final RankingProcess  rankedCache; // ordered search results, grows dynamically as all the query threads enrich this container
    QueryParams     query;
    private final yacySeedDB      peers;
    
    // result values
    protected final LoaderDispatcher        loader;
    protected       Worker[]                workerThreads;
    protected final WeakPriorityBlockingQueue<ReverseElement<ResultEntry>>  result;
    protected final WeakPriorityBlockingQueue<ReverseElement<MediaSnippet>> images; // container to sort images by size
    protected final HandleSet               failedURLs; // a set of urlhashes that could not been verified during search
    protected final HandleSet               snippetFetchWordHashes; // a set of word hashes that are used to match with the snippets
    long urlRetrievalAllTime;
    long snippetComputationAllTime;
    int taketimeout;
    
    public ResultFetcher(
            final LoaderDispatcher loader,
            RankingProcess rankedCache,
            final QueryParams query,
            final yacySeedDB peers,
            final int taketimeout) {
    	
        this.loader = loader;
    	this.rankedCache = rankedCache;
    	this.query = query;
        this.peers = peers;
        this.taketimeout = taketimeout;
        
        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.result = new WeakPriorityBlockingQueue<ReverseElement<ResultEntry>>(-1); // this is the result, enriched with snippets, ranked and ordered by ranking
        this.images = new WeakPriorityBlockingQueue<ReverseElement<MediaSnippet>>(-1);
        this.failedURLs = new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0); // a set of url hashes where a worker thread tried to work on, but failed.
        
        // snippets do not need to match with the complete query hashes,
        // only with the query minus the stopwords which had not been used for the search
        HandleSet filtered;
        try {
            filtered = HandleSet.joinConstructive(query.queryHashes, Switchboard.stopwordHashes);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            filtered = new HandleSet(query.queryHashes.row().primaryKeyLength, query.queryHashes.comparator(), 0);
        }
        this.snippetFetchWordHashes = query.queryHashes.clone();
        if (filtered != null && !filtered.isEmpty()) {
            this.snippetFetchWordHashes.excludeDestructive(Switchboard.stopwordHashes);
        }
        
        // start worker threads to fetch urls and snippets
        this.workerThreads = null;
        deployWorker(Math.min(10, query.itemsPerPage), query.neededResults());
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.SNIPPETFETCH_START, this.workerThreads.length + " online snippet fetch threads started", 0, 0), false);
    }

    public void deployWorker(int deployCount, int neededResults) {
    	if (anyWorkerAlive()) return;
    	this.workerThreads = new Worker[(query.snippetCacheStrategy.isAllowedToFetchOnline()) ? deployCount : 1];
    	for (int i = 0; i < workerThreads.length; i++) {
    		this.workerThreads[i] = new Worker(i, 10000, query.snippetCacheStrategy, neededResults);
    		this.workerThreads[i].start();
        }
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
    
    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }
    
    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }

    protected class Worker extends Thread {
        
        private final long timeout; // the date until this thread should try to work
        private long lastLifeSign; // when the last time the run()-loop was executed
        private final int id;
        private final CrawlProfile.CacheStrategy cacheStrategy;
        private final int neededResults;
        
        public Worker(final int id, final long maxlifetime, CrawlProfile.CacheStrategy cacheStrategy, int neededResults) {
            this.id = id;
            this.cacheStrategy = cacheStrategy;
            this.lastLifeSign = System.currentTimeMillis();
            this.timeout = System.currentTimeMillis() + Math.max(1000, maxlifetime);
            this.neededResults = neededResults;
        }

        public void run() {

            // start fetching urls and snippets
            URIMetadataRow page;
            //final int fetchAhead = snippetMode == 0 ? 0 : 10;
            boolean nav_topics = query.navigators.equals("all") || query.navigators.indexOf("topics") >= 0;
            try {
                while (System.currentTimeMillis() < this.timeout) {
                	if (result.sizeAvailable() > neededResults) break;
                    this.lastLifeSign = System.currentTimeMillis();
    
                    // check if we have enough
                    if ((query.contentdom == ContentDomain.IMAGE) && (images.sizeAvailable() >= query.neededResults() + 50)) break;
                    if ((query.contentdom != ContentDomain.IMAGE) && (result.sizeAvailable() >= query.neededResults() + 10)) break;
    
                    // get next entry
                    page = rankedCache.takeURL(true, taketimeout);
                    //if (page == null) page = rankedCache.takeURL(false, taketimeout);
                    if (page == null) break;
                    if (failedURLs.has(page.hash())) continue;
                    
                    final ResultEntry resultEntry = fetchSnippet(page, cacheStrategy); // does not fetch snippets if snippetMode == 0

                    if (resultEntry == null) continue; // the entry had some problems, cannot be used
                    //if (result.contains(resultEntry)) continue;
                    
                    urlRetrievalAllTime += resultEntry.dbRetrievalTime;
                    snippetComputationAllTime += resultEntry.snippetComputationTime;
                    //System.out.println("+++DEBUG-resultWorker+++ fetched " + resultEntry.urlstring());
                    
                    // place the result to the result vector
                    // apply post-ranking
                    long ranking = Long.valueOf(rankedCache.getOrder().cardinal(resultEntry.word()));
                    ranking += postRanking(resultEntry, rankedCache.getTopics());
                    //System.out.println("*** resultEntry.hash = " + resultEntry.hash());
                    result.put(new ReverseElement<ResultEntry>(resultEntry, ranking)); // remove smallest in case of overflow
                    if (nav_topics) rankedCache.addTopics(resultEntry);
                    //System.out.println("DEBUG SNIPPET_LOADING: thread " + id + " got " + resultEntry.url());
                }
            } catch (final Exception e) {
                Log.logException(e);
            }
            Log.logInfo("SEARCH", "resultWorker thread " + id + " terminated");
        }
        
        public long busytime() {
            return System.currentTimeMillis() - this.lastLifeSign;
        }
    }
    
    protected ResultEntry fetchSnippet(final URIMetadataRow page, CrawlProfile.CacheStrategy cacheStrategy) {
        // Snippet Fetching can has 3 modes:
        // 0 - do not fetch snippets
        // 1 - fetch snippets offline only
        // 2 - online snippet fetch
        
        // load only urls if there was not yet a root url of that hash
        // find the url entry

        long startTime = System.currentTimeMillis();
        final URIMetadataRow.Components metadata = page.metadata();
        if (metadata == null) return null;
        final long dbRetrievalTime = System.currentTimeMillis() - startTime;
        
        if (cacheStrategy == null) {
            return new ResultEntry(page, query.getSegment(), peers, null, null, dbRetrievalTime, 0); // result without snippet
        }
        
        // load snippet
        if (query.contentdom == ContentDomain.TEXT) {
            // attach text snippet
            startTime = System.currentTimeMillis();
            final TextSnippet snippet = TextSnippet.retrieveTextSnippet(
                    this.loader,
                    metadata,
                    snippetFetchWordHashes,
                    cacheStrategy,
                    ((query.constraint != null) && (query.constraint.get(Condenser.flag_cat_indexof))),
                    180,
                    Integer.MAX_VALUE,
                    query.isGlobal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH", "text snippet load time for " + metadata.url() + ": " + snippetComputationTime + ", " + ((snippet.getErrorCode() < 11) ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));
            
            if (snippet.getErrorCode() < 11) {
                // we loaded the file and found the snippet
                return new ResultEntry(page, query.getSegment(), peers, snippet, null, dbRetrievalTime, snippetComputationTime); // result with snippet attached
            } else if (cacheStrategy.mustBeOffline()) {
                // we did not demand online loading, therefore a failure does not mean that the missing snippet causes a rejection of this result
                // this may happen during a remote search, because snippet loading is omitted to retrieve results faster
                return new ResultEntry(page, query.getSegment(), peers, null, null, dbRetrievalTime, snippetComputationTime); // result without snippet
            } else {
                // problems with snippet fetch
                registerFailure(page.hash(), "no text snippet for URL " + metadata.url() + "; errorCode = " + snippet.getErrorCode());
                return null;
            }
        } else {
            // attach media information
            startTime = System.currentTimeMillis();
            final ArrayList<MediaSnippet> mediaSnippets = MediaSnippet.retrieveMediaSnippets(metadata.url(), snippetFetchWordHashes, query.contentdom, cacheStrategy, 6000, query.isGlobal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH", "media snippet load time for " + metadata.url() + ": " + snippetComputationTime);
            
            if (mediaSnippets != null && !mediaSnippets.isEmpty()) {
                // found media snippets, return entry
                return new ResultEntry(page, query.getSegment(), peers, null, mediaSnippets, dbRetrievalTime, snippetComputationTime);
            } else if (cacheStrategy.mustBeOffline()) {
                return new ResultEntry(page, query.getSegment(), peers, null, null, dbRetrievalTime, snippetComputationTime);
            } else {
                // problems with snippet fetch
                registerFailure(page.hash(), "no media snippet for URL " + metadata.url());
                return null;
            }
        }
        // finished, no more actions possible here
    }
    
    private void registerFailure(final byte[] urlhash, final String reason) {
        try {
            this.failedURLs.put(urlhash);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        Log.logInfo("SEARCH", "sorted out urlhash " + new String(urlhash) + " during search: " + reason);
    }
    
    public ResultEntry oneResult(final int item) {
        // check if we already retrieved this item
    	// (happens if a search pages is accessed a second time)
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.ONERESULT, "started, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
        if (this.result.sizeAvailable() > item) {
            // we have the wanted result already in the result array .. return that
            ResultEntry re = this.result.element(item).getElement();
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.ONERESULT, "prefetched, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
            return re;
        }
        /*
        System.out.println("rankedCache.size() = " + this.rankedCache.size());
        System.out.println("result.size() = " + this.result.size());
        System.out.println("query.neededResults() = " + query.neededResults());
        */
        if ((!anyWorkerAlive()) &&
            (((query.contentdom == ContentDomain.IMAGE) && (images.sizeAvailable() + 30 < query.neededResults())) ||
             (this.result.sizeAvailable() < query.neededResults())) &&
            //(event.query.onlineSnippetFetch) &&
            (this.rankedCache.size() > this.result.sizeAvailable())
           ) {
        	// start worker threads to fetch urls and snippets
            deployWorker(Math.min(10, query.itemsPerPage), query.neededResults());
        }

        // finally wait until enough results are there produced from the
        // snippet fetch process
        while ((anyWorkerAlive()) && (result.sizeAvailable() <= item)) {
            try {Thread.sleep((item % query.itemsPerPage) * 10L);} catch (final InterruptedException e) {}
        }

        // finally, if there is something, return the result
        if (this.result.sizeAvailable() <= item) {
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.ONERESULT, "not found, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
            return null;
        }
        ResultEntry re = this.result.element(item).getElement();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.ONERESULT, "retrieved, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
        return re;
    }
    
    private int resultCounter = 0;
    public ResultEntry nextResult() {
        final ResultEntry re = oneResult(resultCounter);
        resultCounter++;
        return re;
    }
    
    public MediaSnippet oneImage(final int item) {
        // always look for a next object if there are way too less
        if (this.images.sizeAvailable() <= item + 10) fillImagesCache();

        // check if we already retrieved the item
        if (this.images.sizeDrained() > item) return this.images.element(item).getElement();
        
        // look again if there are not enough for presentation
        while (this.images.sizeAvailable() <= item) {
            if (fillImagesCache() == 0) break;
        }        
        if (this.images.sizeAvailable() <= item) return null;
        
        // now take the specific item from the image stack
        return this.images.element(item).getElement();
    }
    
    private int fillImagesCache() {
        ResultEntry result = nextResult();
        int c = 0;
        if (result == null) return c;
        // iterate over all images in the result
        final ArrayList<MediaSnippet> imagemedia = result.mediaSnippets();
        if (imagemedia != null) {
            for (MediaSnippet ms: imagemedia) {
                images.put(new ReverseElement<MediaSnippet>(ms, ms.ranking)); // remove smallest in case of overflow
                c++;
                //System.out.println("*** image " + new String(ms.href.hash()) + " images.size = " + images.size() + "/" + images.size());
            }
        }
        return c;
    }
     
    public ArrayList<ReverseElement<ResultEntry>> completeResults(final long waitingtime) {
        final long timeout = System.currentTimeMillis() + waitingtime;
        while ((result.sizeAvailable() < query.neededResults()) && (anyWorkerAlive()) && (System.currentTimeMillis() < timeout)) {
            try {Thread.sleep(100);} catch (final InterruptedException e) {}
            //System.out.println("+++DEBUG-completeResults+++ sleeping " + 200);
        }
        return this.result.list(this.result.sizeAvailable());
    }

    public long postRanking(
            final ResultEntry rentry,
            final Map<String, Navigator.Item> topwords) {

        long r = 0;
        
        // for media search: prefer pages with many links
        if (query.contentdom == ContentDomain.IMAGE) r += rentry.limage() << query.ranking.coeff_cathasimage;
        if (query.contentdom == ContentDomain.AUDIO) r += rentry.laudio() << query.ranking.coeff_cathasaudio;
        if (query.contentdom == ContentDomain.VIDEO) r += rentry.lvideo() << query.ranking.coeff_cathasvideo;
        if (query.contentdom == ContentDomain.APP  ) r += rentry.lapp()   << query.ranking.coeff_cathasapp;
        
        // prefer hit with 'prefer' pattern
        if (query.prefer.matcher(rentry.url().toNormalform(true, true)).matches()) r += 256 << query.ranking.coeff_prefer;
        if (query.prefer.matcher(rentry.title()).matches()) r += 256 << query.ranking.coeff_prefer;
        
        // apply 'common-sense' heuristic using references
        final String urlstring = rentry.url().toNormalform(true, true);
        final String[] urlcomps = MultiProtocolURI.urlComps(urlstring);
        final String[] descrcomps = MultiProtocolURI.splitpattern.split(rentry.title().toLowerCase());
        Navigator.Item tc;
        for (int j = 0; j < urlcomps.length; j++) {
            tc = topwords.get(urlcomps[j]);
            if (tc != null) r += Math.max(1, tc.count) << query.ranking.coeff_urlcompintoplist;
        }
        for (int j = 0; j < descrcomps.length; j++) {
            tc = topwords.get(descrcomps[j]);
            if (tc != null) r += Math.max(1, tc.count) << query.ranking.coeff_descrcompintoplist;
        }
        
        // apply query-in-result matching
        final HandleSet urlcomph = Word.words2hashesHandles(urlcomps);
        final HandleSet descrcomph = Word.words2hashesHandles(descrcomps);
        final Iterator<byte[]> shi = query.queryHashes.iterator();
        byte[] queryhash;
        while (shi.hasNext()) {
            queryhash = shi.next();
            if (urlcomph.has(queryhash)) r += 256 << query.ranking.coeff_appurl;
            if (descrcomph.has(queryhash)) r += 256 << query.ranking.coeff_app_dc_title;
        }
        
        return r;
    }

}
