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
import de.anomic.kelondro.text.ReferenceRow;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.Log;
import de.anomic.server.serverProfiling;

public final class BufferedIndexCollection extends AbstractBufferedIndex implements Index, BufferedIndex {

    // environment constants
    public  static final long wCacheMaxAge    = 1000 * 60 * 30; // milliseconds; 30 minutes
    public  static final int  wCacheMaxChunk  =  800;           // maximum number of references for each urlhash
    public  static final int  lowcachedivisor =  900;
    public  static final int  maxCollectionPartition = 7;       // should be 7
    
    private final IndexBuffer      indexCache;
    private final IndexCollection collections;          // new database structure to replace AssortmentCluster and FileCluster
    
    public BufferedIndexCollection (
            File indexPrimaryTextLocation,
            final ByteOrder wordOrdering,
            final Row payloadrow,
            final int entityCacheMaxSize,
            final boolean useCommons, 
            final int redundancy,
            Log log) throws IOException {

        final File textindexcache = new File(indexPrimaryTextLocation, "RICACHE");
        if (!(textindexcache.exists())) textindexcache.mkdirs();
        if (new File(textindexcache, "index.dhtin.blob").exists()) {
            // migration of the both caches into one
            this.indexCache = new IndexBuffer(textindexcache, wordOrdering, payloadrow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtout.blob", log);
            IndexBuffer dhtInCache  = new IndexBuffer(textindexcache, wordOrdering, payloadrow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtin.blob", log);
            for (ReferenceContainer c: dhtInCache) {
                this.indexCache.add(c);
            }
            new File(textindexcache, "index.dhtin.blob").delete();
        } else {
            // read in new BLOB
            this.indexCache = new IndexBuffer(textindexcache, wordOrdering, payloadrow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtout.blob", log);            
        }
        
        // create collections storage path
        final File textindexcollections = new File(indexPrimaryTextLocation, "RICOLLECTION");
        if (!(textindexcollections.exists())) textindexcollections.mkdirs();
        this.collections = new IndexCollection(
                    textindexcollections, 
                    "collection",
                    12,
                    Base64Order.enhancedCoder,
                    maxCollectionPartition, 
                    ReferenceRow.urlEntryRow, 
                    useCommons);
    }

    /* methods for interface Index */
    
    public void add(final ReferenceContainer entries) {
        assert (entries.row().objectsize == ReferenceRow.urlEntryRow.objectsize);
 
        // add the entry
        indexCache.add(entries);
        cacheFlushControl();
    }
    
    public void add(final String wordHash, final ReferenceRow entry) throws IOException {
        // add the entry
        indexCache.add(wordHash, entry);
        cacheFlushControl();
    }

    public boolean has(final String wordHash) {
        if (indexCache.has(wordHash)) return true;
        if (collections.has(wordHash)) return true;
        return false;
    }
    
    public int count(String key) {
        return indexCache.count(key) + collections.count(key);
    }
    
    public ReferenceContainer get(final String wordHash, final Set<String> urlselection) {
        if (wordHash == null) {
            // wrong input
            return null;
        }
        
        // get from cache
        ReferenceContainer container;
        container = indexCache.get(wordHash, urlselection);
        
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
            ReferenceRow e, elm = null;
            long lm = 0;
            for (int j = 0; j < set.size(); j++) {
                e = new ReferenceRow(set.get(j, true));
                if ((elm == null) || (e.lastModified() > lm)) {
                    elm = e;
                    lm = e.lastModified();
                }
            }
            if(elm != null) {
                container.addUnique(elm.toKelondroEntry());
            }
        }
        if (container.size() < beforeDouble) System.out.println("*** DEBUG DOUBLECHECK - removed " + (beforeDouble - container.size()) + " index entries from word container " + container.getWordHash());

        return container;
    }

    public ReferenceContainer delete(final String wordHash) {
        final ReferenceContainer c = new ReferenceContainer(
                wordHash,
                ReferenceRow.urlEntryRow,
                indexCache.count(wordHash));
        c.addAllUnique(indexCache.delete(wordHash));
        c.addAllUnique(collections.delete(wordHash));
        return c;
    }
    
    public boolean remove(final String wordHash, final String urlHash) {
        boolean removed = false;
        removed = removed | (indexCache.remove(wordHash, urlHash));
        removed = removed | (collections.remove(wordHash, urlHash));
        return removed;
    }
    
    public int remove(final String wordHash, final Set<String> urlHashes) {
        int removed = 0;
        removed += indexCache.remove(wordHash, urlHashes);
        removed += collections.remove(wordHash, urlHashes);
        return removed;
    }
    
    public synchronized CloneableIterator<ReferenceContainer> references(final String startHash, final boolean rot, final boolean ram) throws IOException {
        final CloneableIterator<ReferenceContainer> i = wordContainers(startHash, ram);
        if (rot) {
            return new RotateIterator<ReferenceContainer>(i, new String(Base64Order.zero(startHash.length())), indexCache.size() + ((ram) ? 0 : collections.size()));
        }
        return i;
    }
    
    private synchronized CloneableIterator<ReferenceContainer> wordContainers(final String startWordHash, final boolean ram) throws IOException {
        final Order<ReferenceContainer> containerOrder = new ReferenceContainerOrder(indexCache.ordering().clone());
        containerOrder.rotate(ReferenceContainer.emptyContainer(startWordHash, 0));
        if (ram) {
            return indexCache.references(startWordHash, false);
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
        indexCache.clear();
        try {
            collections.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void close() {
        indexCache.close();
        collections.close();
    }
    
    public int size() {
        return java.lang.Math.max(collections.size(), indexCache.size());
    }
    
    public int minMem() {
        return 1024*1024 /* indexing overhead */ + indexCache.minMem() + collections.minMem();
    }

    
    /* 
     * methods for cache management
     */
    
    public int getBufferMaxReferences() {
        return indexCache.getBufferMaxReferences();
    }

    public long getBufferMinAge() {
        return indexCache.getBufferMinAge();
    }

    public long getBufferMaxAge() {
        return indexCache.getBufferMaxAge();
    }
    
    public long getBufferSizeBytes() {
        return indexCache.getBufferSizeBytes();
    }

    public void setBufferMaxWordCount(final int maxWords) {
        indexCache.setMaxWordCount(maxWords);
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
            while (this.indexCache.size() > 0 && (l++ < 100) && (this.indexCache.getBufferMaxReferences() > wCacheMaxChunk)) {
                flushCacheOne(this.indexCache);
            }
            // next flush more entries if the size exceeds the maximum size of the cache
            while (this.indexCache.size() > 0 &&
                    ((this.indexCache.size() > this.indexCache.getMaxWordCount()) ||
                    (MemoryControl.available() < collections.minMem()))) {
                flushCacheOne(this.indexCache);
            }
            if (getBufferSize() != cs) serverProfiling.update("wordcache", Long.valueOf(getBufferSize()), true);
        }
    }
    
    public void cleanupBuffer(int time) {
        flushCacheUntil(System.currentTimeMillis() + time);
    }
    
    private synchronized void flushCacheUntil(long timeout) {
        while (System.currentTimeMillis() < timeout && indexCache.size() > 0) {
            flushCacheOne(indexCache);
        }
    }
    
    private synchronized void flushCacheOne(final IndexBuffer ram) {
        if (ram.size() > 0) collections.add(flushContainer(ram));
    }
    
    private ReferenceContainer flushContainer(final IndexBuffer ram) {
        String wordHash;
        ReferenceContainer c;
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
        return indexCache.size();
    }

    public ByteOrder ordering() {
        return collections.ordering();
    }
    
    public CloneableIterator<ReferenceContainer> references(String startWordHash, boolean rot) {
        final Order<ReferenceContainer> containerOrder = new ReferenceContainerOrder(this.indexCache.ordering().clone());
        return new MergeIterator<ReferenceContainer>(
            this.indexCache.references(startWordHash, rot),
            new MergeIterator<ReferenceContainer>(
                this.indexCache.references(startWordHash, false),
                this.collections.references(startWordHash, false),
                containerOrder,
                ReferenceContainer.containerMergeMethod,
                true),
            containerOrder,
            ReferenceContainer.containerMergeMethod,
            true);
    }

}
