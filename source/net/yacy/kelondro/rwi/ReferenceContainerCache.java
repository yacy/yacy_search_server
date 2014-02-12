// ReferenceContainerCache.java
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

package net.yacy.kelondro.rwi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.sorting.Rating;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.HeapWriter;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.util.FileUtils;

/**
 * A ReferenceContainerCache is the ram cache for word indexes or other entity type indexes
 * The <ReferenceType> defines the index reference specification and attributes that can be
 * accessed during a search without using the metadata reference that shall be contained within
 * the <ReferenceType>. A ReferenceContainerCache has no active backup in a file, it must be flushed to
 * a file to save the content of the cache.
 *
 * @param <ReferenceType>
 */
public final class ReferenceContainerCache<ReferenceType extends Reference> extends AbstractIndex<ReferenceType> implements Index<ReferenceType>, IndexReader<ReferenceType>, Iterable<ReferenceContainer<ReferenceType>> {

    private static final ConcurrentLog log = new ConcurrentLog("ReferenceContainerCache");

    private final int termSize;
    private final ByteOrder termOrder;
    private final ContainerOrder<ReferenceType> containerOrder;
    private ConcurrentHashMap<ByteArray, ReferenceContainer<ReferenceType>> cache;

    /**
     * open an existing heap file in undefined mode
     * after this a initialization should be made to use the heap:
     * either a read-only or read/write mode initialization
     * @param factory the factory for payload reference objects
     * @param termOrder the order on search terms for the cache
     * @param termSize the fixed size of search terms
     */
    public ReferenceContainerCache(final ReferenceFactory<ReferenceType> factory, final ByteOrder termOrder, final int termSize) {
        super(factory);
        assert termOrder != null;
        this.termOrder = termOrder;
        this.termSize = termSize;
        this.containerOrder = new ContainerOrder<ReferenceType>(this.termOrder);
        this.cache = new ConcurrentHashMap<ByteArray, ReferenceContainer<ReferenceType>>();
    }

    public Row rowdef() {
        return this.factory.getRow();
    }


    /**
     * every index entry is made for a term which has a fixed size
     * @return the size of the term
     */
    @Override
    public int termKeyLength() {
        return this.termSize;
    }

    @Override
    public void clear() {
        if (this.cache != null) this.cache.clear();
    }

    @Override
    public synchronized void close() {
    	this.cache = null;
    }
    
    public Iterator<ByteArray> keys() {
        return this.cache.keySet().iterator();
    }

    /**
     * dump the cache to a file. This method can be used in a destructive way
     * which means that memory can be freed during the dump. This may be important
     * because the dump is done in such situations when memory gets low. To get more
     * memory during the dump helps to solve tight memory situations.
     * @param heapFile
     * @param writeBuffer
     * @param destructive - if true then the cache is cleaned during the dump causing to free memory
     */
    public void dump(final File heapFile, final int writeBuffer, final boolean destructive) {
        assert this.cache != null;
        if (this.cache == null) return;
        log.info("creating rwi heap dump '" + heapFile.getName() + "', " + this.cache.size() + " rwi's");
        if (heapFile.exists()) FileUtils.deletedelete(heapFile);
        final File tmpFile = new File(heapFile.getParentFile(), heapFile.getName() + ".prt");
        HeapWriter dump;
        try {
            dump = new HeapWriter(tmpFile, heapFile, this.termSize, this.termOrder, writeBuffer);
        } catch (final IOException e1) {
            ConcurrentLog.logException(e1);
            return;
        }
        final long startTime = System.currentTimeMillis();

        // sort the map
        final List<ReferenceContainer<ReferenceType>> cachecopy = sortedClone();

        // write wCache
        long wordcount = 0, urlcount = 0;
        byte[] term = null, lwh;
        assert this.termKeyOrdering() != null;
        for (final ReferenceContainer<ReferenceType> container: cachecopy) {
            // get entries
            lwh = term;
            term = container.getTermHash();
            if (term == null) continue;

            // check consistency: entries must be ordered
            assert (lwh == null || this.termKeyOrdering().compare(term, lwh) > 0);

            // put entries on heap
            if (container != null && term.length == this.termSize) {
                //System.out.println("Dump: " + wordHash);
                try {
                    dump.add(term, container.exportCollection());
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
                if (destructive) container.clear(); // this memory is not needed any more
                urlcount += container.size();
            }
            wordcount++;
        }
        try {
            dump.close(true);
            log.info("finished rwi heap dump: " + wordcount + " words, " + urlcount + " word/URL relations in " + (System.currentTimeMillis() - startTime) + " milliseconds");
        } catch (final IOException e) {
            log.severe("failed rwi heap dump: " + e.getMessage(), e);
        } finally {
            dump = null;
        }
    }

    /**
     * create a clone of the cache content that is sorted using the this.containerOrder
     * @return the sorted ReferenceContainer[]
     */
    private List<ReferenceContainer<ReferenceType>> sortedClone() {
        final List<ReferenceContainer<ReferenceType>> cachecopy = new ArrayList<ReferenceContainer<ReferenceType>>(this.cache.size());
        synchronized (this.cache) {
            for (final Map.Entry<ByteArray, ReferenceContainer<ReferenceType>> entry: this.cache.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getTermHash() != null) cachecopy.add(entry.getValue());
            }
        }
        Collections.sort(cachecopy, this.containerOrder);
        return cachecopy;
    }

