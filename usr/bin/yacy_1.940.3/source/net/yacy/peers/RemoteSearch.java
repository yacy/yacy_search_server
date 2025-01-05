// yacySearch.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.peers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;

import org.apache.solr.client.solrj.SolrQuery;

import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Memory;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.repository.Blacklist;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SecondarySearchSuperviser;

/**
 * Handle remote YaCy peers selection and search requests on them, targeting either their Solr index or RWI (Reverse Word Index)
 */
public class RemoteSearch extends Thread {

    private static final ThreadGroup ysThreadGroup = new ThreadGroup("yacySearchThreadGroup");
    public static final ConcurrentLog log = new ConcurrentLog("DHT");
    
    final private SearchEvent event;
    final private String wordhashes, excludehashes;
    final private ContentDomain contentdom;
    final private boolean strictContentDom;
    final private int partitions;
    final private SecondarySearchSuperviser secondarySearchSuperviser;
    final private Blacklist blacklist;
    
    /** The target peer of this search Thread */
    final private Seed targetPeer;
    private int urls;
    private final int count, maxDistance;
    private final long time;
    final private String language;

    public RemoteSearch(
              final SearchEvent event,
              final String wordhashes,
              final String excludehashes,
              final String language,
              final ContentDomain contentdom,
              final boolean strictContentDom,
              final int count,
              final long time,
              final int maxDistance,
              final int partitions,
              final Seed targetPeer,
              final SecondarySearchSuperviser secondarySearchSuperviser,
              final Blacklist blacklist) {
        super(ysThreadGroup, "yacySearch_" + targetPeer.getName());
        this.event = event;
        this.wordhashes = wordhashes;
        this.excludehashes = excludehashes;
        this.language = language;
        this.contentdom = contentdom;
        this.strictContentDom = strictContentDom;
        this.partitions = partitions;
        this.secondarySearchSuperviser = secondarySearchSuperviser;
        this.blacklist = blacklist;
        this.targetPeer = targetPeer;
        this.urls = -1;
        this.count = count;
        this.time = time;
        this.maxDistance = maxDistance;
    }

    /**
     * Run a search request on a YaCy peer RWI (Reverse Word Index).
     */
    @Override
    public void run() {
        this.event.oneFeederStarted();
        try {
            this.urls = Protocol.primarySearch(
                        this.event,
                        this.wordhashes,
                        this.excludehashes,
                        this.language,
                        this.contentdom,
                        this.strictContentDom,
                        this.count,
                        this.time,
                        this.maxDistance,
                        this.partitions,
                        this.targetPeer,
                        this.secondarySearchSuperviser,
                        this.blacklist);
            if (this.urls >= 0) {
                // urls is an array of url hashes. this is only used for log output
                this.event.peers.mySeed().incRI(this.urls);
                this.event.peers.mySeed().incRU(this.urls);
            } else {
                Network.log.info("REMOTE SEARCH - no answer from remote peer " + this.targetPeer.hash + ":" + this.targetPeer.getName());
            }
        } catch(InterruptedException e) {
        	Network.log.info("REMOTE SEARCH - interrupted search to remote peer " + this.targetPeer.hash + ":" + this.targetPeer.getName());
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        } finally {
            this.event.oneFeederTerminated();
        }
    }

    /**
     * Convenience method to get a String representation of a set of hashes
     * @param hashes word hashes
     * @return the set serialized as an ASCII String
     */
    public static String set2string(final HandleSet hashes) {
        final StringBuilder wh = new StringBuilder(hashes.size() * 12);
        final Iterator<byte[]> iter = hashes.iterator();
        while (iter.hasNext()) { wh.append(ASCII.String(iter.next())); }
        return wh.toString();
    }

    /**
     * @return the target peer of this search Thread
     */
    public Seed target() {
        return this.targetPeer;
    }

