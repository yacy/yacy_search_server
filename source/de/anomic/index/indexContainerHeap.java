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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroByteOrder;
import de.anomic.kelondro.kelondroBytesLongMap;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.server.logging.serverLog;

public final class indexContainerHeap {

    private final kelondroRow payloadrow;
    private final serverLog log;
    private kelondroBytesLongMap index;
    private SortedMap<String, indexContainer> cache;
    private File backupFile;
    private boolean readOnlyMode;
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
     * opens an existing heap file in undefined mode
     * after this a initialization should be made to use the heap:
     * either a read-only or read/write mode inititalization
     * @param payloadrow
     * @param log
     */
    public indexContainerHeap(final kelondroRow payloadrow, final serverLog log) {
        this.payloadrow = payloadrow;
        this.log = log;
        this.cache = null;
        this.index = null;
        this.backupFile = null;
        this.readOnlyMode = false;
    }
    
    public void clear() throws IOException {
        if (index != null) index.clear();
        if (cache != null) cache.clear();
        initWriteMode();
    }
    
    /**
     * initializes the heap in read/write mode without reading of a dump first
     * another dump reading afterwards is not possible
     */
    public void initWriteMode() {
        this.cache = Collections.synchronizedSortedMap(new TreeMap<String, indexContainer>(new kelondroByteOrder.StringOrder(payloadrow.getOrdering())));
        this.readOnlyMode = false;
    }
    
    /**
     * restore a heap dump: this is a heap in write mode. There should no heap file
     * be assigned in initialization; the file name is given here in this method
     * when the heap is once dumped again, the heap file name may be different
     * @param heapFile
     * @throws IOException
     */
    public void initWriteMode(final File heapFile) throws IOException {
        this.readOnlyMode = false;
        if (log != null) log.logInfo("restoring dump for rwi heap '" + heapFile.getName() + "'");
        final long start = System.currentTimeMillis();
        this.cache = Collections.synchronizedSortedMap(new TreeMap<String, indexContainer>(new kelondroByteOrder.StringOrder(payloadrow.getOrdering())));
        int urlCount = 0;
        synchronized (cache) {
            for (final indexContainer container : new heapFileEntries(heapFile, this.payloadrow)) {
                if (container == null) break;
                cache.put(container.getWordHash(), container);
                urlCount += container.size();
            }
        }
        if (log != null) log.logInfo("finished rwi heap restore: " + cache.size() + " words, " + urlCount + " word/URL relations in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
    }
    
    /**
     * init a heap file in read-only mode
     * this initiates a heap index generation which is then be used to access elements of the heap
     * during the life-time of this object, the file is _not_ open; it is opened each time
     * the heap is accessed for reading
     * @param heapFile
     * @throws IOException 
     */
    public void initReadMode(final File heapFile) throws IOException {
        this.readOnlyMode = true;
        assert this.cache == null;
        assert this.index == null;
        this.backupFile = heapFile;
        if (!(heapFile.exists())) throw new IOException("file " + heapFile + " does not exist");
        if (heapFile.length() >= Integer.MAX_VALUE) throw new IOException("file " + heapFile + " too large, index can only be crated for files less than 2GB");
        if (log != null) log.logInfo("creating index for rwi heap '" + heapFile.getName() + "'");
        
        final long start = System.currentTimeMillis();
        this.index = new kelondroBytesLongMap(payloadrow.primaryKeyLength, (kelondroByteOrder) payloadrow.getOrdering(), 0);
        DataInputStream is = null;
        final long urlCount = 0;
        String wordHash;
        final byte[] word = new byte[payloadrow.primaryKeyLength];
        long seek = 0, seek0;
        synchronized (index) {
            is = new DataInputStream(new BufferedInputStream(new FileInputStream(heapFile), 64*1024));
        
            // don't test available() here because this does not work for files > 2GB
            loop: while (true) {
                // remember seek position
                seek0 = seek;
            
                // read word
                try {
                    is.readFully(word);
                } catch (final IOException e) {
                    break loop; // terminate loop
                }
                wordHash = new String(word);
                seek += wordHash.length();
            
                // read collection
                try {
                    seek += kelondroRowSet.skipNextRowSet(is, payloadrow);
                } catch (final IOException e) {
                    break loop; // terminate loop
                }
                index.addl(word, seek0);
            }
        }
        is.close();
        if (log != null) log.logInfo("finished rwi heap indexing: " + urlCount + " word/URL relations in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
    }
    
    public void dump(final File heapFile) throws IOException {
        assert this.cache != null;
        if (log != null) log.logInfo("creating rwi heap dump '" + heapFile.getName() + "', " + cache.size() + " rwi's");
        if (heapFile.exists()) heapFile.delete();
        final DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(heapFile), 64 * 1024));
        final long startTime = System.currentTimeMillis();
        long wordcount = 0, urlcount = 0;
        String wordHash;
        indexContainer container;

        // write wCache
        synchronized (cache) {
            for (final Map.Entry<String, indexContainer> entry: cache.entrySet()) {
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
                    urlcount += container.size();
                }
                wordcount++;
            }
        }
        os.flush();
        os.close();
        if (log != null) log.logInfo("finished rwi heap dump: " + wordcount + " words, " + urlcount + " word/URL relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        
        // finally delete the internal cache to switch handling to read-only mode
        this.cache = null;
        // if the cache will be used in read-only mode afterwards, it must be initiated with initReadMode(file);
    }
    
    public int size() {
        return (this.cache == null) ? 0 : this.cache.size();
    }
    
