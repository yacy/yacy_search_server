// kelondroTree.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 07.02.2005
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
  This class extends the kelondroRecords and adds a tree structure
*/

package de.anomic.kelondro;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Logger;

import de.anomic.kelondro.kelondroRow.Entry;
import de.anomic.server.logging.serverLog;

public class kelondroTree extends kelondroCachedRecords implements kelondroIndex {
	
    // logging (This probably needs someone to initialize the java.util.logging.* facilities);
    public static final Logger log = Logger.getLogger("KELONDRO");

    // define the Over-Head-Array
    protected static final short thisOHBytes   = 2; // our record definition of two bytes
    protected static final short thisOHHandles = 3; // and three handles overhead
    protected static final short thisFHandles  = 1; // file handles: one for a root pointer

    // define pointers for OH array access
    protected static final int magic      = 0; // pointer for OHByte-array: marker for Node purpose; defaults to 1
    protected static final int balance    = 1; // pointer for OHByte-array: balance value of tree node; balanced = 0

    protected static final int parent     = 0; // pointer for OHHandle-array: handle()-Value of parent Node
    protected static final int leftchild  = 1; // pointer for OHHandle-array: handle()-Value of left child Node
    protected static final int rightchild = 2; // pointer for OHHandle-array: handle()-Value of right child Node

    protected static final int root       = 0; // pointer for FHandles-array: pointer to root node
    
    // class variables
    private   final Search             writeSearchObj = new Search();
    protected       Comparator<String> loopDetectionOrder;
    protected       int                readAheadChunkSize = 100;
    protected       long               lastIteratorCount = readAheadChunkSize;
    
