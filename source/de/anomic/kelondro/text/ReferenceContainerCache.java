// indexContainerCache.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.03.2008 on http://yacy.net
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

package de.anomic.kelondro.text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.anomic.kelondro.blob.HeapReader;
import de.anomic.kelondro.blob.HeapWriter;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.Log;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowSet;

public final class ReferenceContainerCache<ReferenceType extends Reference> extends AbstractIndex<ReferenceType> implements Index<ReferenceType>, IndexReader<ReferenceType>, Iterable<ReferenceContainer<ReferenceType>> {

    private final Row payloadrow;
    private final ByteOrder termOrder;
    private SortedMap<byte[], ReferenceContainer<ReferenceType>> cache;
    
    /**
     * opens an existing heap file in undefined mode
     * after this a initialization should be made to use the heap:
     * either a read-only or read/write mode initialization
     * @param payloadrow
     * @param log
     */
    public ReferenceContainerCache(final ReferenceFactory<ReferenceType> factory, final Row payloadrow, ByteOrder termOrder) {
        super(factory);
        this.payloadrow = payloadrow;
        this.termOrder = termOrder;
        this.cache = null;
    }
    
    public Row rowdef() {
        return this.payloadrow;
    }
    
    public void clear() {
        if (cache != null) cache.clear();
        initWriteMode();
    }
    
    public void close() {
    	this.cache = null;
    }
    
    /**
     * initializes the heap in read/write mode without reading of a dump first
     * another dump reading afterwards is not possible
     */
    public void initWriteMode() {
        this.cache = Collections.synchronizedSortedMap(new TreeMap<byte[], ReferenceContainer<ReferenceType>>(this.termOrder));
    }
    
    /**
     * this is the new cache file format initialization
     * @param heapFile
     * @throws IOException
     */
    public void initWriteModeFromBLOB(final File blobFile) throws IOException {
        Log.logInfo("indexContainerRAMHeap", "restoring rwi blob dump '" + blobFile.getName() + "'");
        final long start = System.currentTimeMillis();
        this.cache = Collections.synchronizedSortedMap(new TreeMap<byte[], ReferenceContainer<ReferenceType>>(this.termOrder));
        int urlCount = 0;
        synchronized (cache) {
            for (final ReferenceContainer<ReferenceType> container : new blobFileEntries<ReferenceType>(blobFile, factory, this.payloadrow)) {
                // TODO: in this loop a lot of memory may be allocated. A check if the memory gets low is necessary. But what do when the memory is low?
                if (container == null) break;
                //System.out.println("***DEBUG indexContainerHeap.initwriteModeFromBLOB*** container.size = " + container.size() + ", container.sorted = " + container.sorted());
                cache.put(container.getTermHash(), container);
                urlCount += container.size();
            }
        }
        // remove idx and gap files if they exist here
        HeapWriter.deleteAllFingerprints(blobFile);
        Log.logInfo("indexContainerRAMHeap", "finished rwi blob restore: " + cache.size() + " words, " + urlCount + " word/URL relations in " + (System.currentTimeMillis() - start) + " milliseconds");
    }
    
