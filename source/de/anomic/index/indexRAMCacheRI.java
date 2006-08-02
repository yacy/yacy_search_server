// indexRAMCacheRI.java
// (C) 2005, 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2005 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroFixedWidthArray;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.plasma.plasmaWordIndexAssortment;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public final class indexRAMCacheRI extends indexAbstractRI implements indexRI {

    // environment constants
    private static final String indexArrayFileName = "indexDump1.array";
    public static final int  wCacheReferenceLimit = 64;
    public static final long wCacheMaxAge         = 1000 * 60 * 30; // milliseconds; 30 minutes
    public static final long kCacheMaxAge         = 1000 * 60 * 2;  // milliseconds; 2 minutes
    
    // class variables
    private final File databaseRoot;
    private final TreeMap wCache; // wordhash-container
    private final TreeMap kCache; // time-container; for karenz/DHT caching (set with high priority)
    private final kelondroMScoreCluster hashScore;
    private final kelondroMScoreCluster hashDate;
    private long  kCacheInc = 0;
    private long  startTime;
    private int   wCacheMaxCount;
    private final serverLog log;

    // calculated constants
    private static String maxKey;
    static {
        maxKey = ""; for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += 'z';
        //minKey = ""; for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += '-';
    }

    public indexRAMCacheRI(File databaseRoot, serverLog log) {

        // creates a new index cache
        // the cache has a back-end where indexes that do not fit in the cache are flushed
        this.databaseRoot = databaseRoot;
        this.wCache = new TreeMap();
        this.kCache = new TreeMap();
        this.hashScore = new kelondroMScoreCluster();
        this.hashDate  = new kelondroMScoreCluster();
        this.kCacheInc = 0;
        this.startTime = System.currentTimeMillis();
        this.wCacheMaxCount = 10000;
        this.log = log;
        
        // read in dump of last session
        try {
            restore();
        } catch (IOException e){
            log.logSevere("unable to restore cache dump: " + e.getMessage(), e);
        }
    }

    private void dump(int waitingSeconds) throws IOException {
        log.logConfig("creating dump for index cache, " + wCache.size() + " words (and much more urls)");
        File indexDumpFile = new File(databaseRoot, indexArrayFileName);
        if (indexDumpFile.exists()) indexDumpFile.delete();
        kelondroFixedWidthArray dumpArray = null;
            dumpArray = new kelondroFixedWidthArray(indexDumpFile, new kelondroRow(plasmaWordIndexAssortment.bufferStructureBasis), 0, false);
            long startTime = System.currentTimeMillis();
            long messageTime = System.currentTimeMillis() + 5000;
            long wordsPerSecond = 0, wordcount = 0, urlcount = 0;
            Map.Entry entry;
            String wordHash;
            indexTreeMapContainer container;
            long updateTime;
            indexURLEntry wordEntry;
            kelondroRow.Entry row = dumpArray.row().newEntry();
            
            // write kCache, this will be melted with the wCache upon load
            synchronized (kCache) {
                Iterator i = kCache.values().iterator();
                while (i.hasNext()) {
                    container = (indexTreeMapContainer) i.next();

                    // put entries on stack
                    if (container != null) {
                        Iterator ci = container.entries();
                        while (ci.hasNext()) {
                            wordEntry = (indexURLEntry) ci.next();
                            row.setCol(0, container.getWordHash().getBytes());
                            row.setCol(1, kelondroNaturalOrder.encodeLong(container.size(), 4));
                            row.setCol(2, kelondroNaturalOrder.encodeLong(container.updated(), 8));
                            row.setCol(3, wordEntry.urlHash().getBytes());
                            row.setCol(4, wordEntry.toEncodedStringForm().getBytes());
                            dumpArray.set((int) urlcount++, row);
                        }
                    }
                    wordcount++;
                    i.remove(); // free some mem
                    
                }
            }
            
            // write wCache
            synchronized (wCache) {
                Iterator i = wCache.entrySet().iterator();
                while (i.hasNext()) {
                    // get entries
                    entry = (Map.Entry) i.next();
                    wordHash = (String) entry.getKey();
                    updateTime = getUpdateTime(wordHash);
                    container = (indexTreeMapContainer) entry.getValue();

                    // put entries on stack
                    if (container != null) {
                        Iterator ci = container.entries();
                        while (ci.hasNext()) {
                            wordEntry = (indexURLEntry) ci.next();
                            row.setCol(0, wordHash.getBytes());
                            row.setCol(1, kelondroNaturalOrder.encodeLong(container.size(), 4));
                            row.setCol(2, kelondroNaturalOrder.encodeLong(updateTime, 8));
                            row.setCol(3, wordEntry.urlHash().getBytes());
                            row.setCol(4, wordEntry.toEncodedStringForm().getBytes());
                            dumpArray.set((int) urlcount++, row);
                        }
                    }
                    wordcount++;
                    i.remove(); // free some mem

                    // write a log
                    if (System.currentTimeMillis() > messageTime) {
                        // System.gc(); // for better statistic
                        wordsPerSecond = wordcount * 1000 / (1 + System.currentTimeMillis() - startTime);
                        log.logInfo("dumping status: " + wordcount + " words done, " + (wCache.size() / (wordsPerSecond + 1)) + " seconds remaining, free mem = " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB");
                        messageTime = System.currentTimeMillis() + 5000;
                    }
                }
            }
            dumpArray.close();
            dumpArray = null;
            log.logConfig("dumped " + urlcount + " word/URL relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
    }

    private long restore() throws IOException {
        File indexDumpFile = new File(databaseRoot, indexArrayFileName);
        if (!(indexDumpFile.exists())) return 0;
        kelondroFixedWidthArray dumpArray = new kelondroFixedWidthArray(indexDumpFile);
        log.logConfig("restore array dump of index cache, " + dumpArray.size() + " word/URL relations");
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long urlCount = 0, urlsPerSecond = 0;
        try {
            synchronized (wCache) {
                int i = dumpArray.size();
                String wordHash;
                //long creationTime;
                indexURLEntry wordEntry;
                kelondroRow.Entry row;
                //Runtime rt = Runtime.getRuntime();
                while (i-- > 0) {
                    // get out one entry
                    row = dumpArray.get(i);
                    if ((row == null) || (row.empty(0)) || (row.empty(3)) || (row.empty(4))) continue;
                    wordHash = row.getColString(0, "UTF-8");
                    //creationTime = kelondroRecords.bytes2long(row[2]);
                    wordEntry = new indexURLEntry(row.getColString(3, "UTF-8"), row.getColString(4, "UTF-8"));
                    // store to cache
                    addEntry(wordHash, wordEntry, startTime, false);
                    urlCount++;
                    // protect against memory shortage
                    //while (rt.freeMemory() < 1000000) {flushFromMem(); java.lang.System.gc();}
                    // write a log
                    if (System.currentTimeMillis() > messageTime) {
                        System.gc(); // for better statistic
                        urlsPerSecond = 1 + urlCount * 1000 / (1 + System.currentTimeMillis() - startTime);
                        log.logInfo("restoring status: " + urlCount + " urls done, " + (i / urlsPerSecond) + " seconds remaining, free mem = " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB");
                        messageTime = System.currentTimeMillis() + 5000;
                    }
                }
            }

            dumpArray.close();
            log.logConfig("restored " + wCache.size() + " words in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        } catch (kelondroException e) {
            // restore failed
            log.logSevere("restore of indexCache array dump failed: " + e.getMessage(), e);
        } finally {
            if (dumpArray != null) try {dumpArray.close();}catch(Exception e){}
        }
        return urlCount;
    }

    // cache settings

    public int maxURLinWCache() {
        if (hashScore.size() == 0) return 0;
        return hashScore.getMaxScore();
    }

    public long minAgeOfWCache() {
        if (hashDate.size() == 0) return 0;
        return System.currentTimeMillis() - longEmit(hashDate.getMaxScore());
    }

    public long maxAgeOfWCache() {
        if (hashDate.size() == 0) return 0;
        return System.currentTimeMillis() - longEmit(hashDate.getMinScore());
    }

    public long minAgeOfKCache() {
        if (kCache.size() == 0) return 0;
        return System.currentTimeMillis() - ((Long) kCache.lastKey()).longValue();
    }

    public long maxAgeOfKCache() {
        if (kCache.size() == 0) return 0;
        return System.currentTimeMillis() - ((Long) kCache.firstKey()).longValue();
    }

    public void setMaxWordCount(int maxWords) {
        this.wCacheMaxCount = maxWords;
    }
    
    public int getMaxWordCount() {
        return this.wCacheMaxCount;
    }
    
    public int wSize() {
        return wCache.size();
    }

    public int kSize() {
        return kCache.size();
    }

    public int size() {
        return wCache.size() + kCache.size();
    }

    public int indexSize(String wordHash) {
        int size = 0;
        indexTreeMapContainer cacheIndex = (indexTreeMapContainer) wCache.get(wordHash);
        if (cacheIndex != null) size += cacheIndex.size();
        return size;
    }

    public Iterator wordContainers(String startWordHash, boolean rot) {
        // we return an iterator object that creates top-level-clones of the indexContainers
        // in the cache, so that manipulations of the iterated objects do not change
        // objects in the cache.
        return new wordContainerIterator(startWordHash, rot);
    }

    public class wordContainerIterator implements Iterator {

        // this class exists, because the wCache cannot be iterated with rotation
        // and because every indeContainer Object that is iterated must be returned as top-level-clone
        // so this class simulates wCache.tailMap(startWordHash).values().iterator()
        // plus the mentioned features
        
        private boolean rot;
        private Iterator iterator;
        
        public wordContainerIterator(String startWordHash, boolean rot) {
            this.rot = rot;
            this.iterator = (startWordHash == null) ? wCache.values().iterator() : wCache.tailMap(startWordHash).values().iterator();
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }
        
        public boolean hasNext() {
            if (rot) return true;
            return iterator.hasNext();
        }

        public Object next() {
            if (iterator.hasNext()) {
                return ((indexContainer) iterator.next()).topLevelClone();
            } else {
                // rotation iteration
                if (rot) {
                    iterator = wCache.values().iterator();
                    return ((indexContainer) iterator.next()).topLevelClone();
                } else {
                    return null;
                }
            }
        }

        public void remove() {
            iterator.remove();
        }
        
    }
    
    public void shiftK2W() {
        // find entries in kCache that are too old for that place and shift them to the wCache
        long time;
        Long l;
        indexTreeMapContainer container;
        synchronized (kCache) {
            while (kCache.size() > 0) {
                l = (Long) kCache.firstKey();
                time = l.longValue();
                if (System.currentTimeMillis() - time < kCacheMaxAge) return;
                container = (indexTreeMapContainer) kCache.remove(l);
                addEntries(container, container.updated(), false);
            }
        }
    }
    
    public String bestFlushWordHash() {
        // select appropriate hash
        // we have 2 different methods to find a good hash:
        // - the oldest entry in the cache
        // - the entry with maximum count
        shiftK2W();
        if (wCache.size() == 0) return null;
        try {
            synchronized (wCache) {
                String hash = null;
                int count = hashScore.getMaxScore();
                if ((count > wCacheReferenceLimit) &&
                    ((hash = (String) hashScore.getMaxObject()) != null)) {
                    // we MUST flush high-score entries, because a loop deletes entries in cache until this condition fails
                    // in this cache we MUST NOT check wCacheMinAge
                    return hash;
                }
                long oldestTime = longEmit(hashDate.getMinScore());
                if (((System.currentTimeMillis() - oldestTime) > wCacheMaxAge) &&
                    ((hash = (String) hashDate.getMinObject()) != null)) {
                    // flush out-dated entries
                    return hash;
                }
                // cases with respect to memory situation
                if (Runtime.getRuntime().freeMemory() < 1000000) {
                    // urgent low-memory case
                    hash = (String) hashScore.getMaxObject(); // flush high-score entries (saves RAM)
                } else {
                    // not-efficient-so-far case. cleans up unnecessary cache slots
                    hash = (String) hashDate.getMinObject(); // flush oldest entries
                }
                return hash;
            }
        } catch (Exception e) {
            log.logSevere("flushFromMem: " + e.getMessage(), e);
        }
        return null;
    }

    private int intTime(long longTime) {
        return (int) Math.max(0, ((longTime - startTime) / 1000));
    }

    private long longEmit(int intTime) {
        return (((long) intTime) * (long) 1000) + startTime;
    }
    
    public indexContainer getContainer(String wordHash, boolean deleteIfEmpty, long maxtime_dummy) {
        return (indexTreeMapContainer) wCache.get(wordHash);
    }

    public indexContainer deleteContainer(String wordHash) {
        // returns the index that had been deleted
        synchronized (wCache) {
            indexTreeMapContainer container = (indexTreeMapContainer) wCache.remove(wordHash);
            hashScore.deleteScore(wordHash);
            hashDate.deleteScore(wordHash);
            return container;
        }
    }

    public boolean removeEntry(String wordHash, String urlHash, boolean deleteComplete) {
        synchronized (wCache) {
            indexTreeMapContainer c = (indexTreeMapContainer) deleteContainer(wordHash);
            if (c != null) {
                if (c.removeEntry(wordHash, urlHash, deleteComplete)) return true;
                this.addEntries(c, System.currentTimeMillis(), false);
            }
        }
        return false;
    }
    
    public int removeEntries(String wordHash, Set urlHashes, boolean deleteComplete) {
        if (urlHashes.size() == 0) return 0;
        int count = 0;
        synchronized (wCache) {
            indexTreeMapContainer c = (indexTreeMapContainer) deleteContainer(wordHash);
            if (c != null) {
                count = c.removeEntries(wordHash, urlHashes, deleteComplete);
                if (c.size() != 0) this.addEntries(c, System.currentTimeMillis(), false);
            }
        }
        return count;
    }
    
    public int tryRemoveURLs(String urlHash) {
        // this tries to delete an index from the cache that has this
        // urlHash assigned. This can only work if the entry is really fresh
        // Such entries must be searched in the latest entries
        int delCount = 0;
        synchronized (kCache) {
            Iterator i = kCache.entrySet().iterator();
            Map.Entry entry;
            Long l;
            indexTreeMapContainer c;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                l = (Long) entry.getKey();
            
                // get container
                c = (indexTreeMapContainer) entry.getValue();
                if (c.remove(urlHash) != null) {
                    if (c.size() == 0) {
                        i.remove();
                    } else {
                        kCache.put(l, c); // superfluous?
                    }
                    delCount++;
                }
            }
        }
        return delCount;
    }
    
    public indexContainer addEntries(indexContainer container, long updateTime, boolean dhtCase) {
        // this puts the entries into the cache, not into the assortment directly
        int added = 0;

        // put new words into cache
        if (dhtCase) synchronized (kCache) {
            // put container into kCache
            kCache.put(new Long(updateTime + kCacheInc), container);
            kCacheInc++;
            if (kCacheInc > 10000) kCacheInc = 0;
            added = container.size();
        } else synchronized (wCache) {
            // put container into wCache
            String wordHash = container.getWordHash();
            indexTreeMapContainer entries = (indexTreeMapContainer) wCache.get(wordHash); // null pointer exception? wordhash != null! must be cache==null
            if (entries == null) entries = new indexTreeMapContainer(wordHash);
            added = entries.add(container, -1);
            if (added > 0) {
                wCache.put(wordHash, entries);
                hashScore.addScore(wordHash, added);
                hashDate.setScore(wordHash, intTime(updateTime));
            }
            entries = null;
        }
        return null;
    }

    public indexContainer addEntry(String wordHash, indexEntry newEntry, long updateTime, boolean dhtCase) {
        if (dhtCase) synchronized (kCache) {
            // put container into kCache
            indexTreeMapContainer container = new indexTreeMapContainer(wordHash);
            container.add(newEntry);
            kCache.put(new Long(updateTime + kCacheInc), container);
            kCacheInc++;
            if (kCacheInc > 10000) kCacheInc = 0;
            return null;
        } else synchronized (wCache) {
            indexTreeMapContainer container = (indexTreeMapContainer) wCache.get(wordHash);
            if (container == null) container = new indexTreeMapContainer(wordHash);
            indexEntry[] entries = new indexEntry[] { newEntry };
            if (container.add(entries, updateTime) > 0) {
                wCache.put(wordHash, container);
                hashScore.incScore(wordHash);
                hashDate.setScore(wordHash, intTime(updateTime));
                return null;
            }
            container = null;
            entries = null;
            return null;
        }
    }

    public void close(int waitingSeconds) {
        // dump cache
        try {
            dump(waitingSeconds);
        } catch (IOException e){
            log.logSevere("unable to dump cache: " + e.getMessage(), e);
        }
    }
}
