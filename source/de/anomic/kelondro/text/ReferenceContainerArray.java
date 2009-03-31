// ReferenceContainerArray.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.01.2009 on http://yacy.net
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
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import de.anomic.kelondro.blob.BLOB;
import de.anomic.kelondro.blob.BLOBArray;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowSet;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;

public final class ReferenceContainerArray {

    private final Row payloadrow;
    private final BLOBArray array;
    private final IODispatcher merger;
    
    /**
     * open a index container based on a BLOB dump. The content of the BLOB will not be read
     * unless a .idx file exists. Only the .idx file is opened to get a fast read access to
     * the BLOB. This class provides no write methods, because BLOB files should not be
     * written in random access. To support deletion, a write access to the BLOB for deletion
     * is still possible
     * @param payloadrow
     * @param log
     * @throws IOException 
     */
    public ReferenceContainerArray(
    		final File heapLocation,
    		final ByteOrder wordOrder,
    		final Row payloadrow,
    		IODispatcher merger) throws IOException {
        this.payloadrow = payloadrow;
        this.array = new BLOBArray(
            heapLocation,
            "index",
            payloadrow.primaryKeyLength,
            wordOrder,
            0);
        assert merger != null;
        this.merger = merger;
    }
    
    public synchronized void close() {
    	this.array.close(true);
    }
    
    public synchronized void clear() throws IOException {
    	this.array.clear();
    }
    
    public synchronized int size() {
        return (this.array == null) ? 0 : this.array.size();
    }
    
    public ByteOrder ordering() {
        return this.array.ordering();
    }
    
    public File newContainerBLOBFile() {
    	return this.array.newBLOB(new Date());
    }
    
    public void mountBLOBFile(File location) throws IOException {
        this.array.mountBLOB(location);
    }
    
    public Row rowdef() {
        return this.payloadrow;
    }
    
    /**
     * return an iterator object that creates top-level-clones of the indexContainers
     * in the cache, so that manipulations of the iterated objects do not change
     * objects in the cache.
     * @throws IOException 
     */
    public synchronized CloneableIterator<ReferenceContainer> wordContainerIterator(final String startWordHash, final boolean rot, final boolean ram) {
        try {
            return new heapCacheIterator(startWordHash, rot);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * cache iterator: iterates objects within the heap cache. This can only be used
     * for write-enabled heaps, read-only heaps do not have a heap cache
     */
    public class heapCacheIterator implements CloneableIterator<ReferenceContainer>, Iterable<ReferenceContainer> {

        // this class exists, because the wCache cannot be iterated with rotation
        // and because every indexContainer Object that is iterated must be returned as top-level-clone
        // so this class simulates wCache.tailMap(startWordHash).values().iterator()
        // plus the mentioned features
        
        private final boolean rot;
        private CloneableIterator<byte[]> iterator;
        
        public heapCacheIterator(final String startWordHash, final boolean rot) throws IOException {
            this.rot = rot;
            this.iterator = array.keys(true, startWordHash.getBytes());
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }
        
        public heapCacheIterator clone(final Object secondWordHash) {
            try {
				return new heapCacheIterator((String) secondWordHash, rot);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
        }
        
        public boolean hasNext() {
            if (rot) return true;
            return iterator.hasNext();
        }

        public ReferenceContainer next() {
        	try {
				if (iterator.hasNext()) {
                	return get(new String(iterator.next()));
				}
	            // rotation iteration
	            if (!rot) {
	                return null;
	            }
	            iterator = array.keys(true, null);
	            return get(new String(iterator.next()));
            } catch (IOException e) {
				e.printStackTrace();
				return null;
			}
        }

        public void remove() {
            iterator.remove();
        }

        public Iterator<ReferenceContainer> iterator() {
            return this;
        }
        
    }

    /**
     * test if a given key is in the heap
     * this works with heaps in write- and read-mode
     * @param key
     * @return true, if the key is used in the heap; false othervise
     * @throws IOException 
     */
    public synchronized boolean has(final String key) {
        return this.array.has(key.getBytes());
    }
    
    /**
     * get a indexContainer from a heap
     * @param key
     * @return the indexContainer if one exist, null otherwise
     * @throws IOException 
     */
    public synchronized ReferenceContainer get(final String key) throws IOException {
    	List<byte[]> entries = this.array.getAll(key.getBytes());
    	if (entries == null || entries.size() == 0) return null;
    	byte[] a = entries.remove(0);
    	ReferenceContainer c = new ReferenceContainer(key, RowSet.importRowSet(a, payloadrow));
    	while (entries.size() > 0) {
    		c = c.merge(new ReferenceContainer(key, RowSet.importRowSet(entries.remove(0), payloadrow)));
    	}
    	return c;
    }
    
    /**
     * delete a indexContainer from the heap cache. This can only be used for write-enabled heaps
     * @param wordHash
     * @return the indexContainer if the cache contained the container, null othervise
     * @throws IOException 
     */
    public synchronized void delete(final String wordHash) throws IOException {
        // returns the index that had been deleted
    	array.remove(wordHash.getBytes());
    }
    
    public synchronized int replace(final String wordHash, ContainerRewriter rewriter) throws IOException {
        return array.replace(wordHash.getBytes(), new BLOBRewriter(wordHash, rewriter));
    }
    
    public class BLOBRewriter implements BLOB.Rewriter {

        ContainerRewriter rewriter;
        String wordHash;
        
        public BLOBRewriter(String wordHash, ContainerRewriter rewriter) {
            this.rewriter = rewriter;
            this.wordHash = wordHash;
        }
        
        public byte[] rewrite(byte[] b) {
            if (b == null) return null;
            ReferenceContainer c = rewriter.rewrite(new ReferenceContainer(this.wordHash, RowSet.importRowSet(b, payloadrow)));
            if (c == null) return null;
            return c.exportCollection();
        }
    }

    public interface ContainerRewriter {
        
        public ReferenceContainer rewrite(ReferenceContainer container);
        
    }
    
    public int entries() {
        return this.array.entries();
    }
    
    public synchronized boolean shrink(long targetFileSize, long maxFileSize) throws IOException {
        if (this.array.entries() < 2) return false;
        if (this.merger.queueLength() > 0) return false;
        
        File[] ff = this.array.unmountBestMatch(2.0, targetFileSize);
        if (ff != null) {
            merger.merge(ff[0], ff[1], this.array, this.payloadrow, newContainerBLOBFile());
            return true;
        }
        
        ff = this.array.unmountSmallest(targetFileSize);
        if (ff != null) {
            merger.merge(ff[0], ff[1], this.array, this.payloadrow, newContainerBLOBFile());
            return true;
        }
        
        ff = this.array.unmountBestMatch(2.0, maxFileSize);
        if (ff != null) {
            merger.merge(ff[0], ff[1], this.array, this.payloadrow, newContainerBLOBFile());
            return true;
        }

        return false;
    }
    
    
}
