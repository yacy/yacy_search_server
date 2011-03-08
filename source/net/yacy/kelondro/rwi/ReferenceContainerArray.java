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

package net.yacy.kelondro.rwi;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;

import net.yacy.kelondro.blob.ArrayStack;
import net.yacy.kelondro.blob.BLOB;
import net.yacy.kelondro.index.HandleMap;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;


public final class ReferenceContainerArray<ReferenceType extends Reference> {

    protected final ReferenceFactory<ReferenceType> factory;
    protected final Row payloadrow;
    protected final ArrayStack array;
    private   final IODispatcher merger;
    
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
    		final String prefix,
    		final ReferenceFactory<ReferenceType> factory,
    		final ByteOrder termOrder,
    		final Row payloadrow,
    		IODispatcher merger) throws IOException {
        this.factory = factory;
        this.payloadrow = payloadrow;
        this.array = new ArrayStack(
            heapLocation,
            prefix,
            payloadrow.primaryKeyLength,
            termOrder,
            0,
            true);
        assert merger != null;
        this.merger = merger;
    }
    
    public void close() {
        this.array.close(true);
    }
    
    public void clear() throws IOException {
    	this.array.clear();
    }
    
    public long mem() {
        return array.mem();
    }
    
    public int[] sizes() {
        return (this.array == null) ? new int[0] : this.array.sizes();
    }
    
    public ByteOrder ordering() {
        return this.array.ordering();
    }
    
    public File newContainerBLOBFile() {
    	return this.array.newBLOB(new Date());
    }
    
    public void mountBLOBFile(File location) throws IOException {
        this.array.mountBLOB(location, false);
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
    public CloneableIterator<ReferenceContainer<ReferenceType>> wordContainerIterator(final byte[] startWordHash, final boolean rot) {
        try {
            return new heapCacheIterator(startWordHash, rot);
        } catch (IOException e) {
            Log.logException(e);
            return null;
        }
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
        protected CloneableIterator<byte[]> iterator;
        
        public heapCacheIterator(final byte[] startWordHash, final boolean rot) throws IOException {
            this.rot = rot;
            this.iterator = array.keys(true, startWordHash);
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }
        
        public heapCacheIterator clone(final Object secondWordHash) {
            try {
				return new heapCacheIterator((byte[]) secondWordHash, rot);
			} catch (IOException e) {
			    Log.logException(e);
				return null;
			}
        }
        
        public boolean hasNext() {
            if (this.iterator == null) return false;
            if (rot) return true;
            return iterator.hasNext();
        }

        public ReferenceContainer<ReferenceType> next() {
			if (iterator.hasNext()) try {
                return get(iterator.next());
            } catch (Exception e) {
                Log.logException(e);
                return null;
            }
            // rotation iteration
            if (!rot) {
                return null;
            }
            try {
                iterator = array.keys(true, null);
                return get(iterator.next());
            } catch (Exception e) {
                Log.logException(e);
                return null;
            }
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
     * @return true, if the key is used in the heap; false otherwise
     * @throws IOException 
     */
    public boolean has(final byte[] termHash) {
        return this.array.containsKey(termHash);
    }
    
    /**
     * get a indexContainer from a heap
     * @param key
     * @return the indexContainer if one exist, null otherwise
     * @throws IOException 
     * @throws RowSpaceExceededException 
     */
    public ReferenceContainer<ReferenceType> get(final byte[] termHash) throws IOException, RowSpaceExceededException {
        long timeout = System.currentTimeMillis() + 3000;
        Iterator<byte[]> entries = this.array.getAll(termHash).iterator();
    	if (entries == null || !entries.hasNext()) return null;
    	byte[] a = entries.next();
    	int k = 1;
    	ReferenceContainer<ReferenceType> c = new ReferenceContainer<ReferenceType>(this.factory, termHash, RowSet.importRowSet(a, payloadrow));
    	if (System.currentTimeMillis() > timeout) {
    	    Log.logWarning("ReferenceContainerArray", "timout in index retrieval (1): " + k + " tables searched. timeout = 3000");
    	    return c;
    	}
    	while (entries.hasNext()) {
    		c = c.merge(new ReferenceContainer<ReferenceType>(this.factory, termHash, RowSet.importRowSet(entries.next(), payloadrow)));
    		k++;
    		if (System.currentTimeMillis() > timeout) {
    		    Log.logWarning("ReferenceContainerArray", "timout in index retrieval (2): " + k + " tables searched. timeout = 3000");
    		    return c;
            }
    	}
    	return c;
    }
    
    public int count(final byte[] termHash) throws IOException {
        long timeout = System.currentTimeMillis() + 3000;
        Iterator<Long> entries = this.array.lengthAll(termHash).iterator();
        if (entries == null || !entries.hasNext()) return 0;
        Long a = entries.next();
        int k = 1;
        int c = RowSet.importRowCount(a, payloadrow);
        assert c >= 0;
        if (System.currentTimeMillis() > timeout) {
            Log.logWarning("ReferenceContainerArray", "timout in index retrieval (1): " + k + " tables searched. timeout = 3000");
            return c;
        }
        while (entries.hasNext()) {
            c += RowSet.importRowCount(entries.next(), payloadrow);
            assert c >= 0;
            k++;
            if (System.currentTimeMillis() > timeout) {
                Log.logWarning("ReferenceContainerArray", "timout in index retrieval (2): " + k + " tables searched. timeout = 3000");
                return c;
            }
        }
        assert c >= 0;
        return c;
    }
    
    /**
     * calculate an upper limit for a ranking number of the container size
     * the returned number is not a counter. It can only be used to compare the
     * ReferenceContainer, that may be produced as a result of get()
     * @param termHash
     * @return a ranking number
     * @throws IOException
     */
    public long lenghtRankingUpperLimit(final byte[] termHash) throws IOException {
        return this.array.lengthAdd(termHash);
    }
    
    /**
     * delete a indexContainer from the heap cache. This can only be used for write-enabled heaps
     * @param wordHash
     * @return the indexContainer if the cache contained the container, null otherwise
     * @throws IOException 
     */
    public void delete(final byte[] termHash) throws IOException {
        // returns the index that had been deleted
    	array.delete(termHash);
    }
    
    public int reduce(final byte[] termHash, ContainerReducer<ReferenceType> reducer) throws IOException, RowSpaceExceededException {
        return array.reduce(termHash, new BLOBReducer(termHash, reducer));
    }
    
    public class BLOBReducer implements BLOB.Reducer {

        ContainerReducer<ReferenceType> rewriter;
        byte[] wordHash;
        
        public BLOBReducer(byte[] wordHash, ContainerReducer<ReferenceType> rewriter) {
            this.rewriter = rewriter;
            this.wordHash = wordHash;
        }
        
        public byte[] rewrite(byte[] b) throws RowSpaceExceededException {
            if (b == null) return null;
            ReferenceContainer<ReferenceType> c = rewriter.reduce(new ReferenceContainer<ReferenceType>(factory, this.wordHash, RowSet.importRowSet(b, payloadrow)));
            if (c == null) return null;
            byte bb[] = c.exportCollection();
            assert bb.length <= b.length;
            return bb;
        }
    }

    public interface ContainerReducer<ReferenceType extends Reference> {
        
        public ReferenceContainer<ReferenceType> reduce(ReferenceContainer<ReferenceType> container);
        
    }
   
    public int entries() {
        return this.array.entries();
    }
    
    public boolean shrink(long targetFileSize, long maxFileSize) {
        if (this.array.entries() < 2) return false;
        boolean donesomething = false;
        
        // first try to merge small files that match
        while (this.merger.queueLength() < 3 || this.array.entries() >= 50) {
            File[] ff = this.array.unmountBestMatch(2.0f, targetFileSize);
            if (ff == null) break;
            Log.logInfo("RICELL-shrink1", "unmountBestMatch(2.0, " + targetFileSize + ")");
            merger.merge(ff[0], ff[1], this.factory, this.array, this.payloadrow, newContainerBLOBFile());
            donesomething = true;
        }
        
        // then try to merge simply any small file
        while (this.merger.queueLength() < 2) {
            File[] ff = this.array.unmountSmallest(targetFileSize);
            if (ff == null) break;
            Log.logInfo("RICELL-shrink2", "unmountSmallest(" + targetFileSize + ")");
            merger.merge(ff[0], ff[1], this.factory, this.array, this.payloadrow, newContainerBLOBFile());
            donesomething = true;
        }
        
        // if there is no small file, then merge matching files up to limit
        while (this.merger.queueLength() < 1) {
            File[] ff = this.array.unmountBestMatch(2.0f, maxFileSize);
            if (ff == null) break;
            Log.logInfo("RICELL-shrink3", "unmountBestMatch(2.0, " + maxFileSize + ")");
            merger.merge(ff[0], ff[1], this.factory, this.array, this.payloadrow, newContainerBLOBFile());
            donesomething = true;
        }

        // rewrite old files (hack from sixcooler, see http://forum.yacy-websuche.de/viewtopic.php?p=15004#p15004)
        while (this.merger.queueLength() < 1) {
            File ff = this.array.unmountOldest();
            if (ff == null) break;
            Log.logInfo("RICELL-shrink4/rewrite", "unmountOldest()");
            merger.merge(ff, null, this.factory, this.array, this.payloadrow, newContainerBLOBFile());
            donesomething = true;
        }

        return donesomething;
    }
    
    public static <ReferenceType extends Reference> HandleMap referenceHashes(
                            final File heapLocation,
                            final ReferenceFactory<ReferenceType> factory,
                            final ByteOrder termOrder,
                            final Row payloadrow) throws IOException, RowSpaceExceededException {
       
        System.out.println("CELL REFERENCE COLLECTION startup");
        HandleMap references = new HandleMap(payloadrow.primaryKeyLength, termOrder, 4, 1000000, heapLocation.getAbsolutePath());
        String[] files = heapLocation.list();
        for (String f: files) {
            if (f.length() < 22 || !f.startsWith("text.index") || !f.endsWith(".blob")) continue;
            File fl = new File(heapLocation, f);
            System.out.println("CELL REFERENCE COLLECTION opening blob " + fl);
            CloneableIterator<ReferenceContainer<ReferenceType>>  ei = new ReferenceIterator<ReferenceType>(fl, factory, payloadrow);
        
            ReferenceContainer<ReferenceType> container;
            final long start = System.currentTimeMillis();
            long lastlog = start - 27000;
            int count = 0;
            ReferenceType reference;
            byte[] mh;
            while (ei.hasNext()) {
                container = ei.next();
                if (container == null) continue;
                Iterator<ReferenceType> refi = container.entries();
                while (refi.hasNext()) {
                	reference = refi.next();
                	if (reference == null) continue;
                	mh = reference.metadataHash();
                	if (mh == null) continue;
                    references.inc(mh);
                }
                count++;
                // write a log
                if (System.currentTimeMillis() - lastlog > 30000) {
                    System.out.println("CELL REFERENCE COLLECTION scanned " + count + " RWI index entries. ");
                    lastlog = System.currentTimeMillis();
                }
            }
        }
        System.out.println("CELL REFERENCE COLLECTION finished");
        return references;
    }

    
}
