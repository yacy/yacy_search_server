//plasmaWordIndexDistribution.java 
//-------------------------------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.HashMap;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;

public final class plasmaWordIndexDistribution {

    // distributes parts of the index to other peers
    // stops as soon as an error occurrs

    private int indexCount;
    private int juniorPeerCount, seniorPeerCount;
    private long maxTime;

    private final plasmaURLPool urlPool;
    private final plasmaWordIndex wordIndex;
    final serverLog log;
    boolean paused = false;
    private boolean enabled;
    private boolean enabledWhileCrawling;
    private boolean closed;
    private boolean gzipBody4Distribution;
    private int timeout4Distribution;
    public transferIndexThread transferIdxThread = null;

    public plasmaWordIndexDistribution(
            plasmaURLPool urlPool, 
            plasmaWordIndex wordIndex, 
            serverLog log,
            boolean enable, 
            boolean enabledWhileCrawling, 
            boolean gzipBody, 
            int timeout
    ) {
        this.urlPool = urlPool;
        this.wordIndex = wordIndex;
        this.enabled = enable;
        this.enabledWhileCrawling = enabledWhileCrawling;
        this.log = log;
        this.closed = false;
        setCounts(100 /*indexCount*/,  1 /*juniorPeerCount*/, 3 /*seniorPeerCount*/, 8000);
        this.gzipBody4Distribution = gzipBody;
        this.timeout4Distribution = timeout;
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
            log.logFine("no DHT distribution: closed");
            return false;
        }
        if (yacyCore.seedDB == null) {
            log.logFine("no DHT distribution: seedDB == null");
            return false;
        }
        if (yacyCore.seedDB.mySeed == null) {
            log.logFine("no DHT distribution: mySeed == null");
            return false;
        }
        if (yacyCore.seedDB.mySeed.isVirgin()) {
            log.logFine("no DHT distribution: status is virgin");
            return false;
        }
        if (!(enabled)) {
            log.logFine("no DHT distribution: not enabled");
            return false;
        }
        if (paused) {
            log.logFine("no DHT distribution: paused");
            return false;            
        }
        if (urlPool.loadedURL.size() < 10) {
            log.logFine("no DHT distribution: loadedURL.size() = " + urlPool.loadedURL.size());
            return false;
        }
        if (wordIndex.size() < 100) {
            log.logFine("no DHT distribution: not enough words - wordIndex.size() = " + wordIndex.size());
            return false;
        }
        if ((!enabledWhileCrawling) && (urlPool.noticeURL.stackSize() > 0)) {
            log.logFine("no DHT distribution: crawl in progress - noticeURL.stackSize() = " + urlPool.noticeURL.stackSize());
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
        Object[] selectResult = selectTransferContainers(startPointHash, indexCount);
        plasmaWordIndexEntryContainer[] indexContainers = (plasmaWordIndexEntryContainer[]) selectResult[0];
        HashMap urlCache = (HashMap) selectResult[1]; // String (url-hash) / plasmaCrawlLURL.Entry 
        if ((indexContainers == null) || (indexContainers.length == 0)) {
            log.logFine("No index available for index transfer, hash start-point " + startPointHash);
            return -1;
        }
        // count the indexes again, can be smaller as expected
        indexCount = 0;
        for (int i = 0; i < indexContainers.length; i++) {
            indexCount += indexContainers[i].size();
        }
        if (indexCount < 50) {
            log.logFine("Too few (" + indexCount + ") indexes selected for transfer.");
            closeTransferIndexes(indexContainers);
            return -1; // failed
        }

        // find start point for DHT-selection
        String keyhash = indexContainers[indexContainers.length - 1].wordHash(); // DHT targets must have greater hashes

        // find a list of DHT-peers
        yacySeed[] seeds = new yacySeed[peerCount + 10];
        int hc0 = 0;
        double ownDistance = Math.min(yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, indexContainers[0].wordHash()),
                                      yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, indexContainers[indexContainers.length - 1].wordHash()));
        double maxDistance = Math.min(ownDistance, 0.4);
        synchronized (yacyCore.dhtAgent) {
            double avdist;
            Enumeration e = yacyCore.dhtAgent.getAcceptRemoteIndexSeeds(keyhash);
            while ((e.hasMoreElements()) && (hc0 < seeds.length)) {
                if (closed) {
                    log.logSevere("Index distribution interrupted by close, nothing deleted locally.");
                    return -1; // interrupted
                }
                seeds[hc0] = (yacySeed) e.nextElement();
                if (seeds[hc0] != null) {
                    avdist = Math.max(yacyDHTAction.dhtDistance(seeds[hc0].hash, indexContainers[0].wordHash()),
                                      yacyDHTAction.dhtDistance(seeds[hc0].hash, indexContainers[indexContainers.length - 1].wordHash()));
                    if (avdist < maxDistance) {
                        log.logInfo("Selected " + ((hc0 < peerCount) ? "primary" : "reserve") + " DHT target peer " + seeds[hc0].getName() + ":" + seeds[hc0].hash + ", distance = " + avdist);
                        hc0++;
                    }
                }
            }
            e = null; // finish enumeration
        }
        
        if (hc0 < peerCount) {
            log.logWarning("found not enough (" + hc0 + ") peers for distribution");
            closeTransferIndexes(indexContainers);
            return -1; // failed
        }
        
        // send away the indexes to all these indexes
        String error;
        String peerNames = "";
        long start;
        int hc1 = 0;
        for (int i = 0; i < hc0; i++) {
            if (closed) {
                log.logSevere("Index distribution interrupted by close, nothing deleted locally.");
                return -1; // interrupted
            }
            start = System.currentTimeMillis();
            error = yacyClient.transferIndex(
                            seeds[i],
                            indexContainers,
                            urlCache,
                            this.gzipBody4Distribution,
                            this.timeout4Distribution);
            if (error == null) {
                log.logInfo("Index transfer of " + indexCount + " words [" + indexContainers[0].wordHash() + " .. " + indexContainers[indexContainers.length - 1].wordHash() + "] to peer " + seeds[i].getName() + ":" + seeds[i].hash + " in " + ((System.currentTimeMillis() - start) / 1000)
                                + " seconds successfull (" + (1000 * indexCount / (System.currentTimeMillis() - start + 1)) + " words/s)");
                peerNames += ", " + seeds[i].getName();
                hc1++;
            } else {
                log.logWarning("Index transfer to peer " + seeds[i].getName() + ":" + seeds[i].hash + " failed:'" + error + "', disconnecting peer");
                yacyCore.peerActions.peerDeparture(seeds[i]);
            }
            if (hc1 >= peerCount) break;
        }
        if (peerNames.length() > 0) peerNames = peerNames.substring(2); // remove comma

        // clean up and finish with deletion of indexes
        if (hc1 >= peerCount) {
            // success
            if (delete) {
                int deletedURLs = deleteTransferIndexes(indexContainers);
                log.logFine("Deleted from " + indexContainers.length + " transferred RWIs locally, removed " + deletedURLs + " URL references");
                return indexCount;
            } else {
                // simply close the indexEntities
                closeTransferIndexes(indexContainers);
            }
            return indexCount;
        } else {
            log.logSevere("Index distribution failed. Too few peers (" + hc1 + ") received the index, not deleted locally.");
            // simply close the indexEntities
            closeTransferIndexes(indexContainers);
            return -1;
        }
    }

    private String selectTransferStart() {
        String startPointHash;
        // first try to select with increasing probality a good start point
        if (Math.round(Math.random() * 6) != 4) for (int i = 9; i > 0; i--) {
            startPointHash = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(i + System.currentTimeMillis()))).substring(2, 2 + yacySeedDB.commonHashLength);
            if (yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, startPointHash) > ((double) i / (double) 10)) return startPointHash;
        }
        // if that fails, take simply the best start point (this is usually avoided, since that leads to always the same target peers)
        startPointHash = yacyCore.seedDB.mySeed.hash.substring(0, 11) + "z";
        return startPointHash;
    }

    Object[] /* of {plasmaWordIndexEntryContainer[], HashMap(String, plasmaCrawlLURL.Entry)}*/
           selectTransferContainers(String hash, int count) {
        // the hash is a start hash from where the indexes are picked
        ArrayList tmpContainers = new ArrayList(count);
        String nexthash = "";
        try {
            Iterator wordHashIterator = this.wordIndex.wordHashes(hash, true, true);
            plasmaWordIndexEntryContainer indexContainer;
            Iterator urlIter;
            plasmaWordIndexEntry indexEntry;
            plasmaCrawlLURL.Entry lurl;
            int notBoundCounter = 0;
            final HashMap knownURLs = new HashMap();
            while (
                    (count > 0) &&
                    (wordHashIterator.hasNext()) &&
                    ((nexthash = (String) wordHashIterator.next()) != null) && 
                    (nexthash.trim().length() > 0) &&
                    ((tmpContainers.size() == 0) ||
                     (yacyDHTAction.dhtDistance(nexthash, ((plasmaWordIndexEntryContainer)tmpContainers.get(0)).wordHash()) < 0.2))
            ) {
                // make an on-the-fly entity and insert values
                    indexContainer = this.wordIndex.getContainer(nexthash, true, 10000);
                    try {
                        urlIter = indexContainer.entries();
                        // iterate over indexes to fetch url entries and store them in the urlCache
                        while ((urlIter.hasNext()) && (count > 0)) {
                            indexEntry = (plasmaWordIndexEntry) urlIter.next();
                            try {
                                lurl = this.urlPool.loadedURL.getEntry(indexEntry.getUrlHash(), indexEntry);
                                if ((lurl == null) || (lurl.url() == null)) {
                                    notBoundCounter++;
                                    urlIter.remove();
                                    this.wordIndex.removeEntries(nexthash, new String[]{indexEntry.getUrlHash()}, true);
                                } else {
                                    knownURLs.put(indexEntry.getUrlHash(), lurl);
                                    count--;
                                }
                            } catch (IOException e) {
                                notBoundCounter++;
                                urlIter.remove();
                                this.wordIndex.removeEntries(nexthash, new String[]{indexEntry.getUrlHash()}, true);
                            }
                        }
                        
                        // remove all remaining; we have enough
                        while (urlIter.hasNext()) {
                            indexEntry = (plasmaWordIndexEntry) urlIter.next();
                            urlIter.remove();
                        }
                        
                        // use whats left
                        this.log.logFine("Selected partial index (" + indexContainer.size() + " from " + this.wordIndex.indexSize(nexthash) +" URLs, " + notBoundCounter + " not bound) for word " + indexContainer.wordHash());
                        tmpContainers.add(indexContainer);
                    } catch (kelondroException e) {
                        this.log.logSevere("plasmaWordIndexDistribution/2: deleted DB for word " + nexthash, e);
                        this.wordIndex.deleteIndex(nexthash);
                    }
            }
            // transfer to array
            plasmaWordIndexEntryContainer[] entryContainers = (plasmaWordIndexEntryContainer[]) tmpContainers.toArray(new plasmaWordIndexEntryContainer[tmpContainers.size()]);
            return new Object[]{entryContainers, knownURLs};
        } catch (kelondroException e) {
            this.log.logSevere("selectTransferIndexes database corrupted: " + e.getMessage(), e);
            return new Object[]{new plasmaWordIndexEntity[0], new HashMap(0)};
        }
    }

    void closeTransferIndex(plasmaWordIndexEntity indexEntity) throws IOException {
        Object migrationStatus;
        indexEntity.close();
        try {
            String wordhash = indexEntity.wordHash();
            migrationStatus = wordIndex.migrateWords2Assortment(wordhash);
            if (migrationStatus instanceof Integer) {
                int migrationCount = ((Integer) migrationStatus).intValue();
                if (migrationCount == 0)
                    log.logFine("SKIPPED  " + wordhash + ": empty");
                else if (migrationCount > 0)
                    log.logFine("MIGRATED " + wordhash + ": " + migrationCount + " entries");
                else
                    log.logFine("REVERSED " + wordhash + ": " + (-migrationCount) + " entries");
            } else if (migrationStatus instanceof String) {
                log.logFine("SKIPPED  " + wordhash + ": " + migrationStatus);
            }
        } catch (Exception e) {
            log.logWarning("EXCEPTION: ", e);
        }
    }

    void closeTransferIndexes(plasmaWordIndexEntity[] indexEntities) {
        for (int i = 0; i < indexEntities.length; i++) try {
            closeTransferIndex(indexEntities[i]);
        } catch (IOException ee) {}
    }

    void closeTransferIndexes(plasmaWordIndexEntryContainer[] indexContainers) {
        for (int i = 0; i < indexContainers.length; i++) {
            indexContainers[i] = null;
        }
    }

    int deleteTransferIndexes(plasmaWordIndexEntryContainer[] indexContainers) {
        Iterator urlIter;
        plasmaWordIndexEntry indexEntry;
        String[] urlHashes;
        int count = 0;
        for (int i = 0; i < indexContainers.length; i++) {
            // delete entries separately
            int c = 0;
            urlHashes = new String[indexContainers[i].size()];
            urlIter = indexContainers[i].entries();
            while (urlIter.hasNext()) {
                indexEntry = (plasmaWordIndexEntry) urlIter.next();
                urlHashes[c++] = indexEntry.getUrlHash();
            }
            count += wordIndex.removeEntries(indexContainers[i].wordHash(), urlHashes, true);
            log.logFine("Deleted partial index (" + c + " URLs) for word " + indexContainers[i].wordHash() + "; " + this.wordIndex.indexSize(indexContainers[i].wordHash()) + " entries left");
            indexContainers[i] = null;
        }
        return count;
    }

