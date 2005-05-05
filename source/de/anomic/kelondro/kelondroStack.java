// kelondroStack.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 11.01.2004
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
  This class extends the kelondroRecords and adds a stack structure
*/

package de.anomic.kelondro;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.StringTokenizer;

public class kelondroStack extends kelondroRecords {

    // define the Over-Head-Array
    private static short thisOHBytes   = 0; // our record definition does not need extra bytes
    private static short thisOHHandles = 2; // and two handles overhead for a double-chained list
    private static short thisFHandles  = 2; // two file handles for root handle and handle to last lement

    // define pointers for OH array access
    private static int left  = 0; // pointer for OHHandle-array: handle()-Value of left child Node
    private static int right = 1; // pointer for OHHandle-array: handle()-Value of right child Node
    private static int root  = 0; // pointer for FHandles-array: pointer to root node
    private static int toor  = 1; // pointer for FHandles-array: pointer to root node

    public kelondroStack(File file, long buffersize, int key, int value) throws IOException {
	this(file, buffersize, new int[] {key, value});
    }

    public kelondroStack(File file, long buffersize, int[] columns) throws IOException {
	// this creates a new tree
	super(file, buffersize, thisOHBytes, thisOHHandles, columns, thisFHandles, columns.length /*txtProps*/, 80 /*txtPropWidth*/);
	setHandle(root, null); // define the root value
	setHandle(toor, null); // define the toor value
    }

    public kelondroStack(File file, long buffersize) throws IOException{
	// this opens a file with an existing tree
	super(file, buffersize);
    }

    public class Counter implements Iterator {
	Handle nextHandle = null;
	public Counter() throws IOException {
	    nextHandle = getHandle(root);
	}
	public boolean hasNext() {
	    return (nextHandle != null);
	}
	public Object next() {
	    Handle ret = nextHandle;
	    try {
		nextHandle = getNode(nextHandle, null, 0).getOHHandle()[right];
	    } catch (IOException e) {
		System.err.println("IO error at Counter:next()");
	    }
	    return getNode(ret, null, 0);
	}
	public void remove() {
	    throw new UnsupportedOperationException("no remove here..");
	}
    }

    
    public synchronized void push(byte[][] row) throws IOException {
	if (row.length != columns()) throw new IllegalArgumentException("push: wrong row length " + row.length + "; must be " + columns());
	// check if there is already a stack
	if (getHandle(toor) == null) {
	    if (getHandle(root) != null) throw new RuntimeException("push: internal organisation of root and toor");
	    // create node
	    Node n = newNode(row);
	    n.save();
	    n.setOHHandle(new Handle[] {null, null});
	    n.setValues(row);
	    // assign handles
	    setHandle(root, n.handle());
	    setHandle(toor, n.handle());
	    // thats it
	} else {
	    // expand the list at the end
	    Node n = newNode(row);
	    n.save();
	    n.setOHHandle(new Handle[] {getHandle(toor),null});
	    Node n1 = getNode(getHandle(toor), null, 0);
	    n1.setOHHandle(new Handle[] {n1.getOHHandle()[left], n.handle()});
	    // assign handles
	    setHandle(toor, n.handle());
	    // thats it
	}
    }

    public synchronized byte[][] pop() throws IOException {
	// return row ontop of the stack and shrink stack by one
	Handle h = getHandle(toor);
	if (h == null) return null;
	Node n = getNode(h, null, 0);
	byte[][] ret = n.getValues();
	// shrink stack
	Handle l = n.getOHHandle()[left];
	if (l == null) {
	    // the stack will be empty, write the root handle
	    setHandle(root, null);
	} else {
	    // un-link the previous record
	    Node k = getNode(l, null, 0);
	    k.setOHHandle(new Handle[] {k.getOHHandle()[left], null});
	}
	setHandle(toor, l);
	deleteNode(h);
	return ret;
    }

    public synchronized byte[][] top() throws IOException {
	// return row ontop of the stack
	Handle h = getHandle(toor);
	if (h == null) return null;
	return getNode(h, null, 0).getValues();
    }

    public synchronized byte[][] top(int dist) throws IOException {
	// return row ontop of the stack
	// with dist == 0 this is the same function as with top()
	Handle h = getHandle(toor);
	if (h == null) return null;
	if (dist >= size()) return null; // that would exceed the stack
	while (dist-- > 0) h = getNode(h, null, 0).getOHHandle()[left]; // track through elements
	return getNode(h, null, 0).getValues();
    }

    public synchronized byte[][] pot() throws IOException {
	// return row on the bottom of the stack and remove record
	Handle h = getHandle(root);
	if (h == null) return null;
	Node n = getNode(h, null, 0);
	byte[][] ret = n.getValues();
	// shrink stack
	Handle r = n.getOHHandle()[right];
	if (r == null) {
	    // the stack will be empty, write the toor handle
	    setHandle(toor, null);
	} else {
	    // un-link the next record
	    Node k = getNode(r, null, 0);
	    k.setOHHandle(new Handle[] {null, k.getOHHandle()[right]});
	}
	setHandle(root, r);
	deleteNode(h);
	return ret;
    }

    public synchronized byte[][] bot() throws IOException {
	// return row on the bottom of the stack
	Handle h = getHandle(root);
	if (h == null) return null;
	return getNode(h, null, 0).getValues();
    }

