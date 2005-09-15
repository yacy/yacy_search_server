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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
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
        if ((getHandle(root) == null) && (getHandle(toor) == null)) clear();
    }

    public void clear() throws IOException {
        super.clear();
        setHandle(root, null); // reset the root value
	setHandle(toor, null); // reset the toor value
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
		nextHandle = getNode(nextHandle, null, 0).getOHHandle(right);
                return getNode(ret, null, 0);
	    } catch (IOException e) {
                throw new kelondroException(filename, "IO error at Counter:next()");
	    }
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
	    Node n = newNode();
            n.setValues(row);
	    n.setOHHandle(left, null);
            n.setOHHandle(right, null);
	    n.commit(CP_NONE);
	    // assign handles
	    setHandle(root, n.handle());
	    setHandle(toor, n.handle());
	    // thats it
	} else {
	    // expand the list at the end
	    Node n = newNode();
            n.setValues(row);
	    n.setOHHandle(left, getHandle(toor));
            n.setOHHandle(right, null);
	    Node n1 = getNode(getHandle(toor), null, 0);
            n1.setOHHandle(right, n.handle());
	    n.commit(CP_NONE);
	    n1.commit(CP_NONE);
	    // assign handles
	    setHandle(toor, n.handle());
	    // thats it
	}
    }

    public synchronized byte[][] pop() throws IOException {
	// return row ontop of the stack and shrink stack by one
	return pop(0);
    }

    public synchronized byte[][] pop(int dist) throws IOException {
	// return row relative to top of the stack and remove addressed element
	Node n = topNode(dist);
        if (n == null) return null;
        byte[][] ret = n.getValues();

        // remove node
        unlinkNode(n);
	deleteNode(n.handle());
        
	return ret;
    }
    
    public synchronized byte[][] top() throws IOException {
	// return row ontop of the stack
	return top(0);
    }

    public synchronized byte[][] top(int dist) throws IOException {
	// return row ontop of the stack
	// with dist == 0 this is the same function as with top()
        Node n = topNode(dist);
        if (n == null) return null;
        return n.getValues();
    }

    public synchronized byte[][] pot() throws IOException {
	// return row on the bottom of the stack and remove record
	return pot(0);
    }

    public synchronized byte[][] pot(int dist) throws IOException {
	// return row relative to the bottom of the stack and remove addressed element
	Node n = botNode(dist);
        if (n == null) return null;
	byte[][] ret = n.getValues();
        
        // remove node
        unlinkNode(n);
	deleteNode(n.handle());
        
	return ret;
    }

    public synchronized byte[][] bot() throws IOException {
	// return row on the bottom of the stack
	return bot(0);
    }

    public synchronized byte[][] bot(int dist) throws IOException {
	// return row on bottom of the stack
	// with dist == 0 this is the same function as with bot()
	Node n = botNode(dist);
	if (n == null) return null;
        return n.getValues();
    }
    
    public synchronized ArrayList botList(int dist) throws IOException {
        ArrayList botList = new ArrayList(size());
        for (int i=dist; i < size(); i++) {
            botList.add(bot(i));
        }
        return botList;
    }
    
    private void unlinkNode(Node n) throws IOException {
        // join chaines over node
	Handle l = n.getOHHandle(left);
        Handle r = n.getOHHandle(right);
        // look left
	if (l == null) {
	    // reached the root on left side
	    setHandle(root, r);
	} else {
	    // un-link the previous record
	    Node k = getNode(l, null, 0);
	    k.setOHHandle(left, k.getOHHandle(left));
            k.setOHHandle(right, r);
            k.commit(CP_NONE);
	}
        // look right
        if (r == null) {
	    // reached the root on right side
	    setHandle(toor, l);
	} else {
	    // un-link the following record
	    Node k = getNode(r, null, 0);
	    k.setOHHandle(left, l);
            k.setOHHandle(right, k.getOHHandle(right));
            k.commit(CP_NONE);
	}
    }
    
    private Node topNode(int dist) throws IOException {
	// return node ontop of the stack
        return queueNode(dist, toor, left);
    }
    
    private Node botNode(int dist) throws IOException {
	// return node on bottom of the stack
        return queueNode(dist, root, right);
    }
    
    private Node queueNode(int dist, int side, int dir) throws IOException {
	// with dist == 0 this is the same function as with getNode(getHandle(side), null, 0)
	Handle h = getHandle(side);
	if (h == null) return null;
	if (dist >= size()) return null; // that would exceed the stack
	while (dist-- > 0) h = getNode(h, null, 0).getOHHandle(dir); // track through elements
	return getNode(h, null, 0);
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
        RandomAccessFile f = null;
        try {
            f = new RandomAccessFile(file,"r");
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
        } finally {
            if (f!=null) try{f.close();}catch(Exception e){}
        }
    }

    public String hp(Handle h) {
	if (h == null) return "NULL"; else return h.toString();
    }

    public void print() throws IOException {
	super.print(false);
	Node n;
	try {
	    Iterator it = iterator();
	    while (it.hasNext()) {
		n = (Node) it.next();
		//n = getNode(h, null, 0);
		System.out.println("> NODE " + hp(n.handle()) +
				   "; left " + hp(n.getOHHandle(left)) + ", right " + hp(n.getOHHandle(right)));
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
		    BufferedReader f = null;
            try {
                f = new BufferedReader(new FileReader(args[1]));
                
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
            } finally {
                if (f != null) try {f.close();}catch(Exception e) {}
            }
		} else if (args[0].equals("-g")) {
		    kelondroStack fm = new kelondroStack(new File(args[2]), 0x100000);
		    byte[][] ret2 = fm.pop(Integer.parseInt(args[1]));
		    ret = ((ret2 == null) ? null : ret2[1]); 
		    fm.close();
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
        // -c 10 20 test.stack
        // -p a a1 test.stack
        // -p b b1 test.stack
        // -p c c1 test.stack
        // -v test.stack
        // -g test.stack
        // -v test.stack
        // -g 1 test.stack
	cmd(args);
    }
    
}
