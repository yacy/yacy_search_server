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

package net.yacy.peers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.federate.yacy.Distribution;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.peers.operation.yacyVersion;



/*
 * this package is a collection of peer selection iterations that had been
 * part of yacyPeerActions, yacyDHTActions and yacySeedDB
 */

public class DHTSelection {

    public static List<Seed> selectClusterPeers(final SeedDB seedDB, final SortedMap<byte[], String> peerhashes) {
        final Iterator<Map.Entry<byte[], String>> i = peerhashes.entrySet().iterator();
        final List<Seed> l = new ArrayList<Seed>();
        Map.Entry<byte[], String> entry;
        Seed s;
        while (i.hasNext()) {
            entry = i.next();
            s = seedDB.get(ASCII.String(entry.getKey())); // should be getConnected; get only during testing time
            if (s != null) {
                s.setAlternativeAddress(entry.getValue());
                l.add(s);
            }
        }
        return l;
    }
    
    public static List<Seed> selectNodeSearchTargets(final SeedDB seedDB, int maxCount, Set<Seed> omit) {
        if (seedDB == null) { return null; }

        final List<Seed> goodSeeds = new ArrayList<Seed>();
        final List<Seed> optionalSeeds = new ArrayList<Seed>();
        Seed seed;
        Iterator<Seed> seedenum = seedDB.seedsConnected(true, true, Seed.randomHash(), 1.041f);
        int c = seedDB.sizeConnected();
        while (seedenum.hasNext() && c-- > 0 && goodSeeds.size() < maxCount) {
            seed = seedenum.next();
            if (seed == null || omit.contains(seed)) continue;
            if (seed.getFlagRootNode()) {
            	goodSeeds.add(seed);
            } else {
            	optionalSeeds.add(seed);
            }
        }
        Random r = new Random(System.currentTimeMillis());
        while (goodSeeds.size() < maxCount && optionalSeeds.size() > 0) {
        	goodSeeds.add(optionalSeeds.remove(r.nextInt(optionalSeeds.size())));
        }
        
        return goodSeeds;
    }

