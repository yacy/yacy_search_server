// kelondroCache.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 26.10.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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


package de.anomic.kelondro;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroRow.Entry;

public class kelondroCache implements kelondroIndex {

    // this is a combined read cache and write buffer
    // we maintain four tables:
    // - a read-cache
    // - a miss-cache
    // - a write buffer for rows that are not contained in the target index
    // - a write buffer for rows that are known to be contained in target
    // furthermore, if we access a kelondroFlexTable, we can use the ram index of the underlying index

    // static object tracker; stores information about object cache usage
    private static final TreeMap objectTracker = new TreeMap();
    private static long memStopGrow = 4000000; // a limit for the node cache to stop growing if less than this memory amount is available
    private static long memStartShrink = 2000000; // a limit for the node cache to start with shrinking if less than this memory amount is available
    
    // class objects
    private kelondroRowSet readHitCache;
    private kelondroRowSet readMissCache;
    private kelondroRowSet writeBufferUnique;  // entries of that buffer are not contained in index
    private kelondroRowSet writeBufferDoubles; // entries of that buffer shall overwrite entries in index
    private kelondroIndex  index;
    private kelondroRow    keyrow;
    private int            readHit, readMiss, writeUnique, writeDouble, cacheDelete, cacheFlush;
    private int            hasnotHit, hasnotMiss, hasnotUnique, hasnotDouble, hasnotDelete, hasnotFlush;
    
    public kelondroCache(kelondroIndex backupIndex, boolean read, boolean write) throws IOException {
        assert write == false;
        this.index = backupIndex;
        this.keyrow = new kelondroRow(new kelondroColumn[]{index.row().column(index.row().primaryKey)}, index.row().objectOrder, index.row().primaryKey);
        this.readHitCache = (read) ? new kelondroRowSet(index.row(), 0) : null;
        this.readMissCache = (read) ? new kelondroRowSet(this.keyrow, 0) : null;
        this.writeBufferUnique = (write) ? new kelondroRowSet(index.row(), 0) : null;
        this.writeBufferDoubles = (write) ? new kelondroRowSet(index.row(), 0) : null;
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
        this.hasnotFlush = 0;
        objectTracker.put(backupIndex.filename(), this);
    }
    
    public final int cacheObjectChunkSize() {
        try {
            return index.row().objectsize();
        } catch (IOException e) {
            return 0;
        }
    }
    
    public int writeBufferSize() {
        return
          ((writeBufferUnique  == null) ? 0 : writeBufferUnique.size()) + 
          ((writeBufferDoubles == null) ? 0 : writeBufferDoubles.size());
    }
    
    public kelondroProfile profile() {
        return index.profile(); // TODO: implement own profile and merge with global
    }

    public static void setCacheGrowStati(long memStopGrowNew, long memStartShrinkNew) {
        memStopGrow = memStopGrowNew;
        memStartShrink =  memStartShrinkNew;
    }
    
    public static long getMemStopGrow() {
        return memStopGrow ;
    }
    
    public static long getMemStartShrink() {
        return memStartShrink ;
    }
    
    public static final Iterator filenames() {
        // iterates string objects; all file names from record tracker
        return objectTracker.keySet().iterator();
    }

    public static final Map memoryStats(String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record oobjects,
        // i.e. for cache memory allocation
        kelondroCache theObjectsCache = (kelondroCache) objectTracker.get(filename);
        return theObjectsCache.memoryStats();
    }
    