    public kelondroTree(File file, boolean useNodeCache, long preloadTime, kelondroRow rowdef) throws IOException {
        this(file, useNodeCache, preloadTime, rowdef, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
    }

    public kelondroTree(File file, boolean useNodeCache, long preloadTime, kelondroRow rowdef, int txtProps, int txtPropsWidth) throws IOException {
        // opens an existing tree file or creates a new tree file
        super(file, useNodeCache, preloadTime,
              thisOHBytes, thisOHHandles, rowdef,
              thisFHandles, txtProps, txtPropsWidth);
        if (!fileExisted) {
            // create new file structure
            setHandle(root, null); // define the root value
        }
        super.setLogger(log);
        this.loopDetectionOrder = new kelondroByteOrder.StringOrder(rowdef.objectOrder);
    }
    
    public static final kelondroTree open(File file, boolean useNodeCache, long preloadTime, kelondroRow rowdef) {
        return open(file, useNodeCache, preloadTime, rowdef, rowdef.columns() /* txtProps */, 80 /* txtPropWidth */);
    }
    
    public static final kelondroTree open(File file, boolean useNodeCache, long preloadTime, kelondroRow rowdef, int txtProps, int txtPropsWidth) {
        // opens new or existing file; in case that any error occur the file is deleted again and it is tried to create the file again
        // if that fails, the method returns null
        try {
            return new kelondroTree(file, useNodeCache, preloadTime, rowdef, txtProps, txtPropsWidth);
        } catch (IOException e) {
            file.delete();
            try {
                return new kelondroTree(file, useNodeCache, preloadTime, rowdef, txtProps, txtPropsWidth);
            } catch (IOException ee) {
                log.severe("cannot open or create file " + file.toString());
                e.printStackTrace();
                ee.printStackTrace();
                return null;
            }
        }
    }
    
    public kelondroTree(kelondroRA ra, String filename, boolean useNodeCache, long preloadTime, kelondroRow rowdef, boolean exitOnFail) {
        // this creates a new tree within a kelondroRA
        this(ra, filename, useNodeCache, preloadTime, rowdef, new kelondroNaturalOrder(true), rowdef.columns() /* txtProps */, 80 /* txtPropWidth */, exitOnFail);
    }

    public kelondroTree(kelondroRA ra, String filename, boolean useNodeCache, long preloadTime, kelondroRow rowdef, kelondroByteOrder objectOrder, int txtProps, int txtPropsWidth, boolean exitOnFail) {
        // this creates a new tree within a kelondroRA
        super(ra, filename, useNodeCache, preloadTime,
              thisOHBytes, thisOHHandles, rowdef,
              thisFHandles, txtProps, txtPropsWidth, exitOnFail);
        try {
            setHandle(root, null); // define the root value
        } catch (IOException e) {
            super.logFailure("cannot set root handle / " + e.getMessage());
            if (exitOnFail) System.exit(-1);
            throw new RuntimeException("cannot set root handle / " + e.getMessage());
        }
        super.setLogger(log);
        this.loopDetectionOrder = new kelondroByteOrder.StringOrder(rowdef.objectOrder);
    }
    
    public kelondroTree(kelondroRA ra, String filename, boolean useNodeCache, long preloadTime) throws IOException {
        // this opens a file with an existing tree in a kelondroRA
        super(ra, filename, useNodeCache, preloadTime);
        super.setLogger(log);
    }

    public void clear() throws IOException {
    	super.clear();
        setHandle(root, null); 
	}
    
    private void commitNode(kelondroNode n) throws IOException {
        //kelondroHandle left = n.getOHHandle(leftchild);
        //kelondroHandle right = n.getOHHandle(rightchild);
        n.commit();
    }

    public boolean has(byte[] key) {
        throw new UnsupportedOperationException("has should not be used with kelondroTree.");
    }
    
    // Returns the value to which this map maps the specified key.
    public kelondroRow.Entry get(byte[] key) throws IOException {
        kelondroRow.Entry result;
        synchronized (writeSearchObj) {
            writeSearchObj.process(key);
            if (writeSearchObj.found()) {
                result = row().newEntry(writeSearchObj.getMatcher().getValueRow());
            } else {
                result = null;
            }
        }
        return result;
    }
    
    public ArrayList<kelondroRowCollection> removeDoubles() {
        // this data structure cannot have doubles; return empty array
        return new ArrayList<kelondroRowCollection>();
    }
    
    public class Search {

        // a search object combines the results of a search in the tree, which are
        // - the searched object is found, an index pointing to the node can be returned
        // - the object was not found, an index pointing to an appropriate possible parent node
        //   can be returned, together with the information wether the new key shall
        //   be left or right child.

        private CacheNode thenode, parentnode;
        private boolean found; // property if node was found
        private byte child; // -1: left child; 0: root node; 1: right child

        // temporary variables
        private kelondroHandle thisHandle;
        String keybuffer;
        
        protected Search() {
        }

        protected void process(byte[] key) throws IOException {
            // searchs the database for the key and stores the result in the thisHandle
            // if the key was found, then found=true, thisHandle and leftchild is set;
            // else found=false and thisHandle and leftchild is undefined
            thisHandle = getHandle(root);
            parentnode = null;
            if (key == null) {
                throw new kelondroException("startet search process with key == null");
                /*
                child = 0;
                if (thisHandle == null) {
                    thenode = null;
                    found = false;
                } else {
                    thenode = getNode(thisHandle, null, 0);
                    found = true;
                }
                return;
                */
            }
            thenode = null;
            child = 0;
            found = false;
            int c;
            
            TreeSet<String> visitedNodeKeys = new TreeSet<String>(loopDetectionOrder); // to detect loops
            // System.out.println("Starting Compare Loop in Database " + filename); // debug
            while (thisHandle != null) {
                try {
                    parentnode = thenode;
                    thenode = new CacheNode(thisHandle, thenode, (child == -1) ? leftchild : rightchild, true);
                } catch (IllegalArgumentException e) {
                    logWarning("kelondroTree.Search.process: fixed a broken handle");
                    found = false;
                    return;
                }
                if (thenode == null) throw new kelondroException(filename, "kelondroTree.Search.process: thenode==null");

                keybuffer = new String(thenode.getKey());
                if (keybuffer == null) {
                    // this is an error. distinguish two cases:
                    // 1. thenode is a leaf node. Then this error can be fixed if we can consider this node as a good node to be replaced with a new value
                    // 2. thenode is not a leaf node. An exception must be thrown
                    if ((thenode.getOHHandle(leftchild) == null) && (thenode.getOHHandle(rightchild) == null)) {
                        // case 1: recover
                        deleteNode(thisHandle);
                        thenode = parentnode;
                        found = false;
                        return;
                    } else {
                        // case 2: fail
                        throw new kelondroException("found key during search process with key == null");
                    }
                }
                if (visitedNodeKeys.contains(keybuffer)) {
                    // we have loops in the database.
                    // to fix this, all affected nodes must be patched
                    thenode.setOHByte(magic, (byte) 1);
                    thenode.setOHByte(balance, (byte) 0);
                    thenode.setOHHandle(parent, null);
                    thenode.setOHHandle(leftchild, null);
                    thenode.setOHHandle(rightchild, null);
                    thenode.commit();
                    logWarning("kelondroTree.Search.process: database contains loops; the loop-nodes have been auto-fixed");
                    found = false;
                    return;
                }
                // System.out.println("Comparing key = '" + new String(key) + "' with '" + otherkey + "':"); // debug
                c = row().objectOrder.compare(key, keybuffer.getBytes());
                // System.out.println(c); // debug
                if (c == 0) {
                    found = true;
                    // System.out.println("DEBUG: search for " + new String(key) + " ended with status=" + ((found) ? "found" : "not-found") + ", node=" + ((thenode == null) ? "NULL" : thenode.toString()) + ", parent=" + ((parentnode == null) ? "NULL" : parentnode.toString()));
                    return;
                } else if (c < 0) {
                    child = -1;
                    thisHandle = thenode.getOHHandle(leftchild);
                } else {
                    child = 1;
                    thisHandle = thenode.getOHHandle(rightchild);
                }
                visitedNodeKeys.add(keybuffer);
            }
            // System.out.println("DEBUG: search for " + new String(key) + " ended with status=" + ((found) ? "found" : "not-found") + ", node=" + ((thenode == null) ? "NULL" : thenode.toString()) + ", parent=" + ((parentnode == null) ? "NULL" : parentnode.toString()));
            // we reached a node where we must insert the new value
            // the parent of this new value can be obtained by getParent()
            // all values are set, just return
        }

        public boolean found() {
            return found;
        }

        public CacheNode getMatcher() {
            if (found) return thenode;
            throw new IllegalArgumentException("wrong access of matcher");
        }

        public CacheNode getParent() {
            if (found) return parentnode;
            return thenode;
        }

        public boolean isRoot() {
            if (found) throw new IllegalArgumentException("wrong access of isRoot");
            return (child == 0);
        }

        public boolean isLeft() {
            if (found) throw new IllegalArgumentException("wrong access of leftchild");
            return (child == -1);
        }

        public boolean isRight() {
            if (found) throw new IllegalArgumentException("wrong access of leftchild");
            return (child == 1);
        }
    }

    public synchronized boolean isChild(kelondroNode childn, kelondroNode parentn, int child) {
        if (childn == null) throw new IllegalArgumentException("isLeftChild: Node parameter is NULL");
        kelondroHandle lc = parentn.getOHHandle(child);
        if (lc == null) return false;
        return (lc.equals(childn.handle()));
    }
    
    public synchronized void putMultiple(List<Entry> rows) throws IOException {
        Iterator<Entry> i = rows.iterator();
        while (i.hasNext()) put(i.next());
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        return put(row);
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry newrow) throws IOException {
        assert (newrow != null);
        assert (newrow.columns() == row().columns());
        assert (!(serverLog.allZero(newrow.getPrimaryKeyBytes())));
        assert newrow.objectsize() <= super.row().objectsize;
        // Associates the specified value with the specified key in this map
        kelondroRow.Entry result = null;
        //writeLock.stay(2000, 1000);
        // first try to find the key element in the database
        synchronized(writeSearchObj) {
            writeSearchObj.process(newrow.getColBytes(0));
            if (writeSearchObj.found()) {
                // a node with this key exist. simply overwrite the content and return old content
                kelondroNode e = writeSearchObj.getMatcher();
                result = row().newEntry(e.getValueRow());
                e.setValueRow(newrow.bytes());
                commitNode(e);
            } else if (writeSearchObj.isRoot()) {
                // a node with this key does not exist and there is no node at all
                // this therefore creates the root node if an only if there was no root Node yet
                if (getHandle(root) != null)
                    throw new kelondroException(filename, "tried to create root node twice");
                // we dont have any Nodes in the file, so start here to create one
                kelondroNode e = new CacheNode(newrow.bytes());
                // write the propetries
                e.setOHByte(magic,   (byte) 1);
                e.setOHByte(balance, (byte) 0);
                e.setOHHandle(parent, null);
                e.setOHHandle(leftchild, null);
                e.setOHHandle(rightchild, null);
                // do updates
                e.commit();
                setHandle(root, e.handle());
                result = null;
            } else {
                // a node with this key does not exist
                // this creates a new node if there is already at least a root node
                // to create the new node, it is necessary to assign it to a parent
                // it must also be defined weather this new node is a left child of the
                // parent or not. It is checked if the parent node already has a child on
                // that side, but not if the assigned position is appropriate.
                
                // create new node and assign values
                CacheNode parentNode = writeSearchObj.getParent();
                CacheNode theNode = new CacheNode(newrow.bytes());
                theNode.setOHByte(0, (byte) 1); // fresh magic
                theNode.setOHByte(1, (byte) 0); // fresh balance
                theNode.setOHHandle(parent, parentNode.handle());
                theNode.setOHHandle(leftchild, null);
                theNode.setOHHandle(rightchild, null);
                theNode.commit();
                
                // check consistency and link new node to parent node
                byte parentBalance;
                if (writeSearchObj.isLeft()) {
                    if (parentNode.getOHHandle(leftchild) != null) throw new kelondroException(filename, "tried to create leftchild node twice. parent=" + new String(parentNode.getKey()) + ", leftchild=" + new String(new CacheNode(parentNode.getOHHandle(leftchild), (CacheNode) null, 0, true).getKey()));
                    parentNode.setOHHandle(leftchild, theNode.handle());
                } else if (writeSearchObj.isRight()) {
                    if (parentNode.getOHHandle(rightchild) != null) throw new kelondroException(filename, "tried to create rightchild node twice. parent=" + new String(parentNode.getKey()) + ", rightchild=" + new String(new CacheNode(parentNode.getOHHandle(rightchild), (CacheNode) null, 0, true).getKey()));
                    parentNode.setOHHandle(rightchild, theNode.handle());
                } else {
                    throw new kelondroException(filename, "neither left nor right child");
                }
                commitNode(parentNode);
                
                // now update recursively the node balance of the parentNode
                // what do we have:
                // - new Node, called 'theNode'
                // - parent Node
                
                // set balance factor in parent node(s)
                boolean increasedHight = true;
                String path = "";
                byte prevHight;
                kelondroHandle parentSideHandle;
                while (increasedHight) {
                    
                    // update balance
                    parentBalance = parentNode.getOHByte(balance); // {magic, balance}
                    prevHight = parentBalance;
                    parentSideHandle = parentNode.getOHHandle(leftchild);
                    if ((parentSideHandle != null) && (parentSideHandle.equals(theNode.handle()))) {
                        // is left child
                        parentBalance++;
                        path = "L" + path;
                    }
                    parentSideHandle =parentNode.getOHHandle(rightchild);
                    if ((parentSideHandle != null) && (parentSideHandle.equals(theNode.handle()))) {
                        // is right child
                        parentBalance--;
                        path = "R" + path;
                    }
                    increasedHight = ((java.lang.Math.abs(parentBalance) - java.lang.Math.abs(prevHight)) > 0);
                    parentNode.setOHByte(balance, parentBalance);
                    commitNode(parentNode);
                    
                    // here we either stop because we had no increased hight,
                    // or we have a balance greater then 1 or less than -1 and we do rotation
                    // or we crawl up the tree and change the next balance
                    if (!(increasedHight)) break; // finished
                    
                    // check rotation need
                    if (java.lang.Math.abs(parentBalance) > 1) {
                        // rotate and stop then
                        //System.out.println("* DB DEBUG: " + path.substring(0,2) + " ROTATION AT NODE " + parentNode.handle().toString() + ": BALANCE=" + parentOHByte[balance]);
                        if (path.startsWith("LL")) {
                            LL_RightRotation(parentNode, theNode);
                            break;
                        }
                        if (path.startsWith("RR")) {
                            RR_LeftRotation(parentNode, theNode);
                            break;
                        }
                        if (path.startsWith("RL")) {
                            kelondroHandle parentHandle = parentNode.handle();
                            LL_RightRotation(theNode, new CacheNode(theNode.getOHHandle(leftchild), theNode, leftchild, false));
                            parentNode = new CacheNode(parentHandle, null, 0, false); // reload the parent node
                            RR_LeftRotation(parentNode, new CacheNode(parentNode.getOHHandle(rightchild), parentNode, rightchild, false));
                            break;
                        }
                        if (path.startsWith("LR")) {
                            kelondroHandle parentHandle = parentNode.handle();
                            RR_LeftRotation(theNode, new CacheNode(theNode.getOHHandle(rightchild), theNode, rightchild, false));
                            parentNode = new CacheNode(parentHandle, null, 0, false); // reload the parent node
                            LL_RightRotation(parentNode, new CacheNode(parentNode.getOHHandle(leftchild), parentNode, leftchild, false));
                            break;
                        }
                        break;
                    }
                    // crawl up the tree
                    if (parentNode.getOHHandle(parent) == null) break; // root reached: stop
                    theNode = parentNode;
                    parentNode = new CacheNode(parentNode.getOHHandle(parent), null, 0, false);
                }
                
                result = null; // that means: no previous stored value present
            }
        }
        //writeLock.release();
        return result;
    }

    public synchronized boolean addUnique(kelondroRow.Entry row) throws IOException {
        int s = this.size();
        this.put(row);
        return this.size() > s;
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) throws IOException {
        this.put(row, entryDate);
    }
    
    public synchronized int addUniqueMultiple(List<kelondroRow.Entry> rows) throws IOException {
        Iterator<kelondroRow.Entry> i = rows.iterator();
        int c = 0;
        while (i.hasNext()) {
            if (addUnique(i.next())) c++;
        }
        return c;
    }
    
    private void assignChild(kelondroNode parentNode, kelondroNode childNode, int childType) throws IOException {
        parentNode.setOHHandle(childType, childNode.handle());
        childNode.setOHHandle(parent, parentNode.handle());
        commitNode(parentNode);
        commitNode(childNode);
    }

    private void replace(kelondroNode oldNode, kelondroNode oldNodeParent, kelondroNode newNode) throws IOException {
	// this routine looks where the oldNode is connected to, and replaces
	// the anchor's link to the oldNode by the newNode-link
	// the new link gets the anchor as parent link assigned
        // the oldNode will not be updated, so this must be done outside this routine
	// distinguish case where the oldNode is the root node
	if (oldNodeParent == null) {
	    // this is the root, update root
	    setHandle(root, newNode.handle());
	    // update new Node
	    newNode.setOHHandle(parent, null);
            commitNode(newNode);
	} else {
	    // not the root, find parent
	    // ok, we have the parent, but for updating the child link we must know
	    // if the oldNode was left or right child
        kelondroHandle parentSideHandle = oldNodeParent.getOHHandle(leftchild);
        if ((parentSideHandle != null) && (parentSideHandle.equals(oldNode.handle()))) {
            // update left node from parent
            oldNodeParent.setOHHandle(leftchild, newNode.handle());
	    }
	    parentSideHandle = oldNodeParent.getOHHandle(rightchild);
	    if ((parentSideHandle != null) && (parentSideHandle.equals(oldNode.handle()))) {
	        // update right node from parent
            oldNodeParent.setOHHandle(rightchild, newNode.handle());
	    }
	    commitNode(oldNodeParent);
	    // update new Node
	    newNode.setOHHandle(parent, oldNodeParent.handle());
        commitNode(newNode);
	}
	// finished. remember that we did not set the links to the oldNode
        // we have also not set the children of the newNode.
	// this must be done somewhere outside this function.
	// if the oldNode is not needed any more, it can be disposed (check childs first).
    }

    private static byte max0(byte b) {
        if (b > 0) return b;
        return 0;
    }

    private static byte min0(byte b) {
        if (b < 0) return b;
        return 0;
    }

    private void LL_RightRotation(kelondroNode parentNode, CacheNode childNode) throws IOException {
        // replace the parent node; the parent is afterwards unlinked
        kelondroHandle p2Handle = parentNode.getOHHandle(parent);
        kelondroNode p2Node = (p2Handle == null) ? null : new CacheNode(p2Handle, null, 0, false);
        replace(parentNode, p2Node, childNode);

        // set the left son of the parent to the right son of the childNode
        kelondroHandle childOfChild = childNode.getOHHandle(rightchild);
        if (childOfChild == null) {
            parentNode.setOHHandle(leftchild, null);
        } else {
            assignChild(parentNode, new CacheNode(childOfChild, childNode, rightchild, false), leftchild);
        }

        // link the old parent node as the right child of childNode
        assignChild(childNode, parentNode, rightchild);

        // - newBal(parent)  = oldBal(parent) - 1 - max(oldBal(leftChild), 0)
        // - newBal(leftChild) = oldBal(leftChild) - 1 + min(newBal(parent), 0)
        byte parentBalance = parentNode.getOHByte(balance);
        byte childBalance = childNode.getOHByte(balance);
        byte oldBalParent = parentBalance;
        byte oldBalChild = childBalance;
        parentBalance = (byte) (oldBalParent - 1 - max0(oldBalChild));
        childBalance = (byte) (oldBalChild - 1 + min0(parentBalance));
        parentNode.setOHByte(balance, parentBalance);
        childNode.setOHByte(balance, childBalance);
        commitNode(parentNode);
        commitNode(childNode);
    }

    private void RR_LeftRotation(kelondroNode parentNode, CacheNode childNode) throws IOException {
        // replace the parent node; the parent is afterwards unlinked
        kelondroHandle p2Handle = parentNode.getOHHandle(parent);
        kelondroNode p2Node = (p2Handle == null) ? null : new CacheNode(p2Handle, null, 0, false);
        replace(parentNode, p2Node, childNode);

        // set the left son of the parent to the right son of the childNode
        kelondroHandle childOfChild = childNode.getOHHandle(leftchild);
        if (childOfChild == null) {
            parentNode.setOHHandle(rightchild, null);
        } else {
            assignChild(parentNode, new CacheNode(childOfChild, childNode, leftchild, false), rightchild);
        }

        // link the old parent node as the left child of childNode
        assignChild(childNode, parentNode, leftchild);

        // - newBal(parent)   = oldBal(parent) + 1 - min(oldBal(rightChild), 0)
        // - newBal(rightChild) = oldBal(rightChild) + 1 + max(newBal(parent), 0)
        byte parentBalance = parentNode.getOHByte(balance);
        byte childBalance = childNode.getOHByte(balance);
        byte oldBalParent = parentBalance;
        byte oldBalChild = childBalance;
        parentBalance = (byte) (oldBalParent + 1 - min0(oldBalChild));
        childBalance = (byte) (oldBalChild + 1 + max0(parentBalance));
        parentNode.setOHByte(balance, parentBalance);
        childNode.setOHByte(balance, childBalance);
        commitNode(parentNode);
        commitNode(childNode);
    }

    // Associates the specified value with the specified key in this map
    public byte[] put(byte[] key, byte[] value) throws IOException {
        kelondroRow.Entry row = row().newEntry(new byte[][]{key, value});
        kelondroRow.Entry ret = put(row);
        if (ret == null) return null; else return ret.getColBytes(0);
    }
    
    // Removes the mapping for this key from this map if present (optional operation).
    public kelondroRow.Entry remove(byte[] key, boolean keepOrder) throws IOException {
        // keepOrder cannot have any effect: the order inside the database file cannot be maintained, but iteration over objects will always maintain the object order
        // therefore keepOrder should be true, because the effect is always given, while the data structure does not maintain order
        // delete from database
        synchronized(writeSearchObj) {
            writeSearchObj.process(key);
            if (writeSearchObj.found()) {
                CacheNode result = writeSearchObj.getMatcher();
                kelondroRow.Entry values = row().newEntry(result.getValueRow());
                remove(result, writeSearchObj.getParent());
                return values;
            } else {
                return null;
            }
        }
    }

    public kelondroRow.Entry removeOne() throws IOException {
        // removes just any entry and removes that entry
        synchronized(writeSearchObj) {
            CacheNode theOne = lastNode();
            kelondroRow.Entry values = row().newEntry(theOne.getValueRow());
            remove(theOne, null);
            return values;
        }
    }
    
    public synchronized void removeAll() throws IOException {
        while (size() > 0) remove(lastNode(), null);
    }
    
    private void remove(CacheNode node, kelondroNode parentOfNode) throws IOException {
        // there are three cases when removing a node
        // - the node is a leaf - it can be removed easily
        // - the node has one child - the child replaces the node
        // - the node has two childs - it can be replaced either
        //   by the greatest node of the left child or the smallest
        //   node of the right child

        kelondroNode childnode;
        if ((node.getOHHandle(leftchild) == null) && (node.getOHHandle(rightchild) == null)) {
	    // easy case: the node is a leaf
	    if (parentOfNode == null) {
		// this is the root!
		setHandle(root, null);
	    } else {
            kelondroHandle h = parentOfNode.getOHHandle(leftchild);
            if ((h != null) && (h.equals(node.handle()))) parentOfNode.setOHHandle(leftchild, null);
            h = parentOfNode.getOHHandle(rightchild);
            if ((h != null) && (h.equals(node.handle()))) parentOfNode.setOHHandle(rightchild, null);
            commitNode(parentOfNode);
	    }
	} else if ((node.getOHHandle(leftchild) != null) && (node.getOHHandle(rightchild) == null)) {
	    replace(node, parentOfNode, new CacheNode(node.getOHHandle(leftchild), node, leftchild, false));
	} else if ((node.getOHHandle(leftchild) == null) && (node.getOHHandle(rightchild) != null)) {
	    replace(node, parentOfNode, new CacheNode(node.getOHHandle(rightchild), node, rightchild, false));
	} else {
	    // difficult case: node has two children
	    CacheNode repl = lastNode(new CacheNode(node.getOHHandle(leftchild), node, leftchild, false));
	    //System.out.println("last node is " + repl.toString());
	    // we remove that replacement node and put it where the node was
	    // this seems to be recursive, but is not since the replacement
	    // node cannot have two children (it would not have been the smallest or greatest)
	    kelondroNode n;
        kelondroHandle h;
            // remove leaf
	    if ((repl.getOHHandle(leftchild) == null) && (repl.getOHHandle(rightchild) == null)) {
		// the replacement cannot be the root, so simply remove from parent node
		n = new CacheNode(repl.getOHHandle(parent), null, 0, false); // parent node of replacement node
		h = n.getOHHandle(leftchild);
		if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(leftchild, null);
                h = n.getOHHandle(rightchild);
		if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(rightchild, null);
                commitNode(n);
	    } else if ((repl.getOHHandle(leftchild) != null) && (repl.getOHHandle(rightchild) == null)) {
                try {
                    childnode = new CacheNode(repl.getOHHandle(leftchild), repl, leftchild, false);
                    replace(repl, new CacheNode(repl.getOHHandle(parent), null, 0, false), childnode);
                } catch (IllegalArgumentException e) {
                    // now treat the situation as if that link had been null before
                    n = new CacheNode(repl.getOHHandle(parent), null, 0, false); // parent node of replacement node
                    h = n.getOHHandle(leftchild);
                    if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(leftchild, null);
                    h = n.getOHHandle(rightchild);
                    if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(rightchild, null);
                    commitNode(n);
                }
	    } else if ((repl.getOHHandle(leftchild) == null) && (repl.getOHHandle(rightchild) != null)) {
                try {
                    childnode = new CacheNode(repl.getOHHandle(rightchild), repl, rightchild, false);
                    replace(repl, new CacheNode(repl.getOHHandle(parent), null, 0, false), childnode);
                } catch (IllegalArgumentException e) {
                    // now treat the situation as if that link had been null before
                    n = new CacheNode(repl.getOHHandle(parent), null, 0, false); // parent node of replacement node
                    h = n.getOHHandle(leftchild);
                    if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(leftchild, null);
                    h = n.getOHHandle(rightchild);
                    if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(rightchild, null);
                    commitNode(n);
                }
	    }
	    //System.out.println("node before reload is " + node.toString());
	    node = new CacheNode(node.handle(), null, 0, false); // reload the node, it is possible that it has been changed
	    //System.out.println("node after reload is " + node.toString());
            
            // now plant in the replha node
	    byte b = node.getOHByte(balance); // save balance of disappearing node
        kelondroHandle parentHandle = node.getOHHandle(parent);
        kelondroHandle leftchildHandle = node.getOHHandle(leftchild);
        kelondroHandle rightchildHandle = node.getOHHandle(rightchild);
	    replace(node, parentOfNode, repl);
	    repl.setOHByte(balance, b); // restore balance
	    repl.setOHHandle(parent, parentHandle); // restore handles
        repl.setOHHandle(leftchild, leftchildHandle);
        repl.setOHHandle(rightchild, rightchildHandle);
        commitNode(repl);
	    // last thing to do: change uplinks of children to this new node
	    if (leftchildHandle != null) {
	        n = new CacheNode(leftchildHandle, node, leftchild, false);
            n.setOHHandle(parent, repl.handle());
            commitNode(n);
	    }
	    if (rightchildHandle != null) {
	        n = new CacheNode(rightchildHandle, node, rightchild, false);
            n.setOHHandle(parent, repl.handle());
            commitNode(n);
	    }
 	}
        // move node to recycling queue
        synchronized (this) {
            deleteNode(node.handle());
        }
    }

    protected CacheNode firstNode() throws IOException {
        kelondroHandle h = getHandle(root);
        if (h == null) return null;
        return firstNode(new CacheNode(h, null, 0, true));
    }
    
    protected CacheNode firstNode(CacheNode node) throws IOException {
        if (node == null) throw new IllegalArgumentException("firstNode: node=null"); 
        kelondroHandle h = node.getOHHandle(leftchild);
        HashSet<String> visitedNodeKeys = new HashSet<String>(); // to detect loops
        String nodeKey;
        while (h != null) {
            try {
                node = new CacheNode(h, node, leftchild, true);
                nodeKey = new String(node.getKey());
                if (visitedNodeKeys.contains(nodeKey)) throw new kelondroException(this.filename, "firstNode: database contains loops: '" + nodeKey + "' appears twice.");
                visitedNodeKeys.add(nodeKey);
            } catch (IllegalArgumentException e) {
                // return what we have
                return node;
            }
	    h = node.getOHHandle(leftchild);
        }
        return node;
    }
    
    protected CacheNode lastNode() throws IOException {
        kelondroHandle h = getHandle(root);
        if (h == null) return null;
        return lastNode(new CacheNode(h, null, 0, true));
    }

    protected CacheNode lastNode(CacheNode node) throws IOException {
        if (node == null) throw new IllegalArgumentException("lastNode: node=null"); 
        kelondroHandle h = node.getOHHandle(rightchild);
        HashSet<String> visitedNodeKeys = new HashSet<String>(); // to detect loops
        String nodeKey;
        while (h != null) {
	    try {
                node = new CacheNode(h, node, rightchild, true);
                nodeKey = new String(node.getKey());
                if (visitedNodeKeys.contains(nodeKey)) throw new kelondroException(this.filename, "lastNode: database contains loops: '" + nodeKey + "' appears twice.");
                visitedNodeKeys.add(nodeKey);
            } catch (IllegalArgumentException e) {
                // return what we have
                return node;
            }
	    h = node.getOHHandle(rightchild);
        }
        return node;
    }
    
    private class nodeIterator implements Iterator<CacheNode> {
        // we implement an iteration! (not a recursive function as the structure would suggest...)
        // the iterator iterates Node objects
        CacheNode nextNode = null;
        boolean up, rot;
        LinkedList<Object[]> nodeStack;
        int count;
        
        public nodeIterator(boolean up, boolean rotating) throws IOException {
            this.count = 0;
            this.up = up;
            this.rot = rotating;
            
            // initialize iterator
            init((up) ? firstNode() : lastNode());
        }
        
        public nodeIterator(boolean up, boolean rotating, byte[] firstKey, boolean including) throws IOException {
            this.count = 0;
            this.up = up;
            this.rot = rotating;

            Search search = new Search();
            search.process(firstKey);
            if (search.found()) {
                init(search.getMatcher());
            } else {
                CacheNode nn = search.getParent();
                if (nn == null) {
                    this.nextNode = null;
                } else {
                    // the node nn may be greater or smaller than the firstKey
                    // depending on the ordering direction,
                    // we must find the next smaller or greater node
                    // this is corrected in the initializer of nodeIterator
                    init(nn);
                }
            }
            
            // correct nextNode upon start
            // this happens, if the start node was not proper, or could not be found
            while ((nextNode != null) && (nextNode.getKey() != null)) {
                int c = row().objectOrder.compare(firstKey, nextNode.getKey());
                if (c == 0) {
                    if (including) {
                        break; // correct + finished
                    } else {
                        if (hasNext()) next(); else nextNode = null;
                        break; // corrected + finished
                    }
                } else if (c < 0) {
                    if (up) {
                        break; // correct + finished
                    } else {
                        // firstKey < nextNode.getKey(): correct once
                        if (hasNext()) next(); else nextNode = null;
                    }
                } else if (c > 0) {
                    if (up) {
                        // firstKey > nextNode.getKey(): correct once
                        if (hasNext()) next(); else nextNode = null;
                    } else {
                        break; // correct + finished
                    }
                }
            }
        }
        
        private void init(CacheNode start) throws IOException {
            this.nextNode = start;
            
            // fill node stack for start node
            nodeStack = new LinkedList<Object[]>();
            
            kelondroHandle searchHandle = getHandle(root);
            if (searchHandle == null) {nextNode = null; return;}

            CacheNode searchNode = new CacheNode(searchHandle, null, 0, false);            
            byte[] startKey = start.getKey();
            int c, ct;
            while ((c = row().objectOrder.compare(startKey, searchNode.getKey())) != 0) {
                // the current 'thisNode' is not the start node, put it on the stack
                ct = (c < 0) ? leftchild : rightchild;
                nodeStack.addLast(new Object[]{searchNode, new Integer(ct)});
                
                // go to next node
                searchHandle = searchNode.getOHHandle(ct);
                if (searchHandle == null) throw new kelondroException(filename, "nodeIterator.init: start node does not exist (handle null)");
                searchNode = new CacheNode(searchHandle, searchNode, ct, false);
                if (searchNode == null) throw new kelondroException(filename, "nodeIterator.init: start node does not exist (node null)");
            }
            // now every parent node to the start node is on the stack
        }
        
        public void finalize() {
            nextNode = null;
            nodeStack = null;
        }
            
        public boolean hasNext() {
            return (rot && (size() > 0)) || (nextNode != null);
        }

        public CacheNode next() {
            count++;
            if ((rot) && (nextNode == null)) try {
                init((up) ? firstNode() : lastNode());
            } catch (IOException e) {
                throw new kelondroException(filename, "io-error while rot");
            }
            if (nextNode == null) throw new kelondroException(filename, "nodeIterator.next: no more entries available");
            if ((count > size()) && (!(rot))) throw new kelondroException(filename, "nodeIterator.next: internal loopback; database corrupted");
            CacheNode ret = nextNode;
            
            // middle-case
            try {
                int childtype = (up) ? rightchild : leftchild;
                kelondroHandle childHandle = nextNode.getOHHandle(childtype);
                if (childHandle != null) {
                    //System.out.println("go to other leg, stack size=" + nodeStack.size());
                    // we have walked one leg of the tree; now go to the other one: step down to next child
                    HashSet<kelondroHandle> visitedNodeHandles = new HashSet<kelondroHandle>(); // to detect loops
                    nodeStack.addLast(new Object[]{nextNode, new Integer(childtype)});
                    nextNode = new CacheNode(childHandle, nextNode, childtype, false);
                    childtype = (up) ? leftchild : rightchild;
                    while ((childHandle = nextNode.getOHHandle(childtype)) != null) {
                        if (visitedNodeHandles.contains(childHandle)) {
                            // try to repair the nextNode
                            nextNode.setOHHandle(childtype, null);
                            nextNode.commit();
                            logWarning("nodeIterator.next: internal loopback; fixed loop and try to go on");
                            break;
                        }
                        visitedNodeHandles.add(childHandle);
                        try {
                            nodeStack.addLast(new Object[]{nextNode, new Integer(childtype)});
                            nextNode = new CacheNode(childHandle, nextNode, childtype, false);
                        } catch (IllegalArgumentException e) {
                            // return what we have
                            nodeStack.removeLast();
                            return ret;
                        }
                    }
                    // thats it: we are at a place where we can't go further
                    // nextNode is correct
                } else {
                    //System.out.println("go up");
                    // we have walked along both legs of the child-trees.
                    
                    // Now step up.
                    if (nodeStack.size() == 0) {
                        nextNode = null;
                    } else {
                        Object[] stacktop;
                        CacheNode parentNode = null;
                        int parentpointer = (up) ? rightchild : leftchild;
                        while ((nodeStack.size() != 0) && (parentpointer == ((up) ? rightchild : leftchild))) {
                            //System.out.println("step up");
                            // go on, walk up further
                            stacktop = nodeStack.removeLast(); // top of stack: Node/parentpointer pair
                            parentNode = (CacheNode) stacktop[0];
                            parentpointer = ((Integer) stacktop[1]).intValue();
                        }
                        if ((nodeStack.size() == 0) && (parentpointer == ((up) ? rightchild : leftchild))) {
                            nextNode = null;
                        } else {
                            nextNode = parentNode;
                        }
                    }
                }
            } catch (IOException e) {
                nextNode = null;
            }
            
            return ret;
        }
        
        public void remove() {
            throw new java.lang.UnsupportedOperationException("kelondroTree: remove in kelondro node iterator not yet supported");
        }
    }

    public TreeMap<String, kelondroRow.Entry> rowMap(boolean up, byte[] firstKey, boolean including, int count) throws IOException {
        // returns an ordered map of keys/row relations; key objects are of type String, value objects are of type byte[][]
        kelondroByteOrder setOrder = (kelondroByteOrder) row().objectOrder.clone();
        setOrder.direction(up);
        setOrder.rotate(firstKey);
        TreeMap<String, kelondroRow.Entry> rows = new TreeMap<String, kelondroRow.Entry>(this.loopDetectionOrder);
        CacheNode n;
        String key;
        synchronized (this) {
            Iterator<CacheNode> i = (firstKey == null) ? new nodeIterator(up, false) : new nodeIterator(up, false, firstKey, including);
            while ((rows.size() < count) && (i.hasNext())) {
                n = i.next();
                if (n == null) return rows;
                key = new String(n.getKey());
                if (rows.put(key, row().newEntry(n.getValueRow())) != null) return rows; // protection against loops
            }
        }
        return rows;
    }
    
    public TreeSet<String> keySet(boolean up, boolean rotating, byte[] firstKey, boolean including, int count) throws IOException {
        // returns an ordered set of keys; objects are of type String
        kelondroByteOrder setOrder = (kelondroByteOrder) row().objectOrder.clone();
        setOrder.direction(up);
        setOrder.rotate(firstKey);
        TreeSet<String> set = new TreeSet<String>(this.loopDetectionOrder);
        kelondroNode n;
        synchronized (this) {
            Iterator<CacheNode> i = (firstKey == null) ? new nodeIterator(up, rotating) : new nodeIterator(up, rotating, firstKey, including);
            while ((set.size() < count) && (i.hasNext())) {
                n = i.next();
                if ((n != null) && (n.getKey() != null)) set.add(new String(n.getKey()));
            }
        }
        return set;
    }
    
    public kelondroCloneableIterator<kelondroRow.Entry> rows(boolean up, byte[] firstKey) throws IOException {
        // iterates the rows of the Nodes
        // enumerated objects are of type byte[][]
        // iterates the elements in a sorted way.
        // if iteration should start at smallest element, set firstKey to null
        return new rowIterator(up, firstKey, this.size());
    }
    
    public class rowIterator implements kelondroCloneableIterator<kelondroRow.Entry> {
        
        int chunkSize;
        boolean inc;
        long count;
        byte[] lastKey;
        TreeMap<String, kelondroRow.Entry> rowBuffer;
        Iterator<Map.Entry<String, kelondroRow.Entry>> bufferIterator;
        long guessedCountLimit;
        
        public rowIterator(boolean up, byte[] firstKey, long guessedCountLimit) throws IOException {
            this.guessedCountLimit = guessedCountLimit;
            inc = up;
            count = 0;
            lastKey = null;
            //System.out.println("*** rowIterator: " + filename + ": readAheadChunkSize = " + readAheadChunkSize + ", lastIteratorCount = " + lastIteratorCount);
            readAheadChunkSize = Math.min(1000, 3 + (int) ((3 * readAheadChunkSize + lastIteratorCount) / 4));
            chunkSize = (int) Math.min(readAheadChunkSize / 3, guessedCountLimit);
            rowBuffer = rowMap(inc, firstKey, true, chunkSize);
            bufferIterator = rowBuffer.entrySet().iterator();
            lastIteratorCount = 0;
        }
        
		public rowIterator clone(Object secondStart) {
            try {
                return new rowIterator(inc, (byte[]) secondStart, guessedCountLimit);
            } catch (IOException e) {
                return null;
            }
        }
        
        public boolean hasNext() {
            return ((bufferIterator != null) && (bufferIterator.hasNext()) && (count < size()));
        }
        
        public kelondroRow.Entry next() {
            if (!(bufferIterator.hasNext())) return null;
            Map.Entry<String, kelondroRow.Entry> entry = bufferIterator.next();
            lastKey = entry.getKey().getBytes();
            
            // check if this was the last entry in the rowBuffer
            if (!(bufferIterator.hasNext())) {
                // assign next buffer chunk
                try {
                    lastKey[lastKey.length - 1]++; // ***BUG??? FIXME
                    rowBuffer = rowMap(inc, lastKey, false, chunkSize);
                    bufferIterator = rowBuffer.entrySet().iterator();
                } catch (IOException e) {
                    rowBuffer = null;
                    bufferIterator = null;
                }
            }
            
            // return the row
            count++;
            lastIteratorCount++;
            return entry.getValue();
        }
        
        public void remove() {
            if (lastKey != null) try {
                kelondroTree.this.remove(lastKey, true);
            } catch (IOException e) {
                // do nothing
            }
        }
        
    }

	public kelondroCloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
		return new keyIterator(up, firstKey, this.size());
	}
	
