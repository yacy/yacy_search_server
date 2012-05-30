// RankingProcess.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.11.2007 on http://yacy.net
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

import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.Classification;
import net.yacy.cora.document.Classification.ContentDomain;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Scanner;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.document.Autotagging;
import net.yacy.document.Autotagging.Metatag;
import net.yacy.document.Condenser;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.TermSearch;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.search.ranking.ReferenceOrder;
import net.yacy.search.snippet.ResultEntry;

public final class RWIProcess extends Thread
{

    private static final long maxWaitPerResult = 300;
    private static final int maxDoubleDomAll = 1000, maxDoubleDomSpecial = 10000;

    private final QueryParams query;
    private final HandleSet urlhashes; // map for double-check; String/Long relation, addresses ranking number (backreference for deletion)
    private final int[] flagcount; // flag counter
    private final HandleSet misses; // contains url-hashes that could not been found in the LURL-DB
    private int sortout; // counter for referenced that had been sorted out for other reasons
    //private final int[] domZones;
    private SortedMap<byte[], ReferenceContainer<WordReference>> localSearchInclusion;

    private int remote_resourceSize;
    private int remote_indexCount;
    private int remote_peerCount;
    private int local_indexCount;
    private final AtomicInteger maxExpectedRemoteReferences, expectedRemoteReferences,
        receivedRemoteReferences;
    private final WeakPriorityBlockingQueue<WordReferenceVars> stack;
    private final AtomicInteger feedersAlive, feedersTerminated;
    private final ConcurrentHashMap<String, WeakPriorityBlockingQueue<WordReferenceVars>> doubleDomCache; // key = domhash (6 bytes); value = like stack
    //private final HandleSet handover; // key = urlhash; used for double-check of urls that had been handed over to search process

    private final ScoreMap<String> ref; // reference score computation for the commonSense heuristic
    private final Map<String, byte[]> hostResolver; // a mapping from a host hash (6 bytes) to the full url hash of one of these urls that have the host hash
    private final ReferenceOrder order;
    private boolean addRunning;
    private final boolean remote;

    // navigation scores
    private final ScoreMap<String> hostNavigator; // a counter for the appearance of the host hash
    private final ScoreMap<String> authorNavigator; // a counter for the appearances of authors
    private final ScoreMap<String> namespaceNavigator; // a counter for name spaces
    private final ScoreMap<String> protocolNavigator; // a counter for protocol types
    private final ScoreMap<String> filetypeNavigator; // a counter for file types
    private final Map<String, ScoreMap<String>> vocabularyNavigator; // counters for Vocabularies