    /**
     * static iterator of heap files: is used to import heap dumps into a write-enabled index heap
     */
    public static class heapFileEntries implements Iterator<indexContainer>, Iterable<indexContainer> {
        DataInputStream is;
        byte[] word;
        kelondroRow payloadrow;
        indexContainer nextContainer;
        
        public heapFileEntries(final File heapFile, final kelondroRow payloadrow) throws IOException {
            if (!(heapFile.exists())) throw new IOException("file " + heapFile + " does not exist");
            is = new DataInputStream(new BufferedInputStream(new FileInputStream(heapFile), 64*1024));
            word = new byte[payloadrow.primaryKeyLength];
            this.payloadrow = payloadrow;
            this.nextContainer = next0();
        }
        
        public boolean hasNext() {
            return this.nextContainer != null;
        }

        private indexContainer next0() {
            try {
                is.readFully(word);
                return new indexContainer(new String(word), kelondroRowSet.importRowSet(is, payloadrow));
            } catch (final IOException e) {
                return null;
            }
        }
        
        public indexContainer next() {
            final indexContainer n = this.nextContainer;
            this.nextContainer = next0();
            return n;
        }

        public void remove() {
            throw new UnsupportedOperationException("heap dumps are read-only");
        }

        public Iterator<indexContainer> iterator() {
            return this;
        }
        
        public void close() {
            if (is != null) try { is.close(); } catch (final IOException e) {}
            is = null;
        }
        
        protected void finalize() {
            this.close();
        }
    }

    /**
     * return an iterator object that creates top-level-clones of the indexContainers
     * in the cache, so that manipulations of the iterated objects do not change
     * objects in the cache.
     */
    public synchronized kelondroCloneableIterator<indexContainer> wordContainers(final String startWordHash, final boolean rot) {
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
        
        private final boolean rot;
        private Iterator<indexContainer> iterator;
        
        public heapCacheIterator(final String startWordHash, final boolean rot) {
            this.rot = rot;
            this.iterator = (startWordHash == null) ? cache.values().iterator() : cache.tailMap(startWordHash).values().iterator();
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }
        
        public heapCacheIterator clone(final Object secondWordHash) {
            return new heapCacheIterator((String) secondWordHash, rot);
        }
        
        public boolean hasNext() {
            if (rot) return true;
            return iterator.hasNext();
        }

        public indexContainer next() {
            if (iterator.hasNext()) {
                return (iterator.next()).topLevelClone();
            }
            // rotation iteration
            if (!rot) {
                return null;
            }
            iterator = cache.values().iterator();
            return (iterator.next()).topLevelClone();
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
    public boolean has(final String key) {
        if (this.readOnlyMode) {
            assert index != null;
            assert index.row().primaryKeyLength == key.length();
            
            // check if the index contains the key
            try {
                return index.getl(key.getBytes()) >= 0;
            } catch (final IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return this.cache.containsKey(key);
    }
    
    /**
     * get a indexContainer from a heap
     * @param key
     * @return the indexContainer if one exist, null otherwise
     */
    public indexContainer get(final String key) {
        if (this.readOnlyMode) try {
            assert index != null;
            assert index.row().primaryKeyLength == key.length();
            
            // check if the index contains the key
            final long pos = index.getl(key.getBytes());
            if (pos < 0) return null;
            
            // access the file and read the container
            final RandomAccessFile raf = new RandomAccessFile(backupFile, "r");
            final byte[] word = new byte[index.row().primaryKeyLength];
            
            raf.seek(pos);
            final int bytesRead = raf.read(word);
            assert bytesRead == word.length;
            assert key.equals(new String(word));
            
            // read collection
            final indexContainer container = new indexContainer(key, kelondroRowSet.importRowSet(raf, payloadrow));
            raf.close();
            return container;
        } catch (final IOException e) {
            log.logSevere("error accessing entry in heap file " + this.backupFile + ": " + e.getMessage());
            return null;
        }
        
        return this.cache.get(key);
    }
    
    /**
     * delete a indexContainer from the heap cache. This can only be used for write-enabled heaps
     * @param wordHash
     * @return the indexContainer if the cache contained the container, null othervise
     */
    public synchronized indexContainer delete(final String wordHash) {
        // returns the index that had been deleted
        assert this.cache != null;
        assert !this.readOnlyMode;
        return cache.remove(wordHash);
    }

    
    public synchronized boolean removeReference(final String wordHash, final String urlHash) {
        assert this.cache != null;
        assert !this.readOnlyMode;
        final indexContainer c = cache.get(wordHash);
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
    
    public synchronized int removeReferences(final String wordHash, final Set<String> urlHashes) {
        assert this.cache != null;
        assert !this.readOnlyMode;
        if (urlHashes.size() == 0) return 0;
        final indexContainer c = cache.get(wordHash);
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
 
    public synchronized int add(final indexContainer container) {
        // this puts the entries into the cache
        int added = 0;
        if ((container == null) || (container.size() == 0)) return 0;
        assert this.cache != null;
        assert !this.readOnlyMode;
        
        // put new words into cache
        final String wordHash = container.getWordHash();
        indexContainer entries = cache.get(wordHash); // null pointer exception? wordhash != null! must be cache==null
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

    public synchronized void addEntry(final String wordHash, final indexRWIRowEntry newEntry) {
        assert this.cache != null;
        assert !this.readOnlyMode;
        indexContainer container = cache.get(wordHash);
        if (container == null) container = new indexContainer(wordHash, this.payloadrow, 1);
        container.put(newEntry);
        cache.put(wordHash, container);
    }
    
}
