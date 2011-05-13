/**
 *  Cache.java
 *  Copyright 2006 by Michael Peter Christen
 *  First released 26.10.2006 at http://yacy.net
 *  
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.index;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.util.MemoryControl;


public final class Cache implements Index, Iterable<Row.Entry> {

    // this is a combined read cache and write buffer
    // we maintain four tables:
    // - a read-cache
    // - a miss-cache
    // - a write buffer for rows that are not contained in the target index
    // - a write buffer for rows that are known to be contained in target
    // furthermore, if we access a kelondroFlexTable, we can use the ram index of the underlying index

    // static object tracker; stores information about object cache usage
    private static final TreeMap<String, Cache> objectTracker = new TreeMap<String, Cache>();
    private static final long memStopGrow    = 40 * 1024 * 1024; // a limit for the node cache to stop growing if less than this memory amount is available
    private static final long memStartShrink = 20 * 1024 * 1024; // a limit for the node cache to start with shrinking if less than this memory amount is available
    
    // class objects
    private final Index index;         // the back-end of the cache
    private       RowSet      readHitCache;  // contains a complete copy of the cached objects
    private       RowSet      readMissCache; // contains only the keys of the objects that had been a miss
    private       Row         keyrow;
    private       int         readHit, readMiss, writeUnique, writeDouble, cacheDelete, cacheFlush;
    private       int         hasnotHit, hasnotMiss, hasnotUnique, hasnotDouble, hasnotDelete;
    private final int         hitLimit, missLimit;
    
    /**
     * create a ObjectIndex cache. The cache may either limited by a number of entries in the hit/miss cache
     * or the cache size can only be limited by the available RAM
     * @param backupIndex the ObjectIndex that is cached
     * @param hitLimit a limit of cache hit entries. If given as value <= 0, then only the RAM limits the size
     * @param missLimit a limit of cache miss entries. If given as value <= 0, then only the RAM limits the size
     */
    public Cache(final Index backupIndex, final int hitLimit, final int missLimit) {
        this.index = backupIndex;
        this.hitLimit = hitLimit;
        this.missLimit = missLimit;
        init();
        objectTracker.put(backupIndex.filename(), this);
    }

    private void init() {
        Row row = index.row();
        this.keyrow = new Row(new Column[]{row.column(0)}, row.objectOrder);
        this.readHitCache = new RowSet(row);
        this.readMissCache = new RowSet(this.keyrow);
        this.readHit = 0;
        this.readMiss = 0;
        this.writeUnique = 0;
        this.writeDouble = 0;
        this.cacheDelete = 0;
        this.cacheFlush = 0;
        this.hasnotHit = 0;
        this.hasnotMiss = 0;
        this.hasnotUnique = 0;
        this.hasnotDouble = 0;
        this.hasnotDelete = 0;
    }
    
    public long mem() {
        return this.index.mem() + this.readHitCache.mem() + this.readMissCache.mem();
    }
    
    public final int writeBufferSize() {
        return 0;
    }
    
    public final static long getMemStopGrow() {
        return memStopGrow ;
    }
    
    public final static long getMemStartShrink() {
        return memStartShrink ;
    }
    
    public final int getHitLimit() {
        return this.hitLimit;
    }
    
    public final int getMissLimit() {
        return this.missLimit;
    }
    
    public byte[] smallestKey() {
        return this.index.smallestKey();
    }
    
    public byte[] largestKey() {
        return this.index.largestKey();
    }

    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return objectTracker.keySet().iterator();
    }

    public static final Map<String, String> memoryStats(final String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record oobjects,
        // i.e. for cache memory allocation
        final Cache theObjectsCache = objectTracker.get(filename);
        return theObjectsCache.memoryStats();
    }
    
    private final Map<String, String> memoryStats() {
        // returns statistical data about this object
        final HashMap<String, String> map = new HashMap<String, String>(20);
        map.put("objectHitChunkSize", (readHitCache == null) ? "0" : Integer.toString(readHitCache.rowdef.objectsize));
        map.put("objectHitCacheCount", (readHitCache == null) ? "0" : Integer.toString(readHitCache.size()));
        map.put("objectHitMem", (readHitCache == null) ? "0" : Long.toString(readHitCache.rowdef.objectsize * readHitCache.size()));
        map.put("objectHitCacheReadHit", Integer.toString(readHit));
        map.put("objectHitCacheReadMiss", Integer.toString(readMiss));
        map.put("objectHitCacheWriteUnique", Integer.toString(writeUnique));
        map.put("objectHitCacheWriteDouble", Integer.toString(writeDouble));
        map.put("objectHitCacheDeletes", Integer.toString(cacheDelete));
        map.put("objectHitCacheFlushes", Integer.toString(cacheFlush));
        
        map.put("objectMissChunkSize", (readMissCache == null) ? "0" : Integer.toString(readMissCache.rowdef.objectsize));
        map.put("objectMissCacheCount", (readMissCache == null) ? "0" : Integer.toString(readMissCache.size()));
        map.put("objectMissMem", (readMissCache == null) ? "0" : Long.toString(readMissCache.rowdef.objectsize * readMissCache.size()));
        map.put("objectMissCacheReadHit", Integer.toString(hasnotHit));
        map.put("objectMissCacheReadMiss", Integer.toString(hasnotMiss));
        map.put("objectMissCacheWriteUnique", Integer.toString(hasnotUnique));
        map.put("objectMissCacheWriteDouble", Integer.toString(hasnotDouble));
        map.put("objectMissCacheDeletes", Integer.toString(hasnotDelete));
        map.put("objectMissCacheFlushes", "0"); // a miss cache flush can only happen if we have a deletion cache (which we dont have)
        
        // future feature .. map.put("objectElderTimeRead", index.profile().)
        return map;
    }

    
    /**
     * checks for space in the miss cache
     * @return true if it is allowed to write into this cache
     */
    private final boolean checkMissSpace() {
        // returns true if it is allowed to write into this cache
        if (readMissCache == null) return false;
        
        // check given limitation
        if (this.missLimit > 0 && this.readMissCache.size() >= this.missLimit) return false;
        
        // check memory
        long available = MemoryControl.available();
        if (MemoryControl.shortStatus() || available - 2 * 1024 * 1024 < readMissCache.memoryNeededForGrow()) {
            readMissCache.clear();
        }
        available = MemoryControl.available();
        return (available - 2 * 1024 * 1024 > readMissCache.memoryNeededForGrow());
    }
    
    /**
     * checks for space in the hit cache
     * @return true if it is allowed to write into this cache
     */
    private final boolean checkHitSpace() {
        // returns true if it is allowed to write into this cache
        if (readHitCache == null) return false;
        
        // check given limitation
        if (this.hitLimit > 0 && this.readHitCache.size() >= this.hitLimit) return false;
        
        // check memory
        long available = MemoryControl.available();
        if (MemoryControl.shortStatus() || available - 2 * 1024 * 1024 < readHitCache.memoryNeededForGrow()) {
            readHitCache.clear();
        }
        available = MemoryControl.available();
        return (available - 2 * 1024 * 1024 > readHitCache.memoryNeededForGrow());
    }
    
    public final synchronized void clearCache() {
        if (readMissCache != null) readMissCache.clear();
        if (readHitCache != null) readHitCache.clear();
    }
    
    public final synchronized void close() {
        index.close();
        readHitCache = null;
        readMissCache = null;
    }

    public final synchronized boolean has(final byte[] key) {
        // first look into the miss cache
        if (readMissCache != null) {
            if (readMissCache.has(key)) {
                this.hasnotHit++;
                return false;
            } else {
                this.hasnotMiss++;
            }
        }

        // then try the hit cache and the buffers
        if (readHitCache != null) {
            if (readHitCache.has(key)) {
                this.readHit++;
                return true;
            } else {
                this.readMiss++;
            }
        }
        
        // finally ask the back-end index
        return index.has(key);
    }
    
    public final synchronized Row.Entry get(final byte[] key) throws IOException {
        // first look into the miss cache
        if (readMissCache != null) {
            if (readMissCache.has(key)) {
                this.hasnotHit++;
                return null;
            } else {
                this.hasnotMiss++;
            }
        }

        Row.Entry entry = null;
        
        // then try the hit cache and the buffers
        if (readHitCache != null) {
            entry = readHitCache.get(key);
            if (entry != null) {
                this.readHit++;
                return entry;
            }
        }
        
        // finally ask the back-end index
        this.readMiss++;
        entry = index.get(key);
        // learn from result
        if (entry == null) {
            if (checkMissSpace()) try {
                final Row.Entry dummy = readMissCache.replace(readMissCache.row().newEntry(key));
                if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
            } catch (RowSpaceExceededException e) {
                clearCache();
            }
            return null;
        }
        
        if (checkHitSpace()) try {
            final Row.Entry dummy = readHitCache.replace(entry);
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (RowSpaceExceededException e) {
            clearCache();
        }
        return entry;
    }

    public Map<byte[], Row.Entry> get(Collection<byte[]> keys) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(this.row().objectOrder);
        Row.Entry entry;
        for (byte[] key: keys) {
            entry = get(key);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }
    
    public final synchronized boolean put(final Row.Entry row) throws IOException, RowSpaceExceededException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));
        
        final byte[] key = row.getPrimaryKeyBytes();
        checkHitSpace();
        
        // remove entry from miss- and hit-cache
        if (readMissCache != null) {
            if (readMissCache.delete(key)) {
                this.hasnotHit++;
            }
        }
        
        // write to the back-end
        boolean c;
        try {
            c = index.put(row);
        } catch (RowSpaceExceededException e1) {
            // flush all caches to get more memory
            clearCache();
            c = index.put(row); // try again
        }
        if (checkHitSpace()) try {
            final Row.Entry dummy = readHitCache.replace(row); // overwrite old entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (RowSpaceExceededException e) {
            clearCache();
        }
        return c;
    }
    
    public final synchronized Row.Entry replace(final Row.Entry row) throws IOException, RowSpaceExceededException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));
        
        final byte[] key = row.getPrimaryKeyBytes();
        checkHitSpace();
        
        // remove entry from miss- and hit-cache
        if (readMissCache != null) {
            if (readMissCache.delete(key)) {
                this.hasnotHit++;
                // the entry does not exist before
                try {
                    index.put(row);
                } catch (RowSpaceExceededException e1) {
                    // flush all caches to get more memory
                    clearCache();
                    index.put(row); // try again
                }
                // write to backend
                if (checkHitSpace()) try {
                    final Row.Entry dummy = readHitCache.replace(row); // learn that entry
                    if (dummy == null) this.writeUnique++; else this.writeDouble++;
                } catch (RowSpaceExceededException e) {
                    clearCache();
                }
                return null;
            }
        }
        
        Row.Entry entry = null;
        // write to the back-end
        try {
            entry = index.replace(row);
        } catch (RowSpaceExceededException e1) {
            // flush all caches to get more memory
            clearCache();
            index.replace(row); // try again
        }
        if (checkHitSpace()) try {
            final Row.Entry dummy = readHitCache.replace(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (RowSpaceExceededException e) {
            clearCache();
        }
        return entry;
    }

    public final synchronized void addUnique(final Row.Entry row) throws IOException, RowSpaceExceededException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));
        
        final byte[] key = row.getPrimaryKeyBytes();
        checkHitSpace();
        
        // remove entry from miss- and hit-cache
        if (readMissCache != null) {
            this.readMissCache.delete(key);
            this.hasnotDelete++;
            // the entry does not exist before
        }
        
        // the worst case: we must write to the back-end directly
        try {
            index.addUnique(row);
        } catch (RowSpaceExceededException e1) {
            // flush all caches to get more memory
            clearCache();
            index.addUnique(row); // try again
        }
        if (checkHitSpace()) try {
            final Row.Entry dummy = readHitCache.replace(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (RowSpaceExceededException e) {
            clearCache();
        }
    }

    public final synchronized void addUnique(final Row.Entry row, final Date entryDate) throws IOException, RowSpaceExceededException {
        if (entryDate == null) {
            addUnique(row);
            return;
        }
        
        assert (row != null);
        assert (row.columns() == row().columns());
        
        final byte[] key = row.getPrimaryKeyBytes();
        checkHitSpace();
        
        // remove entry from miss- and hit-cache
        if (readMissCache != null) {
            this.readMissCache.delete(key);
            this.hasnotDelete++;
        }

        // the worst case: we must write to the backend directly
        try {
            index.addUnique(row);
        } catch (RowSpaceExceededException e1) {
            // flush all caches to get more memory
            clearCache();
            index.addUnique(row); // try again
        }
        if (checkHitSpace()) try {
            final Row.Entry dummy = readHitCache.replace(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (RowSpaceExceededException e) {
            clearCache();
        }
    }
    
    public final synchronized void addUnique(final List<Row.Entry> rows) throws IOException, RowSpaceExceededException {
        final Iterator<Row.Entry> i = rows.iterator();
        Row.Entry r;
        while (i.hasNext()) {
            r = i.next();
            try {
                addUnique(r);
            } catch (RowSpaceExceededException e) {
                // flush all caches to get more memory
                clearCache();
                addUnique(r); // try again
            }
        }
    }

    public final synchronized List<RowCollection> removeDoubles() throws IOException, RowSpaceExceededException {
        return index.removeDoubles();
        // todo: remove reported entries from the cache!!!
    }
    
    public final synchronized boolean delete(final byte[] key) throws IOException {
        checkMissSpace();
        
        // add entry to miss-cache
        if (checkMissSpace()) try {
            // set the miss cache; if there was already an entry we know that the return value must be null
            final Row.Entry dummy = readMissCache.replace(readMissCache.row().newEntry(key));
            if (dummy == null) {
                this.hasnotUnique++;
            } else {
                this.hasnotHit++;
                this.hasnotDouble++;
            }
        } catch (RowSpaceExceededException e) {
            clearCache();
        }
        
        // remove entry from hit-cache
        if (readHitCache != null) {
            final Row.Entry entry = readHitCache.remove(key);
            if (entry == null) {
                this.readMiss++;
            } else {
                this.readHit++;
                this.cacheDelete++;
            }
        }
        
        return index.delete(key);
    }

    public final synchronized Row.Entry remove(final byte[] key) throws IOException {
        checkMissSpace();
        
        // add entry to miss-cache
        if (checkMissSpace()) try {
            // set the miss cache; if there was already an entry we know that the return value must be null
            final Row.Entry dummy = readMissCache.replace(readMissCache.row().newEntry(key));
            if (dummy == null) {
                this.hasnotUnique++;
            } else {
                this.hasnotHit++;
                this.hasnotDouble++;
            }
        } catch (RowSpaceExceededException e) {
            clearCache();
        }
        
        // remove entry from hit-cache
        if (readHitCache != null) {
            final Row.Entry entry = readHitCache.remove(key);
            if (entry == null) {
                this.readMiss++;
            } else {
                this.readHit++;
                this.cacheDelete++;
            }
        }
        
        return index.remove(key);
    }

    public final synchronized Row.Entry removeOne() throws IOException {
        
        checkMissSpace();
        
        final Row.Entry entry = index.removeOne();
        if (entry == null) return null;
        final byte[] key = entry.getPrimaryKeyBytes();
        if (checkMissSpace()) try {
            final Row.Entry dummy = readMissCache.replace(readMissCache.row().newEntry(key));
            if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
        } catch (RowSpaceExceededException e) {
            clearCache();
        }
        if (readHitCache != null) {
            if (readHitCache.delete(key)) this.cacheDelete++;
        }
        return entry;
    }

    public synchronized List<Row.Entry> top(int count) throws IOException {
        return this.index.top(count);
    }

    public final synchronized Row row() {
        return index.row();
    }

    public final synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return index.keys(up, firstKey);
    }

    public final synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return index.rows(up, firstKey);
    }

    public final Iterator<Entry> iterator() {
        try {
            return rows();
        } catch (IOException e) {
            return null;
        }
    }
    
    public final synchronized CloneableIterator<Row.Entry> rows() throws IOException {
        return index.rows();
    }

    public final int size() {
        return index.size();
    }

    public final boolean isEmpty() {
        return index.isEmpty();
    }
    
    public final String filename() {
        return index.filename();
    }

    public final void clear() throws IOException {
        this.index.clear();
        init();
    }

    public final void deleteOnExit() {
        this.index.deleteOnExit();
    }

}