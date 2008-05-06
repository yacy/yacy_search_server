// yacyDHTAction.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
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
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroCloneableMapIterator;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroRotateIterator;
import de.anomic.server.logging.serverLog;

public class yacyDHTAction {
   
    protected yacySeedDB seedDB;
    protected kelondroMScoreCluster<String> seedCrawlReady;
    
    public yacyDHTAction(yacySeedDB seedDB) {
        this.seedDB = seedDB;
        this.seedCrawlReady = new kelondroMScoreCluster<String>();
        // init crawl-ready table
        try {
            Iterator<yacySeed> en = seedDB.seedsConnected(true, false, null, (float) 0.0);
            yacySeed ys;
            while (en.hasNext()) {
                ys = (yacySeed) en.next();
                if ((ys != null) && (ys.getVersion() >= ((float) 0.3))) seedCrawlReady.setScore(ys.hash, yacyCore.yacyTime());
            }
        } catch (IllegalArgumentException e) {
        }
    }
   
    public Iterator<yacySeed> getDHTSeeds(boolean up, String firstHash, float minVersion) {
        // enumerates seed-type objects: all seeds with starting point in the middle, rotating at the end/beginning
        return new seedDHTEnum(up, firstHash, minVersion);
    }

    class seedDHTEnum implements Iterator<yacySeed> {

        Iterator<yacySeed> e1, e2;
        boolean up;
        int steps;
        float minVersion;
        
        public seedDHTEnum(boolean up, String firstHash, float minVersion) {
            this.steps = seedDB.sizeConnected();
            this.up = up;
            this.minVersion = minVersion;
            this.e1 = seedDB.seedsConnected(up, false, firstHash, minVersion);
            this.e2 = null;
        }
        
        public boolean hasNext() {
            return (steps > 0) && ((e2 == null) || (e2.hasNext()));
        }

