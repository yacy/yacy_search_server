// BufferedIndexCollection.java
// (C) 2005, 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2005 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-03-13 11:34:51 +0100 (Fr, 13 Mrz 2009) $
// $LastChangedRevision: 5709 $
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

package de.anomic.kelondro.text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowCollection;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.MergeIterator;
import de.anomic.kelondro.order.Order;
import de.anomic.kelondro.order.RotateIterator;
import de.anomic.kelondro.text.Index;
import de.anomic.kelondro.text.IndexBuffer;
import de.anomic.kelondro.text.IndexCollection;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.ReferenceContainerOrder;
import de.anomic.kelondro.text.referencePrototype.WordReferenceRow;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.Log;
import de.anomic.server.serverProfiling;

public final class BufferedIndexCollection<ReferenceType extends Reference> extends AbstractBufferedIndex<ReferenceType> implements Index<ReferenceType>, BufferedIndex<ReferenceType> {

    // environment constants
    public  static final long wCacheMaxAge    = 1000 * 60 * 30; // milliseconds; 30 minutes
    public  static final int  wCacheMaxChunk  =  800;           // maximum number of references for each urlhash
    public  static final int  lowcachedivisor =  900;
    public  static final int  maxCollectionPartition = 7;       // should be 7
    
    private final IndexBuffer<ReferenceType>     buffer;
    private final IndexCollection<ReferenceType> collections;
    
