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

import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Row.Entry;
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
        final Row row = this.index.row();
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

    @Override
    public long mem() {
        return this.index.mem() + this.readHitCache.mem() + this.readMissCache.mem();
    }

    @Override
    public void optimize() {
        this.index.optimize();
        this.readHitCache.optimize();
        this.readMissCache.optimize();
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

    @Override
    public byte[] smallestKey() {
        return this.index.smallestKey();
    }

    @Override
    public byte[] largestKey() {
        return this.index.largestKey();
    }

    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return objectTracker.keySet().iterator();
    }

    public enum StatKeys {
        objectHitChunkSize,
        objectHitCacheCount,
        objectHitMem,
        objectHitCacheReadHit,
        objectHitCacheReadMiss,
        objectHitCacheWriteUnique,
        objectHitCacheWriteDouble,
        objectHitCacheDeletes,
        objectHitCacheFlushes,
        objectMissChunkSize,
        objectMissCacheCount,
        objectMissMem,
        objectMissCacheReadHit,
        objectMissCacheReadMiss,
        objectMissCacheWriteUnique,
        objectMissCacheWriteDouble,
        objectMissCacheDeletes,
        objectMissCacheFlushes;
    }

    public static final Map<StatKeys, String> memoryStats(final String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record oobjects,
        // i.e. for cache memory allocation
        final Cache theObjectsCache = objectTracker.get(filename);
        return theObjectsCache.memoryStats();
    }

    private final Map<StatKeys, String> memoryStats() {
        // returns statistical data about this object
        final HashMap<StatKeys, String> map = new HashMap<StatKeys, String>(20);
        map.put(StatKeys.objectHitChunkSize, (this.readHitCache == null) ? "0" : Integer.toString(this.readHitCache.rowdef.objectsize));
        map.put(StatKeys.objectHitCacheCount, (this.readHitCache == null) ? "0" : Integer.toString(this.readHitCache.size()));
        map.put(StatKeys.objectHitMem, (this.readHitCache == null) ? "0" : Long.toString(this.readHitCache.rowdef.objectsize * this.readHitCache.size()));
        map.put(StatKeys.objectHitCacheReadHit, Integer.toString(this.readHit));
        map.put(StatKeys.objectHitCacheReadMiss, Integer.toString(this.readMiss));
        map.put(StatKeys.objectHitCacheWriteUnique, Integer.toString(this.writeUnique));
        map.put(StatKeys.objectHitCacheWriteDouble, Integer.toString(this.writeDouble));
        map.put(StatKeys.objectHitCacheDeletes, Integer.toString(this.cacheDelete));
        map.put(StatKeys.objectHitCacheFlushes, Integer.toString(this.cacheFlush));

        map.put(StatKeys.objectMissChunkSize, (this.readMissCache == null) ? "0" : Integer.toString(this.readMissCache.rowdef.objectsize));
        map.put(StatKeys.objectMissCacheCount, (this.readMissCache == null) ? "0" : Integer.toString(this.readMissCache.size()));
        map.put(StatKeys.objectMissMem, (this.readMissCache == null) ? "0" : Long.toString(this.readMissCache.rowdef.objectsize * this.readMissCache.size()));
        map.put(StatKeys.objectMissCacheReadHit, Integer.toString(this.hasnotHit));
        map.put(StatKeys.objectMissCacheReadMiss, Integer.toString(this.hasnotMiss));
        map.put(StatKeys.objectMissCacheWriteUnique, Integer.toString(this.hasnotUnique));
        map.put(StatKeys.objectMissCacheWriteDouble, Integer.toString(this.hasnotDouble));
        map.put(StatKeys.objectMissCacheDeletes, Integer.toString(this.hasnotDelete));
        map.put(StatKeys.objectMissCacheFlushes, "0"); // a miss cache flush can only happen if we have a deletion cache (which we dont have)

        // future feature .. map.put("objectElderTimeRead", index.profile().)
        return map;
    }


    /**
     * checks for space in the miss cache
     * @return true if it is allowed to write into this cache
     */
    private final boolean checkMissSpace() {
        // returns true if it is allowed to write into this cache
        if (this.readMissCache == null) return false;

        // check given limitation
        if (this.missLimit > 0 && this.readMissCache.size() >= this.missLimit) return false;

        // check memory
        long available = MemoryControl.available();
        if (MemoryControl.shortStatus() || available - 2 * 1024 * 1024 < this.readMissCache.memoryNeededForGrow()) {
            this.readMissCache.clear();
        }
        available = MemoryControl.available();
        return (available - 2 * 1024 * 1024 > this.readMissCache.memoryNeededForGrow());
    }

    /**
     * checks for space in the hit cache
     * @return true if it is allowed to write into this cache
     */
    private final boolean checkHitSpace() {
        // returns true if it is allowed to write into this cache
        if (this.readHitCache == null) return false;

        // check given limitation
        if (this.hitLimit > 0 && this.readHitCache.size() >= this.hitLimit) return false;

        // check memory
        long available = MemoryControl.available();
        if (MemoryControl.shortStatus() || available - 2 * 1024 * 1024 < this.readHitCache.memoryNeededForGrow()) {
            this.readHitCache.clear();
        }
        available = MemoryControl.available();
        return (available - 2 * 1024 * 1024 > this.readHitCache.memoryNeededForGrow());
    }

    public final synchronized void clearCache() {
        if (this.readMissCache != null) this.readMissCache.clear();
        if (this.readHitCache != null) this.readHitCache.clear();
    }

    @Override
    public final synchronized void close() {
        this.index.close();
        this.readHitCache = null;
        this.readMissCache = null;
    }

    @Override
    public final synchronized boolean has(final byte[] key) {
        // first look into the miss cache
        if (this.readMissCache != null) {
            if (this.readMissCache.has(key)) {
                this.hasnotHit++;
                return false;
            }
            this.hasnotMiss++;
        }

        // then try the hit cache and the buffers
        if (this.readHitCache != null) {
            if (this.readHitCache.has(key)) {
                this.readHit++;
                return true;
            }
            this.readMiss++;
        }

        // finally ask the back-end index
        return this.index.has(key);
    }

    @Override
    public final synchronized Row.Entry get(final byte[] key, final boolean cachecopy) throws IOException {
        // first look into the miss cache
        if (this.readMissCache != null) {
            if (this.readMissCache.has(key)) {
                this.hasnotHit++;
                return null;
            }
            this.hasnotMiss++;
        }

        Row.Entry entry = null;

        // then try the hit cache and the buffers
        if (this.readHitCache != null) {
            entry = this.readHitCache.get(key, cachecopy);
            if (entry != null) {
                this.readHit++;
                return entry;
            }
        }

        // finally ask the back-end index
        this.readMiss++;
        entry = this.index.get(key, cachecopy);
        // learn from result
        if (entry == null) {
            if (checkMissSpace()) try {
                final Row.Entry dummy = this.readMissCache.replace(this.readMissCache.row().newEntry(key));
                if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
            } catch (final SpaceExceededException e) {
                clearCache();
            }
            return null;
        }

        if (checkHitSpace()) try {
            final Row.Entry dummy = this.readHitCache.replace(entry);
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (final SpaceExceededException e) {
            clearCache();
        }
        return entry;
    }

    @Override
    public Map<byte[], Row.Entry> get(final Collection<byte[]> keys, final boolean forcecopy) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(row().objectOrder);
        Row.Entry entry;
        for (final byte[] key: keys) {
            entry = get(key, forcecopy);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }

    @Override
    public final synchronized boolean put(final Row.Entry row) throws IOException, SpaceExceededException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));

        final byte[] key = row.getPrimaryKeyBytes();
        checkHitSpace();

        // remove entry from miss- and hit-cache
        if (this.readMissCache != null) {
            if (this.readMissCache.delete(key)) {
                this.hasnotHit++;
            }
        }

        // write to the back-end
        boolean c;
        try {
            c = this.index.put(row);
        } catch (final SpaceExceededException e1) {
            // flush all caches to get more memory
            clearCache();
            c = this.index.put(row); // try again
        }
        if (checkHitSpace()) try {
            final Row.Entry dummy = this.readHitCache.replace(row); // overwrite old entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (final SpaceExceededException e) {
            clearCache();
        }
        return c;
    }

    @Override
    public final synchronized Row.Entry replace(final Row.Entry row) throws IOException, SpaceExceededException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));

        final byte[] key = row.getPrimaryKeyBytes();
        checkHitSpace();

        // remove entry from miss- and hit-cache
        if (this.readMissCache != null) {
            if (this.readMissCache.delete(key)) {
                this.hasnotHit++;
                // the entry does not exist before
                try {
                    this.index.put(row);
                } catch (final SpaceExceededException e1) {
                    // flush all caches to get more memory
                    clearCache();
                    this.index.put(row); // try again
                }
                // write to backend
                if (checkHitSpace()) try {
                    final Row.Entry dummy = this.readHitCache.replace(row); // learn that entry
                    if (dummy == null) this.writeUnique++; else this.writeDouble++;
                } catch (final SpaceExceededException e) {
                    clearCache();
                }
                return null;
            }
        }

        Row.Entry entry = null;
        // write to the back-end
        try {
            entry = this.index.replace(row);
        } catch (final SpaceExceededException e1) {
            // flush all caches to get more memory
            clearCache();
            this.index.replace(row); // try again
        }
        if (checkHitSpace()) try {
            final Row.Entry dummy = this.readHitCache.replace(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (final SpaceExceededException e) {
            clearCache();
        }
        return entry;
    }

    @Override
    public final synchronized void addUnique(final Row.Entry row) throws IOException, SpaceExceededException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));

        final byte[] key = row.getPrimaryKeyBytes();
        checkHitSpace();

        // remove entry from miss- and hit-cache
        if (this.readMissCache != null) {
            this.readMissCache.delete(key);
            this.hasnotDelete++;
            // the entry does not exist before
        }

        // the worst case: we must write to the back-end directly
        try {
            this.index.addUnique(row);
        } catch (final SpaceExceededException e1) {
            // flush all caches to get more memory
            clearCache();
            this.index.addUnique(row); // try again
        }
        if (checkHitSpace()) try {
            final Row.Entry dummy = this.readHitCache.replace(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (final SpaceExceededException e) {
            clearCache();
        }
    }

    public final synchronized void addUnique(final Row.Entry row, final Date entryDate) throws IOException, SpaceExceededException {
        if (entryDate == null) {
            addUnique(row);
            return;
        }

        assert (row != null);
        assert (row.columns() == row().columns());

        final byte[] key = row.getPrimaryKeyBytes();
        checkHitSpace();

        // remove entry from miss- and hit-cache
        if (this.readMissCache != null) {
            this.readMissCache.delete(key);
            this.hasnotDelete++;
        }

        // the worst case: we must write to the backend directly
        try {
            this.index.addUnique(row);
        } catch (final SpaceExceededException e1) {
            // flush all caches to get more memory
            clearCache();
            this.index.addUnique(row); // try again
        }
        if (checkHitSpace()) try {
            final Row.Entry dummy = this.readHitCache.replace(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        } catch (final SpaceExceededException e) {
            clearCache();
        }
    }

    public final synchronized void addUnique(final List<Row.Entry> rows) throws IOException, SpaceExceededException {
        final Iterator<Row.Entry> i = rows.iterator();
        Row.Entry r;
        while (i.hasNext()) {
            r = i.next();
            try {
                addUnique(r);
            } catch (final SpaceExceededException e) {
                // flush all caches to get more memory
                clearCache();
                addUnique(r); // try again
            }
        }
    }

    @Override
    public final synchronized List<RowCollection> removeDoubles() throws IOException, SpaceExceededException {
        return this.index.removeDoubles();
        // todo: remove reported entries from the cache!!!
    }

    @Override
    public final synchronized boolean delete(final byte[] key) throws IOException {
        checkMissSpace();

        // add entry to miss-cache
        if (checkMissSpace()) try {
            // set the miss cache; if there was already an entry we know that the return value must be null
            final Row.Entry dummy = this.readMissCache.replace(this.readMissCache.row().newEntry(key));
            if (dummy == null) {
                this.hasnotUnique++;
            } else {
                this.hasnotHit++;
                this.hasnotDouble++;
            }
        } catch (final SpaceExceededException e) {
            clearCache();
        }

        // remove entry from hit-cache
        if (this.readHitCache != null) {
            final Row.Entry entry = this.readHitCache.remove(key);
            if (entry == null) {
                this.readMiss++;
            } else {
                this.readHit++;
                this.cacheDelete++;
            }
        }

        return this.index.delete(key);
    }

    @Override
    public final synchronized Row.Entry remove(final byte[] key) throws IOException {
        checkMissSpace();

        // add entry to miss-cache
        if (checkMissSpace()) try {
            // set the miss cache; if there was already an entry we know that the return value must be null
            final Row.Entry dummy = this.readMissCache.replace(this.readMissCache.row().newEntry(key));
            if (dummy == null) {
                this.hasnotUnique++;
            } else {
                this.hasnotHit++;
                this.hasnotDouble++;
            }
        } catch (final SpaceExceededException e) {
            clearCache();
        }

        // remove entry from hit-cache
        if (this.readHitCache != null) {
            final Row.Entry entry = this.readHitCache.remove(key);
            if (entry == null) {
                this.readMiss++;
            } else {
                this.readHit++;
                this.cacheDelete++;
            }
        }

        return this.index.remove(key);
    }

    @Override
    public final synchronized Row.Entry removeOne() throws IOException {

        checkMissSpace();

        final Row.Entry entry = this.index.removeOne();
        if (entry == null) return null;
        final byte[] key = entry.getPrimaryKeyBytes();
        if (checkMissSpace()) try {
            final Row.Entry dummy = this.readMissCache.replace(this.readMissCache.row().newEntry(key));
            if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
        } catch (final SpaceExceededException e) {
            clearCache();
        }
        if (this.readHitCache != null) {
            if (this.readHitCache.delete(key)) this.cacheDelete++;
        }
        return entry;
    }

    @Override
    public synchronized List<Row.Entry> top(final int count) throws IOException {
        return this.index.top(count);
    }

    @Override
    public synchronized List<Row.Entry> random(final int count) throws IOException {
        return this.index.random(count);
    }

    @Override
    public final synchronized Row row() {
        return this.index.row();
    }

    @Override
    public final synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return this.index.keys(up, firstKey);
    }

    @Override
    public final synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return this.index.rows(up, firstKey);
    }

    @Override
    public final Iterator<Entry> iterator() {
        try {
            return rows();
        } catch (final IOException e) {
            return null;
        }
    }

    @Override
    public final synchronized CloneableIterator<Row.Entry> rows() throws IOException {
        return this.index.rows();
    }

    @Override
    public final int size() {
        return this.index.size();
    }

    @Override
    public final boolean isEmpty() {
        return this.index.isEmpty();
    }

    @Override
    public final String filename() {
        return this.index.filename();
    }

    @Override
    public final void clear() throws IOException {
        this.index.clear();
        init();
    }

    @Override
    public final void deleteOnExit() {
        this.index.deleteOnExit();
    }

}