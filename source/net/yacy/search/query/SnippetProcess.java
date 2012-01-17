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
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.services.federated.solr.SolrConnector;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.Element;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.EventTracker;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.SeedDB;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.Switchboard;
import net.yacy.search.snippet.ContentDomain;
import net.yacy.search.snippet.MediaSnippet;
import net.yacy.search.snippet.ResultEntry;
import net.yacy.search.snippet.TextSnippet;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import de.anomic.data.WorkTables;
import de.anomic.http.client.Cache;

public class SnippetProcess {

    // input values
    final RWIProcess  rankingProcess; // ordered search results, grows dynamically as all the query threads enrich this container
    QueryParams     query;
    private final SeedDB      peers;
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
    private final boolean deleteIfSnippetFail, remote;
    private boolean cleanupState;

    public SnippetProcess(
            final LoaderDispatcher loader,
            final RWIProcess rankedCache,
            final QueryParams query,
            final SeedDB peers,
            final WorkTables workTables,
            final int taketimeout,
            final boolean deleteIfSnippetFail,
            final boolean remote) {
    	assert query != null;
        this.loader = loader;
    	this.rankingProcess = rankedCache;
    	this.query = query;
        this.peers = peers;
        this.workTables = workTables;
        this.taketimeout = taketimeout;
        this.deleteIfSnippetFail = deleteIfSnippetFail;
        this.remote = remote;
        this.cleanupState = false;

        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.result = new WeakPriorityBlockingQueue<ResultEntry>(-1); // this is the result, enriched with snippets, ranked and ordered by ranking
        this.images = new WeakPriorityBlockingQueue<MediaSnippet>(-1);

        // snippets do not need to match with the complete query hashes,
        // only with the query minus the stopwords which had not been used for the search
        HandleSet filtered;
        try {
            filtered = HandleSet.joinConstructive(query.queryHashes, Switchboard.stopwordHashes);
        } catch (final RowSpaceExceededException e) {
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
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(query.id(true), SearchEvent.Type.SNIPPETFETCH_START, ((this.workerThreads == null) ? "no" : this.workerThreads.length) + " online snippet fetch threads started", 0, 0), false);
    }

