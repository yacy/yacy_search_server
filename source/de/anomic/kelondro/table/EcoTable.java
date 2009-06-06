// kelondroEcoIndex.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2008 on http://yacy.net
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

package de.anomic.kelondro.table;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.kelondro.index.IntegerHandleIndex;
import de.anomic.kelondro.index.Column;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowCollection;
import de.anomic.kelondro.index.RowSet;
import de.anomic.kelondro.index.ObjectIndex;
import de.anomic.kelondro.index.Row.Entry;
import de.anomic.kelondro.io.BufferedEcoFS;
import de.anomic.kelondro.io.EcoFS;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.kelondroException;
import de.anomic.kelondro.util.Log;

/*
 * The EcoIndex builds upon the EcoFS and tries to reduce the number of IO requests that the
 * EcoFS must do to a minimum. In best cases, no IO has to be done for read operations (complete database shadow in RAM)
 * and a rare number of write IO operations must be done for a large number of table-writings (using the write buffer of EcoFS)
 * To make the EcoIndex scalable in question of available RAM, there are two elements that must be scalable:
 * - the access index can be either completely in RAM (kelondroRAMIndex) or it is file-based (kelondroTree)
 * - the content cache can be either a complete RAM-based shadow of the File, or empty.
 * The content cache can also be deleted during run-time, if the available RAM gets too low.
 */

public class EcoTable implements ObjectIndex {

    // static tracker objects
    private static TreeMap<String, EcoTable> tableTracker = new TreeMap<String, EcoTable>();
    
    public static final int tailCacheDenyUsage  = 0;
    public static final int tailCacheForceUsage = 1;
    public static final int tailCacheUsageAuto  = 2;
    
    public static final long maxarraylength = 134217727L; // that may be the maxmimum size of array length in some JVMs
    private static final long minmemremaining = 20 * 1024 * 1024; // if less than this memory is remaininig, the memory copy of a table is abandoned
    private RowSet table;
    private IntegerHandleIndex index;
    private BufferedEcoFS file;
    private Row rowdef;
    private int fail;
    private File tablefile;
    private Row taildef;
    private final int buffersize;
    
