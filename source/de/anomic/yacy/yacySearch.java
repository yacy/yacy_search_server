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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.crawler.ResultURLs;
import de.anomic.index.indexReferenceBlacklist;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProcess;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.logging.serverLog;

public class yacySearch extends Thread {

    final private String wordhashes, excludehashes, urlhashes;
    final private boolean global;
    final private int partitions;
    final private plasmaWordIndex wordIndex;
    final private plasmaSearchRankingProcess containerCache;
    final private Map<String, TreeMap<String, String>> abstractCache;
    final private indexReferenceBlacklist blacklist;
    final private yacySeed targetPeer;
    private String[] urls;
    private final int count, maxDistance;
    final private plasmaSearchRankingProfile rankingProfile;
    final private String prefer, filter, language;
    final private kelondroBitfield constraint;
    
    ResultURLs crawlResults;
    
    public yacySearch(final String wordhashes, final String excludehashes, final String urlhashes,
                      final String prefer, final String filter, final String language,
                      final int count, final int maxDistance, 
                      final boolean global, final int partitions, final yacySeed targetPeer, final plasmaWordIndex wordIndex,
                      final ResultURLs crawlResults,
                      final plasmaSearchRankingProcess containerCache,
                      final Map<String, TreeMap<String, String>> abstractCache,
                      final indexReferenceBlacklist blacklist,
                      final plasmaSearchRankingProfile rankingProfile,
                      final kelondroBitfield constraint) {
        super("yacySearch_" + targetPeer.getName());
        //System.out.println("DEBUG - yacySearch thread " + this.getName() + " initialized " + ((urlhashes.length() == 0) ? "(primary)" : "(secondary)"));
        this.wordhashes = wordhashes;
        this.excludehashes = excludehashes;
        this.urlhashes = urlhashes;
        this.prefer = prefer;
        this.filter = filter;
        this.language = language;
        this.global = global;
        this.partitions = partitions;
        this.wordIndex = wordIndex;
        this.crawlResults = crawlResults;
        this.containerCache = containerCache;
        this.abstractCache = abstractCache;
        this.blacklist = blacklist;
        this.targetPeer = targetPeer;
        this.urls = null;
        this.count = count;
        this.maxDistance = maxDistance;
        this.rankingProfile = rankingProfile;
        this.constraint = constraint;
    }

    public void run() {
        this.urls = yacyClient.search(
                    wordIndex.seedDB.mySeed(),
                    wordhashes, excludehashes, urlhashes, prefer, filter, language, count, maxDistance, global, partitions,
                    targetPeer, wordIndex, crawlResults, containerCache, abstractCache,
                    blacklist, rankingProfile, constraint);
        if (urls != null) {
            // urls is an array of url hashes. this is only used for log output
            final StringBuffer urllist = new StringBuffer(this.urls.length * 13);
            for (int i = 0; i < this.urls.length; i++) urllist.append(this.urls[i]).append(' ');
            yacyCore.log.logInfo("REMOTE SEARCH - remote peer " + targetPeer.hash + ":" + targetPeer.getName() + " contributed " + urls.length + " links for word hash " + wordhashes + ": " + new String(urllist));
            wordIndex.seedDB.mySeed().incRI(urls.length);
            wordIndex.seedDB.mySeed().incRU(urls.length);
        } else {
            yacyCore.log.logInfo("REMOTE SEARCH - no answer from remote peer " + targetPeer.hash + ":" + targetPeer.getName());
        }
    }

    public static String set2string(final Set<String> hashes) {
        String wh = "";
        final Iterator<String> iter = hashes.iterator();
        while (iter.hasNext()) { wh = wh + iter.next(); }
        return wh;
    }

    public int links() {
        return this.urls.length;
    }
    
    public int count() {
        return this.count;
    }
    
    public yacySeed target() {
        return targetPeer;
    }

    private static yacySeed[] selectClusterPeers(final yacySeedDB seedDB, final TreeMap<String, String> peerhashes) {
    	final Iterator<Map.Entry<String, String>> i = peerhashes.entrySet().iterator();
    	final ArrayList<yacySeed> l = new ArrayList<yacySeed>();
    	Map.Entry<String, String> entry;
    	yacySeed s;
    	while (i.hasNext()) {
    		entry = i.next();
    		s = seedDB.get(entry.getKey()); // should be getConnected; get only during testing time
    		if (s != null) {
    			s.setAlternativeAddress(entry.getValue());
    			l.add(s);
    		}
    	}
    	final yacySeed[] result = new yacySeed[l.size()];
    	for (int j = 0; j < l.size(); j++) {
    		result[j] = l.get(j);
    	}
    	return result;
    	//return (yacySeed[]) l.toArray();
    }
    
