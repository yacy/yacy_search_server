// kelondroFlexTable.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 01.06.2006 on http://www.anomic.de
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;

public class kelondroFlexTable extends kelondroFlexWidthArray implements kelondroIndex {

    // static tracker objects
    private static TreeMap tableTracker = new TreeMap();
    
    // class objects
    protected kelondroBytesIntMap ROindex, RWindex;
    private boolean RAMIndex;
    
    public kelondroFlexTable(File path, String tablename, long preloadTime, kelondroRow rowdef, boolean resetOnFail) {
    	// the buffersize applies to a possible load of the ram-index
    	// if the ram is not sufficient, a tree file is generated
    	// if, and only if a tree file exists, the preload time is applied
    	super(path, tablename, rowdef, resetOnFail);
    	try {
    	long neededRAM = (long) ((super.row().column(0).cellwidth() + 4) * super.size() * kelondroRowCollection.growfactor);
    	
    	File newpath = new File(path, tablename);
        File indexfile = new File(newpath, "col.000.index");
        kelondroIndex ki = null;
        String description = "";
        description = new String(this.col[0].getDescription());
        int p = description.indexOf(';', 4);
        long stt = (p > 0) ? Long.parseLong(description.substring(4, p)) : 0;
        System.out.println("*** Last Startup time: " + stt + " milliseconds");
        long start = System.currentTimeMillis();

        if (serverMemory.available(neededRAM, true)) {
        	// we can use a RAM index
        	
        	if (indexfile.exists()) {
                // delete existing index file
                System.out.println("*** Delete File index " + indexfile);
                indexfile.delete();
        	}
        	
        	// fill the index
            System.out.print("*** Loading RAM index for " + size() + " entries from "+ newpath);
            ki = initializeRamIndex();
            
            System.out.println(" -done-");
            System.out.println(ki.size()
                    + " index entries initialized and sorted from "
                    + super.col[0].size() + " keys.");
            RAMIndex = true;
            ROindex = new kelondroBytesIntMap(ki);
            RWindex = new kelondroBytesIntMap(new kelondroRowSet(new kelondroRow(new kelondroColumn[]{super.row().column(0), new kelondroColumn("int c-4 {b256}")}, super.rowdef.objectOrder, super.rowdef.primaryKey), 100));
            tableTracker.put(this.filename(), this);
        } else {
            // too less ram for a ram index
            if (indexfile.exists()) {
                // use existing index file
                System.out.println("*** Using File index " + indexfile);
                ki = new kelondroCache(kelondroTree.open(indexfile, true, preloadTime, treeIndexRow(rowdef.width(0), rowdef.objectOrder, rowdef.primaryKey), 2, 80), true, false);
                RAMIndex = false;
            } else {
                // generate new index file
                System.out.println("*** Generating File index for " + size() + " entries from " + indexfile);
                System.out.println("*** Cause: too less RAM (" + serverMemory.available() + " Bytes) configured. Assign at least " + (neededRAM / 1024 / 1024) + " MB more RAM to enable a RAM index.");
                ki = initializeTreeIndex(indexfile, preloadTime, rowdef.objectOrder, rowdef.primaryKey);

                System.out.println(" -done-");
                System.out.println(ki.size() + " entries indexed from " + super.col[0].size() + " keys.");
                RAMIndex = false;
            }
            ROindex = null;
            RWindex = new kelondroBytesIntMap(ki);
        }
        // assign index to wrapper
        description = "stt=" + Long.toString(System.currentTimeMillis() - start) + ";";
        super.col[0].setDescription(description.getBytes());
    	} catch (IOException e) {
    		if (resetOnFail) {
    			RAMIndex = true;
    	    	ROindex = null;
    	        try {
					RWindex = new kelondroBytesIntMap(new kelondroRowSet(new kelondroRow(new kelondroColumn[]{super.row().column(0), new kelondroColumn("int c-4 {b256}")}, super.rowdef.objectOrder, super.rowdef.primaryKey), 100));
				} catch (IOException e1) {
					throw new kelondroException(e1.getMessage());
				}
    		} else {
    			throw new kelondroException(e.getMessage());
    		}
    	}
    }
    
