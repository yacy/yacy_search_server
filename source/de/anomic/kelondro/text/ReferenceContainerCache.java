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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.kelondro.blob.HeapReader;
import de.anomic.kelondro.blob.HeapWriter;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.util.ByteArray;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowSet;
import de.anomic.yacy.logging.Log;

public final class ReferenceContainerCache<ReferenceType extends Reference> extends AbstractIndex<ReferenceType> implements Index<ReferenceType>, IndexReader<ReferenceType>, Iterable<ReferenceContainer<ReferenceType>> {

    private   final Row payloadrow;
    private   final ByteOrder termOrder;
    protected Map<ByteArray, ReferenceContainer<ReferenceType>> cache;
    
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
        this.cache = new ConcurrentHashMap<ByteArray, ReferenceContainer<ReferenceType>>();
    }
    
    public void dump(final File heapFile, int writeBuffer) {
        assert this.cache != null;
        Log.logInfo("indexContainerRAMHeap", "creating rwi heap dump '" + heapFile.getName() + "', " + cache.size() + " rwi's");
        if (heapFile.exists()) FileUtils.deletedelete(heapFile);
        File tmpFile = new File(heapFile.getParentFile(), heapFile.getName() + ".prt");
        HeapWriter dump;
        try {
            dump = new HeapWriter(tmpFile, heapFile, payloadrow.primaryKeyLength, Base64Order.enhancedCoder, writeBuffer);
        } catch (IOException e1) {
            e1.printStackTrace();
            return;
        }
        final long startTime = System.currentTimeMillis();
        
        // sort the map
        SortedMap<byte[], ReferenceContainer<ReferenceType>> cachecopy = sortedClone();
        
        // write wCache
        long wordcount = 0, urlcount = 0;
        byte[] wordHash = null, lwh;
        ReferenceContainer<ReferenceType> container;
        for (final Map.Entry<byte[], ReferenceContainer<ReferenceType>> entry: cachecopy.entrySet()) {
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
        try {
            dump.close(true);
            Log.logInfo("indexContainerRAMHeap", "finished rwi heap dump: " + wordcount + " words, " + urlcount + " word/URL relations in " + (System.currentTimeMillis() - startTime) + " milliseconds");
        } catch (IOException e) {
            Log.logSevere("indexContainerRAMHeap", "failed rwi heap dump: " + e.getMessage(), e);
        } finally {
            dump = null;
        }
    }
    
    public SortedMap<byte[], ReferenceContainer<ReferenceType>> sortedClone() {
        SortedMap<byte[], ReferenceContainer<ReferenceType>> cachecopy;
        synchronized (cache) {
            cachecopy = new TreeMap<byte[], ReferenceContainer<ReferenceType>>(this.termOrder);
            for (final Map.Entry<ByteArray, ReferenceContainer<ReferenceType>> entry: cache.entrySet()) {
                cachecopy.put(entry.getKey().asBytes(), entry.getValue());
            }
        }
        return cachecopy;
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

    public int maxReferences() {
        // iterate to find the max score
        int max = 0;
        for (ReferenceContainer<ReferenceType> container : cache.values()) {
            if (container.size() > max) max = container.size();
        }
        return max;
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
        private byte[] latestTermHash;
        
        public heapCacheIterator(byte[] startWordHash, final boolean rot) {
            this.rot = rot;
            if (startWordHash != null && startWordHash.length == 0) startWordHash = null;
            SortedMap<byte[], ReferenceContainer<ReferenceType>> cachecopy = sortedClone();
            this.iterator = (startWordHash == null) ? cachecopy.values().iterator() : cachecopy.tailMap(startWordHash).values().iterator();
            this.latestTermHash = null;
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
                ReferenceContainer<ReferenceType> c = iterator.next();
                this.latestTermHash = c.getTermHash();
                return c.topLevelClone();
            }
            // rotation iteration
            if (!rot) {
                return null;
            }
            iterator = cache.values().iterator();
            ReferenceContainer<ReferenceType> c = iterator.next();
            this.latestTermHash = c.getTermHash();
            return c.topLevelClone();
        }

        public void remove() {
            iterator.remove();
            cache.remove(new ByteArray(this.latestTermHash));
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
        return this.cache.containsKey(new ByteArray(key));
    }
    
    /**
     * get a indexContainer from a heap
     * @param key
     * @return the indexContainer if one exist, null otherwise
     */
    public ReferenceContainer<ReferenceType> get(final byte[] key, Set<String> urlselection) {
        if (urlselection == null) return this.cache.get(new ByteArray(key));
        ReferenceContainer<ReferenceType> c = this.cache.get(new ByteArray(key));
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
        ReferenceContainer<ReferenceType> c = this.cache.get(new ByteArray(key));
        if (c == null) return 0;
        return c.size();
    }
    
    /**
     * delete a indexContainer from the heap cache. This can only be used for write-enabled heaps
     * @param wordHash
     * @return the indexContainer if the cache contained the container, null othervise
     */
    public ReferenceContainer<ReferenceType> delete(final byte[] termHash) {
        // returns the index that had been deleted
        assert this.cache != null;
        return cache.remove(new ByteArray(termHash));
    }

    public boolean remove(final byte[] termHash, final String urlHash) {
        assert this.cache != null;
        ByteArray tha = new ByteArray(termHash);
        synchronized (cache) {
	        final ReferenceContainer<ReferenceType> c = cache.get(tha);
	        if ((c != null) && (c.remove(urlHash) != null)) {
	            // removal successful
	            if (c.size() == 0) {
	                delete(termHash);
	            } else {
	                cache.put(tha, c);
	            }
	            return true;
	        }
        }
        return false;
    }
    
    public int remove(final byte[] termHash, final Set<String> urlHashes) {
        assert this.cache != null;
        if (urlHashes.size() == 0) return 0;
        ByteArray tha = new ByteArray(termHash);
        int count;
        synchronized (cache) {
	        final ReferenceContainer<ReferenceType> c = cache.get(tha);
	        if ((c != null) && ((count = c.removeEntries(urlHashes)) > 0)) {
	            // removal successful
	            if (c.size() == 0) {
	                delete(termHash);
	            } else {
	                cache.put(tha, c);
	            }
	            return count;
	        }
        }
        return 0;
    }
 
    public void add(final ReferenceContainer<ReferenceType> container) {
        // this puts the entries into the cache
    	assert this.cache != null;
        if (this.cache == null || container == null || container.size() == 0) return;
        
        // put new words into cache
        ByteArray tha = new ByteArray(container.getTermHash());
        int added = 0;
        synchronized (cache) {
            ReferenceContainer<ReferenceType> entries = cache.get(tha); // null pointer exception? wordhash != null! must be cache==null
            if (entries == null) {
                entries = container.topLevelClone();
                added = entries.size();
            } else {
                added = entries.putAllRecent(container);
            }
            if (added > 0) {
                cache.put(tha, entries);
            }
            entries = null;
            return;
        }
    }

    public void add(final byte[] termHash, final ReferenceType newEntry) {
        assert this.cache != null;
        ByteArray tha = new ByteArray(termHash);
        
        // first access the cache without synchronization
        ReferenceContainer<ReferenceType> container = cache.remove(tha);
        if (container == null) container = new ReferenceContainer<ReferenceType>(factory, termHash, this.payloadrow, 1);
        container.put(newEntry);
        
        // synchronization: check if the entry is still empty and set new value
        synchronized (cache) {
            ReferenceContainer<ReferenceType> containerNew = cache.put(tha, container);
            if (containerNew == null) return;
            // Now merge the smaller container into the lager.
            // The other way around can become very slow
            if (container.size() >= containerNew.size()) { 
                container.putAllRecent(containerNew);
       	        cache.put(tha, container);
            } else {
                containerNew.putAllRecent(container);
                cache.put(tha, containerNew);
            }
        }
    }
    
    /*
    public void add(final byte[] termHash, final ReferenceType newEntry) {
        assert this.cache != null;
        ByteArray tha = new ByteArray(termHash);
        
        // first access the cache without synchronization
        ReferenceContainer<ReferenceType> container = cache.remove(tha);
        if (container == null) container = new ReferenceContainer<ReferenceType>(factory, termHash, this.payloadrow, 1);
        container.put(newEntry);
        
        // then try to replace the entry that should be empty,
        // but it can be possible that another thread has written something in between
        ReferenceContainer<ReferenceType> containerNew = cache.put(tha, container);
        if (containerNew == null) return;
        container = containerNew;
        
        // finally use synchronization: ensure that the entry is written exclusively
        synchronized (cache) {
            containerNew = cache.get(tha);
            if (containerNew != null) container.putAllRecent(containerNew);
        	cache.put(tha, container);
        }
    }
    */

    public int minMem() {
        return 0;
    }

    public ByteOrder ordering() {
        return this.termOrder;
    }
 
}