    private static yacySeed[] selectSearchTargets(final yacySeedDB seedDB, final yacyPeerActions peerActions, final Set<String> wordhashes, int seedcount) {
        // find out a specific number of seeds, that would be relevant for the given word hash(es)
        // the result is ordered by relevance: [0] is most relevant
        // the seedcount is the maximum number of wanted results
        if (seedDB == null) { return null; }
        if ((seedcount >= seedDB.sizeConnected()) || (seedDB.noDHTActivity())) {
            seedcount = seedDB.sizeConnected();
        }
        
        // put in seeds according to dht
        final kelondroMScoreCluster<String> ranking = new kelondroMScoreCluster<String>();
        final HashMap<String, yacySeed> regularSeeds = new HashMap<String, yacySeed>();
        final HashMap<String, yacySeed> matchingSeeds = new HashMap<String, yacySeed>();
        final HashMap<String, yacySeed> robinsonSeeds = new HashMap<String, yacySeed>();
        yacySeed seed;
        Iterator<yacySeed> dhtEnum;         
        int c;
        String wordhash;
        double distance;
        Iterator<String> iter = wordhashes.iterator();
        while (iter.hasNext()) {
            wordhash = iter.next();
            dhtEnum = peerActions.dhtAction.getDHTSeeds(true, wordhash, (float) 0.0);
            c = seedcount;
            while (dhtEnum.hasNext() && c > 0) {
                seed = dhtEnum.next();
                if (seed == null || seed.hash == null) continue;
                distance = yacyDHTAction.dhtDistance(seed.hash, wordhash);
                if (distance > 0.2) continue; // catch bug in peer selection
                if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
                if (serverLog.isFine("PLASMA")) serverLog.logFine("PLASMA", "selectPeers/DHTorder: " + seed.hash + ":" + seed.getName() + "/" + distance + " for wordhash " + wordhash + ", score " + c);
                ranking.addScore(seed.hash, c--);
                regularSeeds.put(seed.hash, seed);
            }
        }

        // put in seeds according to size of peer
        dhtEnum = seedDB.seedsSortedConnected(false, yacySeed.ICOUNT);
        c = Math.min(seedDB.sizeConnected(), seedcount);
        int score;
        while (dhtEnum.hasNext() && c > 0) {
            seed = dhtEnum.next();
            if (seed == null) continue;
            if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
            score = (int) Math.round(Math.random() * ((c / 3) + 3));
            if (serverLog.isFine("PLASMA")) serverLog.logFine("PLASMA", "selectPeers/RWIcount: " + seed.hash + ":" + seed.getName() + ", RWIcount=" + seed.get(yacySeed.ICOUNT,"") + ", score " + score);
            ranking.addScore(seed.hash, score);
            regularSeeds.put(seed.hash, seed);
            c--;
        }

        // put in seeds that are public robinson peers and where the peer tags match with query
        // or seeds that are newbies to ensure that public demonstrations always work
        dhtEnum = seedDB.seedsConnected(true, false, null, (float) 0.50);
        while (dhtEnum.hasNext()) {
        	seed = dhtEnum.next();
            if (seed == null) continue;
            if (seed.getFlagAcceptRemoteIndex()) {
                // enhance ranking for regular peers
                if (seed.matchPeerTags(wordhashes)) { // access robinson peers with matching tag
                    serverLog.logInfo("PLASMA", "selectPeers/PeerTags: " + seed.hash + ":" + seed.getName() + ", is specialized peer for " + seed.getPeerTags().toString());
                    regularSeeds.remove(seed.hash);
                    ranking.deleteScore(seed.hash);
                    matchingSeeds.put(seed.hash, seed);
                }
                if (seed.getAge() < 1) { // the 'workshop feature'
                    serverLog.logInfo("PLASMA", "selectPeers/Age: " + seed.hash + ":" + seed.getName() + ", is newbie, age = " + seed.getAge());
                    regularSeeds.remove(seed.hash);
                    ranking.deleteScore(seed.hash);
                    matchingSeeds.put(seed.hash, seed);
                }
            } else {
                // this is a robinson peer
                // in case the peer has more than a million urls, take it as search target
                if (seed.getLinkCount() > 1000000 || seed.matchPeerTags(wordhashes)) {
                    regularSeeds.remove(seed.hash);
                    ranking.deleteScore(seed.hash);
                    if (seed.matchPeerTags(wordhashes))
                        matchingSeeds.put(seed.hash, seed);
                    else
                        robinsonSeeds.put(seed.hash, seed);
                }
            }
        }
        
        // evaluate the ranking score and select seeds
        seedcount = Math.min(ranking.size(), seedcount);
        final yacySeed[] result = new yacySeed[seedcount + robinsonSeeds.size() + matchingSeeds.size()];
        c = 0;
        iter = ranking.scores(false); // higher are better
        while (iter.hasNext() && c < seedcount) {
            seed = regularSeeds.get(iter.next());
            seed.selectscore = c;
            serverLog.logInfo("PLASMA", "selectPeers/_dht_: " + seed.hash + ":" + seed.getName() + " is choice " + c);
            result[c++] = seed;
        }
        for (final yacySeed s: robinsonSeeds.values()) {
            serverLog.logInfo("PLASMA", "selectPeers/_robinson_: " + s.hash + ":" + s.getName() + " is choice " + c);
            result[c++] = s;
        }
        for (final yacySeed s: matchingSeeds.values()) {
            serverLog.logInfo("PLASMA", "selectPeers/_match_: " + s.hash + ":" + s.getName() + " is choice " + c);
            result[c++] = s;
        }

//      System.out.println("DEBUG yacySearch.selectPeers = " + seedcount + " seeds:"); for (int i = 0; i < seedcount; i++) System.out.println(" #" + i + ":" + result[i]); // debug
        return result;
    }

