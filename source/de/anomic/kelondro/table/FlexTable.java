// kelondroFlexTable.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

package de.anomic.kelondro.table;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.kelondro.index.IntegerHandleIndex;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowCollection;
import de.anomic.kelondro.index.RowSet;
import de.anomic.kelondro.index.ObjectIndex;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.kelondroException;
import de.anomic.kelondro.util.Log;

public class FlexTable extends FlexWidthArray implements ObjectIndex {

    // static tracker objects
    private static TreeMap<String, FlexTable> tableTracker = new TreeMap<String, FlexTable>();
    
    // class objects
    protected IntegerHandleIndex index;
    private boolean RAMIndex;
    
    /**
     * Deprecated Class. Please use kelondroEcoTable instead
     */
    @Deprecated
    public FlexTable(final File path, final String tablename, final Row rowdef, int minimumSpace, final boolean resetOnFail) {
    	// the buffersize applies to a possible load of the ram-index
        // the minimumSpace is a initial allocation space for the index; names the number of index slots
    	// if the ram is not sufficient, a tree file is generated
    	// if, and only if a tree file exists, the preload time is applied
    	super(path, tablename, rowdef, resetOnFail);
        if ((super.col[0].size() < 0) && (resetOnFail)) try {
            super.reset();
        } catch (final IOException e2) {
            e2.printStackTrace();
            throw new kelondroException(e2.getMessage());
        }
        minimumSpace = Math.max(minimumSpace, super.size());
        try {
    	final long neededRAM = 10 * 1024 * 104 + (long) ((super.row().primaryKeyLength + 4) * minimumSpace * RowCollection.growfactor);
    	
    	final File newpath = new File(path, tablename);
        final File indexfile = new File(newpath, "col.000.index");
        String description = "";
        description = new String(this.col[0].getDescription());
        final int p = description.indexOf(';', 4);
        final long stt = (p > 0) ? Long.parseLong(description.substring(4, p)) : 0;
        System.out.println("*** Last Startup time: " + stt + " milliseconds");
        final long start = System.currentTimeMillis();

		// we use a RAM index
		if (indexfile.exists()) {
			// delete existing index file
			System.out.println("*** Delete File index " + indexfile);
			FileUtils.deletedelete(indexfile);
		}

		// fill the index
		System.out.print("*** Loading RAM index for " + size() + " entries from " + newpath + "; available RAM = " + (MemoryControl.available() >> 20) + " MB, allocating " + (neededRAM >> 20) + " MB for index.");
		index = initializeRamIndex(minimumSpace);

		System.out.println(" -done-");
		System.out.println(index.size() + " index entries initialized and sorted from " + super.col[0].size() + " keys.");
		RAMIndex = true;
		tableTracker.put(this.filename(), this);

        // check consistency
        final ArrayList<Integer[]> doubles = index.removeDoubles();
        if (doubles.size() > 0) {
            System.out.println("DEBUG: WARNING - FlexTable " + newpath.toString() + " has " + doubles.size() + " doubles");
        }
        
        // assign index to wrapper
        description = "stt=" + Long.toString(System.currentTimeMillis() - start) + ";";
        super.col[0].setDescription(description.getBytes());
    	} catch (final IOException e) {
    		if (resetOnFail) {
    			RAMIndex = true;
    	        index = new IntegerHandleIndex(super.row().primaryKeyLength, super.rowdef.objectOrder, 0, 0);
    		} else {
    			throw new kelondroException(e.getMessage());
    		}
    	}
    }
    
    public void clear() throws IOException {
    	super.reset();
    	RAMIndex = true;
        index = new IntegerHandleIndex(super.row().primaryKeyLength, super.rowdef.objectOrder, 0, 0);
    }
    
    public static int staticSize(final File path, final String tablename) {
        return FlexWidthArray.staticsize(path, tablename);
    }
    
    public static int staticRAMIndexNeed(final File path, final String tablename, final Row rowdef) {
        return (int) ((rowdef.primaryKeyLength + 4) * staticSize(path, tablename) * RowCollection.growfactor);
    }
    
    public boolean hasRAMIndex() {
        return RAMIndex;
    }
    
    public synchronized boolean has(final byte[] key) {
        // it is not recommended to implement or use a has predicate unless
        // it can be ensured that it causes no IO
        if ((AbstractRecords.debugmode) && (RAMIndex != true)) Log.logWarning("kelondroFlexTable", "RAM index warning in file " + super.tablename);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        return index.has(key);
    }
    