    public class keyIterator implements kelondroCloneableIterator<byte[]> {
        
        int chunkSize;
        boolean inc;
        long count;
        byte[] lastKey;
        TreeSet<String> keyBuffer;
        Iterator<String> bufferIterator;
        long guessedCountLimit;
        
        public keyIterator(boolean up, byte[] firstKey, long guessedCountLimit) throws IOException {
            this.guessedCountLimit = guessedCountLimit;
            inc = up;
            count = 0;
            lastKey = null;
            //System.out.println("*** rowIterator: " + filename + ": readAheadChunkSize = " + readAheadChunkSize + ", lastIteratorCount = " + lastIteratorCount);
            readAheadChunkSize = Math.min(1000, 3 + (int) ((3 * readAheadChunkSize + lastIteratorCount) / 4));
            chunkSize = (int) Math.min(readAheadChunkSize / 3, guessedCountLimit);
            keyBuffer = keySet(inc, false, firstKey, true, chunkSize);
            bufferIterator = keyBuffer.iterator();
            lastIteratorCount = 0;
        }
        
		public keyIterator clone(Object secondStart) {
            try {
                return new keyIterator(inc, (byte[]) secondStart, guessedCountLimit);
            } catch (IOException e) {
                return null;
            }
        }
        
        public boolean hasNext() {
            return ((bufferIterator != null) && (bufferIterator.hasNext()) && (count < size()));
        }
        
