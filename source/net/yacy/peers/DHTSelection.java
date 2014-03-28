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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
import net.yacy.cora.sorting.OrderedScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.peers.operation.yacyVersion;



/*
 * this package is a collection of peer selection iterations that had been
 * part of yacyPeerActions, yacyDHTActions and yacySeedDB
 */

public class DHTSelection {
    
    public static Set<Seed> selectClusterPeers(final SeedDB seedDB, final SortedMap<byte[], String> peerhashes) {
        final Iterator<Map.Entry<byte[], String>> i = peerhashes.entrySet().iterator();
        final Set<Seed> l = new HashSet<Seed>();
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

    /**
     * 
     * @param seedDB
     * @param wordhashes
     * @param minage
     * @param omit
     * @param maxcount
     * @param r we must use a random factor for the selection to prevent that all peers do the same and therefore overload the same peers
     * @return
     */
    public static Collection<Seed> selectExtraTargets(
            final SeedDB seedDB,
            final HandleSet wordhashes,
            final int minage,
            final Set<Seed> omit,
            final int maxcount,
            final Random r) {
        
        Collection<Seed> extraSeeds = new HashSet<Seed>();
        
        if (seedDB != null) {
            final OrderedScoreMap<Seed> seedSelection = new OrderedScoreMap<Seed>(null);
            
            // create sets that contains only robinson/node/large/young peers
            Iterator<Seed> dhtEnum = seedDB.seedsConnected(true, false, null, 0.50f);
            Seed seed;
            while (dhtEnum.hasNext()) {
                seed = dhtEnum.next();
                if (seed == null) continue;
                if (omit != null && omit.contains(seed)) continue; // sort out peers that are target for DHT
                if (seed.isLastSeenTimeout(3600000)) continue; // do not ask peers that had not been seen more than one hour (happens during a startup situation)
                if (!seed.getFlagSolrAvailable()) continue; // extra peers always use solr direct, skip if solr interface is not available
                if (!seed.getFlagAcceptRemoteIndex() && seed.matchPeerTags(wordhashes)) seedSelection.dec(seed, r.nextInt(10) + 2); // robinson peers with matching peer tags
                if (seed.getFlagRootNode()) seedSelection.dec(seed, r.nextInt(30) + 6); // root nodes (fast peers)
                if (seed.getAge() < minage) seedSelection.dec(seed, r.nextInt(15) + 3); // young peers (with fresh info)
                if (seed.getAge() < 1) seedSelection.dec(seed, r.nextInt(40) + 8); // the 'workshop feature', fresh peers should be seen
                if (seed.getLinkCount() >= 100000 && seed.getLinkCount() < 1000000) { // peers above 100.000 links take part on a selection of medium-size peers
                    seedSelection.dec(seed, r.nextInt(25) + 5);
                }
                if (seed.getLinkCount() >= 1000000) { // peers above 1 million links take part on a selection of large peers
                    int pf = 1 + (int) (20000000 / seed.getLinkCount());
                    seedSelection.dec(seed, r.nextInt(pf) + pf / 5); // large peers; choose large one less frequent to reduce load on their peer
                }
            }
            
            // select the maxount
            Iterator<Seed> i = seedSelection.iterator();
            int count = 0;
            while (i.hasNext() && count++ < maxcount) {
                seed = i.next();
                if (RemoteSearch.log.isInfo()) {
                    RemoteSearch.log.info("selectPeers/extra: " + seed.hash + ":" + seed.getName() + ", " + seed.getLinkCount() + " URLs" +
                            (seed.getLinkCount() >= 1000000 ? " LARGE-SIZE" : "") +
                            (seed.getLinkCount() >= 100000 && seed.getLinkCount() < 1000000 ? " MEDIUM-SIZE" : "") +
                            (!seed.getFlagAcceptRemoteIndex() && seed.matchPeerTags(wordhashes) ? " ROBINSON" : "") +
                            (seed.getFlagRootNode() ? " NODE" : "") +
                            (seed.getAge() < 1 ? " FRESH" : "")
                            );
                }
                extraSeeds.add(seed);
            }
        }
        
        return extraSeeds;
    }
    
    public static Set<Seed> selectDHTSearchTargets(final SeedDB seedDB, final HandleSet wordhashes, final int minage, final int redundancy, final int maxredundancy, final Random random) {

        // put in seeds according to dht
        Set<Seed> seeds = new LinkedHashSet<Seed>(); // dht position seeds
        if (seedDB != null) {
            Iterator<byte[]> iter = wordhashes.iterator();
            while (iter.hasNext()) {
                seeds.addAll(collectHorizontalDHTPositions(seedDB, iter.next(), minage, redundancy, maxredundancy, random));
            }
        }
        
        return seeds;
    }

    private static ArrayList<Seed> collectHorizontalDHTPositions(final SeedDB seedDB, final byte[] wordhash, final int minage, final int redundancy, final int maxredundancy, Random random) {
        // this method is called from the search target computation
        ArrayList<Seed> collectedSeeds = new ArrayList<Seed>(redundancy * seedDB.scheme.verticalPartitions());
        for (int verticalPosition = 0; verticalPosition < seedDB.scheme.verticalPartitions(); verticalPosition++) {
            ArrayList<Seed> seeds = selectVerticalDHTPositions(seedDB, wordhash, minage, maxredundancy, verticalPosition);
            if (seeds.size() <= redundancy) {
                collectedSeeds.addAll(seeds);
            } else {
                // we pick some random peers from the vertical position.
                // All of them should be valid, but picking a random subset is a distributed load balancing on the whole YaCy network.
                // without picking a random subset, always the same peers would be targeted for the same word resulting in (possible) DoS on the target.
                for (int i = 0; i < redundancy; i++) {
                    collectedSeeds.add(seeds.remove(random.nextInt(seeds.size())));
                }
            }
        }
        return collectedSeeds;
    }
    
    /**
     * collecting vertical positions: that chooses for each of the DHT partition a collection of redundant storage positions
     * @param seedDB the database of seeds
     * @param wordhash the word we are searching for
     * @param minage the minimum age of a seed (to prevent that too young seeds which cannot have results yet are asked)
     * @param redundancy the number of redundant peer position for this parition, minimum is 1
     * @param verticalPosition the verical position, thats the number of the partition 0 <= verticalPosition < seedDB.scheme.verticalPartitions()
     * @return a list of seeds for the redundant positions
     */
    private static ArrayList<Seed> selectVerticalDHTPositions(final SeedDB seedDB, final byte[] wordhash, final int minage, final int redundancy, int verticalPosition) {
        // this method is called from the search target computation
        ArrayList<Seed> seeds = new ArrayList<Seed>(redundancy);
        final long dhtVerticalTarget = seedDB.scheme.verticalDHTPosition(wordhash, verticalPosition);
        final byte[] verticalhash = Distribution.positionToHash(dhtVerticalTarget);
        final Iterator<Seed> dhtEnum = getAcceptRemoteIndexSeeds(seedDB, verticalhash, redundancy, false);
        int c = Math.min(seedDB.sizeConnected(), redundancy);
        int cc = 20; // in case that the network grows rapidly, we may jump to several additional peers but that must have a limit
        while (dhtEnum.hasNext() && c > 0 && cc-- > 0) {
            Seed seed = dhtEnum.next();
            if (seed == null || seed.hash == null) continue;
            if (!seed.getFlagAcceptRemoteIndex()) continue; // probably a robinson peer
            if (seed.getAge() < minage) continue; // prevent bad results because of too strong network growth
            if (RemoteSearch.log.isInfo()) RemoteSearch.log.info("selectPeers/DHTorder: " + seed.hash + ":" + seed.getName() + "/ score " + c);
            seeds.add(seed);
            c--;
        }
        return seeds;
    }

    public static byte[] selectRandomTransferStart() {
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

    private static class acceptRemoteIndexSeedEnum extends LookAheadIterator<Seed> implements Iterator<Seed>, Iterable<Seed> {

        private final Iterator<Seed> se;
        private final SeedDB seedDB;
        private int remaining;
        private final boolean alsoMyOwn;

        private acceptRemoteIndexSeedEnum(SeedDB seedDB, final byte[] starthash, int max, boolean alsoMyOwn) {
            this.seedDB = seedDB;
            this.se = new seedDHTEnum(seedDB, starthash, alsoMyOwn);
            this.remaining = max;
            this.alsoMyOwn = alsoMyOwn;
        }

        @Override
        protected Seed next0() {
            if (this.remaining <= 0) return null;
            Seed s = null;
            try {
                while (this.se.hasNext()) {
                    s = this.se.next();
                    if (s == null) return null;
                    if (s.getFlagAcceptRemoteIndex() ||
                        (this.alsoMyOwn && s.hash.equals(this.seedDB.mySeed().hash)) // Accept own peer regardless of FlagAcceptRemoteIndex
                       ) {
                        this.remaining--;
                        break;
                    }
                }
                return s;
            } catch (final kelondroException e) {
                System.out.println("DEBUG acceptRemoteIndexSeedEnum:" + e.getMessage());
                Network.log.severe("database inconsistency (" + e.getMessage() + "), re-set of db.");
                this.seedDB.resetActiveTable();
                return null;
            }
        }

    }
    
    private static class seedDHTEnum implements Iterator<Seed> {

        private Iterator<Seed> e;
        private int steps;
        private final SeedDB seedDB;
        private boolean alsoMyOwn;
        private int pass, insertOwnInPass;
        private Seed nextSeed;

        private seedDHTEnum(final SeedDB seedDB, final byte[] firstHash, final boolean alsoMyOwn) {
            this.seedDB = seedDB;
            this.steps = seedDB.sizeConnected() + ((alsoMyOwn) ? 1 : 0);
            this.e = seedDB.seedsConnected(true, false, firstHash, yacyVersion.YACY_HANDLES_COLLECTION_INDEX);
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
                // rotate from the beginning; this closes the ordering of the DHT at the ends
                this.e = this.seedDB.seedsConnected(true, false, null, yacyVersion.YACY_HANDLES_COLLECTION_INDEX);
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

    private static class providesRemoteCrawlURLsEnum extends LookAheadIterator<Seed> implements Iterator<Seed>, Iterable<Seed> {

        private final Iterator<Seed> se;
        private final SeedDB seedDB;

        private providesRemoteCrawlURLsEnum(final SeedDB seedDB) {
            this.seedDB = seedDB;
            this.se = seedDB.seedsConnected(true, false, null, yacyVersion.YACY_POVIDES_REMOTECRAWL_LISTS);
        }

        @Override
        protected Seed next0() {
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