    public void setCleanupState() {
        this.cleanupState = true;
    }

    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }

    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }

    public ResultEntry oneResult(final int item, final long timeout) {
        // check if we already retrieved this item
    	// (happens if a search pages is accessed a second time)
        final long finishTime = System.currentTimeMillis() + timeout;
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEvent.Type.ONERESULT, "started, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
        //Log.logInfo("SnippetProcess", "*start method for item = " + item + "; anyWorkerAlive=" + anyWorkerAlive() + "; this.rankingProcess.isAlive() = " + this.rankingProcess.isAlive() + "; this.rankingProcess.feedingIsFinished() = " + this.rankingProcess.feedingIsFinished() + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.rankingProcess.sizeQueue());

        // we must wait some time until the first result page is full to get enough elements for ranking
        final long waittimeout = System.currentTimeMillis() + 300;
        if (this.remote && item < 10 && !this.rankingProcess.feedingIsFinished()) {
            // the first 10 results have a very special timing to get most of the remote results ordered
            // before they are presented on the first lines .. yes sleeps seem to be bad. but how shall we predict how long other
            // peers will take until they respond?
            long sleep = item == 0 ? 600 : (10 - item) * 12; // the first result takes the longest time
            //Log.logInfo("SnippetProcess", "SLEEP = " + sleep);
            try { Thread.sleep(sleep); } catch (final InterruptedException e1) { Log.logException(e1); }
        }
        int thisRankingQueueSize, lastRankingQueueSize = 0;
        if (item < 10) {
            while (
              ((thisRankingQueueSize = this.rankingProcess.sizeQueue()) > 0 || !this.rankingProcess.feedingIsFinished()) &&
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
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEvent.Type.ONERESULT, "prefetched, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
            return re;
        }

        // finally wait until enough results are there produced from the snippet fetch process
        WeakPriorityBlockingQueue.Element<ResultEntry> entry = null;
        while (System.currentTimeMillis() < finishTime) {

            //Log.logInfo("SnippetProcess", "item = " + item + "; anyWorkerAlive=" + anyWorkerAlive() + "; this.rankingProcess.isAlive() = " + this.rankingProcess.isAlive() + "; this.rankingProcess.feedingIsFinished() = " + this.rankingProcess.feedingIsFinished() + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.rankingProcess.sizeQueue());

            if (!anyWorkerAlive() && !this.rankingProcess.isAlive() && this.result.sizeAvailable() + this.rankingProcess.sizeQueue() <= item && this.rankingProcess.feedingIsFinished()) {
                //Log.logInfo("SnippetProcess", "interrupted result fetching; item = " + item + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.rankingProcess.sizeQueue() + "; this.rankingProcess.feedingIsFinished() = " + this.rankingProcess.feedingIsFinished());
                break; // the fail case
            }

            // deploy worker to get more results
            if (!anyWorkerAlive()) {
                final int neededInclPrefetch = this.query.neededResults() + ((MemoryControl.available() > 100 * 1024 * 1024) ? this.query.itemsPerPage : 0);
                deployWorker(Math.min(20, this.query.itemsPerPage), neededInclPrefetch);
            }

            try {entry = this.result.element(item, 50);} catch (final InterruptedException e) {break;}
            if (entry != null) {
                break;
            }
        }

        // finally, if there is something, return the result
        if (entry == null) {
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEvent.Type.ONERESULT, "not found, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
            //Log.logInfo("SnippetProcess", "NO ENTRY computed; anyWorkerAlive=" + anyWorkerAlive() + "; this.rankingProcess.isAlive() = " + this.rankingProcess.isAlive() + "; this.rankingProcess.feedingIsFinished() = " + this.rankingProcess.feedingIsFinished() + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.rankingProcess.sizeQueue());
            return null;
        }
        final ResultEntry re = entry.getElement();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEvent.Type.ONERESULT, "retrieved, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
        if (item == this.query.offset + this.query.itemsPerPage - 1)
         {
            stopAllWorker(); // we don't need more
        }
        return re;
    }

    private int resultCounter = 0;
    public ResultEntry nextResult() {
        final ResultEntry re = oneResult(this.resultCounter, 3000);
        this.resultCounter++;
        return re;
    }

    public MediaSnippet oneImage(final int item) {
        // always look for a next object if there are way too less
        if (this.images.sizeAvailable() <= item + 10) {
            fillImagesCache();
        }

        // check if we already retrieved the item
        if (this.images.sizeDrained() > item) {
            return this.images.element(item).getElement();
        }

        // look again if there are not enough for presentation
        while (this.images.sizeAvailable() <= item) {
            if (fillImagesCache() == 0) {
                break;
            }
        }
        if (this.images.sizeAvailable() <= item) {
            return null;
        }

        // now take the specific item from the image stack
        return this.images.element(item).getElement();
    }

    private int fillImagesCache() {
        final ResultEntry result = nextResult();
        int c = 0;
        if (result == null) {
            return c;
        }
        // iterate over all images in the result
        final List<MediaSnippet> imagemedia = result.mediaSnippets();
        if (imagemedia != null) {
        	ResponseHeader header;
            feedloop: for (final MediaSnippet ms: imagemedia) {
                // check cache to see if the mime type of the image url is correct
                header = Cache.getResponseHeader(ms.href.hash());
                if (header != null) {
                    // this does not work for all urls since some of them may not be in the cache
                    if (header.mime().startsWith("text") || header.mime().startsWith("application")) {
                        continue feedloop;
                    }
                }
                this.images.put(new ReverseElement<MediaSnippet>(ms, ms.ranking)); // remove smallest in case of overflow
                c++;
                //System.out.println("*** image " + UTF8.String(ms.href.hash()) + " images.size = " + images.size() + "/" + images.size());
            }
        }
        return c;
    }

    public ArrayList<WeakPriorityBlockingQueue.Element<ResultEntry>> completeResults(final long waitingtime) {
        final long timeout = System.currentTimeMillis() + waitingtime;
        while ( this.result.sizeAvailable() < this.query.neededResults() &&
                anyWorkerAlive() &&
                System.currentTimeMillis() < timeout) {
            try {Thread.sleep(10);} catch (final InterruptedException e) {}
            //System.out.println("+++DEBUG-completeResults+++ sleeping " + 200);
        }
        return this.result.list(Math.min(this.query.neededResults(), this.result.sizeAvailable()));
    }

    public long postRanking(
            final ResultEntry rentry,
            final ScoreMap<String> topwords) {

        long r = 0;

        // for media search: prefer pages with many links
        if (this.query.contentdom == ContentDomain.IMAGE) {
            r += rentry.limage() << this.query.ranking.coeff_cathasimage;
        }
        if (this.query.contentdom == ContentDomain.AUDIO) {
            r += rentry.laudio() << this.query.ranking.coeff_cathasaudio;
        }
        if (this.query.contentdom == ContentDomain.VIDEO) {
            r += rentry.lvideo() << this.query.ranking.coeff_cathasvideo;
        }
        if (this.query.contentdom == ContentDomain.APP  ) {
            r += rentry.lapp()   << this.query.ranking.coeff_cathasapp;
        }

        // prefer hit with 'prefer' pattern
        if (this.query.prefer.matcher(rentry.url().toNormalform(true, true)).matches()) {
            r += 256 << this.query.ranking.coeff_prefer;
        }
        if (this.query.prefer.matcher(rentry.title()).matches()) {
            r += 256 << this.query.ranking.coeff_prefer;
        }

        // apply 'common-sense' heuristic using references
        final String urlstring = rentry.url().toNormalform(true, true);
        final String[] urlcomps = MultiProtocolURI.urlComps(urlstring);
        final String[] descrcomps = MultiProtocolURI.splitpattern.split(rentry.title().toLowerCase());
        int tc;
        for (final String urlcomp : urlcomps) {
            tc = topwords.get(urlcomp);
            if (tc > 0) {
                r += Math.max(1, tc) << this.query.ranking.coeff_urlcompintoplist;
            }
        }
        for (final String descrcomp : descrcomps) {
            tc = topwords.get(descrcomp);
            if (tc > 0) {
                r += Math.max(1, tc) << this.query.ranking.coeff_descrcompintoplist;
            }
        }

        // apply query-in-result matching
        final HandleSet urlcomph = Word.words2hashesHandles(urlcomps);
        final HandleSet descrcomph = Word.words2hashesHandles(descrcomps);
        final Iterator<byte[]> shi = this.query.queryHashes.iterator();
        byte[] queryhash;
        while (shi.hasNext()) {
            queryhash = shi.next();
            if (urlcomph.has(queryhash)) {
                r += 256 << this.query.ranking.coeff_appurl;
            }
            if (descrcomph.has(queryhash)) {
                r += 256 << this.query.ranking.coeff_app_dc_title;
            }
        }

        return r;
    }


    public void deployWorker(int deployCount, final int neededResults) {
        if (this.cleanupState ||
            (this.rankingProcess.feedingIsFinished() && this.rankingProcess.sizeQueue() == 0) ||
            this.result.sizeAvailable() >= neededResults) {
            return;
        }
        Worker worker;
        if (this.workerThreads == null) {
            this.workerThreads = new Worker[deployCount];
            synchronized(this.workerThreads) {
                for (int i = 0; i < this.workerThreads.length; i++) {
                    if (this.result.sizeAvailable() >= neededResults ||
                        (this.rankingProcess.feedingIsFinished() && this.rankingProcess.sizeQueue() == 0)) {
                        break;
                    }
                    worker = new Worker(i, 10000, this.query.snippetCacheStrategy, this.query.snippetMatcher, neededResults);
                    worker.start();
                    this.workerThreads[i] = worker;
                    if (this.rankingProcess.expectMoreRemoteReferences()) {
                        long wait = this.rankingProcess.waitTimeRecommendation();
                        if (wait > 0) {
                            try {Thread.sleep(wait);} catch ( InterruptedException e ) {}
                        }
                    }
                }
            }
        } else {
            // there are still worker threads running, but some may be dead.
            // if we find dead workers, reanimate them
            synchronized(this.workerThreads) {
                for (int i = 0; i < this.workerThreads.length; i++) {
                    if (deployCount <= 0 ||
                        this.result.sizeAvailable() >= neededResults ||
                        (this.rankingProcess.feedingIsFinished() && this.rankingProcess.sizeQueue() == 0)) {
                        break;
                    }
                    if (this.workerThreads[i] == null || !this.workerThreads[i].isAlive()) {
                        worker = new Worker(i, 10000, this.query.snippetCacheStrategy, this.query.snippetMatcher, neededResults);
                        worker.start();
                        this.workerThreads[i] = worker;
                        deployCount--;
                    }
                    if (this.rankingProcess.expectMoreRemoteReferences()) {
                        long wait = this.rankingProcess.waitTimeRecommendation();
                        if (wait > 0) {
                            try {Thread.sleep(wait);} catch ( InterruptedException e ) {}
                        }
                    }
                }
            }
        }
    }

    public void stopAllWorker() {
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
            for (final Worker workerThread : this.workerThreads) {
               if ((workerThread != null) &&
                   (workerThread.isAlive()) &&
                   (workerThread.busytime() < 10000)) {
                return true;
            }
            }
        }
        return false;
    }

    protected class Worker extends Thread {

        private final long timeout; // the date until this thread should try to work
        private long lastLifeSign; // when the last time the run()-loop was executed
        private final CacheStrategy cacheStrategy;
        private final int neededResults;
        private final Pattern snippetPattern;
        private boolean shallrun;
        private final SolrConnector solr;

        public Worker(final int id, final long maxlifetime, final CacheStrategy cacheStrategy, final Pattern snippetPattern, final int neededResults) {
            this.cacheStrategy = cacheStrategy;
            this.lastLifeSign = System.currentTimeMillis();
            this.snippetPattern = snippetPattern;
            this.timeout = System.currentTimeMillis() + Math.max(1000, maxlifetime);
            this.neededResults = neededResults;
            this.shallrun = true;
            this.solr = SnippetProcess.this.rankingProcess.getQuery().getSegment().getSolr();
        }

        @Override
        public void run() {

            // start fetching urls and snippets
            URIMetadataRow page;
            ResultEntry resultEntry;
            //final int fetchAhead = snippetMode == 0 ? 0 : 10;
            final boolean nav_topics = SnippetProcess.this.query.navigators.equals("all") || SnippetProcess.this.query.navigators.indexOf("topics",0) >= 0;
            try {
                //System.out.println("DEPLOYED WORKER " + id + " FOR " + this.neededResults + " RESULTS, timeoutd = " + (this.timeout - System.currentTimeMillis()));
                int loops = 0;
                while (this.shallrun && System.currentTimeMillis() < this.timeout) {
                    //Log.logInfo("SnippetProcess", "***** timeleft = " + (this.timeout - System.currentTimeMillis()));
                    this.lastLifeSign = System.currentTimeMillis();

                    if (MemoryControl.shortStatus()) {
                    	break;
                    }

                    // check if we have enough; we stop only if we can fetch online; otherwise its better to run this to get better navigation
                    if (this.cacheStrategy.isAllowedToFetchOnline() && SnippetProcess.this.result.sizeAvailable() >= this.neededResults) {
                        //Log.logWarning("ResultFetcher", SnippetProcess.this.result.sizeAvailable() + " = result.sizeAvailable() >= this.neededResults = " + this.neededResults);
                        break;
                    }

                    // check if we can succeed if we try to take another url
                    if (SnippetProcess.this.rankingProcess.feedingIsFinished() && SnippetProcess.this.rankingProcess.sizeQueue() == 0) {
                        //Log.logWarning("ResultFetcher", "rankingProcess.feedingIsFinished() && rankingProcess.sizeQueue() == 0");
                        break;
                    }

                    // get next entry
                    page = SnippetProcess.this.rankingProcess.takeURL(true, Math.min(500, Math.max(20, this.timeout - System.currentTimeMillis())));
                    //if (page != null) Log.logInfo("ResultFetcher", "got one page: " + page.metadata().url().toNormalform(true, false));
                    //if (page == null) page = rankedCache.takeURL(false, this.timeout - System.currentTimeMillis());
                    if (page == null) {
                        //Log.logWarning("ResultFetcher", "page == null");
                        break; // no more available
                    }

                    this.setName(page.url().toNormalform(true, false)); // to support debugging
                    if (SnippetProcess.this.query.filterfailurls && SnippetProcess.this.workTables.failURLsContains(page.hash())) {
                        continue;
                    }

                    // in case that we have an attached solr, we load also the solr document
                    String solrContent = null;
                    if (this.solr != null) {
                        SolrDocument sd = null;
                        final SolrDocumentList sdl = this.solr.get("id:" + ASCII.String(page.hash()), 0, 1);
                        if (sdl.size() > 0) {
                            sd = sdl.get(0);
                        }
                        if (sd != null) {
                            solrContent = this.solr.getScheme().solrGetText(sd);
                        }
                    }

                    loops++;
                    resultEntry = fetchSnippet(page, solrContent, this.cacheStrategy); // does not fetch snippets if snippetMode == 0
                    if (resultEntry == null)
                     {
                        continue; // the entry had some problems, cannot be used
                        //final String rawLine = resultEntry.textSnippet() == null ? null : resultEntry.textSnippet().getLineRaw();
                        //System.out.println("***SNIPPET*** raw='" + rawLine + "', pattern='" + this.snippetPattern.toString() + "'");
                        //if (rawLine != null && !this.snippetPattern.matcher(rawLine).matches()) continue;
                    }

                    //if (result.contains(resultEntry)) continue;
                    SnippetProcess.this.urlRetrievalAllTime += resultEntry.dbRetrievalTime;
                    SnippetProcess.this.snippetComputationAllTime += resultEntry.snippetComputationTime;

                    // place the result to the result vector
                    // apply post-ranking
                    long ranking = Long.valueOf(SnippetProcess.this.rankingProcess.getOrder().cardinal(resultEntry.word()));
                    ranking += postRanking(resultEntry, SnippetProcess.this.rankingProcess.getTopicNavigator(10));
                    resultEntry.ranking = ranking;
                    SnippetProcess.this.result.put(new ReverseElement<ResultEntry>(resultEntry, ranking)); // remove smallest in case of overflow
                    if (nav_topics) {
                        SnippetProcess.this.rankingProcess.addTopics(resultEntry);
                    }
                }
                //System.out.println("FINISHED WORKER " + id + " FOR " + this.neededResults + " RESULTS, loops = " + loops);
            } catch (final Exception e) {
                Log.logException(e);
            }
            //Log.logInfo("SEARCH", "resultWorker thread " + this.id + " terminated");
        }

        public void pleaseStop() {
            this.shallrun = false;
        }

        /**
         * calculate the time since the worker has had the latest activity
         * @return time in milliseconds lasted since latest activity
         */
        public long busytime() {
            return System.currentTimeMillis() - this.lastLifeSign;
        }
    }

    protected ResultEntry fetchSnippet(final URIMetadataRow page, final String solrText, final CacheStrategy cacheStrategy) {
        // Snippet Fetching can has 3 modes:
        // 0 - do not fetch snippets
        // 1 - fetch snippets offline only
        // 2 - online snippet fetch

        // load only urls if there was not yet a root url of that hash
        // find the url entry

        long startTime = System.currentTimeMillis();
        if (page == null) {
            return null;
        }
        final long dbRetrievalTime = System.currentTimeMillis() - startTime;

        if (cacheStrategy == null) {
            final TextSnippet snippet = new TextSnippet(
                    null,
                    solrText,
                    page,
                    this.snippetFetchWordHashes,
                    null,
                    ((this.query.constraint != null) && (this.query.constraint.get(Condenser.flag_cat_indexof))),
                    220,
                    Integer.MAX_VALUE,
                    !this.query.isLocal());
            return new ResultEntry(page, this.query.getSegment(), this.peers, snippet, null, dbRetrievalTime, 0); // result without snippet
        }

        // load snippet
        if (this.query.contentdom == ContentDomain.TEXT) {
            // attach text snippet
            startTime = System.currentTimeMillis();
            final TextSnippet snippet = new TextSnippet(
                    this.loader,
                    solrText,
                    page,
                    this.snippetFetchWordHashes,
                    cacheStrategy,
                    ((this.query.constraint != null) && (this.query.constraint.get(Condenser.flag_cat_indexof))),
                    180,
                    Integer.MAX_VALUE,
                    !this.query.isLocal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH", "text snippet load time for " + page.url() + ": " + snippetComputationTime + ", " + (!snippet.getErrorCode().fail() ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));

            if (!snippet.getErrorCode().fail()) {
                // we loaded the file and found the snippet
                return new ResultEntry(page, this.query.getSegment(), this.peers, snippet, null, dbRetrievalTime, snippetComputationTime); // result with snippet attached
            } else if (cacheStrategy.mustBeOffline()) {
                // we did not demand online loading, therefore a failure does not mean that the missing snippet causes a rejection of this result
                // this may happen during a remote search, because snippet loading is omitted to retrieve results faster
                return new ResultEntry(page, this.query.getSegment(), this.peers, null, null, dbRetrievalTime, snippetComputationTime); // result without snippet
            } else {
                // problems with snippet fetch
                final String reason = "no text snippet; errorCode = " + snippet.getErrorCode();
                if (this.deleteIfSnippetFail) {
                    this.workTables.failURLsRegisterMissingWord(this.query.getSegment().termIndex(), page.url(), this.query.queryHashes, reason);
                }
                Log.logInfo("SEARCH", "sorted out url " + page.url().toNormalform(true, false) + " during search: " + reason);
                return null;
            }
        } else {
            // attach media information
            startTime = System.currentTimeMillis();
            final List<MediaSnippet> mediaSnippets = MediaSnippet.retrieveMediaSnippets(page.url(), this.snippetFetchWordHashes, this.query.contentdom, cacheStrategy, 6000, !this.query.isLocal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH", "media snippet load time for " + page.url() + ": " + snippetComputationTime);

            if (mediaSnippets != null && !mediaSnippets.isEmpty()) {
                // found media snippets, return entry
                return new ResultEntry(page, this.query.getSegment(), this.peers, null, mediaSnippets, dbRetrievalTime, snippetComputationTime);
            } else if (cacheStrategy.mustBeOffline()) {
                return new ResultEntry(page, this.query.getSegment(), this.peers, null, null, dbRetrievalTime, snippetComputationTime);
            } else {
                // problems with snippet fetch
                final String reason = "no media snippet";
                if (this.deleteIfSnippetFail) {
                    this.workTables.failURLsRegisterMissingWord(this.query.getSegment().termIndex(), page.url(), this.query.queryHashes, reason);
                }
                Log.logInfo("SEARCH", "sorted out url " + page.url().toNormalform(true, false) + " during search: " + reason);
                return null;
            }
        }
        // finished, no more actions possible here
    }

    /**
     * delete a specific entry from the search results
     * this is used if the user clicks on a '-' sign beside the search result
     * @param urlhash
     * @return true if an entry was deleted, false otherwise
     */
    public boolean delete(final String urlhash) {
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
