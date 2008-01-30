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
 * The EcoIndex builts upon the EcoFS and tries to reduce the number of IO requests that the
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
    
    public static final long maxarraylength = 134217727; // that may be the maxmimum size of array length in some JVMs
    
    private kelondroRowSet table;
    private kelondroBytesIntMap index;
    private kelondroBufferedEcoFS file;
    private kelondroRow rowdef, taildef;
    private int buffersize;
    
    public kelondroEcoTable(File tablefile, kelondroRow rowdef, int useTailCache, int buffersize, int initialSpace) {
        this.rowdef = rowdef;
        this.buffersize = buffersize;
        assert rowdef.primaryKeyIndex == 0;
        // define the taildef, a row like the rowdef but without the first column
        kelondroColumn[] cols = new kelondroColumn[rowdef.columns() - 1];
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
            } catch (FileNotFoundException e) {
                // should not happen
                e.printStackTrace();
            }
            try { fos.close(); } catch (IOException e) {}
        }
        
        try {
            // open an existing table file
            this.file = new kelondroBufferedEcoFS(new kelondroEcoFS(tablefile, rowdef.objectsize), this.buffersize);
        
            // initialize index and copy table
            int  records = (int) Math.max(file.size(), initialSpace);
            long neededRAM4table = 200 * 1024 * 1024 + records * (rowdef.objectsize + 4) * 3 / 2;
            table = ((neededRAM4table < maxarraylength) &&
                     ((useTailCache == tailCacheForceUsage) ||
                      ((useTailCache == tailCacheUsageAuto) && (serverMemory.request(neededRAM4table, false))))) ?
                    new kelondroRowSet(taildef, records) : null;
            System.out.println("*** DEBUG " + tablefile + ": available RAM: " + (serverMemory.available() / 1024 / 1024) + "MB, allocating space for " + records + " entries");
            long neededRAM4index = 200 * 1024 * 1024 + records * (rowdef.primaryKeyLength + 4) * 3 / 2;
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
            byte[] record = new byte[rowdef.objectsize];
            byte[] key = new byte[rowdef.primaryKeyLength];
            int fs = (int) file.size();
            System.out.print("*** initializing RAM index for EcoTable " + tablefile.getName() + ":");
            for (int i = 0; i < fs; i++) {
                // read entry
                file.get(i, record, 0);
            
                // write the key into the index table
                System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);
                index.addi(key, i);
            
                // write the tail into the table
                if (table != null) table.addUnique(taildef.newEntry(record, rowdef.primaryKeyLength, true));
                
                if ((i % 10000) == 0) {
                    System.out.print('.');
                    System.out.flush();
                }
            }
            System.out.print(" -ordering- ..");
            System.out.flush();
            // check consistency
            ArrayList<Integer[]> doubles = index.removeDoubles();
            System.out.println(" -removed " + doubles.size() + " doubles- done.");
            if (doubles.size() > 0) {
                System.out.println("DEBUG " + tablefile + ": WARNING - EcoTable " + tablefile + " has " + doubles.size() + " doubles");
                // from all the doubles take one, put it back to the index and remove the others from the file
                Iterator<Integer[]> i = doubles.iterator();
                Integer[] ds;
                // first put back one element each
                while (i.hasNext()) {
                    ds = i.next();
                    file.get(ds[0].longValue(), record, 0);
                    System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);
                    index.addi(key, ds[0].intValue());
                }
                // then remove the other doubles by removing them from the table, but do a re-indexing while doing that
                // first aggregate all the delete positions because the elements from the top positions must be removed first
                i = doubles.iterator();
                TreeSet<Integer> delpos = new TreeSet<Integer>();
                while (i.hasNext()) {
                    ds = i.next();
                    for (int j = 1; j < ds.length; j++) {
                        delpos.add(ds[j]);
                    }
                }
                // now remove the entries in a sorted way (top-down)
                Integer top;
                while (delpos.size() > 0) {
                    top = delpos.last();
                    delpos.remove(top);
                    removeInFile(top.intValue());
                }
            }
        } catch (FileNotFoundException e) {
            // should never happen
            e.printStackTrace();
            throw new kelondroException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new kelondroException(e.getMessage());
        }
        try {
            assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // track this table
        tableTracker.put(tablefile.toString(), this);
    }
    
    public static long tableSize(File tablefile, int recordsize) {
        // returns number of records in table
        return kelondroEcoFS.tableSize(tablefile, recordsize);
    }

    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return tableTracker.keySet().iterator();
    }

    public static final Map<String, String> memoryStats(String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record objects,
        // i.e. for cache memory allocation
        kelondroEcoTable theEcoTable = tableTracker.get(filename);
        return theEcoTable.memoryStats();
    }

    private final Map<String, String> memoryStats() {
        // returns statistical data about this object
        assert ((table == null) || (table.size() == index.size()));
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("tableSize", Integer.toString(index.size()));
        map.put("tableKeyChunkSize", Integer.toString(index.row().objectsize));
        map.put("tableKeyMem", Integer.toString((int) (index.row().objectsize * index.size() * kelondroRowCollection.growfactor)));
        map.put("tableValueChunkSize", (table == null) ? "0" : Integer.toString(table.row().objectsize));
        map.put("tableValueMem", (table == null) ? "0" : Integer.toString((int) (table.row().objectsize * table.size() * kelondroRowCollection.growfactor)));
        return map;
    }
    
    public static int staticRAMIndexNeed(File f, kelondroRow rowdef) {
        return (int) ((rowdef.primaryKeyLength + 4) * tableSize(f, rowdef.objectsize) * kelondroRowSet.growfactor);
    }
    
    public synchronized void addUnique(Entry row) throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        int i = (int) file.size();
        index.addi(row.getPrimaryKeyBytes(), i);
        if (table != null) {
            assert table.size() == i;
            table.addUnique(taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
        }
        file.put(i, row.bytes(), 0);
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
    }

    public synchronized void addUniqueMultiple(List<Entry> rows) throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        Iterator<Entry> i = rows.iterator();
        while (i.hasNext()) {
            addUnique(i.next());
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
    }

    public synchronized ArrayList<kelondroRowSet> removeDoubles() throws IOException {
        ArrayList<Integer[]> indexreport = index.removeDoubles();
        ArrayList<kelondroRowSet> report = new ArrayList<kelondroRowSet>();
        Iterator<Integer[]> i = indexreport.iterator();
        Integer[] is;
        kelondroRowSet rows;
        TreeSet<Integer> d = new TreeSet<Integer>();
        byte[] b = new byte[rowdef.objectsize];
        while (i.hasNext()) {
            is = i.next();
            rows = new kelondroRowSet(this.rowdef, is.length);
            for (int j = 0; j < is.length; j++) {
                d.add(is[j]);
                file.get(is[j].intValue(), b, 0);
                rows.addUnique(rowdef.newEntry(b));
            }
            report.add(rows);
        }
        // finally delete the affected rows, but start with largest id first, othervise we overwrite wrong entries
        Integer s;
        while (d.size() > 0) {
            s = d.last();
            d.remove(s);
            this.removeInFile(s.intValue());
        }
        return report;
    }
    
    public void close() {
        file.close();
        file = null;
    }
    
    public void finalize() {
        if (this.file != null) this.close();
    }

    public String filename() {
        return this.file.filename().toString();
    }
    
    public synchronized Entry get(byte[] key) throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        int i = index.geti(key);
        if (i == -1) return null;
        byte[] b = new byte[rowdef.objectsize];
        if (table == null) {
            // read row from the file
            file.get(i, b, 0);
        } else {
            // construct the row using the copy in RAM
            kelondroRow.Entry v = table.get(i);
            assert v != null;
            assert key.length == rowdef.primaryKeyLength;
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, rowdef.objectsize - rowdef.primaryKeyLength);
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        return rowdef.newEntry(b);
    }

    public synchronized boolean has(byte[] key) throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        return index.geti(key) >= 0;
    }

    public synchronized kelondroCloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
        return index.keys(up, firstKey);
    }

    public kelondroProfile profile() {
        return null;
    }

    public synchronized Entry put(Entry row) throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        assert row != null;
        assert row.bytes() != null;
        if ((row == null) || (row.bytes() == null)) return null;
        int i = index.geti(row.getPrimaryKeyBytes());
        if (i == -1) {
            addUnique(row);
            return null;
        }
        
        byte[] b = new byte[rowdef.objectsize];
        if (table == null) {
            // read old value
            file.get(i, b, 0);
            // write new value
            file.put(i, row.bytes(), 0);
        } else {
            // read old value
            kelondroRow.Entry v = table.get(i);
            assert v != null;
            System.arraycopy(row.getPrimaryKeyBytes(), 0, b, 0, rowdef.primaryKeyLength);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, rowdef.objectsize - rowdef.primaryKeyLength);
            // write new value
            table.set(i, taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
            file.put(i, row.bytes(), 0);
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        // return old value
        return rowdef.newEntry(b);
    }

    public synchronized Entry put(Entry row, Date entryDate) throws IOException {
        return put(row);
    }

    public synchronized void putMultiple(List<Entry> rows) throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        Iterator<Entry> i = rows.iterator();
        while (i.hasNext()) {
            put(i.next());
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
    }

    private void removeInFile(int i) throws IOException {
        assert i >= 0;
        
        byte[] p = new byte[rowdef.objectsize];
        if (table == null) {
            if (i == index.size() - 1) {
                file.clean(i);
            } else {
                file.cleanLast(p, 0);
                file.put(i, p, 0);
                byte[] k = new byte[rowdef.primaryKeyLength];
                System.arraycopy(p, 0, k, 0, rowdef.primaryKeyLength);
                index.puti(k, i);
            }
        } else {
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                table.removeRow(i, false);
                file.clean(i);
            } else {
                // switch values
                kelondroRow.Entry te = table.removeOne();
                table.set(i, te);

                file.cleanLast(p, 0);
                file.put(i, p, 0);
                kelondroRow.Entry lr = rowdef.newEntry(p);
                index.puti(lr.getPrimaryKeyBytes(), i);
            }
        }
    }
    
    public synchronized Entry remove(byte[] key, boolean keepOrder) throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        assert keepOrder == false; // this class cannot keep the order during a remove
        assert key.length == rowdef.primaryKeyLength;
        int i = index.geti(key);
        if (i == -1) return null; // nothing to do
        
        // prepare result
        byte[] b = new byte[rowdef.objectsize];
        byte[] p = new byte[rowdef.objectsize];
        int sb = index.size();
        if (table == null) {
            if (i == index.size() - 1) {
                index.removei(key);
                file.clean(i, b, 0);
            } else {
                index.removei(key);
                file.get(i, b, 0);
                file.cleanLast(p, 0);
                file.put(i, p, 0);
                byte[] k = new byte[rowdef.primaryKeyLength];
                System.arraycopy(p, 0, k, 0, rowdef.primaryKeyLength);
                index.puti(k, i);
            }
            assert (file.size() == index.size());
        } else {
            // get result value from the table copy, so we don't need to read it from the file
            kelondroRow.Entry v = table.get(i);
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, taildef.objectsize);
            
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                index.removei(key);
                table.removeRow(i, false);
                file.clean(i);
            } else {
                // switch values
                index.removei(key);
                
                kelondroRow.Entry te = table.removeOne();
                table.set(i, te);

                file.cleanLast(p, 0);
                file.put(i, p, 0);
                kelondroRow.Entry lr = rowdef.newEntry(p);
                index.puti(lr.getPrimaryKeyBytes(), i);
            }
            assert (file.size() == index.size());
            assert (table.size() == index.size()) : "table.size() = " + table.size() + ", index.size() = " + index.size();
        }
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        assert index.size() + 1 == sb : "index.size() = " + index.size() + ", sb = " + sb;
        return rowdef.newEntry(b);
    }

    public synchronized Entry removeOne() throws IOException {
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        assert ((table == null) || (table.size() == index.size()));
        byte[] le = new byte[rowdef.objectsize];
        file.cleanLast(le, 0);
        kelondroRow.Entry lr = rowdef.newEntry(le);
        int i = index.removei(lr.getPrimaryKeyBytes());
        assert i >= 0;
        if (table != null) table.removeOne();
        assert file.size() == index.size() : "file.size() = " + file.size() + ", index.size() = " + index.size();
        return lr;
    }

    public void reset() throws IOException {
        File f = file.filename();
        file.close();
        f.delete();
        
        // make new file
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            // should not happen
            e.printStackTrace();
        }
        try { fos.close(); } catch (IOException e) {}
        
        
        // open an existing table file
        try {
            this.file = new kelondroBufferedEcoFS(new kelondroEcoFS(f, rowdef.objectsize), this.buffersize);
        } catch (FileNotFoundException e) {
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


    public synchronized kelondroCloneableIterator<Entry> rows(boolean up, byte[] firstKey) throws IOException {
        return new rowIterator(up, firstKey);
    }

    public class rowIterator implements kelondroCloneableIterator<Entry> {
        Iterator<byte[]> i;
        boolean up;
        byte[] fk;
        int c;
        
        public rowIterator(boolean up, byte[] firstKey) throws IOException {
            this.up = up;
            this.fk = firstKey;
            this.i  = index.keys(up, firstKey);
            this.c = -1;
        }
        
        public kelondroCloneableIterator<Entry> clone(Object modifier) {
            try {
                return new rowIterator(up, fk);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public boolean hasNext() {
            return i.hasNext();
        }

        public Entry next() {
            byte[] k = i.next();
            try {
                this.c = index.geti(k);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            byte[] b = new byte[rowdef.objectsize];
            if (table == null) {
                // read from file
                try {
                    file.get(this.c, b, 0);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                // compose from table and key
                kelondroRow.Entry v = table.get(this.c);
                System.arraycopy(k, 0, b, 0, rowdef.primaryKeyLength);
                System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, taildef.objectsize);
            }
            return rowdef.newEntry(b);
        }

        public void remove() {
            throw new UnsupportedOperationException("no remove in EcoTable");
        }
        
    }
    
    public static kelondroIndex testTable(File f, String testentities, int testcase) throws IOException {
        if (f.exists()) f.delete();
        kelondroRow rowdef = new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0);
        kelondroIndex tt = new kelondroEcoTable(f, rowdef, testcase, 100, 0);
        byte[] b;
        kelondroRow.Entry row = rowdef.newEntry();
        for (int i = 0; i < testentities.length(); i++) {
            b = kelondroTree.testWord(testentities.charAt(i));
            row.setCol(0, b);
            row.setCol(1, b);
            tt.put(row);
        }
        return tt;
    }
    
   public static void bigtest(int elements, File testFile, int testcase) {
        System.out.println("starting big test with " + elements + " elements:");
        long start = System.currentTimeMillis();
        String[] s = kelondroTree.permutations(elements);
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
                        tt.remove(kelondroTree.testWord(s[j].charAt(elt)), false);
                        if (kelondroTree.countElements(tt) != tt.size()) {
                            System.out.println("ERROR! wrong size for probe tree " + s[i] + "; probe delete " + s[j] + "; position " + elt);
                        }
                    }
                    tt.close();
                }
            }
            System.out.println("FINISHED test after " + ((System.currentTimeMillis() - start) / 1000) + " seconds.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("TERMINATED");
        }
    }
    
    public static void main(String[] args) {
        // open a file, add one entry and exit
        File f = new File(args[0]);
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
