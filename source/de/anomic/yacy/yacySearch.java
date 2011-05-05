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

package de.anomic.yacy;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.repository.Blacklist;

import de.anomic.search.QueryParams;
import de.anomic.search.RankingProfile;
import de.anomic.search.RankingProcess;
import de.anomic.search.SearchEvent;
import de.anomic.search.Segment;
import de.anomic.yacy.dht.PeerSelection;

public class yacySearch extends Thread {

    private static final ThreadGroup ysThreadGroup = new ThreadGroup("yacySearchThreadGroup");
    
    final private String wordhashes, excludehashes, urlhashes, sitehash, authorhash;
    final private boolean global;
    final private int partitions;
    final private Segment indexSegment;
    final private RankingProcess containerCache;
    final private SearchEvent.SecondarySearchSuperviser secondarySearchSuperviser;
    final private Blacklist blacklist;
    final private yacySeed targetPeer;
    private int urls;
    private final int count, maxDistance;
    private final long time;
    final private RankingProfile rankingProfile;
    final private Pattern prefer, filter, snippet;
    final private String language;
    final private Bitfield constraint;
    final private yacySeedDB peers;
    
    public yacySearch(
              final String wordhashes, final String excludehashes,
              final String urlhashes,
              final Pattern prefer,
              final Pattern filter,
              final Pattern snippet,
              final String language,
              final String sitehash, final String authorhash,
              final int count, final long time, final int maxDistance, 
              final boolean global, final int partitions,
              final yacySeed targetPeer,
              final Segment indexSegment,
              final yacySeedDB peers,
              final RankingProcess containerCache,
              final SearchEvent.SecondarySearchSuperviser secondarySearchSuperviser,
              final Blacklist blacklist,
              final RankingProfile rankingProfile,
              final Bitfield constraint) {
        super(ysThreadGroup, "yacySearch_" + targetPeer.getName());
        //System.out.println("DEBUG - yacySearch thread " + this.getName() + " initialized " + ((urlhashes.length() == 0) ? "(primary)" : "(secondary)"));
        assert wordhashes.length() >= 12;
        this.wordhashes = wordhashes;
        this.excludehashes = excludehashes;
        this.urlhashes = urlhashes;
        this.prefer = prefer;
        this.filter = filter;
        this.snippet = snippet;
        this.language = language;
        this.sitehash = sitehash;
        this.authorhash = authorhash;
        this.global = global;
        this.partitions = partitions;
        this.indexSegment = indexSegment;
        this.peers = peers;
        this.containerCache = containerCache;
        this.secondarySearchSuperviser = secondarySearchSuperviser;
        this.blacklist = blacklist;
        this.targetPeer = targetPeer;
        this.urls = -1;
        this.count = count;
        this.time = time;
        this.maxDistance = maxDistance;
        this.rankingProfile = rankingProfile;
        this.constraint = constraint;
    }

    @Override
    public void run() {
        try {
            this.urls = yacyClient.search(
                        peers.mySeed(),
                        wordhashes, excludehashes, urlhashes,
                        prefer, filter, snippet,
                        language, sitehash, authorhash,
                        count, time, maxDistance, global, partitions,
                        targetPeer, indexSegment, containerCache, secondarySearchSuperviser,
                        blacklist, rankingProfile, constraint);
            if (urls >= 0) {
                // urls is an array of url hashes. this is only used for log output
                if (urlhashes != null && urlhashes.length() > 0) yacyCore.log.logInfo("SECONDARY REMOTE SEARCH - remote peer " + targetPeer.hash + ":" + targetPeer.getName() + " contributed " + this.urls + " links for word hash " + wordhashes);
                peers.mySeed().incRI(urls);
                peers.mySeed().incRU(urls);
            } else {
                yacyCore.log.logInfo("REMOTE SEARCH - no answer from remote peer " + targetPeer.hash + ":" + targetPeer.getName());
            }
        } catch (final Exception e) {
            Log.logException(e);
        } finally {
        	containerCache.oneFeederTerminated();
        }
    }
    
    public static String set2string(final HandleSet hashes) {
        StringBuilder wh = new StringBuilder(hashes.size() * 12);
        final Iterator<byte[]> iter = hashes.iterator();
        while (iter.hasNext()) { wh.append(UTF8.String(iter.next())); }
        return wh.toString();
    }

    public int links() {
        return this.urls;
    }
    
    public int count() {
        return this.count;
    }
    
