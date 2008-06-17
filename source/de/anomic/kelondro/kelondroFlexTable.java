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
import java.util.TreeSet;

import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;

public class kelondroFlexTable extends kelondroFlexWidthArray implements kelondroIndex {

    // static tracker objects
    private static TreeMap<String, kelondroFlexTable> tableTracker = new TreeMap<String, kelondroFlexTable>();
    
    // class objects
    protected kelondroBytesIntMap index;
    private boolean RAMIndex;
    
    public kelondroFlexTable(File path, String tablename, kelondroRow rowdef, int minimumSpace, boolean resetOnFail) {
    	// the buffersize applies to a possible load of the ram-index
        // the minimumSpace is a initial allocation space for the index; names the number of index slots
    	// if the ram is not sufficient, a tree file is generated
    	// if, and only if a tree file exists, the preload time is applied
    	super(path, tablename, rowdef, resetOnFail);
        if ((super.col[0].size() < 0) && (resetOnFail)) try {
            super.reset();
        } catch (IOException e2) {
            e2.printStackTrace();
            throw new kelondroException(e2.getMessage());
        }
        minimumSpace = Math.max(minimumSpace, super.size());
        try {
    	long neededRAM = 10 * 1024 * 104 + (long) ((super.row().column(0).cellwidth + 4) * minimumSpace * kelondroRowCollection.growfactor);
    	
    	File newpath = new File(path, tablename);
        File indexfile = new File(newpath, "col.000.index");
        String description = "";
        description = new String(this.col[0].getDescription());
        int p = description.indexOf(';', 4);
        long stt = (p > 0) ? Long.parseLong(description.substring(4, p)) : 0;
        System.out.println("*** Last Startup time: " + stt + " milliseconds");
        long start = System.currentTimeMillis();

        if (serverMemory.request(neededRAM, false)) {
			// we can use a RAM index
			if (indexfile.exists()) {
				// delete existing index file
				System.out.println("*** Delete File index " + indexfile);
				indexfile.delete();
			}

			// fill the index
			System.out.print("*** Loading RAM index for " + size() + " entries from " + newpath + "; available RAM = " + (serverMemory.available() >> 20) + " MB, allocating " + (neededRAM >> 20) + " MB for index.");
			index = initializeRamIndex(minimumSpace);

			System.out.println(" -done-");
			System.out.println(index.size() + " index entries initialized and sorted from " + super.col[0].size() + " keys.");
			RAMIndex = true;
			tableTracker.put(this.filename(), this);
		} else {
			// too less ram for a ram index
			kelondroIndex ki;
			if (indexfile.exists()) {
				// use existing index file
				System.out.println("*** Using File index " + indexfile);
				ki = new kelondroCache(kelondroTree.open(indexfile, true, 0, treeIndexRow(rowdef.width(0), rowdef.objectOrder), 2, 80));
				RAMIndex = false;
			} else {
				// generate new index file
				System.out.println("*** Generating File index for " + size() + " entries from " + indexfile);
				System.out.println("*** Cause: too less RAM (" + serverMemory.available() + " Bytes) configured. Assign at least " + (neededRAM / 1024 / 1024) + " MB more RAM to enable a RAM index.");
				ki = initializeTreeIndex(indexfile, 0, rowdef.objectOrder);

				System.out.println(" -done-");
				System.out.println(ki.size() + " entries indexed from " + super.col[0].size() + " keys.");
				RAMIndex = false;
			}
			index = new kelondroBytesIntMap(ki);
			assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		}

        // check consistency
        ArrayList<Integer[]> doubles = index.removeDoubles();
        if (doubles.size() > 0) {
            System.out.println("DEBUG: WARNING - FlexTable " + newpath.toString() + " has " + doubles.size() + " doubles");
        }
        
        // assign index to wrapper
        description = "stt=" + Long.toString(System.currentTimeMillis() - start) + ";";
        super.col[0].setDescription(description.getBytes());
    	} catch (IOException e) {
    		if (resetOnFail) {
    			RAMIndex = true;
    	        index = new kelondroBytesIntMap(super.row().column(0).cellwidth, super.rowdef.objectOrder, 0);
    		} else {
    			throw new kelondroException(e.getMessage());
    		}
    	}
    }
    
    public void clear() throws IOException {
    	super.reset();
    	RAMIndex = true;
        index = new kelondroBytesIntMap(super.row().column(0).cellwidth, super.rowdef.objectOrder, 0);
    }
    