    public synchronized byte[][] bot(int dist) throws IOException {
	// return row on bottom of the stack
	// with dist == 0 this is the same function as with bot()
	Handle h = getHandle(root);
	if (h == null) return null;
	if (dist >= size()) return null; // that would exceed the stack
	while (dist-- > 0) h = getNode(h, null, 0).getOHHandle()[right]; // track through elements
	return getNode(h, null, 0).getValues();
    }

    /*
    public synchronized byte[][] seekPop(byte[] key, long maxdepth) throws IOException {
    }

    public synchronized byte[][] seekPot(byte[] key, long maxdepth) throws IOException {
    }
    */

    public Iterator iterator() {
	// iterates the elements in an ordered way. returns Node - type Objects
	try {
	    return new Counter();
	} catch (IOException e) {
	    throw new RuntimeException("error creating an iteration: " + e.getMessage());
	}
    }

    public int imp(File file, String separator) throws IOException {
	// imports a value-separated file, returns number of records that have been read
	RandomAccessFile f = new RandomAccessFile(file,"r");
	String s;
	StringTokenizer st;
	int recs = 0;
	byte[][] buffer = new byte[columns()][];
	int c;
	int line = 0;
	while ((s = f.readLine()) != null) {
	    s = s.trim();
	    line++;
	    if ((s.length() > 0) && (!(s.startsWith("#")))) {
		st = new StringTokenizer(s, separator);
		// buffer the entry
		c = 0;
		while ((c < columns()) && (st.hasMoreTokens())) {
		    buffer[c++] = st.nextToken().trim().getBytes();
		}
		if ((st.hasMoreTokens()) || (c != columns())) {
		    System.err.println("inapropriate number of entries in line " + line);
		} else {
		    push(buffer);
		    recs++;
		}

	    }
	}
	return recs;
    }

    public String hp(Handle h) {
	if (h == null) return "NULL"; else return h.toString();
    }

    public void print() {
	super.print(false);
	Handle h;
	Node n;
	try {
	    Iterator it = iterator();
	    while (it.hasNext()) {
		h = (Handle) it.next();
		n = getNode(h, null, 0);
		System.out.println("> NODE " + hp(h) +
				   "; left " + hp(n.getOHHandle()[left]) + ", right " + hp(n.getOHHandle()[right]));
		System.out.print("  KEY:'" + (new String(n.getValues()[0])).trim() + "'");
		for (int j = 1; j < columns(); j++)
		    System.out.print(", V[" + j + "]:'" + (new String(n.getValues()[j])).trim() + "'");
		System.out.println();
	    }
	    System.out.println();
	} catch (IOException e) {
	    System.out.println("File error: " + e.getMessage());
	}
    }

    private static void cmd(String[] args) {
	System.out.print("kelondroStack ");
	for (int i = 0; i < args.length; i++) System.out.print(args[i] + " ");
	System.out.println("");
	byte[] ret = null;
	try {
	    if ((args.length > 4) || (args.length < 2)) {
		System.err.println("usage: kelondroStack -c|-p|-v|-g|-i|-s [file]|[key [value]] <db-file>");
		System.err.println("( create, push, view, (g)pop, imp, shell)");
		System.exit(0);
	    } else if (args.length == 2) {
		kelondroStack fm = new kelondroStack(new File(args[1]), 0x100000);
		if (args[0].equals("-v")) {
		    fm.print();
		    ret = null;
		} else if (args[0].equals("-g")) {
		    fm = new kelondroStack(new File(args[1]), 0x100000);
		    byte[][] ret2 = fm.pop();
		    ret = ((ret2 == null) ? null : ret2[1]); 
		    fm.close();
		}
		fm.close();
	    } else if (args.length == 3) {
		if (args[0].equals("-i")) {
		    kelondroStack fm = new kelondroStack(new File(args[2]), 0x100000);
		    int i = fm.imp(new File(args[1]),";");
		    fm.close();
		    ret = (i + " records imported").getBytes();
		} else if (args[0].equals("-s")) {
		    String db = args[2];
		    BufferedReader f = new BufferedReader(new FileReader(args[1]));
		    String m;
		    while (true) {
			m = f.readLine();
			if (m == null) break;
			if ((m.length() > 1) && (!m.startsWith("#"))) {
			    m = m + " " + db;
			    cmd(line2args(m));
			}
		    }
		    ret = null;
		}
	    } else if (args.length == 4) {
		if (args[0].equals("-c")) {
		    // create <keylen> <valuelen> <filename>
		    File f = new File(args[3]);
		    if (f.exists()) f.delete();
		    int[] lens = new int[2];
		    lens[0] = Integer.parseInt(args[1]);
		    lens[1] = Integer.parseInt(args[2]);
		    kelondroStack fm = new kelondroStack(f, 0x100000, lens);
		    fm.close();
		} else if (args[0].equals("-p")) {
		    kelondroStack fm = new kelondroStack(new File(args[3]), 0x100000);
		    fm.push(new byte[][] {args[1].getBytes(), args[2].getBytes()});
		    fm.close();
		}
	    }
	    if (ret == null)
		System.out.println("NULL");
	    else
		System.out.println(new String(ret));
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    
    public static void main(String[] args) {
	cmd(args);
    }
    
}
