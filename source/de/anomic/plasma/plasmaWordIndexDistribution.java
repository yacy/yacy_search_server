

package de.anomic.plasma;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;

import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.kelondro.kelondroException;

public class plasmaWordIndexDistribution {
    
    // distributes parts of the index to other peers
    // stops as soon as an error occurrs
    
    private int indexCount;
    private int juniorPeerCount, seniorPeerCount;
    private long maxTime;
    
    private plasmaURLPool urlPool;
    private plasmaWordIndex wordIndex;
    serverLog log;
    boolean paused = false;
    private boolean enabled;
    private boolean enabledWhileCrawling;
    private boolean closed;
    
    public transferIndexThread transferIdxThread = null;
    
    public plasmaWordIndexDistribution(plasmaURLPool urlPool, plasmaWordIndex wordIndex, serverLog log,
    boolean enable, boolean enabledWhileCrawling) {
        this.urlPool = urlPool;
        this.wordIndex = wordIndex;
        this.enabled = enable;
        this.enabledWhileCrawling = enabledWhileCrawling;
        this.log = log;
        this.closed = false;
        setCounts(100 /*indexCount*/,  1 /*juniorPeerCount*/, 3 /*seniorPeerCount*/, 8000);
    }
    
    public void enable() {
        enabled = true;
    }
    
    public void disable() {
        enabled = false;
    }
    
    public void enableWhileCrawling() {
        this.enabledWhileCrawling = true;
    }
    
    public void disableWhileCrawling() {
        this.enabledWhileCrawling = false;
    }
    
    public void close() {
        closed = true;
        if (transferIdxThread != null) {
            stopTransferWholeIndex(false);
        }
    }
    
    public boolean job() {

        if (this.closed) {
            log.logFine("no word distribution: closed");
            return false;
        }
        if (yacyCore.seedDB == null) {
            log.logFine("no word distribution: seedDB == null");
            return false;
        }
        if (yacyCore.seedDB.mySeed == null) {
            log.logFine("no word distribution: mySeed == null");
            return false;
        }
        if (yacyCore.seedDB.mySeed.isVirgin()) {
            log.logFine("no word distribution: status is virgin");
            return false;
        }
        if (!(enabled)) {
            log.logFine("no word distribution: not enabled");
            return false;
        }
        if (paused) {
            log.logFine("no word distribution: paused");
            return false;            
        }
        if (urlPool.loadedURL.size() < 10) {
            log.logFine("no word distribution: loadedURL.size() = " + urlPool.loadedURL.size());
            return false;
        }
        if (wordIndex.size() < 100) {
            log.logFine("no word distribution: not enough words - wordIndex.size() = " + wordIndex.size());
            return false;
        }
        if ((!enabledWhileCrawling) && (urlPool.noticeURL.stackSize() > 0)) {
            log.logFine("no word distribution: crawl in progress - noticeURL.stackSize() = " + urlPool.noticeURL.stackSize());
            return false;
        }
        
        // do the transfer
        int peerCount = (yacyCore.seedDB.mySeed.isJunior()) ? juniorPeerCount : seniorPeerCount;
        long starttime = System.currentTimeMillis();
        int transferred = performTransferIndex(indexCount, peerCount, true);
        
        if (transferred <= 0) {
            log.logFine("no word distribution: transfer failed");
            return false;
        }

        // adopt transfer count
        if ((System.currentTimeMillis() - starttime) > (maxTime * peerCount))
            indexCount--;
        else
            indexCount++;
        if (indexCount < 50) indexCount = 50;

        // show success
        return true;
        
    }
    
    public void setCounts(int indexCount, int juniorPeerCount, int seniorPeerCount, long maxTimePerTransfer) {
        this.maxTime = maxTimePerTransfer;
        this.indexCount = indexCount;
        if (indexCount < 30) indexCount = 30;
        this.juniorPeerCount = juniorPeerCount;
        this.seniorPeerCount = seniorPeerCount;
    }
    