    public static int staticSize(File path, String tablename) {
        return kelondroFlexWidthArray.staticsize(path, tablename);
    }
    
    public static int staticRAMIndexNeed(File path, String tablename, kelondroRow rowdef) {
        return (int) ((rowdef.column(0).cellwidth + 4) * staticSize(path, tablename) * kelondroRowCollection.growfactor);
    }
    
    public boolean hasRAMIndex() {
        return RAMIndex;
    }
    
    public synchronized boolean has(byte[] key) {
        // it is not recommended to implement or use a has predicate unless
        // it can be ensured that it causes no IO
        if ((kelondroAbstractRecords.debugmode) && (RAMIndex != true)) serverLog.logWarning("kelondroFlexTable", "RAM index warning in file " + super.tablename);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        return index.has(key);
    }
    
    private kelondroBytesIntMap initializeRamIndex(int initialSpace) {
    	int space = Math.max(super.col[0].size(), initialSpace) + 1;
    	if (space < 0) throw new kelondroException("wrong space: " + space);
        kelondroBytesIntMap ri = new kelondroBytesIntMap(super.row().column(0).cellwidth, super.rowdef.objectOrder, space);
        Iterator<kelondroNode> content = super.col[0].contentNodes(-1);
        kelondroNode node;
        int i;
        byte[] key;
        while (content.hasNext()) {
            node = content.next();
            i = node.handle().hashCode();
            key = node.getKey();
            assert (key != null) : "DEBUG: empty key in initializeRamIndex"; // should not happen; if it does, it is an error of the condentNodes iterator
            //System.out.println("ENTRY: " + serverLog.arrayList(indexentry.bytes(), 0, indexentry.objectsize()));
            try { ri.addi(key, i); } catch (IOException e) {} // no IOException can happen here
            if ((i % 10000) == 0) {
                System.out.print('.');
                System.out.flush();
            }
        }
        System.out.print(" -ordering- ");
        System.out.flush();
        return ri;
    }

