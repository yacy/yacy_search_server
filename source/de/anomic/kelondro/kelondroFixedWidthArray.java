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

public class kelondroFixedWidthArray extends kelondroRecords implements kelondroArray {

    // define the Over-Head-Array
    private static short thisOHBytes   = 0; // our record definition does not need extra bytes
    private static short thisOHHandles = 0; // and no handles
    
    public kelondroFixedWidthArray(File file, kelondroRow rowdef, int intprops) throws IOException {
        // this creates a new array
        super(file, 0, 0, thisOHBytes, thisOHHandles, rowdef, intprops, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
        if (!(super.fileExisted)) {
            for (int i = 0; i < intprops; i++) {
                setHandle(i, new Handle(0));
            }
            // store column description
            for (int i = 0; i < rowdef.columns(); i++) {
                try {super.setText(i, rowdef.column(i).toString().getBytes());} catch (IOException e) {}
            }
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
    
    public synchronized kelondroRow.Entry set(int index, kelondroRow.Entry rowentry) throws IOException {

        // make room for element
        Node n;
        while (size() <= index) {
            n = newNode();
            n.commit(CP_NONE);
        }

        // get the node at position index
        n = getNode(new Handle(index));

        // write the row
        byte[] before = n.setValueRow((rowentry == null) ? null : rowentry.bytes());
        n.commit(CP_NONE);

        return row().newEntry(before);
    }
    
    public synchronized kelondroRow.Entry get(int index) throws IOException {
        return row().newEntry(getNode(new Handle(index)).getValueRow());
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

        Node n = newNode();
        n.setValueRow(rowentry.bytes());
        n.commit(CP_NONE);

        return n.handle().hashCode();
    }

    public synchronized void remove(int index) throws IOException {
        if (index >= super.USAGE.allCount()) throw new IOException("remove: index " + index + " out of bounds " + super.USAGE.allCount());

        // get the node at position index
        Handle h = new Handle(index);
        Node n = getNode(h);

        // erase the row
        n.setValueRow(null);
        n.commit(CP_NONE);
        
        // mark row as deleted so it can be re-used
        deleteNode(h);
    }
    
    public void print() throws IOException {
        System.out.println("PRINTOUT of table, length=" + size());
        kelondroRow.Entry row;
        for (int i = 0; i < super.USAGE.allCount(); i++) {
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
        kelondroRow rowdef = new kelondroRow("byte[] a-12, byte[] b-4");
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
            System.out.println(k.get(4).toString());
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
                    k.remove(j);
                }
            }
            k.print();
            k.print(true);
            k.close();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