    /**
     * Select YaCy peers using DHT rules and start new threads requesting remotely RWI or Solr index on them.
     * @param event the origin search event. Must not be null.
     * @param start offset start index for Solr queries
     * @param count the desired number of elements to retrieve on Solr indexes
     * @param time the maximum processing time used to retrieve results on the remote RWI peers. Does not include HTTP request networking latency.
     * @param blacklist the blacklist to use. Can be empty but must not be null.
     * @param clusterselection a eventual selection of YaCy peers hashes from a same cluster. Can be null.
     */
    public static void primaryRemoteSearches(
    		final SearchEvent event,
    		final int start, final int count, 
            final long time,
            final Blacklist blacklist,
            final SortedSet<byte[]> clusterselection) {
        // check own peer status
        //if (wordIndex.seedDB.mySeed() == null || wordIndex.seedDB.mySeed().getPublicAddress() == null) { return null; }
        Switchboard sb = Switchboard.getSwitchboard();
        
        // check the peer memory and lifesign-situation to get a scaling for the number of remote search processes
        final boolean shortmem = MemoryControl.shortStatus();
        final int indexingQueueSize = event.query.getSegment().fulltext().bufferSize();
        int redundancy = event.peers.redundancy();
        StringBuilder healthMessage = new StringBuilder(50);
        if (indexingQueueSize > 0) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", indexingQueueSize > 0");}
        if (indexingQueueSize > 10) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", indexingQueueSize > 10");}
        if (indexingQueueSize > 50) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", indexingQueueSize > 50");}
        if (Memory.getSystemLoadAverage() > 2.0) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", load() > 2.0");}
        if (Memory.cores() < 4) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", cores() < 4");}
        if (Memory.cores() == 1) {redundancy = 1; healthMessage.append(", cores() == 1");}
        final int minage = 3;
        final int minRWIWordCount = 1; // we exclude seeds with empty or disabled RWI from remote RWI search
        int robinsoncount = event.peers.scheme.verticalPartitions() * redundancy / 2;
        if (indexingQueueSize > 0) robinsoncount = Math.max(1, robinsoncount / 2);
        if (indexingQueueSize > 10) robinsoncount = Math.max(1, robinsoncount / 2);
        if (indexingQueueSize > 50) robinsoncount = Math.max(1, robinsoncount / 2);
        if (shortmem) {redundancy = 1; robinsoncount = Math.max(1, robinsoncount / 2); healthMessage.append(", shortmem");}
        
        
        // prepare seed targets and threads
        Random random = new Random(System.currentTimeMillis());
        Collection<Seed> dhtPeers = null;
        if (clusterselection != null) {
            dhtPeers = DHTSelection.selectClusterPeers(event.peers, clusterselection);
        } else {
            if (event.query.getQueryGoal().isCatchall() || event.query.getQueryGoal().getIncludeHashes().has(Segment.catchallHash)) {
                if (event.query.modifier.sitehost != null && event.query.modifier.sitehost.length() > 0) {
                    // select peers according to host name, not the query goal
                    String newGoal = Domains.getSmartSLD(event.query.modifier.sitehost);
                    dhtPeers = DHTSelection.selectDHTSearchTargets(
                            event.peers,
                            QueryParams.hashes2Set(ASCII.String(Word.word2hash(newGoal))),
                            minage,
                            minRWIWordCount,
                            redundancy, event.peers.redundancy(),
                            random);
                } else {
                    // select just random peers
                    dhtPeers = DHTSelection.seedsByAge(event.peers, false, event.peers.redundancy(), minRWIWordCount).values();
                }
            } else {
                dhtPeers = DHTSelection.selectDHTSearchTargets(
                                event.peers,
                                event.query.getQueryGoal().getIncludeHashes(),
                                minage,
                                minRWIWordCount,
                                redundancy, event.peers.redundancy(),
                                random);
                // this set of peers may be too large and consume too many threads if more than one word is searched.
                // to prevent overloading, we do a subset collection based on random to prevent the death of the own peer
                // and to do a distributed load-balancing on the target peers
                long targetSize = 1 + redundancy * event.peers.scheme.verticalPartitions(); // this is the maximum for one word plus one
                if (dhtPeers.size() > targetSize) {
                    ArrayList<Seed> pa = new ArrayList<Seed>(dhtPeers.size());
                    pa.addAll(dhtPeers);
                    dhtPeers.clear();
                    for (int i = 0; i < targetSize; i++) dhtPeers.add(pa.remove(random.nextInt(pa.size())));
                }
            }
        }
        if (dhtPeers == null) dhtPeers = new HashSet<Seed>();

        // select node targets
        final Collection<Seed> robinsonPeers = DHTSelection.selectExtraTargets(event.peers, event.query.getQueryGoal().getIncludeHashes(), minage, dhtPeers, robinsoncount, random);
        
        if (event.peers != null) {
            if (sb.getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_DHT_TESTLOCAL, false)) {
                dhtPeers.clear();
                dhtPeers.add(event.peers.mySeed());
            }
            
            if (sb.getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_SOLR_TESTLOCAL, false)) {
                robinsonPeers.clear();
                robinsonPeers.add(event.peers.mySeed());
            }
        }
        
        log.info("preparing remote search: shortmem=" + (shortmem ? "true" : "false") + ", indexingQueueSize=" + indexingQueueSize +
                ", redundancy=" + redundancy + ", minage=" + minage + ", dhtPeers=" + dhtPeers.size() + ", robinsonpeers=" + robinsonPeers.size() + ", health: " + (healthMessage.length() > 0 ? healthMessage.substring(2) : "perfect"));

        /* Computing Solr facets is not relevant for remote Solr results and adds unnecessary CPU load on remote peers :
         * facets count the total number of matching results per facet field, but we only fetch here at most 'count' results. The remaining part
         * is not to be retrieved from remote peers even if making a new request filtering on one of these fields,
         * as there is no insurance the same remote peers would be selected. What's more, remote results can contain many
         * duplicates that would be filtered when adding them to the event node stack.
         */
        final boolean useFacets = false;
        
        // start solr searches
        final int targets = dhtPeers.size() + robinsonPeers.size();
        if (!sb.getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_SOLR_OFF, false)) {
			final SolrQuery solrQuery = event.query.solrQuery(event.getQuery().contentdom,
					event.query.isStrictContentDom(), useFacets, event.excludeintext_image);
            for (Seed s: robinsonPeers) {
				if (MemoryControl.shortStatus()
						|| Memory.getSystemLoadAverage() > sb.getConfigFloat(SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR,
								SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR_DEFAULT)) {
					continue;
				}
                Thread t = solrRemoteSearch(event, solrQuery, start, count, s, targets, blacklist, useFacets, true);
                event.nodeSearchThreads.add(t);
            }
        }
        
        // start search to YaCy DHT peers
        if (!sb.getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_DHT_OFF, false)) {
            for (Seed dhtPeer: dhtPeers) {
                if (dhtPeer == null || dhtPeer.hash == null) continue;
				if (MemoryControl.shortStatus()
						|| Memory.getSystemLoadAverage() > sb.getConfigFloat(SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI,
								SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI_DEFAULT)) {
					continue;
				}
                try {
                    RemoteSearch rs = new RemoteSearch(
                        event,
                        QueryParams.hashSet2hashString(event.query.getQueryGoal().getIncludeHashes()),
                        QueryParams.hashSet2hashString(event.query.getQueryGoal().getExcludeHashes()),
                        event.query.targetlang == null ? "" : event.query.targetlang,
                        event.query.contentdom == null ? ContentDomain.ALL : event.query.contentdom,
                        event.query.isStrictContentDom(),
                        count,
                        time,
                        event.query.maxDistance,
                        targets,
                        dhtPeer,
                        event.secondarySearchSuperviser,
                        blacklist);
                    rs.start();
                    event.primarySearchThreadsL.add(rs);
                } catch (final OutOfMemoryError e) {
                    ConcurrentLog.logException(e);
                    break;
                }
            }
        }
    }

    public static Thread secondaryRemoteSearch(
    		final SearchEvent event,
            final Set<String> wordhashes,
            final String urlhashes,
            final long time,
            final String targethash,
            final Blacklist blacklist) {

        // check own peer status
        if (event.peers.mySeed() == null || event.peers.mySeed().getIPs().size() == 0) { return null; }
        assert urlhashes != null;
        assert urlhashes.length() > 0;

        // prepare seed targets and threads
        final Seed targetPeer = event.peers.getConnected(targethash);
        if (targetPeer == null || targetPeer.hash == null) return null;
        Thread secondary = new Thread("RemoteSearch.secondaryRemoteSearch(" + wordhashes + " to " + targethash + ")") {
            @Override
            public void run() {
                event.oneFeederStarted();
                try {
                    int urls = Protocol.secondarySearch(
                                event,
                                QueryParams.hashSet2hashString(wordhashes),
                                urlhashes,
                                ContentDomain.ALL,
                                false,
                                20,
                                time,
                                999,
                                0,
                                targetPeer,
                                blacklist);
                    if (urls >= 0) {
                        // urls is an array of url hashes. this is only used for log output
                        if (urlhashes != null && urlhashes.length() > 0) Network.log.info("SECONDARY REMOTE SEARCH - remote peer " + targetPeer.hash + ":" + targetPeer.getName() + " contributed " + urls + " links for word hash " + wordhashes);
                        event.peers.mySeed().incRI(urls);
                        event.peers.mySeed().incRU(urls);
                    } else {
                        Network.log.info("REMOTE SEARCH - no answer from remote peer " + targetPeer.hash + ":" + targetPeer.getName());
                    }
                } catch (final InterruptedException e) {
                	Network.log.info("REMOTE SEARCH - interrupted search to remote peer " + targetPeer.hash + ":" + targetPeer.getName());
                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                } finally {
                    event.oneFeederTerminated();
                }
            }
        };
        secondary.start();
        return secondary;
    }

    /**
     * Create and start a thread running a Solr query on the specified target or on this peer when the target is null.
     * @param event the origin search event. Must not be null.
     * @param solrQuery the Solr query derived from the search event. Must not be null.
     * @param start offset start index
     * @param count the desired number of elements to retrieve
     * @param targetPeer the target of the Solr query. When null, the query will run on this local peer.
     * @param partitions the Solr query "partitions" parameter. Ignored when set to zero.
     * @param blacklist the blacklist to use. Can be empty but must not be null.
     * @param useSolrFacets when true, use Solr computed facets when possible to update the event navigators counters
     * @param incrementNavigators when true, increment event navigators either with facet counts or with individual results
     * @return the created and running Thread instance
     */
    public static Thread solrRemoteSearch(
                    final SearchEvent event,
                    final SolrQuery solrQuery,
                    final int start,
                    final int count,
                    final Seed targetPeer,
                    final int partitions,
                    final Blacklist blacklist,
                    final boolean useSolrFacets,
                    final boolean incrementNavigators) {
        
        //System.out.println("*** debug-remoteSearch ***:" + ConcurrentLog.stackTrace());
        
        assert solrQuery != null;
        // check own peer status
        if (event.peers.mySeed() == null) { return null; }
        // prepare threads
        Thread solr = new Thread("RemoteSearch.solrRemoteSearch(" + solrQuery.getQuery() + " to " + (targetPeer == null ? "myself" : targetPeer.hash) + ")") {
            @Override
            public void run() {
                    int urls = 0;
                    try {
                        event.oneFeederStarted();
                        urls = Protocol.solrQuery(
                                        event,
                                        solrQuery,
                                        start,
                                        count,
                                        targetPeer == null ? event.peers.mySeed() : targetPeer,
                                        partitions,
                                        blacklist,
                                        useSolrFacets,
                                        incrementNavigators);
                        if (urls >= 0) {
                            // urls is an array of url hashes. this is only used for log output
                            event.peers.mySeed().incRI(urls);
                            event.peers.mySeed().incRU(urls);
                        } else {
                            if (targetPeer != null) {
                                Network.log.info("REMOTE SEARCH - no answer from remote peer " + targetPeer.hash + ":" + targetPeer.getName());
                            }
                        }
                    } catch (final InterruptedException e) {
                    	Network.log.info("REMOTE SEARCH - interrupted search to remote peer " + targetPeer.hash + ":" + targetPeer.getName());
                    } catch (final Exception e) {
                        ConcurrentLog.logException(e);
                    } finally {
                        event.oneFeederTerminated();
                    }
            }
        };
        /*if (targetPeer == null) solr.run(); else*/ solr.start();
        return solr;
    }

    public static int remainingWaiting(final RemoteSearch[] searchThreads) {
        if (searchThreads == null) return 0;
        int alive = 0;
        for (final RemoteSearch searchThread : searchThreads) {
            if (searchThread.isAlive()) alive++;
        }
        return alive;
    }

    public static int collectedLinks(final RemoteSearch[] searchThreads) {
        int links = 0;
        for (final RemoteSearch searchThread : searchThreads) {
            if (!(searchThread.isAlive()) && searchThread.urls > 0) {
                links += searchThread.urls;
            }
        }
        return links;
    }

    public static void interruptAlive(final RemoteSearch[] searchThreads) {
        for (final RemoteSearch searchThread : searchThreads) {
            if (searchThread.isAlive()) searchThread.interrupt();
        }
    }

}
