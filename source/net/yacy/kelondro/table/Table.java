// Table.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2008 on http://yacy.net
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

package net.yacy.kelondro.table;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.HandleMap;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowCollection;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.io.BufferedRecords;
import net.yacy.kelondro.io.Records;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.kelondroException;


/*
 * The Table builds upon the EcoFS and tries to reduce the number of IO requests that the
 * EcoFS must do to a minimum. In best cases, no IO has to be done for read operations (complete database shadow in RAM)
 * and a rare number of write IO operations must be done for a large number of table-writings (using the write buffer of EcoFS)
 * To make the Table scalable in question of available RAM, there are two elements that must be scalable:
 * - the access index can be either completely in RAM (kelondroRAMIndex) or it is file-based (kelondroTree)
 * - the content cache can be either a complete RAM-based shadow of the File, or empty.
 * The content cache can also be deleted during run-time, if the available RAM gets too low.
 */

public class Table implements Index, Iterable<Row.Entry> {

    // static tracker objects
    private final static TreeMap<String, Table> tableTracker = new TreeMap<String, Table>();
    private final static long maxarraylength = 134217727L; // that may be the maximum size of array length in some JVMs
    
    private final long minmemremaining; // if less than this memory is remaininig, the memory copy of a table is abandoned
    private final int buffersize;
    private final Row rowdef;
    private final Row taildef;
    private       HandleMap index;
    private       BufferedRecords file;
    private       RowSet table;
    
    public Table(
    		final File tablefile,
    		final Row rowdef,
    		final int buffersize,
    		final int initialSpace,
    		boolean useTailCache,
    		final boolean exceed134217727) throws RowSpaceExceededException {
        useTailCache = true; // fixed for testing
        
        this.rowdef = rowdef;
        this.buffersize = buffersize;
        this.minmemremaining = Math.max(20 * 1024 * 1024, MemoryControl.available() / 10);
        //this.fail = 0;
        // define the taildef, a row like the rowdef but without the first column
        final Column[] cols = new Column[rowdef.columns() - 1];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = rowdef.column(i + 1);
        }
        this.taildef = new Row(cols, NaturalOrder.naturalOrder);
        
