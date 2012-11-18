/**
 *  SnippetWorker
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 01.11.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.search.query;

import java.util.Iterator;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.Classification;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.cora.storage.HandleSet;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.index.Segment;
import net.yacy.search.snippet.ResultEntry;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.search.snippet.TextSnippet.ResultClass;

public class SnippetWorker extends Thread {
    private final SearchEvent snippetProcess;
    private final long timeout; // the date until this thread should try to work
    private long lastLifeSign; // when the last time the run()-loop was executed
    private final CacheStrategy cacheStrategy;
    private final int neededResults;
    private boolean shallrun;

    protected SnippetWorker(final SearchEvent snippetProcess, final long maxlifetime, final CacheStrategy cacheStrategy, final int neededResults) {
        this.snippetProcess = snippetProcess;
        this.cacheStrategy = cacheStrategy;
        this.lastLifeSign = System.currentTimeMillis();
        this.timeout = System.currentTimeMillis() + Math.max(1000, maxlifetime);
        this.neededResults = neededResults;
        this.shallrun = true;
    }

    @Override
    public void run() {

        // start fetching urls and snippets
        URIMetadataNode page;
        ResultEntry resultEntry;
        try {
            while (this.shallrun && System.currentTimeMillis() < this.timeout) {
                this.lastLifeSign = System.currentTimeMillis();

                if (MemoryControl.shortStatus()) {
                    Log.logWarning("SnippetProcess", "shortStatus");
                    break;
                }

                // check if we have enough; we stop only if we can fetch online; otherwise its better to run this to get better navigation
                if ((this.cacheStrategy == null || this.cacheStrategy.isAllowedToFetchOnline()) && this.snippetProcess.result.sizeAvailable() >= this.neededResults) {
                    Log.logWarning("SnippetProcess", this.snippetProcess.result.sizeAvailable() + " = result.sizeAvailable() >= this.neededResults = " + this.neededResults);
                    break;
                }

                // check if we can succeed if we try to take another url
                if (this.snippetProcess.rankingProcess.feedingIsFinished() && this.snippetProcess.rankingProcess.rwiQueueSize() == 0 && this.snippetProcess.nodeStack.sizeAvailable() == 0) {
                    Log.logWarning("SnippetProcess", "rankingProcess.feedingIsFinished() && rankingProcess.sizeQueue() == 0");
                    break;
                }

                // get next entry
                page = this.snippetProcess.takeURL(true, Math.min(500, Math.max(20, this.timeout - System.currentTimeMillis())));
                //if (page != null) Log.logInfo("SnippetProcess", "got one page: " + page.metadata().url().toNormalform(true, false));
                //if (page == null) page = rankedCache.takeURL(false, this.timeout - System.currentTimeMillis());
                if (page == null) {
                    //Log.logWarning("SnippetProcess", "page == null");
                    break; // no more available
                }

                this.setName(page.url().toNormalform(true)); // to support debugging
                if (this.snippetProcess.query.filterfailurls && this.snippetProcess.workTables.failURLsContains(page.hash())) {
                    continue;
                }

                resultEntry = fetchSnippet(page, this.cacheStrategy); // does not fetch snippets if snippetMode == 0
                if (resultEntry == null) {
                    continue; // the entry had some problems, cannot be used
                }

                //if (result.contains(resultEntry)) continue;
                this.snippetProcess.snippetComputationAllTime += resultEntry.snippetComputationTime;

                // place the result to the result vector
                // apply post-ranking
                long ranking = resultEntry.word() == null ? 0 : Long.valueOf(this.snippetProcess.rankingProcess.order.cardinal(resultEntry.word()));
                ranking += postRanking(resultEntry, new ConcurrentScoreMap<String>() /*this.snippetProcess.rankingProcess.getTopicNavigator(10)*/);
                resultEntry.ranking = ranking;
                this.snippetProcess.result.put(new ReverseElement<ResultEntry>(resultEntry, ranking)); // remove smallest in case of overflow
                this.snippetProcess.rankingProcess.addTopics(resultEntry);
            }
            if (System.currentTimeMillis() >= this.timeout) {
                Log.logWarning("SnippetProcess", "worker ended with timeout");
            }
            //System.out.println("FINISHED WORKER " + id + " FOR " + this.neededResults + " RESULTS, loops = " + loops);
        } catch (final Exception e) { Log.logException(e); }
        //Log.logInfo("SEARCH", "resultWorker thread " + this.id + " terminated");
    }

    protected void pleaseStop() {
        this.shallrun = false;
    }

    /**
     * calculate the time since the worker has had the latest activity
     * @return time in milliseconds lasted since latest activity
     */
    protected long busytime() {
        return System.currentTimeMillis() - this.lastLifeSign;
    }

    private long postRanking(
            final ResultEntry rentry,
            final ScoreMap<String> topwords) {

        long r = 0;

        // for media search: prefer pages with many links
        r += rentry.limage() << this.snippetProcess.query.ranking.coeff_cathasimage;
        r += rentry.laudio() << this.snippetProcess.query.ranking.coeff_cathasaudio;
        r += rentry.lvideo() << this.snippetProcess.query.ranking.coeff_cathasvideo;
        r += rentry.lapp()   << this.snippetProcess.query.ranking.coeff_cathasapp;

        // apply citation count
        //System.out.println("POSTRANKING CITATION: references = " + rentry.referencesCount() + ", inbound = " + rentry.llocal() + ", outbound = " + rentry.lother());
        r += (128 * rentry.referencesCount() / (1 + 2 * rentry.llocal() + rentry.lother())) << this.snippetProcess.query.ranking.coeff_citation;

        // prefer hit with 'prefer' pattern
        if (this.snippetProcess.query.prefer.matcher(rentry.url().toNormalform(true)).matches()) {
            r += 256 << this.snippetProcess.query.ranking.coeff_prefer;
        }
        if (this.snippetProcess.query.prefer.matcher(rentry.title()).matches()) {
            r += 256 << this.snippetProcess.query.ranking.coeff_prefer;
        }

        // apply 'common-sense' heuristic using references
        final String urlstring = rentry.url().toNormalform(true);
        final String[] urlcomps = MultiProtocolURI.urlComps(urlstring);
        final String[] descrcomps = MultiProtocolURI.splitpattern.split(rentry.title().toLowerCase());
        int tc;
        for (final String urlcomp : urlcomps) {
            tc = topwords.get(urlcomp);
            if (tc > 0) {
                r += Math.max(1, tc) << this.snippetProcess.query.ranking.coeff_urlcompintoplist;
            }
        }
        for (final String descrcomp : descrcomps) {
            tc = topwords.get(descrcomp);
            if (tc > 0) {
                r += Math.max(1, tc) << this.snippetProcess.query.ranking.coeff_descrcompintoplist;
            }
        }

        // apply query-in-result matching
        final HandleSet urlcomph = Word.words2hashesHandles(urlcomps);
        final HandleSet descrcomph = Word.words2hashesHandles(descrcomps);
        final Iterator<byte[]> shi = this.snippetProcess.query.getQueryGoal().getIncludeHashes().iterator();
        byte[] queryhash;
        while (shi.hasNext()) {
            queryhash = shi.next();
            if (urlcomph.has(queryhash)) {
                r += 256 << this.snippetProcess.query.ranking.coeff_appurl;
            }
            if (descrcomph.has(queryhash)) {
                r += 256 << this.snippetProcess.query.ranking.coeff_app_dc_title;
            }
        }

        return r;
    }
    
    private ResultEntry fetchSnippet(final URIMetadataNode page, final CacheStrategy cacheStrategy) {
        // Snippet Fetching can has 3 modes:
        // 0 - do not fetch snippets
        // 1 - fetch snippets offline only
        // 2 - online snippet fetch

        // load only urls if there was not yet a root url of that hash
        // find the url entry

        String solrsnippet = this.snippetProcess.snippets.get(ASCII.String(page.hash()));
        if (solrsnippet != null && solrsnippet.length() > 0) {
            final TextSnippet snippet = new TextSnippet(page.hash(), solrsnippet, true, ResultClass.SOURCE_CACHE, "");
            return new ResultEntry(page, this.snippetProcess.query.getSegment(), this.snippetProcess.peers, snippet, null, 0);
        }
        
        if (cacheStrategy == null) {
            final TextSnippet snippet = new TextSnippet(
                    null,
                    page,
                    this.snippetProcess.snippetFetchWordHashes,
                    //this.query.queryString,
                    null,
                    ((this.snippetProcess.query.constraint != null) && (this.snippetProcess.query.constraint.get(Condenser.flag_cat_indexof))),
                    SearchEvent.SNIPPET_MAX_LENGTH,
                    !this.snippetProcess.query.isLocal());
            return new ResultEntry(page, this.snippetProcess.query.getSegment(), this.snippetProcess.peers, snippet, null, 0); // result without snippet
        }

        // load snippet
        if (page.url().getContentDomain() == Classification.ContentDomain.TEXT || page.url().getContentDomain() == Classification.ContentDomain.ALL) {
            // attach text snippet
            long startTime = System.currentTimeMillis();
            final TextSnippet snippet = new TextSnippet(
                    this.snippetProcess.loader,
                    page,
                    this.snippetProcess.snippetFetchWordHashes,
                    cacheStrategy,
                    ((this.snippetProcess.query.constraint != null) && (this.snippetProcess.query.constraint.get(Condenser.flag_cat_indexof))),
                    180,
                    !this.snippetProcess.query.isLocal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            SearchEvent.log.logInfo("text snippet load time for " + page.url() + ": " + snippetComputationTime + ", " + (!snippet.getErrorCode().fail() ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));

            if (!snippet.getErrorCode().fail()) {
                // we loaded the file and found the snippet
                return new ResultEntry(page, this.snippetProcess.query.getSegment(), this.snippetProcess.peers, snippet, null, snippetComputationTime); // result with snippet attached
            } else if (cacheStrategy.mustBeOffline()) {
                // we did not demand online loading, therefore a failure does not mean that the missing snippet causes a rejection of this result
                // this may happen during a remote search, because snippet loading is omitted to retrieve results faster
                return new ResultEntry(page, this.snippetProcess.query.getSegment(), this.snippetProcess.peers, null, null, snippetComputationTime); // result without snippet
            } else {
                // problems with snippet fetch
                if (this.snippetProcess.snippetFetchWordHashes.has(Segment.catchallHash)) {
                    // we accept that because the word cannot be on the page
                    return new ResultEntry(page, this.snippetProcess.query.getSegment(), this.snippetProcess.peers, null, null, 0);
                }
                final String reason = "no text snippet; errorCode = " + snippet.getErrorCode();
                if (this.snippetProcess.deleteIfSnippetFail) {
                    this.snippetProcess.workTables.failURLsRegisterMissingWord(this.snippetProcess.query.getSegment().termIndex(), page.url(), this.snippetProcess.query.getQueryGoal().getIncludeHashes(), reason);
                }
                SearchEvent.log.logInfo("sorted out url " + page.url().toNormalform(true) + " during search: " + reason);
                return null;
            }
        }
        return new ResultEntry(page, this.snippetProcess.query.getSegment(), this.snippetProcess.peers, null, null, 0); // result without snippet
    }
}
