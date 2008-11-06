// yacyPeerSelection.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published 05.11.2008 on http://yacy.net
// Frankfurt, Germany, 2008
//
// $LastChangedDate: 2008-09-03 02:30:21 +0200 (Mi, 03 Sep 2008) $
// $LastChangedRevision: 5102 $
// $LastChangedBy: orbiter $
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.serverDate;
import de.anomic.server.logging.serverLog;


/*
 * this package is a collection of peer selection iterations that had been
 * part of yacyPeerActions, yacyDHTActions and yacySeedDB
 */

public class yacyPeerSelection {
    
    public static void selectDHTPositions(final yacySeedDB seedDB, String wordhash, int redundancy, HashMap<String, yacySeed> regularSeeds, kelondroMScoreCluster<String> ranking) {
        // this method is called from the search target computation
        long[] dhtVerticalTargets = yacySeed.dhtPositions(wordhash, yacySeed.partitionExponent);
        yacySeed seed;
        long distance;
        for (int v = 0; v < dhtVerticalTargets.length; v++) {
            wordhash = yacySeed.positionToHash(dhtVerticalTargets[v]);
            Iterator<yacySeed> dhtEnum = getAcceptRemoteIndexSeeds(seedDB, wordhash, redundancy);
            int c = Math.min(seedDB.sizeConnected(), redundancy);
            int cc = 3; // select a maximum of 3, this is enough redundancy
            while (dhtEnum.hasNext() && c > 0 && cc-- > 0) {
                seed = dhtEnum.next();
                if (seed == null || seed.hash == null) continue;
                distance = yacySeed.dhtDistance(wordhash, seed);
                if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
                if (serverLog.isFine("PLASMA")) serverLog.logFine("PLASMA", "selectPeers/DHTorder: " + seed.hash + ":" + seed.getName() + "/" + distance + " for wordhash " + wordhash + ", score " + c);
                ranking.addScore(seed.hash, 2 * c);
                regularSeeds.put(seed.hash, seed);
                c--;
            }
        }
    }
    
    public static boolean verifyIfOwnWord(final yacySeedDB seedDB, final String wordhash, int redundancy) {
        String myHash = seedDB.mySeed().hash;
        long[] dhtVerticalTargets = yacySeed.dhtPositions(wordhash, yacySeed.partitionExponent);
        for (int v = 0; v < dhtVerticalTargets.length; v++) {
            Iterator<yacySeed> dhtEnum = getAcceptRemoteIndexSeeds(seedDB, yacySeed.positionToHash(dhtVerticalTargets[v]), redundancy);
            while (dhtEnum.hasNext()) {
                if (dhtEnum.next().equals(myHash)) return true;
            }
        }
        return false;
    }
    
    public static Iterator<yacySeed> getAcceptRemoteIndexSeeds(yacySeedDB seedDB, final String starthash, int max) {
        // returns an enumeration of yacySeed-Objects
        // that have the AcceptRemoteIndex-Flag set
        // the seeds are enumerated in the right order according DHT
        return new acceptRemoteIndexSeedEnum(seedDB, starthash, Math.max(max, seedDB.sizeConnected()));
    }
    
    private static class acceptRemoteIndexSeedEnum implements Iterator<yacySeed> {

        private Iterator<yacySeed> se;
        private yacySeed nextSeed;
        private yacySeedDB seedDB;
        private HashSet<String> doublecheck;
        private int remaining;
        
        public acceptRemoteIndexSeedEnum(yacySeedDB seedDB, final String starthash, int max) {
            this.seedDB = seedDB;
            this.se = getDHTSeeds(seedDB, starthash, yacyVersion.YACY_HANDLES_COLLECTION_INDEX);
            this.nextSeed = nextInternal();
            this.doublecheck = new HashSet<String>();
            this.remaining = max;
        }
        
        public boolean hasNext() {
            return nextSeed != null;
        }

        private yacySeed nextInternal() {
            if (this.remaining <= 0) return null;
            yacySeed s;
            try {
                while (se.hasNext()) {
                    s = se.next();
                    if (s == null) return null;
                    if (doublecheck.contains(s.hash)) return null;
                    this.doublecheck.add(s.hash);
                    if (s.getFlagAcceptRemoteIndex()) {
                        this.remaining--;
                        return s;
                    }
                }
            } catch (final kelondroException e) {
                System.out.println("DEBUG acceptRemoteIndexSeedEnum:" + e.getMessage());
                yacyCore.log.logSevere("database inconsistency (" + e.getMessage() + "), re-set of db.");
                seedDB.resetActiveTable();
                return null;
            }
            return null;
        }
        
