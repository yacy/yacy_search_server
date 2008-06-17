// indexRAMRI.java
// (C) 2005, 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package de.anomic.index;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroRow;
import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;

public final class indexRAMRI implements indexRI, indexRIReader {

    // class variables
    private final kelondroMScoreCluster<String> hashScore;
    private final kelondroMScoreCluster<String> hashDate;
    private long  initTime;
    private int   cacheEntityMaxCount;       // the maximum number of cache slots for RWI entries
    public  int   cacheReferenceCountLimit;  // the maximum number of references to a single RWI entity
    public  long  cacheReferenceAgeLimit;    // the maximum age (= time not changed) of a RWI entity
    private final serverLog log;
    private File indexHeapFile;
    private indexContainerHeap heap;
    
    @SuppressWarnings("unchecked")
    public indexRAMRI(File databaseRoot, kelondroRow payloadrow, int entityCacheMaxSize, int wCacheReferenceCountLimitInit, long wCacheReferenceAgeLimitInit, String oldArrayName, String newHeapName, serverLog log) {

        // creates a new index cache
        // the cache has a back-end where indexes that do not fit in the cache are flushed
        this.hashScore = new kelondroMScoreCluster<String>();
        this.hashDate  = new kelondroMScoreCluster<String>();
        this.initTime = System.currentTimeMillis();
        this.cacheEntityMaxCount = entityCacheMaxSize;
        this.cacheReferenceCountLimit = wCacheReferenceCountLimitInit;
        this.cacheReferenceAgeLimit = wCacheReferenceAgeLimitInit;
        this.log = log;
        this.indexHeapFile = new File(databaseRoot, newHeapName);
        this.heap = new indexContainerHeap(payloadrow, log);
        
        // read in dump of last session
        if (indexHeapFile.exists()) {
            try {
                heap.initWriteMode(indexHeapFile);
                for (indexContainer ic : (Iterable<indexContainer>) heap.wordContainers(null, false)) {
                    this.hashDate.setScore(ic.getWordHash(), intTime(ic.lastWrote()));
                    this.hashScore.setScore(ic.getWordHash(), ic.size());
                }
            } catch (IOException e){
                log.logSevere("unable to restore cache dump: " + e.getMessage(), e);
            }
        } else {
            heap.initWriteMode();
        }
    }
    
