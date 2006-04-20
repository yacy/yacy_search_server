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

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.HashMap;

import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaURLPattern;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSearchTimingProfile;
import de.anomic.plasma.plasmaWordIndexEntryContainer;
import de.anomic.server.logging.serverLog;

public class yacySearch extends Thread {

    final private Set wordhashes;
    final private boolean global;
    final private plasmaCrawlLURL urlManager;
    final private plasmaWordIndexEntryContainer containerCache;
    final private plasmaURLPattern blacklist;
    final private plasmaSnippetCache snippetCache;
    final private yacySeed targetPeer;
    private int links;
    private int maxDistance;
    final private plasmaSearchTimingProfile timingProfile;
    final private plasmaSearchRankingProfile rankingProfile;
    final private String prefer, filter;
    
    public yacySearch(Set wordhashes, String prefer, String filter, int maxDistance, boolean global, yacySeed targetPeer,
                      plasmaCrawlLURL urlManager, plasmaWordIndexEntryContainer containerCache, plasmaURLPattern blacklist, plasmaSnippetCache snippetCache,
                      plasmaSearchTimingProfile timingProfile, plasmaSearchRankingProfile rankingProfile) {
        super("yacySearch_" + targetPeer.getName());
        this.wordhashes = wordhashes;
        this.prefer = prefer;
        this.filter = filter;
        this.global = global;
        this.urlManager = urlManager;
        this.containerCache = containerCache;
        this.blacklist = blacklist;
        this.snippetCache = snippetCache;
        this.targetPeer = targetPeer;
        this.links = -1;
        this.maxDistance = maxDistance;
        this.timingProfile = (plasmaSearchTimingProfile) timingProfile.clone();
        this.rankingProfile = rankingProfile;
    }

    public void run() {
        this.links = yacyClient.search(set2string(wordhashes), prefer, filter, maxDistance, global, targetPeer, urlManager, containerCache, blacklist, snippetCache, timingProfile, rankingProfile);
        if (links != 0) {
            //yacyCore.log.logInfo("REMOTE SEARCH - remote peer " + targetPeer.hash + ":" + targetPeer.getName() + " contributed " + links + " links for word hash " + wordhashes);
            yacyCore.seedDB.mySeed.incRI(links);
            yacyCore.seedDB.mySeed.incRU(links);
        }
    }

    public static String set2string(Set hashes) {
        String wh = "";
        final Iterator iter = hashes.iterator();
        while (iter.hasNext()) { wh = wh + (String) iter.next(); }
        return wh;
    }

    public int links() {
        return this.links;
    }
    
    public plasmaSearchTimingProfile timingProfile() {
        return this.timingProfile;
    }
    
    public yacySeed target() {
        return targetPeer;
    }
    
    private static yacySeed[] selectPeers(Set wordhashes, int seedcount) {
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
            dhtEnum = yacyCore.dhtAgent.getDHTSeeds(true, wordhash);
            c = seedcount;
            while (dhtEnum.hasMoreElements() && c > 0) {
                seed = (yacySeed) dhtEnum.nextElement();
                if (seed == null) { continue; }
                distance = yacyDHTAction.dhtDistance(seed.hash, wordhash);
                if (distance > 0.9) { continue; } // catch bug in peer selection
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
            if (seed == null) { continue; }
            score = (int) Math.round(Math.random() * ((c / 3) + 3));
            serverLog.logFine("PLASMA", "selectPeers/RWIcount: " + seed.hash + ":" + seed.getName() + ", RWIcount=" + seed.get(yacySeed.ICOUNT,"") + ", score " + score);
            ranking.addScore(seed.hash, score);
            seeds.put(seed.hash, seed);
            c--;
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

    public static yacySearch[] searchHashes(Set wordhashes, String prefer, String filter, int maxDist, plasmaCrawlLURL urlManager, plasmaWordIndexEntryContainer containerCache,
                           int targets, plasmaURLPattern blacklist, plasmaSnippetCache snippetCache,
                           plasmaSearchTimingProfile timingProfile, plasmaSearchRankingProfile rankingProfile) {
        // check own peer status
        if (yacyCore.seedDB.mySeed == null || yacyCore.seedDB.mySeed.getAddress() == null) { return null; }

        // prepare seed targets and threads
        //Set wordhashes = plasmaSearch.words2hashes(querywords);
        final yacySeed[] targetPeers = selectPeers(wordhashes, targets);
        if (targetPeers == null) return null;
        targets = targetPeers.length;
        if (targets == 0) return null;
        yacySearch[] searchThreads = new yacySearch[targets];
        for (int i = 0; i < targets; i++) {
            searchThreads[i]= new yacySearch(wordhashes, prefer, filter, maxDist, true, targetPeers[i],
                    urlManager, containerCache, blacklist, snippetCache, timingProfile, rankingProfile);
            searchThreads[i].start();
            try {Thread.sleep(20);} catch (InterruptedException e) {}

        }
        return searchThreads;
    }
    
    public static int remainingWaiting(yacySearch[] searchThreads) {
        int alive = 0;
        for (int i = 0; i < searchThreads.length; i++) {
            if (searchThreads[i].isAlive()) alive++;
        }
        return alive;
    }
    
    public static int collectedLinks(yacySearch[] searchThreads) {
        int links = 0;
        for (int i = 0; i < searchThreads.length; i++) {
            if (!(searchThreads[i].isAlive())) links += searchThreads[i].links;
        }
        return links;
    }
    
    public static void interruptAlive(yacySearch[] searchThreads) {
        for (int i = 0; i < searchThreads.length; i++) {
            if (searchThreads[i].isAlive()) searchThreads[i].interrupt();
        }
    }
    
}