    private final Map memoryStats() {
        // returns statistical data about this object
        HashMap map = new HashMap();
        map.put("objectHitChunkSize", (readHitCache == null) ? "0" : Integer.toString(readHitCache.rowdef.objectsize));
        map.put("objectHitCacheCount", (readHitCache == null) ? "0" : Integer.toString(readHitCache.size()));
        map.put("objectHitMem", (readHitCache == null) ? "0" : Integer.toString((int) (readHitCache.rowdef.objectsize * readHitCache.size() * kelondroRowCollection.growfactor)));
        map.put("objectHitCacheReadHit", Integer.toString(readHit));
        map.put("objectHitCacheReadMiss", Integer.toString(readMiss));
        map.put("objectHitCacheWriteUnique", Integer.toString(writeUnique));
        map.put("objectHitCacheWriteDouble", Integer.toString(writeDouble));
        map.put("objectHitCacheDeletes", Integer.toString(cacheDelete));
        map.put("objectHitCacheFlushes", Integer.toString(cacheFlush));
        
        map.put("objectMissChunkSize", (readMissCache == null) ? "0" : Integer.toString(readMissCache.rowdef.objectsize));
        map.put("objectMissCacheCount", (readMissCache == null) ? "0" : Integer.toString(readMissCache.size()));
        map.put("objectMissMem", (readMissCache == null) ? "0" : Integer.toString((int) (readMissCache.rowdef.objectsize * readMissCache.size() * kelondroRowCollection.growfactor)));
        map.put("objectMissCacheReadHit", Integer.toString(hasnotHit));
        map.put("objectMissCacheReadMiss", Integer.toString(hasnotMiss));
        map.put("objectMissCacheWriteUnique", Integer.toString(hasnotUnique));
        map.put("objectMissCacheWriteDouble", Integer.toString(hasnotDouble));
        map.put("objectMissCacheDeletes", Integer.toString(hasnotDelete));
        map.put("objectMissCacheFlushes", Integer.toString(hasnotFlush));
        
        // future feature .. map.put("objectElderTimeRead", index.profile().)
        return map;
    }
    
    private int cacheGrowStatus() {
        return kelondroRecords.cacheGrowStatus(memStopGrow, memStartShrink);
    }
    
    private void flushUnique() throws IOException {
        if (writeBufferUnique == null) return;
        synchronized (writeBufferUnique) {
            Iterator i = writeBufferUnique.rows();
            while (i.hasNext()) {
                this.index.addUnique((kelondroRow.Entry) i.next());
                this.cacheFlush++;
            }
            writeBufferUnique.clear();
            writeBufferUnique.trim();
        }
    }

    private void flushUnique(int maxcount) throws IOException {
        if (writeBufferUnique == null) return;
        if (maxcount == 0) return;
        synchronized (writeBufferUnique) {
            kelondroRowCollection delete = new kelondroRowCollection(this.keyrow, maxcount);
            Iterator i = writeBufferUnique.rows();
            kelondroRow.Entry row;
            while ((i.hasNext()) && (maxcount-- > 0)) {
                row = (kelondroRow.Entry) i.next();
                delete.add(row.getColBytes(index.row().primaryKey));
                this.index.addUnique(row);
                this.cacheFlush++;
            }
            i = delete.rows();
            while (i.hasNext()) writeBufferUnique.remove(((kelondroRow.Entry) i.next()).getColBytes(0));
            delete = null;
            writeBufferUnique.trim();
        }
    }

    private void flushDoubles() throws IOException {
        if (writeBufferDoubles == null) return;
        synchronized (writeBufferDoubles) {
            Iterator i = writeBufferDoubles.rows();
            while (i.hasNext()) {
                this.index.put((kelondroRow.Entry) i.next());
                this.cacheFlush++;
            }
            writeBufferDoubles.clear();
            writeBufferDoubles.trim();
        }
    }

    private void flushDoubles(int maxcount) throws IOException {
        if (writeBufferDoubles == null) return;
        if (maxcount == 0) return;
        synchronized (writeBufferDoubles) {
            kelondroRowCollection delete = new kelondroRowCollection(this.keyrow, maxcount);
            Iterator i = writeBufferDoubles.rows();
            kelondroRow.Entry row;
            while ((i.hasNext()) && (maxcount-- > 0)) {
                row = (kelondroRow.Entry) i.next();
                delete.add(row.getColBytes(index.row().primaryKey));
                this.index.addUnique(row);
                this.cacheFlush++;
            }
            i = delete.rows();
            while (i.hasNext()) writeBufferDoubles.remove(((kelondroRow.Entry) i.next()).getColBytes(0));
            delete = null;
            writeBufferDoubles.trim();
        }
    }

