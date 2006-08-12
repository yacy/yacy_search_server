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
import java.util.Iterator;

public class kelondroFlexTable extends kelondroFlexWidthArray implements kelondroIndex {

    private kelondroBytesIntMap index;
    
    public kelondroFlexTable(File path, String tablename, kelondroOrder objectOrder, long buffersize, long preloadTime, kelondroRow rowdef, boolean exitOnFail) throws IOException {
        super(path, tablename, rowdef, exitOnFail);
        File newpath = new File(path, tablename);
        File indexfile = new File(newpath, "col.000.index");
        kelondroIndex ki = null;
        String description = new String(this.col[0].getDescription());
        int p = description.indexOf(';', 4);
        long stt = (p > 0) ? Long.parseLong(description.substring(4, p)) : 0;
        System.out.println("*** Last Startup time: " + stt + " milliseconds");
        long start = System.currentTimeMillis();

        if ((preloadTime < 0) && (indexfile.exists())) {
            // delete existing index file
            System.out.println("*** Delete File index " + indexfile);
            indexfile.delete();
        }
        if (indexfile.exists()) {
            // use existing index file
            System.out.println("*** Using File index " + indexfile);
            ki = new kelondroTree(indexfile, buffersize, preloadTime, 10);
        } else if ((preloadTime >= 0) && (stt > preloadTime)) {
            // generate new index file
            System.out.print("*** Generating File index for " + size() + " entries from " + indexfile);
            ki = initializeTreeIndex(indexfile, buffersize, preloadTime, objectOrder);

            System.out.println(" -done-");
            System.out.println(ki.size()
                    + " entries indexed from "
                    + super.col[0].size() + " keys.");
        } else {
            // fill the index
            System.out.print("*** Loading RAM index for " + size() + " entries from "+ newpath);
            ki = initializeRamIndex(objectOrder);
            
            System.out.println(" -done-");
            System.out.println(ki.size()
                    + " index entries initialized and sorted from "
                    + super.col[0].size() + " keys.");
        }
        // assign index to wrapper
        index = new kelondroBytesIntMap(ki);
        description = "stt=" + Long.toString(System.currentTimeMillis() - start) + ";";
        super.col[0].setDescription(description.getBytes());
    }
    
    private kelondroIndex initializeRamIndex(kelondroOrder objectOrder) throws IOException {
        kelondroRowBufferedSet ri = new kelondroRowBufferedSet(new kelondroRow(new kelondroColumn[]{super.row().column(0), new kelondroColumn("int c-4 {b256}")}), 0);
        ri.setOrdering(objectOrder, 0);
        Iterator content = super.col[0].contentNodes(-1);
        kelondroRecords.Node node;
        kelondroRow.Entry indexentry;
        int i;
        while (content.hasNext()) {
            node = (kelondroRecords.Node) content.next();
            i = node.handle().hashCode();
            indexentry = ri.rowdef.newEntry();
            indexentry.setCol(0, node.getValueRow());
            indexentry.setCol(1, i);
            ri.add(indexentry);
            if ((i % 10000) == 0) {
                System.out.print('.');
                System.out.flush();
            }
        }
        System.out.print(" -ordering- ");
        System.out.flush();
        ri.shape();
        return ri;
    }
    
    
    private kelondroIndex initializeTreeIndex(File indexfile, long buffersize, long preloadTime, kelondroOrder objectOrder) throws IOException {
        kelondroTree index = new kelondroTree(indexfile, buffersize, preloadTime, 10,
                new kelondroRow("byte[] key-" + rowdef.width(0) + ", int reference-4 {b256}"),
                objectOrder, 2, 80, true);
        Iterator content = super.col[0].contentNodes(-1);
        kelondroRecords.Node node;
        kelondroRow.Entry indexentry;
        int i;
        while (content.hasNext()) {
            node = (kelondroRecords.Node) content.next();
            i = node.handle().hashCode();
            indexentry = index.row().newEntry();
            indexentry.setCol(0, node.getValueRow());
            indexentry.setCol(1, i);
            index.put(indexentry);
            if ((i % 10000) == 0) {
                System.out.print('.');
                System.out.flush();
            }
        }
        return index;
    }
    
    public synchronized kelondroRow.Entry get(byte[] key) throws IOException {
        synchronized (index) {
            int i = index.geti(key);
            if (i < 0) return null;
            // i may be greater than this.size(), because this table may have deleted entries
            // the deleted entries are subtracted from the 'real' tablesize, so the size may be
            // smaller than an index to a row entry
            return super.get(i);
        }
    }

    public synchronized kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        synchronized (index) {
            int i = index.geti(row.getColBytes(0));
            if (i < 0) {
                index.puti(row.getColBytes(0), super.add(row));
                return null;
            } else {
                return super.set(i, row);
            }
        }
    }
    
    public synchronized kelondroRow.Entry remove(byte[] key) throws IOException {
        synchronized (index) {
            int i = index.removei(key);
            if (i < 0) return null;
            kelondroRow.Entry r = super.get(i);
            super.remove(i);
            return r;
        }
    }

    public Iterator rows(boolean up, boolean rotating, byte[] firstKey) throws IOException {
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

}