    public void reset() throws IOException {
    	super.reset();
    	RAMIndex = true;
    	ROindex = null;
        RWindex = new kelondroBytesIntMap(new kelondroRowSet(new kelondroRow(new kelondroColumn[]{super.row().column(0), new kelondroColumn("int c-4 {b256}")}, super.rowdef.objectOrder, super.rowdef.primaryKey), 100));
    }
    
    public static int staticSize(File path, String tablename) {
        return kelondroFlexWidthArray.staticsize(path, tablename);
    }
    
    public static int staticRAMIndexNeed(File path, String tablename, kelondroRow rowdef) {
        return (int) ((rowdef.column(0).cellwidth() + 4) * staticSize(path, tablename) * kelondroRowSet.growfactor);
    }
    
    public boolean hasRAMIndex() {
        return RAMIndex;
    }
    
    public boolean has(byte[] key) throws IOException {
        // it is not recommended to implement or use a has predicate unless
        // it can be ensured that it causes no IO
        if ((kelondroRecords.debugmode) && (RAMIndex != true)) serverLog.logWarning("kelondroFlexTable", "RAM index warning in file " + super.tablename);
        return (RWindex.geti(key) >= 0) || ((ROindex != null) && (ROindex.geti(key) >= 0));
    }
    
    private kelondroIndex initializeRamIndex() {
        kelondroRowSet ri = new kelondroRowSet(new kelondroRow(new kelondroColumn[]{super.row().column(0), new kelondroColumn("int c-4 {b256}")}, super.rowdef.objectOrder, super.rowdef.primaryKey), super.col[0].size() + 1);
        Iterator content = super.col[0].contentNodes(-1);
        kelondroRecords.Node node;
        kelondroRow.Entry indexentry;
        int i;
        byte[] key;
        while (content.hasNext()) {
            node = (kelondroRecords.Node) content.next();
            i = node.handle().hashCode();
            key = node.getKey();
            assert (key != null) : "DEBUG: empty key in initializeRamIndex"; // should not happen; if it does, it is an error of the condentNodes iterator
            indexentry = ri.row().newEntry();
            indexentry.setCol(0, key);
            indexentry.setCol(1, i);
            ri.addUnique(indexentry);
            if ((i % 10000) == 0) {
                System.out.print('.');
                System.out.flush();
            }
        }
        System.out.print(" -ordering- ");
        System.out.flush();
        ri.sort();
        return ri;
    }
    
    private kelondroIndex initializeTreeIndex(File indexfile, long preloadTime, kelondroOrder objectOrder, int primaryKey) throws IOException {
        kelondroIndex treeindex = new kelondroCache(new kelondroTree(indexfile, true, preloadTime, treeIndexRow(rowdef.width(0), objectOrder, primaryKey), 2, 80), true, false);
        Iterator content = super.col[0].contentNodes(-1);
        kelondroRecords.Node node;
        kelondroRow.Entry indexentry;
        int i, c = 0, all = super.col[0].size();
        long start = System.currentTimeMillis();
        long last = start;
        while (content.hasNext()) {
            node = (kelondroRecords.Node) content.next();
            i = node.handle().hashCode();
            indexentry = treeindex.row().newEntry();
            indexentry.setCol(0, node.getValueRow());
            indexentry.setCol(1, i);
            treeindex.addUnique(indexentry);
            c++;
            if (System.currentTimeMillis() - last > 30000) {
                System.out.println(".. generated " + c + "/" + all + " entries, " + ((System.currentTimeMillis() - start) / c * (all - c) / 60000) + " minutes remaining");
                System.out.flush();
                last = System.currentTimeMillis();
            }
        }
        return treeindex;
    }
    
