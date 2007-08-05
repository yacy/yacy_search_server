// yacySearch.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.index.indexContainer;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchProcessing;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.logging.serverLog;

public class yacySearch extends Thread {

    final private String wordhashes, excludehashes, urlhashes;
    final private boolean global;
    final private int partitions;
    final private plasmaCrawlLURL urlManager;
    final private plasmaWordIndex wordIndex;
    final private indexContainer containerCache;
    final private Map abstractCache;
    final private plasmaURLPattern blacklist;
    final private plasmaSnippetCache snippetCache;
    final private yacySeed targetPeer;
    private String[] urls;
    private int maxDistance;
    final private plasmaSearchProcessing timingProfile;
    final private plasmaSearchRankingProfile rankingProfile;
    final private String prefer, filter;
    final private kelondroBitfield constraint;
    
    public yacySearch(String wordhashes, String excludehashes, String urlhashes, String prefer, String filter, int maxDistance, 
                      boolean global, int partitions, yacySeed targetPeer, plasmaCrawlLURL urlManager, plasmaWordIndex wordIndex,
                      indexContainer containerCache, Map abstractCache,
                      plasmaURLPattern blacklist, plasmaSnippetCache snippetCache,
                      plasmaSearchProcessing timingProfile, plasmaSearchRankingProfile rankingProfile,
                      kelondroBitfield constraint) {
        super("yacySearch_" + targetPeer.getName());
        //System.out.println("DEBUG - yacySearch thread " + this.getName() + " initialized " + ((urlhashes.length() == 0) ? "(primary)" : "(secondary)"));
        this.wordhashes = wordhashes;
        this.excludehashes = excludehashes;
        this.urlhashes = urlhashes;
        this.prefer = prefer;
        this.filter = filter;
        this.global = global;
        this.partitions = partitions;
        this.urlManager = urlManager;
        this.wordIndex = wordIndex;
        this.containerCache = containerCache;
        this.abstractCache = abstractCache;
        this.blacklist = blacklist;
        this.snippetCache = snippetCache;
        this.targetPeer = targetPeer;
        this.urls = null;
        this.maxDistance = maxDistance;
        this.timingProfile = (plasmaSearchProcessing) timingProfile.clone();
        this.rankingProfile = rankingProfile;
        this.constraint = constraint;
    }

    public void run() {
        this.urls = yacyClient.search(
                    wordhashes, excludehashes, urlhashes, prefer, filter, maxDistance, global, partitions,
                    targetPeer, urlManager, wordIndex, containerCache, abstractCache,
                    blacklist, snippetCache, timingProfile, rankingProfile, constraint);
        if (urls != null) {
            StringBuffer urllist = new StringBuffer(this.urls.length * 13);
            for (int i = 0; i < this.urls.length; i++) urllist.append(this.urls[i]).append(' ');
            yacyCore.log.logInfo("REMOTE SEARCH - remote peer " + targetPeer.hash + ":" + targetPeer.getName() + " contributed " + urls.length + " links for word hash " + wordhashes + ": " + new String(urllist));
            yacyCore.seedDB.mySeed.incRI(urls.length);
            yacyCore.seedDB.mySeed.incRU(urls.length);
        } else {
            yacyCore.log.logInfo("REMOTE SEARCH - no answer from remote peer " + targetPeer.hash + ":" + targetPeer.getName());
        }
    }

    public static String set2string(Set hashes) {
        String wh = "";
        final Iterator iter = hashes.iterator();
        while (iter.hasNext()) { wh = wh + (String) iter.next(); }
        return wh;
    }

    public int links() {
        return this.urls.length;
    }
    
    public plasmaSearchProcessing timingProfile() {
        return this.timingProfile;
    }
    
    public yacySeed target() {
        return targetPeer;
    }

    private static yacySeed[] selectClusterPeers(TreeMap peerhashes) {
    	Iterator i = peerhashes.entrySet().iterator();
    	ArrayList l = new ArrayList();
    	Map.Entry entry;
    	yacySeed s;
    	while (i.hasNext()) {
    		entry = (Map.Entry) i.next();
    		s = yacyCore.seedDB.get((String) entry.getKey()); // should be getConnected; get only during testing time
    		if (s != null) {
    			s.setAlternativeAddress((String) entry.getValue());
    			l.add(s);
    		}
    	}
    	yacySeed[] result = new yacySeed[l.size()];
    	for (int j = 0; j < l.size(); j++) {
    		result[j] = (yacySeed) l.get(j);
    	}
    	return result;
    	//return (yacySeed[]) l.toArray();
    }
    