    private IntegerHandleIndex initializeRamIndex(final int initialSpace) {
    	final int space = Math.max(super.col[0].size(), initialSpace) + 1;
    	if (space < 0) throw new kelondroException("wrong space: " + space);
        final IntegerHandleIndex ri = new IntegerHandleIndex(super.row().primaryKeyLength, super.rowdef.objectOrder, space, 0);
        final Iterator<Node> content = super.col[0].contentNodes(-1);
        Node node;
        int i;
        byte[] key;
        while (content.hasNext()) {
            node = content.next();
            i = node.handle().hashCode();
            try {
                key = node.getKey();
            } catch (IOException e1) {
                e1.printStackTrace();
                break;
            }
            assert (key != null) : "DEBUG: empty key in initializeRamIndex"; // should not happen; if it does, it is an error of the condentNodes iterator
            //System.out.println("ENTRY: " + serverLog.arrayList(indexentry.bytes(), 0, indexentry.objectsize()));
            ri.putUnique(key, i);
            if ((i % 10000) == 0) {
                System.out.print('.');
                System.out.flush();
            }
        }
        System.out.print(" -ordering- ");
        System.out.flush();
        return ri;
    }

    public synchronized Row.Entry get(final byte[] key) throws IOException {
        if (index == null) return null; // case may happen during shutdown
		final int pos = index.get(key);
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
			final Row.Entry result = super.getOmitCol0(pos, key);
			assert result != null;
			return result;
		//}
	}
    
    public synchronized Row.Entry put(final Row.Entry row, final Date entryDate) throws IOException {
    	assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return replace(row);
    }
    
    public synchronized Row.Entry replace(final Row.Entry row) throws IOException {
        assert (row != null);
        assert (!(Log.allZero(row.getColBytes(0))));
        assert row.objectsize() <= this.rowdef.objectsize;
        final byte[] key = row.getColBytes(0);
        if (index == null) return null; // case may appear during shutdown
        int pos = index.get(key);
        if (pos < 0) {
        	pos = super.add(row);
            index.put(key, pos);
            assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
            return null;
        }
        //System.out.println("row.key=" + serverLog.arrayList(row.bytes(), 0, row.objectsize()));
        final Row.Entry oldentry = super.get(pos);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        if (oldentry == null) {
        	Log.logSevere("kelondroFlexTable", "put(): index failure; the index pointed to a cell which is empty. content.size() = " + this.size() + ", index.size() = " + index.size());
        	// patch bug ***** FIND CAUSE! (see also: remove)
        	final int oldindex = index.remove(key);
        	assert oldindex >= 0;
        	assert index.get(key) == -1;
        	// here is this.size() > index.size() because of remove operation above
        	index.put(key, super.add(row));
        	assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
            return null;
        }
        assert oldentry != null : "overwrite of empty position " + pos + ", index management must have failed before";
        assert rowdef.objectOrder.equal(oldentry.getPrimaryKeyBytes(), key) : "key and row does not match; key = " + NaturalOrder.arrayList(key, 0, key.length) + " row.key = " + NaturalOrder.arrayList(oldentry.getPrimaryKeyBytes(), 0, rowdef.primaryKeyLength);
        super.set(pos, row);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        return oldentry;
    }
    
    public synchronized void put(final Row.Entry row) throws IOException {
        assert (row != null);
        assert (!(Log.allZero(row.getColBytes(0))));
        assert row.objectsize() <= this.rowdef.objectsize;
        final byte[] key = row.getColBytes(0);
        if (index == null) return; // case may appear during shutdown
        int pos = index.get(key);
        if (pos < 0) {
            pos = super.add(row);
            index.put(key, pos);
            assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
            return;
        }
        //System.out.println("row.key=" + serverLog.arrayList(row.bytes(), 0, row.objectsize()));
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        super.set(pos, row);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
    }
    
    public synchronized void addUnique(final Row.Entry row) throws IOException {
        assert row.objectsize() == this.rowdef.objectsize;
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		index.putUnique(row.getColBytes(0), super.add(row));
    }
    
