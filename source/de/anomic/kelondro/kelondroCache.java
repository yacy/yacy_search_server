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
import java.util.Iterator;

import de.anomic.kelondro.kelondroRow.Entry;
import de.anomic.server.serverMemory;

public class kelondroCache implements kelondroIndex {

    // this is a combined read cache and write buffer
    // we maintain four tables:
    // - a read-cache
    // - a miss-cache
    // - a write buffer for rows that are not contained in the target index
    // - a write buffer for rows that are known to be contained in target
    // furthermore, if we access a kelondroFlexTable, we can use the ram index of the underlying index

    private kelondroRowSet readHitCache;
    private kelondroRowSet readMissCache;
    private kelondroRowSet writeBufferUnique;  // entries of that buffer are not contained in index
    private kelondroRowSet writeBufferDoubles; // entries of that buffer shall overwrite entries in index
    private kelondroIndex  index;
    private kelondroRow    keyrow;
    private int            maxrecords, maxmiss;
    private int            readHit, readMiss, writeUnique, writeDouble, cacheDelete, cacheFlush;
    private int            hasnotHit, hasnotMiss, hasnotUnique, hasnotDouble, hasnotDelete, hasnotFlush;
    
    public kelondroCache(kelondroIndex backupIndex, long buffersize, boolean read, boolean write) throws IOException {
        assert write == false;
        this.index = backupIndex;
        this.keyrow = new kelondroRow(new kelondroColumn[]{index.row().column(index.primarykey())});
        this.readHitCache = (read) ? new kelondroRowSet(index.row()) : null;
        this.readMissCache = (read) ? new kelondroRowSet(this.keyrow) : null;
        this.writeBufferUnique = (write) ? new kelondroRowSet(index.row()) : null;
        this.writeBufferDoubles = (write) ? new kelondroRowSet(index.row()) : null;
        this.maxmiss = (read) ? (int) (buffersize / 10 / index.row().column(index.primarykey()).cellwidth()) : 0;
        this.maxrecords = (int) ((buffersize - maxmiss * index.row().column(index.primarykey()).cellwidth()) / index.row().objectsize());
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
    }
    
    
    public final int cacheNodeChunkSize() {
        // returns the size that the node cache uses for a single entry
        return index.cacheNodeChunkSize();
    }
    
    public final int[] cacheNodeStatus() {
        // a collection of different node cache status values
        return index.cacheNodeStatus();
    }
    
    public final int cacheObjectChunkSize() {
        try {
            return index.row().objectsize();
        } catch (IOException e) {
            return 0;
        }
    }
    
    public long[] cacheObjectStatus() {
        return new long[]{
                (long) maxrecords,
                (long) maxmiss,
                (long) ((readHitCache == null) ? 0 : readHitCache.size()),
                (long) ((readMissCache == null) ? 0 : readMissCache.size()),
                0, // this.maxAge
                0, // minAge()
                0, // maxAge()
                (long) readHit,
                (long) readMiss,
                (long) writeUnique,
                (long) writeDouble,
                (long) cacheDelete,
                (long) cacheFlush,
                (long) hasnotHit,
                (long) hasnotMiss,
                (long) hasnotUnique,
                (long) hasnotDouble,
                (long) hasnotDelete,
                (long) hasnotFlush
                };
    }
    
    private static long[] combinedStatus(long[] a, long[] b) {
        return new long[]{
                a[0] + b[0],
                a[1] + b[1],
                a[2] + b[2],
                a[3] + b[3],
                Math.max(a[4], b[4]),
                Math.min(a[5], b[5]),
                Math.max(a[6], b[6]),
                a[7] + b[7],
                a[8] + b[8],
                a[9] + b[9],
                a[10] + b[10],
                a[11] + b[11],
                a[12] + b[12],
                a[13] + b[13],
                a[14] + b[14],
                a[15] + b[15],
                a[16] + b[16],
                a[17] + b[17],
                a[18] + b[18]
        };
    }
    
    public static long[] combinedStatus(long[][] a, int l) {
        if ((a == null) || (a.length == 0) || (l == 0)) return null;
        if ((a.length >= 1) && (l == 1)) return a[0];
        if ((a.length >= 2) && (l == 2)) return combinedStatus(a[0], a[1]);
        return combinedStatus(combinedStatus(a, l - 1), a[l - 1]);
    }
    
    public int writeBufferSize() {
        return
          ((writeBufferUnique  == null) ? 0 : writeBufferUnique.size()) + 
          ((writeBufferDoubles == null) ? 0 : writeBufferDoubles.size());
    }
    
    public kelondroProfile profile() {
        return index.profile(); // TODO: implement own profile and merge with global
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
                delete.add(row.getColBytes(index.primarykey()));
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
                delete.add(row.getColBytes(index.primarykey()));
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
    
    private boolean shortMemory() {
        return (serverMemory.available() < 2000000);
    }
    
    private void checkMissSpace() {
        if ((readMissCache != null) &&
            ((readMissCache.size() >= maxmiss) || (shortMemory()))
           ) {readMissCache.clear(); readMissCache.trim();}
    }
    
    private void checkHitSpace() throws IOException {
        int s;
        if ((s = sumRecords()) >= maxrecords) flushDoubles(s / 4);
        if ((s = sumRecords()) >= maxrecords) flushUnique(s / 4);
        if (((s = sumRecords()) >= maxrecords) && (readHitCache != null)) {
            readHitCache.clear();
            readHitCache.trim();
        }
        if (shortMemory()) {
            flushUnique();
            flushDoubles();
            if (readHitCache != null) {
                readHitCache.clear();
                readHitCache.trim();
            }
        }
    }
    
    public synchronized void close() throws IOException {
        readHitCache = null;
        readMissCache = null;
        flushUnique();
        flushDoubles();
        writeBufferUnique = null;
        writeBufferDoubles = null;
        index.close();
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

    public kelondroOrder order() {
        return index.order();
    }

    public int primarykey() {
        return index.primarykey();
    }

    public synchronized Entry put(Entry row) throws IOException {
        assert (row != null);
        assert (row.columns() == row().columns());
        //assert (!(serverLog.allZero(row.getColBytes(index.primarykey()))));
        
        byte[] key = row.getColBytes(index.primarykey());
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
        
        byte[] key = row.getColBytes(index.primarykey());
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
        
        byte[] key = row.getColBytes(index.primarykey());
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
        
        byte[] key = row.getColBytes(index.primarykey());
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
                kelondroRow.Entry dummy = readMissCache.put(readMissCache.row().newEntry(entry.getColBytes(index.primarykey())));
                if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
            }
            return entry;
        }

        if ((writeBufferDoubles != null) && (writeBufferDoubles.size() > 0)) {
            Entry entry = writeBufferDoubles.removeOne();
            byte[] key = entry.getColBytes(index.primarykey());
            if (readMissCache != null) {
                kelondroRow.Entry dummy = readMissCache.put(readMissCache.row().newEntry(key));
                if (dummy == null) this.hasnotUnique++; else this.hasnotDouble++;
            }
            index.remove(key);
            return entry;
        }
        
        Entry entry = index.removeOne();
        if (entry == null) return null;
        byte[] key = entry.getColBytes(index.primarykey());
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

}