    private static yacySeed[] selectDHTPeers(Set wordhashes, int seedcount) {
        // find out a specific number of seeds, that would be relevant for the given word hash(es)
        // the result is ordered by relevance: [0] is most relevant
        // the seedcount is the maximum number of wanted results
        if (yacyCore.seedDB == null) { return null; }
        if (seedcount > yacyCore.seedDB.sizeConnected()) { seedcount = yacyCore.seedDB.sizeConnected(); }

        // put in seeds according to dht
        final kelondroMScoreCluster ranking = new kelondroMScoreCluster();
        final HashMap seeds = new HashMap();
        yacySeed seed;
        Enumeration dhtEnum;         
        int c;
        String wordhash;
        double distance;
        Iterator iter = wordhashes.iterator();
        while (iter.hasNext()) {
            wordhash = (String) iter.next();
            dhtEnum = yacyCore.dhtAgent.getDHTSeeds(true, wordhash, (float) 0.0);
            c = seedcount;
            while (dhtEnum.hasMoreElements() && c > 0) {
                seed = (yacySeed) dhtEnum.nextElement();
                if (seed == null) continue;
                distance = yacyDHTAction.dhtDistance(seed.hash, wordhash);
                if (distance > 0.2) continue; // catch bug in peer selection
                if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
                serverLog.logFine("PLASMA", "selectPeers/DHTorder: " + seed.hash + ":" + seed.getName() + "/" + distance + " for wordhash " + wordhash + ", score " + c);
                ranking.addScore(seed.hash, c--);
                seeds.put(seed.hash, seed);
            }
        }

        // put in seeds according to size of peer
        dhtEnum = yacyCore.seedDB.seedsSortedConnected(false, yacySeed.ICOUNT);
        c = seedcount;
        int score;
        if (c > yacyCore.seedDB.sizeConnected()) { c = yacyCore.seedDB.sizeConnected(); }
        while (dhtEnum.hasMoreElements() && c > 0) {
            seed = (yacySeed) dhtEnum.nextElement();
            if (seed == null) continue;
            if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
            score = (int) Math.round(Math.random() * ((c / 3) + 3));
            serverLog.logFine("PLASMA", "selectPeers/RWIcount: " + seed.hash + ":" + seed.getName() + ", RWIcount=" + seed.get(yacySeed.ICOUNT,"") + ", score " + score);
            ranking.addScore(seed.hash, score);
            seeds.put(seed.hash, seed);
            c--;
        }

        // put in seeds that are public robinson peers and where the peer tags match with query
        // or seeds that are newbies to ensure that public demonstrations always work
        dhtEnum = yacyCore.seedDB.seedsConnected(true, false, null, (float) 0.50);
        while (dhtEnum.hasMoreElements()) {
        	seed = (yacySeed) dhtEnum.nextElement();
            if (seed == null) continue;
            if (seed.matchPeerTags(wordhashes)) { // access robinson peers with matching tag
            	serverLog.logInfo("PLASMA", "selectPeers/PeerTags: " + seed.hash + ":" + seed.getName() + ", is specialized peer for " + seed.getPeerTags().toString());
            	ranking.addScore(seed.hash, seedcount);
            	seeds.put(seed.hash, seed);
            }
            if (seed.getAge() < 1) { // the 'workshop feature'
            	serverLog.logInfo("PLASMA", "selectPeers/Age: " + seed.hash + ":" + seed.getName() + ", is newbie, age = " + seed.getAge());
            	ranking.addScore(seed.hash, seedcount);
            	seeds.put(seed.hash, seed);
            }
        }
        
        // evaluate the ranking score and select seeds
        if (ranking.size() < seedcount) { seedcount = ranking.size(); }
        yacySeed[] result = new yacySeed[seedcount];
        c = 0;
        iter = ranking.scores(false); // higher are better
        while (iter.hasNext() && c < result.length) {
            seed = (yacySeed) seeds.get((String) iter.next());
            seed.selectscore = c;
            serverLog.logFine("PLASMA", "selectPeers/_lineup_: " + seed.hash + ":" + seed.getName() + " is choice " + c);
            result[c++] = seed;
        }

//      System.out.println("DEBUG yacySearch.selectPeers = " + seedcount + " seeds:"); for (int i = 0; i < seedcount; i++) System.out.println(" #" + i + ":" + result[i]); // debug
        return result;
    }