    public synchronized ArrayList<RowCollection> removeDoubles() throws IOException {
        final ArrayList<RowCollection> report = new ArrayList<RowCollection>();
        RowSet rows;
        final TreeSet<Integer> d = new TreeSet<Integer>();
        for (final Integer[] is: index.removeDoubles()) {
            rows = new RowSet(this.rowdef, is.length);
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
    
    public synchronized Row.Entry remove(final byte[] key) throws IOException {
        // the underlying data structure is a file, where the order cannot be maintained. Gaps are filled with new values.
        final int i = index.remove(key);
        assert (index.get(key) < 0); // must be deleted
        if (i < 0) {
        	assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
    		return null;
        }
        final Row.Entry r = super.getOmitCol0(i, key);
        if (r == null) {
        	Log.logSevere("kelondroFlexTable", "remove(): index failure; the index pointed to a cell which is empty. content.size() = " + this.size() + ", index.size() = " + ((index == null) ? 0 : index.size()));
        	// patch bug ***** FIND CAUSE! (see also: put)
        	assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
    		return null;
        }
        assert r != null : "r == null"; // should be avoided with path above
        assert rowdef.objectOrder.equal(r.getPrimaryKeyBytes(), key) : "key and row does not match; key = " + NaturalOrder.arrayList(key, 0, key.length) + " row.key = " + NaturalOrder.arrayList(r.getPrimaryKeyBytes(), 0, rowdef.primaryKeyLength);
        super.remove(i);
        assert super.get(i) == null : "i = " + i + ", get(i) = " + NaturalOrder.arrayList(super.get(i).bytes(), 0, 12);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return r;
    }

    public synchronized Row.Entry removeOne() throws IOException {
        final int i = index.removeone();
        if (i < 0) return null;
        Row.Entry r;
        r = super.get(i);
        super.remove(i);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
		return r;
    }
    
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
    	return index.keys(up, firstKey);
    }
    
    public synchronized CloneableIterator<Row.Entry> rows() throws IOException {
        return new rowIterator(true, null);
    }
    
    public synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        if (index == null) return new rowIterator(up, firstKey);
        assert this.size() == index.size() : "content.size() = " + this.size() + ", index.size() = " + index.size();
        return new rowIterator(up, firstKey);
    }
    
    public class rowIterator implements CloneableIterator<Row.Entry> {

        CloneableIterator<Row.Entry> indexIterator;
        boolean up;
        
        public rowIterator(final boolean up, final byte[] firstKey) throws IOException {
            this.up = up;
            indexIterator = index.rows(up, firstKey);
        }
        
        public rowIterator clone(final Object modifier) {
            try {
                return new rowIterator(up, (byte[]) modifier);
            } catch (final IOException e) {
                return null;
            }
        }
        
        public boolean hasNext() {
            return indexIterator.hasNext();
        }

        public Row.Entry next() {
            Row.Entry idxEntry = null;
            while ((indexIterator.hasNext()) && (idxEntry == null)) {
                idxEntry = indexIterator.next();
            }
            if (idxEntry == null) {
                Log.logSevere("kelondroFlexTable.rowIterator: " + tablename, "indexIterator returned null");
                return null;
            }
            final int idx = (int) idxEntry.getColLong(1);
            try {
                return get(idx);
            } catch (final IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public void remove() {
            indexIterator.remove();
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
        final FlexTable theFlexTable = tableTracker.get(filename);
        return theFlexTable.memoryStats();
    }
    
    private final Map<String, String> memoryStats() {
        // returns statistical data about this object
        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("tableIndexChunkSize", (!RAMIndex) ? "0" : Integer.toString(index.row().objectsize));
        map.put("tableIndexCount", (!RAMIndex) ? "0" : Integer.toString(index.size()));
        map.put("tableIndexMem", (!RAMIndex) ? "0" : Integer.toString((int) (index.row().objectsize * index.size() * RowCollection.growfactor)));
        return map;
    }
    
    public synchronized void close() {
        if (tableTracker.remove(this.filename) == null) {
            Log.logWarning("kelondroFlexTable", "close(): file '" + this.filename + "' was not tracked with record tracker.");
        }
        if ((index != null) && (this.size() != ((index == null) ? 0 : index.size()))) {
            Log.logSevere("kelondroFlexTable", this.filename + " close(): inconsistent content/index size. content.size() = " + this.size() + ", index.size() = " + ((index == null) ? 0 : index.size()));
        }
        
        if (index != null) {index.close(); index = null;}
        super.close();
    }
    
    public static void main(final String[] args) {
        // open a file, add one entry and exit
        final File f = new File(args[0]);
        final String name = args[1];
        final Row row = new Row("Cardinal key-4 {b256}, byte[] x-64", NaturalOrder.naturalOrder);
        try {
            final FlexTable t = new FlexTable(f, name, row, 0, true);
            final Row.Entry entry = row.newEntry();
            entry.setCol(0, System.currentTimeMillis());
            entry.setCol(1, "dummy".getBytes());
            t.put(entry);
            t.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
    
}