    public int performTransferIndex(int indexCount, int peerCount, boolean delete) {
        if ((yacyCore.seedDB == null) || (yacyCore.seedDB.sizeConnected() == 0)) return -1;
        
        // collect index
        String startPointHash = selectTransferStart();
        log.logFine("Selected hash " + startPointHash + " as start point for index distribution, distance = " + yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, startPointHash));
        Object[] selectResult = selectTransferIndexes(startPointHash, indexCount);
        plasmaWordIndexEntity[] indexEntities = (plasmaWordIndexEntity[]) selectResult[0];
        HashMap urlCache = (HashMap) selectResult[1]; // String (url-hash) / plasmaCrawlLURL.Entry 
        if ((indexEntities == null) || (indexEntities.length == 0)) {
            log.logFine("No index available for index transfer, hash start-point " + startPointHash);
            return -1;
        }
        // count the indexes again, can be smaller as expected
        indexCount = 0;
        for (int i = 0; i < indexEntities.length; i++) {
            indexCount += indexEntities[i].size();
        }
        
        // find start point for DHT-selection
        String keyhash = indexEntities[indexEntities.length - 1].wordHash(); // DHT targets must have greater hashes
        
        // iterate over DHT-peers and send away the indexes
        yacySeed seed;
        int hc = 0;
        Enumeration e = yacyCore.dhtAgent.getAcceptRemoteIndexSeeds(keyhash);
        String error;
        String peerNames = "";
        double avdist;
        long start;
        while ((e.hasMoreElements()) && (hc < peerCount)) {
            if (closed) {
                log.logSevere("Index distribution interrupted by close, nothing deleted locally.");
                return -1; // interrupted
            }
            seed = (yacySeed) e.nextElement();
            if ((seed != null) &&
                ((avdist = (yacyDHTAction.dhtDistance(seed.hash, indexEntities[0].wordHash()) +
                            yacyDHTAction.dhtDistance(seed.hash, indexEntities[indexEntities.length-1].wordHash())) / 2.0) < 0.3)) {
                start = System.currentTimeMillis();
                error = yacyClient.transferIndex(seed, indexEntities, urlCache);
                if (error == null) {
                    log.logInfo("Index transfer of " + indexCount + " words [" + indexEntities[0].wordHash() + " .. " + indexEntities[indexEntities.length-1].wordHash() + "]/" +
                                 avdist + " to peer " + seed.getName() + ":" + seed.hash + " in " +
                                 ((System.currentTimeMillis() - start) / 1000) + " seconds successfull (" +
                                 (1000 * indexCount / (System.currentTimeMillis() - start + 1)) + " words/s)");
                    peerNames += ", " + seed.getName();
                    hc++;
                } else {
                    log.logWarning("Index transfer to peer " + seed.getName() + ":" + seed.hash + " failed:'" + error + "', disconnecting peer");
                    yacyCore.peerActions.peerDeparture(seed);
                }
            }
        }
        if (peerNames.length() > 0) peerNames = peerNames.substring(2); // remove comma
        