        public byte[] next() {
            if (!(bufferIterator.hasNext())) return null;
            lastKey = bufferIterator.next().getBytes();
            
            // check if this was the last entry in the rowBuffer
            if (!(bufferIterator.hasNext())) {
                // assign next buffer chunk
                try {
                    lastKey[lastKey.length - 1]++; // ***BUG??? FIXME
                    keyBuffer = keySet(inc, false, lastKey, false, chunkSize);
                    bufferIterator = keyBuffer.iterator();
                } catch (IOException e) {
                    keyBuffer = null;
                    bufferIterator = null;
                }
            }
            
            // return the row
            count++;
            lastIteratorCount++;
            return lastKey;
        }
        
        public void remove() {
            if (lastKey != null) try {
                kelondroTree.this.remove(lastKey, true);
            } catch (IOException e) {
                // do nothing
            }
        }
        
    }

    public int imp(File file, String separator) throws IOException {
	// imports a value-separated file, returns number of records that have been read

	RandomAccessFile f = new RandomAccessFile(file,"r");
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
		    put(buffer);
		    recs++;
		}

	    }
	}
	return recs;
    }

    public synchronized int height() {
        try {
            kelondroHandle h = getHandle(root);
            if (h == null) return 0;
            return height(new CacheNode(h, null, 0, false));
        } catch (IOException e) {
            return 0;
        }
    }
    
    private int height(CacheNode node) throws IOException {
        if (node == null) return 0;
        kelondroHandle h = node.getOHHandle(leftchild);
        int hl = (h == null) ? 0 : height(new CacheNode(h, node, leftchild, false));
        h = node.getOHHandle(rightchild);
        int hr = (h == null) ? 0 : height(new CacheNode(h, node, rightchild, false));
        if (hl > hr) return hl + 1;
        return hr + 1;
    }
    
    public void print() throws IOException {
        super.print();
        int height = height();
        System.out.println("HEIGHT = " + height);
        Vector<kelondroHandle> thisline = new Vector<kelondroHandle>();
        thisline.add(getHandle(root));
        Vector<kelondroHandle> nextline;
        kelondroHandle handle;
        kelondroNode node;
        int linelength, width = (1 << (height - 1)) * (row().width(0) + 1);
        String key;
        for (int h = 1; h < height; h++) {
            linelength = width / (thisline.size() * 2);
            nextline = new Vector<kelondroHandle>();
            for (int i = 0; i < thisline.size(); i++) {
                handle = thisline.elementAt(i);
                if (handle == null) {
                    node = null;
                    key = "[..]";
                } else {
                    node = new CacheNode(handle, null, 0, false);
                    if (node == null) key = "NULL"; else key = new String(node.getKey());
                }
                System.out.print(key);
                for (int j = 0; j < (linelength - key.length()); j++) System.out.print("-");
                System.out.print("+");
                for (int j = 0; j < (linelength - 1); j++) System.out.print(" ");
                if (node == null) {
                    nextline.add(null);
                    nextline.add(null);
                } else {
                    nextline.add(node.getOHHandle(leftchild));
                    nextline.add(node.getOHHandle(rightchild));
                }
            }
            System.out.println();
            for (int i = 0; i < thisline.size(); i++) {
                System.out.print("|");
                for (int j = 0; j < (linelength - 1); j++) System.out.print(" ");
                System.out.print("|");
                for (int j = 0; j < (linelength - 1); j++) System.out.print(" ");
            }
            System.out.println();
            thisline = nextline;
            nextline = null;
        }
        // now print last line
        if ((thisline != null) && (width >= 0)) {
            linelength = width / thisline.size();
            for (int i = 0; i < thisline.size(); i++) {
                handle = thisline.elementAt(i);
                if (handle == null) {
                    node = null;
                    key = "NULL";
                } else {
                    node = new CacheNode(handle, null, 0, false);
                    if (node == null) key = "NULL"; else key = new String(node.getKey());
                }
                System.out.print(key);
                for (int j = 0; j < (linelength - key.length()); j++) System.out.print(" ");
            }
        }
	System.out.println();
    }
    
    public static void cmd(String[] args) {
	System.out.print("kelondroTree ");
	for (int i = 0; i < args.length; i++) System.out.print(args[i] + " ");
	System.out.println("");
	byte[] ret = null;
	try {
	    if ((args.length > 4) || (args.length < 1)) {
		System.err.println("usage: kelondroTree -c|-u|-v|-g|-d|-i|-s [file]|[key [value]] <db-file>");
		System.err.println("( create, update, view, get, delete, imp, shell)");
		System.exit(0);
	    } else if (args.length == 1) {
		if (args[0].equals("-t")) {
                    // test script
                    File testFile = new File("test.db");
                    while (testFile.exists()) testFile.delete();
                    kelondroTree fm = new kelondroTree(testFile, true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
                    byte[] dummy = "".getBytes();
                    fm.put("abc0".getBytes(), dummy); fm.put("bcd0".getBytes(), dummy);
                    fm.put("def0".getBytes(), dummy); fm.put("bab0".getBytes(), dummy);
                    fm.put("abc1".getBytes(), dummy); fm.put("bcd1".getBytes(), dummy);
                    fm.put("def1".getBytes(), dummy); fm.put("bab1".getBytes(), dummy);
                    fm.put("abc2".getBytes(), dummy); fm.put("bcd2".getBytes(), dummy);
                    fm.put("def2".getBytes(), dummy); fm.put("bab2".getBytes(), dummy);
                    fm.put("abc3".getBytes(), dummy); fm.put("bcd3".getBytes(), dummy);
                    fm.put("def3".getBytes(), dummy); fm.put("bab3".getBytes(), dummy);
                    fm.print();
                    fm.remove("def1".getBytes(), true); fm.remove("bab1".getBytes(), true);
                    fm.remove("abc2".getBytes(), true); fm.remove("bcd2".getBytes(), true);
                    fm.remove("def2".getBytes(), true); fm.remove("bab2".getBytes(), true);
                    fm.put("def1".getBytes(), dummy); fm.put("bab1".getBytes(), dummy);
                    fm.put("abc2".getBytes(), dummy); fm.put("bcd2".getBytes(), dummy);
                    fm.put("def2".getBytes(), dummy); fm.put("bab2".getBytes(), dummy);
                    fm.print();
                    fm.close();
		    ret = null;
		}
	    } else if (args.length == 2) {
		kelondroTree fm = new kelondroTree(new File(args[1]), true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
		if (args[0].equals("-v")) {
		    fm.print();
		    ret = null;
		}
		fm.close();
	    } else if (args.length == 3) {
		if (args[0].equals("-d")) {
		    kelondroTree fm = new kelondroTree(new File(args[1]), true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
		    fm.remove(args[2].getBytes(), true);
		    fm.close();
		} else if (args[0].equals("-i")) {
		    kelondroTree fm = new kelondroTree(new File(args[1]), true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
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
                if (f != null) try {f.close();}catch(Exception e){}
            }
		} else if (args[0].equals("-g")) {
		    kelondroTree fm = new kelondroTree(new File(args[1]), true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
		    kelondroRow.Entry ret2 = fm.get(args[2].getBytes());
		    ret = ((ret2 == null) ? null : ret2.getColBytes(1)); 
		    fm.close();
		} else if (args[0].equals("-n")) {
		    kelondroTree fm = new kelondroTree(new File(args[1]), true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
		    //byte[][] keys = fm.getSequentialKeys(args[2].getBytes(), 500, true);
                    Iterator<kelondroRow.Entry> rowIt = fm.rows(true, (args[2].length() == 0) ? null : args[2].getBytes());
                    Vector<String> v = new Vector<String>();
                    while (rowIt.hasNext()) v.add(rowIt.next().getColString(0, null));
                    ret = v.toString().getBytes(); 
		    fm.close();
		}
	    } else if (args.length == 4) {
		if (args[0].equals("-c")) {
		    // create <keylen> <valuelen> <filename>
		    File f = new File(args[3]);
		    if (f.exists()) f.delete();
            kelondroRow lens = new kelondroRow("byte[] key-" + Integer.parseInt(args[1]) + ", byte[] value-" + Integer.parseInt(args[2]), kelondroNaturalOrder.naturalOrder, 0);
		    kelondroTree fm = new kelondroTree(f, true, 10, lens);
		    fm.close();
		} else if (args[0].equals("-u")) {
		    kelondroTree fm = new kelondroTree(new File(args[1]), true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
		    ret = fm.put(args[1].getBytes(), args[2].getBytes());
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
        //cmd(args);
        //iterationtest();
        //bigtest(Integer.parseInt(args[0]));
        randomtest(Integer.parseInt(args[0]));
        //smalltest();
    }
 
    public static String[] permutations(int letters) {
        String p = "";
        for (int i = 0; i < letters; i++) p = p + ((char) (('A') + i));
        return permutations(p);
    }
    public static String[] permutations(String source) {
        if (source.length() == 0) return new String[0];
        if (source.length() == 1) return new String[]{source};
        char c = source.charAt(0);
        String[] recres = permutations(source.substring(1));
        String[] result = new String[source.length() * recres.length];
        for (int perm = 0; perm < recres.length; perm++) {
            result[perm * source.length()] = c + recres[perm];
            for (int pos = 1; pos < source.length() - 1; pos++) {
                result[perm * source.length() + pos] = recres[perm].substring(0, pos) + c + recres[perm].substring(pos);
            }
            result[perm * source.length() + source.length() - 1] = recres[perm] + c;
        }
        return result;
    }
    
    public static byte[] testWord(char c) {
        return new byte[]{(byte) c, 32, 32, 32};
    }
    
    public static void randomtest(int elements) {
        System.out.println("random " + elements + ":");
        String s = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".substring(0, elements);
        String t, d;
        char c;
        kelondroTree tt = null;
        File testFile = new File("test.db");
        byte[] b;
        try {
            int steps = 0;
            while (true) {
                if (testFile.exists()) testFile.delete();
                tt = new kelondroTree(testFile, true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
                steps = 10 + ((int) System.currentTimeMillis() % 7) * (((int) System.currentTimeMillis() + 17) % 11);
                t = s;
                d = "";
                System.out.println("NEW SESSION");
                for (int i = 0; i < steps; i++) {
                    if ((d.length() < 3) || ((t.length() > 0) && (((int) System.currentTimeMillis() % 7) < 2))) {
                        // add one
                        c = t.charAt((int) (System.currentTimeMillis() % t.length()));
                        b = testWord(c);
                        tt.put(b, b);
                        d = d + c;
                        t = t.substring(0, t.indexOf(c)) + t.substring(t.indexOf(c) + 1);
                        System.out.println("added " + new String(b));
                    } else {
                        // delete one
                        c = d.charAt((int) (System.currentTimeMillis() % d.length()));
                        b = testWord(c);
                        tt.remove(b, true);
                        d = d.substring(0, d.indexOf(c)) + d.substring(d.indexOf(c) + 1);
                        t = t + c;
                        System.out.println("removed " + new String(b));
                    }
                    //tt.printCache();
                    //tt.print();
                    
                    if (countElements(tt) != tt.size()) {
                        System.out.println("wrong size for this table:");
                        tt.print();
                    }
                    
                    // check all words within
                    for (int j = 0; j < d.length(); j++) {
                        if (tt.get(testWord(d.charAt(j))) == null) {
                            System.out.println("missing entry " + d.charAt(j) + " in this table:");
                            tt.print();
                        }
                    }
                    // check all words outside
                    for (int j = 0; j < t.length(); j++) {
                        if (tt.get(testWord(t.charAt(j))) != null) {
                            System.out.println("superfluous entry " + t.charAt(j) + " in this table:");
                            tt.print();
                        }
                    }
                    if (tt.get(testWord('z')) != null) {
                        System.out.println("superfluous entry z in this table:");
                        tt.print();
                    }
                    
                }
                //tt.print();
                tt.close();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            if (tt != null) try {tt.print();} catch (IOException ee) {}
            System.out.println("TERMINATED");
        }
    }
    
    public static void smalltest() {
        File f = new File("test.db");
        if (f.exists()) f.delete();
        try {
            kelondroTree tt = new kelondroTree(f, true, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
            byte[] b;
            b = testWord('B'); tt.put(b, b); //tt.print();
            b = testWord('C'); tt.put(b, b); //tt.print();
            b = testWord('D'); tt.put(b, b); //tt.print();
            b = testWord('A'); tt.put(b, b); //tt.print();
            b = testWord('D'); tt.remove(b, true); //tt.print();
            b = testWord('B'); tt.remove(b, true); //tt.print();
            b = testWord('B'); tt.put(b, b); //tt.print();
            b = testWord('D'); tt.put(b, b);
            b = testWord('E'); tt.put(b, b);
            b = testWord('F'); tt.put(b, b);
            b = testWord('G'); tt.put(b, b);
            b = testWord('H'); tt.put(b, b);
            b = testWord('I'); tt.put(b, b);
            b = testWord('J'); tt.put(b, b);
            b = testWord('K'); tt.put(b, b);
            b = testWord('L'); tt.put(b, b);
            int c = countElements(tt);
            System.out.println("elements: " + c);
            Iterator<kelondroRow.Entry> i = tt.rows(true, testWord('G'));
            for (int j = 0; j < c; j++) {
                System.out.println("Row " + j + ": " + new String((i.next()).getColBytes(0)));
            }
            System.out.println("TERMINATED");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /*
    public static void iterationtest() {
        File f = new File("test.db");
        if (f.exists()) f.delete();
        try {
            kelondroTree tt = new kelondroTree(f, 0, 0, 10, 4, 4, true);
            byte[] b;
            for (int i = 0; i < 100; i++) {
                b = ("T" + i).getBytes(); tt.put(b, b);
            }
            Iterator i = tt.keys(true, false, null);
            while (i.hasNext()) System.out.print((String) i.next() + ", ");
            System.out.println();

            i = tt.keys(true, false, "T80".getBytes());
            while (i.hasNext()) System.out.print((String) i.next() + ", ");
            System.out.println();
            
            i = tt.keys(true, true, "T80".getBytes());
            for (int j = 0; j < 40; j++) System.out.print((String) i.next() + ", ");
            System.out.println();
            
            i = tt.keys(false, true, "T20".getBytes());
            for (int j = 0; j < 40; j++) System.out.print((String) i.next() + ", ");
            System.out.println();
            
            tt.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    */
    
    public static kelondroTree testTree(File f, String testentities) throws IOException {
        if (f.exists()) f.delete();
        kelondroTree tt = new kelondroTree(f, false, 10, new kelondroRow("byte[] a-4, byte[] b-4", kelondroNaturalOrder.naturalOrder, 0));
        byte[] b;
        for (int i = 0; i < testentities.length(); i++) {
            b = testWord(testentities.charAt(i));
            tt.put(b, b);
        }
        return tt;
    }
    
   public static void bigtest(int elements) {
        System.out.println("starting big test with " + elements + " elements:");
        long start = System.currentTimeMillis();
        String[] s = permutations(elements);
        kelondroTree tt;
        File testFile = new File("test.db");
        try {
            for (int i = 0; i < s.length; i++) {
                System.out.println("*** probing tree " + i + " for permutation " + s[i]);
                // generate tree and delete elements
                tt = testTree(testFile, s[i]);
                //tt.print();
                if (countElements(tt) != tt.size()) {
                    System.out.println("wrong size for " + s[i]);
                    tt.print();
                }
                tt.close();
                for (int j = 0; j < s.length; j++) {
                    tt = testTree(testFile, s[i]);
                    //tt.print();
                    // delete by permutation j
                    for (int elt = 0; elt < s[j].length(); elt++) {
                        tt.remove(testWord(s[j].charAt(elt)), true);
                        //tt.print();
                        if (countElements(tt) != tt.size()) {
                            System.out.println("ERROR! wrong size for probe tree " + s[i] + "; probe delete " + s[j] + "; position " + elt);
                            tt.print();
                        }
                    }
                    // add another one
                    //tt.print();
                    /*
                    b = testWord('0'); tt.put(b, b);
                    b = testWord('z'); tt.put(b, b);
                    b = testWord('G'); tt.put(b, b);
                    b = testWord('t'); tt.put(b, b);
                    if (countElements(tt) != tt.size()) {
                       System.out.println("ERROR! wrong size for probe tree " + s[i] + "; probe delete " + s[j] + "; final add");
                       tt.print();
                    }
                    tt.print();
                     */
                    // close this
                    tt.close();
                }
            }
            System.out.println("FINISHED test after " + ((System.currentTimeMillis() - start) / 1000) + " seconds.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("TERMINATED");
        }
    }
    
    public static int countElements(kelondroIndex t) {
        int count = 0;
        try {
            Iterator<kelondroRow.Entry> iter = t.rows(true, null);
            kelondroRow.Entry row;
            while (iter.hasNext()) {
                count++;
                row = iter.next();
                if (row == null) System.out.println("ERROR! null element found");
                // else System.out.println("counted element: " + new
                // String(n.getKey()));
            }
        } catch (IOException e) {
        }
        return count;
    }

}
