// plasmaWordIndexCache.java
// -------------------------
// part of YACY
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

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.index.indexContainer;
import de.anomic.index.indexEntry;
import de.anomic.index.indexRI;
import de.anomic.index.indexAbstractRI;
import de.anomic.kelondro.kelondroArray;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public final class plasmaWordIndexCache extends indexAbstractRI implements indexRI {

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

    public plasmaWordIndexCache(File databaseRoot, serverLog log) {

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
        kelondroArray dumpArray = null;
            dumpArray = new kelondroArray(indexDumpFile, plasmaWordIndexAssortment.bufferStructureBasis, 0, false);
            long startTime = System.currentTimeMillis();
            long messageTime = System.currentTimeMillis() + 5000;
            long wordsPerSecond = 0, wordcount = 0, urlcount = 0;
            Map.Entry entry;
            String wordHash;
            plasmaWordIndexEntryContainer container;
            long updateTime;
            plasmaWordIndexEntryInstance wordEntry;
            byte[][] row = new byte[5][];
            
            // write kCache, this will be melted with the wCache upon load
            synchronized (kCache) {
                Iterator i = kCache.values().iterator();
                while (i.hasNext()) {
                    container = (plasmaWordIndexEntryContainer) i.next();

                    // put entries on stack
                    if (container != null) {
                        Iterator ci = container.entries();
                        while (ci.hasNext()) {
                            wordEntry = (plasmaWordIndexEntryInstance) ci.next();
                            row[0] = container.wordHash().getBytes();
                            row[1] = kelondroRecords.long2bytes(container.size(), 4);
                            row[2] = kelondroRecords.long2bytes(container.updated(), 8);
                            row[3] = wordEntry.getUrlHash().getBytes();
                            row[4] = wordEntry.toEncodedStringForm().getBytes();
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
                    container = (plasmaWordIndexEntryContainer) entry.getValue();

                    // put entries on stack
                    if (container != null) {
                        Iterator ci = container.entries();
                        while (ci.hasNext()) {
                            wordEntry = (plasmaWordIndexEntryInstance) ci.next();
                            row[0] = wordHash.getBytes();
                            row[1] = kelondroRecords.long2bytes(container.size(), 4);
                            row[2] = kelondroRecords.long2bytes(updateTime, 8);
                            row[3] = wordEntry.getUrlHash().getBytes();
                            row[4] = wordEntry.toEncodedStringForm().getBytes();
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
        kelondroArray dumpArray = new kelondroArray(indexDumpFile);
        log.logConfig("restore array dump of index cache, " + dumpArray.size() + " word/URL relations");
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long urlCount = 0, urlsPerSecond = 0;
        try {
            synchronized (wCache) {
                int i = dumpArray.size();
                String wordHash;
                //long creationTime;
                plasmaWordIndexEntryInstance wordEntry;
                byte[][] row;
                //Runtime rt = Runtime.getRuntime();
                while (i-- > 0) {
                    // get out one entry
                    row = dumpArray.get(i);
                    if ((row[0] == null) || (row[1] == null) || (row[2] == null) || (row[3] == null) || (row[4] == null)) continue;
                    wordHash = new String(row[0], "UTF-8");
                    //creationTime = kelondroRecords.bytes2long(row[2]);
                    wordEntry = new plasmaWordIndexEntryInstance(new String(row[3], "UTF-8"), new String(row[4], "UTF-8"));
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
        plasmaWordIndexEntryContainer cacheIndex = (plasmaWordIndexEntryContainer) wCache.get(wordHash);
        if (cacheIndex != null) size += cacheIndex.size();
        return size;
    }
    
    public Iterator wordHashes(String startWordHash, boolean rot) {
        if (rot) throw new UnsupportedOperationException("plasmaWordIndexCache cannot rotate");
        return wCache.tailMap(startWordHash).keySet().iterator();
    }

    public void shiftK2W() {
        // find entries in kCache that are too old for that place and shift them to the wCache
        long time;
        Long l;
        plasmaWordIndexEntryContainer container;
        synchronized (kCache) {
            while (kCache.size() > 0) {
                l = (Long) kCache.firstKey();
                time = l.longValue();
                if (System.currentTimeMillis() - time < kCacheMaxAge) return;
                container = (plasmaWordIndexEntryContainer) kCache.remove(l);
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
        return (plasmaWordIndexEntryContainer) wCache.get(wordHash);
    }

    public indexContainer deleteContainer(String wordHash) {
        // returns the index that had been deleted
        synchronized (wCache) {
            plasmaWordIndexEntryContainer container = (plasmaWordIndexEntryContainer) wCache.remove(wordHash);
            hashScore.deleteScore(wordHash);
            hashDate.deleteScore(wordHash);
            return container;
        }
    }

    public int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        if (urlHashes.length == 0) return 0;
        int count = 0;
        synchronized (wCache) {
            plasmaWordIndexEntryContainer c = (plasmaWordIndexEntryContainer) deleteContainer(wordHash);
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
            plasmaWordIndexEntryContainer c;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                l = (Long) entry.getKey();
            
                // get container
                c = (plasmaWordIndexEntryContainer) entry.getValue();
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
            String wordHash = container.wordHash();
            plasmaWordIndexEntryContainer entries = (plasmaWordIndexEntryContainer) wCache.get(wordHash); // null pointer exception? wordhash != null! must be cache==null
            if (entries == null) entries = new plasmaWordIndexEntryContainer(wordHash);
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
            plasmaWordIndexEntryContainer container = new plasmaWordIndexEntryContainer(wordHash);
            container.add(newEntry);
            kCache.put(new Long(updateTime + kCacheInc), container);
            kCacheInc++;
            if (kCacheInc > 10000) kCacheInc = 0;
            return null;
        } else synchronized (wCache) {
            plasmaWordIndexEntryContainer container = (plasmaWordIndexEntryContainer) wCache.get(wordHash);
            if (container == null) container = new plasmaWordIndexEntryContainer(wordHash);
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
