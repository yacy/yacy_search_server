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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
 * 
 */

public class kelondroEcoTable implements kelondroIndex {

    // static tracker objects
    private static TreeMap<String, kelondroEcoTable> tableTracker = new TreeMap<String, kelondroEcoTable>();
    
    private kelondroRowSet table;
    private kelondroBytesIntMap index;
    private kelondroBufferedEcoFS file;
    private kelondroRow rowdef, taildef;
    private int buffersize;
    
    public kelondroEcoTable(File tablefile, kelondroRow rowdef, boolean useTailCache, int buffersize) {
        this.rowdef = rowdef;
        this.buffersize = buffersize;
        assert rowdef.primaryKeyIndex == 0;
        // define the taildef, a row like the rowdef but without the first column
        kelondroColumn[] cols = new kelondroColumn[rowdef.columns() - 1];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = rowdef.column(i + 1);
        }
        this.taildef = new kelondroRow(cols, kelondroNaturalOrder.naturalOrder, rowdef.primaryKeyIndex);
        
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
            int records = file.size();
            long neededRAM4table = 10 * 1024 * 1024 + records * (rowdef.objectsize + 4) * 3 / 2;
            table = ((useTailCache) && (serverMemory.request(neededRAM4table, true))) ? new kelondroRowSet(taildef, records + 1) : null;
            index = new kelondroBytesIntMap(rowdef.primaryKeyLength, rowdef.objectOrder, records + 1);
        
            // read all elements from the file into the copy table
            byte[] record = new byte[rowdef.objectsize];
            byte[] key = new byte[rowdef.primaryKeyLength];
            for (int i = 0; i < records; i++) {
                // read entry
                file.get(i, record, 0);
            
                // write the key into the index table
                System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);
                index.addi(key, i);
            
