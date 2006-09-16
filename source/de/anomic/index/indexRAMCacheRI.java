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
    public  static final long wCacheMaxAge         = 1000 * 60 * 30; // milliseconds; 30 minutes
    
    // class variables
    private final File databaseRoot;
    protected final TreeMap cache; // wordhash-container
    private final kelondroMScoreCluster hashScore;
    private final kelondroMScoreCluster hashDate;
    private long  initTime;
    private int   cacheMaxCount;
    public  int   cacheReferenceLimit;
    private final serverLog log;
    private String indexArrayFileName;
    
    // calculated constants
    private static String maxKey;
    static {
        maxKey = ""; for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += 'z';
        //minKey = ""; for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += '-';
    }

    public indexRAMCacheRI(File databaseRoot, int wCacheReferenceLimitInit, String dumpname, serverLog log) {

        // creates a new index cache
        // the cache has a back-end where indexes that do not fit in the cache are flushed
        this.databaseRoot = databaseRoot;
        this.cache = new TreeMap();
        this.hashScore = new kelondroMScoreCluster();
        this.hashDate  = new kelondroMScoreCluster();
        this.initTime = System.currentTimeMillis();
        this.cacheMaxCount = 10000;
        this.cacheReferenceLimit = wCacheReferenceLimitInit;
        this.log = log;
        indexArrayFileName = dumpname;
        
        // read in dump of last session
        try {
            restore();
        } catch (IOException e){
            log.logSevere("unable to restore cache dump: " + e.getMessage(), e);
        }
    }

    private void dump(int waitingSeconds) throws IOException {
        log.logConfig("creating dump for index cache '" + indexArrayFileName + "', " + cache.size() + " words (and much more urls)");
        File indexDumpFile = new File(databaseRoot, indexArrayFileName);
        if (indexDumpFile.exists()) indexDumpFile.delete();
        kelondroFixedWidthArray dumpArray = null;
            dumpArray = new kelondroFixedWidthArray(indexDumpFile, plasmaWordIndexAssortment.bufferStructureBasis, 0);
            long startTime = System.currentTimeMillis();
            long messageTime = System.currentTimeMillis() + 5000;
            long wordsPerSecond = 0, wordcount = 0, urlcount = 0;
            Map.Entry entry;
            String wordHash;
            indexContainer container;
            long updateTime;
            indexEntry iEntry;
            kelondroRow.Entry row = dumpArray.row().newEntry();
      
            // write wCache
            synchronized (cache) {
                Iterator i = cache.entrySet().iterator();
                while (i.hasNext()) {
                    // get entries
                    entry = (Map.Entry) i.next();
                    wordHash = (String) entry.getKey();
                    updateTime = getUpdateTime(wordHash);
                    container = (indexContainer) entry.getValue();

                    // put entries on stack
                    if (container != null) {
                        Iterator ci = container.entries();
                        while (ci.hasNext()) {
                            iEntry = (indexEntry) ci.next();
                            row.setCol(0, wordHash.getBytes());
                            row.setCol(1, kelondroNaturalOrder.encodeLong(container.size(), 4));
                            row.setCol(2, kelondroNaturalOrder.encodeLong(updateTime, 8));
                            row.setCol(3, iEntry.urlHash().getBytes());
                            row.setCol(4, iEntry.toEncodedByteArrayForm(false));
                            dumpArray.set((int) urlcount++, row);
                        }
                    }
                    wordcount++;
                    i.remove(); // free some mem

                    // write a log
                    if (System.currentTimeMillis() > messageTime) {
                        // System.gc(); // for better statistic
                        wordsPerSecond = wordcount * 1000 / (1 + System.currentTimeMillis() - startTime);
                        log.logInfo("dumping status: " + wordcount + " words done, " + (cache.size() / (wordsPerSecond + 1)) + " seconds remaining, free mem = " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB");
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
        kelondroFixedWidthArray dumpArray = new kelondroFixedWidthArray(indexDumpFile, plasmaWordIndexAssortment.bufferStructureBasis, 0);
        log.logConfig("restore array dump of index cache '" + indexArrayFileName + "', " + dumpArray.size() + " word/URL relations");
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long urlCount = 0, urlsPerSecond = 0;
        try {
            synchronized (cache) {
                int i = dumpArray.size();
                String wordHash;
                //long creationTime;
                indexEntry wordEntry;
                kelondroRow.Entry row;
                //Runtime rt = Runtime.getRuntime();
                while (i-- > 0) {
                    // get out one entry
                    row = dumpArray.get(i);
                    if ((row == null) || (row.empty(0)) || (row.empty(3)) || (row.empty(4))) continue;
                    wordHash = row.getColString(0, "UTF-8");
                    //creationTime = kelondroRecords.bytes2long(row[2]);
                    wordEntry = new indexURLEntry(row.getColString(3, null), row.getColString(4, null));
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
            log.logConfig("restored " + cache.size() + " words in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        } catch (kelondroException e) {
            // restore failed
            log.logSevere("restore of indexCache array dump failed: " + e.getMessage(), e);
        } finally {
            if (dumpArray != null) try {dumpArray.close();}catch(Exception e){}
        }
        return urlCount;
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
        this.cacheMaxCount = maxWords;
    }
    
    public int getMaxWordCount() {
        return this.cacheMaxCount;
    }
    
    public int size() {
        return cache.size();
    }

    public int indexSize(String wordHash) {
        int size = 0;
        indexContainer cacheIndex = (indexContainer) cache.get(wordHash);
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
            this.iterator = (startWordHash == null) ? cache.values().iterator() : cache.tailMap(startWordHash).values().iterator();
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
                    iterator = cache.values().iterator();
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

    public String bestFlushWordHash() {
        // select appropriate hash
        // we have 2 different methods to find a good hash:
        // - the oldest entry in the cache
        // - the entry with maximum count
        if (cache.size() == 0) return null;
        try {
            synchronized (cache) {
                String hash = null;
                int count = hashScore.getMaxScore();
                if ((count >= cacheReferenceLimit) &&
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
                if (Runtime.getRuntime().freeMemory() < 100000) {
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
        return (int) Math.max(0, ((longTime - initTime) / 1000));
    }

    private long longEmit(int intTime) {
        return (((long) intTime) * (long) 1000) + initTime;
    }
    
    public indexContainer getContainer(String wordHash, Set urlselection, boolean deleteIfEmpty, long maxtime_dummy) {

        // retrieve container
        indexContainer container = (indexContainer) cache.get(wordHash);
        
        // We must not use the container from cache to store everything we find,
        // as that container remains linked to in the cache and might be changed later
        // while the returned container is still in use.
        // create a clone from the container
        if (container != null) container = container.topLevelClone();
        
        // select the urlselection
        if ((urlselection != null) && (container != null)) container.select(urlselection);

        return container;
    }

    public indexContainer deleteContainer(String wordHash) {
        // returns the index that had been deleted
        synchronized (cache) {
            indexContainer container = (indexContainer) cache.remove(wordHash);
            hashScore.deleteScore(wordHash);
            hashDate.deleteScore(wordHash);
            return container;
        }
    }

    public boolean removeEntry(String wordHash, String urlHash, boolean deleteComplete) {
        synchronized (cache) {
            indexContainer c = (indexContainer) deleteContainer(wordHash);
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
        synchronized (cache) {
            indexContainer c = (indexContainer) deleteContainer(wordHash);
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
        synchronized (cache) {
            Iterator i = cache.entrySet().iterator();
            Map.Entry entry;
            String wordhash;
            indexContainer c;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                wordhash = (String) entry.getKey();
            
                // get container
                c = (indexContainer) entry.getValue();
                if (c.remove(urlHash) != null) {
                    if (c.size() == 0) {
                        i.remove();
                    } else {
                        cache.put(wordhash, c); // superfluous?
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
        synchronized (cache) {
            // put container into wCache
            String wordHash = container.getWordHash();
            indexContainer entries = (indexContainer) cache.get(wordHash); // null pointer exception? wordhash != null! must be cache==null
            if (entries == null) entries = new indexContainer(wordHash);
            added = entries.add(container, -1);
            if (added > 0) {
                cache.put(wordHash, entries);
                hashScore.addScore(wordHash, added);
                hashDate.setScore(wordHash, intTime(updateTime));
            }
            entries = null;
        }
        return null;
    }

    public indexContainer addEntry(String wordHash, indexEntry newEntry, long updateTime, boolean dhtCase) {
        synchronized (cache) {
            indexContainer container = (indexContainer) cache.get(wordHash);
            if (container == null) container = new indexContainer(wordHash);
            indexEntry[] entries = new indexEntry[] { newEntry };
            if (container.add(entries, updateTime) > 0) {
                cache.put(wordHash, container);
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
