// kelondroArray.java
// ------------------
// part of the Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
  This class extends the kelondroRecords and adds a array structure
*/

package de.anomic.kelondro;


import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class kelondroFixedWidthArray extends kelondroEcoRecords implements kelondroArray {

    // define the Over-Head-Array
    private static short thisOHBytes   = 0; // our record definition does not need extra bytes
    private static short thisOHHandles = 0; // and no handles
    
    public kelondroFixedWidthArray(File file, kelondroRow rowdef, int intprops) throws IOException {
        // this creates a new array
        //super(file, true, -1, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
        super(file, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
        if (!(super.fileExisted)) {
            for (int i = 0; i < intprops; i++) {
                setHandle(i, new kelondroHandle(kelondroHandle.NUL));
            }
            // store column description
            for (int i = 0; i < rowdef.columns(); i++) {
                try {super.setText(i, rowdef.column(i).toString().getBytes());} catch (IOException e) {}
            }
        }
    }
    
    public kelondroFixedWidthArray(kelondroRA ra, String filename, kelondroRow rowdef, int intprops) throws IOException {
        // this creates a new array
        //super(ra, filename, true, -1, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */, false);
        super(ra, filename, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */, false);
        for (int i = 0; i < intprops; i++) {
            setHandle(i, new kelondroHandle(0));
        }
        // store column description
        for (int i = 0; i < rowdef.columns(); i++) {
            try {super.setText(i, rowdef.column(i).toString().getBytes());} catch (IOException e) {}
        }
    }
    
    public static kelondroFixedWidthArray open(File file, kelondroRow rowdef, int intprops) {
        try {
            return new kelondroFixedWidthArray(file, rowdef, intprops);
        } catch (IOException e) {
            file.delete();
            try {
                return new kelondroFixedWidthArray(file, rowdef, intprops);
            } catch (IOException ee) {
                e.printStackTrace();
                ee.printStackTrace();
                System.exit(-1);
                return null;
            }
        }
    }
    
    public synchronized void set(int index, kelondroRow.Entry rowentry) throws IOException {
        // this writes a row without reading the row from the file system first
        
        // create a node at position index with rowentry
        kelondroHandle h = new kelondroHandle(index);
        (new EcoNode(h, (rowentry == null) ? null : rowentry.bytes(), 0)).commit();
        // attention! this newNode call wants that the OH bytes are passed within the bulkchunk
        // field. Here, only the rowentry.bytes() raw payload is passed. This is valid, because
        // the OHbytes and OHhandles are zero.
    }
    
    public synchronized void setMultiple(TreeMap<Integer, kelondroRow.Entry> rows) throws IOException {
        Iterator<Map.Entry<Integer, kelondroRow.Entry>> i = rows.entrySet().iterator();
        Map.Entry<Integer, kelondroRow.Entry> entry;
        int k;
        while (i.hasNext()) {
            entry = i.next();
            k = entry.getKey().intValue();
            set(k, entry.getValue());
        }
    }
   
    public synchronized kelondroRow.Entry getIfValid(int index) throws IOException {
    	byte[] b = (new EcoNode(new kelondroHandle(index))).getValueRow();
    	if (b[0] == 0) return null;
    	if ((b[0] == -128) && (b[1] == 0)) return null;
    	return row().newEntry(b);
    }
    
    public synchronized kelondroRow.Entry get(int index) throws IOException {
    	return row().newEntry(new EcoNode(new kelondroHandle(index)).getValueRow());
    }

    protected synchronized int seti(int index, int value) throws IOException {
        int before = getHandle(index).hashCode();
        setHandle(index, new kelondroHandle(value));
        return before;
    }

    protected synchronized int geti(int index) {
        return getHandle(index).hashCode();
    }

    public synchronized int add(kelondroRow.Entry rowentry) throws IOException {
        // adds a new rowentry, but re-uses a previously as-deleted marked entry
        kelondroNode n = new EcoNode(rowentry.bytes());
        n.commit();
        return n.handle().hashCode();
    }
    
    public synchronized void remove(int index) throws IOException {
        assert (index < (super.free() + super.size())) : "remove: index " + index + " out of bounds " + (super.free() + super.size());

        // get the node at position index
        kelondroHandle h = new kelondroHandle(index);
        kelondroNode n = new EcoNode(h);

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

    public static void main(String[] args) {
        //File f = new File("d:\\\\mc\\privat\\fixtest.db");
        File f = new File("/Users/admin/fixtest.db");
        kelondroRow rowdef = new kelondroRow("byte[] a-12, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0);
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
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
