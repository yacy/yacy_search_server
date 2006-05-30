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

public class kelondroArray extends kelondroRecords {

    // define the Over-Head-Array
    private static short thisOHBytes   = 0; // our record definition does not need extra bytes
    private static short thisOHHandles = 0; // and two handles overhead for a double-chained list
    
    public kelondroArray(File file, int[] columns, int intprops, boolean exitOnFail) {
        // this creates a new array
        super(file, 0, thisOHBytes, thisOHHandles, columns, intprops, columns.length /* txtProps */, 80 /* txtPropWidth */, exitOnFail);
        for (int i = 0; i < intprops; i++) try {
            setHandle(i, new Handle(0));
        } catch (IOException e) {
            super.logFailure("cannot set handle " + i + " / " + e.getMessage());
            if (exitOnFail) System.exit(-1);
            throw new RuntimeException("cannot set handle " + i + " / " + e.getMessage());
        }
    }

    public kelondroArray(File file) throws IOException{
        // this opens a file with an existing array
        super(file, 0);
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
        byte[] before = n.setValueRow(rowentry.bytes());
        n.commit(CP_NONE);

        return row().newEntry(before);
    }
    
    public synchronized kelondroRow.Entry get(int index) throws IOException {
        if (index >= size()) throw new kelondroException(filename, "out of bounds, index=" + index + ", size=" + size());
        return row().newEntry(getNode(new Handle(index)).getValueRow());
    }

    public synchronized int seti(int index, int value) throws IOException {
        int before = getHandle(index).hashCode();
        setHandle(index, new Handle(value));
        return before;
    }

    public synchronized int geti(int index) {
        return getHandle(index).hashCode();
    }

    public synchronized int add(kelondroRow.Entry rowinstance) throws IOException {
        Node n = newNode();
        n.commit(CP_LOW);
        int index = n.handle().hashCode();
        set(index, rowinstance);
        return index;
    }

    public synchronized void remove(int index) throws IOException {
        deleteNode(new Handle(index));
    }
    
    public void print() throws IOException {
        System.out.println("PRINTOUT of table, length=" + size());
        kelondroRow.Entry row;
        for (int i = 0; i < size(); i++) {
            System.out.print("row " + i + ": ");
            row = get(i);
            for (int j = 0; j < columns(); j++) System.out.print(((row.empty(j)) ? "NULL" : row.getColString(j, "UTF-8")) + ", ");
            System.out.println();
        }
        System.out.println("EndOfTable");
    }
    
    private static void cmd(String[] args) {
        /*
         java -classpath classes de.anomic.kelondro.kelondroArray -c testarray.array 40
         java -classpath classes de.anomic.kelondro.kelondroArray -v testarray.array
         
         */
	System.out.print("kelondroArray ");
	for (int i = 0; i < args.length; i++) System.out.print(args[i] + " ");
	System.out.println("");
        try {
            if ((args.length == 3) && (args[0].equals("-c"))) {
                // create <filename> <valuelen> 
                File f = new File(args[1]);
                if (f.exists()) f.delete();
                kelondroArray fm = new kelondroArray(f, new int[]{Integer.parseInt(args[2])}, 2, true);
                fm.close();
            } else
            if ((args.length == 2) && (args[0].equals("-v"))) {
                // view <filename>
                kelondroArray fm = new kelondroArray(new File(args[1]));
                fm.print();
                fm.print(true);
                fm.close();
            } else
            if ((args.length == 3) && (args[0].equals("-g"))) {
                // get <filename> <index> 
                kelondroArray fm = new kelondroArray(new File(args[1]));
                kelondroRow.Entry row = fm.get(Integer.parseInt(args[2]));
                for (int j = 0; j < fm.columns(); j++) System.out.print(row.getColString(j, null) + " ");
                System.out.println();
                fm.close();
            } else
            if ((args.length == 4) && (args[0].equals("-s"))) {
                // set <filename> <index> <value>
                kelondroArray fm = new kelondroArray(new File(args[1]));
                kelondroRow.Entry row = fm.row().newEntry(new byte[][]{args[3].getBytes()});
                fm.set(Integer.parseInt(args[2]), row);
                fm.close();
            } else
            if ((args.length == 3) && (args[0].equals("-a"))) {
                // add <filename> <value>
                kelondroArray fm = new kelondroArray(new File(args[1]));
                kelondroRow.Entry row = fm.row().newEntry(new byte[][] {args[2].getBytes()});
                int index = fm.add(row);
                System.out.println("Added to row " + index);
                fm.close();
            } else
            if ((args.length == 3) && (args[0].equals("-d"))) {
                // delete <filename> <index>
                kelondroArray fm = new kelondroArray(new File(args[1]));
                fm.remove(Integer.parseInt(args[2]));
                fm.close();
            } else
            if ((args.length == 1) && (args[0].equals("-test"))) {
                File testfile = new File("test.array");
                if (testfile.exists()) testfile.delete();
                kelondroArray fm = new kelondroArray(testfile, new int[]{30, 50}, 9, true);
                for (int i = 0; i < 100; i++) {
                    fm.set(i, fm.row().newEntry(new byte[][]{("name" + i).getBytes(), ("value" + i).getBytes()}));
                }
                fm.close();
            } else
            {
                System.err.println("usage: kelondroArray -c|-v|-s|-g [file]|[index [value]] <db-file>");
                System.err.println("( create, view, set, get)");
                System.exit(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
	cmd(args);
    }
    
}