/*
    boolean deleteTransferIndexes(plasmaWordIndexEntity[] indexEntities) throws IOException {
        Iterator urlIter;
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
                urlIter = indexEntities[i].elements(true);
                while (urlIter.hasNext()) {
                    indexEntry = (plasmaWordIndexEntry) urlIter.next();
                    urlHashes[c++] = indexEntry.getUrlHash();
                }
                wordIndex.removeEntries(indexEntities[i].wordHash(), urlHashes, true);
                indexEntity = wordIndex.getEntity(indexEntities[i].wordHash(), true, -1);
                sz = indexEntity.size();
                // indexEntity.close();
                closeTransferIndex(indexEntity);
                log.logFine("Deleted partial index (" + c + " URLs) for word " + indexEntities[i].wordHash() + "; " + sz + " entries left");
                // end debug
                indexEntities[i].close();
            } else {
                // delete complete file
                if (indexEntities[i].deleteComplete()) {
                    indexEntities[i].close();
                } else {
                    indexEntities[i].close();
                    // have another try...
                    if (!(plasmaWordIndexEntity.wordHash2path(wordIndex.getRoot(), indexEntities[i].wordHash()).delete())) {
                        success = false;
                        log.logSevere("Could not delete whole index for word " + indexEntities[i].wordHash());
                    }
                }
            }
            indexEntities[i] = null;
        }
        return success;
    }
 */
 
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

    private class transferIndexWorkerThread extends Thread{
        // connection properties
        private boolean gzipBody4Transfer = false;
        private int timeout4Transfer = 60000;

        
        // status fields
        private boolean finished = false;
        boolean success = false;
        private String status = "running";
        private long iteration = 0;
        private long transferTime = 0;
        private int idxCount = 0;
        private int chunkSize = 0;
        
        // delivery destination
        yacySeed seed = null;
        
        // word chunk
        private String endPointHash;
        private String startPointHash;
        plasmaWordIndexEntryContainer[] indexContainers;
        
        // other fields
        HashMap urlCache;
        
        public transferIndexWorkerThread(
                yacySeed seed, 
                plasmaWordIndexEntryContainer[] indexContainers, 
                HashMap urlCache, 
                boolean gzipBody, 
                int timeout,
                long iteration, 
                int idxCount, 
                int chunkSize,
                String endPointHash,
                String startPointHash) {
            super(new ThreadGroup("TransferIndexThreadGroup"),"TransferIndexWorker_" + seed.getName());
            this.gzipBody4Transfer = gzipBody;
            this.timeout4Transfer = timeout;
            this.iteration = iteration;
            this.seed = seed;
            this.indexContainers = indexContainers;
            this.urlCache = urlCache;
            this.idxCount = idxCount;
            this.chunkSize = chunkSize;
            this.startPointHash = startPointHash;
            this.endPointHash = endPointHash;
        }
        
        public void run() {
            try {
                uploadIndex();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                
            }
        }
        
        public boolean success() {
            return this.success;
        }
        
        public int getChunkSize() {
            return this.chunkSize;            
        }
        
        public String getStatus() {
            return this.status;
        }
        
        private boolean isAborted() {
            if (finished  || Thread.currentThread().isInterrupted()) {
                return true;
            } 
            return false;
        }        
        
        public void stopIt() {
            this.finished = true;
        }
        
        public String getRange() {
            return "[" + startPointHash + ".." + endPointHash + "]";
        }
        
        public long getTransferTime() {
            return this.transferTime;
        }
        
        private void uploadIndex() throws InterruptedException {
            
            /* loop until we 
             * - have successfully transfered the words list or 
             * - the retry counter limit was exceeded
             */
            long retryCount = 0, start = System.currentTimeMillis();
            while (true) {
                // testing if we wer aborted
                if (isAborted()) return;
                
                // transfering seleted words to remote peer
                this.status = "Running: Transfering chunk " + iteration;
                String error = yacyClient.transferIndex(seed, indexContainers, urlCache, gzipBody4Transfer, timeout4Transfer);
                if (error == null) {
                    // words successfully transfered
                    transferTime = System.currentTimeMillis() - start;
                    plasmaWordIndexDistribution.this.log.logInfo("Index transfer of " + idxCount + " words [" + indexContainers[0].wordHash() + " .. " + indexContainers[indexContainers.length-1].wordHash() + "]" +
                            " to peer " + seed.getName() + ":" + seed.hash + " in " + (transferTime/1000) + " seconds successfull (" +
                            (1000 * idxCount / (transferTime + 1)) + " words/s)");
                    retryCount = 0;
                    
//                    if (transferTime > 30000) {
//                        if (chunkSize>100) chunkSize-=50;
//                    } else {
//                        chunkSize+=50;
//                    }
                    this.success = true;
                    this.status = "Finished: Transfer of chunk " + iteration;
                    break;
                } else {
                    // worts transfer failed
                    
                    // inc retry counter
                    retryCount++; 
                    
                    // testing if we were aborted ...
                    if (isAborted()) return;
                    
                    // we have lost the connection to the remote peer. Adding peer to disconnected list
                    plasmaWordIndexDistribution.this.log.logWarning("Index transfer to peer " + seed.getName() + ":" + seed.hash + " failed:'" + error + "', disconnecting peer");
                    yacyCore.peerActions.peerDeparture(seed);
                    
                    // if the retry counter limit was not exceeded we'll retry it in a few seconds
                    this.status = "Disconnected peer: " + ((retryCount > 5)? error + ". Transfer aborted":"Retry " + retryCount);
                    if (retryCount > 5) return;
                    Thread.sleep(retryCount*5000);
                    
                    /* loop until 
                     * - we have successfully done a peer ping or 
                     * - the retry counter limit was exceeded
                     */
                    while (true) {                                
                        // testing if we were aborted ...
                        if (isAborted()) return;
                        
                        // doing a peer ping to the remote seed
                        int added = yacyClient.publishMySeed(seed.getAddress(), seed.hash);                            
                        if (added < 0) {
                            // inc. retry counter
                            retryCount++; 
                            this.status = "Disconnected peer: Peer ping failed. " + ((retryCount > 5)?"Transfer aborted.":"Retry " + retryCount);
                            if (retryCount > 5) return;
                            Thread.sleep(retryCount*5000);
                            continue;
                        } else {
                            yacyCore.seedDB.getConnected(seed.hash);
                            this.status = "running";
                            break;
                        }             
                    }
                }          
            }
        }
    }
    
    public class transferIndexThread extends Thread {
        private yacySeed seed = null;
        private boolean delete = false;
        private boolean finished = false;
        private boolean gzipBody4Transfer = false;
        private int timeout4Transfer = 60000;
        private int transferedEntryCount = 0;
        private int transferedEntityCount = 0;
        private String status = "Running";
        private String oldStartingPointHash = "------------", startPointHash = "------------";
        private int initialWordsDBSize = 0;
        private int chunkSize = 500;   
        private final long startingTime = System.currentTimeMillis();
        private final plasmaSwitchboard sb;
        private transferIndexWorkerThread worker = null;
        
        public transferIndexThread(yacySeed seed, boolean delete) {
            super(new ThreadGroup("TransferIndexThreadGroup"),"TransferIndex_" + seed.getName());
            this.seed = seed;
            this.delete = delete;
            this.sb = plasmaSwitchboard.getSwitchboard();
            this.initialWordsDBSize = sb.wordIndex.size();   
            this.gzipBody4Transfer = "true".equalsIgnoreCase(sb.getConfig("indexTransfer.gzipBody","false"));
            this.timeout4Transfer = (int) sb.getConfigLong("indexTransfer.timeout",60000);
            //this.maxOpenFiles4Transfer = (int) sb.getConfigLong("indexTransfer.maxOpenFiles",800);
        }
        
        public void run() {
            performTransferWholeIndex();
        }
        
        public void stopIt(boolean wait) throws InterruptedException {
            this.finished = true;
            if (wait) this.join();
        }
        
        public boolean isFinished() {
            return this.finished;
        }
        
        public boolean deleteIndex() {
            return this.delete;
        }
        
        public int[] getChunkSize() {
            transferIndexWorkerThread workerThread = this.worker;
            if (workerThread != null) {
                return new int[]{this.chunkSize,workerThread.getChunkSize()};
            }
            return new int[]{this.chunkSize,500};
        }
        
        public int getTransferedEntryCount() {
            return this.transferedEntryCount;
        }
        
        public int getTransferedEntityCount() {
            return this.transferedEntityCount;
        }
        
        public float getTransferedEntityPercent() {
            long currentWordsDBSize = sb.wordIndex.size(); 
            if (initialWordsDBSize == 0) return 100;
            else if (currentWordsDBSize >= initialWordsDBSize) return 0;
            //else return (float) ((initialWordsDBSize-currentWordsDBSize)/(initialWordsDBSize/100));
            else return (float)(this.transferedEntityCount*100/initialWordsDBSize);
        }
        
        public int getTransferedEntitySpeed() {
            long transferTime = System.currentTimeMillis() - startingTime;
            if (transferTime <= 0) transferTime = 1;
            return (int) ((1000 * transferedEntryCount) / transferTime);
        }
        
        public yacySeed getSeed() {
            return this.seed;
        }
        
        public String[] getStatus() {
            transferIndexWorkerThread workerThread = this.worker;
            if (workerThread != null) {
                return new String[]{this.status,workerThread.getStatus()};
            }
            return new String[]{this.status,"Not running"};
        }
        
        public String[] getRange() {
            transferIndexWorkerThread workerThread = this.worker;
            if (workerThread != null) {
                return new String[]{"[" + oldStartingPointHash + ".." + startPointHash + "]",workerThread.getRange()};
            }
            return new String[]{"[" + oldStartingPointHash + ".." + startPointHash + "]","[------------..------------]"};
        }
        
        public void performTransferWholeIndex() {
            plasmaWordIndexEntryContainer[] newIndexContainers = null, oldIndexContainers = null;
            try {
                // pausing the regular index distribution
                // TODO: adding sync, to wait for a still running index distribution to finish
                plasmaWordIndexDistribution.this.paused = true;
                
                // initial startingpoint of intex transfer is "------------"                 
                plasmaWordIndexDistribution.this.log.logFine("Selected hash " + startPointHash + " as start point for index distribution of whole index");        
                
                /* Loop until we have
                 * - finished transfer of whole index
                 * - detected a server shutdown or user interruption
                 * - detected a failure
                 */
                long selectionStart = System.currentTimeMillis(), selectionEnd = 0, selectionTime = 0, iteration = 0;
                
                while (!finished && !Thread.currentThread().isInterrupted()) {
                    iteration++;
                    int idxCount = 0;
                    selectionStart = System.currentTimeMillis();
                    oldIndexContainers = newIndexContainers;
                    
                    // selecting 500 words to transfer
                    this.status = "Running: Selecting chunk " + iteration;
                    Object[] selectResult = selectTransferContainers(this.startPointHash, this.chunkSize);
                    newIndexContainers = (plasmaWordIndexEntryContainer[]) selectResult[0];                                        
                    HashMap urlCache = (HashMap) selectResult[1]; // String (url-hash) / plasmaCrawlLURL.Entry
                    
                    /* If we havn't selected a word chunk this could be because of
                     * a) no words are left in the index
                     * b) max open file limit was exceeded 
                     */
                    if ((newIndexContainers == null) || (newIndexContainers.length == 0)) {
                        if (sb.wordIndex.size() > 0) {
                            // if there are still words in the index we try it again now
                            startPointHash = "------------";
                        } else {                            
                            // otherwise we could end transfer now
                            plasmaWordIndexDistribution.this.log.logFine("No index available for index transfer, hash start-point " + startPointHash);
                            this.status = "Finished. " + iteration + " chunks transfered.";
                            finished = true; 
                        }
                    } else {
                        // count the indexes again, can be smaller as expected                    
                        for (int i = 0; i < newIndexContainers.length; i++) idxCount += newIndexContainers[i].size();
                        
                        // getting start point for next DHT-selection
                        oldStartingPointHash = startPointHash;
                        startPointHash = newIndexContainers[newIndexContainers.length - 1].wordHash(); // DHT targets must have greater hashes
                        
                        selectionEnd = System.currentTimeMillis();
                        selectionTime = selectionEnd - selectionStart;
                        plasmaWordIndexDistribution.this.log.logInfo("Index selection of " + idxCount + " words [" + newIndexContainers[0].wordHash() + " .. " + newIndexContainers[newIndexContainers.length-1].wordHash() + "]" +
                                " in " +
                                (selectionTime / 1000) + " seconds (" +
                                (1000 * idxCount / (selectionTime+1)) + " words/s)");                     
                    }
                    
                    // query status of old worker thread
                    if (worker != null) {
                        this.status = "Finished: Selecting chunk " + iteration;
                        worker.join();
                        if (!worker.success) {
                            // if the transfer failed we abort index transfer now
                            this.status = "Aborted because of Transfer error:\n" + worker.getStatus();
                            
                            // cleanup. closing all open files
                            closeContainers(oldIndexContainers);
                            oldIndexContainers = null;
                            closeContainers(newIndexContainers);
                            newIndexContainers = null;
                            
                            // abort index transfer
                            return;
                        } else {
                            /* 
                             * If index transfer was done successfully we close all remaining open
                             * files that belong to the old index chunk and handover a new chunk
                             * to the transfer thread.
                             * Addintionally we recalculate the chunk size to optimize performance
                             */
                            
                            this.chunkSize = worker.getChunkSize();
                            long transferTime = worker.getTransferTime();
                            //TODO: only increase chunk Size if there is free memory left on the server
                            
                            // we need aprox. 73Byte per IndexEntity and an average URL length of 32 char
                            //if (ft.freeMemory() < 73*2*100)                                                        
                            if (transferTime > 60*1000) {
                                if (chunkSize>200) chunkSize-=100;
                            } else if (selectionTime < transferTime){
                                this.chunkSize +=100;
                                //chunkSize+=50;
                            } else if (selectionTime >= selectionTime){
                                if (chunkSize>200) chunkSize-=100;
                            }
                            
                            selectionStart = System.currentTimeMillis();
                            
                            // deleting transfered words from index
                            if (delete) {
                                this.status = "Running: Deleting chunk " + iteration;
                                int urlReferences = deleteTransferIndexes(oldIndexContainers);
                                plasmaWordIndexDistribution.this.log.logFine("Deleted from " + oldIndexContainers.length + " transferred RWIs locally " + urlReferences + " URL references");
                                transferedEntryCount += idxCount;
                                transferedEntityCount += oldIndexContainers.length;
                            } else {
                                this.closeContainers(oldIndexContainers);
                                transferedEntryCount += idxCount;
                                transferedEntityCount += oldIndexContainers.length;
                            }
                            oldIndexContainers = null;
                        }
                        this.worker = null;
                    }
                    
                    // handover chunk to transfer worker
                    if (!((newIndexContainers == null) || (newIndexContainers.length == 0))) {
                        worker = new transferIndexWorkerThread(seed,newIndexContainers,urlCache,gzipBody4Transfer,timeout4Transfer,iteration,idxCount,idxCount,startPointHash,oldStartingPointHash);
                        worker.start();
                    }
                }
                
                // if we reach this point we were aborted by the user or by server shutdown
                if (sb.wordIndex.size() > 0) this.status = "aborted";
            } catch (Exception e) {
                this.status = "Error: " + e.getMessage();
                plasmaWordIndexDistribution.this.log.logWarning("Index transfer to peer " + seed.getName() + ":" + seed.hash + " failed:'" + e.getMessage() + "'",e);
                
            } finally {
                if (worker != null) {
                    worker.stopIt();
                    try {worker.join();}catch(Exception e){}
                    // worker = null;
                }
                if (oldIndexContainers != null) closeContainers(oldIndexContainers);
                if (newIndexContainers != null) closeContainers(newIndexContainers);
                
                plasmaWordIndexDistribution.this.paused = false;
            }
        }
        
        private void closeContainers(plasmaWordIndexEntryContainer[] indexContainers) {
            if ((indexContainers == null)||(indexContainers.length ==0)) return;
            
            for (int i = 0; i < indexContainers.length; i++) {
                indexContainers[i] = null;
            }
        }

    }

}