    public static yacySearch[] primaryRemoteSearches(
            final String wordhashes, final String excludehashes, final String urlhashes,
            final String prefer, final String filter, String language,
            final int count, final int maxDist,
            final plasmaWordIndex wordIndex,
            final ResultURLs crawlResults,
            final plasmaSearchRankingProcess containerCache,
            final Map<String, TreeMap<String, String>> abstractCache,
            int targets,
            final indexReferenceBlacklist blacklist,
            final plasmaSearchRankingProfile rankingProfile,
            final kelondroBitfield constraint,
            final TreeMap<String, String> clusterselection) {
        // check own peer status
        //if (wordIndex.seedDB.mySeed() == null || wordIndex.seedDB.mySeed().getPublicAddress() == null) { return null; }

        // prepare seed targets and threads
        final yacySeed[] targetPeers = (clusterselection == null) ? selectSearchTargets(wordIndex.seedDB, wordIndex.peerActions, plasmaSearchQuery.hashes2Set(wordhashes), targets) : selectClusterPeers(wordIndex.seedDB, clusterselection);
        if (targetPeers == null) return new yacySearch[0];
        targets = targetPeers.length;
        if (targets == 0) return new yacySearch[0];
        final yacySearch[] searchThreads = new yacySearch[targets];
        for (int i = 0; i < targets; i++) {
            if (targetPeers[i] == null || targetPeers[i].hash == null) continue;
            searchThreads[i] = new yacySearch(wordhashes, excludehashes, urlhashes, prefer, filter, language, count, maxDist, true, targets, targetPeers[i],
                    wordIndex, crawlResults, containerCache, abstractCache, blacklist, rankingProfile, constraint);
            searchThreads[i].start();
            //try {Thread.sleep(20);} catch (InterruptedException e) {}
        }
        return searchThreads;
    }
    
    public static yacySearch secondaryRemoteSearch(
            final String wordhashes, final String excludehashes, final String urlhashes,
            final plasmaWordIndex wordIndex,
            final ResultURLs crawlResults,
            final plasmaSearchRankingProcess containerCache,
            final String targethash, final indexReferenceBlacklist blacklist,
            final plasmaSearchRankingProfile rankingProfile,
            final kelondroBitfield constraint, final TreeMap<String, String> clusterselection) {
        // check own peer status
        if (wordIndex.seedDB.mySeed() == null || wordIndex.seedDB.mySeed().getPublicAddress() == null) { return null; }

        // prepare seed targets and threads
        final yacySeed targetPeer = wordIndex.seedDB.getConnected(targethash);
        if (targetPeer == null || targetPeer.hash == null) return null;
        if (clusterselection != null) targetPeer.setAlternativeAddress(clusterselection.get(targetPeer.hash));
        final yacySearch searchThread = new yacySearch(wordhashes, excludehashes, urlhashes, "", "", "en", 0, 9999, true, 0, targetPeer,
                                             wordIndex, crawlResults, containerCache, new TreeMap<String, TreeMap<String, String>>(), blacklist, rankingProfile, constraint);
        searchThread.start();
        return searchThread;
    }
    
    public static int remainingWaiting(final yacySearch[] searchThreads) {
        if (searchThreads == null) return 0;
        int alive = 0;
        for (int i = 0; i < searchThreads.length; i++) {
            if (searchThreads == null) break; // may occur
            if (searchThreads[i].isAlive()) alive++;
        }
        return alive;
    }
    
    public static int collectedLinks(final yacySearch[] searchThreads) {
        int links = 0;
        for (int i = 0; i < searchThreads.length; i++) {
            if (!(searchThreads[i].isAlive())) links += searchThreads[i].urls.length;
        }
        return links;
    }
    
    public static void interruptAlive(final yacySearch[] searchThreads) {
        for (int i = 0; i < searchThreads.length; i++) {
            if (searchThreads[i].isAlive()) searchThreads[i].interrupt();
        }
    }
    
}