    public EcoTable(final File tablefile, final Row rowdef, final int useTailCache, final int buffersize, final int initialSpace) {
        this.tablefile = tablefile;
        this.rowdef = rowdef;
        this.buffersize = buffersize;
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
                e.printStackTrace();
            }
            if (fos != null) try { fos.close(); } catch (final IOException e) {}
        }
        
        try {
            // open an existing table file
            final int fileSize = (int) tableSize(tablefile, rowdef.objectsize);
            
            // initialize index and copy table
            final int  records = Math.max(fileSize, initialSpace);
            final long neededRAM4table = (records) * ((rowdef.objectsize) + 4L) * 3L;
            table = ((neededRAM4table < maxarraylength) &&
                     ((useTailCache == tailCacheForceUsage) ||
                      ((useTailCache == tailCacheUsageAuto) && (MemoryControl.free() > neededRAM4table + 200 * 1024 * 1024)))) ?
                    new RowSet(taildef, records) : null;
            Log.logInfo("ECOTABLE", "initialization of " + tablefile + ": available RAM: " + (MemoryControl.available() / 1024 / 1024) + "MB, allocating space for " + records + " entries");
            final long neededRAM4index = 2 * 1024 * 1024 + records * (rowdef.primaryKeyLength + 4) * 3 / 2;
            if (!MemoryControl.request(neededRAM4index, false)) {
                // despite calculations seemed to show that there is enough memory for the table AND the index
                // there is now not enough memory left for the index. So delete the table again to free the memory
                // for the index
                Log.logSevere("ECOTABLE", tablefile + ": not enough RAM (" + (MemoryControl.available() / 1024 / 1024) + "MB) left for index, deleting allocated table space to enable index space allocation (needed: " + (neededRAM4index / 1024 / 1024) + "MB)");
                table = null; System.gc();
                Log.logSevere("ECOTABLE", tablefile + ": RAM after releasing the table: " + (MemoryControl.available() / 1024 / 1024) + "MB");
            }
            index = new IntegerHandleIndex(rowdef.primaryKeyLength, rowdef.objectOrder, records, 100000);
            Log.logInfo("ECOTABLE", tablefile + ": EcoTable " + tablefile.toString() + " has table copy " + ((table == null) ? "DISABLED" : "ENABLED"));

            // read all elements from the file into the copy table
            Log.logInfo("ECOTABLE", "initializing RAM index for EcoTable " + tablefile.getName() + ", please wait.");
            int i = 0;
            byte[] key;
            if (table == null) {
                final Iterator<byte[]> ki = new ChunkIterator(tablefile, rowdef.objectsize, rowdef.primaryKeyLength);
                while (ki.hasNext()) {
                    key = ki.next();
                    // write the key into the index table
                    assert key != null;
                    if (key == null) {i++; continue;}
                    index.putUnique(key, i++);
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
                    index.putUnique(key, i++);
                    
                    // write the tail into the table
                    table.addUnique(taildef.newEntry(record, rowdef.primaryKeyLength, true));
                    if (abandonTable()) {
                        table = null;
                        break;
                    }
                }
            }
            
            // open the file
            this.file = new BufferedEcoFS(new EcoFS(tablefile, rowdef.objectsize), this.buffersize);
 
            // remove doubles
            if (!freshFile) {
                final ArrayList<Integer[]> doubles = index.removeDoubles();
                //assert index.size() + doubles.size() + fail == i;
                //System.out.println(" -removed " + doubles.size() + " doubles- done.");
                if (doubles.size() > 0) {
                    Log.logInfo("ECOTABLE", tablefile + ": WARNING - EcoTable " + tablefile + " has " + doubles.size() + " doubles");
                    // from all the doubles take one, put it back to the index and remove the others from the file
                    // first put back one element each
                    final byte[] record = new byte[rowdef.objectsize];
                    key = new byte[rowdef.primaryKeyLength];
                    for (final Integer[] ds: doubles) {
                        file.get(ds[0].intValue(), record, 0);
                        System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);
                        index.putUnique(key, ds[0].intValue());
                    }
                    // then remove the other doubles by removing them from the table, but do a re-indexing while doing that
                    // first aggregate all the delete positions because the elements from the top positions must be removed first
                    final TreeSet<Integer> delpos = new TreeSet<Integer>();
                    for (final Integer[] ds: doubles) {
                        for (int j = 1; j < ds.length; j++) delpos.add(ds[j]);
                    }
                    // now remove the entries in a sorted way (top-down)
                    Integer top;
                    while (delpos.size() > 0) {
                        top = delpos.last();
                        delpos.remove(top);
                        removeInFile(top.intValue());
                    }
                }
            }
        } catch (final FileNotFoundException e) {
            // should never happen
            e.printStackTrace();
            throw new kelondroException(e.getMessage());
        } catch (final IOException e) {
            e.printStackTrace();
            throw new kelondroException(e.getMessage());
        }
        
        // track this table
        tableTracker.put(tablefile.toString(), this);
    }
    
    private boolean abandonTable() {
        // check if not enough memory is there to maintain a memory copy of the table
        return MemoryControl.available() < minmemremaining;
    }
    
    public static long tableSize(final File tablefile, final int recordsize) {
        // returns number of records in table
        return EcoFS.tableSize(tablefile, recordsize);
    }

    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return tableTracker.keySet().iterator();
    }

    public static final Map<String, String> memoryStats(final String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record objects,
        // i.e. for cache memory allocation
        final EcoTable theEcoTable = tableTracker.get(filename);
        return theEcoTable.memoryStats();
    }

    private final Map<String, String> memoryStats() {
        // returns statistical data about this object
        assert ((table == null) || (table.size() == index.size()));
        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("tableSize", Integer.toString(index.size()));
        map.put("tableKeyChunkSize", Integer.toString(index.row().objectsize));
        map.put("tableKeyMem", Integer.toString((int) (index.row().objectsize * index.size() * RowCollection.growfactor)));
        map.put("tableValueChunkSize", (table == null) ? "0" : Integer.toString(table.row().objectsize));
        map.put("tableValueMem", (table == null) ? "0" : Integer.toString((int) (table.row().objectsize * table.size() * RowCollection.growfactor)));
        return map;
    }
    
    public boolean usesFullCopy() {
        return this.table != null;
    }
    
    public static int staticRAMIndexNeed(final File f, final Row rowdef) {
        return (int) ((rowdef.primaryKeyLength + 4) * tableSize(f, rowdef.objectsize) * RowCollection.growfactor);
    }
    
    public synchronized void addUnique(final Entry row) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        final int i = (int) file.size();
        index.putUnique(row.getPrimaryKeyBytes(), i);
        if (table != null) {
            assert table.size() == i;
            table.addUnique(taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
            if (abandonTable()) table = null;
        }
        file.add(row.bytes(), 0);
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
    }

    public synchronized void addUnique(final List<Entry> rows) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        final Iterator<Entry> i = rows.iterator();
        while (i.hasNext()) {
            addUnique(i.next());
        }
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
    }

    /**
     * remove double-entries from the table
     * this process calls the underlying removeDoubles() method from the table index
     * and 
     */
    public synchronized ArrayList<RowCollection> removeDoubles() throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        final ArrayList<RowCollection> report = new ArrayList<RowCollection>();
        RowSet rows;
        final TreeSet<Integer> d = new TreeSet<Integer>();
        final byte[] b = new byte[rowdef.objectsize];
        Integer L;
        Row.Entry inconsistentEntry;
        // iterate over all entries that have inconsistent index references
        long lastlog = System.currentTimeMillis();
        for (final Integer[] is: index.removeDoubles()) {
            // 'is' is the set of all indexes, that have the same reference
            // we collect that entries now here
            rows = new RowSet(this.rowdef, is.length);
            for (int j = 0; j < is.length; j++) {
                L = is[j];
                assert L.intValue() < file.size() : "L.intValue() = " + L.intValue() + ", file.size = " + file.size(); // prevent ooBounds Exception
                d.add(L);
                if (L.intValue() >= file.size()) continue; // prevent IndexOutOfBoundsException
                file.get(L.intValue(), b, 0); // TODO: fix IndexOutOfBoundsException here
                inconsistentEntry = rowdef.newEntry(b);
                rows.addUnique(inconsistentEntry);
            }
            report.add(rows);
        }
        // finally delete the affected rows, but start with largest id first, otherwise we overwrite wrong entries
        Integer s;
        while (d.size() > 0) {
            s = d.last();
            d.remove(s);
            this.removeInFile(s.intValue());
            if (System.currentTimeMillis() - lastlog > 30000) {
                Log.logInfo("EcoTable", "removing " + d.size() + " entries in " + this.filename());
                lastlog = System.currentTimeMillis();
            }
        }
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        return report;
    }
    
    public void close() {
        this.file.close();
        this.file = null;
        this.table = null;
        this.index = null;
    }
    
    protected void finalize() {
        if (this.file != null) this.close();
    }

    public String filename() {
        return this.file.filename().toString();
    }
    
    public synchronized Entry get(final byte[] key) throws IOException {
    	if ((file == null) || (index == null)) return null;
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size() + ", fail = " + fail;
        assert ((table == null) || (table.size() == index.size()));
        final int i = index.get(key);
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
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        return rowdef.newEntry(b);
    }

    public synchronized boolean has(final byte[] key) {
        try {
            assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        assert ((table == null) || (table.size() == index.size()));
        return index.has(key);
    }

    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return index.keys(up, firstKey);
    }

    public synchronized Entry replace(final Entry row) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        assert row != null;
        assert row.bytes() != null;
        if ((row == null) || (row.bytes() == null)) return null;
        final int i = index.get(row.getPrimaryKeyBytes());
        if (i == -1) {
            addUnique(row);
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
            table.set(i, taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
            file.put(i, row.bytes(), 0);
        }
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        // return old value
        return rowdef.newEntry(b);
    }
    
    public synchronized void put(final Entry row) throws IOException {
        assert file == null || file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert table == null || table.size() == index.size();
        assert row != null;
        assert row.bytes() != null;
        if (file == null || row == null || row.bytes() == null) return;
        final int i = index.get(row.getPrimaryKeyBytes());
        if (i == -1) {
            addUnique(row);
            return;
        }
        
        if (table == null) {
            // write new value
            file.put(i, row.bytes(), 0);
        } else {
            // write new value
            table.set(i, taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
            file.put(i, row.bytes(), 0);
        }
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
    }

    public synchronized Entry put(final Entry row, final Date entryDate) throws IOException {
        return replace(row);
    }

    private void removeInFile(final int i) throws IOException {
        assert i >= 0;
        
        final byte[] p = new byte[rowdef.objectsize];
        if (table == null) {
            if (i == index.size() - 1) {
                file.cleanLast();
            } else {
                file.cleanLast(p, 0);
                file.put(i, p, 0);
                final byte[] k = new byte[rowdef.primaryKeyLength];
                System.arraycopy(p, 0, k, 0, rowdef.primaryKeyLength);
                index.put(k, i);
            }
        } else {
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                table.removeRow(i, false);
                file.cleanLast();
            } else {
                // switch values
                final Row.Entry te = table.removeOne();
                table.set(i, te);

                file.cleanLast(p, 0);
                file.put(i, p, 0);
                final Row.Entry lr = rowdef.newEntry(p);
                index.put(lr.getPrimaryKeyBytes(), i);
            }
        }
    }
    
    public synchronized Entry remove(final byte[] key) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        assert key.length == rowdef.primaryKeyLength;
        final int i = index.get(key);
        if (i == -1) return null; // nothing to do
        
        // prepare result
        final byte[] b = new byte[rowdef.objectsize];
        final byte[] p = new byte[rowdef.objectsize];
        final int sb = index.size();
        int ix;
        assert i < index.size();
        if (table == null) {
            if (i == index.size() - 1) {
                ix = index.remove(key);
                assert ix == i;
                file.cleanLast(b, 0);
            } else {
                assert i < index.size() - 1;
                ix = index.remove(key);
                assert ix == i;
                file.get(i, b, 0);
                file.cleanLast(p, 0);
                file.put(i, p, 0);
                final byte[] k = new byte[rowdef.primaryKeyLength];
                System.arraycopy(p, 0, k, 0, rowdef.primaryKeyLength);
                index.put(k, i);
            }
            assert (file.size() == index.size() + fail);
        } else {
            // get result value from the table copy, so we don't need to read it from the file
            final Row.Entry v = table.get(i, false);
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, taildef.objectsize);
            
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                ix = index.remove(key);
                assert ix == i;
                table.removeRow(i, false);
                file.cleanLast();
            } else {
                // switch values
                ix = index.remove(key);
                assert ix == i;
                
                final Row.Entry te = table.removeOne();
                table.set(i, te);

                file.cleanLast(p, 0);
                file.put(i, p, 0);
                final Row.Entry lr = rowdef.newEntry(p);
                index.put(lr.getPrimaryKeyBytes(), i);
            }
            assert (file.size() == index.size() + fail);
            assert (table.size() == index.size()) : "table.size() = " + table.size() + ", index.size() = " + index.size();
        }
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        assert index.size() + 1 == sb : "index.size() = " + index.size() + ", sb = " + sb;
        return rowdef.newEntry(b);
    }

    public synchronized Entry removeOne() throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        final byte[] le = new byte[rowdef.objectsize];
        file.cleanLast(le, 0);
        final Row.Entry lr = rowdef.newEntry(le);
        final int i = index.remove(lr.getPrimaryKeyBytes());
        assert i >= 0;
        if (table != null) table.remove(lr.getPrimaryKeyBytes());
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        return lr;
    }

    public void clear() throws IOException {
        final File f = file.filename();
        file.close();
        FileUtils.deletedelete(f);
        
        // make new file
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
        } catch (final FileNotFoundException e) {
            // should not happen
            e.printStackTrace();
        }
        if (fos != null) try { fos.close(); } catch (final IOException e) {}
        
        
        // open an existing table file
        try {
            this.file = new BufferedEcoFS(new EcoFS(f, rowdef.objectsize), this.buffersize);
        } catch (final FileNotFoundException e) {
            // should never happen
            e.printStackTrace();
        }
        
        // initialize index and copy table
        table = (table == null) ? null : new RowSet(taildef, 1);
        index = new IntegerHandleIndex(rowdef.primaryKeyLength, rowdef.objectOrder, 1, 100000);        
    }

    public Row row() {
        return this.rowdef;
    }

    public synchronized int size() {
        return index.size();
    }

    public synchronized CloneableIterator<Entry> rows() throws IOException {
        return new rowIteratorNoOrder();
    }

    public class rowIteratorNoOrder implements CloneableIterator<Entry> {
        final Iterator<byte[]> ri;
        
        public rowIteratorNoOrder() throws IOException {
            ri = new ChunkIterator(tablefile, rowdef.objectsize, rowdef.objectsize);
        }
        
        public CloneableIterator<Entry> clone(Object modifier) {
            try {
                return new rowIteratorNoOrder();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        
        public boolean hasNext() {
            return ri.hasNext();
        }
        
        public Entry next() {
            byte[] r = ri.next();
            return rowdef.newEntry(r);
        }
        
        public void remove() {
            throw new UnsupportedOperationException("no remove in row iterator");
        }
        
    }

    public synchronized CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return new rowIterator(up, firstKey);
    }

    public class rowIterator implements CloneableIterator<Entry> {
        Iterator<byte[]> i;
        boolean up;
        byte[] fk;
        int c;
        
        public rowIterator(final boolean up, final byte[] firstKey) throws IOException {
            this.up = up;
            this.fk = firstKey;
            this.i  = index.keys(up, firstKey);
            this.c = -1;
        }
        
        public CloneableIterator<Entry> clone(final Object modifier) {
            try {
                return new rowIterator(up, fk);
            } catch (final IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public boolean hasNext() {
            return i.hasNext();
        }

        public Entry next() {
            final byte[] k = i.next();
            assert k != null;
            if (k == null) return null;
            this.c = index.get(k);
            if (this.c < 0) throw new ConcurrentModificationException(); // this should only happen if the table was modified during the iteration
            final byte[] b = new byte[rowdef.objectsize];
            if (table == null) {
                // read from file
                try {
                    file.get(this.c, b, 0);
                } catch (final IOException e) {
                    e.printStackTrace();
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
            throw new UnsupportedOperationException("no remove in EcoTable");
        }
        
    }
    
    public static ObjectIndex testTable(final File f, final String testentities, final int testcase) throws IOException {
        if (f.exists()) FileUtils.deletedelete(f);
        final Row rowdef = new Row("byte[] a-4, byte[] b-4", NaturalOrder.naturalOrder);
        final ObjectIndex tt = new EcoTable(f, rowdef, testcase, 100, 0);
        byte[] b;
        final Row.Entry row = rowdef.newEntry();
        for (int i = 0; i < testentities.length(); i++) {
            b = Tree.testWord(testentities.charAt(i));
            row.setCol(0, b);
            row.setCol(1, b);
            tt.put(row);
        }
        return tt;
    }
    
   public static void bigtest(final int elements, final File testFile, final int testcase) {
        System.out.println("starting big test with " + elements + " elements:");
        final long start = System.currentTimeMillis();
        final String[] s = Tree.permutations(elements);
        ObjectIndex tt;
        try {
            for (int i = 0; i < s.length; i++) {
                System.out.println("*** probing tree " + i + " for permutation " + s[i]);
                // generate tree and delete elements
                tt = testTable(testFile, s[i], testcase);
                if (Tree.countElements(tt) != tt.size()) {
                    System.out.println("wrong size for " + s[i]);
                }
                tt.close();
                for (int j = 0; j < s.length; j++) {
                    tt = testTable(testFile, s[i], testcase);
                    // delete by permutation j
                    for (int elt = 0; elt < s[j].length(); elt++) {
                        tt.remove(Tree.testWord(s[j].charAt(elt)));
                        if (Tree.countElements(tt) != tt.size()) {
                            System.out.println("ERROR! wrong size for probe tree " + s[i] + "; probe delete " + s[j] + "; position " + elt);
                        }
                    }
                    tt.close();
                }
            }
            System.out.println("FINISHED test after " + ((System.currentTimeMillis() - start) / 1000) + " seconds.");
        } catch (final Exception e) {
            e.printStackTrace();
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
        bigtest(5, f, tailCacheDenyUsage);
        System.out.println("========= Testcase: with tail cache:");
        bigtest(5, f, tailCacheForceUsage);
        /*
        kelondroRow row = new kelondroRow("byte[] key-4, byte[] x-5", kelondroNaturalOrder.naturalOrder, 0);
        try {
            kelondroEcoTable t = new kelondroEcoTable(f, row);
            kelondroRow.Entry entry = row.newEntry();
            entry.setCol(0, "abcd".getBytes());
            entry.setCol(1, "dummy".getBytes());
            t.put(entry);
            t.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
    }

    public void deleteOnExit() {
        this.file.deleteOnExit();
    }

}