    public static List<Seed> selectSearchTargets(
            final SeedDB seedDB,
            final HandleSet wordhashes,
            int redundancy,
            int burstRobinsonPercent,
            int burstMultiwordPercent) {
        // find out a specific number of seeds, that would be relevant for the given word hash(es)
        // the result is ordered by relevance: [0] is most relevant
        // the seedcount is the maximum number of wanted results
        if (seedDB == null) { return null; }

        // put in seeds according to dht
        final Map<String, Seed> regularSeeds = new HashMap<String, Seed>(); // dht position seeds
        Seed seed;
        Iterator<Seed> dhtEnum;
        Iterator<byte[]> iter = wordhashes.iterator();
        while (iter.hasNext()) {
            selectDHTPositions(seedDB, iter.next(), redundancy, regularSeeds);
        }
        //int minimumseeds = Math.min(seedDB.scheme.verticalPartitions(), regularSeeds.size()); // that should be the minimum number of seeds that are returned
        //int maximumseeds = seedDB.scheme.verticalPartitions() * redundancy; // this is the maximum number of seeds according to dht and heuristics. It can be more using burst mode.

        // put in some seeds according to size of peer.
        // But not all, that would produce too much load on the largest peers
        dhtEnum = seedDB.seedsSortedConnected(false, Seed.ICOUNT);
        int c = Math.max(Math.min(5, seedDB.sizeConnected()), wordhashes.size() > 1 ? seedDB.sizeConnected() * burstMultiwordPercent / 100 : 0);
        while (dhtEnum.hasNext() && c-- > 0) {
            seed = dhtEnum.next();
            if (seed == null) continue;
            if (seed.isLastSeenTimeout(3600000)) continue;
            if (seed.getAge() < 1) { // the 'workshop feature'
                ConcurrentLog.info("DHT", "selectPeers/Age: " + seed.hash + ":" + seed.getName() + ", is newbie, age = " + seed.getAge());
                regularSeeds.put(seed.hash, seed);
                continue;
            }
            if (Math.random() * 100 + (wordhashes.size() > 1 ? burstMultiwordPercent : 25) >= 50) {
                if (ConcurrentLog.isFine("DHT")) ConcurrentLog.fine("DHT", "selectPeers/CountBurst: " + seed.hash + ":" + seed.getName() + ", RWIcount=" + seed.getWordCount());
                regularSeeds.put(seed.hash, seed);
                continue;
            }
        }

        // create a set that contains only robinson peers because these get a special handling
        dhtEnum = seedDB.seedsConnected(true, false, null, 0.50f);
        Set<Seed> robinson = new HashSet<Seed>();
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
                if (ConcurrentLog.isFine("DHT")) ConcurrentLog.fine("DHT", "selectPeers/RobinsonBurst: " + seed.hash + ":" + seed.getName());
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
                    ConcurrentLog.info("DHT", "selectPeers/RobinsonTag: " + seed.hash + ":" + seed.getName() + " grants search for all");
                } else {
                    ConcurrentLog.info("DHT", "selectPeers/RobinsonTag " + seed.hash + ":" + seed.getName() + " is specialized peer for " + specialized);
                }
                regularSeeds.put(seed.hash, seed);
            }
        }

        // produce return set
        List<Seed> result = new ArrayList<Seed>(regularSeeds.size());
        result.addAll(regularSeeds.values());
        return result;
    }
    
    private static void selectDHTPositions(
            final SeedDB seedDB,
            byte[] wordhash,
            int redundancy,
            Map<String, Seed> regularSeeds) {
        // this method is called from the search target computation
        Seed seed;
        for (int verticalPosition = 0; verticalPosition < seedDB.scheme.verticalPartitions(); verticalPosition++) {
            long dhtVerticalTarget = seedDB.scheme.verticalDHTPosition(wordhash, verticalPosition);
            wordhash = Distribution.positionToHash(dhtVerticalTarget);
            Iterator<Seed> dhtEnum = getAcceptRemoteIndexSeeds(seedDB, wordhash, redundancy, false);
            int c = Math.min(seedDB.sizeConnected(), redundancy);
            int cc = 2; // select a maximum of 3, this is enough redundancy
            while (dhtEnum.hasNext() && c > 0 && cc-- > 0) {
                seed = dhtEnum.next();
                if (seed == null || seed.hash == null) continue;
                if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
                if (ConcurrentLog.isFine("DHT")) ConcurrentLog.fine("DHT", "selectPeers/DHTorder: " + seed.hash + ":" + seed.getName() + "/ score " + c);
                regularSeeds.put(seed.hash, seed);
                c--;
            }
        }
    }

    public static byte[] selectTransferStart() {
        return ASCII.getBytes(Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(Long.toString(System.currentTimeMillis()))).substring(2, 2 + Word.commonHashLength));
    }

    public static byte[] limitOver(final SeedDB seedDB, final byte[] startHash) {
        final Iterator<Seed> seeds = getAcceptRemoteIndexSeeds(seedDB, startHash, 1, false);
        if (seeds.hasNext()) return ASCII.getBytes(seeds.next().hash);
        return null;
    }

    public static List<Seed> getAcceptRemoteIndexSeedsList(
            SeedDB seedDB,
            final byte[] starthash,
            int max,
            boolean alsoMyOwn) {
        final Iterator<Seed> seedIter = getAcceptRemoteIndexSeeds(seedDB, starthash, max, alsoMyOwn);
        final ArrayList<Seed> targets = new ArrayList<Seed>();
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
    public static Iterator<Seed> getAcceptRemoteIndexSeeds(final SeedDB seedDB, final byte[] starthash, final int max, final boolean alsoMyOwn) {
        return new acceptRemoteIndexSeedEnum(seedDB, starthash, Math.min(max, seedDB.sizeConnected()), alsoMyOwn);
    }

    private static class acceptRemoteIndexSeedEnum implements Iterator<Seed> {

        private final Iterator<Seed> se;
        private Seed nextSeed;
        private final SeedDB seedDB;
        private final HandleSet doublecheck;
        private int remaining;
        private final boolean alsoMyOwn;

        private acceptRemoteIndexSeedEnum(SeedDB seedDB, final byte[] starthash, int max, boolean alsoMyOwn) {
            this.seedDB = seedDB;
            this.se = getDHTSeeds(seedDB, starthash, yacyVersion.YACY_HANDLES_COLLECTION_INDEX, alsoMyOwn);
            this.remaining = max;
            this.doublecheck = new RowHandleSet(12, Base64Order.enhancedCoder, 0);
            this.nextSeed = nextInternal();
            this.alsoMyOwn = alsoMyOwn;
        }

        @Override
        public boolean hasNext() {
            return this.nextSeed != null;
        }

        private Seed nextInternal() {
            if (this.remaining <= 0) return null;
            Seed s;
            try {
                while (this.se.hasNext()) {
                    s = this.se.next();
                    if (s == null) return null;
                    byte[] hashb = ASCII.getBytes(s.hash);
                    if (this.doublecheck.has(hashb)) return null;
                    try {
                        this.doublecheck.put(hashb);
                    } catch (final SpaceExceededException e) {
                        ConcurrentLog.logException(e);
                        break;
                    }
                    if (s.getFlagAcceptRemoteIndex() ||
                        (this.alsoMyOwn && s.hash.equals(this.seedDB.mySeed().hash)) // Accept own peer regardless of FlagAcceptRemoteIndex
                       ) {
                        this.remaining--;
                        return s;
                    }
                }
            } catch (final kelondroException e) {
                System.out.println("DEBUG acceptRemoteIndexSeedEnum:" + e.getMessage());
                Network.log.severe("database inconsistency (" + e.getMessage() + "), re-set of db.");
                this.seedDB.resetActiveTable();
                return null;
            }
            return null;
        }

        @Override
        public Seed next() {
            final Seed next = this.nextSeed;
            this.nextSeed = nextInternal();
            return next;
        }

        @Override
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
    protected static Iterator<Seed> getDHTSeeds(final SeedDB seedDB, final byte[] firstHash, final double minVersion) {
        // enumerates seed-type objects: all seeds with starting point in the middle, rotating at the end/beginning
        return new seedDHTEnum(seedDB, firstHash, minVersion, false);
    }

    protected static Iterator<Seed> getDHTSeeds(final SeedDB seedDB, final byte[] firstHash, final double minVersion, final boolean alsoMyOwn) {
        // enumerates seed-type objects: all seeds with starting point in the middle, rotating at the end/beginning
        return new seedDHTEnum(seedDB, firstHash, minVersion, alsoMyOwn);
    }
    private static class seedDHTEnum implements Iterator<Seed> {

        private Iterator<Seed> e;
        private int steps;
        private final double minVersion;
        private final SeedDB seedDB;
        private boolean alsoMyOwn;
        private int pass, insertOwnInPass;
        private Seed nextSeed;

        private seedDHTEnum(final SeedDB seedDB, final byte[] firstHash, final double minVersion, final boolean alsoMyOwn) {
            this.seedDB = seedDB;
            this.steps = seedDB.sizeConnected() + ((alsoMyOwn) ? 1 : 0);
            this.minVersion = minVersion;
            this.e = seedDB.seedsConnected(true, false, firstHash, minVersion);
            this.pass = 1;
            this.alsoMyOwn = alsoMyOwn;
            if (alsoMyOwn) {
                this.insertOwnInPass = (Base64Order.enhancedCoder.compare(ASCII.getBytes(seedDB.mySeed().hash), firstHash) > 0) ? 1 : 2;
            } else {
                this.insertOwnInPass = 0;
            }
            this.nextSeed = nextInternal();
        }

        @Override
        public boolean hasNext() {
            return (this.nextSeed != null) || this.alsoMyOwn;
        }

        public Seed nextInternal() {
            if (this.steps == 0) return null;
            this.steps--;

            if (!this.e.hasNext() && this.pass == 1) {
                this.e = this.seedDB.seedsConnected(true, false, null, this.minVersion);
                this.pass = 2;
            }
            if (this.e.hasNext()) {
                return this.e.next();
            }
            this.steps = 0;
            return null;
        }

        @Override
        public Seed next() {
            if (this.alsoMyOwn &&
                ((this.pass > this.insertOwnInPass) ||
                 (this.pass == this.insertOwnInPass && this.nextSeed == null) || // Own hash is last in line
                 (this.pass == this.insertOwnInPass && this.nextSeed != null && (Base64Order.enhancedCoder.compare(ASCII.getBytes(this.seedDB.mySeed().hash), ASCII.getBytes(this.nextSeed.hash)) < 0)))
               ) {
                // take my own seed hash instead the enumeration result
                this.alsoMyOwn = false;
                return this.seedDB.mySeed();
            }
            final Seed next = this.nextSeed;
            this.nextSeed = nextInternal();
            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * enumerate peers that provide remote crawl urls
     * @param seedDB
     * @return an iterator of seed objects
     */
    public static Iterator<Seed> getProvidesRemoteCrawlURLs(final SeedDB seedDB) {
        return new providesRemoteCrawlURLsEnum(seedDB);
    }

    private static class providesRemoteCrawlURLsEnum implements Iterator<Seed> {

        private final Iterator<Seed> se;
        private Seed nextSeed;
        private final SeedDB seedDB;

        private providesRemoteCrawlURLsEnum(final SeedDB seedDB) {
            this.seedDB = seedDB;
            this.se = getDHTSeeds(seedDB, null, yacyVersion.YACY_POVIDES_REMOTECRAWL_LISTS);
            this.nextSeed = nextInternal();
        }

        @Override
        public boolean hasNext() {
            return this.nextSeed != null;
        }

        private Seed nextInternal() {
            Seed s;
            try {
                while (this.se.hasNext()) {
                    s = this.se.next();
                    if (s == null) return null;
                    if (s.getLong(Seed.RCOUNT, 0) > 0) return s;
                }
            } catch (final kelondroException e) {
                System.out.println("DEBUG providesRemoteCrawlURLsEnum:" + e.getMessage());
                Network.log.severe("database inconsistency (" + e.getMessage() + "), re-set of db.");
                this.seedDB.resetActiveTable();
                return null;
            }
            return null;
        }

        @Override
        public Seed next() {
            final Seed next = this.nextSeed;
            this.nextSeed = nextInternal();
            return next;
        }

        @Override
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
    public static ConcurrentMap<String, Seed> seedsByAge(final SeedDB seedDB, final boolean up, int count) {
        if (count > seedDB.sizeConnected()) count = seedDB.sizeConnected();
        Seed ys;
        //long age;
        final Iterator<Seed> s = seedDB.seedsSortedConnected(!up, Seed.LASTSEEN);
        try {
            final ConcurrentMap<String, Seed> result = new ConcurrentHashMap<String, Seed>();
            while (s.hasNext() && count-- > 0) {
                ys = s.next();
                if (ys != null && ys.hash != null) {
                    //age = (System.currentTimeMillis() - ys.getLastSeenUTC()) / 1000 / 60;
                    //System.out.println("selected seedsByAge up=" + up + ", age/min = " + age);
                    result.put(ys.hash, ys);
                }
            }
            return result;
        } catch (final kelondroException e) {
            Network.log.severe("Internal Error at yacySeedDB.seedsByAge: " + e.getMessage(), e);
            return null;
        }
    }



}