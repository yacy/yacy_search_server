// plasmaDHTChunk.java 
// ------------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created: 18.02.2006
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

package de.anomic.plasma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIRowEntry;
import de.anomic.index.indexURLReference;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.yacy.yacySeedDB;

public class plasmaDHTChunk {

    public static final int chunkStatus_UNDEFINED   = -1;
    public static final int chunkStatus_FAILED      =  0;
    public static final int chunkStatus_FILLED      =  1;
    public static final int chunkStatus_RUNNING     =  2;
    public static final int chunkStatus_INTERRUPTED =  3;
    public static final int chunkStatus_COMPLETE    =  4;
    
    public static final int peerRedundancy = 3;
    
    private plasmaWordIndex wordIndex;
    private serverLog log;
    
    private int status = chunkStatus_UNDEFINED;
    private String startPointHash = "AAAAAAAAAAAA";
    private indexContainer[] indexContainers = null;
    private HashMap<String, indexURLReference> urlCache; // String (url-hash) / plasmaCrawlLURL.Entry
    private int idxCount;
    
    private long selectionStartTime = 0;
    private long selectionEndTime = 0;
    
    private int transferFailedCounter = 0;
    private int kelondroExceptionCounter = 0;
    
    public indexContainer firstContainer() {
        return indexContainers[0];
    }
    
    public indexContainer lastContainer() {
        return indexContainers[indexContainers.length - 1];
    }
    
    public indexContainer[] containers() {
        return indexContainers;
    }
    
    public int containerSize() {
        return indexContainers.length;
    }
    
    public int indexCount() {
        return this.idxCount;
    }
    
    private int indexCounter() {
        int c = 0;
        for (int i = 0; i < indexContainers.length; i++) {
            c += indexContainers[i].size();
        }
        return c;
    }
    
    public HashMap<String, indexURLReference> urlCacheMap() {
        return urlCache;
    }
    
    public void setStatus(final int newStatus) {
        this.status = newStatus;
    }
    
    public int getStatus() {
        return this.status;
    }
    
    public plasmaDHTChunk(final serverLog log, final plasmaWordIndex wordIndex, final int minCount, final int maxCount, final int maxtime) {
        try {
            this.log = log;
            this.wordIndex = wordIndex;
            this.startPointHash = selectTransferStart();
            if (this.log.isFine()) log.logFine("Selected hash " + this.startPointHash + " as start point for index distribution, distance = " + yacyDHTAction.dhtDistance(wordIndex.seedDB.mySeed().hash, this.startPointHash));
            selectTransferContainers(this.startPointHash, minCount, maxCount, maxtime);

            // count the indexes, can be smaller as expected
            this.idxCount = indexCounter();
            if (this.idxCount < minCount) {
                if (this.log.isFine()) log.logFine("Too few (" + this.idxCount + ") indexes selected for transfer.");
                this.status = chunkStatus_FAILED;
            }
        } catch (final InterruptedException e) {
            this.status = chunkStatus_INTERRUPTED;
        }
    }

    public plasmaDHTChunk(final serverLog log, final plasmaWordIndex wordIndex, final int minCount, final int maxCount, final int maxtime, final String startHash) {
        try {
            this.log = log;
            this.wordIndex = wordIndex;
            if (this.log.isFine()) log.logFine("Demanded hash " + startHash + " as start point for index distribution, distance = " + yacyDHTAction.dhtDistance(wordIndex.seedDB.mySeed().hash, this.startPointHash));
            selectTransferContainers(startHash, minCount, maxCount, maxtime);

            // count the indexes, can be smaller as expected
            this.idxCount = indexCounter();
            if (this.idxCount < minCount) {
                if (this.log.isFine()) log.logFine("Too few (" + this.idxCount + ") indexes selected for transfer.");
                this.status = chunkStatus_FAILED;
            }
        } catch (final InterruptedException e) {
            this.status = chunkStatus_INTERRUPTED;
        }
    }

