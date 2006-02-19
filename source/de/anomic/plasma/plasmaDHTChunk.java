// plasmaDHTChunk.java 
// ------------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created: 18.02.2006
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

package de.anomic.plasma;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.yacy.yacySeedDB;

public class plasmaDHTChunk {

    public static final int chunkStatus_UNDEFINED = -1;
    public static final int chunkStatus_FAILED = 0;
    public static final int chunkStatus_FILLED = 1;
    public static final int chunkStatus_RUNNING = 2;
    public static final int chunkStatus_INTERRUPTED = 3;
    public static final int chunkStatus_COMPLETE = 4;
    
    private plasmaWordIndex wordIndex;
    private serverLog log;
    private plasmaCrawlLURL lurls;
    
    private int status = chunkStatus_UNDEFINED;
    private String statusMessage = "";
    private String startPointHash;
    private plasmaWordIndexEntryContainer[] indexContainers = null;
    private HashMap urlCache; // String (url-hash) / plasmaCrawlLURL.Entry
    private int idxCount;
    
    public plasmaWordIndexEntryContainer firstContainer() {
        return indexContainers[0];
    }
    
    public plasmaWordIndexEntryContainer lastContainer() {
        return indexContainers[indexContainers.length - 1];
    }
    
    public plasmaWordIndexEntryContainer[] containers() {
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
    
    public HashMap urlCacheMap() {
        return urlCache;
    }
    
    public void setStatus(int newStatus) {
        this.status = newStatus;
    }
    
    public int getStatus() {
        return this.status;
    }
    
    public void setStatusMessage(String message) {
        this.statusMessage = message;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public plasmaDHTChunk(serverLog log, plasmaWordIndex wordIndex, plasmaCrawlLURL lurls, int minCount, int maxCount) {
        this.log = log;
        this.wordIndex = wordIndex;
        this.lurls = lurls;
        startPointHash = selectTransferStart();
        log.logFine("Selected hash " + startPointHash + " as start point for index distribution, distance = " + yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, startPointHash));
        selectTransferContainers(startPointHash, minCount, maxCount);

        // count the indexes, can be smaller as expected
        this.idxCount = indexCounter();
        if (idxCount < minCount) {
            log.logFine("Too few (" + idxCount + ") indexes selected for transfer.");
            this.status = chunkStatus_FAILED;
        }
    }

    public plasmaDHTChunk(serverLog log, plasmaWordIndex wordIndex, plasmaCrawlLURL lurls, int minCount, int maxCount, String startHash) {
        this.log = log;
        this.wordIndex = wordIndex;
        this.lurls = lurls;
        log.logFine("Demanded hash " + startHash + " as start point for index distribution, distance = " + yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, startPointHash));
        selectTransferContainers(startHash, minCount, maxCount);

        // count the indexes, can be smaller as expected
        this.idxCount = indexCounter();
        if (idxCount < minCount) {
            log.logFine("Too few (" + idxCount + ") indexes selected for transfer.");
            this.status = chunkStatus_FAILED;
        }
    }

