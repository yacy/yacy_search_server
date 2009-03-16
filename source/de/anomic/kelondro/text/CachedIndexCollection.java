// plasmaWordIndex.java
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.kelondro.index.RowCollection;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.Order;
import de.anomic.kelondro.order.RotateIterator;
import de.anomic.kelondro.text.Index;
import de.anomic.kelondro.text.IndexCache;
import de.anomic.kelondro.text.IndexCollection;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.ReferenceContainerOrder;
import de.anomic.kelondro.text.ReferenceRow;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.Log;
import de.anomic.server.serverProfiling;

public final class CachedIndexCollection extends AbstractIndex implements Index, IndexPackage {

    // environment constants
    public  static final long wCacheMaxAge    = 1000 * 60 * 30; // milliseconds; 30 minutes
    public  static final int  wCacheMaxChunk  =  800;           // maximum number of references for each urlhash
    public  static final int  lowcachedivisor =  900;
    public  static final int  maxCollectionPartition = 7;       // should be 7
    private static final ByteOrder indexOrder = Base64Order.enhancedCoder;
    

    
    private final IndexCache      indexCache;
    private final IndexCollection collections;          // new database structure to replace AssortmentCluster and FileCluster
    
    public CachedIndexCollection (
            File indexPrimaryTextLocation,
            final int entityCacheMaxSize,
            final boolean useCommons, 
            final int redundancy,
            Log log) throws IOException {

        final File textindexcache = new File(indexPrimaryTextLocation, "RICACHE");
        if (!(textindexcache.exists())) textindexcache.mkdirs();
        if (new File(textindexcache, "index.dhtin.blob").exists()) {
            // migration of the both caches into one
            this.indexCache = new IndexCache(textindexcache, ReferenceRow.urlEntryRow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtout.blob", log);
            IndexCache dhtInCache  = new IndexCache(textindexcache, ReferenceRow.urlEntryRow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtin.blob", log);
            for (ReferenceContainer c: dhtInCache) {
                this.indexCache.addReferences(c);
            }
            new File(textindexcache, "index.dhtin.blob").delete();
        } else {
            // read in new BLOB
            this.indexCache = new IndexCache(textindexcache, ReferenceRow.urlEntryRow, entityCacheMaxSize, wCacheMaxChunk, wCacheMaxAge, "index.dhtout.blob", log);            
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
    
    public void addReferences(final ReferenceContainer entries) {
        assert (entries.row().objectsize == ReferenceRow.urlEntryRow.objectsize);
 
        // add the entry
        indexCache.addReferences(entries);
        cacheFlushControl();
    }
    
    public boolean hasReferences(final String wordHash) {
        if (indexCache.hasReferences(wordHash)) return true;
        if (collections.hasReferences(wordHash)) return true;
        return false;
    }
    
    public int countReferences(String key) {
        return indexCache.countReferences(key) + collections.countReferences(key);
    }
    
    public ReferenceContainer getReferences(final String wordHash, final Set<String> urlselection) {
        if (wordHash == null) {
            // wrong input
            return null;
        }
        
        // get from cache
        ReferenceContainer container;
        container = indexCache.getReferences(wordHash, urlselection);
        
        // get from collection index
        if (container == null) {
            container = collections.getReferences(wordHash, urlselection);
        } else {
            container.addAllUnique(collections.getReferences(wordHash, urlselection));
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

    public ReferenceContainer deleteAllReferences(final String wordHash) {
        final ReferenceContainer c = new ReferenceContainer(
                wordHash,
                ReferenceRow.urlEntryRow,
                indexCache.countReferences(wordHash));
        c.addAllUnique(indexCache.deleteAllReferences(wordHash));
        c.addAllUnique(collections.deleteAllReferences(wordHash));
        return c;
    }
    
    public boolean removeReference(final String wordHash, final String urlHash) {
        boolean removed = false;
        removed = removed | (indexCache.removeReference(wordHash, urlHash));
        removed = removed | (collections.removeReference(wordHash, urlHash));
        return removed;
    }
    
    public int removeReferences(final String wordHash, final Set<String> urlHashes) {
        int removed = 0;
        removed += indexCache.removeReferences(wordHash, urlHashes);
        removed += collections.removeReferences(wordHash, urlHashes);
        return removed;
    }
    
    public synchronized CloneableIterator<ReferenceContainer> referenceIterator(final String startHash, final boolean rot, final boolean ram) {
        final CloneableIterator<ReferenceContainer> i = wordContainers(startHash, ram);
        if (rot) {
            return new RotateIterator<ReferenceContainer>(i, new String(Base64Order.zero(startHash.length())), indexCache.size() + ((ram) ? 0 : collections.size()));
        }
        return i;
    }
    
    private synchronized CloneableIterator<ReferenceContainer> wordContainers(final String startWordHash, final boolean ram) {
        final Order<ReferenceContainer> containerOrder = new ReferenceContainerOrder(indexOrder.clone());
        containerOrder.rotate(ReferenceContainer.emptyContainer(startWordHash, 0));
        if (ram) {
            return indexCache.referenceIterator(startWordHash, false, true);
        }
        return collections.referenceIterator(startWordHash, false, false);
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
    
    public int maxURLinCache() {
        return indexCache.maxURLinCache();
    }

    public long minAgeOfCache() {
        return indexCache.minAgeOfCache();
    }

    public long maxAgeOfCache() {
        return indexCache.maxAgeOfCache();
    }

    public int indexCacheSize() {
        return indexCache.size();
    }
    
    public long indexCacheSizeBytes() {
        // calculate the real size in bytes of the index cache
        long cacheBytes = 0;
        final long entryBytes = ReferenceRow.urlEntryRow.objectsize;
        final IndexCache cache = (indexCache);
        synchronized (cache) {
            final Iterator<ReferenceContainer> it = cache.referenceIterator(null, false, true);
            while (it.hasNext()) cacheBytes += it.next().size() * entryBytes;
        }
        return cacheBytes;
    }

    public void setMaxWordCount(final int maxWords) {
        indexCache.setMaxWordCount(maxWords);
    }

    public void cacheFlushControl() {
        // check for forced flush
        int cs = cacheSize();
        if (cs > 0) {
            // flush elements that are too big. This flushing depends on the fact that the flush rule
            // selects the biggest elements first for flushing. If it does not for any reason, the following
            // loop would not terminate.
            serverProfiling.update("wordcache", Long.valueOf(cs), true);
            // To ensure termination an additional counter is used
            int l = 0;
            while (this.indexCache.size() > 0 && (l++ < 100) && (this.indexCache.maxURLinCache() > wCacheMaxChunk)) {
                flushCacheOne(this.indexCache);
            }
            // next flush more entries if the size exceeds the maximum size of the cache
            while (this.indexCache.size() > 0 &&
                    ((this.indexCache.size() > this.indexCache.getMaxWordCount()) ||
                    (MemoryControl.available() < collections.minMem()))) {
                flushCacheOne(this.indexCache);
            }
            if (cacheSize() != cs) serverProfiling.update("wordcache", Long.valueOf(cacheSize()), true);
        }
    }
    
    public void flushCacheFor(int time) {
        flushCacheUntil(System.currentTimeMillis() + time);
    }
    
    private synchronized void flushCacheUntil(long timeout) {
        while (System.currentTimeMillis() < timeout && indexCache.size() > 0) {
            flushCacheOne(indexCache);
        }
    }
    
    private synchronized void flushCacheOne(final IndexCache ram) {
        if (ram.size() > 0) collections.addReferences(flushContainer(ram));
    }
    
    private ReferenceContainer flushContainer(final IndexCache ram) {
        String wordHash;
        ReferenceContainer c;
        wordHash = ram.maxScoreWordHash();
        c = ram.getReferences(wordHash, null);
        if ((c != null) && (c.size() > wCacheMaxChunk)) {
            return ram.deleteAllReferences(wordHash);
        } else {
            return ram.deleteAllReferences(ram.bestFlushWordHash());
        }
    }

    public int backendSize() {
        return collections.size();
    }
    
    public int cacheSize() {
        return indexCache.size();
    }

    
    /*
     * methods to update the index
     */
    
    public void addEntry(final String wordHash, final ReferenceRow entry, final long updateTime) {
        // add the entry
        indexCache.addEntry(wordHash, entry, updateTime, true);
        cacheFlushControl();
    }


    /*
     * methods to search the index
     */

    @SuppressWarnings("unchecked")
    public HashMap<String, ReferenceContainer>[] localSearchContainers(
                            final TreeSet<String> queryHashes, 
                            final TreeSet<String> excludeHashes, 
                            final Set<String> urlselection) {
        // search for the set of hashes and return a map of of wordhash:indexContainer containing the seach result

        // retrieve entities that belong to the hashes
        HashMap<String, ReferenceContainer> inclusionContainers = (queryHashes.size() == 0) ? new HashMap<String, ReferenceContainer>(0) : getContainers(
                        queryHashes,
                        urlselection);
        if ((inclusionContainers.size() != 0) && (inclusionContainers.size() < queryHashes.size())) inclusionContainers = new HashMap<String, ReferenceContainer>(0); // prevent that only a subset is returned
        final HashMap<String, ReferenceContainer> exclusionContainers = (inclusionContainers.size() == 0) ? new HashMap<String, ReferenceContainer>(0) : getContainers(
                excludeHashes,
                urlselection);
        return new HashMap[]{inclusionContainers, exclusionContainers};
    }
    
    /**
     * collect containers for given word hashes. This collection stops if a single container does not contain any references.
     * In that case only a empty result is returned.
     * @param wordHashes
     * @param urlselection
     * @return map of wordhash:indexContainer
     */
    private HashMap<String, ReferenceContainer> getContainers(final Set<String> wordHashes, final Set<String> urlselection) {
        // retrieve entities that belong to the hashes
        final HashMap<String, ReferenceContainer> containers = new HashMap<String, ReferenceContainer>(wordHashes.size());
        String singleHash;
        ReferenceContainer singleContainer;
            final Iterator<String> i = wordHashes.iterator();
            while (i.hasNext()) {
            
                // get next word hash:
                singleHash = i.next();
            
                // retrieve index
                singleContainer = getReferences(singleHash, urlselection);
            
                // check result
                if ((singleContainer == null || singleContainer.size() == 0)) return new HashMap<String, ReferenceContainer>(0);
            
                containers.put(singleHash, singleContainer);
            }
        return containers;
    }

    public synchronized TreeSet<ReferenceContainer> indexContainerSet(final String startHash, final boolean ram, final boolean rot, int count) {
        // creates a set of indexContainers
        // this does not use the cache
        final Order<ReferenceContainer> containerOrder = new ReferenceContainerOrder(indexOrder.clone());
        containerOrder.rotate(ReferenceContainer.emptyContainer(startHash, 0));
        final TreeSet<ReferenceContainer> containers = new TreeSet<ReferenceContainer>(containerOrder);
        final Iterator<ReferenceContainer> i = referenceIterator(startHash, rot, ram);
        if (ram) count = Math.min(indexCache.size(), count);
        ReferenceContainer container;
        // this loop does not terminate using the i.hasNex() predicate when rot == true
        // because then the underlying iterator is a rotating iterator without termination
        // in this case a termination must be ensured with a counter
        // It must also be ensured that the counter is in/decreased every loop
        while ((count > 0) && (i.hasNext())) {
            container = i.next();
            if ((container != null) && (container.size() > 0)) {
                containers.add(container);
            }
            count--; // decrease counter even if the container was null or empty to ensure termination
        }
        return containers; // this may return less containers as demanded
    }

}