    private kelondroIndex initializeTreeIndex(File indexfile, long preloadTime, kelondroByteOrder objectOrder) throws IOException {
		kelondroIndex treeindex = new kelondroCache(new kelondroTree(indexfile, true, preloadTime, treeIndexRow(rowdef.primaryKeyLength, objectOrder), 2, 80));
		Iterator<kelondroNode> content = super.col[0].contentNodes(-1);
		kelondroNode node;
		kelondroRow.Entry indexentry;
		int i, c = 0, all = super.col[0].size();
		long start = System.currentTimeMillis();
		long last = start;
		while (content.hasNext()) {
			node = content.next();
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

	private static final kelondroRow treeIndexRow(int keywidth, kelondroByteOrder objectOrder) {
		return new kelondroRow("byte[] key-" + keywidth + ", int reference-4 {b256}", objectOrder, 0);
	}
    
    public synchronized kelondroRow.Entry get(byte[] key) throws IOException {
        if (index == null) return null; // case may happen during shutdown
		int pos = index.geti(key);
		assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		if (pos < 0) return null;
		// pos may be greater than this.size(), because this table may have deleted entries
		// the deleted entries are subtracted from the 'real' tablesize,
		// so the size may be smaller than an index to a row entry
		/*if (kelondroAbstractRecords.debugmode) {
			kelondroRow.Entry result = super.get(pos);
			assert result != null;
			assert rowdef.objectOrder.compare(result.getPrimaryKeyBytes(), key) == 0 : "key and row does not match; key = " + serverLog.arrayList(key, 0, key.length) + " row.key = " + serverLog.arrayList(result.getPrimaryKeyBytes(), 0, rowdef.primaryKeyLength);
			return result;
		} else {*/
			// assume that the column for the primary key is 0,
			// and the column 0 is stored in a file only for that column
			// then we don't need to lookup from that file, because we already know the value (it's the key)
			kelondroRow.Entry result = super.getOmitCol0(pos, key);
			assert result != null;
			return result;
		//}
	}
    
    public synchronized void putMultiple(List<kelondroRow.Entry> rows) throws IOException {
        // put a list of entries in a ordered way.
        // this should save R/W head positioning time
        Iterator<kelondroRow.Entry> i = rows.iterator();
        kelondroRow.Entry row;
        int pos;
        byte[] key;
        TreeMap<Integer, kelondroRow.Entry> old_rows_ordered = new TreeMap<Integer, kelondroRow.Entry>();
        ArrayList<kelondroRow.Entry> new_rows_sequential = new ArrayList<kelondroRow.Entry>();
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        while (i.hasNext()) {
            row = i.next();
            key = row.getColBytes(0);
            pos = index.geti(key);
            if (pos < 0) {
                new_rows_sequential.add(row);
            } else {
                old_rows_ordered.put(new Integer(pos), row);
            }
        }
        // overwrite existing entries in index
        super.setMultiple(old_rows_ordered);
        
        // write new entries to index
        addUniqueMultiple(new_rows_sequential);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
    }

    public synchronized kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
    	assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return put(row);
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        assert (row != null);
        assert (!(serverLog.allZero(row.getColBytes(0))));
        assert row.objectsize() <= this.rowdef.objectsize;
        byte[] key = row.getColBytes(0);
        if (index == null) return null; // case may appear during shutdown
        int pos = index.geti(key);
        if (pos < 0) {
        	pos = super.add(row);
            index.puti(key, pos);
            assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
            return null;
        }
        //System.out.println("row.key=" + serverLog.arrayList(row.bytes(), 0, row.objectsize()));
        kelondroRow.Entry oldentry = super.get(pos);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        if (oldentry == null) {
        	serverLog.logSevere("kelondroFlexTable", "put(): index failure; the index pointed to a cell which is empty. content.size() = " + this.size() + ", index.size() = " + ((index == null) ? 0 : index.size()));
        	// patch bug ***** FIND CAUSE! (see also: remove)
        	int oldindex = index.removei(key);
        	assert oldindex >= 0;
        	assert index.geti(key) == -1;
        	// here is this.size() > index.size() because of remove operation above
        	index.puti(key, super.add(row));
        	assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
            return null;
        }
        assert oldentry != null : "overwrite of empty position " + pos + ", index management must have failed before";
        assert rowdef.objectOrder.compare(oldentry.getPrimaryKeyBytes(), key) == 0 : "key and row does not match; key = " + serverLog.arrayList(key, 0, key.length) + " row.key = " + serverLog.arrayList(oldentry.getPrimaryKeyBytes(), 0, rowdef.primaryKeyLength);
        super.set(pos, row);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        return oldentry;
    }
    
    public synchronized boolean addUnique(kelondroRow.Entry row) throws IOException {
        assert row.objectsize() == this.rowdef.objectsize;
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return index.addi(row.getColBytes(0), super.add(row));
    }
    
    public synchronized int addUniqueMultiple(List<kelondroRow.Entry> rows) throws IOException {
        // add a list of entries in a ordered way.
        // this should save R/W head positioning time
        TreeMap<Integer, byte[]> indexed_result = super.addMultiple(rows);
        // indexed_result is a Integer/byte[] relation
        // that is used here to store the index
        Iterator<Map.Entry<Integer, byte[]>> i = indexed_result.entrySet().iterator();
        Map.Entry<Integer, byte[]> entry;
        while (i.hasNext()) {
            entry = i.next();
            index.puti(entry.getValue(), entry.getKey().intValue());
        }
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return indexed_result.size();
    }
    
    public synchronized ArrayList<kelondroRowCollection> removeDoubles() throws IOException {
        ArrayList<kelondroRowCollection> report = new ArrayList<kelondroRowCollection>();
        kelondroRowSet rows;
        TreeSet<Integer> d = new TreeSet<Integer>();
        for (Integer[] is: index.removeDoubles()) {
            rows = new kelondroRowSet(this.rowdef, is.length);
            for (int j = 0; j < is.length; j++) {
                d.add(is[j]);
                rows.addUnique(this.get(is[j].intValue()));
            }
            report.add(rows);
        }
        // finally delete the affected rows, but start with largest id first, otherwise we overwrite wrong entries
        Integer s;
        while (d.size() > 0) {
            s = d.last();
            d.remove(s);
            this.remove(s.intValue());
        }
        return report;
    }
    
    public synchronized kelondroRow.Entry remove(byte[] key, boolean keepOrder) throws IOException {
        assert keepOrder == false; // the underlying data structure is a file, where the order cannot be maintained. Gaps are filled with new values.
        int i = index.removei(key);
        assert (index.geti(key) < 0); // must be deleted
        if (i < 0) {
        	assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
    		return null;
        }
        kelondroRow.Entry r = super.getOmitCol0(i, key);
        if (r == null) {
        	serverLog.logSevere("kelondroFlexTable", "remove(): index failure; the index pointed to a cell which is empty. content.size() = " + this.size() + ", index.size() = " + ((index == null) ? 0 : index.size()));
        	// patch bug ***** FIND CAUSE! (see also: put)
        	assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
    		return null;
        }
        assert r != null : "r == null"; // should be avoided with path above
        assert rowdef.objectOrder.compare(r.getPrimaryKeyBytes(), key) == 0 : "key and row does not match; key = " + serverLog.arrayList(key, 0, key.length) + " row.key = " + serverLog.arrayList(r.getPrimaryKeyBytes(), 0, rowdef.primaryKeyLength);
        super.remove(i);
        assert super.get(i) == null : "i = " + i + ", get(i) = " + serverLog.arrayList(super.get(i).bytes(), 0, 12);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return r;
    }

    public synchronized kelondroRow.Entry removeOne() throws IOException {
        int i = index.removeonei();
        if (i < 0) return null;
        kelondroRow.Entry r;
        r = super.get(i);
        super.remove(i);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return r;
    }
    
    public synchronized kelondroCloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
    	return index.keys(up, firstKey);
    }
    