    private static final kelondroRow treeIndexRow(int keywidth, kelondroOrder objectOrder, int primaryKey) {
        return new kelondroRow("byte[] key-" + keywidth + ", int reference-4 {b256}", objectOrder, primaryKey);
    }
    
    public synchronized kelondroRow.Entry get(byte[] key) throws IOException {
            int pos = RWindex.geti(key);
            if ((pos < 0) && (ROindex != null)) pos = ROindex.geti(key);
            if (pos < 0) return null;
            // i may be greater than this.size(), because this table may have deleted entries
            // the deleted entries are subtracted from the 'real' tablesize, so the size may be
            // smaller than an index to a row entry
            return super.get(pos);
    }
    
    public synchronized void putMultiple(List rows, Date entryDate) throws IOException {
        // put a list of entries in a ordered way.
        // this should save R/W head positioning time
        Iterator i = rows.iterator();
        kelondroRow.Entry row;
        int pos;
        byte[] key;
        TreeMap   old_rows_ordered    = new TreeMap();
        ArrayList new_rows_sequential = new ArrayList();
        while (i.hasNext()) {
            row = (kelondroRow.Entry) i.next();
            key = row.getColBytes(0);
            pos = RWindex.geti(key);
            if ((pos < 0) && (ROindex != null)) pos = ROindex.geti(key);
            if (pos < 0) {
                new_rows_sequential.add(row);
            } else {
                old_rows_ordered.put(new Integer(pos), row);
            }
        }
        // overwrite existing entries in index
        super.setMultiple(old_rows_ordered);
        
        // write new entries to index
        addUniqueMultiple(new_rows_sequential, entryDate);
    }

