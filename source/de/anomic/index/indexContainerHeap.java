// indexContainerHeap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.03.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBufferedRA;
import de.anomic.kelondro.kelondroByteOrder;
import de.anomic.kelondro.kelondroBytesIntMap;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroFixedWidthArray;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.kelondro.kelondroRow.EntryIndex;
import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public final class indexContainerHeap {

    private kelondroRow payloadrow;
    private serverLog log;
    private kelondroBytesIntMap index;
    private SortedMap<String, indexContainer> cache;
    private File heapFile;
    // index xor cache is used. If one is not null, then the other must be null
    
    /*
     * An indexContainerHeap is a caching structure for indexContainer objects
     * A heap can have the following stati:
     * write: the heap can be extended with more indexContainer entries.
     * a heap that is open to be written may be dumped to a heap file.
     * after that, the heap is still accessible, but only in read-status,
     * which is not reversible. Once a heap is dumped, it can never be extended with new
     * indexConatiner entries.
     * A write-heap can also initiated using a restore of a dumped heap.
     * read: a dumped head can be accessed using a heap index. when the heap is
     * accessed the first time, all entries are scanned and an index is computed
     */
    
    /**
     * open a new container heap and prepare it as a heap to be written
     * @param payloadrow
     * @param log
     */
    public indexContainerHeap(kelondroRow payloadrow, serverLog log) {
        this.payloadrow = payloadrow;
        this.log = log;
        this.cache = Collections.synchronizedSortedMap(new TreeMap<String, indexContainer>(new kelondroByteOrder.StringOrder(payloadrow.getOrdering())));
        this.index = null;
        this.heapFile = null;
    }
    
    /**
     * opens an existing heap file in read-only mode
     * @param indexHeapFile
     * @param payloadrow
     * @param log
     */
    public indexContainerHeap(kelondroRow payloadrow, serverLog log, File heapFile) {
        this.payloadrow = payloadrow;
        this.log = log;
        this.cache = null;
        this.index = null;
        this.heapFile = heapFile;
    }
    
    public void dumpHeap(File heapFile) throws IOException {
        assert this.heapFile == null;
        assert this.cache != null;
        this.heapFile = heapFile;
        if (log != null) log.logInfo("creating rwi heap dump '" + heapFile.getName() + "', " + cache.size() + " rwi's");
        if (heapFile.exists()) heapFile.delete();
        OutputStream os = new BufferedOutputStream(new FileOutputStream(heapFile), 64 * 1024);
        long startTime = System.currentTimeMillis();
        long wordcount = 0, urlcount = 0;
        String wordHash;
        indexContainer container;

        // write wCache
        synchronized (cache) {
            for (Map.Entry<String, indexContainer> entry: cache.entrySet()) {
                // get entries
                wordHash = entry.getKey();
                container = entry.getValue();
                
                // put entries on heap
                if (container != null) {
                    os.write(wordHash.getBytes());
                    if (wordHash.length() < payloadrow.primaryKeyLength) {
                        for (int i = 0; i < payloadrow.primaryKeyLength - wordHash.length(); i++) os.write(0);
                    }
                    os.write(container.exportCollection());
                }
                wordcount++;
                urlcount += container.size();
            }
        }
        os.flush();
        os.close();
        if (log != null) log.logInfo("finished rwi heap dump: " + wordcount + " words, " + urlcount + " word/URL relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        
        // finally delete the internal cache to switch handling to read-only mode
        this.cache = null;
    }
    
    public int size() {
        assert this.cache != null;
        return this.cache.size();
    }
    
    /**
     * static iterator of heap files: is used to import heap dumps into a write-enabled index heap
     */
    public static class heapFileEntries implements Iterator<indexContainer>, Iterable<indexContainer> {
        DataInputStream is;
        byte[] word;
        kelondroRow payloadrow;
        
        public heapFileEntries(File heapFile, kelondroRow payloadrow) throws IOException {
            if (!(heapFile.exists())) throw new IOException("file " + heapFile + " does not exist");
            is = new DataInputStream(new BufferedInputStream(new FileInputStream(heapFile), 64*1024));
            word = new byte[payloadrow.primaryKeyLength];
            this.payloadrow = payloadrow;
        }
        
        public boolean hasNext() {
            try {
                return is.available() > 0;
            } catch (IOException e) {
                return false;
            }
        }

        public indexContainer next() {
            try {
                is.read(word);
                return new indexContainer(new String(word), kelondroRowSet.importRowSet(is, payloadrow));
            } catch (IOException e) {
                return null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException("heap dumps are read-only");
        }

        public Iterator<indexContainer> iterator() {
            return this;
        }
        
        public void close() {
            if (is != null) try { is.close(); } catch (IOException e) {}
            is = null;
        }
        
        public void finalize() {
            this.close();
        }
    }
    
    /**
     * restore a heap dump: this is a heap in write mode. There should no heap file
     * be assigned in initialization; the file name is given here in this method
     * when the heap is once dumped again, the heap file name may be different
     * @param heapFile
     * @throws IOException
     */
    public void restoreHeap(File heapFile) throws IOException {
        assert this.heapFile == null; // the heap must be opened on write-mode
        
        if (log != null) log.logInfo("restoring dump for rwi heap '" + heapFile.getName() + "'");
        long start = System.currentTimeMillis();
        this.cache = Collections.synchronizedSortedMap(new TreeMap<String, indexContainer>(new kelondroByteOrder.StringOrder(payloadrow.getOrdering())));
        int urlCount = 0;
        synchronized (cache) {
            for (indexContainer container : new heapFileEntries(heapFile, this.payloadrow)) {
                cache.put(container.getWordHash(), container);
                urlCount += container.size();
            }
        }
        if (log != null) log.logInfo("finished rwi heap restore: " + cache.size() + " words, " + urlCount + " word/URL relations in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
    }
    
    private void indexHeap() throws IOException {
        assert this.cache == null;
        if (this.index != null) return;
        if (!(heapFile.exists())) throw new IOException("file " + heapFile + " does not exist");
        if (heapFile.length() >= (long) Integer.MAX_VALUE) throw new IOException("file " + heapFile + " too large, index can only be crated for files less than 2GB");
        if (log != null) log.logInfo("creating index for rwi heap '" + heapFile.getName() + "'");
        
        long start = System.currentTimeMillis();
        this.index = new kelondroBytesIntMap(payloadrow.primaryKeyLength, (kelondroByteOrder) payloadrow.getOrdering(), 0);
        DataInputStream is = null;
        long urlCount = 0;
        String wordHash;
        byte[] word = new byte[payloadrow.primaryKeyLength];
        int seek = 0, seek0;
        synchronized (index) {
            is = new DataInputStream(new BufferedInputStream(new FileInputStream(heapFile), 64*1024));
        
            while (is.available() > 0) {
                // remember seek position
                seek0 = seek;
            
                // read word
                is.read(word);
                wordHash = new String(word);
                seek += wordHash.length();
            
                // read collection
                seek += kelondroRowSet.skipNextRowSet(is, payloadrow);
                index.addi(word, seek0);
            }
        }
        is.close();
        if (log != null) log.logInfo("finished rwi heap indexing: " + urlCount + " word/URL relations in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
    }

    /**
     * return an iterator object that creates top-level-clones of the indexContainers
     * in the cache, so that manipulations of the iterated objects do not change
     * objects in the cache.
     */
    public synchronized kelondroCloneableIterator<indexContainer> wordContainers(String startWordHash, boolean rot) {
        return new heapCacheIterator(startWordHash, rot);
    }

    /**
     * cache iterator: iterates objects within the heap cache. This can only be used
     * for write-enabled heaps, read-only heaps do not have a heap cache
     */
    public class heapCacheIterator implements kelondroCloneableIterator<indexContainer>, Iterable<indexContainer> {

        // this class exists, because the wCache cannot be iterated with rotation
        // and because every indexContainer Object that is iterated must be returned as top-level-clone
        // so this class simulates wCache.tailMap(startWordHash).values().iterator()
        // plus the mentioned features
        
        private boolean rot;
        private Iterator<indexContainer> iterator;
        
        public heapCacheIterator(String startWordHash, boolean rot) {
            this.rot = rot;
            this.iterator = (startWordHash == null) ? cache.values().iterator() : cache.tailMap(startWordHash).values().iterator();
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }
        
        public heapCacheIterator clone(Object secondWordHash) {
            return new heapCacheIterator((String) secondWordHash, rot);
        }
        
        public boolean hasNext() {
            if (rot) return true;
            return iterator.hasNext();
        }

        public indexContainer next() {
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

        public Iterator<indexContainer> iterator() {
            return this;
        }
        
    }

    /**
     * test if a given key is in the heap
     * this works with heaps in write- and read-mode
     * @param key
     * @return true, if the key is used in the heap; false othervise
     */
    public boolean has(String key) {
        if (this.cache == null) try {
            if (!(heapFile.exists())) throw new IOException("file " + heapFile + " does not exist");
            if (index == null) indexHeap();
            assert index != null;
            assert index.row().primaryKeyLength == key.length();
            
            // check if the index contains the key
            return index.geti(key.getBytes()) >= 0;
        } catch (IOException e) {
            log.logSevere("error accessing entry in heap file " + this.heapFile + ": " + e.getMessage());
            return false;
        } else {
            return this.cache.containsKey(key);
        }
    }
    
    /**
     * get a indexContainer from a heap
     * @param key
     * @return the indexContainer if one exist, null otherwise
     */
    public indexContainer get(String key) {
        if (this.cache == null) try {
            if (!(heapFile.exists())) throw new IOException("file " + heapFile + " does not exist");
            if (index == null) indexHeap();
            assert index != null;
            assert index.row().primaryKeyLength == key.length();
            
            // check if the index contains the key
            int pos = index.geti(key.getBytes());
            if (pos < 0) return null;
            
            // access the file and read the container
            RandomAccessFile raf = new RandomAccessFile(heapFile, "r");
            byte[] word = new byte[index.row().primaryKeyLength];
            
            raf.seek(pos);
            raf.read(word);
            assert key.equals(new String(word));
            
            // read collection
            indexContainer container = new indexContainer(key, kelondroRowSet.importRowSet(raf, payloadrow));
            raf.close();
            return container;
        } catch (IOException e) {
            log.logSevere("error accessing entry in heap file " + this.heapFile + ": " + e.getMessage());
            return null;
        } else {
            return this.cache.get(key);
        }
    }
    
    /**
     * delete a indexContainer from the heap cache. This can only be used for write-enabled heaps
     * @param wordHash
     * @return the indexContainer if the cache contained the container, null othervise
     */
    public synchronized indexContainer delete(String wordHash) {
        // returns the index that had been deleted
        assert this.cache != null;
        return cache.remove(wordHash);
    }

    
    public synchronized boolean removeReference(String wordHash, String urlHash) {
        indexContainer c = (indexContainer) cache.get(wordHash);
        if ((c != null) && (c.remove(urlHash) != null)) {
            // removal successful
            if (c.size() == 0) {
                delete(wordHash);
            } else {
                cache.put(wordHash, c);
            }
            return true;
        }
        return false;
    }

    public synchronized int removeReference(String urlHash) {
        // this tries to delete an index from the cache that has this
        // urlHash assigned. This can only work if the entry is really fresh
        // Such entries must be searched in the latest entries
        int delCount = 0;
        Iterator<Map.Entry<String, indexContainer>> i = cache.entrySet().iterator();
        Map.Entry<String, indexContainer> entry;
        String wordhash;
        indexContainer c;
        while (i.hasNext()) {
            entry = i.next();
            wordhash = entry.getKey();
        
            // get container
            c = entry.getValue();
            if (c.remove(urlHash) != null) {
                if (c.size() == 0) {
                    i.remove();
                } else {
                    cache.put(wordhash, c); // superfluous?
                }
                delCount++;
            }
        }
        return delCount;
    }
    
    public synchronized int removeReferences(String wordHash, Set<String> urlHashes) {
        if (urlHashes.size() == 0) return 0;
        indexContainer c = (indexContainer) cache.get(wordHash);
        int count;
        if ((c != null) && ((count = c.removeEntries(urlHashes)) > 0)) {
            // removal successful
            if (c.size() == 0) {
                delete(wordHash);
            } else {
                cache.put(wordHash, c);
            }
            return count;
        }
        return 0;
    }
 
    public synchronized int add(indexContainer container) {
        // this puts the entries into the cache
        int added = 0;
        if ((container == null) || (container.size() == 0)) return 0;

        // put new words into cache
        String wordHash = container.getWordHash();
        indexContainer entries = (indexContainer) cache.get(wordHash); // null pointer exception? wordhash != null! must be cache==null
        if (entries == null) {
            entries = container.topLevelClone();
            added = entries.size();
        } else {
            added = entries.putAllRecent(container);
        }
        if (added > 0) {
            cache.put(wordHash, entries);
        }
        entries = null;
        return added;
    }

    public synchronized void addEntry(String wordHash, indexRWIRowEntry newEntry) {
        indexContainer container = (indexContainer) cache.get(wordHash);
        if (container == null) container = new indexContainer(wordHash, this.payloadrow, 1);
        container.put(newEntry);
        cache.put(wordHash, container);
    }
    
    /**
     * this is a compatibility method for a old heap dump format. don't use it if not necessary
     * @param indexArrayFile
     * @throws IOException
     */
    public void restoreArray(File indexArrayFile) throws IOException {
        // is only here to read old array data structures
        if (!(indexArrayFile.exists())) return;
        kelondroFixedWidthArray dumpArray;
        kelondroBufferedRA readBuffer = null;
        kelondroRow bufferStructureBasis = new kelondroRow(
                    "byte[] wordhash-" + yacySeedDB.commonHashLength + ", " +
                    "Cardinal occ-4 {b256}, " +
                    "Cardinal time-8 {b256}, " +
                    "byte[] urlprops-" + payloadrow.objectsize,
                    kelondroBase64Order.enhancedCoder, 0);
        dumpArray = new kelondroFixedWidthArray(indexArrayFile, bufferStructureBasis, 0);
        log.logInfo("started restore of ram cache '" + indexArrayFile.getName() + "', " + dumpArray.size() + " word/URL relations");
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long urlCount = 0, urlsPerSecond = 0;
        this.cache = Collections.synchronizedSortedMap(new TreeMap<String, indexContainer>(new kelondroByteOrder.StringOrder(payloadrow.getOrdering())));
        try {
            Iterator<EntryIndex> i = dumpArray.contentRows(-1);
            String wordHash;
            //long creationTime;
            indexRWIRowEntry wordEntry;
            kelondroRow.EntryIndex row;
            while (i.hasNext()) {
                // get out one entry
                row = i.next();
                if ((row == null) || (row.empty(0)) || (row.empty(3))) continue;
                wordHash = row.getColString(0, "UTF-8");
                //creationTime = kelondroRecords.bytes2long(row[2]);
                wordEntry = new indexRWIRowEntry(row.getColBytes(3));
                
                // store to cache
                indexContainer container = cache.get(wordHash);
                if (container == null) container = new indexContainer(wordHash, payloadrow, 1);
                container.put(wordEntry);
                cache.put(wordHash, container);
                
                urlCount++;
                // protect against memory shortage
                //while (serverMemory.free() < 1000000) {flushFromMem(); java.lang.System.gc();}
                // write a log
                if (System.currentTimeMillis() > messageTime) {
                    serverMemory.gc(1000, "indexRAMRI, for better statistic-2"); // for better statistic - thq
                    urlsPerSecond = 1 + urlCount * 1000 / (1 + System.currentTimeMillis() - startTime);
                    log.logInfo("restoring status: " + urlCount + " urls done, " + ((dumpArray.size() - urlCount) / urlsPerSecond) + " seconds remaining, free mem = " + (serverMemory.free() / 1024 / 1024) + "MB");
                    messageTime = System.currentTimeMillis() + 5000;
                }
            }
            if (readBuffer != null) readBuffer.close();
            dumpArray.close();
            dumpArray = null;
            log.logInfo("finished restore: " + cache.size() + " words in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        } catch (kelondroException e) {
            // restore failed
            log.logSevere("failed restore of indexCache array dump: " + e.getMessage(), e);
        } finally {
            if (dumpArray != null) try {dumpArray.close();}catch(Exception e){}
        }
    }
    
}