        // clean up and finish with deletion of indexes
        if (hc >= peerCount) {
            // success
            if (delete) {
                try {
                    if (deleteTransferIndexes(indexEntities)) {
                        log.logFine("Deleted all " + indexEntities.length + " transferred whole-word indexes locally");
                        return indexCount;
                    } else {
                        log.logSevere("Deleted not all transferred whole-word indexes");
                        return -1;
                    }
                } catch (IOException ee) {
                    log.logSevere("Deletion of indexes not possible:" + ee.getMessage(), ee);
                    return -1;
                }
            } else {
                // simply close the indexEntities
                for (int i = 0; i < indexEntities.length; i++) try {
                    indexEntities[i].close();
                } catch (IOException ee) {}
            }
            return indexCount;
        } else {
            log.logSevere("Index distribution failed. Too less peers (" + hc + ") received the index, not deleted locally.");
            return -1;
        }
    }
    
    private String selectTransferStart() {
        String startPointHash;
        // first try to select with increasing probality a good start point
        if (Math.round(Math.random() * 6) != 4) for (int i = 9; i > 0; i--) {
            startPointHash = serverCodings.encodeMD5B64(Long.toString(i + System.currentTimeMillis()), true).substring(2, 2 + yacySeedDB.commonHashLength);
            if (yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, startPointHash) > ((double) i / (double) 10)) return startPointHash;
        }
        // if that fails, take simply the best start point (this is usually avoided, since that leads to always the same target peers)
        startPointHash = yacyCore.seedDB.mySeed.hash.substring(0, 11) + "z";
        return startPointHash;
    }
    
    Object[] /* of {plasmaWordIndexEntity[], HashMap(String, plasmaCrawlLURL.Entry)}*/
            selectTransferIndexes(String hash, int count) {
        // the hash is a start hash from where the indexes are picked
        Vector tmpEntities = new Vector();
        String nexthash = "";
        try {
            Iterator wordHashIterator = wordIndex.wordHashes(hash, true, true);
            plasmaWordIndexEntity indexEntity, tmpEntity;
            Enumeration urlEnum;
            Iterator hashIter;
            plasmaWordIndexEntry indexEntry;
            plasmaCrawlLURL.Entry lurl;
            HashSet unknownURLEntries;
            HashMap knownURLs = new HashMap();
            while ((count > 0) && (wordHashIterator.hasNext()) &&
                   ((nexthash = (String) wordHashIterator.next()) != null) && (nexthash.trim().length() > 0)) {
                indexEntity = wordIndex.getEntity(nexthash, true);
                if (indexEntity.size() == 0) {
                    indexEntity.deleteComplete();
                } else if ((indexEntity.size() <= count)||        // if we havn't exceeded the limit
                           (Math.abs(indexEntity.size() - count) <= 10)){  // or there are only at most 10 entries left
                    // take the whole entity
                    try {
                        // fist check if we know all urls
                        urlEnum = indexEntity.elements(true);
                        unknownURLEntries = new HashSet();
                        while (urlEnum.hasMoreElements()) {
                            indexEntry = (plasmaWordIndexEntry) urlEnum.nextElement();
                            lurl = urlPool.loadedURL.getEntry(indexEntry.getUrlHash());
                            if ((lurl == null) || (lurl.toString() == null)) {
                                unknownURLEntries.add(indexEntry.getUrlHash());
                            } else {
                                if (lurl.toString() == null) {
                                    urlPool.loadedURL.remove(indexEntry.getUrlHash());
                                    unknownURLEntries.add(indexEntry.getUrlHash());
                                } else {
                                    knownURLs.put(indexEntry.getUrlHash(), lurl);
                                }
                            }
                        }
                        // now delete all entries that have no url entry
                        hashIter = unknownURLEntries.iterator();
                        while (hashIter.hasNext()) {
                            indexEntity.removeEntry((String) hashIter.next(), false);
                        }
                        // use whats remaining
                        tmpEntities.add(indexEntity);
                        log.logFine("Selected whole index (" + indexEntity.size() + " URLs, " + unknownURLEntries.size() + " not bound) for word " + indexEntity.wordHash());
                        count -= indexEntity.size();
                    } catch (kelondroException e) {
                        log.logSevere("plasmaWordIndexDistribution/1: deleted DB for word " + indexEntity.wordHash(), e);
                        try {indexEntity.deleteComplete();} catch (IOException ee) {}
                    }
                } else {
                    // make an on-the-fly entity and insert values
                    tmpEntity = new plasmaWordIndexEntity(indexEntity.wordHash());
                    try {
                        urlEnum = indexEntity.elements(true);
                        unknownURLEntries = new HashSet();
                        while ((urlEnum.hasMoreElements()) && (count > 0)) {
                            indexEntry = (plasmaWordIndexEntry) urlEnum.nextElement();
                            lurl = urlPool.loadedURL.getEntry(indexEntry.getUrlHash());
                            if (lurl == null) {
                                unknownURLEntries.add(indexEntry.getUrlHash());
                            } else {
                                if (lurl.toString() == null) {
                                    urlPool.loadedURL.remove(indexEntry.getUrlHash());
                                    unknownURLEntries.add(indexEntry.getUrlHash());
                                } else {
                                    knownURLs.put(indexEntry.getUrlHash(), lurl);
                                    tmpEntity.addEntry(indexEntry);
                                    count--;
                                }
                            }
                        }
                        // now delete all entries that have no url entry
                        hashIter = unknownURLEntries.iterator();
                        while (hashIter.hasNext()) {
                            indexEntity.removeEntry((String) hashIter.next(), true);
                        }
                        // use whats remaining
                        log.logFine("Selected partial index (" + tmpEntity.size() + " from " + indexEntity.size() +" URLs, " + unknownURLEntries.size() + " not bound) for word " + tmpEntity.wordHash());
                        tmpEntities.add(tmpEntity);
                    } catch (kelondroException e) {
                        log.logSevere("plasmaWordIndexDistribution/2: deleted DB for word " + indexEntity.wordHash(), e);
                        try {indexEntity.deleteComplete();} catch (IOException ee) {}
                    }
                    indexEntity.close(); // important: is not closed elswhere and cannot be deleted afterwards
                    indexEntity = null;
                }
                
            }
            // transfer to array
            plasmaWordIndexEntity[] indexEntities = new plasmaWordIndexEntity[tmpEntities.size()];
            for (int i = 0; i < tmpEntities.size(); i++) indexEntities[i] = (plasmaWordIndexEntity) tmpEntities.elementAt(i);
            return new Object[]{indexEntities, knownURLs};
        } catch (IOException e) {
            log.logSevere("selectTransferIndexes IO-Error (hash=" + nexthash + "): " + e.getMessage(), e);
            return new Object[]{new plasmaWordIndexEntity[0], new HashMap()};
        } catch (kelondroException e) {
            log.logSevere("selectTransferIndexes database corrupted: " + e.getMessage(), e);
            return new Object[]{new plasmaWordIndexEntity[0], new HashMap()};
        }
    }
    
    boolean deleteTransferIndexes(plasmaWordIndexEntity[] indexEntities) throws IOException {
        String wordhash;
        Enumeration urlEnum;
        plasmaWordIndexEntry indexEntry;
        plasmaWordIndexEntity indexEntity;
        String[] urlHashes;
        int sz;
        boolean success = true;
        for (int i = 0; i < indexEntities.length; i++) {
            if (indexEntities[i].isTMPEntity()) {
                // delete entries separately
                int c = 0;
                urlHashes = new String[indexEntities[i].size()];
                urlEnum = indexEntities[i].elements(true);
                while (urlEnum.hasMoreElements()) {
                    indexEntry = (plasmaWordIndexEntry) urlEnum.nextElement();
                    urlHashes[c++] = indexEntry.getUrlHash();
                }
                wordIndex.removeEntries(indexEntities[i].wordHash(), urlHashes, true);
                indexEntity = wordIndex.getEntity(indexEntities[i].wordHash(), true);
                sz = indexEntity.size();
                indexEntity.close();
                log.logFine("Deleted partial index (" + c + " URLs) for word " + indexEntities[i].wordHash() + "; " + sz + " entries left");
                // DEBUG: now try to delete the remaining index. If this works, this routine is fine
                /*
                if (wordIndex.getEntity(indexEntities[i].wordHash()).deleteComplete())
                    System.out.println("DEBUG: trial delete of partial word index " + indexEntities[i].wordHash() + " SUCCESSFULL");
                else
                    System.out.println("DEBUG: trial delete of partial word index " + indexEntities[i].wordHash() + " FAILED");
                 */
                // end debug
                indexEntities[i].close();
            } else {
                // delete complete file
                if (indexEntities[i].deleteComplete()) {
                    indexEntities[i].close();
                } else {
                    indexEntities[i].close();
                    // have another try...
                    if (!(plasmaWordIndexEntity.wordHash2path(wordIndex.getRoot() /*PLASMADB*/, indexEntities[i].wordHash()).delete())) {
                        success = false;
                        log.logSevere("Could not delete whole index for word " + indexEntities[i].wordHash());
                    }
                }
            }
            indexEntities[i] = null;
        }
        return success;
    }
    

    public void startTransferWholeIndex(yacySeed seed, boolean delete) {
        if (transferIdxThread == null) {
            this.transferIdxThread = new transferIndexThread(seed,delete);
            this.transferIdxThread.start();
        }
    }    
    
    public void stopTransferWholeIndex(boolean wait) {
        if ((transferIdxThread != null) && (transferIdxThread.isAlive()) && (!transferIdxThread.isFinished())) {
            try {
                this.transferIdxThread.stopIt(wait);
            } catch (InterruptedException e) { }
        }
    }    

    public void abortTransferWholeIndex(boolean wait) {
        if (transferIdxThread != null) {
            if (!transferIdxThread.isFinished())
                try {
                    this.transferIdxThread.stopIt(wait);
                } catch (InterruptedException e) { }
            transferIdxThread = null;
        }
    } 
    
    
    public class transferIndexThread extends Thread {

        private yacySeed seed = null;
        private boolean delete = false;
        private boolean finished = false;
        private int transferedIndexCount = 0;
        private String status = "running";
        private String oldStartingPointHash = "------------", startPointHash = "------------";
        private int wordsDBSize = 0;
        
        public transferIndexThread(yacySeed seed, boolean delete) {
            this.seed = seed;
            this.delete = delete;
            this.wordsDBSize = plasmaSwitchboard.getSwitchboard().wordIndex.size();
        }
        
        public void run() {
            performTransferWholeIndex();
        }
        
        public void stopIt(boolean wait) throws InterruptedException {
            this.finished = true;
            this.join();
        }
        
        public boolean isFinished() {
            return this.finished;
        }
        
        public int getTransferedIndexCount() {
            return this.transferedIndexCount;
        }
        
        public float getTransferedIndexPercent() {
            if (wordsDBSize == 0) return 100;
            else return (float)(this.transferedIndexCount*100/wordsDBSize);
        }
        
        public yacySeed getSeed() {
            return this.seed;
        }
        
        public String getStatus() {
            return this.status;
        }
        
        public String getRange() {
            return "[" + oldStartingPointHash + " .. " + startPointHash + "]";
        }
        
        public void performTransferWholeIndex() {
            try {
                plasmaWordIndexDistribution.this.paused = true;
                
                // collect index                
                plasmaWordIndexDistribution.this.log.logFine("Selected hash " + startPointHash + " as start point for index distribution of whole index");        
                
                long start;
                while (!finished && !Thread.currentThread().isInterrupted()) {
                    start = System.currentTimeMillis();
                    Object[] selectResult = selectTransferIndexes(startPointHash, 500);
                    plasmaWordIndexEntity[] indexEntities = (plasmaWordIndexEntity[]) selectResult[0];
                    if (finished || Thread.currentThread().isInterrupted()) {
                        this.status = "aborted";
                        return;
                    }
                    
                    HashMap urlCache = (HashMap) selectResult[1]; // String (url-hash) / plasmaCrawlLURL.Entry 
                    if ((indexEntities == null) || (indexEntities.length == 0)) {
                        plasmaWordIndexDistribution.this.log.logFine("No index available for index transfer, hash start-point " + startPointHash);
                        this.status = "finished.";
                        return;
                    }
                    // count the indexes again, can be smaller as expected
                    int idxCount = 0;
                    for (int i = 0; i < indexEntities.length; i++) {
                        idxCount += indexEntities[i].size();
                    }
                    
                    // find start point for DHT-selection
                    oldStartingPointHash = startPointHash;
                    startPointHash = indexEntities[indexEntities.length - 1].wordHash(); // DHT targets must have greater hashes
                    
                    plasmaWordIndexDistribution.this.log.logInfo("Index selection of " + idxCount + " words [" + indexEntities[0].wordHash() + " .. " + indexEntities[indexEntities.length-1].wordHash() + "]" +
                            " in " +
                            ((System.currentTimeMillis() - start) / 1000) + " seconds (" +
                            (1000 * idxCount / (System.currentTimeMillis() - start + 1)) + " words/s)");                     
                    
                    start = System.currentTimeMillis();
                    String error = yacyClient.transferIndex(seed, indexEntities, urlCache);
                    if (error == null) {
                        plasmaWordIndexDistribution.this.log.logInfo("Index transfer of " + idxCount + " words [" + indexEntities[0].wordHash() + " .. " + indexEntities[indexEntities.length-1].wordHash() + "]" +
                                " to peer " + seed.getName() + ":" + seed.hash + " in " +
                                ((System.currentTimeMillis() - start) / 1000) + " seconds successfull (" +
                                (1000 * idxCount / (System.currentTimeMillis() - start + 1)) + " words/s)");                
                    } else {
                        plasmaWordIndexDistribution.this.log.logWarning("Index transfer to peer " + seed.getName() + ":" + seed.hash + " failed:'" + error + "', disconnecting peer");
                        yacyCore.peerActions.peerDeparture(seed);
                        this.status = "Disconnected peer";
                        return;
                    }            
                    
                    if (delete) {
                        try {
                            if (deleteTransferIndexes(indexEntities)) {
                                plasmaWordIndexDistribution.this.log.logFine("Deleted all " + indexEntities.length + " transferred whole-word indexes locally");
                                transferedIndexCount += idxCount;
                            } else {
                                plasmaWordIndexDistribution.this.log.logSevere("Deleted not all transferred whole-word indexes");
                            }
                        } catch (IOException ee) {
                            plasmaWordIndexDistribution.this.log.logSevere("Deletion of indexes not possible:" + ee.getMessage(), ee);
                        }
                    } else {
                        // simply close the indexEntities
                        for (int i = 0; i < indexEntities.length; i++) try {
                            indexEntities[i].close();
                        } catch (IOException ee) {}
                        transferedIndexCount += idxCount;
                    }
                }
                this.status = "aborted";
            } finally {
                plasmaWordIndexDistribution.this.paused = false;
            }
        }    
    }
}
