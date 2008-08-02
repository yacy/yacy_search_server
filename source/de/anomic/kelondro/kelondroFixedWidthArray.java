// kelondroArray.java
// ------------------
// part of the Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 20.06.2005
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

/*
  This class extends the kelondroRecords and adds a array structure
*/

package de.anomic.kelondro;


import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class kelondroFixedWidthArray extends kelondroFullRecords implements kelondroArray {

    // define the Over-Head-Array
    private static short thisOHBytes   = 0; // our record definition does not need extra bytes
    private static short thisOHHandles = 0; // and no handles
    
    public kelondroFixedWidthArray(final File file, final kelondroRow rowdef, final int intprops) throws IOException {
        // this creates a new array
        //super(file, true, -1, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
        super(file, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
        if (!(super.fileExisted)) {
            for (int i = 0; i < intprops; i++) {
                setHandle(i, new kelondroHandle(kelondroHandle.NUL));
            }
            // store column description
            for (int i = 0; i < rowdef.columns(); i++) {
                try {super.setText(i, rowdef.column(i).toString().getBytes());} catch (final IOException e) {}
            }
        }
    }
    
    public kelondroFixedWidthArray(final kelondroRA ra, final String filename, final kelondroRow rowdef, final int intprops) throws IOException {
        // this creates a new array
        //super(ra, filename, true, -1, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */, false);
        super(ra, filename, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */, false);
        for (int i = 0; i < intprops; i++) {
            setHandle(i, new kelondroHandle(0));
        }
        // store column description
        for (int i = 0; i < rowdef.columns(); i++) {
            try {super.setText(i, rowdef.column(i).toString().getBytes());} catch (final IOException e) {}
        }
    }
    
    public static kelondroFixedWidthArray open(final File file, final kelondroRow rowdef, final int intprops) {
        try {
            return new kelondroFixedWidthArray(file, rowdef, intprops);
        } catch (final IOException e) {
            file.delete();
            try {
                return new kelondroFixedWidthArray(file, rowdef, intprops);
            } catch (final IOException ee) {
                e.printStackTrace();
                ee.printStackTrace();
                System.exit(-1);
                return null;
            }
        }
    }
    
    public synchronized void set(final int index, final kelondroRow.Entry rowentry) throws IOException {
        // this writes a row without reading the row from the file system first
        
        // create a node at position index with rowentry
        final kelondroHandle h = new kelondroHandle(index);
        (new EcoNode(h, (rowentry == null) ? null : rowentry.bytes(), 0)).commit();
        // attention! this newNode call wants that the OH bytes are passed within the bulkchunk
        // field. Here, only the rowentry.bytes() raw payload is passed. This is valid, because
        // the OHbytes and OHhandles are zero.
    }
    
    public synchronized void setMultiple(final TreeMap<Integer, kelondroRow.Entry> rows) throws IOException {
        final Iterator<Map.Entry<Integer, kelondroRow.Entry>> i = rows.entrySet().iterator();
        Map.Entry<Integer, kelondroRow.Entry> entry;
        int k;
        while (i.hasNext()) {
            entry = i.next();
            k = entry.getKey().intValue();
            set(k, entry.getValue());
        }
    }
   
    public synchronized kelondroRow.Entry getIfValid(final int index) throws IOException {
    	final byte[] b = (new EcoNode(new kelondroHandle(index))).getValueRow();
    	if (b[0] == 0) return null;
    	if ((b[0] == -128) && (b[1] == 0)) return null;
    	return row().newEntry(b);
    }
    
    public synchronized kelondroRow.Entry get(final int index) throws IOException {
    	return row().newEntry(new EcoNode(new kelondroHandle(index)).getValueRow());
    }

    protected synchronized int seti(final int index, final int value) throws IOException {
        final int before = getHandle(index).hashCode();
        setHandle(index, new kelondroHandle(value));
        return before;
    }

    protected synchronized int geti(final int index) {
        return getHandle(index).hashCode();
    }

    public synchronized int add(final kelondroRow.Entry rowentry) throws IOException {
        // adds a new rowentry, but re-uses a previously as-deleted marked entry
        final kelondroNode n = new EcoNode(rowentry.bytes());
        n.commit();
        return n.handle().hashCode();
    }
    
    public synchronized void remove(final int index) throws IOException {
        assert (index < (super.free() + super.size())) : "remove: index " + index + " out of bounds " + (super.free() + super.size());

        // get the node at position index
        final kelondroHandle h = new kelondroHandle(index);
        final kelondroNode n = new EcoNode(h);

		// erase the row
		n.setValueRow(null);
		n.commit();

		// mark row as deleted so it can be re-used
		deleteNode(h);
	}
    
    public void print() throws IOException {
        System.out.println("PRINTOUT of table, length=" + size());
        kelondroRow.Entry row;
        for (int i = 0; i < (super.free() + super.size()); i++) {
            System.out.print("row " + i + ": ");
            row = get(i);
            for (int j = 0; j < row.columns(); j++) System.out.print(((row.empty(j)) ? "NULL" : row.getColString(j, "UTF-8")) + ", ");
            System.out.println();
        }
        System.out.println("EndOfTable");
    }

    public static void main(final String[] args) {
        //File f = new File("d:\\\\mc\\privat\\fixtest.db");
        final File f = new File("/Users/admin/fixtest.db");
        final kelondroRow rowdef = new kelondroRow("byte[] a-12, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0);
        try {
            System.out.println("erster Test");
            f.delete();
            kelondroFixedWidthArray k = new kelondroFixedWidthArray(f, rowdef, 6);
            k.set(3, k.row().newEntry(new byte[][]{
                "test123".getBytes(), "abcd".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{
                "test456".getBytes(), "efgh".getBytes()}));
            k.close();
            
            k = new kelondroFixedWidthArray(f, rowdef, 6);
            System.out.println(k.get(2).toString());
            System.out.println(k.get(3).toString());
            k.close();

            System.out.println("zweiter Test");
            f.delete();
            k = new kelondroFixedWidthArray(f, rowdef, 6);
            k.add(k.row().newEntry(new byte[][]{"a".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"b".getBytes(), "xxxx".getBytes()}));
            k.remove(0);
            
            k.add(k.row().newEntry(new byte[][]{"c".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"d".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"e".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"f".getBytes(), "xxxx".getBytes()}));
            k.remove(0);
            k.remove(1);
            
            k.print();
            k.print();
            k.close();
            
            
            System.out.println("dritter Test");
            f.delete();
            k = new kelondroFixedWidthArray(f, rowdef, 6);
            for (int i = 1; i <= 200; i = i * 2) {
                for (int j = 0; j < i*2; j++) {
                    k.add(k.row().newEntry(new byte[][]{(Integer.toString(i) + "-" + Integer.toString(j)).getBytes(), "xxxx".getBytes()}));
                }
                for (int j = 0; j < i; j++) {
                    k.remove(j);
                }
            }
            k.print();
            k.print();
            k.close();
            
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
    
}