    public yacySeed target() {
        return targetPeer;
    }
    
    public static yacySearch[] primaryRemoteSearches(
            final String wordhashes, final String excludehashes,
            final Pattern prefer, final Pattern filter, final Pattern snippet,
            final String language,
            final String sitehash,
            final String authorhash,
            final int count, long time, final int maxDist,
            final Segment indexSegment,
            final yacySeedDB peers,
            final RankingProcess containerCache,
            final SearchEvent.SecondarySearchSuperviser secondarySearchSuperviser,
            final Blacklist blacklist,
            final RankingProfile rankingProfile,
            final Bitfield constraint,
            final SortedMap<byte[], String> clusterselection,
            final int burstRobinsonPercent,
            final int burstMultiwordPercent) {
        // check own peer status
        //if (wordIndex.seedDB.mySeed() == null || wordIndex.seedDB.mySeed().getPublicAddress() == null) { return null; }

        // prepare seed targets and threads
        assert language != null;
        assert wordhashes.length() >= 12 : "wordhashes = " + wordhashes;
        final yacySeed[] targetPeers =
            (clusterselection == null) ?
                    PeerSelection.selectSearchTargets(
                            peers,
                            QueryParams.hashes2Set(wordhashes),
                            peers.redundancy(),
                            burstRobinsonPercent,
                            burstMultiwordPercent)
                  : PeerSelection.selectClusterPeers(peers, clusterselection);
        if (targetPeers == null) return new yacySearch[0];
        int targets = targetPeers.length;
        if (targets == 0) return new yacySearch[0];
        final yacySearch[] searchThreads = new yacySearch[targets];
        for (int i = 0; i < targets; i++) {
            if (targetPeers[i] == null || targetPeers[i].hash == null) continue;
            try {
                searchThreads[i] = new yacySearch(
                    wordhashes, excludehashes, "", prefer, filter, snippet,
                    language, sitehash, authorhash,
                    count, time, maxDist, true, targets, targetPeers[i],
                    indexSegment, peers, containerCache, secondarySearchSuperviser, blacklist, rankingProfile, constraint);
                searchThreads[i].start();
            } catch (OutOfMemoryError e) {
                Log.logException(e);
                break;
            }
        }
        return searchThreads;
    }
    
    public static yacySearch secondaryRemoteSearch(
            final String wordhashes, final String urlhashes,
            final long time,
            final Segment indexSegment,
            final yacySeedDB peers,
            final RankingProcess containerCache,
            final String targethash, final Blacklist blacklist,
            final RankingProfile rankingProfile,
            final Bitfield constraint, final SortedMap<byte[], String> clusterselection) {
    	assert wordhashes.length() >= 12 : "wordhashes = " + wordhashes;
    	
        // check own peer status
        if (peers.mySeed() == null || peers.mySeed().getPublicAddress() == null) { return null; }
        assert urlhashes != null;
        assert urlhashes.length() > 0;
        
        // prepare seed targets and threads
        final yacySeed targetPeer = peers.getConnected(targethash);
        if (targetPeer == null || targetPeer.hash == null) return null;
        if (clusterselection != null) targetPeer.setAlternativeAddress(clusterselection.get(UTF8.getBytes(targetPeer.hash)));
        final yacySearch searchThread = new yacySearch(
                wordhashes, "", urlhashes, QueryParams.matchnothing_pattern, QueryParams.catchall_pattern, QueryParams.catchall_pattern, "", "", "", 20, time, 9999, true, 0, targetPeer,
                indexSegment, peers, containerCache, null, blacklist, rankingProfile, constraint);
        searchThread.start();
        return searchThread;
    }
    
    public static int remainingWaiting(final yacySearch[] searchThreads) {
        if (searchThreads == null) return 0;
        int alive = 0;
        for (final yacySearch searchThread : searchThreads) {
            if (searchThread.isAlive()) alive++;
        }
        return alive;
    }
    
    public static int collectedLinks(final yacySearch[] searchThreads) {
        int links = 0;
        for (final yacySearch searchThread : searchThreads) {
            if (!(searchThread.isAlive()) && searchThread.urls > 0) {
                links += searchThread.urls;
            }
        }
        return links;
    }
    
    public static void interruptAlive(final yacySearch[] searchThreads) {
        for (final yacySearch searchThread : searchThreads) {
            if (searchThread.isAlive()) searchThread.interrupt();
        }
    }
    
}
