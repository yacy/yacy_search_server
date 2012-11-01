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

import net.yacy.cora.document.ASCII;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.Element;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.Cache;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.SeedDB;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.snippet.MediaSnippet;
import net.yacy.search.snippet.ResultEntry;

public class SnippetProcess {

	public static Log log = new Log("SEARCH");

	public static final int SNIPPET_MAX_LENGTH = 220;
    private final static int SNIPPET_WORKER_THREADS = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    // input values
    final SearchEvent searchEvent;
    QueryParams       query;
    final SeedDB      peers;
    final WorkTables  workTables;

    // result values
    private   final WeakPriorityBlockingQueue<MediaSnippet> images; // container to sort images by size
    protected final WeakPriorityBlockingQueue<ResultEntry>  result;
    protected final LoaderDispatcher                        loader;
    protected final HandleSet                               snippetFetchWordHashes; // a set of word hashes that are used to match with the snippets
    protected final boolean                                 deleteIfSnippetFail;
    protected SnippetWorker[]                               workerThreads;
    protected long                                          urlRetrievalAllTime;
    protected long                                          snippetComputationAllTime;

    private final boolean remote;
    private boolean cleanupState;

    protected SnippetProcess(
            final SearchEvent searchEvent,
            final LoaderDispatcher loader,
            final QueryParams query,
            final SeedDB peers,
            final WorkTables workTables,
            final boolean deleteIfSnippetFail,
            final boolean remote) {
    	assert query != null;
    	this.searchEvent = searchEvent;
        this.loader = loader;
    	this.query = query;
        this.peers = peers;
        this.workTables = workTables;
        this.deleteIfSnippetFail = deleteIfSnippetFail;
        this.remote = remote;
        this.cleanupState = false;
        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.result = new WeakPriorityBlockingQueue<ResultEntry>(Math.max(1000, 10 * query.itemsPerPage()), true); // this is the result, enriched with snippets, ranked and ordered by ranking
        this.images = new WeakPriorityBlockingQueue<MediaSnippet>(Math.max(1000, 10 * query.itemsPerPage()), true);

        // snippets do not need to match with the complete query hashes,
        // only with the query minus the stopwords which had not been used for the search
        HandleSet filtered;
        try {
            filtered = RowHandleSet.joinConstructive(query.query_include_hashes, Switchboard.stopwordHashes);
        } catch (final SpaceExceededException e) {
            Log.logException(e);
            filtered = new RowHandleSet(query.query_include_hashes.keylen(), query.query_include_hashes.comparator(), 0);
        }
        this.snippetFetchWordHashes = query.query_include_hashes.clone();
        if (filtered != null && !filtered.isEmpty()) {
            this.snippetFetchWordHashes.excludeDestructive(Switchboard.stopwordHashes);
        }

        // start worker threads to fetch urls and snippets
        this.workerThreads = null;
        deployWorker(Math.min(SNIPPET_WORKER_THREADS, query.itemsPerPage), query.neededResults());
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(query.id(true), SearchEventType.SNIPPETFETCH_START, ((this.workerThreads == null) ? "no" : this.workerThreads.length) + " online snippet fetch threads started", 0, 0), false);
    }

    protected void setCleanupState() {
        this.cleanupState = true;
    }

    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }

    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }

    protected ResultEntry oneResult(final int item, final long timeout) {
        // check if we already retrieved this item
    	// (happens if a search pages is accessed a second time)
        final long finishTime = System.currentTimeMillis() + timeout;
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "started, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
        //Log.logInfo("SnippetProcess", "*start method for item = " + item + "; anyWorkerAlive=" + anyWorkerAlive() + "; this.rankingProcess.isAlive() = " + this.rankingProcess.isAlive() + "; this.rankingProcess.feedingIsFinished() = " + this.rankingProcess.feedingIsFinished() + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.rankingProcess.sizeQueue());

        // we must wait some time until the first result page is full to get enough elements for ranking
        final long waittimeout = System.currentTimeMillis() + 300;
        if (this.remote && item < 10 && !this.searchEvent.rankingProcess.feedingIsFinished()) {
            // the first 10 results have a very special timing to get most of the remote results ordered
            // before they are presented on the first lines .. yes sleeps seem to be bad. but how shall we predict how long other
            // peers will take until they respond?
            long sleep = item == 0 ? 400 : (10 - item) * 9; // the first result takes the longest time
            //Log.logInfo("SnippetProcess", "SLEEP = " + sleep);
            try { Thread.sleep(sleep); } catch (final InterruptedException e1) { Log.logException(e1); }
        }
        int thisRankingQueueSize, lastRankingQueueSize = 0;
        if (item < 10) {
            while (
              ((thisRankingQueueSize = this.searchEvent.rankingProcess.rwiQueueSize()) > 0 || !this.searchEvent.rankingProcess.feedingIsFinished()) &&
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

            Log.logInfo("SnippetProcess", "item = " + item + "; anyWorkerAlive=" + anyWorkerAlive() + "; this.rankingProcess.isAlive() = " + this.searchEvent.rankingProcess.isAlive() + "; this.rankingProcess.feedingIsFinished() = " + this.searchEvent.rankingProcess.feedingIsFinished() + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.searchEvent.rankingProcess.rwiQueueSize() + ", this.rankingProcess.nodeStack.sizeAvailable() = " + this.searchEvent.nodeStack.sizeAvailable());

            if (!anyWorkerAlive() && !this.searchEvent.rankingProcess.isAlive() && this.result.sizeAvailable() + this.searchEvent.rankingProcess.rwiQueueSize() + this.searchEvent.nodeStack.sizeAvailable() <= item && this.searchEvent.rankingProcess.feedingIsFinished()) {
                //Log.logInfo("SnippetProcess", "interrupted result fetching; item = " + item + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.rankingProcess.sizeQueue() + "; this.rankingProcess.feedingIsFinished() = " + this.rankingProcess.feedingIsFinished());
                break; // the fail case
            }

            // deploy worker to get more results
            if (!anyWorkerAlive()) {
                final int neededInclPrefetch = this.query.neededResults() + ((MemoryControl.available() > 100 * 1024 * 1024 && SNIPPET_WORKER_THREADS >= 8) ? this.query.itemsPerPage : 0);
                deployWorker(Math.min(SNIPPET_WORKER_THREADS, this.query.itemsPerPage), neededInclPrefetch);
            }

            try {entry = this.result.element(item, 50);} catch (final InterruptedException e) {break;}
            if (entry != null) { break; }
        }

        // finally, if there is something, return the result
        if (entry == null) {
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "not found, item = " + item + ", available = " + this.result.sizeAvailable(), 0, 0), false);
            //Log.logInfo("SnippetProcess", "NO ENTRY computed (possible timeout); anyWorkerAlive=" + anyWorkerAlive() + "; this.rankingProcess.isAlive() = " + this.rankingProcess.isAlive() + "; this.rankingProcess.feedingIsFinished() = " + this.rankingProcess.feedingIsFinished() + "; this.result.sizeAvailable() = " + this.result.sizeAvailable() + ", this.rankingProcess.sizeQueue() = " + this.rankingProcess.sizeQueue());
            return null;
        }
        final ResultEntry re = entry.getElement();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(this.query.id(true), SearchEventType.ONERESULT, "retrieved, item = " + item + ", available = " + this.result.sizeAvailable() + ": " + re.urlstring(), 0, 0), false);
        if (item == this.query.offset + this.query.itemsPerPage - 1) {
            stopAllWorker(); // we don't need more
        }
        return re;
    }

    private int resultCounter = 0;
    private ResultEntry nextResult() {
        final ResultEntry re = oneResult(this.resultCounter, Math.max(3000, this.query.timeout - System.currentTimeMillis()));
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


    private void deployWorker(int deployCount, final int neededResults) {
        if (this.cleanupState ||
            (this.searchEvent.rankingProcess.feedingIsFinished() && this.searchEvent.rankingProcess.rwiQueueSize() == 0 && this.searchEvent.nodeStack.sizeAvailable() == 0) ||
            this.result.sizeAvailable() >= neededResults) {
            return;
        }
        SnippetWorker worker;
        if (this.workerThreads == null) {
            this.workerThreads = new SnippetWorker[deployCount];
            synchronized(this.workerThreads) {try {
                for (int i = 0; i < this.workerThreads.length; i++) {
                    if (this.result.sizeAvailable() >= neededResults ||
                        (this.searchEvent.rankingProcess.feedingIsFinished() && this.searchEvent.rankingProcess.rwiQueueSize() == 0) && this.searchEvent.nodeStack.sizeAvailable() == 0) {
                        break;
                    }
                    worker = new SnippetWorker(this, this.query.maxtime, this.query.snippetCacheStrategy, neededResults);
                    worker.start();
                    this.workerThreads[i] = worker;
                    if (this.searchEvent.expectMoreRemoteReferences()) {
                        long wait = this.searchEvent.waitTimeRecommendation();
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
                        (this.searchEvent.rankingProcess.feedingIsFinished() && this.searchEvent.rankingProcess.rwiQueueSize() == 0) && this.searchEvent.nodeStack.sizeAvailable() == 0) {
                        break;
                    }
                    if (this.workerThreads[i] == null || !this.workerThreads[i].isAlive()) {
                        worker = new SnippetWorker(this, this.query.maxtime, this.query.snippetCacheStrategy, neededResults);
                        worker.start();
                        this.workerThreads[i] = worker;
                        deployCount--;
                    }
                    if (this.searchEvent.expectMoreRemoteReferences()) {
                        long wait = this.searchEvent.waitTimeRecommendation();
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
               if ((workerThread != null) &&
                   (workerThread.isAlive()) &&
                   (workerThread.busytime() < 10000)) {
                return true;
            }
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
