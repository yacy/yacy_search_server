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
import java.util.List;
import java.util.regex.Pattern;


import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.storage.ScoreMap;
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
import de.anomic.data.WorkTables;
import de.anomic.http.client.Cache;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.graphics.ProfilingGraph;

public class ResultFetcher {
    
    // input values
    final RankingProcess  rankingProcess; // ordered search results, grows dynamically as all the query threads enrich this container
    QueryParams     query;
    private final yacySeedDB      peers;
    private final WorkTables workTables;
    
    // result values
    protected final LoaderDispatcher        loader;
    protected       Worker[]                workerThreads;
    protected final WeakPriorityBlockingQueue<ResultEntry>  result;
    protected final WeakPriorityBlockingQueue<MediaSnippet> images; // container to sort images by size
    protected final HandleSet               snippetFetchWordHashes; // a set of word hashes that are used to match with the snippets
    long urlRetrievalAllTime;
    long snippetComputationAllTime;
    int taketimeout;
    private final boolean deleteIfSnippetFail;
    
    public ResultFetcher(
            final LoaderDispatcher loader,
            RankingProcess rankedCache,
            final QueryParams query,
            final yacySeedDB peers,
            final WorkTables workTables,
            final int taketimeout,
            final boolean deleteIfSnippetFail) {
    	assert query != null;
        this.loader = loader;
    	this.rankingProcess = rankedCache;
    	this.query = query;
        this.peers = peers;
        this.workTables = workTables;
        this.taketimeout = taketimeout;
        this.deleteIfSnippetFail = deleteIfSnippetFail;
        
        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.result = new WeakPriorityBlockingQueue<ResultEntry>(-1); // this is the result, enriched with snippets, ranked and ordered by ranking
        this.images = new WeakPriorityBlockingQueue<MediaSnippet>(-1);
        
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
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.SNIPPETFETCH_START, ((this.workerThreads == null) ? "no" : this.workerThreads.length) + " online snippet fetch threads started", 0, 0), false);
    }
    
    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }
    
    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }
    
    public ResultEntry oneResult(final int item, long timeout) {
        // check if we already retrieved this item
    	// (happens if a search pages is accessed a second time)
        long finishTime = System.currentTimeMillis() + timeout;
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.ONERESULT, "started, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
        if (this.result.sizeAvailable() > item) {
            // we have the wanted result already in the result array .. return that
            ResultEntry re = this.result.element(item).getElement();
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.ONERESULT, "prefetched, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
            return re;
        }
        
        // deploy worker to get more results        
        deployWorker(Math.min(20, query.itemsPerPage), item + query.itemsPerPage);

        // finally wait until enough results are there produced from the snippet fetch process
        WeakPriorityBlockingQueue.Element<ResultEntry> entry = null;
        while (System.currentTimeMillis() < finishTime) {
            if (this.result.sizeAvailable() + this.rankingProcess.sizeQueue() <= item  && !anyWorkerAlive() && this.rankingProcess.feedingIsFinished()) break;
            try {entry = this.result.element(item, 50);} catch (InterruptedException e) {Log.logException(e);}
            if (entry != null) break;
            if (!anyWorkerAlive() && this.rankingProcess.sizeQueue() == 0 && this.rankingProcess.feedingIsFinished()) break;
        }
        
        // finally, if there is something, return the result
        if (entry == null) {
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.ONERESULT, "not found, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
            return null;
        }
        ResultEntry re = entry.getElement();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.ONERESULT, "retrieved, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
        return re;
    }
    
    private int resultCounter = 0;
    public ResultEntry nextResult() {
        final ResultEntry re = oneResult(resultCounter, 1000);
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
        final List<MediaSnippet> imagemedia = result.mediaSnippets();
        if (imagemedia != null) {
            feedloop: for (final MediaSnippet ms: imagemedia) {
                // check cache to see if the mime type of the image url is correct
                ResponseHeader header = Cache.getResponseHeader(ms.href.hash());
                if (header != null) {
                    // this does not work for all urls since some of them may not be in the cache
                    if (header.mime().startsWith("text") || header.mime().startsWith("application")) continue feedloop;
                }
                images.put(new ReverseElement<MediaSnippet>(ms, ms.ranking)); // remove smallest in case of overflow
                c++;
                //System.out.println("*** image " + UTF8.String(ms.href.hash()) + " images.size = " + images.size() + "/" + images.size());
            }
        }
        return c;
    }
     
    public ArrayList<WeakPriorityBlockingQueue.Element<ResultEntry>> completeResults(final long waitingtime) {
        final long timeout = System.currentTimeMillis() + waitingtime;
        while ( result.sizeAvailable() < query.neededResults() &&
                anyWorkerAlive() &&
                System.currentTimeMillis() < timeout) {
            try {Thread.sleep(20);} catch (final InterruptedException e) {}
            //System.out.println("+++DEBUG-completeResults+++ sleeping " + 200);
        }
        return this.result.list(Math.min(query.neededResults(), this.result.sizeAvailable()));
    }

    public long postRanking(
            final ResultEntry rentry,
            final ScoreMap<String> topwords) {

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
        int tc;
        for (int j = 0; j < urlcomps.length; j++) {
            tc = topwords.get(urlcomps[j]);
            if (tc > 0) r += Math.max(1, tc) << query.ranking.coeff_urlcompintoplist;
        }
        for (int j = 0; j < descrcomps.length; j++) {
            tc = topwords.get(descrcomps[j]);
            if (tc > 0) r += Math.max(1, tc) << query.ranking.coeff_descrcompintoplist;
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


    public void deployWorker(int deployCount, final int neededResults) {
        if (rankingProcess.feedingIsFinished() && rankingProcess.sizeQueue() == 0) return;
        if (this.workerThreads == null) {
            this.workerThreads = new Worker[deployCount];
            synchronized(this.workerThreads) {
                for (int i = 0; i < workerThreads.length; i++) {
                    Worker worker = new Worker(i, 10000, query.snippetCacheStrategy, query.snippetMatcher, neededResults);
                    worker.start();
                    this.workerThreads[i] = worker;
                }
            }
        } else {
            // there are still worker threads running, but some may be dead.
            // if we find dead workers, reanimate them
            synchronized(this.workerThreads) {
                for (int i = 0; i < this.workerThreads.length; i++) {
                   if (deployCount <= 0) break;
                   if (this.workerThreads[i] == null || !this.workerThreads[i].isAlive()) {
                       Worker worker = new Worker(i, 10000, query.snippetCacheStrategy, query.snippetMatcher, neededResults);
                       worker.start();
                       this.workerThreads[i] = worker;
                       deployCount--;
                   }
                }
            }
        }
    }
    
   private boolean anyWorkerAlive() {
        if (this.workerThreads == null) return false;
        synchronized(this.workerThreads) {
            for (int i = 0; i < this.workerThreads.length; i++) {
               if ((this.workerThreads[i] != null) &&
                   (this.workerThreads[i].isAlive()) &&
                   (this.workerThreads[i].busytime() < 1000)) return true;
            }
        }
        return false;
    }

    protected class Worker extends Thread {
        
        private final long timeout; // the date until this thread should try to work
        private long lastLifeSign; // when the last time the run()-loop was executed
        private final int id;
        private final CrawlProfile.CacheStrategy cacheStrategy;
        private final int neededResults;
        private final Pattern snippetPattern;
        
        public Worker(final int id, final long maxlifetime, CrawlProfile.CacheStrategy cacheStrategy, Pattern snippetPattern, int neededResults) {
            this.id = id;
            this.cacheStrategy = cacheStrategy;
            this.lastLifeSign = System.currentTimeMillis();
            this.snippetPattern = snippetPattern;
            this.timeout = System.currentTimeMillis() + Math.max(1000, maxlifetime);
            this.neededResults = neededResults;
        }

        @Override
        public void run() {

            // start fetching urls and snippets
            URIMetadataRow page;
            //final int fetchAhead = snippetMode == 0 ? 0 : 10;
            boolean nav_topics = query.navigators.equals("all") || query.navigators.indexOf("topics") >= 0;
            try {
                //System.out.println("DEPLOYED WORKER " + id + " FOR " + this.neededResults + " RESULTS, timeoutd = " + (this.timeout - System.currentTimeMillis()));
                int loops = 0;
                while (System.currentTimeMillis() < this.timeout) {
                    this.lastLifeSign = System.currentTimeMillis();

                    // check if we have enough
                    if (result.sizeAvailable() >= this.neededResults) {
                        //Log.logWarning("ResultFetcher", result.sizeAvailable() + " = result.sizeAvailable() >= this.neededResults = " + this.neededResults);
                        break;
                    }

                    // check if we can succeed if we try to take another url
                    if (rankingProcess.feedingIsFinished() && rankingProcess.sizeQueue() == 0) {
                        Log.logWarning("ResultFetcher", "rankingProcess.feedingIsFinished() && rankingProcess.sizeQueue() == 0");
                        break;
                    }
    
                    // get next entry
                    page = rankingProcess.takeURL(true, this.timeout - System.currentTimeMillis());
                    //if (page == null) page = rankedCache.takeURL(false, this.timeout - System.currentTimeMillis());
                    if (page == null) {
                        //System.out.println("page == null");
                        break; // no more available
                    }
                    if (query.filterfailurls && workTables.failURLsContains(page.hash())) continue;

                    loops++;
                    final ResultEntry resultEntry = fetchSnippet(page, cacheStrategy); // does not fetch snippets if snippetMode == 0
                    if (resultEntry == null) continue; // the entry had some problems, cannot be used
                    String rawLine = resultEntry.textSnippet() == null ? null : resultEntry.textSnippet().getLineRaw();
                    //System.out.println("***SNIPPET*** raw='" + rawLine + "', pattern='" + this.snippetPattern.toString() + "'");
                    if (rawLine != null && !this.snippetPattern.matcher(rawLine).matches()) continue;
                    
                    //if (result.contains(resultEntry)) continue;
                    urlRetrievalAllTime += resultEntry.dbRetrievalTime;
                    snippetComputationAllTime += resultEntry.snippetComputationTime;
                    
                    // place the result to the result vector
                    // apply post-ranking
                    long ranking = Long.valueOf(rankingProcess.getOrder().cardinal(resultEntry.word()));
                    ranking += postRanking(resultEntry, rankingProcess.getTopicNavigator(10));
                    resultEntry.ranking = ranking;
                    result.put(new ReverseElement<ResultEntry>(resultEntry, ranking)); // remove smallest in case of overflow
                    if (nav_topics) rankingProcess.addTopics(resultEntry);
                }
                //System.out.println("FINISHED WORKER " + id + " FOR " + this.neededResults + " RESULTS, loops = " + loops);
            } catch (final Exception e) {
                Log.logException(e);
            }
            Log.logInfo("SEARCH", "resultWorker thread " + id + " terminated");
        }
        
        /**
         * calculate the time since the worker has had the latest activity
         * @return time in milliseconds lasted since latest activity
         */
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
            final TextSnippet snippet = new TextSnippet(
                    null,
                    metadata,
                    snippetFetchWordHashes,
                    null,
                    ((query.constraint != null) && (query.constraint.get(Condenser.flag_cat_indexof))),
                    220,
                    Integer.MAX_VALUE,
                    !query.isLocal());
            return new ResultEntry(page, query.getSegment(), peers, snippet, null, dbRetrievalTime, 0); // result without snippet
        }
        
        // load snippet
        if (query.contentdom == ContentDomain.TEXT) {
            // attach text snippet
            startTime = System.currentTimeMillis();
            final TextSnippet snippet = new TextSnippet(
                    this.loader,
                    metadata,
                    snippetFetchWordHashes,
                    cacheStrategy,
                    ((query.constraint != null) && (query.constraint.get(Condenser.flag_cat_indexof))),
                    180,
                    Integer.MAX_VALUE,
                    !query.isLocal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH", "text snippet load time for " + metadata.url() + ": " + snippetComputationTime + ", " + (!snippet.getErrorCode().fail() ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));
            
            if (!snippet.getErrorCode().fail()) {
                // we loaded the file and found the snippet
                return new ResultEntry(page, query.getSegment(), peers, snippet, null, dbRetrievalTime, snippetComputationTime); // result with snippet attached
            } else if (cacheStrategy.mustBeOffline()) {
                // we did not demand online loading, therefore a failure does not mean that the missing snippet causes a rejection of this result
                // this may happen during a remote search, because snippet loading is omitted to retrieve results faster
                return new ResultEntry(page, query.getSegment(), peers, null, null, dbRetrievalTime, snippetComputationTime); // result without snippet
            } else {
                // problems with snippet fetch
                String reason = "no text snippet; errorCode = " + snippet.getErrorCode();
                if (deleteIfSnippetFail) this.workTables.failURLsRegisterMissingWord(query.getSegment().termIndex(), metadata.url(), query.queryHashes, reason);
                Log.logInfo("SEARCH", "sorted out url " + metadata.url().toNormalform(true, false) + " during search: " + reason);
                return null;
            }
        } else {
            // attach media information
            startTime = System.currentTimeMillis();
            final List<MediaSnippet> mediaSnippets = MediaSnippet.retrieveMediaSnippets(metadata.url(), snippetFetchWordHashes, query.contentdom, cacheStrategy, 6000, !query.isLocal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH", "media snippet load time for " + metadata.url() + ": " + snippetComputationTime);
            
            if (mediaSnippets != null && !mediaSnippets.isEmpty()) {
                // found media snippets, return entry
                return new ResultEntry(page, query.getSegment(), peers, null, mediaSnippets, dbRetrievalTime, snippetComputationTime);
            } else if (cacheStrategy.mustBeOffline()) {
                return new ResultEntry(page, query.getSegment(), peers, null, null, dbRetrievalTime, snippetComputationTime);
            } else {
                // problems with snippet fetch
                String reason = "no media snippet";
                if (deleteIfSnippetFail) this.workTables.failURLsRegisterMissingWord(query.getSegment().termIndex(), metadata.url(), query.queryHashes, reason);
                Log.logInfo("SEARCH", "sorted out url " + metadata.url().toNormalform(true, false) + " during search: " + reason);
                return null;
            }
        }
        // finished, no more actions possible here
    }
}