    public synchronized kelondroCloneableIterator<kelondroRow.Entry> rows(boolean up, byte[] firstKey) throws IOException {
        if (index == null) return new rowIterator(up, firstKey);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return new rowIterator(up, firstKey);
    }
    
    public class rowIterator implements kelondroCloneableIterator<kelondroRow.Entry> {

        kelondroCloneableIterator<kelondroRow.Entry> indexIterator;
        boolean up;
        
        public rowIterator(boolean up, byte[] firstKey) throws IOException {
            this.up = up;
            indexIterator = index.rows(up, firstKey);
        }
        
        public rowIterator clone(Object modifier) {
            try {
                return new rowIterator(up, (byte[]) modifier);
            } catch (IOException e) {
                return null;
            }
        }
        
        public boolean hasNext() {
            return indexIterator.hasNext();
        }

        public kelondroRow.Entry next() {
            kelondroRow.Entry idxEntry = null;
            while ((indexIterator.hasNext()) && (idxEntry == null)) {
                idxEntry = indexIterator.next();
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
        return index.profile();
    }
    
    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return tableTracker.keySet().iterator();
    }

    public static final kelondroProfile profileStats(String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record oobjects,
        // i.e. for cache memory allocation
        kelondroFlexTable theFlexTable = tableTracker.get(filename);
        return theFlexTable.profile();
    }
    
    public static final Map<String, String> memoryStats(String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record objects,
        // i.e. for cache memory allocation
        kelondroFlexTable theFlexTable = tableTracker.get(filename);
        return theFlexTable.memoryStats();
    }
    
    private final Map<String, String> memoryStats() {
        // returns statistical data about this object
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("tableIndexChunkSize", (!RAMIndex) ? "0" : Integer.toString(index.row().objectsize));
        map.put("tableIndexCount", (!RAMIndex) ? "0" : Integer.toString(index.size()));
        map.put("tableIndexMem", (!RAMIndex) ? "0" : Integer.toString((int) (index.row().objectsize * index.size() * kelondroRowCollection.growfactor)));
        return map;
    }
    
    public synchronized void close() {
        if (tableTracker.remove(this.filename) == null) {
            serverLog.logWarning("kelondroFlexTable", "close(): file '" + this.filename + "' was not tracked with record tracker.");
        }
        if ((index != null) && (this.size() != ((index == null) ? 0 : index.size()))) {
        	serverLog.logSevere("kelondroFlexTable", "close(): inconsistent content/index size. content.size() = " + this.size() + ", index.size() = " + ((index == null) ? 0 : index.size()));
        }
        
        if (index != null) {index.close(); index = null;}
        super.close();
    }
    
    public static void main(String[] args) {
        // open a file, add one entry and exit
        File f = new File(args[0]);
        String name = args[1];
        kelondroRow row = new kelondroRow("Cardinal key-4 {b256}, byte[] x-64", kelondroNaturalOrder.naturalOrder, 0);
        try {
            kelondroFlexTable t = new kelondroFlexTable(f, name, row, 0, true);
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