    /**
     * clear the content
     * @throws IOException 
     */
    public void clear() {
        hashScore.clear();
        hashDate.clear();
        initTime = System.currentTimeMillis();
        try {
            heap.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int minMem() {
        // there is no specific large array that needs to be maintained
        // this value is just a guess of the possible overhead
        return 100 * 1024; // 100 kb
    }
    
    public synchronized long getUpdateTime(String wordHash) {
        indexContainer entries = getContainer(wordHash, null);
        if (entries == null) return 0;
        return entries.updated();
    }
    
    // cache settings
    public int maxURLinCache() {
        if (hashScore.size() == 0) return 0;
        return hashScore.getMaxScore();
    }

    public long minAgeOfCache() {
        if (hashDate.size() == 0) return 0;
        return System.currentTimeMillis() - longEmit(hashDate.getMaxScore());
    }

    public long maxAgeOfCache() {
        if (hashDate.size() == 0) return 0;
        return System.currentTimeMillis() - longEmit(hashDate.getMinScore());
    }

    public void setMaxWordCount(int maxWords) {
        this.cacheEntityMaxCount = maxWords;
    }
    
    public int getMaxWordCount() {
        return this.cacheEntityMaxCount;
    }
    
    public int size() {
        return heap.size();
    }

    public synchronized int indexSize(String wordHash) {
        indexContainer cacheIndex = heap.get(wordHash);
        if (cacheIndex == null) return 0;
        return cacheIndex.size();
    }

    public synchronized kelondroCloneableIterator<indexContainer> wordContainers(String startWordHash, boolean rot) {
        // we return an iterator object that creates top-level-clones of the indexContainers
        // in the cache, so that manipulations of the iterated objects do not change
        // objects in the cache.
        return heap.wordContainers(startWordHash, rot);
    }


    public synchronized String maxScoreWordHash() {
        if (heap.size() == 0) return null;
        try {
            return hashScore.getMaxObject();
        } catch (Exception e) {
            log.logSevere("flushFromMem: " + e.getMessage(), e);
        }
        return null;
    }
    
    private String bestFlushWordHash() {
        // select appropriate hash
        // we have 2 different methods to find a good hash:
        // - the oldest entry in the cache
        // - the entry with maximum count
        if (heap.size() == 0) return null;
        try {
            String hash = null;
            int count = hashScore.getMaxScore();
            if ((count >= cacheReferenceCountLimit) &&
                ((hash = hashScore.getMaxObject()) != null)) {
                // we MUST flush high-score entries, because a loop deletes entries in cache until this condition fails
                // in this cache we MUST NOT check wCacheMinAge
                return hash;
            }
            long oldestTime = longEmit(hashDate.getMinScore());
            if (((System.currentTimeMillis() - oldestTime) > cacheReferenceAgeLimit) &&
                ((hash = hashDate.getMinObject()) != null)) {
                // flush out-dated entries
                return hash;
            }
            // cases with respect to memory situation
            if (serverMemory.free() < 100000) {
                // urgent low-memory case
                hash = hashScore.getMaxObject(); // flush high-score entries (saves RAM)
            } else {
                // not-efficient-so-far case. cleans up unnecessary cache slots
                hash = hashDate.getMinObject(); // flush oldest entries
            }
            if (hash == null) {
                indexContainer ic = heap.wordContainers(null, false).next();
                if (ic != null) hash = ic.getWordHash();
            }
            return hash;
        } catch (Exception e) {
            log.logSevere("flushFromMem: " + e.getMessage(), e);
        }
        return null;
    }

    public synchronized ArrayList<indexContainer> bestFlushContainers(int count) {
        ArrayList<indexContainer> containerList = new ArrayList<indexContainer>();
        String hash;
        indexContainer container;
        for (int i = 0; i < count; i++) {
            hash = bestFlushWordHash();
            if (hash == null) return containerList;
            container = heap.delete(hash);
            assert (container != null);
            if (container == null) return containerList;
            hashScore.deleteScore(hash);
            hashDate.deleteScore(hash);
            containerList.add(container);
        }
        return containerList;
    }
    
    private int intTime(long longTime) {
        return (int) Math.max(0, ((longTime - initTime) / 1000));
    }

    private long longEmit(int intTime) {
        return (((long) intTime) * (long) 1000) + initTime;
    }
    
    public boolean hasContainer(String wordHash) {
        return heap.has(wordHash);
    }
    
    public int sizeContainer(String wordHash) {
        indexContainer c = heap.get(wordHash);
        return (c == null) ? 0 : c.size();
    }

    public synchronized indexContainer getContainer(String wordHash, Set<String> urlselection) {
        if (wordHash == null) return null;
        
        // retrieve container
        indexContainer container = heap.get(wordHash);
        
        // We must not use the container from cache to store everything we find,
        // as that container remains linked to in the cache and might be changed later
        // while the returned container is still in use.
        // create a clone from the container
        if (container != null) container = container.topLevelClone();
        
        // select the urlselection
        if ((urlselection != null) && (container != null)) container.select(urlselection);

        return container;
    }

    public synchronized indexContainer deleteContainer(String wordHash) {
        // returns the index that had been deleted
        indexContainer container = heap.delete(wordHash);
        hashScore.deleteScore(wordHash);
        hashDate.deleteScore(wordHash);
        return container;
    }

    public synchronized boolean removeEntry(String wordHash, String urlHash) {
        boolean removed = heap.removeReference(wordHash, urlHash);
        if (removed) {
            if (heap.has(wordHash)) {
                hashScore.decScore(wordHash);
                hashDate.setScore(wordHash, intTime(System.currentTimeMillis()));
            } else {
                hashScore.deleteScore(wordHash);
                hashDate.deleteScore(wordHash);
            }
            return true;
        }
        return false;
    }
    
    public synchronized int removeEntries(String wordHash, Set<String> urlHashes) {
        if (urlHashes.size() == 0) return 0;
        int c = heap.removeReferences(wordHash, urlHashes);
        if (c > 0) {
            // removal successful
            if (heap.has(wordHash)) {
                hashScore.decScore(wordHash);
                hashDate.setScore(wordHash, intTime(System.currentTimeMillis()));
            }
            return c;
        }
        return 0;
    }
    
    public synchronized void addEntries(indexContainer container) {
        // this puts the entries into the cache, not into the assortment directly
        if ((container == null) || (container.size() == 0)) return;

        // put new words into cache
        int added = heap.add(container);
        if (added > 0) {
            hashScore.addScore(container.getWordHash(), added);
            hashDate.setScore(container.getWordHash(), intTime(System.currentTimeMillis()));
        }
    }

    public synchronized void addEntry(String wordHash, indexRWIRowEntry newEntry, long updateTime, boolean dhtCase) {
        heap.addEntry(wordHash, newEntry);
        hashScore.incScore(wordHash);
        hashDate.setScore(wordHash, intTime(updateTime));
    }

    public synchronized void close() {
        // dump cache
        try {
            heap.dump(this.indexHeapFile);
        } catch (IOException e){
            log.logSevere("unable to dump cache: " + e.getMessage(), e);
        }
        heap = null;
        hashScore.clear();
        hashDate.clear();
    }
}