        public yacySeed next() {
            final yacySeed next = nextSeed;
            nextSeed = nextInternal();
            return next;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
    
    public static Iterator<yacySeed> getDHTSeeds(yacySeedDB seedDB, final String firstHash, final float minVersion) {
        // enumerates seed-type objects: all seeds with starting point in the middle, rotating at the end/beginning
        return new seedDHTEnum(seedDB, firstHash, minVersion);
    }

    private static class seedDHTEnum implements Iterator<yacySeed> {

        Iterator<yacySeed> e1, e2;
        int steps;
        float minVersion;
        yacySeedDB seedDB;
        
        public seedDHTEnum(yacySeedDB seedDB, final String firstHash, final float minVersion) {
            this.seedDB = seedDB;
            this.steps = seedDB.sizeConnected();
            this.minVersion = minVersion;
            this.e1 = seedDB.seedsConnected(true, false, firstHash, minVersion);
            this.e2 = null;
        }
        
        public boolean hasNext() {
            return (steps > 0) && ((e2 == null) || (e2.hasNext()));
        }

        public yacySeed next() {
            if (steps == 0) return null;
            steps--;
            
            if (e1 == null || !e1.hasNext()) {
                if (e2 == null) {
                    e1 = null;
                    e2 = seedDB.seedsConnected(true, false, null, minVersion);
                }
                return e2.next();
            }
            
            final yacySeed n = e1.next();
            if (!(e1.hasNext())) {
                e1 = null;
                e2 = seedDB.seedsConnected(true, false, null, minVersion);
            }
            return n;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    
    public static Iterator<yacySeed> getProvidesRemoteCrawlURLs(yacySeedDB seedDB) {
        return new providesRemoteCrawlURLsEnum(seedDB);
    }
    
    private static class providesRemoteCrawlURLsEnum implements Iterator<yacySeed> {

        Iterator<yacySeed> se;
        yacySeed nextSeed;
        yacySeedDB seedDB;
        
        public providesRemoteCrawlURLsEnum(yacySeedDB seedDB) {
            this.seedDB = seedDB;
            se = getDHTSeeds(seedDB, null, yacyVersion.YACY_POVIDES_REMOTECRAWL_LISTS);
            nextSeed = nextInternal();
        }
        
        public boolean hasNext() {
            return nextSeed != null;
        }

        private yacySeed nextInternal() {
            yacySeed s;
            try {
                while (se.hasNext()) {
                    s = se.next();
                    if (s == null) return null;
                    if (s.getLong(yacySeed.RCOUNT, 0) > 0) return s;
                }
            } catch (final kelondroException e) {
                System.out.println("DEBUG providesRemoteCrawlURLsEnum:" + e.getMessage());
                yacyCore.log.logSevere("database inconsistency (" + e.getMessage() + "), re-set of db.");
                seedDB.resetActiveTable();
                return null;
            }
            return null;
        }
        
        public yacySeed next() {
            final yacySeed next = nextSeed;
            nextSeed = nextInternal();
            return next;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    public static HashMap<String, yacySeed> seedsByAge(yacySeedDB seedDB, final boolean up, int count) {
        // returns a peerhash/yacySeed relation
        // to get most recent peers, set up = true; for oldest peers, set up = false
        
        if (count > seedDB.sizeConnected()) count = seedDB.sizeConnected();

        // fill a score object
        final kelondroMScoreCluster<String> seedScore = new kelondroMScoreCluster<String>();
        yacySeed ys;
        long absage;
        final Iterator<yacySeed> s = seedDB.seedsConnected(true, false, null, (float) 0.0);
        int searchcount = 1000;
        if (searchcount > seedDB.sizeConnected()) searchcount = seedDB.sizeConnected();
        try {
            while ((s.hasNext()) && (searchcount-- > 0)) {
                ys = s.next();
                if ((ys != null) && (ys.get(yacySeed.LASTSEEN, "").length() > 10)) try {
                    absage = Math.abs(System.currentTimeMillis() + serverDate.dayMillis - ys.getLastSeenUTC());
                    seedScore.addScore(ys.hash, (int) absage); // the higher absage, the older is the peer
                } catch (final Exception e) {}
            }
            
            // result is now in the score object; create a result vector
            final HashMap<String, yacySeed> result = new HashMap<String, yacySeed>();
            final Iterator<String> it = seedScore.scores(up);
            int c = 0;
            while ((c < count) && (it.hasNext())) {
                c++;
                ys = seedDB.getConnected(it.next());
                if ((ys != null) && (ys.hash != null)) result.put(ys.hash, ys);
            }
            return result;
        } catch (final kelondroException e) {
            yacyCore.log.logSevere("Internal Error at yacySeedDB.seedsByAge: " + e.getMessage(), e);
            return null;
        }
    }
}
