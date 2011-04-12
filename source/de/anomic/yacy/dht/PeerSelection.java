// PeerSelection.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published 05.11.2008 on http://yacy.net
// Frankfurt, Germany, 2008
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

package de.anomic.yacy.dht;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.storage.ConcurrentScoreMap;
import net.yacy.cora.storage.ScoreMap;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.util.kelondroException;

import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyVersion;


/*
 * this package is a collection of peer selection iterations that had been
 * part of yacyPeerActions, yacyDHTActions and yacySeedDB
 */

public class PeerSelection {

    public static yacySeed[] selectClusterPeers(final yacySeedDB seedDB, final SortedMap<byte[], String> peerhashes) {
        final Iterator<Map.Entry<byte[], String>> i = peerhashes.entrySet().iterator();
        final List<yacySeed> l = new ArrayList<yacySeed>();
        Map.Entry<byte[], String> entry;
        yacySeed s;
        while (i.hasNext()) {
            entry = i.next();
            s = seedDB.get(UTF8.String(entry.getKey())); // should be getConnected; get only during testing time
            if (s != null) {
                s.setAlternativeAddress(entry.getValue());
                l.add(s);
            }
        }
        return l.toArray(new yacySeed[l.size()]);
    }

    public static yacySeed[] selectSearchTargets(
            final yacySeedDB seedDB,
            final HandleSet wordhashes,
            int redundancy,
            int burstRobinsonPercent,
            int burstMultiwordPercent) {
        // find out a specific number of seeds, that would be relevant for the given word hash(es)
        // the result is ordered by relevance: [0] is most relevant
        // the seedcount is the maximum number of wanted results
        if (seedDB == null) { return null; }
        
        // put in seeds according to dht
        final Map<String, yacySeed> regularSeeds = new HashMap<String, yacySeed>(); // dht position seeds
        yacySeed seed;
        Iterator<yacySeed> dhtEnum;         
        Iterator<byte[]> iter = wordhashes.iterator();
        while (iter.hasNext()) {
            selectDHTPositions(seedDB, iter.next(), redundancy, regularSeeds);
        }
        //int minimumseeds = Math.min(seedDB.scheme.verticalPartitions(), regularSeeds.size()); // that should be the minimum number of seeds that are returned
        //int maximumseeds = seedDB.scheme.verticalPartitions() * redundancy; // this is the maximum number of seeds according to dht and heuristics. It can be more using burst mode.
        
        // put in some seeds according to size of peer.
        // But not all, that would produce too much load on the largest peers
        dhtEnum = seedDB.seedsSortedConnected(false, yacySeed.ICOUNT);
        int c = Math.max(Math.min(5, seedDB.sizeConnected()), wordhashes.size() > 1 ? seedDB.sizeConnected() * burstMultiwordPercent / 100 : 0);
        while (dhtEnum.hasNext() && c-- > 0) {
            seed = dhtEnum.next();
            if (seed == null) continue;
            if (seed.isLastSeenTimeout(3600000)) continue;
            if (seed.getAge() < 1) { // the 'workshop feature'
                Log.logInfo("DHT", "selectPeers/Age: " + seed.hash + ":" + seed.getName() + ", is newbie, age = " + seed.getAge());
                regularSeeds.put(seed.hash, seed);
                continue;
            }
            if (Math.random() * 100 + (wordhashes.size() > 1 ? burstMultiwordPercent : 25) >= 50) {
                if (Log.isFine("DHT")) Log.logFine("DHT", "selectPeers/CountBurst: " + seed.hash + ":" + seed.getName() + ", RWIcount=" + seed.getWordCount());
                regularSeeds.put(seed.hash, seed);
                continue;
            }
        }

        // create a set that contains only robinson peers because these get a special handling
        dhtEnum = seedDB.seedsConnected(true, false, null, 0.50f);
        Set<yacySeed> robinson = new HashSet<yacySeed>();
        while (dhtEnum.hasNext()) {
            seed = dhtEnum.next();
            if (seed == null) continue;
            if (seed.getFlagAcceptRemoteIndex()) continue;
            if (seed.isLastSeenTimeout(3600000)) continue;
            robinson.add(seed);
        }

        // add robinson peers according to robinson burst rate
        dhtEnum = robinson.iterator();
        c = robinson.size() * burstRobinsonPercent / 100;
        while (dhtEnum.hasNext() && c-- > 0) {
            seed = dhtEnum.next();
            if (seed == null) continue;
            if (seed.isLastSeenTimeout(3600000)) continue;
            if (Math.random() * 100 + burstRobinsonPercent >= 100) {
                if (Log.isFine("DHT")) Log.logFine("DHT", "selectPeers/RobinsonBurst: " + seed.hash + ":" + seed.getName());
                regularSeeds.put(seed.hash, seed);
                continue;
            }
        }

        // put in seeds that are public robinson peers and where the peer tags match with query
        // or seeds that are newbies to ensure that private demonstrations always work
        dhtEnum = robinson.iterator();
        while (dhtEnum.hasNext()) {
            seed = dhtEnum.next();
            if (seed == null) continue;
            if (seed.isLastSeenTimeout(3600000)) continue;
            if (seed.matchPeerTags(wordhashes)) {
                // peer tags match
                String specialized = seed.getPeerTags().toString();
                if (specialized.equals("[*]")) {
                    Log.logInfo("DHT", "selectPeers/RobinsonTag: " + seed.hash + ":" + seed.getName() + " grants search for all");
                } else {
                    Log.logInfo("DHT", "selectPeers/RobinsonTag " + seed.hash + ":" + seed.getName() + " is specialized peer for " + specialized);
                }
                regularSeeds.put(seed.hash, seed);
            }
        }
        
        // produce return set
        yacySeed[] result = new yacySeed[regularSeeds.size()];
        result = regularSeeds.values().toArray(result);
        return result;
    }

