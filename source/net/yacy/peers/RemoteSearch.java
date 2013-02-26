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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.storage.HandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.repository.Blacklist;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SecondarySearchSuperviser;


public class RemoteSearch extends Thread {

    private static final ThreadGroup ysThreadGroup = new ThreadGroup("yacySearchThreadGroup");

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
                Network.log.logInfo("REMOTE SEARCH - no answer from remote peer " + this.targetPeer.hash + ":" + this.targetPeer.getName());
            }
        } catch (final Exception e) {
            Log.logException(e);
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
            final int count, final long time,
            final Blacklist blacklist,
            final SortedMap<byte[], String> clusterselection,
            final int burstRobinsonPercent,
            final int burstMultiwordPercent) {
        // check own peer status
        //if (wordIndex.seedDB.mySeed() == null || wordIndex.seedDB.mySeed().getPublicAddress() == null) { return null; }

        // prepare seed targets and threads
        final List<Seed> dhtPeers =
            (clusterselection == null) ?
                    DHTSelection.selectSearchTargets(
                            event.peers,
                            event.query.getQueryGoal().getIncludeHashes(),
                            event.peers.redundancy(),
                            burstRobinsonPercent,
                            burstMultiwordPercent)
                  : DHTSelection.selectClusterPeers(event.peers, clusterselection);
        if (dhtPeers == null) return;

        // find nodes
        Set<Seed> omit = new HashSet<Seed>();
        for (Seed s: dhtPeers) omit.add(s);
        List<Seed> nodePeers = DHTSelection.selectNodeSearchTargets(event.peers, 20, omit);
        
        // remove all robinson peers from the dhtPeers and put them to the nodes
        Iterator<Seed> si = dhtPeers.iterator();
        while (si.hasNext()) {
            Seed s = si.next();
            if (!s.getFlagAcceptRemoteIndex()) {
                si.remove();
                nodePeers.add(s);
            }
        }
        
        // for debugging: remove all dht peer to see if solr is working properly
        //dhtPeers.clear(); // FOR DEBUGGING ONLY!!!

        // start solr searches
        for (Seed s: nodePeers) {
            solrRemoteSearch(event, count, s, blacklist);
        }
        
        // start search to YaCy DHT peers
        final int targets = dhtPeers.size();
        if (targets == 0) return;
        for (int i = 0; i < targets; i++) {
            if (dhtPeers.get(i) == null || dhtPeers.get(i).hash == null) continue;
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
                    dhtPeers.get(i),
                    event.secondarySearchSuperviser,
                    blacklist);
                rs.start();
                event.primarySearchThreadsL.add(rs);
            } catch (final OutOfMemoryError e) {
                Log.logException(e);
                break;
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
                        if (urlhashes != null && urlhashes.length() > 0) Network.log.logInfo("SECONDARY REMOTE SEARCH - remote peer " + targetPeer.hash + ":" + targetPeer.getName() + " contributed " + urls + " links for word hash " + wordhashes);
                        event.peers.mySeed().incRI(urls);
                        event.peers.mySeed().incRU(urls);
                    } else {
                        Network.log.logInfo("REMOTE SEARCH - no answer from remote peer " + targetPeer.hash + ":" + targetPeer.getName());
                    }
                } catch (final Exception e) {
                    Log.logException(e);
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
                    final int count,
                    final Seed targetPeer,
                    final Blacklist blacklist) {

        // check own peer status
        if (event.peers.mySeed() == null || event.peers.mySeed().getPublicAddress() == null) { return null; }
        // prepare seed targets and threads
        if (targetPeer != null && targetPeer.hash != null && event.preselectedPeerHashes != null) targetPeer.setAlternativeAddress(event.preselectedPeerHashes.get(ASCII.getBytes(targetPeer.hash)));
        Thread solr = new Thread() {
            @Override
            public void run() {
                int tmpoffset = 0;
                int tmpcount = 10;
                while (tmpoffset + tmpcount <= count && tmpcount > 0) {
                    int urls = 0;
                    try {
                        event.oneFeederStarted();
                        urls = Protocol.solrQuery(
                                        event,
                                        tmpoffset,
                                        tmpcount,
                                        tmpoffset == 0,
                                        targetPeer,
                                        blacklist);
                        if (urls >= 0) {
                            // urls is an array of url hashes. this is only used for log output
                            event.peers.mySeed().incRI(urls);
                            event.peers.mySeed().incRU(urls);
                        } else {
                            if (targetPeer != null) {
                                Network.log.logInfo("REMOTE SEARCH - no answer from remote peer " + targetPeer.hash + ":" + targetPeer.getName());
                            }
                        }
                    } catch (final Exception e) {
                        Log.logException(e);
                    } finally {
                        event.oneFeederTerminated();
                    }
                    if (urls < tmpcount) break; // there won't be more
                    tmpoffset += tmpcount;
                    tmpcount = targetPeer == null ? 10 : count - tmpoffset;
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
