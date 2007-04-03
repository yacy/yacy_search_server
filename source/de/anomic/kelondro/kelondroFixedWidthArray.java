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
import java.util.TreeSet;

public class kelondroFixedWidthArray extends kelondroRecords implements kelondroArray {

    // define the Over-Head-Array
    private static short thisOHBytes   = 0; // our record definition does not need extra bytes
    private static short thisOHHandles = 0; // and no handles
    
    private TreeSet markedRemoved; // a set of Integer indexes of removed records (only temporary)
    
    public kelondroFixedWidthArray(File file, kelondroRow rowdef, int intprops) throws IOException {
        // this creates a new array
        super(file, false, 0, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
        if (!(super.fileExisted)) {
            for (int i = 0; i < intprops; i++) {
                setHandle(i, new Handle(NUL));
            }
            // store column description
            for (int i = 0; i < rowdef.columns(); i++) {
                try {super.setText(i, rowdef.column(i).toString().getBytes());} catch (IOException e) {}
            }
        }
        markedRemoved = new TreeSet();
    }
    
    public kelondroFixedWidthArray(kelondroRA ra, String filename, kelondroRow rowdef, int intprops) throws IOException {
        // this creates a new array
        super(ra, filename, false, 0, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */, false);
        for (int i = 0; i < intprops; i++) {
            setHandle(i, new Handle(0));
        }
        // store column description
        for (int i = 0; i < rowdef.columns(); i++) {
            try {super.setText(i, rowdef.column(i).toString().getBytes());} catch (IOException e) {}
        }
        markedRemoved = new TreeSet();
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
        Handle h = new Handle(index);
        newNode(h, (rowentry == null) ? null : rowentry.bytes(), 0).commit(CP_NONE);
        // attention! this newNode call wants that the OH bytes are passed within the bulkchunk
        // field. Here, only the rowentry.bytes() raw payload is passed. This is valid, because
        // the OHbytes and OHhandles are zero.
    }
    
    public synchronized void setMultiple(TreeMap /* of Integer/kelondroRow.Entry */ rows) throws IOException {
        Iterator i = rows.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            set(((Integer) entry.getKey()).intValue(), (kelondroRow.Entry) entry.getValue());
        }
    }
   
    public synchronized kelondroRow.Entry getIfValid(int index) throws IOException {
    	byte[] b = getNode(new Handle(index), true).getValueRow();
    	if (b[0] == 0) return null;
    	return row().newEntry(b);
    }
    
    public synchronized kelondroRow.Entry get(int index) throws IOException {
    	return row().newEntry(getNode(new Handle(index), true).getValueRow());
    }

    protected synchronized int seti(int index, int value) throws IOException {
        int before = getHandle(index).hashCode();
        setHandle(index, new Handle(value));
        return before;
    }

    protected synchronized int geti(int index) {
        return getHandle(index).hashCode();
    }

    public synchronized int add(kelondroRow.Entry rowentry) throws IOException {
        // adds a new rowentry, but re-uses a previously as-deleted marked entry
        if (markedRemoved.size() == 0) {
            // no records there to be re-used
            Node n = newNode(rowentry.bytes());
            n.commit(CP_NONE);
            return n.handle().hashCode();
        } else {
            // re-use a removed record
            Integer index = (Integer) markedRemoved.first();
            markedRemoved.remove(index);
            set(index.intValue(), rowentry);
            return index.intValue();
        }
    }
    
    public synchronized void remove(int index, boolean marked) throws IOException {
        assert (index < (super.free() + super.size())) : "remove: index " + index + " out of bounds " + (super.free() + super.size());

        if (marked) {
            // does not remove directly, but sets only a mark that a record is to be deleted
            // this record can be re-used with add
            markedRemoved.add(new Integer(index));
        } else {
        
            // get the node at position index
            Handle h = new Handle(index);
            Node n = getNode(h, false);

            // erase the row
            n.setValueRow(null);
            n.commit(CP_NONE);
        
            // mark row as deleted so it can be re-used
            deleteNode(h);
        }
    }
    
    public synchronized void resolveMarkedRemoved() throws IOException {
        Iterator i = markedRemoved.iterator();
        Integer index;
        while (i.hasNext()) {
            index = (Integer) i.next();
            remove(index.intValue(), false);
        }
        markedRemoved.clear();
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
            k.remove(0, false);
            
            k.add(k.row().newEntry(new byte[][]{"c".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"d".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"e".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"f".getBytes(), "xxxx".getBytes()}));
            k.remove(0, false);
            k.remove(1, false);
            
            k.print();
            k.print(true);
            k.close();
            
            
            System.out.println("dritter Test");
            f.delete();
            k = new kelondroFixedWidthArray(f, rowdef, 6);
            for (int i = 1; i <= 200; i = i * 2) {
                for (int j = 0; j < i*2; j++) {
                    k.add(k.row().newEntry(new byte[][]{(Integer.toString(i) + "-" + Integer.toString(j)).getBytes(), "xxxx".getBytes()}));
                }
                for (int j = 0; j < i; j++) {
                    k.remove(j, true);
                }
            }
            k.resolveMarkedRemoved();
            k.print();
            k.print(true);
            k.close();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