    public RWIProcess(final QueryParams query, final ReferenceOrder order, final int maxentries, final boolean remote) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        // sortorder: 0 = hash, 1 = url, 2 = ranking
        this.addRunning = true;
        this.localSearchInclusion = null;
        this.stack = new WeakPriorityBlockingQueue<WordReferenceVars>(maxentries);
        this.doubleDomCache = new ConcurrentHashMap<String, WeakPriorityBlockingQueue<WordReferenceVars>>();
        this.query = query;
        this.order = order;
        this.remote = remote;
        this.remote_peerCount = 0;
        this.remote_resourceSize = 0;
        this.remote_indexCount = 0;
        this.local_indexCount = 0;
        this.urlhashes =
            new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 100);
        this.misses =
            new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 100);
        this.sortout = 0;
        this.flagcount = new int[32];
        for ( int i = 0; i < 32; i++ ) {
            this.flagcount[i] = 0;
        }
        this.hostNavigator = new ConcurrentScoreMap<String>();
        this.hostResolver = new ConcurrentHashMap<String, byte[]>();
        this.authorNavigator = new ConcurrentScoreMap<String>();
        this.namespaceNavigator = new ConcurrentScoreMap<String>();
        this.protocolNavigator = new ConcurrentScoreMap<String>();
        this.filetypeNavigator = new ConcurrentScoreMap<String>();
        this.vocabularyNavigator = new ConcurrentHashMap<String, ScoreMap<String>>();
        this.ref = new ConcurrentScoreMap<String>();
        this.feedersAlive = new AtomicInteger(0);
        this.feedersTerminated = new AtomicInteger(0);
        this.maxExpectedRemoteReferences = new AtomicInteger(0);
        this.expectedRemoteReferences = new AtomicInteger(0);
        this.receivedRemoteReferences = new AtomicInteger(0);
    }

    public void addExpectedRemoteReferences(int x) {
        if ( x > 0 ) {
            this.maxExpectedRemoteReferences.addAndGet(x);
        }
        this.expectedRemoteReferences.addAndGet(x);
    }

    public boolean expectMoreRemoteReferences() {
        return this.expectedRemoteReferences.get() > 0;
    }

    public long waitTimeRecommendation() {
        return
            this.maxExpectedRemoteReferences.get() == 0 ? 0 :
                Math.min(maxWaitPerResult,
                    Math.min(
                        maxWaitPerResult * this.expectedRemoteReferences.get() / this.maxExpectedRemoteReferences.get(),
                        maxWaitPerResult * (100 - Math.min(100, this.receivedRemoteReferences.get())) / 100));
    }

    public QueryParams getQuery() {
        return this.query;
    }

    public ReferenceOrder getOrder() {
        return this.order;
    }

    @Override
    public void run() {
        // do a search
        oneFeederStarted();

        // sort the local containers and truncate it to a limited count,
        // so following sortings together with the global results will be fast
        try {
            final long timer = System.currentTimeMillis();
            final TermSearch<WordReference> search =
                this.query
                    .getSegment()
                    .termIndex()
                    .query(
                        this.query.queryHashes,
                        this.query.excludeHashes,
                        null,
                        Segment.wordReferenceFactory,
                        this.query.maxDistance);
            this.localSearchInclusion = search.inclusion();
            final ReferenceContainer<WordReference> index = search.joined();
            EventTracker.update(
                EventTracker.EClass.SEARCH,
                new ProfilingGraph.EventSearch(
                    this.query.id(true),
                    SearchEvent.Type.JOIN,
                    this.query.queryString,
                    index.size(),
                    System.currentTimeMillis() - timer),
                false);
            if ( !index.isEmpty() ) {
                add(index, true, "local index: " + this.query.getSegment().getLocation(), -1, true, 10000);
            }
        } catch ( final Exception e ) {
            Log.logException(e);
        } finally {
            oneFeederTerminated();
        }
    }

    public void add(
        final ReferenceContainer<WordReference> index,
        final boolean local,
        final String resourceName,
        final int fullResource,
        final boolean finalizeAddAtEnd,
        final long maxtime) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        //Log.logInfo("RWIProcess", "added a container, size = " + index.size());

        this.addRunning = true;

        assert (index != null);
        if ( index.isEmpty() ) {
            return;
        }

        if ( !local ) {
            assert fullResource >= 0 : "fullResource = " + fullResource;
            this.remote_resourceSize += fullResource;
            this.remote_peerCount++;
        }

        long timer = System.currentTimeMillis();

        // normalize entries
        final BlockingQueue<WordReferenceVars> decodedEntries = this.order.normalizeWith(index, maxtime);
        int is = index.size();
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
            this.query.id(true),
            SearchEvent.Type.NORMALIZING,
            resourceName,
            is,
            System.currentTimeMillis() - timer), false);
        if (!local) {
            this.receivedRemoteReferences.addAndGet(is);
        }

        // iterate over normalized entries and select some that are better than currently stored
        timer = System.currentTimeMillis();
        final boolean nav_hosts =
            this.query.navigators.equals("all") || this.query.navigators.indexOf("hosts", 0) >= 0;

        // apply all constraints
        try {
            WordReferenceVars iEntry;
            final String pattern = this.query.urlMask.pattern();
            final boolean httpPattern = pattern.equals("http://.*");
            final boolean noHttpButProtocolPattern =
                pattern.equals("https://.*")
                    || pattern.equals("ftp://.*")
                    || pattern.equals("smb://.*")
                    || pattern.equals("file://.*");
            pollloop: while ( true ) {
                iEntry = decodedEntries.poll(1, TimeUnit.SECONDS);
                if ( iEntry == null || iEntry == WordReferenceVars.poison ) {
                    break pollloop;
                }
                assert (iEntry.urlhash().length == index.row().primaryKeyLength);
                //if (iEntry.urlHash().length() != index.row().primaryKeyLength) continue;

                // increase flag counts
                for ( int j = 0; j < 32; j++ ) {
                    if ( iEntry.flags().get(j) ) {
                        this.flagcount[j]++;
                    }
                }

                // check constraints
                if ( !testFlags(iEntry) ) {
                    continue pollloop;
                }

                // check document domain
                if ( this.query.contentdom != Classification.ContentDomain.ALL ) {
                    if ( (this.query.contentdom == ContentDomain.AUDIO)
                        && (!(iEntry.flags().get(Condenser.flag_cat_hasaudio))) ) {
                        continue pollloop;
                    }
                    if ( (this.query.contentdom == ContentDomain.VIDEO)
                        && (!(iEntry.flags().get(Condenser.flag_cat_hasvideo))) ) {
                        continue pollloop;
                    }
                    if ( (this.query.contentdom == ContentDomain.IMAGE)
                        && (!(iEntry.flags().get(Condenser.flag_cat_hasimage))) ) {
                        continue pollloop;
                    }
                    if ( (this.query.contentdom == ContentDomain.APP)
                        && (!(iEntry.flags().get(Condenser.flag_cat_hasapp))) ) {
                        continue pollloop;
                    }
                }

                // count domZones
                //this.domZones[DigestURI.domDomain(iEntry.metadataHash())]++;

                // check site constraints
                final String hosthash = iEntry.hosthash();
                if ( this.query.sitehash == null ) {
                    if (this.query.siteexcludes != null && this.query.siteexcludes.contains(hosthash)) {
                        continue pollloop;
                    }
                } else {
                    if ( !hosthash.equals(this.query.sitehash) ) {
                        // filter out all domains that do not match with the site constraint
                        continue pollloop;
                    }
                }

                // collect host navigation information (even if we have only one; this is to provide a switch-off button)
                if (this.query.navigators.isEmpty() && (nav_hosts || this.query.urlMask_isCatchall)) {
                    this.hostNavigator.inc(hosthash);
                    this.hostResolver.put(hosthash, iEntry.urlhash());
                }

                // check protocol
                if ( !this.query.urlMask_isCatchall ) {
                    final boolean httpFlagSet = DigestURI.flag4HTTPset(iEntry.urlHash);
                    if ( httpPattern && !httpFlagSet ) {
                        continue pollloop;
                    }
                    if ( noHttpButProtocolPattern && httpFlagSet ) {
                        continue pollloop;
                    }
                }

                // finally make a double-check and insert result to stack
                // the url hashes should be unique, no reason to check that
                //if (!this.urlhashes.has(iEntry.urlhash())) {
                this.urlhashes.putUnique(iEntry.urlhash());
                rankingtryloop: while ( true ) {
                    try {
                        this.stack.put(new ReverseElement<WordReferenceVars>(iEntry, this.order.cardinal(iEntry))); // inserts the element and removes the worst (which is smallest)
                        break rankingtryloop;
                    } catch ( final ArithmeticException e ) {
                        // this may happen if the concurrent normalizer changes values during cardinal computation
                        continue rankingtryloop;
                    }
                }
                // increase counter for statistics
                if ( local ) {
                    this.local_indexCount++;
                } else {
                    this.remote_indexCount++;
                    //}
                }
            }

        } catch ( final InterruptedException e ) {
        } catch ( final RowSpaceExceededException e ) {
        } finally {
            if ( finalizeAddAtEnd ) {
                this.addRunning = false;
            }
        }

        //if ((query.neededResults() > 0) && (container.size() > query.neededResults())) remove(true, true);
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
            this.query.id(true),
            SearchEvent.Type.PRESORT,
            resourceName,
            index.size(),
            System.currentTimeMillis() - timer), false);
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
        this.feedersAlive.addAndGet(1);
    }

    public boolean feedingIsFinished() {
        return
            this.feedersTerminated.intValue() > (this.remote ? 1 : 0) &&
            this.feedersAlive.get() == 0 &&
            (!this.remote || this.remote_indexCount > 0);
    }

    private boolean testFlags(final WordReference ientry) {
        if ( this.query.constraint == null ) {
            return true;
        }
        // test if ientry matches with filter
        // if all = true: let only entries pass that has all matching bits
        // if all = false: let all entries pass that has at least one matching bit
        if ( this.query.allofconstraint ) {
            for ( int i = 0; i < 32; i++ ) {
                if ( (this.query.constraint.get(i)) && (!ientry.flags().get(i)) ) {
                    return false;
                }
            }
            return true;
        }
        for ( int i = 0; i < 32; i++ ) {
            if ( (this.query.constraint.get(i)) && (ientry.flags().get(i)) ) {
                return true;
            }
        }
        return false;
    }

    public Map<byte[], ReferenceContainer<WordReference>> searchContainerMap() {
        // direct access to the result maps is needed for abstract generation
        // this is only available if execQuery() was called before
        return this.localSearchInclusion;
    }

    private WeakPriorityBlockingQueue.Element<WordReferenceVars> takeRWI(
        final boolean skipDoubleDom,
        final long waitingtime) {

        // returns from the current RWI list the best entry and removes this entry from the list
        WeakPriorityBlockingQueue<WordReferenceVars> m;
        WeakPriorityBlockingQueue.Element<WordReferenceVars> rwi = null;

        // take one entry from the stack if there are entries on that stack or the feeding is not yet finished
        try {
            //System.out.println("stack.poll: feeders = " + this.feeders + ", stack.sizeQueue = " + stack.sizeQueue());
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
            while ( ((!feedingIsFinished() && this.addRunning) || this.stack.sizeQueue() > 0)
                && (this.query.itemsPerPage < 1 || loops++ < this.query.itemsPerPage) ) {
                if ( waitingtime <= 0 ) {
                    rwi = this.stack.poll();
                } else {
                    timeoutloop: while ( System.currentTimeMillis() < timeout ) {
                        if ( feedingIsFinished() && this.stack.sizeQueue() == 0 ) {
                            break timeoutloop;
                        }
                        rwi = this.stack.poll(50);
                        if ( rwi != null ) {
                            break timeoutloop;
                        }
                    }
                }
                if ( rwi == null ) {
                    break;
                }
                if ( !skipDoubleDom ) {
                    //System.out.println("!skipDoubleDom");
                    return rwi;
                }

                // check doubledom
                final String hosthash = rwi.getElement().hosthash();
                synchronized ( this.doubleDomCache ) {
                    m = this.doubleDomCache.get(hosthash);
                    if ( m == null ) {
                        // first appearance of dom. we create an entry to signal that one of that domain was already returned
                        m = new WeakPriorityBlockingQueue<WordReferenceVars>((this.query.specialRights)
                                ? maxDoubleDomSpecial
                                : maxDoubleDomAll);
                        this.doubleDomCache.put(hosthash, m);
                        return rwi;
                    }
                    // second appearances of dom
                    m.put(rwi);
                }
            }
        } catch ( final InterruptedException e1 ) {
        }
        if ( this.doubleDomCache.isEmpty() ) {
            return null;
        }

        // no more entries in sorted RWI entries. Now take Elements from the doubleDomCache
        // find best entry from all caches
        WeakPriorityBlockingQueue.Element<WordReferenceVars> bestEntry = null;
        WeakPriorityBlockingQueue.Element<WordReferenceVars> o;
        synchronized ( this.doubleDomCache ) {
            final Iterator<WeakPriorityBlockingQueue<WordReferenceVars>> i = this.doubleDomCache.values().iterator();
            while ( i.hasNext() ) {
                try {
                    m = i.next();
                } catch ( final ConcurrentModificationException e ) {
                    Log.logException(e);
                    continue; // not the best solution...
                }
                if ( m == null ) {
                    continue;
                }
                if ( m.isEmpty() ) {
                    continue;
                }
                if ( bestEntry == null ) {
                    bestEntry = m.peek();
                    continue;
                }
                o = m.peek();
                if ( o == null ) {
                    continue;
                }
                if ( o.getWeight() < bestEntry.getWeight() ) {
                    bestEntry = o;
                }
            }
            if ( bestEntry == null ) {
                return null;
            }

            // finally remove the best entry from the doubledom cache
            m = this.doubleDomCache.get(bestEntry.getElement().hosthash());
            bestEntry = m.poll();
        }
        return bestEntry;
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
    public URIMetadataRow takeURL(final boolean skipDoubleDom, final long waitingtime) {
        // returns from the current RWI list the best URL entry and removes this entry from the list
        final long timeout = System.currentTimeMillis() + Math.max(10, waitingtime);
        int p = -1;
        long timeleft;
        takeloop: while ( (timeleft = timeout - System.currentTimeMillis()) > 0 ) {
            //System.out.println("timeleft = " + timeleft);
            final WeakPriorityBlockingQueue.Element<WordReferenceVars> obrwi = takeRWI(skipDoubleDom, timeleft);
            if ( obrwi == null ) {
                return null; // all time was already wasted in takeRWI to get another element
            }
            final URIMetadataRow page = this.query.getSegment().urlMetadata().load(obrwi);
            if ( page == null ) {
                try {
                    this.misses.putUnique(obrwi.getElement().urlhash());
                } catch ( final RowSpaceExceededException e ) {
                }
                continue;
            }

            if ( !this.query.urlMask_isCatchall ) {
                // check url mask
                if ( !page.matches(this.query.urlMask) ) {
                    this.sortout++;
                    continue;
                }

                // in case that we do not have e catchall filter for urls
                // we must also construct the domain navigator here
                //if (query.sitehash == null) {
                //    this.hostNavigator.inc(UTF8.String(urlhash, 6, 6));
                //    this.hostResolver.put(UTF8.String(urlhash, 6, 6), UTF8.String(urlhash));
                //}
            }

            // check for more errors
            if ( page.url() == null ) {
                this.sortout++;
                continue; // rare case where the url is corrupted
            }

            // check content domain
            if (this.query.contentdom != Classification.ContentDomain.ALL &&
                page.url().getContentDomain() != Classification.ContentDomain.ALL &&
                page.url().getContentDomain() != this.query.contentdom) {
                this.sortout++;
                continue;
            }

            final String pageurl = page.url().toNormalform(true, true);
            final String pageauthor = page.dc_creator();
            final String pagetitle = page.dc_title().toLowerCase();

            // check exclusion
            if ( (QueryParams.anymatch(pagetitle, this.query.excludeHashes))
                || (QueryParams.anymatch(pageurl.toLowerCase(), this.query.excludeHashes))
                || (QueryParams.anymatch(pageauthor.toLowerCase(), this.query.excludeHashes)) ) {
                this.sortout++;
                continue;
            }

            // check index-of constraint
            if ( (this.query.constraint != null)
                && (this.query.constraint.get(Condenser.flag_cat_indexof))
                && (!(pagetitle.startsWith("index of"))) ) {
                final Iterator<byte[]> wi = this.query.queryHashes.iterator();
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

            // check vocabulary constraint
            final String tags = page.dc_subject();
            final String[] taglist = tags == null || tags.length() == 0 ? new String[0] : SPACE_PATTERN.split(page.dc_subject());
            if (this.query.metatags != null && this.query.metatags.size() > 0) {
                // all metatags must appear in the tags list
                for (Metatag metatag: this.query.metatags) {
                    if (!Autotagging.metatagAppearIn(metatag, taglist)) {
                        this.sortout++;
                        //Log.logInfo("RWIProcess", "sorted out " + page.url());
                        continue takeloop;
                    }
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
                this.hostNavigator.inc(hosthash);
                this.hostResolver.put(hosthash, page.hash());
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

            // vocabulary navigation
            tagharvest: for (String tag: taglist) {
                if (tag.length() < 1 || tag.charAt(0) != LibraryProvider.tagPrefix) continue tagharvest;
                try {
                    Metatag metatag = LibraryProvider.autotagging.metatag(tag);
                    ScoreMap<String> voc = this.vocabularyNavigator.get(metatag.getVocabularyName());
                    if (voc == null) {
                        voc = new ConcurrentScoreMap<String>();
                        this.vocabularyNavigator.put(metatag.getVocabularyName(), voc);
                    }
                    voc.inc(metatag.getPrintName());
                } catch (RuntimeException e) {
                    // tag may not be well-formed
                }
            }

            // accept url
            return page;
        }
        return null;
    }

    final static Pattern SPACE_PATTERN = Pattern.compile(" ");

    public int sizeQueue() {
        int c = this.stack.sizeQueue();
        for ( final WeakPriorityBlockingQueue<WordReferenceVars> s : this.doubleDomCache.values() ) {
            c += s.sizeQueue();
        }
        return c;
    }

    public int sizeAvailable() {
        int c = this.stack.sizeAvailable();
        for ( final WeakPriorityBlockingQueue<WordReferenceVars> s : this.doubleDomCache.values() ) {
            c += s.sizeAvailable();
        }
        return c;
    }

    public boolean isEmpty() {
        if ( !this.stack.isEmpty() ) {
            return false;
        }
        for ( final WeakPriorityBlockingQueue<WordReferenceVars> s : this.doubleDomCache.values() ) {
            if ( !s.isEmpty() ) {
                return false;
            }
        }
        return true;
    }

    public int[] flagCount() {
        return this.flagcount;
    }

    // "results from a total number of <remote_resourceSize + local_resourceSize> known (<local_resourceSize> local, <remote_resourceSize> remote), <remote_indexCount> links from <remote_peerCount> other YaCy peers."

    public int filteredCount() {
        // the number of index entries that are considered as result set
        return this.stack.sizeAvailable();
    }

    public int getLocalIndexCount() {
        // the number of results in the local peer after filtering
        return this.local_indexCount;
    }

    public int getRemoteIndexCount() {
        // the number of result contributions from all the remote peers
        return this.remote_indexCount;
    }

    public int getRemoteResourceSize() {
        // the number of all hits in all the remote peers
        return Math.max(this.remote_resourceSize, this.remote_indexCount);
    }

    public int getRemotePeerCount() {
        // the number of remote peers that have contributed
        return this.remote_peerCount;
    }

    public Iterator<byte[]> miss() {
        return this.misses.iterator();
    }

    public int getMissCount() {
        return this.misses.size();
    }

    public int getSortOutCount() {
        return this.sortout;
    }

    public ScoreMap<String> getNamespaceNavigator() {
        if ( !this.query.navigators.equals("all") && this.query.navigators.indexOf("namespace", 0) < 0 ) {
            return new ClusteredScoreMap<String>();
        }
        return this.namespaceNavigator;
    }

    public ScoreMap<String> getHostNavigator() {
        final ScoreMap<String> result = new ConcurrentScoreMap<String>();
        if ( !this.query.navigators.equals("all") && this.query.navigators.indexOf("hosts", 0) < 0 ) {
            return result;
        }

        final Iterator<String> domhashs = this.hostNavigator.keys(false);
        URIMetadataRow row;
        byte[] urlhash;
        String hosthash, hostname;
        if ( this.hostResolver != null ) {
            while ( domhashs.hasNext() && result.sizeSmaller(30) ) {
                hosthash = domhashs.next();
                if ( hosthash == null ) {
                    continue;
                }
                urlhash = this.hostResolver.get(hosthash);
                row = urlhash == null ? null : this.query.getSegment().urlMetadata().load(urlhash);
                hostname = row == null ? null : row.url().getHost();
                if ( hostname != null ) {
                    result.set(hostname, this.hostNavigator.get(hosthash));
                }
            }
        }
        return result;
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

    public Map<String,ScoreMap<String>> getVocabularyNavigators() {
        return this.vocabularyNavigator;
    }

    public static final Comparator<Map.Entry<String, Integer>> mecomp =
        new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(final Map.Entry<String, Integer> o1, final Map.Entry<String, Integer> o2) {
                if ( o1.getValue().intValue() < o2.getValue().intValue() ) {
                    return 1;
                }
                if ( o2.getValue().intValue() < o1.getValue().intValue() ) {
                    return -1;
                }
                return 0;
            }
        };

    public ScoreMap<String> getTopicNavigator(final int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        final ScoreMap<String> result = new ConcurrentScoreMap<String>();
        if ( !this.query.navigators.equals("all") && this.query.navigators.indexOf("topics", 0) < 0 ) {
            return result;
        }
        if ( this.ref.sizeSmaller(2) ) {
            this.ref.clear(); // navigators with one entry are not useful
        }
        final Map<String, Float> counts = new HashMap<String, Float>();
        final Iterator<String> i = this.ref.keys(false);
        String word;
        byte[] termHash;
        int c;
        float q, min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        int ic = count;
        while ( ic-- > 0 && i.hasNext() ) {
            word = i.next();
            if ( word == null ) {
                continue;
            }
            termHash = Word.word2hash(word);
            c = this.query.getSegment().termIndex().count(termHash);
            if ( c > 0 ) {
                q = ((float) this.ref.get(word)) / ((float) c);
                min = Math.min(min, q);
                max = Math.max(max, q);
                counts.put(word, q);
            }
        }
        if ( max > min ) {
            for ( final Map.Entry<String, Float> ce : counts.entrySet() ) {
                result.set(ce.getKey(), (int) (((double) count) * (ce.getValue() - min) / (max - min)));
            }
        }
        return this.ref;
    }

    private final static Pattern lettermatch = Pattern.compile("[a-z]+");

    public void addTopic(final String[] words) {
        String word;
        for ( final String w : words ) {
            word = w.toLowerCase();
            if ( word.length() > 2
                && "http_html_php_ftp_www_com_org_net_gov_edu_index_home_page_for_usage_the_and_zum_der_die_das_und_the_zur_bzw_mit_blog_wiki_aus_bei_off"
                    .indexOf(word) < 0
                && !this.query.queryHashes.has(Word.word2hash(word))
                && lettermatch.matcher(word).matches()
                && !Switchboard.badwords.contains(word)
                && !Switchboard.stopwords.contains(word) ) {
                this.ref.inc(word);
            }
        }
    }

    public void addTopics(final ResultEntry resultEntry) {
        // take out relevant information for reference computation
        if ( (resultEntry.url() == null) || (resultEntry.title() == null) ) {
            return;
        }
        //final String[] urlcomps = htmlFilterContentScraper.urlComps(resultEntry.url().toNormalform(true, true)); // word components of the url
        final String[] descrcomps = MultiProtocolURI.splitpattern.split(resultEntry.title().toLowerCase()); // words in the description

        // add references
        //addTopic(urlcomps);
        addTopic(descrcomps);
    }

    public ScoreMap<String> getAuthorNavigator() {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        if ( !this.query.navigators.equals("all") && this.query.navigators.indexOf("authors", 0) < 0 ) {
            return new ConcurrentScoreMap<String>();
        }
        return this.authorNavigator;
    }

}
