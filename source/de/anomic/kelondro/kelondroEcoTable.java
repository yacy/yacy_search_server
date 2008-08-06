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

package de.anomic.kelondro;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.kelondro.kelondroRow.Entry;
import de.anomic.server.serverMemory;

/*
 * The EcoIndex builds upon the EcoFS and tries to reduce the number of IO requests that the
 * EcoFS must do to a minimum. In best cases, no IO has to be done for read operations (complete database shadow in RAM)
 * and a rare number of write IO operations must be done for a large number of table-writings (using the write buffer of EcoFS)
 * To make the EcoIndex scalable in question of available RAM, there are two elements that must be scalable:
 * - the access index can be either completely in RAM (kelondroRAMIndex) or it is file-based (kelondroTree)
 * - the content cache can be either a complete RAM-based shadow of the File, or empty.
 * The content cache can also be deleted during run-time, if the available RAM gets too low.
 */

public class kelondroEcoTable implements kelondroIndex {

    // static tracker objects
    private static TreeMap<String, kelondroEcoTable> tableTracker = new TreeMap<String, kelondroEcoTable>();
    
    public static final int tailCacheDenyUsage  = 0;
    public static final int tailCacheForceUsage = 1;
    public static final int tailCacheUsageAuto  = 2;
    
    public static final long maxarraylength = 134217727L; // that may be the maxmimum size of array length in some JVMs
    
    kelondroRowSet table;
    kelondroBytesIntMap index;
    kelondroBufferedEcoFS file;
    kelondroRow rowdef;
    int fail;

    kelondroRow taildef;
    private final int buffersize;
    
    public kelondroEcoTable(final File tablefile, final kelondroRow rowdef, final int useTailCache, final int buffersize, final int initialSpace) {
        this.rowdef = rowdef;
        this.buffersize = buffersize;
        this.fail = 0;
        assert rowdef.primaryKeyIndex == 0;
        // define the taildef, a row like the rowdef but without the first column
        final kelondroColumn[] cols = new kelondroColumn[rowdef.columns() - 1];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = rowdef.column(i + 1);
        }
        this.taildef = new kelondroRow(cols, kelondroNaturalOrder.naturalOrder, -1);
        