        // initialize table file
        boolean freshFile = false;
        if (!tablefile.exists()) {
            // make new file
            freshFile = true;
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(tablefile);
            } catch (final FileNotFoundException e) {
                // should not happen
                Log.logSevere("Table", "", e);
            }
            if (fos != null) try { fos.close(); } catch (final IOException e) {}
        }
        
        try {
            // open an existing table file
            int fileSize = (int) tableSize(tablefile, rowdef.objectsize, true);
            
            // initialize index and copy table
            final int  records = Math.max(fileSize, initialSpace);
            final long neededRAM4table = (records) * ((rowdef.objectsize) + 4L) * 3L;
            this.table = ((exceed134217727 || neededRAM4table < maxarraylength) &&
                     (useTailCache && MemoryControl.available() > neededRAM4table + 200 * 1024 * 1024)) ?
                    new RowSet(taildef, records) : null;
            Log.logInfo("TABLE", "initialization of " + tablefile.getName() + ". table copy: " + ((table == null) ? "no" : "yes") + ", available RAM: " + (MemoryControl.available() / 1024 / 1024) + "MB, needed: " + (neededRAM4table/1024/1024 + 200) + "MB, allocating space for " + records + " entries");
            final long neededRAM4index = 2 * 1024 * 1024 + records * (rowdef.primaryKeyLength + 4) * 3 / 2;
            if (!MemoryControl.request(neededRAM4index, false)) {
                // despite calculations seemed to show that there is enough memory for the table AND the index
                // there is now not enough memory left for the index. So delete the table again to free the memory
                // for the index
                Log.logSevere("TABLE", tablefile.getName() + ": not enough RAM (" + (MemoryControl.available() / 1024 / 1024) + "MB) left for index, deleting allocated table space to enable index space allocation (needed: " + (neededRAM4index / 1024 / 1024) + "MB)");
                this.table = null; System.gc();
                Log.logSevere("TABLE", tablefile.getName() + ": RAM after releasing the table: " + (MemoryControl.available() / 1024 / 1024) + "MB");
            }
            this.index = new HandleMap(rowdef.primaryKeyLength, rowdef.objectOrder, 4, records, tablefile.getAbsolutePath());
            HandleMap errors = new HandleMap(rowdef.primaryKeyLength, NaturalOrder.naturalOrder, 4, records, tablefile.getAbsolutePath() + ".errors");
            Log.logInfo("TABLE", tablefile + ": TABLE " + tablefile.toString() + " has table copy " + ((table == null) ? "DISABLED" : "ENABLED"));

            // read all elements from the file into the copy table
            Log.logInfo("TABLE", "initializing RAM index for TABLE " + tablefile.getName() + ", please wait.");
            int i = 0;
            byte[] key;
            if (this.table == null) {
                final Iterator<byte[]> ki = new ChunkIterator(tablefile, rowdef.objectsize, rowdef.primaryKeyLength);
                while (ki.hasNext()) {
                    key = ki.next();
                    // write the key into the index table
                    assert key != null;
                    if (key == null) {i++; continue;}
                    if (rowdef.objectOrder.wellformed(key)) {
                        this.index.putUnique(key, i++);
                    } else {
                        errors.putUnique(key, i++);
                    }
                }
            } else {
                byte[] record;
                key = new byte[rowdef.primaryKeyLength];
                final Iterator<byte[]> ri = new ChunkIterator(tablefile, rowdef.objectsize, rowdef.objectsize);
                while (ri.hasNext()) {
                    record = ri.next();
                    assert record != null;
                    if (record == null) {i++; continue;}
                    System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);
                    
                    // write the key into the index table
                    if (rowdef.objectOrder.wellformed(key)) {
                        index.putUnique(key, i++);
                        // write the tail into the table
                        try {
                            this.table.addUnique(taildef.newEntry(record, rowdef.primaryKeyLength, true));
                        } catch (RowSpaceExceededException e) {
                            this.table = null;
                            break;
                        }
                        if (abandonTable()) {
                            this.table = null;
                            break;
                        }
                    } else {
                        errors.putUnique(key, i++);
                    }
                }
            }
            
            // open the file
            this.file = new BufferedRecords(new Records(tablefile, rowdef.objectsize), this.buffersize);
            assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size() + ", file = " + this.filename();
            
            // clean up the file by cleaning badly formed entries
            int errorc = errors.size();
            int errorcc = 0;
            int idx;
            for (Entry entry: errors) {
                key = entry.getPrimaryKeyBytes();
                idx = (int) entry.getColLong(1);
                Log.logWarning("Table", "removing not well-formed entry " + idx + " with key: " + NaturalOrder.arrayList(key, 0, key.length) + ", " + errorcc++ + "/" + errorc);
                removeInFile(idx);
            }
            errors.close();
            assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size() + ", file = " + this.filename();
            
            // remove doubles
            if (!freshFile) {
                final ArrayList<long[]> doubles = index.removeDoubles();
                //assert index.size() + doubles.size() + fail == i;
                //System.out.println(" -removed " + doubles.size() + " doubles- done.");
                if (!doubles.isEmpty()) {
                    Log.logInfo("TABLE", tablefile + ": WARNING - TABLE " + tablefile + " has " + doubles.size() + " doubles");
                    // from all the doubles take one, put it back to the index and remove the others from the file
                    // first put back one element each
                    final byte[] record = new byte[rowdef.objectsize];
                    key = new byte[rowdef.primaryKeyLength];
                    for (final long[] ds: doubles) {
                        this.file.get((int) ds[0], record, 0);
                        System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);
                        this.index.putUnique(key, (int) ds[0]);
                    }
                    // then remove the other doubles by removing them from the table, but do a re-indexing while doing that
                    // first aggregate all the delete positions because the elements from the top positions must be removed first
                    final TreeSet<Long> delpos = new TreeSet<Long>();
                    for (final long[] ds: doubles) {
                        for (int j = 1; j < ds.length; j++) delpos.add(ds[j]);
                    }
                    // now remove the entries in a sorted way (top-down)
                    Long top;
                    while (!delpos.isEmpty()) {
                        top = delpos.last();
                        delpos.remove(top);
                        removeInFile(top.intValue());
                    }
                }
            }
        } catch (final FileNotFoundException e) {
            // should never happen
            Log.logSevere("Table", "", e);
            throw new kelondroException(e.getMessage());
        } catch (final IOException e) {
            Log.logSevere("Table", "", e);
            throw new kelondroException(e.getMessage());
        }
        
        // track this table
        tableTracker.put(tablefile.toString(), this);
    }
    
    public long mem() {
        return index.mem() + ((table == null) ? 0 : table.mem());
    }
    
    private boolean abandonTable() {
        // check if not enough memory is there to maintain a memory copy of the table
        return MemoryControl.shortStatus() || MemoryControl.available() < minmemremaining;
    }
    
    public byte[] smallestKey() {
        return this.index.smallestKey();
    }
    
    public byte[] largestKey() {
        return this.index.largestKey();
    }
    
    public static long tableSize(final File tablefile, final int recordsize, boolean fixIfCorrupted) throws kelondroException {
        try {
            return Records.tableSize(tablefile, recordsize);
        } catch (final IOException e) {
            if (!fixIfCorrupted) {
                Log.logSevere("Table", "table size broken for file " + tablefile.toString(), e);
                throw new kelondroException(e.getMessage());
            }
            Log.logSevere("Table", "table size broken, try to fix " + tablefile.toString());
            try {
                Records.fixTableSize(tablefile, recordsize);
                Log.logInfo("Table", "successfully fixed table file " + tablefile.toString());
                return Records.tableSize(tablefile, recordsize);
            } catch (final IOException ee) {
                Log.logSevere("Table", "table size fix did not work", ee);
                throw new kelondroException(e.getMessage());
            }
        }
    }

    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return tableTracker.keySet().iterator();
    }

    public static final Map<String, String> memoryStats(final String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record objects,
        // i.e. for cache memory allocation
        final Table theTABLE = tableTracker.get(filename);
        return theTABLE.memoryStats();
    }

    private final Map<String, String> memoryStats() {
        // returns statistical data about this object
        synchronized (this) {
            assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        }
        final HashMap<String, String> map = new HashMap<String, String>(8);
        if (index == null) return map; // possibly closed or beeing closed
        map.put("tableSize", Integer.toString(index.size()));
        map.put("tableKeyChunkSize", Integer.toString(index.row().objectsize));
        map.put("tableKeyMem", Integer.toString(index.row().objectsize * index.size()));
        map.put("tableValueChunkSize", (table == null) ? "0" : Integer.toString(table.row().objectsize));
        map.put("tableValueMem", (table == null) ? "0" : Integer.toString(table.row().objectsize * table.size()));
        return map;
    }
    
    public boolean usesFullCopy() {
        return this.table != null;
    }
    
    public static long staticRAMIndexNeed(final File f, final Row rowdef) {
        return (((long)(rowdef.primaryKeyLength + 4)) * tableSize(f, rowdef.objectsize, true) * RowCollection.growfactorLarge100 / 100L);
    }
    
    public boolean consistencyCheck() {
        try {
            return file.size() == index.size();
        } catch (IOException e) {
            Log.logException(e);
            return false;
        }
    }
    
    public synchronized void addUnique(final Entry row) throws IOException, RowSpaceExceededException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        final int i = (int) file.size();
        try {
            index.putUnique(row.getPrimaryKeyBytes(), i);
        } catch (RowSpaceExceededException e) {
            if (table == null) throw e; // in case the table is not used, there is no help here
            table = null;
            // try again with less memory
            index.putUnique(row.getPrimaryKeyBytes(), i);
        }
        if (table != null) {
            assert table.size() == i;
            try {
                table.addUnique(taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
            } catch (RowSpaceExceededException e) {
                table = null;
            }
            if (abandonTable()) table = null;
        }
        file.add(row.bytes(), 0);
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
    }

    public synchronized void addUnique(final List<Entry> rows) throws IOException, RowSpaceExceededException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        for (Entry entry: rows) {
            try {
                addUnique(entry);
            } catch (RowSpaceExceededException e) {
                if (this.table == null) throw e;
                table = null;
                addUnique(entry);
            }
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
    }

    /**
     * @throws RowSpaceExceededException 
     * remove double-entries from the table
     * this process calls the underlying removeDoubles() method from the table index
     * and 
     * @throws  
     */
    public synchronized List<RowCollection> removeDoubles() throws IOException, RowSpaceExceededException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        final List<RowCollection> report = new ArrayList<RowCollection>();
        RowSet rows;
        final TreeSet<Long> d = new TreeSet<Long>();
        final byte[] b = new byte[rowdef.objectsize];
        Row.Entry inconsistentEntry;
        // iterate over all entries that have inconsistent index references
        long lastlog = System.currentTimeMillis();
        List<long[]> doubles;
        try {
            doubles = index.removeDoubles();
        } catch (RowSpaceExceededException e) {
            if (this.table == null) throw e;
            table = null;
            doubles = index.removeDoubles();
        }
        for (final long[] is: doubles) {
            // 'is' is the set of all indexes, that have the same reference
            // we collect that entries now here
            rows = new RowSet(this.rowdef, is.length);
            for (final long L : is) {
                assert (int) L < file.size() : "L.intValue() = " + (int) L + ", file.size = " + file.size(); // prevent ooBounds Exception
                d.add(L);
                if ((int) L >= file.size()) continue; // prevent IndexOutOfBoundsException
                file.get((int) L, b, 0); // TODO: fix IndexOutOfBoundsException here
                inconsistentEntry = rowdef.newEntry(b);
                try {
                    rows.addUnique(inconsistentEntry);
                } catch (RowSpaceExceededException e) {
                    if (this.table == null) throw e;
                    this.table = null;
                    rows.addUnique(inconsistentEntry);
                }
            }
            report.add(rows);
        }
        // finally delete the affected rows, but start with largest id first, otherwise we overwrite wrong entries
        Long s;
        while (!d.isEmpty()) {
            s = d.last();
            d.remove(s);
            this.removeInFile(s.intValue());
            if (System.currentTimeMillis() - lastlog > 30000) {
                Log.logInfo("TABLE", "removing " + d.size() + " entries in " + this.filename());
                lastlog = System.currentTimeMillis();
            }
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        return report;
    }
    
    public void close() {
        if (this.file != null) this.file.close();
        this.file = null;
        if (this.table != null) this.table.close();
        this.table = null;
        if (this.index != null) this.index.close();
        this.index = null;
    }
    
    @Override
    protected void finalize() {
        if (this.file != null) this.close();
    }

    public String filename() {
        return this.file.filename().toString();
    }

    public Entry get(final byte[] key) throws IOException {
        if (file == null || index == null) return null;
        Entry e = get0(key);
        if (e != null && this.rowdef.objectOrder.equal(key, e.getPrimaryKeyBytes())) return e;
        synchronized (this) {
            assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size() + ", file = " + this.filename();
            assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size() + ", file = " + this.filename();
            e = get0(key);
            assert e == null || this.rowdef.objectOrder.equal(key, e.getPrimaryKeyBytes());
            return e;
        }
    }
    
    private Entry get0(final byte[] key) throws IOException {
    	if (file == null || index == null) return null;
        final int i = (int) index.get(key);
        if (i == -1) return null;
        final byte[] b = new byte[rowdef.objectsize];
        if (table == null) {
            // read row from the file
            file.get(i, b, 0);
        } else {
            // construct the row using the copy in RAM
            final Row.Entry v = table.get(i, false);
            assert v != null;
            if (v == null) return null;
            assert key.length == rowdef.primaryKeyLength;
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, rowdef.objectsize - rowdef.primaryKeyLength);
        }
        return rowdef.newEntry(b);
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

    public boolean has(final byte[] key) {
        if (index == null) return false;
        return index.has(key);
    }

    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return index.keys(up, firstKey);
    }

    public synchronized Entry replace(final Entry row) throws IOException, RowSpaceExceededException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        assert row != null;
        assert row.bytes() != null;
        if ((row == null) || (row.bytes() == null)) return null;
        final int i = (int) index.get(row.getPrimaryKeyBytes());
        if (i == -1) {
            try {
                addUnique(row);
            } catch (RowSpaceExceededException e) {
                if (this.table == null) throw e;
                this.table = null;
                addUnique(row);
            }
            return null;
        }
        
        final byte[] b = new byte[rowdef.objectsize];
        if (table == null) {
            // read old value
            file.get(i, b, 0);
            // write new value
            file.put(i, row.bytes(), 0);
        } else {
            // read old value
            final Row.Entry v = table.get(i, false);
            assert v != null;
            System.arraycopy(row.getPrimaryKeyBytes(), 0, b, 0, rowdef.primaryKeyLength);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, rowdef.objectsize - rowdef.primaryKeyLength);
            // write new value
            try {
                table.set(i, taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
            } catch (RowSpaceExceededException e) {
                table = null;
            }
            if (abandonTable()) table = null;
            file.put(i, row.bytes(), 0);
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        // return old value
        return rowdef.newEntry(b);
    }
    
    /**
     * Adds the row to the index. The row is identified by the primary key of the row.
     * @param row a index row
     * @return true if this set did _not_ already contain the given row. 
     * @throws IOException
     * @throws RowSpaceExceededException
     */
    public synchronized boolean put(final Entry row) throws IOException, RowSpaceExceededException {
        assert file == null || file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size() + ", file = " + this.filename();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size() + ", file = " + this.filename();
        assert row != null;
        assert row.bytes() != null;
        if (file == null || row == null || row.bytes() == null) return true;
        final int i = (int) index.get(row.getPrimaryKeyBytes());
        if (i == -1) {
            try {
                addUnique(row);
            } catch (RowSpaceExceededException e) {
                if (this.table == null) throw e;
                this.table = null;
                addUnique(row);
            }
            return true;
        }
        
        if (table == null) {
            // write new value
            file.put(i, row.bytes(), 0);
        } else {
            // write new value
            try {
                table.set(i, taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
            } catch (RowSpaceExceededException e) {
                table = null;
            }
            if (abandonTable()) table = null;
            file.put(i, row.bytes(), 0);
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        return false;
    }

    public Entry put(final Entry row, final Date entryDate) throws IOException, RowSpaceExceededException {
        return replace(row);
    }

    /**
     * remove one entry from the file
     * @param i an index position within the file (not a byte position)
     * @throws IOException
     * @throws RowSpaceExceededException 
     */
    private void removeInFile(final int i) throws IOException, RowSpaceExceededException {
        assert i >= 0;
        
        final byte[] p = new byte[this.rowdef.objectsize];
        if (this.table == null) {
            if (i == this.index.size() - 1) {
                this.file.cleanLast();
            } else {
                while (this.file.size() > 0) {
                    this.file.cleanLast(p, 0);
                    if (!(this.rowdef.objectOrder.wellformed(p, 0, this.rowdef.primaryKeyLength))) {
                        continue;
                    }
                    this.file.put(i, p, 0);
                    final byte[] k = new byte[this.rowdef.primaryKeyLength];
                    System.arraycopy(p, 0, k, 0, this.rowdef.primaryKeyLength);
                    this.index.put(k, i);
                    break;
                }
            }
        } else {
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                table.removeRow(i, false);
                file.cleanLast();
            } else {
                // switch values
                final Row.Entry te = table.removeOne();
                try {
                    table.set(i, te);
                } catch (RowSpaceExceededException e) {
                    table = null;
                }

                while (file.size() > 0) {
                    file.cleanLast(p, 0);
                    final Row.Entry lr = rowdef.newEntry(p);
                    if (lr == null) {
                        // in case that p is not well-formed lr may be null
                        // drop table copy because that becomes too complicated here
                        table.clear();
                        table = null;
                        continue;
                    }
                    file.put(i, p, 0);
                    index.put(lr.getPrimaryKeyBytes(), i);
                    break;
                }
            }
        }
    }
    
    public boolean delete(final byte[] key) throws IOException {
        return remove(key) != null;
    }
    
    public synchronized Entry remove(final byte[] key) throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        assert key.length == rowdef.primaryKeyLength;
        final int i = (int) index.get(key);
        if (i == -1) return null; // nothing to do
        
        // prepare result
        final byte[] b = new byte[rowdef.objectsize];
        final byte[] p = new byte[rowdef.objectsize];
        final int sb = index.size();
        int ix;
        assert i < index.size();
        if (table == null) {
            if (i == index.size() - 1) {
                // element is at last entry position
                ix = (int) index.remove(key);
                assert index.size() < i + 1 : "index.size() = " + index.size() + ", i = " + i;
                assert ix == i;
                file.cleanLast(b, 0);
            } else {
                // remove entry from index
                assert i < index.size() - 1 : "index.size() = " + index.size() + ", i = " + i;
                ix = (int) index.remove(key);
                assert i < index.size() : "index.size() = " + index.size() + ", i = " + i;
                assert ix == i;
                
                // read element that shall be removed
                file.get(i, b, 0);
                
                // fill the gap with value from last entry in file
                file.cleanLast(p, 0);
                file.put(i, p, 0);
                final byte[] k = new byte[rowdef.primaryKeyLength];
                System.arraycopy(p, 0, k, 0, rowdef.primaryKeyLength);
                try {
                    index.put(k, i);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                    throw new IOException("RowSpaceExceededException: " + e.getMessage());
                }
            }
            assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        } else {
            // get result value from the table copy, so we don't need to read it from the file
            final Row.Entry v = table.get(i, false);
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, taildef.objectsize);
            
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                ix = (int) index.remove(key);
                assert index.size() < i + 1  : "index.size() = " + index.size() + ", i = " + i;
                assert ix == i;
                table.removeRow(i, false);
                file.cleanLast();
            } else {
                // remove entry from index
                ix = (int) index.remove(key);
                assert i < index.size() : "index.size() = " + index.size() + ", i = " + i;
                assert ix == i;

                // switch values:
                // remove last entry from the file copy to fill it in the gap
                final Row.Entry te = table.removeOne();
                // fill the gap in file copy
                try {
                    table.set(i, te);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                    table = null;
                }

                // move entry from last entry in file to gap position
                file.cleanLast(p, 0);
                file.put(i, p, 0);
                // set new index for moved entry in index
                final Row.Entry lr = rowdef.newEntry(p);
                try {
                    index.put(lr.getPrimaryKeyBytes(), i);
                } catch (RowSpaceExceededException e) {
                    table = null;
                    throw new IOException("RowSpaceExceededException: " + e.getMessage());
                }
            }
            assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
            assert table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        assert index.size() + 1 == sb : "index.size() = " + index.size() + ", sb = " + sb;
        return rowdef.newEntry(b);
    }

    public synchronized Entry removeOne() throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        final byte[] le = new byte[rowdef.objectsize];
        long fsb = file.size();
        assert fsb != 0 : "file.size() = " + fsb;
        file.cleanLast(le, 0);
        assert file.size() < fsb : "file.size() = " + file.size();
        final Row.Entry lr = rowdef.newEntry(le);
        assert lr != null;
        assert lr.getPrimaryKeyBytes() != null;
        final int is = index.size();
        assert index.has(lr.getPrimaryKeyBytes());
        final int i = (int) index.remove(lr.getPrimaryKeyBytes());
        assert i < 0 || index.size() < is : "index.size() = " + index.size() + ", is = " + is;
        assert i >= 0;
        if (table != null) {
            int tsb = table.size();
            table.removeOne();
            assert table.size() < tsb : "table.size() = " + table.size() + ", tsb = " + tsb;
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size() : "table.size() = " + table.size() + ", index.size() = " + index.size();
        return lr;
    }
    
    public List<Row.Entry> top(int count) throws IOException {
        ArrayList<Row.Entry> list = new ArrayList<Row.Entry>();
        if ((file == null) || (index == null)) return list;
        long i = file.size() - 1;
        while (count > 0 && i >= 0) {
            byte[] b = new byte[rowdef.objectsize];
            file.get(i, b, 0);
            list.add(rowdef.newEntry(b));
            i--;
            count--;
        }
        return list;
    }

    public synchronized void clear() throws IOException {
        final File f = file.filename();
        this.file.close();
        this.file = null;
        FileUtils.deletedelete(f);
        
        // make new file
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
        } catch (final FileNotFoundException e) {
            // should not happen
            Log.logSevere("Table", "", e);
        }
        if (fos != null) try { fos.close(); } catch (final IOException e) {}
        
        this.file = new BufferedRecords(new Records(f, rowdef.objectsize), this.buffersize);
        
        // initialize index and copy table
        table = (table == null) ? null : new RowSet(taildef);
        index.clear();        
    }

    public Row row() {
        return this.rowdef;
    }

    public int size() {
        return index.size();
    }
    
    public boolean isEmpty() {
        return index.isEmpty();
    }
    
    public Iterator<Entry> iterator() {
        try {
            return rows();
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized CloneableIterator<Entry> rows() throws IOException {
        this.file.flushBuffer();
        return new rowIteratorNoOrder();
    }

    private class rowIteratorNoOrder implements CloneableIterator<Entry> {
        Iterator<Row.Entry> i;
        int idx;
        byte[] key;
        
        public rowIteratorNoOrder() {
            // don't use the ChunkIterator here because it may create too many open files during string load
            //ri = new ChunkIterator(tablefile, rowdef.objectsize, rowdef.objectsize);
            i = index.iterator();
        }
        
        public CloneableIterator<Entry> clone(Object modifier) {
            return new rowIteratorNoOrder();
        }
        
        public boolean hasNext() {
            return i != null && i.hasNext();
        }
        
        public Entry next() {
            Row.Entry entry = i.next();
            if (entry == null) return null;
            key = entry.getPrimaryKeyBytes();
            if (key == null) return null;
            idx = (int) entry.getColLong(1);
            try {
                return get(key);
            } catch (IOException e) {
                return null;
            }
        }
        
        public void remove() {
            if (key != null) {
                try {
                    removeInFile(idx);
                } catch (IOException e) {
                } catch (RowSpaceExceededException e) {
                }
                i.remove();
            }
        }
        
    }

    public synchronized CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return new rowIterator(up, firstKey);
    }

    private class rowIterator implements CloneableIterator<Entry> {
        private Iterator<byte[]> i;
        private boolean up;
        private byte[] fk;
        private int c;
        
        private rowIterator(final boolean up, final byte[] firstKey) {
            this.up = up;
            this.fk = firstKey;
            this.i  = index.keys(up, firstKey);
            this.c = -1;
        }
        
        public CloneableIterator<Entry> clone(final Object modifier) {
            return new rowIterator(up, fk);
        }

        public boolean hasNext() {
            return i.hasNext();
        }

        public Entry next() {
            final byte[] k = i.next();
            assert k != null;
            if (k == null) return null;
            this.c = (int) index.get(k);
            if (this.c < 0) throw new ConcurrentModificationException(); // this should only happen if the table was modified during the iteration
            final byte[] b = new byte[rowdef.objectsize];
            if (table == null) {
                // read from file
                try {
                    file.get(this.c, b, 0);
                } catch (final IOException e) {
                    Log.logSevere("Table", "", e);
                    return null;
                }
            } else {
                // compose from table and key
                final Row.Entry v = table.get(this.c, false);
                assert v != null;
                if (v == null) return null;
                System.arraycopy(k, 0, b, 0, rowdef.primaryKeyLength);
                System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, taildef.objectsize);
            }
            return rowdef.newEntry(b);
        }

        public void remove() {
            throw new UnsupportedOperationException("no remove in Table.rowIterator");
        }
        
    }
    
    private static byte[] testWord(final char c) {
        return new byte[]{(byte) c, 32, 32, 32};
    }

    private static String[] permutations(final int letters) {
        String p = "";
        for (int i = 0; i < letters; i++) p = p + ((char) (('A') + i));
        return permutations(p);
    }
    
    private static String[] permutations(final String source) {
        if (source.length() == 0) return new String[0];
        if (source.length() == 1) return new String[]{source};
        final char c = source.charAt(0);
        final String[] recres = permutations(source.substring(1));
        final String[] result = new String[source.length() * recres.length];
        for (int perm = 0; perm < recres.length; perm++) {
            result[perm * source.length()] = c + recres[perm];
            for (int pos = 1; pos < source.length() - 1; pos++) {
                result[perm * source.length() + pos] = recres[perm].substring(0, pos) + c + recres[perm].substring(pos);
            }
	    result[perm * source.length() + source.length() - 1] = recres[perm] + c;
        }
        return result;
    }

    private static Table testTable(final File f, final String testentities, final boolean useTailCache, final boolean exceed134217727) throws IOException, RowSpaceExceededException {
        if (f.exists()) FileUtils.deletedelete(f);
        final Row rowdef = new Row("byte[] a-4, byte[] b-4", NaturalOrder.naturalOrder);
        final Table tt = new Table(f, rowdef, 100, 0, useTailCache, exceed134217727);
        byte[] b;
        final Row.Entry row = rowdef.newEntry();
        for (int i = 0; i < testentities.length(); i++) {
            b = testWord(testentities.charAt(i));
            row.setCol(0, b);
            row.setCol(1, b);
            tt.put(row);
        }
        return tt;
    }
    
    private static int countElements(final Table t) {
        int count = 0;
        for (Row.Entry row: t) {
            count++;
            if (row == null) System.out.println("ERROR! null element found");
            // else System.out.println("counted element: " + new                                                    
            // String(n.getKey()));                                                                                 
        }
        return count;
    }
    
    public static void bigtest(final int elements, final File testFile, final boolean useTailCache, final boolean exceed134217727) {
        System.out.println("starting big test with " + elements + " elements:");
        final long start = System.currentTimeMillis();
        final String[] s = permutations(elements);
        Table tt;
        int count;
        try {
            for (int i = 0; i < s.length; i++) {
                System.out.println("*** probing tree " + i + " for permutation " + s[i]);
                // generate tree and delete elements
                tt = testTable(testFile, s[i], useTailCache, exceed134217727);
                count = countElements(tt);
                if (count != tt.size()) {
                    System.out.println("wrong size for " + s[i] + ": count = " + count + ", size() = " + tt.size());
                }
                tt.close();
                for (int j = 0; j < s.length; j++) {
                    tt = testTable(testFile, s[i], useTailCache, exceed134217727);
                    // delete by permutation j
                    for (int elt = 0; elt < s[j].length(); elt++) {
                        tt.remove(testWord(s[j].charAt(elt)));
                        count = countElements(tt);
                        if (count != tt.size()) {
                            System.out.println("ERROR! wrong size for probe tree " + s[i] + "; probe delete " + s[j] + "; position " + elt + "; count = " + count + ", size() = " + tt.size());
                        }
                    }
                    tt.close();
                }
            }
            System.out.println("FINISHED test after " + ((System.currentTimeMillis() - start) / 1000) + " seconds.");
        } catch (final Exception e) {
            Log.logException(e);
            System.out.println("TERMINATED");
        }
    }
    
    public void print() throws IOException {
        System.out.println("PRINTOUT of table, length=" + size());
        Entry row;
        byte[] key;
        CloneableIterator<byte[]> i = keys(true, null);
        while (i.hasNext()) {
            System.out.print("row " + i + ": ");
            key = i.next();
            row = get(key);
            System.out.println(row.toString());
        }
        System.out.println("EndOfTable");
    }

    public static void main(final String[] args) {
        // open a file, add one entry and exit
        final File f = new File(args[0]);
        System.out.println("========= Testcase: no tail cache:");
        bigtest(5, f, false, false);
        System.out.println("========= Testcase: with tail cache:");
        bigtest(5, f, true, true);
        /*
        kelondroRow row = new kelondroRow("byte[] key-4, byte[] x-5", kelondroNaturalOrder.naturalOrder, 0);
        try {
            kelondroTABLE t = new kelondroTABLE(f, row);
            kelondroRow.Entry entry = row.newEntry();
            entry.setCol(0, "abcd".getBytes());
            entry.setCol(1, "dummy".getBytes());
            t.put(entry);
            t.close();
        } catch (IOException e) {
            Log.logException(e);
        }
        */
    }

    public void deleteOnExit() {
        this.file.deleteOnExit();
    }

}