    public synchronized kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        return put(row);
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        assert (row != null);
        assert (!(serverLog.allZero(row.getColBytes(0))));
        assert row.objectsize() <= this.rowdef.objectsize;
        byte[] key = row.getColBytes(0);
        int pos = RWindex.geti(key);
        if ((pos < 0) && (ROindex != null)) pos = ROindex.geti(key);
        if (pos < 0) {
            RWindex.puti(key, super.add(row));
            return null;
        }
        kelondroRow.Entry oldentry = super.get(pos);
        super.set(pos, row);
        return oldentry;
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) throws IOException {
        addUnique(row);
    }
    
    public synchronized void addUnique(kelondroRow.Entry row) throws IOException {
        assert row.objectsize() == this.rowdef.objectsize;
        RWindex.addi(row.getColBytes(0), super.add(row));
    }
    
    public synchronized void addUniqueMultiple(List rows, Date entryDate) throws IOException {
        // add a list of entries in a ordered way.
        // this should save R/W head positioning time
        TreeMap indexed_result = super.addMultiple(rows);
        // indexed_result is a Integer/byte[] relation
        // that is used here to store the index
        Iterator i = indexed_result.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            RWindex.puti((byte[]) entry.getValue(), ((Integer) entry.getKey()).intValue());
        }
    }
    
    public synchronized kelondroRow.Entry remove(byte[] key) throws IOException {
        int i = RWindex.removei(key);
        if ((i < 0) && (ROindex != null)) i = ROindex.removei(key); // yes, we are allowed to remove entries from RO partition of the index
        if (i < 0) return null;
        kelondroRow.Entry r;
        r = super.get(i);
        super.remove(i, false);
        return r;
    }

    public synchronized kelondroRow.Entry removeOne() throws IOException {
        int i = RWindex.removeonei();
        if ((i < 0) && (ROindex != null)) i = ROindex.removeonei();
        if (i < 0) return null;
        kelondroRow.Entry r;
        r = super.get(i);
        super.remove(i, false);
        return r;
    }
    
    public synchronized kelondroCloneableIterator rows(boolean up, byte[] firstKey) throws IOException {
        if (ROindex == null) return new rowIterator(RWindex, up, firstKey);
        if (RWindex == null) return new rowIterator(ROindex, up, firstKey);
        return new kelondroMergeIterator(
                new rowIterator(ROindex, up, firstKey),
                new rowIterator(RWindex, up, firstKey),
                row().objectOrder,
                kelondroMergeIterator.simpleMerge,
                up
                );
    }
    
    public class rowIterator implements kelondroCloneableIterator {

        kelondroCloneableIterator indexIterator;
        kelondroBytesIntMap index;
        boolean up;
        
        public rowIterator(kelondroBytesIntMap index, boolean up, byte[] firstKey) throws IOException {
            this.index = index;
            this.up = up;
            indexIterator = index.rows(up, firstKey);
        }
        
        public Object clone(Object modifier) {
            try {
                return new rowIterator(index, up, (byte[]) modifier);
            } catch (IOException e) {
                return null;
            }
        }
        
        public boolean hasNext() {
            return indexIterator.hasNext();
        }

        public Object next() {
            kelondroRow.Entry idxEntry = null;
            while ((indexIterator.hasNext()) && (idxEntry == null)) {
                idxEntry = (kelondroRow.Entry) indexIterator.next();
            }
            if (idxEntry == null) {
                serverLog.logSevere("kelondroFlexTable.rowIterator: " + tablename, "indexIterator returned null");
                return null;
            }
            int idx = (int) idxEntry.getColLong(1);
            try {
                return get(idx);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public void remove() {
            indexIterator.remove();
        }
        
    }
    
    public kelondroProfile profile() {
        if (ROindex == null) {
            return RWindex.profile();
        } else {
            return kelondroProfile.consolidate(ROindex.profile(), RWindex.profile());
        }
    }
    
    public static final Iterator filenames() {
        // iterates string objects; all file names from record tracker
        return tableTracker.keySet().iterator();
    }

    public static final kelondroProfile profileStats(String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record oobjects,
        // i.e. for cache memory allocation
        kelondroFlexTable theFlexTable = (kelondroFlexTable) tableTracker.get(filename);
        return theFlexTable.profile();
    }
    
    public static final Map memoryStats(String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record oobjects,
        // i.e. for cache memory allocation
        kelondroFlexTable theFlexTable = (kelondroFlexTable) tableTracker.get(filename);
        return theFlexTable.memoryStats();
    }
    
    private final Map memoryStats() {
        // returns statistical data about this object
        HashMap map = new HashMap();
        try {
            map.put("tableIndexChunkSize", (!RAMIndex) ? "0" : Integer.toString(RWindex.row().objectsize));
            map.put("tableROIndexCount", ((!RAMIndex) || (ROindex == null)) ? "0" : Integer.toString(ROindex.size()));
            map.put("tableROIndexMem", ((!RAMIndex) || (ROindex == null)) ? "0" : Integer.toString((int) (ROindex.row().objectsize * ROindex.size())));
            map.put("tableRWIndexCount", (!RAMIndex) ? "0" : Integer.toString(RWindex.size()));
            map.put("tableRWIndexMem", (!RAMIndex) ? "0" : Integer.toString((int) (RWindex.row().objectsize * RWindex.size() * kelondroRowCollection.growfactor)));
        } catch (IOException e) {
        }
        return map;
    }
    
    public synchronized void close() {
        if (tableTracker.remove(this.filename) == null) {
            serverLog.logWarning("kelondroFlexTable", "close(): file '" + this.filename + "' was not tracked with record tracker.");
        }
        if (ROindex != null) {ROindex.close(); ROindex = null;}
        if (RWindex != null) {RWindex.close(); RWindex = null;}
        super.close();
    }
    
    public static void main(String[] args) {
        // open a file, add one entry and exit
        File f = new File(args[0]);
        String name = args[1];
        kelondroRow row = new kelondroRow("Cardinal key-4 {b256}, byte[] x-64", kelondroNaturalOrder.naturalOrder, 0);
        try {
            kelondroFlexTable t = new kelondroFlexTable(f, name, 0, row, true);
            kelondroRow.Entry entry = row.newEntry();
            entry.setCol(0, System.currentTimeMillis());
            entry.setCol(1, "dummy".getBytes());
            t.put(entry);
            t.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
