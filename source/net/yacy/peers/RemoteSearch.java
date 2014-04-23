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
import java.util.SortedMap;

import org.apache.solr.client.solrj.SolrQuery;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Memory;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.repository.Blacklist;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SecondarySearchSuperviser;


public class RemoteSearch extends Thread {

    private static final ThreadGroup ysThreadGroup = new ThreadGroup("yacySearchThreadGroup");
    public static final ConcurrentLog log = new ConcurrentLog("DHT");
    
    final private SearchEvent event;
    final private String wordhashes, excludehashes, contentdom;
    final private int partitions;
    final private SecondarySearchSuperviser secondarySearchSuperviser;
    final private Blacklist blacklist;
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
              final String contentdom,
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
        this.partitions = partitions;
        this.secondarySearchSuperviser = secondarySearchSuperviser;
        this.blacklist = blacklist;
        this.targetPeer = targetPeer;
        this.urls = -1;
        this.count = count;
        this.time = time;
        this.maxDistance = maxDistance;
    }

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
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        } finally {
            this.event.oneFeederTerminated();
        }
    }

    public static String set2string(final HandleSet hashes) {
        final StringBuilder wh = new StringBuilder(hashes.size() * 12);
        final Iterator<byte[]> iter = hashes.iterator();
        while (iter.hasNext()) { wh.append(ASCII.String(iter.next())); }
        return wh.toString();
    }

    public Seed target() {
        return this.targetPeer;
    }

    public static void primaryRemoteSearches(
    		final SearchEvent event,
    		final int start, final int count, 
            final long time,
            final Blacklist blacklist,
            final SortedMap<byte[], String> clusterselection) {
        // check own peer status
        //if (wordIndex.seedDB.mySeed() == null || wordIndex.seedDB.mySeed().getPublicAddress() == null) { return null; }
        
        // check the peer memory and lifesign-situation to get a scaling for the number of remote search processes
        final boolean shortmem = MemoryControl.shortStatus();
        final int indexingQueueSize = event.query.getSegment().fulltext().bufferSize();
        int redundancy = event.peers.redundancy();
        StringBuilder healthMessage = new StringBuilder(50);
        if (indexingQueueSize > 0) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", indexingQueueSize > 0");}
        if (indexingQueueSize > 10) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", indexingQueueSize > 10");}
        if (indexingQueueSize > 50) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", indexingQueueSize > 50");}
        if (Memory.load() > 2.0) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", load() > 2.0");}
        if (Memory.cores() < 4) {redundancy = Math.max(1, redundancy - 1); healthMessage.append(", cores() < 4");}
        if (Memory.cores() == 1) {redundancy = 1; healthMessage.append(", cores() == 1");}
        int minage = 3;
        int robinsoncount = event.peers.scheme.verticalPartitions() * redundancy / 2;
        if (indexingQueueSize > 0) robinsoncount = Math.max(1, robinsoncount / 2);
        if (indexingQueueSize > 10) robinsoncount = Math.max(1, robinsoncount / 2);
        if (indexingQueueSize > 50) robinsoncount = Math.max(1, robinsoncount / 2);
        if (shortmem) {redundancy = 1; robinsoncount = Math.max(1, robinsoncount / 2); healthMessage.append(", shortmem");}
        
        
        // prepare seed targets and threads
        Random random = new Random(System.currentTimeMillis());
        Set<Seed> dhtPeers = null;
        if (clusterselection != null) {
            dhtPeers = DHTSelection.selectClusterPeers(event.peers, clusterselection);
        } else {
            dhtPeers = DHTSelection.selectDHTSearchTargets(
                            event.peers,
                            event.query.getQueryGoal().getIncludeHashes(),
                            minage,
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
        if (dhtPeers == null) dhtPeers = new HashSet<Seed>();
        
        // select node targets
        final Collection<Seed> robinsonPeers = DHTSelection.selectExtraTargets(event.peers, event.query.getQueryGoal().getIncludeHashes(), minage, dhtPeers, robinsoncount, random);
        
        if (event.peers != null) {
            if (Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_DHT_TESTLOCAL, false)) {
                dhtPeers.clear();
                dhtPeers.add(event.peers.mySeed());
            }
            
            if (Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_SOLR_TESTLOCAL, false)) {
                robinsonPeers.clear();
                robinsonPeers.add(event.peers.mySeed());
            }
        }
        
        log.info("preparing remote search: shortmem=" + (shortmem ? "true" : "false") + ", indexingQueueSize=" + indexingQueueSize +
                ", redundancy=" + redundancy + ", minage=" + minage + ", dhtPeers=" + dhtPeers.size() + ", robinsonpeers=" + robinsonPeers.size() + ", health: " + (healthMessage.length() > 0 ? healthMessage.substring(2) : "perfect"));
        
        
        // start solr searches
        final int targets = dhtPeers.size() + robinsonPeers.size();
        if (!Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_SOLR_OFF, false)) {
            final SolrQuery solrQuery = event.query.solrQuery(event.getQuery().contentdom, start == 0, event.excludeintext_image);
            for (Seed s: robinsonPeers) {
                if (MemoryControl.shortStatus() || Memory.load() > 4.0) continue;
                Thread t = solrRemoteSearch(event, solrQuery, start, count, s, targets, blacklist);
                event.nodeSearchThreads.add(t);
            }
        }
        
        // start search to YaCy DHT peers
        if (!Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_DHT_OFF, false)) {
            for (Seed dhtPeer: dhtPeers) {
                if (dhtPeer == null || dhtPeer.hash == null) continue;
                if (MemoryControl.shortStatus() || Memory.load() > 8.0) continue;
                try {
                    RemoteSearch rs = new RemoteSearch(
                        event,
                        QueryParams.hashSet2hashString(event.query.getQueryGoal().getIncludeHashes()),
                        QueryParams.hashSet2hashString(event.query.getQueryGoal().getExcludeHashes()),
                        event.query.targetlang == null ? "" : event.query.targetlang,
                        event.query.contentdom == null ? "all" : event.query.contentdom.toString(),
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
        if (event.peers.mySeed() == null || event.peers.mySeed().getPublicAddress() == null) { return null; }
        assert urlhashes != null;
        assert urlhashes.length() > 0;

        // prepare seed targets and threads
        final Seed targetPeer = event.peers.getConnected(targethash);
        if (targetPeer == null || targetPeer.hash == null) return null;
        if (event.preselectedPeerHashes != null) targetPeer.setAlternativeAddress(event.preselectedPeerHashes.get(ASCII.getBytes(targetPeer.hash)));
        Thread secondary = new Thread() {
            @Override
            public void run() {
                this.setName("RemoteSearch.secondaryRemoteSearch(" + wordhashes + " to " + targethash + ")");
                event.oneFeederStarted();
                try {
                    int urls = Protocol.secondarySearch(
                                event,
                                QueryParams.hashSet2hashString(wordhashes),
                                urlhashes,
                                "all",
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

    public static Thread solrRemoteSearch(
                    final SearchEvent event,
                    final SolrQuery solrQuery,
                    final int start,
                    final int count,
                    final Seed targetPeer,
                    final int partitions,
                    final Blacklist blacklist) {

        assert solrQuery != null;
        // check own peer status
        if (event.peers.mySeed() == null || event.peers.mySeed().getPublicAddress() == null) { return null; }
        // prepare seed targets and threads
        if (targetPeer != null && targetPeer.hash != null && event.preselectedPeerHashes != null) {
            if (!targetPeer.getFlagSolrAvailable()) return null; // solr interface not avail.
            targetPeer.setAlternativeAddress(event.preselectedPeerHashes.get(ASCII.getBytes(targetPeer.hash)));
        }
        Thread solr = new Thread() {
            @Override
            public void run() {
                this.setName("RemoteSearch.solrRemoteSearch(" + solrQuery.getQuery() + " to " + (targetPeer == null ? "myself" : targetPeer.hash) + ")");
                    int urls = 0;
                    try {
                        event.oneFeederStarted();
                        boolean localsearch = (targetPeer == null || targetPeer.equals(event.peers.mySeed())) && Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_SOLR_TESTLOCAL, false);
                        urls = Protocol.solrQuery(
                                        event,
                                        solrQuery,
                                        start,
                                        count,
                                        localsearch ? event.peers.mySeed() : targetPeer,
                                        partitions,
                                        blacklist);
                        if (urls >= 0) {
                            // urls is an array of url hashes. this is only used for log output
                            event.peers.mySeed().incRI(urls);
                            event.peers.mySeed().incRU(urls);
                        } else {
                            if (targetPeer != null) {
                                Network.log.info("REMOTE SEARCH - no answer from remote peer " + targetPeer.hash + ":" + targetPeer.getName());
                            }
                        }
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