    public BufferedIndexCollection (
            File indexPrimaryTextLocation,
            final ReferenceFactory<ReferenceType> factory,
            final ByteOrder wordOrdering,
            final Row payloadrow,
            final int entityCacheMaxSize,
            final boolean useCommons, 
            final int redundancy,
            Log log) throws IOException {
        super(factory);
        
        final File textindexcache = new File(indexPrimaryTextLocation, "RICACHE");
        if (!(textindexcache.exists())) textindexcache.mkdirs();
        if (new File(textindexcache, "index.dhtin.blob").exists()) {
            // migration of the both caches into one
            this.buffer = new IndexBuffer<ReferenceType>(textindexcache, factory, wordOrdering, payloadrow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtout.blob", log);
            IndexBuffer<ReferenceType> dhtInCache  = new IndexBuffer<ReferenceType>(textindexcache, factory, wordOrdering, payloadrow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtin.blob", log);
            for (ReferenceContainer<ReferenceType> c: dhtInCache) {
                this.buffer.add(c);
            }
            FileUtils.deletedelete(new File(textindexcache, "index.dhtin.blob"));
        } else {
            // read in new BLOB
            this.buffer = new IndexBuffer<ReferenceType>(textindexcache, factory, wordOrdering, payloadrow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtout.blob", log);            
        }
        
        // create collections storage path
        final File textindexcollections = new File(indexPrimaryTextLocation, "RICOLLECTION");
        if (!(textindexcollections.exists())) textindexcollections.mkdirs();
        this.collections = new IndexCollection<ReferenceType>(
                    textindexcollections, 
                    "collection",
                    factory,
                    12,
                    Base64Order.enhancedCoder,
                    maxCollectionPartition, 
                    WordReferenceRow.urlEntryRow, 
                    useCommons);
    }

    /* methods for interface Index */
    
    public void add(final ReferenceContainer<ReferenceType> entries) {
        assert (entries.row().objectsize == WordReferenceRow.urlEntryRow.objectsize);
 
        // add the entry
        buffer.add(entries);
        cacheFlushControl();
    }
    
    public void add(final byte[] wordHash, final ReferenceType entry) throws IOException {
        // add the entry
        buffer.add(wordHash, entry);
        cacheFlushControl();
    }

    public boolean has(final byte[] wordHash) {
        if (buffer.has(wordHash)) return true;
        if (collections.has(wordHash)) return true;
        return false;
    }
    
    public int count(byte[] key) {
        return buffer.count(key) + collections.count(key);
    }
    
    public ReferenceContainer<ReferenceType> get(final byte[] wordHash, final Set<String> urlselection) {
        if (wordHash == null) {
            // wrong input
            return null;
        }
        
        // get from cache
        ReferenceContainer<ReferenceType> container;
        container = buffer.get(wordHash, urlselection);
        
        // get from collection index
        if (container == null) {
            container = collections.get(wordHash, urlselection);
        } else {
            container.addAllUnique(collections.get(wordHash, urlselection));
        }
        
        if (container == null) return null;
        
        // check doubles
        final int beforeDouble = container.size();
        container.sort();
        final ArrayList<RowCollection> d = container.removeDoubles();
        RowCollection set;
        for (int i = 0; i < d.size(); i++) {
            // for each element in the double-set, take that one that is the most recent one
            set = d.get(i);
            WordReferenceRow e, elm = null;
            long lm = 0;
            for (int j = 0; j < set.size(); j++) {
                e = new WordReferenceRow(set.get(j, true));
                if ((elm == null) || (e.lastModified() > lm)) {
                    elm = e;
                    lm = e.lastModified();
                }
            }
            if(elm != null) {
                container.addUnique(elm.toKelondroEntry());
            }
        }
        if (container.size() < beforeDouble) System.out.println("*** DEBUG DOUBLECHECK - removed " + (beforeDouble - container.size()) + " index entries from word container " + container.getTermHashAsString());

        return container;
    }

    public ReferenceContainer<ReferenceType> delete(final byte[] wordHash) {
        final ReferenceContainer<ReferenceType> c = new ReferenceContainer<ReferenceType>(
                factory,
                wordHash,
                WordReferenceRow.urlEntryRow,
                buffer.count(wordHash));
        c.addAllUnique(buffer.delete(wordHash));
        c.addAllUnique(collections.delete(wordHash));
        return c;
    }
    
    public boolean remove(final byte[] wordHash, final String urlHash) {
        boolean removed = false;
        removed = removed | (buffer.remove(wordHash, urlHash));
        removed = removed | (collections.remove(wordHash, urlHash));
        return removed;
    }
    
    public int remove(final byte[] wordHash, final Set<String> urlHashes) {
        int removed = 0;
        removed += buffer.remove(wordHash, urlHashes);
        removed += collections.remove(wordHash, urlHashes);
        return removed;
    }
    
    public synchronized CloneableIterator<ReferenceContainer<ReferenceType>> references(final byte[] startHash, final boolean rot, final boolean ram) throws IOException {
        final CloneableIterator<ReferenceContainer<ReferenceType>> i = wordContainers(startHash, ram);
        if (rot) {
            return new RotateIterator<ReferenceContainer<ReferenceType>>(i, Base64Order.zero(startHash.length), buffer.size() + ((ram) ? 0 : collections.size()));
        }
        return i;
    }
    
    private synchronized CloneableIterator<ReferenceContainer<ReferenceType>> wordContainers(final byte[] startWordHash, final boolean ram) throws IOException {
        final Order<ReferenceContainer<ReferenceType>> containerOrder = new ReferenceContainerOrder<ReferenceType>(factory, buffer.ordering().clone());
        ReferenceContainer<ReferenceType> emptyContainer = ReferenceContainer.emptyContainer(factory, startWordHash, 0);
        containerOrder.rotate(emptyContainer);
        if (ram) {
            return buffer.references(startWordHash, false);
        }
        return collections.references(startWordHash, false);
        /*
        return new MergeIterator<ReferenceContainer>(
                indexCache.referenceIterator(startWordHash, false, true),
                collections.referenceIterator(startWordHash, false, false),
                containerOrder,
                ReferenceContainer.containerMergeMethod,
                true);
        */
    }
    
    public void clear() {
        buffer.clear();
        try {
            collections.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void close() {
        buffer.close();
        collections.close();
    }
    
    public int size() {
        return java.lang.Math.max(collections.size(), buffer.size());
    }
    
    public int minMem() {
        return 1024*1024 /* indexing overhead */ + buffer.minMem() + collections.minMem();
    }

    
    /* 
     * methods for cache management
     */
    
    public int getBufferMaxReferences() {
        return buffer.getBufferMaxReferences();
    }

    public long getBufferMinAge() {
        return buffer.getBufferMinAge();
    }

    public long getBufferMaxAge() {
        return buffer.getBufferMaxAge();
    }
    
    public long getBufferSizeBytes() {
        return buffer.getBufferSizeBytes();
    }

    public void setBufferMaxWordCount(final int maxWords) {
        buffer.setMaxWordCount(maxWords);
    }

    private void cacheFlushControl() {
        // check for forced flush
        int cs = getBufferSize();
        if (cs > 0) {
            // flush elements that are too big. This flushing depends on the fact that the flush rule
            // selects the biggest elements first for flushing. If it does not for any reason, the following
            // loop would not terminate.
            serverProfiling.update("wordcache", Long.valueOf(cs), true);
            // To ensure termination an additional counter is used
            int l = 0;
            while (this.buffer.size() > 0 && (l++ < 100) && (this.buffer.getBufferMaxReferences() > wCacheMaxChunk)) {
                flushCacheOne(this.buffer);
            }
            // next flush more entries if the size exceeds the maximum size of the cache
            while (this.buffer.size() > 0 &&
                    ((this.buffer.size() > this.buffer.getMaxWordCount()) ||
                    (MemoryControl.available() < collections.minMem()))) {
                flushCacheOne(this.buffer);
            }
            if (getBufferSize() != cs) serverProfiling.update("wordcache", Long.valueOf(getBufferSize()), true);
        }
    }
    
    public void cleanupBuffer(int time) {
        flushCacheUntil(System.currentTimeMillis() + time);
    }
    
    private synchronized void flushCacheUntil(long timeout) {
        while (System.currentTimeMillis() < timeout && buffer.size() > 0) {
            flushCacheOne(buffer);
        }
    }
    
    private synchronized void flushCacheOne(final IndexBuffer<ReferenceType> ram) {
        if (ram.size() > 0) collections.add(flushContainer(ram));
    }
    
    private ReferenceContainer<ReferenceType> flushContainer(final IndexBuffer<ReferenceType> ram) {
        byte[] wordHash;
        ReferenceContainer<ReferenceType> c;
        wordHash = ram.maxScoreWordHash();
        c = ram.get(wordHash, null);
        if ((c != null) && (c.size() > wCacheMaxChunk)) {
            return ram.delete(wordHash);
        } else {
            return ram.delete(ram.bestFlushWordHash());
        }
    }

    public int getBackendSize() {
        return collections.size();
    }
    
    public int getBufferSize() {
        return buffer.size();
    }

    public ByteOrder ordering() {
        return collections.ordering();
    }
    
    public CloneableIterator<ReferenceContainer<ReferenceType>> references(byte[] startWordHash, boolean rot) {
        final Order<ReferenceContainer<ReferenceType>> containerOrder = new ReferenceContainerOrder<ReferenceType>(factory, this.buffer.ordering().clone());
        return new MergeIterator<ReferenceContainer<ReferenceType>>(
                this.buffer.references(startWordHash, false),
                this.collections.references(startWordHash, false),
                containerOrder,
                ReferenceContainer.containerMergeMethod,
                true);
    }

}