    private static void selectDHTPositions(
            final yacySeedDB seedDB, 
            byte[] wordhash,
            int redundancy, 
            Map<String, yacySeed> regularSeeds) {
        // this method is called from the search target computation
        final long[] dhtVerticalTargets = seedDB.scheme.dhtPositions(wordhash);
        yacySeed seed;
        for (long  dhtVerticalTarget : dhtVerticalTargets) {
            wordhash = FlatWordPartitionScheme.positionToHash(dhtVerticalTarget);
            Iterator<yacySeed> dhtEnum = getAcceptRemoteIndexSeeds(seedDB, wordhash, redundancy, false);
            int c = Math.min(seedDB.sizeConnected(), redundancy);
            int cc = 2; // select a maximum of 3, this is enough redundancy
            while (dhtEnum.hasNext() && c > 0 && cc-- > 0) {
                seed = dhtEnum.next();
                if (seed == null || seed.hash == null) continue;
                if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
                if (Log.isFine("DHT")) Log.logFine("DHT", "selectPeers/DHTorder: " + seed.hash + ":" + seed.getName() + "/ score " + c);
                regularSeeds.put(seed.hash, seed);
                c--;
            }
        }
    }

    public static byte[] selectTransferStart() {
        return UTF8.getBytes(Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(Long.toString(System.currentTimeMillis()))).substring(2, 2 + Word.commonHashLength));
    }
    
    public static byte[] limitOver(final yacySeedDB seedDB, final byte[] startHash) {
        final Iterator<yacySeed> seeds = getAcceptRemoteIndexSeeds(seedDB, startHash, 1, false);
        if (seeds.hasNext()) return UTF8.getBytes(seeds.next().hash);
        return null;
    }

    protected static List<yacySeed> getAcceptRemoteIndexSeedsList(
            yacySeedDB seedDB,
            final byte[] starthash,
            int max,
            boolean alsoMyOwn) {
        final Iterator<yacySeed> seedIter = getAcceptRemoteIndexSeeds(seedDB, starthash, max, alsoMyOwn);
        final ArrayList<yacySeed> targets = new ArrayList<yacySeed>();
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
    public static Iterator<yacySeed> getAcceptRemoteIndexSeeds(final yacySeedDB seedDB, final byte[] starthash, final int max, final boolean alsoMyOwn) {
        return new acceptRemoteIndexSeedEnum(seedDB, starthash, Math.min(max, seedDB.sizeConnected()), alsoMyOwn);
    }
    
    private static class acceptRemoteIndexSeedEnum implements Iterator<yacySeed> {

        private final Iterator<yacySeed> se;
        private yacySeed nextSeed;
        private final yacySeedDB seedDB;
        private final HandleSet doublecheck;
        private int remaining;
        private boolean alsoMyOwn;
        
        private acceptRemoteIndexSeedEnum(yacySeedDB seedDB, final byte[] starthash, int max, boolean alsoMyOwn) {
            this.seedDB = seedDB;
            this.se = getDHTSeeds(seedDB, starthash, yacyVersion.YACY_HANDLES_COLLECTION_INDEX);
            this.remaining = max;
            this.doublecheck = new HandleSet(12, Base64Order.enhancedCoder, 0);
            this.nextSeed = nextInternal();
            this.alsoMyOwn = alsoMyOwn && nextSeed != null && (Base64Order.enhancedCoder.compare(UTF8.getBytes(seedDB.mySeed().hash), UTF8.getBytes(nextSeed.hash)) > 0);
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
                    byte[] hashb = UTF8.getBytes(s.hash);
                    if (doublecheck.has(hashb)) return null;
                    try {
                        this.doublecheck.put(hashb);
                    } catch (RowSpaceExceededException e) {
                        Log.logException(e);
                        break;
                    }
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
            if (alsoMyOwn && Base64Order.enhancedCoder.compare(UTF8.getBytes(seedDB.mySeed().hash), UTF8.getBytes(nextSeed.hash)) < 0) {
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
    protected static Iterator<yacySeed> getDHTSeeds(final yacySeedDB seedDB, final byte[] firstHash, final float minVersion) {
        // enumerates seed-type objects: all seeds with starting point in the middle, rotating at the end/beginning
        return new seedDHTEnum(seedDB, firstHash, minVersion);
    }

    private static class seedDHTEnum implements Iterator<yacySeed> {

        private Iterator<yacySeed> e1, e2;
        private int steps;
        private float minVersion;
        private yacySeedDB seedDB;
        
        private seedDHTEnum(final yacySeedDB seedDB, final byte[] firstHash, final float minVersion) {
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
    public static Iterator<yacySeed> getProvidesRemoteCrawlURLs(final yacySeedDB seedDB) {
        return new providesRemoteCrawlURLsEnum(seedDB);
    }
    
    private static class providesRemoteCrawlURLsEnum implements Iterator<yacySeed> {

        private Iterator<yacySeed> se;
        private yacySeed nextSeed;
        private yacySeedDB seedDB;
        
        private providesRemoteCrawlURLsEnum(final yacySeedDB seedDB) {
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
    public static Map<String, yacySeed> seedsByAge(final yacySeedDB seedDB, final boolean up, int count) {
        
        if (count > seedDB.sizeConnected()) count = seedDB.sizeConnected();

        // fill a score object
        final ScoreMap<String> seedScore = new ConcurrentScoreMap<String>();
        yacySeed ys;
        long absage;
        final Iterator<yacySeed> s = seedDB.seedsConnected(true, false, null, (float) 0.0);
        int searchcount = 1000;
        if (searchcount > seedDB.sizeConnected()) searchcount = seedDB.sizeConnected();
        try {
            while ((s.hasNext()) && (searchcount-- > 0)) {
                ys = s.next();
                if ((ys != null) && (ys.get(yacySeed.LASTSEEN, "").length() > 10)) try {
                    absage = Math.abs(System.currentTimeMillis() + AbstractFormatter.dayMillis - ys.getLastSeenUTC()) / 1000 / 60;
                    if (absage > Integer.MAX_VALUE) absage = Integer.MAX_VALUE;
                    seedScore.inc(ys.hash, (int) absage); // the higher absage, the older is the peer
                } catch (final Exception e) {}
            }
            
            // result is now in the score object; create a result vector
            final Map<String, yacySeed> result = new HashMap<String, yacySeed>();
            final Iterator<String> it = seedScore.keys(up);
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