        public yacySeed next() {
            if (steps == 0) return null;
            steps--;
            if ((e1 != null) && (e1.hasNext())) {
            	yacySeed n = e1.next();
                if (!(e1.hasNext())) {
                    e1 = null;
                    e2 = seedDB.seedsConnected(up, false, null, minVersion);
                }
                return n;
            } else {
                if (e2 == null) {
                    e1 = null;
                    e2 = seedDB.seedsConnected(up, false, null, minVersion);
                }
                return e2.next();
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    
    public Iterator<yacySeed> getProvidesRemoteCrawlURLs() {
        return new providesRemoteCrawlURLsEnum();
    }
    
    class providesRemoteCrawlURLsEnum implements Iterator<yacySeed> {

        Iterator<yacySeed> se;
        yacySeed nextSeed;
        
        public providesRemoteCrawlURLsEnum() {
            se = getDHTSeeds(true, null, yacyVersion.YACY_POVIDES_REMOTECRAWL_LISTS);
            nextSeed = nextInternal();
        }
        
        public boolean hasNext() {
            return nextSeed != null;
        }

        private yacySeed nextInternal() {
            yacySeed s;
            try {
                while (se.hasNext()) {
                    s = (yacySeed) se.next();
                    if (s == null) return null;
                    if (s.getLong(yacySeed.RCOUNT, 0) > 0) return s;
                }
            } catch (kelondroException e) {
                System.out.println("DEBUG providesRemoteCrawlURLsEnum:" + e.getMessage());
                yacyCore.log.logSevere("database inconsistency (" + e.getMessage() + "), re-set of db.");
                seedDB.resetActiveTable();
                return null;
            }
            return null;
        }
        
        public yacySeed next() {
            yacySeed next = nextSeed;
            nextSeed = nextInternal();
            return next;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
    
    public Iterator<yacySeed> getAcceptRemoteIndexSeeds(String starthash) {
        // returns an enumeration of yacySeed-Objects
        // that have the AcceptRemoteIndex-Flag set
        // the seeds are enumerated in the right order according DHT
        return new acceptRemoteIndexSeedEnum(starthash);
    }
    
    class acceptRemoteIndexSeedEnum implements Iterator<yacySeed> {

        Iterator<yacySeed> se;
        yacySeed nextSeed;
        
        public acceptRemoteIndexSeedEnum(String starthash) {
            se = getDHTSeeds(true, starthash, yacyVersion.YACY_HANDLES_COLLECTION_INDEX);
            nextSeed = nextInternal();
        }
        
        public boolean hasNext() {
            return nextSeed != null;
        }

        private yacySeed nextInternal() {
            yacySeed s;
            try {
                while (se.hasNext()) {
                    s = (yacySeed) se.next();
                    if (s == null) return null;
                    if (s.getFlagAcceptRemoteIndex()) return s;
                }
            } catch (kelondroException e) {
                System.out.println("DEBUG acceptRemoteIndexSeedEnum:" + e.getMessage());
                yacyCore.log.logSevere("database inconsistency (" + e.getMessage() + "), re-set of db.");
                seedDB.resetActiveTable();
                return null;
            }
            return null;
        }
        
        public yacySeed next() {
            yacySeed next = nextSeed;
            nextSeed = nextInternal();
            return next;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
    
    public Iterator<yacySeed> getAcceptRemoteCrawlSeeds(String starthash, boolean available) {
        return new acceptRemoteCrawlSeedEnum(starthash, available);
    }
    
    class acceptRemoteCrawlSeedEnum implements Iterator<yacySeed> {

        Iterator<yacySeed> se;
        yacySeed nextSeed;
        boolean available;
        
        public acceptRemoteCrawlSeedEnum(String starthash, boolean available) {
            this.se = getDHTSeeds(true, starthash, (float) 0.0);
            this.available = available;
            nextSeed = nextInternal();
        }
        
        public boolean hasNext() {
            return nextSeed != null;
        }

        private yacySeed nextInternal() {
            yacySeed s;
            while (se.hasNext()) {
                s = (yacySeed) se.next();
                if (s == null) return null;
                s.available = seedCrawlReady.getScore(s.hash);
                if (available) {
                    if (seedCrawlReady.getScore(s.hash) < yacyCore.yacyTime()) return s;
                } else {
                    if (seedCrawlReady.getScore(s.hash) > yacyCore.yacyTime()) return s;
                }
            }
            return null;
        }
        
        public yacySeed next() {
            yacySeed next = nextSeed;
            nextSeed = nextInternal();
            return next;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
    
    public synchronized yacySeed getGlobalCrawlSeed(String urlHash) {
        Iterator<yacySeed> e = getAcceptRemoteCrawlSeeds(urlHash, true);
        yacySeed seed;
        if (e.hasNext()) seed = e.next(); else seed = null;
        e = null;
        return seed;
    }
    
    public synchronized yacySeed getPublicClusterCrawlSeed(String urlHash, TreeMap<String, String> clusterhashes) {
        // clusterhashes is a String(hash)/String(IP) - mapping
        kelondroCloneableIterator<String> i = new kelondroRotateIterator<String>(new kelondroCloneableMapIterator<String>(clusterhashes, urlHash), null, clusterhashes.size());
        String hash;
        int count = clusterhashes.size(); // counter to ensure termination
        while ((i.hasNext()) && (count-- > 0)) {
            hash = i.next();
        	yacySeed seed = seedDB.getConnected(hash);
        	if (seed == null) continue;
            seed.setAlternativeAddress((String) clusterhashes.get(hash));
        	return seed;
        }
        return null;
    }
        
    public void setCrawlTime(String seedHash, int newYacyTime) {
        if (newYacyTime < yacyCore.yacyTime()) newYacyTime = yacyCore.yacyTime();
        seedCrawlReady.setScore(seedHash, newYacyTime);
    }
    
    public void setCrawlDelay(String seedHash, int newDelay) {
        seedCrawlReady.setScore(seedHash, yacyCore.yacyTime() + newDelay);
    }

    public void processPeerArrival(yacySeed peer, boolean direct) {
        if (peer.getVersion() >= ((float) 0.3)) {
            if (!(seedCrawlReady.existsScore(peer.hash))) seedCrawlReady.setScore(peer.hash, yacyCore.yacyTime());
        } else {
            seedCrawlReady.deleteScore(peer.hash);
        }
    }
    
    public void processPeerDeparture(yacySeed peer) {
        seedCrawlReady.deleteScore(peer.hash);
    }
    
    public static boolean shallBeOwnWord(yacySeedDB seedDB, String wordhash) {
        if (seedDB == null) return false;
        if (seedDB.mySeed().isPotential()) return false;
        final double distance = dhtDistance(seedDB.mySeed().hash, wordhash);
        final double max = 1.2 / seedDB.sizeConnected();
        //System.out.println("Distance for " + wordhash + ": " + distance + "; max is " + max);
        return (distance > 0) && (distance <= max);
    }
    
    public static double dhtDistance(String peer, String word) {
        // the dht distance is a positive value between 0 and 1
        // if the distance is small, the word more probably belongs to the peer
        double d = hashDistance(peer, word);
        if (d > 0) {
            return d; // case where the word is 'before' the peer
        } else {
            return ((double) 1) + d; // wrap-around case
        }
    }
    
    private static double hashDistance(String from, String to) {
        // computes the distance between two hashes.
        // the maximum distance between two hashes is 1, the minimum -1
        // this can be used like "from - to"
        // the result is positive if from > to
        assert (from != null);
        assert (to != null);
        assert (from.length() == 12) : "from.length = " + from.length() + ", from = " + from;
        assert (to.length() == 12) : "to.length = " + to.length() + ", to = " + to;
        return ((double) (kelondroBase64Order.enhancedCoder.cardinal(from.getBytes()) - kelondroBase64Order.enhancedCoder.cardinal(to.getBytes()))) / ((double) Long.MAX_VALUE);
    }
    
    public synchronized ArrayList<yacySeed> getDHTTargets(yacySeedDB seedDB, serverLog log, int primaryPeerCount, int reservePeerCount, String firstKey, String lastKey, double maxDist) {
        // find a list of DHT-peers
        assert firstKey != null;
        assert lastKey != null;
        assert seedDB != null;
        assert seedDB.mySeed() != null;
        assert seedDB.mySeed().hash != null;
        /*
        assert
            !(kelondroBase64Order.enhancedCoder.cardinal(firstKey.getBytes()) < kelondroBase64Order.enhancedCoder.cardinal(yacyCore.seedDB.mySeed.hash.getBytes()) &&
              kelondroBase64Order.enhancedCoder.cardinal(lastKey.getBytes()) > kelondroBase64Order.enhancedCoder.cardinal(yacyCore.seedDB.mySeed.hash.getBytes()));
        */
        ArrayList<yacySeed> seeds = new ArrayList<yacySeed>();
        yacySeed seed;
        //double ownDistance = Math.min(yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, firstKey), yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, lastKey));
        //double maxDistance = Math.min(ownDistance, maxDist);

        double firstdist, lastdist;
        Iterator<yacySeed> e = this.getAcceptRemoteIndexSeeds(lastKey);
        TreeSet<String> doublecheck = new TreeSet<String>(kelondroBase64Order.enhancedComparator);
        int maxloop = Math.min(100, seedDB.sizeConnected()); // to ensure termination
        if (log != null) log.logInfo("Collecting DHT target peers for first_hash = " + firstKey + ", last_hash = " + lastKey);
        while ((e.hasNext()) && (seeds.size() < (primaryPeerCount + reservePeerCount)) && (maxloop-- > 0)) {
            seed = (yacySeed) e.next();
            if (seeds != null) {
            	firstdist = yacyDHTAction.dhtDistance(seed.hash, firstKey);
            	lastdist = yacyDHTAction.dhtDistance(seed.hash, lastKey);
                if (lastdist > maxDist) {
                    if (log != null) log.logFine("Discarded too distant DHT target peer " + seed.getName() + ":" + seed.hash + ", distance2first = " + firstdist + ", distance2last = " + lastdist);
                } else if (doublecheck.contains(seed.hash)) {
                    if (log != null) log.logFine("Discarded double DHT target peer " + seed.getName() + ":" + seed.hash + ", distance2first = " + firstdist + ", distance2last = " + lastdist);
                } else {
                    if (log != null) log.logInfo("Selected  " + ((seeds.size() < primaryPeerCount) ? "primary" : "reserve") + "  DHT target peer " + seed.getName() + ":" + seed.hash + ", distance2first = " + firstdist + ", distance2last = " + lastdist);
                    seeds.add(seed);
                    doublecheck.add(seed.hash);
                }
            }
        }
        e = null; // finish enumeration
        
        return seeds;
    }
}