    private String selectTransferStart() {
        String startPointHash;
        // first try to select with increasing probality a good start point
        final double minimumDistance = ((double) peerRedundancy) / ((double) wordIndex.seedDB.sizeConnected());
        double d, bestDistance = 0.0;
        String bestHash = null;
        for (int i = wordIndex.seedDB.sizeConnected() / 8; i > 0; i--) {
            startPointHash = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(i + System.currentTimeMillis()))).substring(2, 2 + yacySeedDB.commonHashLength);
            d = yacyDHTAction.dhtDistance(wordIndex.seedDB.mySeed().hash, startPointHash);
            if (d > (minimumDistance + ((double) i / (double) 10))) {
                return startPointHash;
            }
            if (d > bestDistance) {
                bestDistance = d;
                bestHash = startPointHash;
            }
        }
        // if that fails, take simply the best start point
        if (bestHash == null) {
            return wordIndex.seedDB.mySeed().hash.substring(0, 11) + "z";
        }
        return bestHash;
    }

    private void selectTransferContainers(final String hash, final int mincount, final int maxcount, final int maxtime) throws InterruptedException {        
        try {
            this.selectionStartTime = System.currentTimeMillis();
            final int refcountRAM = selectTransferContainersResource(hash, true, maxcount, maxtime);
            if (refcountRAM >= mincount) {
                if (this.log.isFine()) log.logFine("DHT selection from RAM: " + refcountRAM + " entries");
                return;
            }
            final int refcountFile = selectTransferContainersResource(hash, false, maxcount, maxtime);
            if (this.log.isFine()) log.logFine("DHT selection from FILE: " + refcountFile + " entries, RAM provided only " + refcountRAM + " entries");
            return;
        } finally {
            this.selectionEndTime = System.currentTimeMillis();
        }
    }

    private int selectTransferContainersResource(final String hash, final boolean ram, final int maxcount, final int maxtime) throws InterruptedException {
        // if (maxcount > 500) { maxcount = 500; } // flooding & OOM reduce
        // the hash is a start hash from where the indexes are picked
        final ArrayList<indexContainer> tmpContainers = new ArrayList<indexContainer>(maxcount);
        try {
            final Iterator<indexContainer> indexContainerIterator = wordIndex.indexContainerSet(hash, ram, true, maxcount).iterator();
            indexContainer container;
            Iterator<indexRWIRowEntry> urlIter;
            indexRWIRowEntry iEntry;
            indexURLReference lurl;
            int refcount = 0;
            int wholesize;

            urlCache = new HashMap<String, indexURLReference>();
            final double maximumDistance = ((double) peerRedundancy * 2) / (wordIndex.seedDB.sizeConnected());
            final long timeout = (maxtime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
            while (
                    (maxcount > refcount) &&
                    (indexContainerIterator.hasNext()) &&
                    ((container = indexContainerIterator.next()) != null) &&
                    (container.size() > 0) &&
                    ((tmpContainers.size() == 0) ||
                     (yacyDHTAction.dhtDistance(container.getWordHash(), tmpContainers.get(0).getWordHash()) < maximumDistance)) &&
                    (System.currentTimeMillis() < timeout)
            ) {
                // check for interruption
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress");

                // make an on-the-fly entity and insert values
                int notBoundCounter = 0;
                try {
                    wholesize = container.size();
                    urlIter = container.entries();
                    // iterate over indexes to fetch url entries and store them in the urlCache
                    while ((urlIter.hasNext()) && (maxcount > refcount) && (System.currentTimeMillis() < timeout)) {
                        // CPU & IO reduce
                        // try { Thread.sleep(50); } catch (InterruptedException e) { }

                        iEntry = urlIter.next();
                        if ((iEntry == null) || (iEntry.urlHash() == null)) {
                            urlIter.remove();
                            continue;
                        }
                        lurl = wordIndex.getURL(iEntry.urlHash(), iEntry, 0);
                        if ((lurl == null) || (lurl.comp() == null) || (lurl.comp().url() == null)) {
                            //yacyCore.log.logFine("DEBUG selectTransferContainersResource: not-bound url hash '" + iEntry.urlHash() + "' for word hash " + container.getWordHash());
                            notBoundCounter++;
                            urlIter.remove();
                            wordIndex.removeEntry(container.getWordHash(), iEntry.urlHash());
                        } else {
                            urlCache.put(iEntry.urlHash(), lurl);
                            //yacyCore.log.logFine("DEBUG selectTransferContainersResource: added url hash '" + iEntry.urlHash() + "' to urlCache for word hash " + container.getWordHash());
                            refcount++;
                        }
                    }

                    // remove all remaining; we have enough
                    while (urlIter.hasNext()) {
                        iEntry = urlIter.next();
                        urlIter.remove();
                    }

                    // use whats left
                    if (this.log.isFine()) log.logFine("Selected partial index (" + container.size() + " from " + wholesize + " URLs, " + notBoundCounter + " not bound) for word " + container.getWordHash());
                    tmpContainers.add(container);
                } catch (final kelondroException e) {
                    log.logSevere("plasmaWordIndexDistribution/2: deleted DB for word " + container.getWordHash(), e);
                    wordIndex.deleteContainer(container.getWordHash());
                }
            }
            // create result
            indexContainers = tmpContainers.toArray(new indexContainer[tmpContainers.size()]);
//[C[16GwGuFzwffp] has 1 entries, C[16hGKMAl0w97] has 9 entries, C[17A8cDPF6SfG] has 9 entries, C[17Kdj__WWnUy] has 1 entries, C[1
            if ((indexContainers == null) || (indexContainers.length == 0)) {
                if (this.log.isFine()) log.logFine("No index available for index transfer, hash start-point " + startPointHash);
                this.status = chunkStatus_FAILED;
                return 0;
            }

            this.status = chunkStatus_FILLED;
            return refcount;
        } catch (final kelondroException e) {
            log.logSevere("selectTransferIndexes database corrupted: " + e.getMessage(), e);
            indexContainers = new indexContainer[0];
            urlCache = new HashMap<String, indexURLReference>();
            this.status = chunkStatus_FAILED;
            kelondroExceptionCounter++;
            if (kelondroExceptionCounter > 50) wordIndex.deleteIndexOnExit(); // delete index on exit for rebuild
            return 0;
        }
    }

    public synchronized String deleteTransferIndexes() {
        Iterator<indexRWIRowEntry> urlIter;
        indexRWIEntry iEntry;
        HashSet<String> urlHashes;
        String count = "0";
        
        for (int i = 0; i < this.indexContainers.length; i++) {
            // delete entries separately
            if (this.indexContainers[i] == null) {
                if (this.log.isFine()) log.logFine("Deletion of partial index #" + i + " not possible, entry is null");
                continue;
            }
            final int c = this.indexContainers[i].size();
            urlHashes = new HashSet<String>(this.indexContainers[i].size());
            urlIter = this.indexContainers[i].entries();
            while (urlIter.hasNext()) {
                iEntry = urlIter.next();
                urlHashes.add(iEntry.urlHash());
            }
            final String wordHash = indexContainers[i].getWordHash();
            count = wordIndex.removeEntriesExpl(this.indexContainers[i].getWordHash(), urlHashes);
            if (log.isFine()) 
                if (this.log.isFine()) log.logFine("Deleted partial index (" + c + " URLs) for word " + wordHash + "; " + this.wordIndex.indexSize(wordHash) + " entries left");
            this.indexContainers[i] = null;
        }
        return count;
    }
    
    public long getSelectionTime() {
        if (this.selectionStartTime == 0 || this.selectionEndTime == 0) return -1;
        return this.selectionEndTime-this.selectionStartTime;
    }

    public void incTransferFailedCounter() {
        this.transferFailedCounter++;
    }

    public int getTransferFailedCounter() {
        return transferFailedCounter;
    }
}