    public void flushSome() throws IOException {
        if (writeBufferUnique != null) flushUnique(writeBufferUnique.size() / 10);
        if (writeBufferDoubles != null) flushDoubles(writeBufferDoubles.size() / 10);
    }

    private int sumRecords() {
        return
          ((readHitCache       == null) ? 0 : readHitCache.size()) + 
          ((writeBufferUnique  == null) ? 0 : writeBufferUnique.size()) + 
          ((writeBufferDoubles == null) ? 0 : writeBufferDoubles.size());
    }
    
    private void checkMissSpace() {
        if ((readMissCache != null) && (cacheGrowStatus() < 1)
           ) {readMissCache.clear(); readMissCache.trim();}
    }
    
    private void checkHitSpace() throws IOException {
        int s = sumRecords();
        if (cacheGrowStatus() < 2) {flushDoubles(s / 4); s = sumRecords();}
        if (cacheGrowStatus() < 2) {flushUnique(s / 4); s = sumRecords();}
        if ((cacheGrowStatus() < 2) && (readHitCache != null)) {
            readHitCache.clear();
            readHitCache.trim();
        }
        if (cacheGrowStatus() < 1) {
            flushUnique();
            flushDoubles();
            if (readHitCache != null) {
                readHitCache.clear();
                readHitCache.trim();
            }
        }
    }
    
    public synchronized void close() throws IOException {
        flushUnique();
        flushDoubles();
        index.close();
        readHitCache = null;
        readMissCache = null;
        writeBufferUnique = null;
        writeBufferDoubles = null;
    }

    public boolean has(byte[] key) throws IOException {
        return (get(key) != null);
    }
    
    public synchronized Entry get(byte[] key) throws IOException {
        // first look into the miss cache
        if (readMissCache != null) {
            if (readMissCache.get(key) != null) {
                this.hasnotHit++;
                return null;
            } else {
                this.hasnotMiss++;
            }
        }

        Entry entry = null;
        
        // then try the hit cache and the buffers
        if (readHitCache != null) {
            entry = readHitCache.get(key);
            if (entry != null) {
                this.readHit++;
                return entry;
            }
        }
        if (writeBufferUnique != null) {
            entry = writeBufferUnique.get(key);
            if (entry != null) {
                this.readHit++;
                return entry;
            }
        }
        if (writeBufferDoubles != null) {
            entry = writeBufferDoubles.get(key);
            if (entry != null) {
                this.readHit++;
                return entry;
            }
        }
        
        // finally ask the backend index
        this.readMiss++;
        entry = index.get(key);
        // learn from result
        if (entry == null) {
            checkMissSpace();
            if (readMissCache != null) {
                kelondroRow.Entry dummy = readMissCache.put(readMissCache.row().newEntry(key));
                if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
            }
            return null;
        } else {
            checkHitSpace();
            if (readHitCache != null) {
                kelondroRow.Entry dummy = readHitCache.put(entry);
                if (dummy == null) this.writeUnique++; else this.writeDouble++;
            }
            return entry;
        }
    }

    public synchronized void putMultiple(List rows, Date entryDate) throws IOException {
        Iterator i = rows.iterator();
        while (i.hasNext()) put ((Entry) i.next(), entryDate);
    }
    