    private String selectTransferStart() {
        String startPointHash;
        // first try to select with increasing probality a good start point
        if (Math.round(Math.random() * 6) != 4)
            for (int i = 9; i > 0; i--) {
                startPointHash = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(i + System.currentTimeMillis()))).substring(2, 2 + yacySeedDB.commonHashLength);
                if (yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, startPointHash) > ((double) i / (double) 10))
                    return startPointHash;
            }
        // if that fails, take simply the best start point (this is usually avoided, since that leads to always the same target peers)
        startPointHash = yacyCore.seedDB.mySeed.hash.substring(0, 11) + "z";
        return startPointHash;
    }

    private void selectTransferContainers(String hash, int mincount, int maxcount) {
        int refcountRAM = selectTransferContainersResource(hash, plasmaWordIndex.RL_RAMCACHE, maxcount);
        if (refcountRAM >= mincount) {
            log.logFine("DHT selection from RAM: " + refcountRAM + " entries");
            return;
        }
        int refcountFile = selectTransferContainersResource(hash, plasmaWordIndex.RL_WORDFILES, maxcount);
        log.logFine("DHT selection from FILE: " + refcountFile + " entries, RAM provided only " + refcountRAM + " entries");
        return;
    }

    private int  selectTransferContainersResource(String hash, int resourceLevel, int maxcount) {
        // the hash is a start hash from where the indexes are picked
        ArrayList tmpContainers = new ArrayList(maxcount);
        String nexthash = "";
        try {
            Iterator wordHashIterator = wordIndex.wordHashes(hash, resourceLevel, true);
            plasmaWordIndexEntryContainer indexContainer;
            Iterator urlIter;
            plasmaWordIndexEntry indexEntry;
            plasmaCrawlLURL.Entry lurl;
            int refcount = 0;

            urlCache = new HashMap();
            while ((maxcount > refcount) && (wordHashIterator.hasNext()) && ((nexthash = (String) wordHashIterator.next()) != null) && (nexthash.trim().length() > 0)
                            && ((tmpContainers.size() == 0) || (yacyDHTAction.dhtDistance(nexthash, ((plasmaWordIndexEntryContainer) tmpContainers.get(0)).wordHash()) < 0.2))) {
                // make an on-the-fly entity and insert values
                indexContainer = wordIndex.getContainer(nexthash, true, 10000);
                int notBoundCounter = 0;
                try {
                    urlIter = indexContainer.entries();
                    // iterate over indexes to fetch url entries and store them in the urlCache
                    while ((urlIter.hasNext()) && (maxcount > refcount)) {
                        indexEntry = (plasmaWordIndexEntry) urlIter.next();
                        try {
                            lurl = lurls.getEntry(indexEntry.getUrlHash(), indexEntry);
                            if ((lurl == null) || (lurl.url() == null)) {
                                notBoundCounter++;
                                urlIter.remove();
                                wordIndex.removeEntries(nexthash, new String[] { indexEntry.getUrlHash() }, true);
                            } else {
                                urlCache.put(indexEntry.getUrlHash(), lurl);
                                refcount++;
                            }
                        } catch (IOException e) {
                            notBoundCounter++;
                            urlIter.remove();
                            wordIndex.removeEntries(nexthash, new String[] { indexEntry.getUrlHash() }, true);
                        }
                    }

                    // remove all remaining; we have enough
                    while (urlIter.hasNext()) {
                        indexEntry = (plasmaWordIndexEntry) urlIter.next();
                        urlIter.remove();
                    }

                    // use whats left
                    log.logFine("Selected partial index (" + indexContainer.size() + " from " + wordIndex.indexSize(nexthash) + " URLs, " + notBoundCounter + " not bound) for word " + indexContainer.wordHash());
                    tmpContainers.add(indexContainer);
                } catch (kelondroException e) {
                    log.logSevere("plasmaWordIndexDistribution/2: deleted DB for word " + nexthash, e);
                    wordIndex.deleteIndex(nexthash);
                }
            }
            // create result
            indexContainers = (plasmaWordIndexEntryContainer[]) tmpContainers.toArray(new plasmaWordIndexEntryContainer[tmpContainers.size()]);

            if ((indexContainers == null) || (indexContainers.length == 0)) {
                log.logFine("No index available for index transfer, hash start-point " + startPointHash);
                this.status = chunkStatus_FAILED;
                return 0;
            }

            this.status = chunkStatus_FILLED;
            
            return refcount;
        } catch (kelondroException e) {
            log.logSevere("selectTransferIndexes database corrupted: " + e.getMessage(), e);
            indexContainers = new plasmaWordIndexEntryContainer[0];
            urlCache = new HashMap();
            
            this.status = chunkStatus_FAILED;
            
            return 0;
        }
    }

    public int deleteTransferIndexes() {
        Iterator urlIter;
        plasmaWordIndexEntry indexEntry;
        String[] urlHashes;
        int count = 0;
        synchronized (wordIndex) {
            for (int i = 0; i < this.indexContainers.length; i++) {
                // delete entries separately
                int c = 0;
                urlHashes = new String[this.indexContainers[i].size()];
                urlIter = this.indexContainers[i].entries();
                while (urlIter.hasNext()) {
                    indexEntry = (plasmaWordIndexEntry) urlIter.next();
                    urlHashes[c++] = indexEntry.getUrlHash();
                }
                count += wordIndex.removeEntries(this.indexContainers[i].wordHash(), urlHashes, true);
                log.logFine("Deleted partial index (" + c + " URLs) for word " + this.indexContainers[i].wordHash() + "; " + this.wordIndex.indexSize(indexContainers[i].wordHash()) + " entries left");
                this.indexContainers[i] = null;
            }
        }
        return count;
    }
    
}
