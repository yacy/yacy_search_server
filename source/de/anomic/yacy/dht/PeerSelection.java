// PeerSelection.java 
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

package de.anomic.yacy.dht;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Digest;

import de.anomic.kelondro.util.DateFormatter;
import de.anomic.kelondro.util.kelondroException;
import de.anomic.kelondro.util.ScoreCluster;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyVersion;


/*
 * this package is a collection of peer selection iterations that had been
 * part of yacyPeerActions, yacyDHTActions and yacySeedDB
 */

public class PeerSelection {
    
    public static void selectDHTPositions(
            final yacySeedDB seedDB, 
            byte[] wordhash,
            int redundancy, 
            HashMap<String, yacySeed> regularSeeds,
            ScoreCluster<String> ranking) {
        // this method is called from the search target computation
        long[] dhtVerticalTargets = seedDB.scheme.dhtPositions(wordhash);
        yacySeed seed;
        for (int v = 0; v < dhtVerticalTargets.length; v++) {
            wordhash = FlatWordPartitionScheme.positionToHash(dhtVerticalTargets[v]);
            Iterator<yacySeed> dhtEnum = getAcceptRemoteIndexSeeds(seedDB, wordhash, redundancy, false);
            int c = Math.min(seedDB.sizeConnected(), redundancy);
            int cc = 3; // select a maximum of 3, this is enough redundancy
            while (dhtEnum.hasNext() && c > 0 && cc-- > 0) {
                seed = dhtEnum.next();
                if (seed == null || seed.hash == null) continue;
                if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
                if (Log.isFine("PLASMA")) Log.logFine("PLASMA", "selectPeers/DHTorder: " + seed.hash + ":" + seed.getName() + "/ score " + c);
                ranking.addScore(seed.hash, 2 * c);
                regularSeeds.put(seed.hash, seed);
                c--;
            }
        }
    }
    
    private static int guessedOwn = 0;
    //private static int guessedNotOwn = 0;
    private static int verifiedOwn = 0;
    private static int verifiedNotOwn = 0;
    
    public static boolean shallBeOwnWord(final yacySeedDB seedDB, final byte[] wordhash, String urlhash, int redundancy) {
        // the guessIfOwnWord is a fast method that should only fail in case that a 'true' may be incorrect, but a 'false' shall always be correct
        if (guessIfOwnWord(seedDB, wordhash, urlhash)) {
            // this case must be verified, because it can be wrong.
            guessedOwn++;
            if (verifyIfOwnWord(seedDB, wordhash, urlhash, redundancy)) {
                // this is the correct case, but does not need to be an average case
                verifiedOwn++;
                //System.out.println("*** DEBUG shallBeOwnWord: true. guessed: true. verified/guessed ration = " + verifiedOwn + "/" + guessedOwn);
                return true;
            } else {
                // this may happen, but can be corrected
                verifiedNotOwn++;
                //System.out.println("*** DEBUG shallBeOwnWord: false. guessed: true. verified/guessed ration = " + verifiedNotOwn + "/" + guessedNotOwn);
                return false;
            }
        } else {
            return false;
            /*
            // this should mean that the guessing should not be wrong
            guessedNotOwn++;
            if (yacyPeerSelection.verifyIfOwnWord(seedDB, wordhash, redundancy)) {
                // this should never happen
                verifiedOwn++;
                System.out.println("*** DEBUG shallBeOwnWord: true. guessed: false. verified/guessed ration = " + verifiedOwn + "/" + guessedOwn);
                return true;
            } else {
                // this should always happen
                verifiedNotOwn++;
                //System.out.println("*** DEBUG shallBeOwnWord: false. guessed: false. verified/guessed ration = " + verifiedNotOwn + "/" + guessedNotOwn);
                return false;
            }
            */
        }
        
    }
    
    public static boolean guessIfOwnWord(final yacySeedDB seedDB, final byte[] wordhash, final String urlhash) {
        if (seedDB == null) return false;
        int connected = seedDB.sizeConnected();
        if (connected == 0) return true;
        final long target = seedDB.scheme.dhtPosition(wordhash, urlhash);
        final long mypos = seedDB.scheme.dhtPosition(seedDB.mySeed().hash.getBytes(), urlhash);
        long distance = FlatWordPartitionScheme.dhtDistance(target, mypos);
        if (distance <= 0) return false;
        if (distance <= Long.MAX_VALUE / connected * 2) return true;
        return false;
    }
    
    public static boolean verifyIfOwnWord(final yacySeedDB seedDB, byte[] wordhash, String urlhash, int redundancy) {
        String myHash = seedDB.mySeed().hash;
        wordhash = FlatWordPartitionScheme.positionToHash(seedDB.scheme.dhtPosition(wordhash, urlhash));
        Iterator<yacySeed> dhtEnum = getAcceptRemoteIndexSeeds(seedDB, wordhash, redundancy, true);
        while (dhtEnum.hasNext()) {
            if (dhtEnum.next().hash.equals(myHash)) return true;
        }
        return false;
    }
    