    public void dump(final File heapFile, boolean writeIDX) {
        assert this.cache != null;
        Log.logInfo("indexContainerRAMHeap", "creating rwi heap dump '" + heapFile.getName() + "', " + cache.size() + " rwi's");
        if (heapFile.exists()) FileUtils.deletedelete(heapFile);
        File tmpFile = new File(heapFile.getParentFile(), heapFile.getName() + ".tmp");
        HeapWriter dump;
        try {
            dump = new HeapWriter(tmpFile, heapFile, payloadrow.primaryKeyLength, Base64Order.enhancedCoder);
        } catch (IOException e1) {
            e1.printStackTrace();
            return;
        }
        final long startTime = System.currentTimeMillis();
        long wordcount = 0, urlcount = 0;
        byte[] wordHash = null, lwh;
        ReferenceContainer<ReferenceType> container;
        
        // write wCache
        synchronized (cache) {
            for (final Map.Entry<byte[], ReferenceContainer<ReferenceType>> entry: cache.entrySet()) {
                // get entries
                lwh = wordHash;
                wordHash = entry.getKey();
                container = entry.getValue();
                
                // check consistency: entries must be ordered
                assert (lwh == null || this.ordering().compare(wordHash, lwh) > 0);
                
                // put entries on heap
                if (container != null && wordHash.length == payloadrow.primaryKeyLength) {
                    //System.out.println("Dump: " + wordHash);
                    try {
                        dump.add(wordHash, container.exportCollection());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    urlcount += container.size();
                }
                wordcount++;
            }
        }
        try {
            dump.close(writeIDX);
            Log.logInfo("indexContainerRAMHeap", "finished rwi heap dump: " + wordcount + " words, " + urlcount + " word/URL relations in " + (System.currentTimeMillis() - startTime) + " milliseconds");
        } catch (IOException e) {
            e.printStackTrace();
            Log.logInfo("indexContainerRAMHeap", "failed rwi heap dump: " + e.getMessage());
        } finally {
            dump = null;
        }
    }
    
    public int size() {
        return (this.cache == null) ? 0 : this.cache.size();
    }
    
    /**
     * static iterator of BLOBHeap files: is used to import heap dumps into a write-enabled index heap
     */
    public static class blobFileEntries <ReferenceType extends Reference> implements CloneableIterator<ReferenceContainer<ReferenceType>>, Iterable<ReferenceContainer<ReferenceType>> {
        HeapReader.entries blobs;
        Row payloadrow;
        File blobFile;
        ReferenceFactory<ReferenceType> factory;
        
        public blobFileEntries(final File blobFile, ReferenceFactory<ReferenceType> factory, final Row payloadrow) throws IOException {
            this.blobs = new HeapReader.entries(blobFile, payloadrow.primaryKeyLength);
            this.payloadrow = payloadrow;
            this.blobFile = blobFile;
            this.factory = factory;
        }
        
        public boolean hasNext() {
            if (blobs == null) return false;
            if (blobs.hasNext()) return true;
            close();
            return false;
        }

        /**
         * return an index container
         * because they may get very large, it is wise to deallocate some memory before calling next()
         */
        public ReferenceContainer<ReferenceType> next() {
            Map.Entry<String, byte[]> entry = blobs.next();
            byte[] payload = entry.getValue();
            return new ReferenceContainer<ReferenceType>(factory, entry.getKey().getBytes(), RowSet.importRowSet(payload, payloadrow));
        }
        
        public void remove() {
            throw new UnsupportedOperationException("heap dumps are read-only");
        }

        public Iterator<ReferenceContainer<ReferenceType>> iterator() {
            return this;
        }
        
        public void close() {
            if (blobs != null) this.blobs.close();
            blobs = null;
        }
        
        protected void finalize() {
            this.close();
        }

        public CloneableIterator<ReferenceContainer<ReferenceType>> clone(Object modifier) {
            if (blobs != null) this.blobs.close();
            blobs = null;
            try {
                return new blobFileEntries<ReferenceType>(this.blobFile, factory, this.payloadrow);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public synchronized int maxReferences() {
        // iterate to find the max score
        int max = 0;
        for (ReferenceContainer<ReferenceType> container : cache.values()) {
            if (container.size() > max) max = container.size();
        }
        return max;
    }
    
    public synchronized byte[] maxReferencesHash() {
        // iterate to find the max score
        int max = 0;
        byte[] hash = null;
        for (ReferenceContainer<ReferenceType> container : cache.values()) {
            if (container.size() > max) {
                max = container.size();
                hash = container.getTermHash();
            }
        }
        return hash;
    }
    
    public synchronized ArrayList<byte[]> maxReferencesHash(int bound) {
        // iterate to find the max score
        ArrayList<byte[]> hashes = new ArrayList<byte[]>();
        for (ReferenceContainer<ReferenceType> container : cache.values()) {
            if (container.size() >= bound) {
                hashes.add(container.getTermHash());
            }
        }
        return hashes;
    }
    
    public synchronized ReferenceContainer<ReferenceType> latest() {
        ReferenceContainer<ReferenceType> c = null;
        for (ReferenceContainer<ReferenceType> container : cache.values()) {
            if (c == null) {c = container; continue;}
            if (container.lastWrote() > c.lastWrote()) {c = container; continue;}
        }
        return c;
    }
    
    public synchronized ReferenceContainer<ReferenceType> first() {
        ReferenceContainer<ReferenceType> c = null;
        for (ReferenceContainer<ReferenceType> container : cache.values()) {
            if (c == null) {c = container; continue;}
            if (container.lastWrote() < c.lastWrote()) {c = container; continue;}
        }
        return c;
    }
    
    public synchronized ArrayList<byte[]> overAge(long maxage) {
        ArrayList<byte[]> hashes = new ArrayList<byte[]>();
        long limit = System.currentTimeMillis() - maxage;
        for (ReferenceContainer<ReferenceType> container : cache.values()) {
            if (container.lastWrote() < limit) hashes.add(container.getTermHash());
        }
        return hashes;
    }
    
    /**
     * return an iterator object that creates top-level-clones of the indexContainers
     * in the cache, so that manipulations of the iterated objects do not change
     * objects in the cache.
     */
    public synchronized CloneableIterator<ReferenceContainer<ReferenceType>> references(final byte[] startWordHash, final boolean rot) {
        return new heapCacheIterator(startWordHash, rot);
    }


    public Iterator<ReferenceContainer<ReferenceType>> iterator() {
        return references(null, false);
    }
    
    
    /**
     * cache iterator: iterates objects within the heap cache. This can only be used
     * for write-enabled heaps, read-only heaps do not have a heap cache
     */
    public class heapCacheIterator implements CloneableIterator<ReferenceContainer<ReferenceType>>, Iterable<ReferenceContainer<ReferenceType>> {

        // this class exists, because the wCache cannot be iterated with rotation
        // and because every indexContainer Object that is iterated must be returned as top-level-clone
        // so this class simulates wCache.tailMap(startWordHash).values().iterator()
        // plus the mentioned features
        
        private final boolean rot;
        private Iterator<ReferenceContainer<ReferenceType>> iterator;
        
        public heapCacheIterator(byte[] startWordHash, final boolean rot) {
            this.rot = rot;
            if (startWordHash != null && startWordHash.length == 0) startWordHash = null;
            this.iterator = (startWordHash == null) ? cache.values().iterator() : cache.tailMap(startWordHash).values().iterator();
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }
        
        public heapCacheIterator clone(final Object secondWordHash) {
            return new heapCacheIterator((byte[]) secondWordHash, rot);
        }
        
        public boolean hasNext() {
            if (rot) return true;
            return iterator.hasNext();
        }

        public ReferenceContainer<ReferenceType> next() {
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

        public Iterator<ReferenceContainer<ReferenceType>> iterator() {
            return this;
        }
        
    }

    /**
     * test if a given key is in the heap
     * this works with heaps in write- and read-mode
     * @param key
     * @return true, if the key is used in the heap; false othervise
     */
    public boolean has(final byte[] key) {
        return this.cache.containsKey(key);
    }
    
    /**
     * get a indexContainer from a heap
     * @param key
     * @return the indexContainer if one exist, null otherwise
     */
    public ReferenceContainer<ReferenceType> get(final byte[] key, Set<String> urlselection) {
        if (urlselection == null) return this.cache.get(key);
        ReferenceContainer<ReferenceType> c = this.cache.get(key);
        if (c == null) return null;
        // because this is all in RAM, we must clone the entries (flat)
        ReferenceContainer<ReferenceType> c1 = new ReferenceContainer<ReferenceType>(factory, c.getTermHash(), c.row(), c.size());
        Iterator<ReferenceType> e = c.entries();
        ReferenceType ee;
        while (e.hasNext()) {
            ee = e.next();
            if (urlselection.contains(ee.metadataHash())) c1.add(ee);
        }
        return c1;
    }

    /**
     * return the size of the container with corresponding key
     * @param key
     * @return
     */
    public int count(final byte[] key) {
        ReferenceContainer<ReferenceType> c = this.cache.get(key);
        if (c == null) return 0;
        return c.size();
    }
    
    /**
     * delete a indexContainer from the heap cache. This can only be used for write-enabled heaps
     * @param wordHash
     * @return the indexContainer if the cache contained the container, null othervise
     */
    public synchronized ReferenceContainer<ReferenceType> delete(final byte[] termHash) {
        // returns the index that had been deleted
        assert this.cache != null;
        return cache.remove(termHash);
    }

    public synchronized boolean remove(final byte[] termHash, final String urlHash) {
        assert this.cache != null;
        final ReferenceContainer<ReferenceType> c = cache.get(termHash);
        if ((c != null) && (c.remove(urlHash) != null)) {
            // removal successful
            if (c.size() == 0) {
                delete(termHash);
            } else {
                cache.put(termHash, c);
            }
            return true;
        }
        return false;
    }
    
    public synchronized int remove(final byte[] termHash, final Set<String> urlHashes) {
        assert this.cache != null;
        if (urlHashes.size() == 0) return 0;
        final ReferenceContainer<ReferenceType> c = cache.get(termHash);
        int count;
        if ((c != null) && ((count = c.removeEntries(urlHashes)) > 0)) {
            // removal successful
            if (c.size() == 0) {
                delete(termHash);
            } else {
                cache.put(termHash, c);
            }
            return count;
        }
        return 0;
    }
 
    public synchronized void add(final ReferenceContainer<ReferenceType> container) {
        // this puts the entries into the cache
        if (this.cache == null || container == null || container.size() == 0) return;
        
        // put new words into cache
        final byte[] termHash = container.getTermHash();
        ReferenceContainer<ReferenceType> entries = cache.get(termHash); // null pointer exception? wordhash != null! must be cache==null
        int added = 0;
        if (entries == null) {
            entries = container.topLevelClone();
            added = entries.size();
        } else {
            added = entries.putAllRecent(container);
        }
        if (added > 0) {
            cache.put(termHash, entries);
        }
        entries = null;
        return;
    }

    public synchronized void add(final byte[] termHash, final ReferenceType newEntry) {
        assert this.cache != null;
        ReferenceContainer<ReferenceType> container = cache.get(termHash);
        if (container == null) container = new ReferenceContainer<ReferenceType>(factory, termHash, this.payloadrow, 1);
        container.put(newEntry);
        cache.put(termHash, container);
    }

    public int minMem() {
        return 0;
    }

    public ByteOrder ordering() {
        return this.termOrder;
    }

}