    private List<Rating<ByteArray>> ratingList() {
        final List<Rating<ByteArray>> list = new ArrayList<Rating<ByteArray>>(this.cache.size());
        synchronized (this.cache) {
            for (final Map.Entry<ByteArray, ReferenceContainer<ReferenceType>> entry: this.cache.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getTermHash() != null) list.add(new Rating<ByteArray>(entry.getKey(), entry.getValue().size()));
            }
        }
        return list;
    }

    @Override
    public int size() {
        return (this.cache == null) ? 0 : this.cache.size();
    }
    
    public long usedMemory() {
        if (this.cache == null) return 0;
        long b = 0L;
        for (Map.Entry<ByteArray, ReferenceContainer<ReferenceType>> e: this.cache.entrySet()) {
            b += e.getKey().usedMemory();
            b += e.getValue().mem();
        }
        return b;
    }

    public boolean isEmpty() {
        if (this.cache == null) return true;
        return this.cache.isEmpty();
    }

    public int maxReferences() {
        // iterate to find the max score
        int max = 0;
        for (final ReferenceContainer<ReferenceType> container : this.cache.values()) {
            if (container.size() > max) max = container.size();
        }
        return max;
    }

    @Override
    public Iterator<ReferenceContainer<ReferenceType>> iterator() {
        return referenceContainerIterator(null, false, false);
    }

    /**
     * return an iterator object that creates top-level-clones of the indexContainers
     * in the cache, so that manipulations of the iterated objects do not change
     * objects in the cache.
     */
    @Override
    public synchronized CloneableIterator<ReferenceContainer<ReferenceType>> referenceContainerIterator(final byte[] startWordHash, final boolean rot, final boolean excludePrivate) {
        return new ReferenceContainerIterator(startWordHash, rot, excludePrivate);
    }

    /**
     * cache iterator: iterates objects within the heap cache. This can only be used
     * for write-enabled heaps, read-only heaps do not have a heap cache
     */
    public class ReferenceContainerIterator implements CloneableIterator<ReferenceContainer<ReferenceType>>, Iterable<ReferenceContainer<ReferenceType>> {

        // this class exists, because the wCache cannot be iterated with rotation
        // and because every indexContainer Object that is iterated must be returned as top-level-clone
        // so this class simulates wCache.tailMap(startWordHash).values().iterator()
        // plus the mentioned features

        private final boolean rot, excludePrivate;
        private final List<ReferenceContainer<ReferenceType>> cachecopy;
        private int p;
        private byte[] latestTermHash;

        public ReferenceContainerIterator(byte[] startWordHash, final boolean rot, final boolean excludePrivate) {
            this.rot = rot;
            this.excludePrivate = excludePrivate;
            if (startWordHash != null && startWordHash.length == 0) startWordHash = null;
            this.cachecopy = sortedClone();
            assert this.cachecopy != null;
            assert ReferenceContainerCache.this.termOrder != null;
            this.p = 0;
            if (startWordHash != null) {
                byte[] b;
                while ( this.p < this.cachecopy.size() &&
                        ReferenceContainerCache.this.termOrder.compare(b = this.cachecopy.get(this.p).getTermHash(), startWordHash) < 0 &&
                        !(excludePrivate && Word.isPrivate(b))
                      ) this.p++;
            }
            this.latestTermHash = null;
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }

        @Override
        public ReferenceContainerIterator clone(final Object secondWordHash) {
            return new ReferenceContainerIterator((byte[]) secondWordHash, this.rot, this.excludePrivate);
        }

        @Override
        public boolean hasNext() {
            if (this.rot) return !this.cachecopy.isEmpty();
            return this.p < this.cachecopy.size();
        }

        @Override
        public ReferenceContainer<ReferenceType> next() {
            while (this.p < this.cachecopy.size()) {
                final ReferenceContainer<ReferenceType> c = this.cachecopy.get(this.p++);
                this.latestTermHash = c.getTermHash();
                if (this.excludePrivate && Word.isPrivate(this.latestTermHash)) continue;
                try {
                    return c.topLevelClone();
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                    return null;
                }
            }
            // rotation iteration
            if (!this.rot) {
                return null;
            }
            if (this.cachecopy.isEmpty()) return null;
            this.p = 0;
            while  (this.p < this.cachecopy.size()) {
                final ReferenceContainer<ReferenceType> c = this.cachecopy.get(this.p++);
                this.latestTermHash = c.getTermHash();
                if (this.excludePrivate && Word.isPrivate(this.latestTermHash)) continue;
                try {
                    return c.topLevelClone();
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                    return null;
                }
            }
            return null;
        }

        @Override
        public void remove() {
            System.arraycopy(this.cachecopy, this.p, this.cachecopy, this.p - 1, this.cachecopy.size() - this.p);
            ReferenceContainerCache.this.cache.remove(new ByteArray(this.latestTermHash));
        }

        @Override
        public Iterator<ReferenceContainer<ReferenceType>> iterator() {
            return this;
        }

        @Override
        public void close() {
        }
    }

    @Override
    public CloneableIterator<Rating<byte[]>> referenceCountIterator(final byte[] startHash, final boolean rot, boolean excludePrivate) {
        return new ReferenceCountIterator(startHash, rot, excludePrivate);
    }

    /**
     * cache iterator: iterates objects within the heap cache. This can only be used
     * for write-enabled heaps, read-only heaps do not have a heap cache
     */
    public class ReferenceCountIterator implements CloneableIterator<Rating<byte[]>>, Iterable<Rating<byte[]>> {

        private final boolean rot, excludePrivate;
        private final List<Rating<ByteArray>> cachecounts;
        private int p;
        private byte[] latestTermHash;

        public ReferenceCountIterator(byte[] startWordHash, final boolean rot, boolean excludePrivate) {
            this.rot = rot;
            this.excludePrivate = excludePrivate;
            if (startWordHash != null && startWordHash.length == 0) startWordHash = null;
            this.cachecounts = ratingList();
            assert this.cachecounts != null;
            assert ReferenceContainerCache.this.termOrder != null;
            this.p = 0;
            if (startWordHash != null) {
                byte[] b;
                while ( this.p < this.cachecounts.size() &&
                        ReferenceContainerCache.this.termOrder.compare(b = this.cachecounts.get(this.p).getObject().asBytes(), startWordHash) < 0 &&
                        !(excludePrivate && Word.isPrivate(b))
                      ) this.p++;
            }
            this.latestTermHash = null;
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }

        @Override
        public ReferenceCountIterator clone(final Object secondWordHash) {
            return new ReferenceCountIterator((byte[]) secondWordHash, this.rot, this.excludePrivate);
        }

        @Override
        public boolean hasNext() {
            if (this.rot) return !this.cachecounts.isEmpty();
            return this.p < this.cachecounts.size();
        }

        @Override
        public Rating<byte[]> next() {
            while (this.p < this.cachecounts.size()) {
                final Rating<ByteArray> c = this.cachecounts.get(this.p++);
                this.latestTermHash = c.getObject().asBytes();
                if (this.excludePrivate && Word.isPrivate(this.latestTermHash)) continue;
                return new Rating<byte[]>(c.getObject().asBytes(), c.getScore());
            }
            // rotation iteration
            if (!this.rot) {
                return null;
            }
            if (this.cachecounts.isEmpty()) return null;
            this.p = 0;
            while (this.p < this.cachecounts.size()) {
                final Rating<ByteArray> c = this.cachecounts.get(this.p++);
                this.latestTermHash = c.getObject().asBytes();
                if (this.excludePrivate && Word.isPrivate(this.latestTermHash)) continue;
                return new Rating<byte[]>(c.getObject().asBytes(), c.getScore());
            }
            return null;
        }

        @Override
        public void remove() {
            System.arraycopy(this.cachecounts, this.p, this.cachecounts, this.p - 1, this.cachecounts.size() - this.p);
            ReferenceContainerCache.this.cache.remove(new ByteArray(this.latestTermHash));
        }

        @Override
        public Iterator<Rating<byte[]>> iterator() {
            return this;
        }

        @Override
        public void close() {
        }
    }

    /**
     * test if a given key is in the heap
     * this works with heaps in write- and read-mode
     * @param key
     * @return true, if the key is used in the heap; false otherwise
     */
    @Override
    public boolean has(final byte[] key) {
        return this.cache.containsKey(new ByteArray(key));
    }

    /**
     * get a indexContainer from a heap
     * @param key
     * @return the indexContainer if one exist, null otherwise
     * @throws
     */
    @Override
    public ReferenceContainer<ReferenceType> get(final byte[] key, final HandleSet urlselection) {
        if (this.cache == null) return null;
        final ReferenceContainer<ReferenceType> c = this.cache.get(new ByteArray(key));
        if (urlselection == null) return c;
        if (c == null) return null;
        // because this is all in RAM, we must clone the entries (flat)
        try {
            final ReferenceContainer<ReferenceType> c1 = new ReferenceContainer<ReferenceType>(this.factory, c.getTermHash(), c.size());
            final Iterator<ReferenceType> e = c.entries();
            ReferenceType ee;
            while (e.hasNext()) {
                ee = e.next();
                if (urlselection.has(ee.urlhash())) {
                    c1.add(ee);
                }
            }
            return c1;
        } catch (final SpaceExceededException e2) {
            ConcurrentLog.logException(e2);
        }
        return null;
    }

    /**
     * return the size of the container with corresponding key
     * @param key
     * @return
     */
    @Override
    public int count(final byte[] key) {
        final ReferenceContainer<ReferenceType> c = this.cache.get(new ByteArray(key));
        if (c == null) return 0;
        return c.size();
    }

    /**
     * delete a indexContainer from the heap cache. This can only be used for write-enabled heaps
     * @param wordHash
     * @return the indexContainer if the cache contained the container, null otherwise
     */
    @Override
    public ReferenceContainer<ReferenceType> remove(final byte[] termHash) {
        // returns the index that had been deleted
        assert this.cache != null;
        if (this.cache == null) return null;
        return this.cache.remove(new ByteArray(termHash));
    }

    @Override
    public void delete(final byte[] termHash) {
        // returns the index that had been deleted
        assert this.cache != null;
        if (this.cache == null) return;
        this.cache.remove(new ByteArray(termHash));
    }

    @Override
    public void removeDelayed(final byte[] termHash, final byte[] urlHashBytes) {
        remove(termHash, urlHashBytes);
    }

    @Override
    public boolean remove(final byte[] termHash, final byte[] urlHashBytes) {
        assert this.cache != null;
        if (this.cache == null) return false;
        final ByteArray tha = new ByteArray(termHash);
        synchronized (this.cache) {
	        final ReferenceContainer<ReferenceType> c = this.cache.get(tha);
	        if (c != null && c.delete(urlHashBytes)) {
	            // removal successful
	            if (c.isEmpty()) {
	                delete(termHash);
	            } else {
	                this.cache.put(tha, c);
	            }
	            return true;
	        }
        }
        return false;
    }

    @Override
    public int remove(final byte[] termHash, final HandleSet urlHashes) {
        assert this.cache != null;
        if (this.cache == null) return  0;
        if (urlHashes.isEmpty()) return 0;
        final ByteArray tha = new ByteArray(termHash);
        int count;
        synchronized (this.cache) {
            final ReferenceContainer<ReferenceType> c = this.cache.get(tha);
            if ((c != null) && ((count = c.removeEntries(urlHashes)) > 0)) {
                // removal successful
                if (c.isEmpty()) {
                    delete(termHash);
                } else {
                    this.cache.put(tha, c);
                }
                return count;
            }
        }
        return 0;
    }

    @Override
    public void removeDelayed() {}

    @Override
    public void add(final ReferenceContainer<ReferenceType> container) throws SpaceExceededException {
        // this puts the entries into the cache
        if (this.cache == null || container == null || container.isEmpty()) return;

        // put new words into cache
        final ByteArray tha = new ByteArray(container.getTermHash());
        int added = 0;
        synchronized (this.cache) {
            ReferenceContainer<ReferenceType> entries = this.cache.get(tha); // null pointer exception? wordhash != null! must be cache==null
            if (entries == null) {
                entries = container.topLevelClone();
                added = entries.size();
            } else {
                added = entries.putAllRecent(container);
            }
            if (added > 0) {
                this.cache.put(tha, entries);
            }
            entries = null;
            return;
        }
    }

    @Override
    public void add(final byte[] termHash, final ReferenceType newEntry) throws SpaceExceededException {
        assert this.cache != null;
        if (this.cache == null) return;
        final ByteArray tha = new ByteArray(termHash);

        // first access the cache without synchronization
        ReferenceContainer<ReferenceType> container = this.cache.remove(tha);
        if (container == null) container = new ReferenceContainer<ReferenceType>(this.factory, termHash, 1);
        container.put(newEntry);

        // synchronization: check if the entry is still empty and set new value
        final ReferenceContainer<ReferenceType> container0 = this.cache.put(tha, container);
        if (container0 != null) synchronized (this.cache) {
            // no luck here, we get a lock exclusively to sort this out
            final ReferenceContainer<ReferenceType> containerNew = this.cache.put(tha, container0);
            if (containerNew == null) return;
            if (container0 == containerNew) {
                // The containers are the same, so nothing needs to be done
                return;
            }
            // Now merge the smaller container into the lager.
            // The other way around can become very slow
            if (container0.size() >= containerNew.size()) {
                container0.putAllRecent(containerNew);
       	        this.cache.put(tha, container0);
            } else {
                containerNew.putAllRecent(container0);
                this.cache.put(tha, containerNew);
            }
        }
    }

    @Override
    public int minMem() {
        return 0;
    }

    @Override
    public ByteOrder termKeyOrdering() {
        return this.termOrder;
    }

    public static class ContainerOrder<ReferenceType extends Reference> implements Comparator<ReferenceContainer<ReferenceType>> {
        private final ByteOrder o;
        public ContainerOrder(final ByteOrder order) {
            this.o = order;
        }
        @Override
        public int compare(final ReferenceContainer<ReferenceType> arg0, final ReferenceContainer<ReferenceType> arg1) {
            if (arg0 == arg1) return 0;
            if (arg0 == null) return -1;
            if (arg1 == null) return 1;
            return this.o.compare(arg0.getTermHash(), arg1.getTermHash());
        }
    }

}
