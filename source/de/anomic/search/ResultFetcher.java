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
import net.yacy.kelondro.util.MemoryControl;
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
            final RankingProcess rankedCache,
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
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.SNIPPETFETCH_START, ((this.workerThreads == null) ? "no" : this.workerThreads.length) + " online snippet fetch threads started", 0, 0), false);
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
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(this.query.id(true), SearchEvent.Type.ONERESULT, "started, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
        if (this.result.sizeAvailable() > item) {
            // we have the wanted result already in the result array .. return that
            final ResultEntry re = this.result.element(item).getElement();
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(this.query.id(true), SearchEvent.Type.ONERESULT, "prefetched, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
            return re;
        }

        // deploy worker to get more results
        final int neededInclPrefetch = this.query.neededResults() + ((MemoryControl.available() > 100 * 1024 * 1024) ? this.query.itemsPerPage : 0);
        deployWorker(Math.min(20, this.query.itemsPerPage), neededInclPrefetch);

        // finally wait until enough results are there produced from the snippet fetch process
        WeakPriorityBlockingQueue.Element<ResultEntry> entry = null;
        while (System.currentTimeMillis() < finishTime) {
            if (this.result.sizeAvailable() + this.rankingProcess.sizeQueue() <= item  && !anyWorkerAlive() && this.rankingProcess.feedingIsFinished()) break;
            try {entry = this.result.element(item, 50);} catch (final InterruptedException e) {Log.logException(e);}
            if (entry != null) break;
            if (!anyWorkerAlive() && this.rankingProcess.sizeQueue() == 0 && this.rankingProcess.feedingIsFinished()) break;
        }

        // finally, if there is something, return the result
        if (entry == null) {
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(this.query.id(true), SearchEvent.Type.ONERESULT, "not found, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
            return null;
        }
        final ResultEntry re = entry.getElement();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(this.query.id(true), SearchEvent.Type.ONERESULT, "retrieved, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
        return re;
    }

    private int resultCounter = 0;
    public ResultEntry nextResult() {
        final ResultEntry re = oneResult(this.resultCounter, 1000);
        this.resultCounter++;
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
        final ResultEntry result = nextResult();
        int c = 0;
        if (result == null) return c;
        // iterate over all images in the result
        final List<MediaSnippet> imagemedia = result.mediaSnippets();
        if (imagemedia != null) {
            feedloop: for (final MediaSnippet ms: imagemedia) {
                // check cache to see if the mime type of the image url is correct
                final ResponseHeader header = Cache.getResponseHeader(ms.href.hash());
                if (header != null) {
                    // this does not work for all urls since some of them may not be in the cache
                    if (header.mime().startsWith("text") || header.mime().startsWith("application")) continue feedloop;
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
            try {Thread.sleep(20);} catch (final InterruptedException e) {}
            //System.out.println("+++DEBUG-completeResults+++ sleeping " + 200);
        }
        return this.result.list(Math.min(this.query.neededResults(), this.result.sizeAvailable()));
    }

    public long postRanking(
            final ResultEntry rentry,
            final ScoreMap<String> topwords) {

        long r = 0;

        // for media search: prefer pages with many links
        if (this.query.contentdom == ContentDomain.IMAGE) r += rentry.limage() << this.query.ranking.coeff_cathasimage;
        if (this.query.contentdom == ContentDomain.AUDIO) r += rentry.laudio() << this.query.ranking.coeff_cathasaudio;
        if (this.query.contentdom == ContentDomain.VIDEO) r += rentry.lvideo() << this.query.ranking.coeff_cathasvideo;
        if (this.query.contentdom == ContentDomain.APP  ) r += rentry.lapp()   << this.query.ranking.coeff_cathasapp;

        // prefer hit with 'prefer' pattern
        if (this.query.prefer.matcher(rentry.url().toNormalform(true, true)).matches()) r += 256 << this.query.ranking.coeff_prefer;
        if (this.query.prefer.matcher(rentry.title()).matches()) r += 256 << this.query.ranking.coeff_prefer;

        // apply 'common-sense' heuristic using references
        final String urlstring = rentry.url().toNormalform(true, true);
        final String[] urlcomps = MultiProtocolURI.urlComps(urlstring);
        final String[] descrcomps = MultiProtocolURI.splitpattern.split(rentry.title().toLowerCase());
        int tc;
        for (final String urlcomp : urlcomps) {
            tc = topwords.get(urlcomp);
            if (tc > 0) r += Math.max(1, tc) << this.query.ranking.coeff_urlcompintoplist;
        }
        for (final String descrcomp : descrcomps) {
            tc = topwords.get(descrcomp);
            if (tc > 0) r += Math.max(1, tc) << this.query.ranking.coeff_descrcompintoplist;
        }

        // apply query-in-result matching
        final HandleSet urlcomph = Word.words2hashesHandles(urlcomps);
        final HandleSet descrcomph = Word.words2hashesHandles(descrcomps);
        final Iterator<byte[]> shi = this.query.queryHashes.iterator();
        byte[] queryhash;
        while (shi.hasNext()) {
            queryhash = shi.next();
            if (urlcomph.has(queryhash)) r += 256 << this.query.ranking.coeff_appurl;
            if (descrcomph.has(queryhash)) r += 256 << this.query.ranking.coeff_app_dc_title;
        }

        return r;
    }


    public void deployWorker(int deployCount, final int neededResults) {
        if (this.rankingProcess.feedingIsFinished() && this.rankingProcess.sizeQueue() == 0) return;
        if (this.result.sizeAvailable() >= neededResults) return;

        if (this.workerThreads == null) {
            this.workerThreads = new Worker[deployCount];
            synchronized(this.workerThreads) {
                for (int i = 0; i < this.workerThreads.length; i++) {
                    final Worker worker = new Worker(i, 10000, this.query.snippetCacheStrategy, this.query.snippetMatcher, neededResults);
                    worker.start();
                    this.workerThreads[i] = worker;
                    if (this.rankingProcess.feedingIsFinished() && this.rankingProcess.sizeQueue() == 0) break;
                    if (this.result.sizeAvailable() >= neededResults) break;
                }
            }
        } else {
            // there are still worker threads running, but some may be dead.
            // if we find dead workers, reanimate them
            synchronized(this.workerThreads) {
                for (int i = 0; i < this.workerThreads.length; i++) {
                   if (deployCount <= 0) break;
                   if (this.workerThreads[i] == null || !this.workerThreads[i].isAlive()) {
                       final Worker worker = new Worker(i, 10000, this.query.snippetCacheStrategy, this.query.snippetMatcher, neededResults);
                       worker.start();
                       this.workerThreads[i] = worker;
                       deployCount--;
                   }
                   if (this.rankingProcess.feedingIsFinished() && this.rankingProcess.sizeQueue() == 0) break;
                   if (this.result.sizeAvailable() >= neededResults) break;
                }
            }
        }
    }

   private boolean anyWorkerAlive() {
        if (this.workerThreads == null) return false;
        synchronized(this.workerThreads) {
            for (final Worker workerThread : this.workerThreads) {
               if ((workerThread != null) &&
                   (workerThread.isAlive()) &&
                   (workerThread.busytime() < 1000)) return true;
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

        public Worker(final int id, final long maxlifetime, final CrawlProfile.CacheStrategy cacheStrategy, final Pattern snippetPattern, final int neededResults) {
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
            final boolean nav_topics = ResultFetcher.this.query.navigators.equals("all") || ResultFetcher.this.query.navigators.indexOf("topics") >= 0;
            try {
                //System.out.println("DEPLOYED WORKER " + id + " FOR " + this.neededResults + " RESULTS, timeoutd = " + (this.timeout - System.currentTimeMillis()));
                int loops = 0;
                while (System.currentTimeMillis() < this.timeout) {
                    this.lastLifeSign = System.currentTimeMillis();

                    // check if we have enough
                    if (ResultFetcher.this.result.sizeAvailable() >= this.neededResults) {
                        //Log.logWarning("ResultFetcher", ResultFetcher.this.result.sizeAvailable() + " = result.sizeAvailable() >= this.neededResults = " + this.neededResults);
                        break;
                    }

                    // check if we can succeed if we try to take another url
                    if (ResultFetcher.this.rankingProcess.feedingIsFinished() && ResultFetcher.this.rankingProcess.sizeQueue() == 0) {
                        //Log.logWarning("ResultFetcher", "rankingProcess.feedingIsFinished() && rankingProcess.sizeQueue() == 0");
                        break;
                    }

                    // get next entry
                    page = ResultFetcher.this.rankingProcess.takeURL(true, Math.min(100, this.timeout - System.currentTimeMillis()));
                    //if (page == null) page = rankedCache.takeURL(false, this.timeout - System.currentTimeMillis());
                    if (page == null) {
                        //System.out.println("page == null");
                        break; // no more available
                    }
                    if (ResultFetcher.this.query.filterfailurls && ResultFetcher.this.workTables.failURLsContains(page.hash())) continue;

                    loops++;
                    final ResultEntry resultEntry = fetchSnippet(page, this.cacheStrategy); // does not fetch snippets if snippetMode == 0
                    if (resultEntry == null) continue; // the entry had some problems, cannot be used
                    final String rawLine = resultEntry.textSnippet() == null ? null : resultEntry.textSnippet().getLineRaw();
                    //System.out.println("***SNIPPET*** raw='" + rawLine + "', pattern='" + this.snippetPattern.toString() + "'");
                    if (rawLine != null && !this.snippetPattern.matcher(rawLine).matches()) continue;

                    //if (result.contains(resultEntry)) continue;
                    ResultFetcher.this.urlRetrievalAllTime += resultEntry.dbRetrievalTime;
                    ResultFetcher.this.snippetComputationAllTime += resultEntry.snippetComputationTime;

                    // place the result to the result vector
                    // apply post-ranking
                    long ranking = Long.valueOf(ResultFetcher.this.rankingProcess.getOrder().cardinal(resultEntry.word()));
                    ranking += postRanking(resultEntry, ResultFetcher.this.rankingProcess.getTopicNavigator(10));
                    resultEntry.ranking = ranking;
                    ResultFetcher.this.result.put(new ReverseElement<ResultEntry>(resultEntry, ranking)); // remove smallest in case of overflow
                    if (nav_topics) ResultFetcher.this.rankingProcess.addTopics(resultEntry);
                }
                //System.out.println("FINISHED WORKER " + id + " FOR " + this.neededResults + " RESULTS, loops = " + loops);
            } catch (final Exception e) {
                Log.logException(e);
            }
            Log.logInfo("SEARCH", "resultWorker thread " + this.id + " terminated");
        }

        /**
         * calculate the time since the worker has had the latest activity
         * @return time in milliseconds lasted since latest activity
         */
        public long busytime() {
            return System.currentTimeMillis() - this.lastLifeSign;
        }
    }

    protected ResultEntry fetchSnippet(final URIMetadataRow page, final CrawlProfile.CacheStrategy cacheStrategy) {
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
                    metadata,
                    this.snippetFetchWordHashes,
                    cacheStrategy,
                    ((this.query.constraint != null) && (this.query.constraint.get(Condenser.flag_cat_indexof))),
                    180,
                    Integer.MAX_VALUE,
                    !this.query.isLocal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH", "text snippet load time for " + metadata.url() + ": " + snippetComputationTime + ", " + (!snippet.getErrorCode().fail() ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));

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
                if (this.deleteIfSnippetFail) this.workTables.failURLsRegisterMissingWord(this.query.getSegment().termIndex(), metadata.url(), this.query.queryHashes, reason);
                Log.logInfo("SEARCH", "sorted out url " + metadata.url().toNormalform(true, false) + " during search: " + reason);
                return null;
            }
        } else {
            // attach media information
            startTime = System.currentTimeMillis();
            final List<MediaSnippet> mediaSnippets = MediaSnippet.retrieveMediaSnippets(metadata.url(), this.snippetFetchWordHashes, this.query.contentdom, cacheStrategy, 6000, !this.query.isLocal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH", "media snippet load time for " + metadata.url() + ": " + snippetComputationTime);

            if (mediaSnippets != null && !mediaSnippets.isEmpty()) {
                // found media snippets, return entry
                return new ResultEntry(page, this.query.getSegment(), this.peers, null, mediaSnippets, dbRetrievalTime, snippetComputationTime);
            } else if (cacheStrategy.mustBeOffline()) {
                return new ResultEntry(page, this.query.getSegment(), this.peers, null, null, dbRetrievalTime, snippetComputationTime);
            } else {
                // problems with snippet fetch
                final String reason = "no media snippet";
                if (this.deleteIfSnippetFail) this.workTables.failURLsRegisterMissingWord(this.query.getSegment().termIndex(), metadata.url(), this.query.queryHashes, reason);
                Log.logInfo("SEARCH", "sorted out url " + metadata.url().toNormalform(true, false) + " during search: " + reason);
                return null;
            }
        }
        // finished, no more actions possible here
    }
}