    public static yacySearch[] primaryRemoteSearches(String wordhashes, String excludehashes, String urlhashes, String prefer, String filter, int maxDist,
                           plasmaCrawlLURL urlManager, plasmaWordIndex wordIndex,
                           indexContainer containerCache, Map abstractCache,
                           int targets, plasmaURLPattern blacklist, plasmaSnippetCache snippetCache,
                           plasmaSearchProcessing timingProfile, plasmaSearchRankingProfile rankingProfile,
                           kelondroBitfield constraint, TreeMap clusterselection) {
        // check own peer status
        if (yacyCore.seedDB.mySeed == null || yacyCore.seedDB.mySeed.getPublicAddress() == null) { return null; }

        // prepare seed targets and threads
        final yacySeed[] targetPeers = (clusterselection == null) ? selectDHTPeers(plasmaSearchQuery.hashes2Set(wordhashes), targets) : selectClusterPeers(clusterselection);
        if (targetPeers == null) return new yacySearch[0];
        targets = targetPeers.length;
        if (targets == 0) return new yacySearch[0];
        yacySearch[] searchThreads = new yacySearch[targets];
        for (int i = 0; i < targets; i++) {
            searchThreads[i]= new yacySearch(wordhashes, excludehashes, urlhashes, prefer, filter, maxDist, true, targets, targetPeers[i],
                    urlManager, wordIndex, containerCache, abstractCache, blacklist, snippetCache, timingProfile, rankingProfile, constraint);
            searchThreads[i].start();
            //try {Thread.sleep(20);} catch (InterruptedException e) {}
        }
        return searchThreads;
    }
    
    public static yacySearch secondaryRemoteSearch(String wordhashes, String excludehashes, String urlhashes,
            plasmaCrawlLURL urlManager, plasmaWordIndex wordIndex,
            indexContainer containerCache,
            String targethash, plasmaURLPattern blacklist, plasmaSnippetCache snippetCache,
            plasmaSearchProcessing timingProfile, plasmaSearchRankingProfile rankingProfile,
            kelondroBitfield constraint, TreeMap clusterselection) {
        // check own peer status
        if (yacyCore.seedDB.mySeed == null || yacyCore.seedDB.mySeed.getPublicAddress() == null) { return null; }

        // prepare seed targets and threads
        final yacySeed targetPeer = yacyCore.seedDB.getConnected(targethash);
        if (targetPeer == null) return null;
        if (clusterselection != null) targetPeer.setAlternativeAddress((String) clusterselection.get(targetPeer.hash));
        yacySearch searchThread = new yacySearch(wordhashes, excludehashes, urlhashes, "", "", 9999, true, 0, targetPeer,
                                             urlManager, wordIndex, containerCache, new TreeMap(), blacklist, snippetCache, timingProfile, rankingProfile, constraint);
        searchThread.start();
        return searchThread;
    }
    
    public static int remainingWaiting(yacySearch[] searchThreads) {
        if (searchThreads == null) return 0;
        int alive = 0;
        for (int i = 0; i < searchThreads.length; i++) {
            if (searchThreads == null) break; // may occur
            if (searchThreads[i].isAlive()) alive++;
        }
        return alive;
    }
    
    public static int collectedLinks(yacySearch[] searchThreads) {
        int links = 0;
        for (int i = 0; i < searchThreads.length; i++) {
            if (!(searchThreads[i].isAlive())) links += searchThreads[i].urls.length;
        }
        return links;
    }
    
    public static void interruptAlive(yacySearch[] searchThreads) {
        for (int i = 0; i < searchThreads.length; i++) {
            if (searchThreads[i].isAlive()) searchThreads[i].interrupt();
        }
    }
    
}