        // initialize table file
        if (!tablefile.exists()) {
            // make new file
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
                      ((useTailCache == tailCacheUsageAuto) && (serverMemory.free() > neededRAM4table + 200 * 1024 * 1024)))) ?
                    new kelondroRowSet(taildef, records) : null;
            System.out.println("*** DEBUG " + tablefile + ": available RAM: " + (serverMemory.available() / 1024 / 1024) + "MB, allocating space for " + records + " entries");
            final long neededRAM4index = 2 * 1024 * 1024 + records * (rowdef.primaryKeyLength + 4) * 3 / 2;
            if (!serverMemory.request(neededRAM4index, false)) {
                // despite calculations seemed to show that there is enough memory for the table AND the index
                // there is now not enough memory left for the index. So delete the table again to free the memory
                // for the index
                System.out.println("*** DEBUG " + tablefile + ": not enough RAM (" + (serverMemory.available() / 1024 / 1024) + "MB) left for index, deleting allocated table space to enable index space allocation (needed: " + (neededRAM4index / 1024 / 1024) + "MB)");
                table = null; System.gc();
                System.out.println("*** DEBUG " + tablefile + ": RAM after releasing the table: " + (serverMemory.available() / 1024 / 1024) + "MB");
            }
            index = new kelondroBytesIntMap(rowdef.primaryKeyLength, rowdef.objectOrder, records);
            System.out.println("*** DEBUG " + tablefile + ": EcoTable " + tablefile.toString() + " has table copy " + ((table == null) ? "DISABLED" : "ENABLED"));

            // read all elements from the file into the copy table
            System.out.print("*** initializing RAM index for EcoTable " + tablefile.getName() + ":");
            int i = 0;
            byte[] key;
            if (table == null) {
                final Iterator<byte[]> ki = keyIterator(tablefile, rowdef);
                while (ki.hasNext()) {
                    key = ki.next();
                
                    // write the key into the index table
                    assert key != null;
                    if (key == null) {i++; continue;}
                    if (!index.addi(key, i++)) fail++;
                    assert index.size() + fail == i : "index.size() = " + index.size() + ", i = " + i + ", fail = " + fail + ", key = '" + new String(key) + "'";
                    
                    if ((i % 10000) == 0) {
                        System.out.print('.');
                        System.out.flush();
                    }
                }
            } else {
                byte[] record;
                key = new byte[rowdef.primaryKeyLength];
                final Iterator<byte[]> ri = new kelondroEcoFS.ChunkIterator(tablefile, rowdef.objectsize, rowdef.objectsize);
                while (ri.hasNext()) {
                    record = ri.next();
                    assert record != null;
                    if (record == null) {i++; continue;}
                    System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);
                    
                    // write the key into the index table
                    if (!index.addi(key, i++)) fail++;
                    
                    // write the tail into the table
                    table.addUnique(taildef.newEntry(record, rowdef.primaryKeyLength, true));
                
                    if ((i % 10000) == 0) {
                        System.out.print('.');
                        System.out.flush();
                    }
                }
            }
            
            // check consistency
            System.out.print(" -ordering- ..");
            System.out.flush();
            this.file = new kelondroBufferedEcoFS(new kelondroEcoFS(tablefile, rowdef.objectsize), this.buffersize);
            final ArrayList<Integer[]> doubles = index.removeDoubles();
            //assert index.size() + doubles.size() + fail == i;
            System.out.println(" -removed " + doubles.size() + " doubles- done.");
            if (doubles.size() > 0) {
                System.out.println("DEBUG " + tablefile + ": WARNING - EcoTable " + tablefile + " has " + doubles.size() + " doubles");
                // from all the doubles take one, put it back to the index and remove the others from the file
                // first put back one element each
                final byte[] record = new byte[rowdef.objectsize];
                key = new byte[rowdef.primaryKeyLength];
                for (final Integer[] ds: doubles) {
                    file.get(ds[0].intValue(), record, 0);
                    System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);
                    if (!index.addi(key, ds[0].intValue())) fail++;
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
            /* try {
                assert file.size() == index.size() + doubles.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size() + ", doubles.size() = " + doubles.size() + ", fail = " + fail + ", i = " + i;
            } catch (IOException e) {
                e.printStackTrace();
            }*/
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
    
    /**
     * a KeyIterator
     * @param file: the eco-file
     * @param rowdef: the row definition
     * @throws FileNotFoundException 
     * @return an iterator for all keys in the file
     */
    public Iterator<byte[]> keyIterator(final File file, final kelondroRow rowdef) throws FileNotFoundException {
        assert rowdef.primaryKeyIndex == 0;
        return new kelondroEcoFS.ChunkIterator(file, rowdef.objectsize, rowdef.primaryKeyLength);
    }
    
    public static long tableSize(final File tablefile, final int recordsize) {
        // returns number of records in table
        return kelondroEcoFS.tableSize(tablefile, recordsize);
    }

    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return tableTracker.keySet().iterator();
    }

    public static final Map<String, String> memoryStats(final String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record objects,
        // i.e. for cache memory allocation
        final kelondroEcoTable theEcoTable = tableTracker.get(filename);
        return theEcoTable.memoryStats();
    }

    private final Map<String, String> memoryStats() {
        // returns statistical data about this object
        assert ((table == null) || (table.size() == index.size()));
        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("tableSize", Integer.toString(index.size()));
        map.put("tableKeyChunkSize", Integer.toString(index.row().objectsize));
        map.put("tableKeyMem", Integer.toString((int) (index.row().objectsize * index.size() * kelondroRowCollection.growfactor)));
        map.put("tableValueChunkSize", (table == null) ? "0" : Integer.toString(table.row().objectsize));
        map.put("tableValueMem", (table == null) ? "0" : Integer.toString((int) (table.row().objectsize * table.size() * kelondroRowCollection.growfactor)));
        return map;
    }
    
    public boolean usesFullCopy() {
        return this.table != null;
    }
    
    public static int staticRAMIndexNeed(final File f, final kelondroRow rowdef) {
        return (int) ((rowdef.primaryKeyLength + 4) * tableSize(f, rowdef.objectsize) * kelondroRowCollection.growfactor);
    }
    
    public synchronized boolean addUnique(final Entry row) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        final int i = (int) file.size();
        final boolean added = index.addi(row.getPrimaryKeyBytes(), i);
        if (!added) return false;
        if (table != null) {
            assert table.size() == i;
            table.addUnique(taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
        }
        file.add(row.bytes(), 0);
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        return true;
    }

    public synchronized int addUniqueMultiple(final List<Entry> rows) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        final Iterator<Entry> i = rows.iterator();
        int c = 0;
        while (i.hasNext()) {
            if (addUnique(i.next())) c++;
        }
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        return c;
    }

    /**
     * remove double-entries from the table
     * this process calls the underlying removeDoubles() method from the table index
     * and 
     */
    public synchronized ArrayList<kelondroRowCollection> removeDoubles() throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        final ArrayList<kelondroRowCollection> report = new ArrayList<kelondroRowCollection>();
        kelondroRowSet rows;
        final TreeSet<Integer> d = new TreeSet<Integer>();
        final byte[] b = new byte[rowdef.objectsize];
        Integer L;
        kelondroRow.Entry inconsistentEntry;
        // iterate over all entries that have inconsistent index references
        for (final Integer[] is: index.removeDoubles()) {
            // 'is' is the set of all indexes, that have the same reference
            // we collect that entries now here
            rows = new kelondroRowSet(this.rowdef, is.length);
            for (int j = 0; j < is.length; j++) {
                L = is[j];
                assert L.intValue() < file.size() : "L.intValue() = " + L.intValue() + ", file.size = " + file.size(); // prevent ooBounds Exception
                d.add(L);
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
        }
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        return report;
    }
    
    public void close() {
        file.close();
        file = null;
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
        final int i = index.geti(key);
        if (i == -1) return null;
        final byte[] b = new byte[rowdef.objectsize];
        if (table == null) {
            // read row from the file
            file.get(i, b, 0);
        } else {
            // construct the row using the copy in RAM
            final kelondroRow.Entry v = table.get(i, false);
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

    public synchronized kelondroCloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return index.keys(up, firstKey);
    }

    public kelondroProfile profile() {
        return null;
    }

    public synchronized Entry put(final Entry row) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        assert row != null;
        assert row.bytes() != null;
        if ((row == null) || (row.bytes() == null)) return null;
        final int i = index.geti(row.getPrimaryKeyBytes());
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
            final kelondroRow.Entry v = table.get(i, false);
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

    public synchronized Entry put(final Entry row, final Date entryDate) throws IOException {
        return put(row);
    }

    public synchronized void putMultiple(final List<Entry> rows) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        final Iterator<Entry> i = rows.iterator();
        while (i.hasNext()) {
            put(i.next());
        }
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
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
                index.puti(k, i);
            }
        } else {
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                table.removeRow(i, false);
                file.cleanLast();
            } else {
                // switch values
                final kelondroRow.Entry te = table.removeOne();
                table.set(i, te);

                file.cleanLast(p, 0);
                file.put(i, p, 0);
                final kelondroRow.Entry lr = rowdef.newEntry(p);
                index.puti(lr.getPrimaryKeyBytes(), i);
            }
        }
    }
    
    public synchronized Entry remove(final byte[] key) throws IOException {
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        assert key.length == rowdef.primaryKeyLength;
        final int i = index.geti(key);
        if (i == -1) return null; // nothing to do
        
        // prepare result
        final byte[] b = new byte[rowdef.objectsize];
        final byte[] p = new byte[rowdef.objectsize];
        final int sb = index.size();
        int ix;
        assert i < index.size();
        if (table == null) {
            if (i == index.size() - 1) {
                ix = index.removei(key);
                assert ix == i;
                file.cleanLast(b, 0);
            } else {
                assert i < index.size() - 1;
                ix = index.removei(key);
                assert ix == i;
                file.get(i, b, 0);
                file.cleanLast(p, 0);
                file.put(i, p, 0);
                final byte[] k = new byte[rowdef.primaryKeyLength];
                System.arraycopy(p, 0, k, 0, rowdef.primaryKeyLength);
                index.puti(k, i);
            }
            assert (file.size() == index.size() + fail);
        } else {
            // get result value from the table copy, so we don't need to read it from the file
            final kelondroRow.Entry v = table.get(i, false);
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, taildef.objectsize);
            
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                ix = index.removei(key);
                assert ix == i;
                table.removeRow(i, false);
                file.cleanLast();
            } else {
                // switch values
                ix = index.removei(key);
                assert ix == i;
                
                final kelondroRow.Entry te = table.removeOne();
                table.set(i, te);

                file.cleanLast(p, 0);
                file.put(i, p, 0);
                final kelondroRow.Entry lr = rowdef.newEntry(p);
                index.puti(lr.getPrimaryKeyBytes(), i);
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
        final kelondroRow.Entry lr = rowdef.newEntry(le);
        final int i = index.removei(lr.getPrimaryKeyBytes());
        assert i >= 0;
        if (table != null) table.removeOne();
        assert file.size() == index.size() + fail : "file.size() = " + file.size() + ", index.size() = " + index.size();
        return lr;
    }

    public void clear() throws IOException {
        final File f = file.filename();
        file.close();
        f.delete();
        
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
            this.file = new kelondroBufferedEcoFS(new kelondroEcoFS(f, rowdef.objectsize), this.buffersize);
        } catch (final FileNotFoundException e) {
            // should never happen
            e.printStackTrace();
        }
        
        // initialize index and copy table
        table = new kelondroRowSet(taildef, 1);
        index = new kelondroBytesIntMap(rowdef.primaryKeyLength, rowdef.objectOrder, 1);        
    }

    public kelondroRow row() {
        return this.rowdef;
    }

    public synchronized int size() {
        return index.size();
    }


    public synchronized kelondroCloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return new rowIterator(up, firstKey);
    }

    public class rowIterator implements kelondroCloneableIterator<Entry> {
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
        
        public kelondroCloneableIterator<Entry> clone(final Object modifier) {
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
            try {
                this.c = index.geti(k);
            } catch (final IOException e) {
                e.printStackTrace();
                return null;
            }
            assert this.c >= 0;
            if (this.c < 0) return null;
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
                final kelondroRow.Entry v = table.get(this.c, false);
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
    
    public static kelondroIndex testTable(final File f, final String testentities, final int testcase) throws IOException {
        if (f.exists()) f.delete();
        final kelondroRow rowdef = new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0);
        final kelondroIndex tt = new kelondroEcoTable(f, rowdef, testcase, 100, 0);
        byte[] b;
        final kelondroRow.Entry row = rowdef.newEntry();
        for (int i = 0; i < testentities.length(); i++) {
            b = kelondroTree.testWord(testentities.charAt(i));
            row.setCol(0, b);
            row.setCol(1, b);
            tt.put(row);
        }
        return tt;
    }
    
   public static void bigtest(final int elements, final File testFile, final int testcase) {
        System.out.println("starting big test with " + elements + " elements:");
        final long start = System.currentTimeMillis();
        final String[] s = kelondroTree.permutations(elements);
        kelondroIndex tt;
        try {
            for (int i = 0; i < s.length; i++) {
                System.out.println("*** probing tree " + i + " for permutation " + s[i]);
                // generate tree and delete elements
                tt = testTable(testFile, s[i], testcase);
                if (kelondroTree.countElements(tt) != tt.size()) {
                    System.out.println("wrong size for " + s[i]);
                }
                tt.close();
                for (int j = 0; j < s.length; j++) {
                    tt = testTable(testFile, s[i], testcase);
                    // delete by permutation j
                    for (int elt = 0; elt < s[j].length(); elt++) {
                        tt.remove(kelondroTree.testWord(s[j].charAt(elt)));
                        if (kelondroTree.countElements(tt) != tt.size()) {
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

}