                // write the tail into the table
                if (table != null) table.addUnique(taildef.newEntry(record, rowdef.primaryKeyLength, true));
            }
        } catch (FileNotFoundException e) {
            // should never happen
            e.printStackTrace();
            throw new kelondroException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new kelondroException(e.getMessage());
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
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("tableIndexChunkSize", Integer.toString(index.row().objectsize));
        map.put("tableIndexCount", Integer.toString(index.size()));
        map.put("tableIndexMem", Integer.toString((int) (index.row().objectsize * index.size() * kelondroRowCollection.growfactor)));
        map.put("tableTailChunkSize", (table == null) ? "0" : Integer.toString(table.row().objectsize));
        map.put("tableTailCount", (table == null) ? "0" : Integer.toString(table.size()));
        map.put("tableTailMem", (table == null) ? "0" : Integer.toString((int) (table.row().objectsize * table.size() * kelondroRowCollection.growfactor)));
        return map;
    }
    
    public static int staticRAMIndexNeed(File f, kelondroRow rowdef) {
        return (int) ((rowdef.primaryKeyLength + 4) * tableSize(f, rowdef.objectsize) * kelondroRowSet.growfactor);
    }
    
    public synchronized void addUnique(Entry row) throws IOException {
        assert (file.size() == index.size());
        assert ((table == null) || (table.size() == index.size()));
        int i = file.size();
        index.addi(row.getPrimaryKeyBytes(), i);
        if (table != null) {
            assert table.size() == i;
            table.addUnique(taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
        }
        file.put(i, row.bytes(), 0);
    }

    public synchronized void addUniqueMultiple(List<Entry> rows) throws IOException {
        Iterator<Entry> i = rows.iterator();
        while (i.hasNext()) {
            addUnique(i.next());
        }
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
        assert (file.size() == index.size());
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
        assert (file.size() == index.size());
        assert ((table == null) || (table.size() == index.size()));
        return rowdef.newEntry(b);
    }

    public synchronized boolean has(byte[] key) throws IOException {
        assert (file.size() == index.size());
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
        assert (file.size() == index.size());
        assert ((table == null) || (table.size() == index.size()));
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
            System.arraycopy(row.getPrimaryKeyBytes(), 0, b, 0, rowdef.primaryKeyLength);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, rowdef.objectsize - rowdef.primaryKeyLength);
            // write new value
            table.set(i, taildef.newEntry(row.bytes(), rowdef.primaryKeyLength, true));
            file.put(i, row.bytes(), 0);
        }
        assert (file.size() == index.size());
        assert ((table == null) || (table.size() == index.size()));
        // return old value
        return rowdef.newEntry(b);
    }

    public synchronized Entry put(Entry row, Date entryDate) throws IOException {
        return put(row);
    }

    public synchronized void putMultiple(List<Entry> rows) throws IOException {
        Iterator<Entry> i = rows.iterator();
        while (i.hasNext()) {
            put(i.next());
        }
    }

    public synchronized Entry remove(byte[] key, boolean keepOrder) throws IOException {
        assert (file.size() == index.size());
        assert ((table == null) || (table.size() == index.size()));
        assert keepOrder == false; // this class cannot keep the order during a remove
        int i = index.geti(key);
        if (i == -1) return null; // nothing to do
        
        // prepare result
        byte[] b = new byte[rowdef.objectsize];
        byte[] p = new byte[rowdef.objectsize];
        if (table == null) {
            index.removei(key);
            file.get(i, b, 0);
            file.cleanLast(p, 0);
            file.put(i, p, 0);
            byte[] k = new byte[rowdef.primaryKeyLength];
            System.arraycopy(p, 0, k, 0, rowdef.primaryKeyLength);
            index.puti(k, i);
            assert (file.size() == index.size());
            assert ((table == null) || (table.size() == index.size()));
        } else {
            kelondroRow.Entry v = table.get(i);
            assert key.length == rowdef.primaryKeyLength;
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(v.bytes(), 0, b, rowdef.primaryKeyLength, taildef.objectsize);
            if (i == index.size() - 1) {
                // special handling if the entry is the last entry in the file
                index.removei(key);
                table.removeRow(i, false);
                file.clean(i);
                assert (file.size() == index.size());
                assert ((table == null) || (table.size() == index.size()));
            } else {
                // switch values
                kelondroRow.Entry te = table.removeOne();
                table.set(i, te);

                file.cleanLast(p, 0);
                file.put(i, p, 0);
                kelondroRow.Entry lr = rowdef.newEntry(p);
                
                index.removei(key);
                index.puti(lr.getPrimaryKeyBytes(), i);
                assert (file.size() == index.size());
                assert ((table == null) || (table.size() == index.size())) : "table.size() = " + table.size() + ", index.size() = " + index.size();
            }
        }
        assert (file.size() == index.size());
        assert ((table == null) || (table.size() == index.size()));
        return rowdef.newEntry(b);
    }

    public synchronized Entry removeOne() throws IOException {
        assert (file.size() == index.size());
        assert ((table == null) || (table.size() == index.size()));
        byte[] le = new byte[rowdef.objectsize];
        file.cleanLast(le, 0);
        kelondroRow.Entry lr = rowdef.newEntry(le);
        int i = index.removei(lr.getPrimaryKeyBytes());
        assert i >= 0;
        table.removeRow(i, false);
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
    
    public static kelondroIndex testTable(File f, String testentities) throws IOException {
        if (f.exists()) f.delete();
        kelondroRow rowdef = new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0);
        kelondroIndex tt = new kelondroEcoTable(f, rowdef, true, 100);
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
    
   public static void bigtest(int elements, File testFile) {
        System.out.println("starting big test with " + elements + " elements:");
        long start = System.currentTimeMillis();
        String[] s = kelondroTree.permutations(elements);
        kelondroIndex tt;
        try {
            for (int i = 0; i < s.length; i++) {
                System.out.println("*** probing tree " + i + " for permutation " + s[i]);
                // generate tree and delete elements
                tt = testTable(testFile, s[i]);
                if (kelondroTree.countElements(tt) != tt.size()) {
                    System.out.println("wrong size for " + s[i]);
                }
                tt.close();
                for (int j = 0; j < s.length; j++) {
                    tt = testTable(testFile, s[i]);
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
        bigtest(5, f);
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
