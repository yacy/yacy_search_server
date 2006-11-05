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
import java.util.Date;
import java.util.Iterator;

import de.anomic.server.logging.serverLog;

public class kelondroFlexTable extends kelondroFlexWidthArray implements kelondroIndex {

    protected kelondroBytesIntMap index;
    private boolean RAMIndex;
    
    public kelondroFlexTable(File path, String tablename, long buffersize, long preloadTime, kelondroRow rowdef, kelondroOrder objectOrder) throws IOException {
    	// the buffersize applies to a possible load of the ram-index
    	// if the ram is not sufficient, a tree file is generated
    	// if, and only if a tree file exists, the preload time is applied
    	super(path, tablename, rowdef);
    	long neededRAM = (super.row().column(0).cellwidth() + 4) * 12 / 10 * super.size();
    	
    	File newpath = new File(path, tablename);
        File indexfile = new File(newpath, "col.000.index");
        kelondroIndex ki = null;
        String description = new String(this.col[0].getDescription());
        int p = description.indexOf(';', 4);
        long stt = (p > 0) ? Long.parseLong(description.substring(4, p)) : 0;
        System.out.println("*** Last Startup time: " + stt + " milliseconds");
        long start = System.currentTimeMillis();

        if (buffersize >= neededRAM) {
        	// we can use a RAM index
        	
        	if (indexfile.exists()) {
                // delete existing index file
                System.out.println("*** Delete File index " + indexfile);
                indexfile.delete();
        	}
        	
        	// fill the index
            System.out.print("*** Loading RAM index for " + size() + " entries from "+ newpath);
            ki = initializeRamIndex(objectOrder);
            
            System.out.println(" -done-");
            System.out.println(ki.size()
                    + " index entries initialized and sorted from "
                    + super.col[0].size() + " keys.");
            RAMIndex = true;
        } else {
            // too less ram for a ram index
            if (indexfile.exists()) {
                // use existing index file
                System.out.println("*** Using File index " + indexfile);
                ki = new kelondroCache(kelondroTree.open(indexfile, buffersize / 3 * 2, preloadTime, treeIndexRow(rowdef.width(0)), objectOrder, 2, 80), buffersize / 3, true, false);
                RAMIndex = false;
            } else if ((preloadTime >= 0) && (stt > preloadTime)) {
                // generate new index file
                System.out.print("*** Generating File index for " + size() + " entries from " + indexfile);
                System.out.print("*** Cause: too less RAM configured. Assign at least " + neededRAM + " bytes buffersize to enable a RAM index.");
                ki = initializeTreeIndex(indexfile, buffersize, preloadTime, objectOrder);

                System.out.println(" -done-");
                System.out.println(ki.size() + " entries indexed from " + super.col[0].size() + " keys.");
                RAMIndex = false;
            }
        }
        // assign index to wrapper
        index = new kelondroBytesIntMap(ki);
        description = "stt=" + Long.toString(System.currentTimeMillis() - start) + ";";
        super.col[0].setDescription(description.getBytes());
    }
    
    public static int staticSize(File path, String tablename) {
        return kelondroFlexWidthArray.staticsize(path, tablename);
    }
    
    public boolean hasRAMIndex() {
        return RAMIndex;
    }
    
    public boolean has(byte[] key) throws IOException {
        // it is not recommended to implement or use a has predicate unless
        // it can be ensured that it causes no IO
        assert (RAMIndex == true);
        return index.geti(key) >= 0;
    }
    
    private kelondroIndex initializeRamIndex(kelondroOrder objectOrder) {
        kelondroRowSet ri = new kelondroRowSet(new kelondroRow(new kelondroColumn[]{super.row().column(0), new kelondroColumn("int c-4 {b256}")}), objectOrder, 0, 0);
        //kelondroRowSet ri = new kelondroRowSet(new kelondroRow(new kelondroColumn[]{super.row().column(0), new kelondroColumn("int c-4 {b256}")}), 0);
        //ri.setOrdering(objectOrder, 0);
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
        ri.shape();
        ri.trim();
        return ri;
    }
    
    private kelondroIndex initializeTreeIndex(File indexfile, long buffersize, long preloadTime, kelondroOrder objectOrder) throws IOException {
        kelondroIndex treeindex = new kelondroCache(new kelondroTree(indexfile, buffersize / 3 * 2, preloadTime, treeIndexRow(rowdef.width(0)), objectOrder, 2, 80), buffersize / 3, true, false);
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
                System.out.println(".. generated " + c+ " entries, " + ((System.currentTimeMillis() - start) / c * (all - c) / 60000) + " minutes remaining");
                System.out.flush();
                last = System.currentTimeMillis();
            }
        }
        return treeindex;
    }
    
    private static final kelondroRow treeIndexRow(int keywidth) {
        return new kelondroRow("byte[] key-" + keywidth + ", int reference-4 {b256}");
    }
    
    public synchronized kelondroRow.Entry get(byte[] key) throws IOException {
            int i = index.geti(key);
            if (i < 0) return null;
            // i may be greater than this.size(), because this table may have deleted entries
            // the deleted entries are subtracted from the 'real' tablesize, so the size may be
            // smaller than an index to a row entry
            return super.get(i);
    }

    public synchronized kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        return put(row);
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        assert (row != null);
        assert (!(serverLog.allZero(row.getColBytes(0))));
        assert row.bytes().length <= this.rowdef.objectsize;
        int i = index.geti(row.getColBytes(0));
        if (i < 0) {
            index.puti(row.getColBytes(0), super.add(row));
            return null;
        }
        return super.set(i, row);
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) throws IOException {
        addUnique(row);
    }
    
    public synchronized void addUnique(kelondroRow.Entry row) throws IOException {
        assert row.bytes().length <= this.rowdef.objectsize;
        index.addi(row.getColBytes(0), super.add(row));
    }
    
    public synchronized kelondroRow.Entry remove(byte[] key) throws IOException {
        int i = index.removei(key);
        if (i < 0) return null;
        kelondroRow.Entry r;
        r = super.get(i);
        super.remove(i);
        return r;
    }

    public synchronized kelondroRow.Entry removeOne() throws IOException {
        int i = index.removeonei();
        if (i < 0) return null;
        kelondroRow.Entry r;
        r = super.get(i);
        super.remove(i);
        return r;
    }
    
    public synchronized Iterator rows(boolean up, boolean rotating, byte[] firstKey) throws IOException {
        return new rowIterator(up, rotating, firstKey);
    }
    
    public class rowIterator implements Iterator {

        Iterator indexIterator;
        
        public rowIterator(boolean up, boolean rotating, byte[] firstKey) throws IOException {
            indexIterator = index.rows(up, rotating, firstKey);
        }
        
        public boolean hasNext() {
            return indexIterator.hasNext();
        }

        public Object next() {
            kelondroRow.Entry idxEntry = (kelondroRow.Entry) indexIterator.next();
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

    public kelondroOrder order() {
        return index.order();
    }

    public int primarykey() {
        return 0;
    }
    
    public kelondroProfile profile() {
        return index.profile();
    }
    
    public final int cacheObjectChunkSize() {
        // dummy method
        return -1;
    }
    
    public long[] cacheObjectStatus() {
        // dummy method
        return null;
    }
    
    public final int cacheNodeChunkSize() {
        // returns the size that the node cache uses for a single entry
        return -1;
    }
    
    public final int[] cacheNodeStatus() {
        // a collection of different node cache status values
        return new int[]{0,0,0,0,0,0,0,0,0,0};
    }
    
    public synchronized void close() throws IOException {
        index.close();
        super.close();
    }
    
}
