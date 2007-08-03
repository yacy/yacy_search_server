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

public final class kelondroStack extends kelondroEcoRecords {

    // define the Over-Head-Array
    private static short thisOHBytes   = 0; // our record definition does not need extra bytes
    private static short thisOHHandles = 2; // and two handles overhead for a double-chained list
    private static short thisFHandles  = 2; // two file handles for root handle and handle to last lement

    // define pointers for OH array access
    protected static final int left  = 0; // pointer for OHHandle-array: handle()-Value of left child Node
    protected static final int right = 1; // pointer for OHHandle-array: handle()-Value of right child Node
    protected static final int root  = 0; // pointer for FHandles-array: pointer to root node
    protected static final int toor  = 1; // pointer for FHandles-array: pointer to root node

    public kelondroStack(File file, kelondroRow rowdef) throws IOException {
        // this creates a new stack
        super(file, thisOHBytes, thisOHHandles, rowdef, thisFHandles, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
        if (super.fileExisted) {
            //if ((getHandle(root) == null) && (getHandle(toor) == null)) clear();
        } else {
            setHandle(root, null); // define the root value
            setHandle(toor, null); // define the toor value
        }
    }

    public static final kelondroStack open(File file, kelondroRow rowdef) {
        try {
            return new kelondroStack(file, rowdef);
        } catch (IOException e) {
            file.delete();
            try {
                return new kelondroStack(file, rowdef);
            } catch (IOException ee) {
                System.out.println("kelondroStack: cannot open or create file " + file.toString());
                e.printStackTrace();
                ee.printStackTrace();
                return null;
            }
        }
    }
    
    public static kelondroStack reset(kelondroStack stack) {
        // memorize settings to this file
        File f = new File(stack.filename);
        kelondroRow row = stack.row();
        
        // close and delete the file
        try {stack.close();} catch (Exception e) {}
        if (f.exists()) f.delete();

        // re-open a database with same settings as before
        return open(f, row);
    }

    public Iterator stackIterator(boolean up) {
        // iterates the elements in an ordered way.
        // returns kelondroRow.Entry - type Objects
        return new stackIterator(up);
    }

    public class stackIterator implements Iterator {
        kelondroHandle nextHandle = null;
        kelondroHandle lastHandle = null;
        boolean up;

        public stackIterator(boolean up) {
            this.up = up;
            nextHandle = getHandle((up) ? root : toor);
        }

        public boolean hasNext() {
            return (nextHandle != null);
        }

        public Object next() {
        	lastHandle = nextHandle;
            try {
                nextHandle = new EcoNode(nextHandle).getOHHandle((up) ? right : left);
                return row().newEntry(new EcoNode(lastHandle).getValueRow());
            } catch (IOException e) {
                throw new kelondroException(filename, "IO error at Counter:next()");
            }
        }

        public void remove() {
        	try {
				unlinkNode(new EcoNode(lastHandle));
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }

    public int size() {
        return super.size();
    }
    
    public synchronized void push(kelondroRow.Entry row) throws IOException {
        // check if there is already a stack
        if (getHandle(toor) == null) {
            if (getHandle(root) != null) throw new RuntimeException("push: internal organisation of root and toor");
            // create node
            kelondroNode n = new EcoNode(row.bytes());
            n.setOHHandle(left, null);
            n.setOHHandle(right, null);
            n.commit();
            // assign handles
            setHandle(root, n.handle());
            setHandle(toor, n.handle());
            // thats it
        } else {
            // expand the list at the end
            kelondroNode n = new EcoNode(row.bytes());
            n.setOHHandle(left, getHandle(toor));
            n.setOHHandle(right, null);
            kelondroNode n1 = new EcoNode(getHandle(toor));
            n1.setOHHandle(right, n.handle());
            n.commit();
            n1.commit();
            // assign handles
            setHandle(toor, n.handle());
            // thats it
        }
    }

    public synchronized kelondroRow.Entry pop() throws IOException {
        // return row ontop of the stack and shrink stack by one
        kelondroNode n = topNode();
        if (n == null) return null;
        kelondroRow.Entry ret = row().newEntry(n.getValueRow());

        // remove node
        unlinkNode(n);
        deleteNode(n.handle());

        return ret;
    }
    
    public synchronized kelondroRow.Entry top() throws IOException {
        // return row ontop of the stack
        kelondroNode n = topNode();
        if (n == null) return null;
        return row().newEntry(n.getValueRow());
    }

    public synchronized kelondroRow.Entry pot() throws IOException {
        // return row on the bottom of the stack and remove record
        kelondroNode n = botNode();
        if (n == null) return null;
        kelondroRow.Entry ret = row().newEntry(n.getValueRow());

        // remove node
        unlinkNode(n);
        deleteNode(n.handle());

        return ret;
    }
    
    public synchronized kelondroRow.Entry bot() throws IOException {
        // return row on the bottom of the stack
        kelondroNode n = botNode();
        if (n == null) return null;
        return row().newEntry(n.getValueRow());
    }
    
    private void unlinkNode(kelondroNode n) throws IOException {
        // join chaines over node
        kelondroHandle l = n.getOHHandle(left);
        kelondroHandle r = n.getOHHandle(right);
        // look left
        if (l == null) {
            // reached the root on left side
            setHandle(root, r);
        } else {
            // un-link the previous record
            kelondroNode k = new EcoNode(l);
            k.setOHHandle(left, k.getOHHandle(left));
            k.setOHHandle(right, r);
            k.commit();
        }
        // look right
        if (r == null) {
            // reached the root on right side
            setHandle(toor, l);
        } else {
            // un-link the following record
            kelondroNode k = new EcoNode(r);
            k.setOHHandle(left, l);
            k.setOHHandle(right, k.getOHHandle(right));
            k.commit();
        }
    }
    
    private kelondroNode topNode() throws IOException {
        // return node ontop of the stack
        if (size() == 0) return null;
        kelondroHandle h = getHandle(toor);
        if (h == null) return null;
        return new EcoNode(h);
    }
    
    private kelondroNode botNode() throws IOException {
        // return node on bottom of the stack
        if (size() == 0) return null;
        kelondroHandle h = getHandle(root);
        if (h == null) return null;
        return new EcoNode(h);
    }
    
    public int imp(File file, String separator) throws IOException {
        // imports a value-separated file, returns number of records that have been read
        RandomAccessFile f = null;
        try {
            f = new RandomAccessFile(file,"r");
            String s;
            StringTokenizer st;
            int recs = 0;
            kelondroRow.Entry buffer = row().newEntry();
            int c;
            int line = 0;
            while ((s = f.readLine()) != null) {
                s = s.trim();
                line++;
                if ((s.length() > 0) && (!(s.startsWith("#")))) {
                    st = new StringTokenizer(s, separator);
                    // buffer the entry
                    c = 0;
                    while ((c < row().columns()) && (st.hasMoreTokens())) {
                        buffer.setCol(c++, st.nextToken().trim().getBytes());
                    }
                    if ((st.hasMoreTokens()) || (c != row().columns())) {
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

    public String hp(kelondroHandle h) {
        if (h == null)
            return "NULL";
        else
            return h.toString();
    }

    public void print() throws IOException {
        super.print();
        Iterator it = stackIterator(true);
        kelondroRow.Entry r;
        while (it.hasNext()) {
            r = (kelondroRow.Entry) it.next();
            System.out.print("  KEY:'" + r.getColString(0, null) + "'");
            for (int j = 1; j < row().columns(); j++)
                System.out.print(", V[" + j + "]:'" + r.getColString(j, null) + "'");
            System.out.println();
        }
        System.out.println();
    }

    private static void cmd(String[] args) {
	System.out.print("kelondroStack ");
	for (int i = 0; i < args.length; i++) System.out.print(args[i] + " ");
	System.out.println("");
	byte[] ret = null;
    kelondroRow lens = new kelondroRow("byte[] key-" + Integer.parseInt(args[1]) + ", byte[] value-" + Integer.parseInt(args[2]), kelondroNaturalOrder.naturalOrder, 0);
	try {
	    if ((args.length > 4) || (args.length < 2)) {
		System.err.println("usage: kelondroStack -c|-p|-v|-g|-i|-s [file]|[key [value]] <db-file>");
		System.err.println("( create, push, view, (g)pop, imp, shell)");
		System.exit(0);
	    } else if (args.length == 2) {
		kelondroStack fm = new kelondroStack(new File(args[1]), lens);
		if (args[0].equals("-v")) {
		    fm.print();
		    ret = null;
		} else if (args[0].equals("-g")) {
		    fm = new kelondroStack(new File(args[1]), lens);
		    kelondroRow.Entry ret2 = fm.pop();
		    ret = ((ret2 == null) ? null : ret2.getColBytes(1)); 
		    fm.close();
		}
		fm.close();
	    } else if (args.length == 3) {
		if (args[0].equals("-i")) {
		    kelondroStack fm = new kelondroStack(new File(args[2]), lens);
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
		    kelondroStack fm = new kelondroStack(new File(args[2]), lens);
            kelondroRow.Entry ret2 = fm.pop();
		    ret = ((ret2 == null) ? null : ret2.getColBytes(1)); 
		    fm.close();
		}
	    } else if (args.length == 4) {
		if (args[0].equals("-c")) {
		    // create <keylen> <valuelen> <filename>
		    File f = new File(args[3]);
		    if (f.exists()) f.delete();
		    kelondroStack fm = new kelondroStack(f, lens);
		    fm.close();
		} else if (args[0].equals("-p")) {
		    kelondroStack fm = new kelondroStack(new File(args[3]), lens);
		    fm.push(fm.row().newEntry(new byte[][] {args[1].getBytes(), args[2].getBytes()}));
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
        /*
        kelondroStack s = new kelondroStack(new File("/Users/admin/dev/yacy/trunk/test.stack"), 1024, new int[]{10,10}, false);
        try {
            s.push(s.row().newEntry(new byte[][]{"test123".getBytes(), "abcdefg".getBytes()}));
            s.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        */
    }
    
}