    public synchronized Entry put(Entry row) throws IOException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));
        
        byte[] key = row.getColBytes(index.row().primaryKey);
        checkHitSpace();
        
        // remove entry from miss- and hit-cache
        if (readMissCache != null) {
            if (readMissCache.remove(key) != null) {
                this.hasnotHit++;
                // the entry does not exist before                
                if (writeBufferUnique != null) {
                    // since we know that the entry does not exist, we know that new
                    // entry belongs to the unique buffer
                    writeBufferUnique.put(row);
                    return null;
                }
                assert (writeBufferDoubles == null);
                index.put(row); // write to backend
                if (readHitCache != null) {
                    kelondroRow.Entry dummy = readHitCache.put(row); // learn that entry
                    if (dummy == null) this.writeUnique++; else this.writeDouble++;
                }
                return null;
            }
        }
        
        Entry entry;
        
        if (readHitCache != null) {
            entry = readHitCache.get(key);
            if (entry != null) {
                // since we know that the entry was in the read cache, it cannot be in any write cache
                if (writeBufferDoubles != null) {
                    // because the entry exists, it must be written in the doubles buffer
                    readHitCache.remove(key);
                    this.cacheDelete++;
                    writeBufferDoubles.put(row);
                    return entry;
                } else {
                    // write directly to backend index
                    index.put(row);
                    // learn from situation
                    kelondroRow.Entry dummy = readHitCache.put(row); // overwrite old entry
                    if (dummy == null) this.writeUnique++; else this.writeDouble++;
                    return entry;
                }
            }
        }

        // we still don't know if the key exists. Look into the buffers
        if (writeBufferUnique != null) {
            entry = writeBufferUnique.get(key);
            if (entry != null) {
                writeBufferUnique.put(row);
                return entry;
            }
        }
        if (writeBufferDoubles != null) {
            entry = writeBufferDoubles.get(key);
            if (entry != null) {
                writeBufferDoubles.put(row);
                return entry;
            }
        }
        
        // finally, we still don't know if this is a double-entry or unique-entry
        // there is a chance to get that information 'cheap':
        // look into the node ram cache of the back-end index.
        // that does only work, if the node cache is complete
        // that is the case for kelondroFlexTables with ram index
        if ((writeBufferUnique != null) &&
            (index instanceof kelondroFlexTable) && 
            (((kelondroFlexTable) index).hasRAMIndex()) &&
            (!(((kelondroFlexTable) index).has(key)))) {
            // this an unique entry
            writeBufferUnique.put(row);
            return null; // since that was unique, there was no entry before
        }

        // the worst case: we must write to the back-end directly
        entry = index.put(row);
        if (readHitCache != null) {
            kelondroRow.Entry dummy = readHitCache.put(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        }
        return entry;
    }

    public synchronized Entry put(Entry row, Date entryDate) throws IOException {
        // a put with a date is bad for the cache: the date cannot be handled
        // The write buffer does not work here, because it does not store dates.
        
        if (entryDate == null) return put(row);
        
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));
        assert (writeBufferUnique == null);
        assert (writeBufferDoubles == null);
        
        byte[] key = row.getColBytes(index.row().primaryKey);
        checkHitSpace();
        
        // remove entry from miss- and hit-cache
        if (readMissCache != null) {
            this.readMissCache.remove(key);
            this.hasnotDelete++;
        }

        // the worst case: we must write to the backend directly
        Entry entry = index.put(row);
        if (readHitCache != null) {
            kelondroRow.Entry dummy = readHitCache.put(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        }
        return entry;
    }

    public synchronized void addUnique(Entry row) throws IOException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));
        
        byte[] key = row.getColBytes(index.row().primaryKey);
        checkHitSpace();
        
        // remove entry from miss- and hit-cache
        if (readMissCache != null) {
            this.readMissCache.remove(key);
            this.hasnotDelete++;
            // the entry does not exist before
            if (writeBufferUnique != null) {
                // since we know that the entry does not exist, we know that new
                // entry belongs to the unique buffer
                writeBufferUnique.put(row);
                return;
            }
            assert (writeBufferDoubles == null);
            index.addUnique(row); // write to backend
            if (readHitCache != null) {
                kelondroRow.Entry dummy = readHitCache.put(row); // learn that entry
                if (dummy == null) this.writeUnique++; else this.writeDouble++;
            }
            return;
        }
        
        if ((writeBufferUnique != null) &&
            (index instanceof kelondroFlexTable) && 
            (((kelondroFlexTable) index).hasRAMIndex()) &&
            (!(((kelondroFlexTable) index).has(key)))) {
            // this an unique entry
            writeBufferUnique.addUnique(row);
            return;
        }

        // the worst case: we must write to the back-end directly
        index.addUnique(row);
        if (readHitCache != null) {
            kelondroRow.Entry dummy = readHitCache.put(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        }
    }

    public synchronized void addUnique(Entry row, Date entryDate) throws IOException {
        if (entryDate == null) {
            addUnique(row);
            return;
        }
        
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));
        assert (writeBufferUnique == null);
        assert (writeBufferDoubles == null);
        
        byte[] key = row.getColBytes(index.row().primaryKey);
        checkHitSpace();
        
        // remove entry from miss- and hit-cache
        if (readMissCache != null) {
            this.readMissCache.remove(key);
            this.hasnotDelete++;
        }

        // the worst case: we must write to the backend directly
        index.addUnique(row);
        if (readHitCache != null) {
            kelondroRow.Entry dummy = readHitCache.put(row); // learn that entry
            if (dummy == null) this.writeUnique++; else this.writeDouble++;
        }
    }
    
    public synchronized void addUniqueMultiple(List rows, Date entryDate) throws IOException {
        Iterator i = rows.iterator();
        while (i.hasNext()) addUnique((Entry) i.next(), entryDate);
    }

    public synchronized Entry remove(byte[] key) throws IOException {
        
        checkMissSpace();
        
        // add entry to miss-cache
        if (readMissCache != null) {
            // set the miss cache; if there was already an entry we know that the return value must be null
            kelondroRow.Entry dummy = readMissCache.put(readMissCache.row().newEntry(key));
            if (dummy == null) {
                this.hasnotUnique++;
            } else {
                this.hasnotHit++;
                this.hasnotDouble++;
                return null;
            }
        }
        
        // remove entry from hit-cache
        if (readHitCache != null) {
            Entry entry = readHitCache.remove(key);
            if (entry == null) {
                this.readMiss++;
            } else {
                this.readHit++;
                this.cacheDelete++;
                index.remove(key);
                return entry;
            }
        }
        
        // if the key already exists in one buffer, remove that buffer
        if (writeBufferUnique != null) {
            Entry entry = writeBufferUnique.remove(key);
            if (entry != null) return entry;
        }
        if (writeBufferDoubles != null) {
            Entry entry = writeBufferDoubles.remove(key);
            if (entry != null) {
                index.remove(key);
                return entry;
            }
        }
        
        return index.remove(key);
    }

    public synchronized Entry removeOne() throws IOException {
        
        checkMissSpace();
        
        if ((writeBufferUnique != null) && (writeBufferUnique.size() > 0)) {
            Entry entry = writeBufferUnique.removeOne();
            if (readMissCache != null) {
                kelondroRow.Entry dummy = readMissCache.put(readMissCache.row().newEntry(entry.getColBytes(index.row().primaryKey)));
                if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
            }
            return entry;
        }

        if ((writeBufferDoubles != null) && (writeBufferDoubles.size() > 0)) {
            Entry entry = writeBufferDoubles.removeOne();
            byte[] key = entry.getColBytes(index.row().primaryKey);
            if (readMissCache != null) {
                kelondroRow.Entry dummy = readMissCache.put(readMissCache.row().newEntry(key));
                if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
            }
            index.remove(key);
            return entry;
        }
        
        Entry entry = index.removeOne();
        if (entry == null) return null;
        byte[] key = entry.getColBytes(index.row().primaryKey);
        if (readMissCache != null) {
            kelondroRow.Entry dummy = readMissCache.put(readMissCache.row().newEntry(key));
            if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
        }
        if (readHitCache != null) {
            kelondroRow.Entry dummy = readHitCache.remove(key);
            if (dummy != null) this.cacheDelete++;
        }
        return entry;
    }

    public synchronized kelondroRow row() throws IOException {
        return index.row();
    }

    public synchronized Iterator rows(boolean up, boolean rotating, byte[] firstKey) throws IOException {
        flushUnique();
        return index.rows(up, rotating, firstKey);
    }

    public int size() throws IOException {
        return index.size() + ((writeBufferUnique == null) ? 0 : writeBufferUnique.size());
    }

    public String filename() {
        return index.filename();
    }

}