    public static byte[] selectTransferStart() {
        return Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(Long.toString(System.currentTimeMillis()))).substring(2, 2 + yacySeedDB.commonHashLength).getBytes();
    }
    
    public static byte[] limitOver(final yacySeedDB seedDB, final byte[] startHash) {
        Iterator<yacySeed> seeds = getAcceptRemoteIndexSeeds(seedDB, startHash, 1, false);
        if (seeds.hasNext()) return seeds.next().hash.getBytes();
        return null;
    }

    public static ArrayList<yacySeed> getAcceptRemoteIndexSeedsList(
            yacySeedDB seedDB,
            final byte[] starthash,
            int max,
            boolean alsoMyOwn) {
        final Iterator<yacySeed> seedIter = PeerSelection.getAcceptRemoteIndexSeeds(seedDB, starthash, max, alsoMyOwn);
        ArrayList<yacySeed> targets = new ArrayList<yacySeed>();
        while (seedIter.hasNext() && max-- > 0) targets.add(seedIter.next());
        return targets;
    }
    
    /**
     * returns an enumeration of yacySeed-Objects that have the AcceptRemoteIndex-Flag set
     * the seeds are enumerated in the right order according to the DHT
     * @param seedDB
     * @param starthash
     * @param max
     * @param alsoMyOwn
     * @return
     */
    public static Iterator<yacySeed> getAcceptRemoteIndexSeeds(yacySeedDB seedDB, final byte[] starthash, int max, boolean alsoMyOwn) {
        return new acceptRemoteIndexSeedEnum(seedDB, starthash, Math.min(max, seedDB.sizeConnected()), alsoMyOwn);
    }
    
    private static class acceptRemoteIndexSeedEnum implements Iterator<yacySeed> {

        private Iterator<yacySeed> se;
        private yacySeed nextSeed;
        private yacySeedDB seedDB;
        private HashSet<String> doublecheck;
        private int remaining;
        private boolean alsoMyOwn;
        
        public acceptRemoteIndexSeedEnum(yacySeedDB seedDB, final byte[] starthash, int max, boolean alsoMyOwn) {
            this.seedDB = seedDB;
            this.se = getDHTSeeds(seedDB, starthash, yacyVersion.YACY_HANDLES_COLLECTION_INDEX);
            this.remaining = max;
            this.doublecheck = new HashSet<String>();
            this.nextSeed = nextInternal();
            this.alsoMyOwn = alsoMyOwn && nextSeed != null && (Base64Order.enhancedCoder.compare(seedDB.mySeed().hash.getBytes(), nextSeed.hash.getBytes()) > 0);
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
            if (alsoMyOwn && Base64Order.enhancedCoder.compare(seedDB.mySeed().hash.getBytes(), nextSeed.hash.getBytes()) < 0) {
                // take my own seed hash instead the enumeration result
                alsoMyOwn = false;
                return seedDB.mySeed();
            } else {
                final yacySeed next = nextSeed;
                nextSeed = nextInternal();
                return next;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
    
    /**
     * enumerate seeds for DHT target positions
     * @param seedDB
     * @param firstHash
     * @param minVersion
     * @return
     */
    protected static Iterator<yacySeed> getDHTSeeds(yacySeedDB seedDB, final byte[] firstHash, final float minVersion) {
        // enumerates seed-type objects: all seeds with starting point in the middle, rotating at the end/beginning
        return new seedDHTEnum(seedDB, firstHash, minVersion);
    }

    private static class seedDHTEnum implements Iterator<yacySeed> {

        Iterator<yacySeed> e1, e2;
        int steps;
        float minVersion;
        yacySeedDB seedDB;
        
        public seedDHTEnum(yacySeedDB seedDB, final byte[] firstHash, final float minVersion) {
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
    
    /**
     * enumerate peers that provide remote crawl urls
     * @param seedDB
     * @return an iterator of seed objects
     */
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

    /**
     * get either the youngest or oldest peers from the seed db. Count as many as requested
     * @param seedDB
     * @param up if up = true then get the most recent peers, if up = false then get oldest
     * @param count number of wanted peers
     * @return a hash map of peer hashes to seed object
     */
    public static HashMap<String, yacySeed> seedsByAge(yacySeedDB seedDB, final boolean up, int count) {
        
        if (count > seedDB.sizeConnected()) count = seedDB.sizeConnected();

        // fill a score object
        final ScoreCluster<String> seedScore = new ScoreCluster<String>();
        yacySeed ys;
        long absage;
        final Iterator<yacySeed> s = seedDB.seedsConnected(true, false, null, (float) 0.0);
        int searchcount = 1000;
        if (searchcount > seedDB.sizeConnected()) searchcount = seedDB.sizeConnected();
        try {
            while ((s.hasNext()) && (searchcount-- > 0)) {
                ys = s.next();
                if ((ys != null) && (ys.get(yacySeed.LASTSEEN, "").length() > 10)) try {
                    absage = Math.abs(System.currentTimeMillis() + DateFormatter.dayMillis - ys.getLastSeenUTC());
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
