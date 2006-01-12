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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.Vector;

public class kelondroTree extends kelondroRecords implements kelondroIndex {

    // define the Over-Head-Array
    private static short thisOHBytes   = 2; // our record definition of two bytes
    private static short thisOHHandles = 3; // and three handles overhead
    private static short thisFHandles  = 1; // file handles: one for a root pointer

    // define pointers for OH array access
    private static int magic      = 0; // pointer for OHByte-array: marker for Node purpose; defaults to 1
    private static int balance    = 1; // pointer for OHByte-array: balance value of tree node; balanced = 0

    private static int parent     = 0; // pointer for OHHandle-array: handle()-Value of parent Node
    private static int leftchild  = 1; // pointer for OHHandle-array: handle()-Value of left child Node
    private static int rightchild = 2; // pointer for OHHandle-array: handle()-Value of right child Node

    private static int root       = 0; // pointer for FHandles-array: pointer to root node

    private Search writeSearchObj = new Search();
    private kelondroOrder objectOrder = new kelondroNaturalOrder(true);
    
    public kelondroTree(File file, long buffersize, int key, int value, boolean exitOnFail) {
        this(file, buffersize, new int[] { key, value }, 1, 8, exitOnFail);
    }

    public kelondroTree(kelondroRA ra, long buffersize, int key, int value, boolean exitOnFail) {
        this(ra, buffersize, new int[] { key, value }, 1, 8, exitOnFail);
    }

    public kelondroTree(File file, long buffersize, int[] columns, boolean exitOnFail) {
        // this creates a new tree file
        this(file, buffersize, columns, columns.length /* txtProps */, 80 /* txtPropWidth */, exitOnFail);
    }

    public kelondroTree(File file, long buffersize, int[] columns, int txtProps, int txtPropsWidth, boolean exitOnFail) {
        // this creates a new tree file
        super(file, buffersize, thisOHBytes, thisOHHandles, columns, thisFHandles, txtProps, txtPropsWidth, exitOnFail);
        try {
            setHandle(root, null); // define the root value
        } catch (IOException e) {
            super.logFailure("cannot set root handle / " + e.getMessage());
            if (exitOnFail) System.exit(-1);
            throw new RuntimeException("cannot set root handle / " + e.getMessage());
        }
    }
    
    public kelondroTree(kelondroRA ra, long buffersize, int[] columns, boolean exitOnFail) {
        // this creates a new tree within a kelondroRA
        this(ra, buffersize, columns, columns.length /* txtProps */, 80 /* txtPropWidth */, exitOnFail);
    }

    public kelondroTree(kelondroRA ra, long buffersize, int[] columns, int txtProps, int txtPropsWidth, boolean exitOnFail) {
        // this creates a new tree within a kelondroRA
        super(ra, buffersize, thisOHBytes, thisOHHandles, columns,
                thisFHandles, txtProps, txtPropsWidth, exitOnFail);
        try {
            setHandle(root, null); // define the root value
        } catch (IOException e) {
            super.logFailure("cannot set root handle / " + e.getMessage());
            if (exitOnFail) System.exit(-1);
            throw new RuntimeException("cannot set root handle / " + e.getMessage());
        }
    }

    public kelondroTree(File file, long buffersize) throws IOException {
        // this opens a file with an existing tree file
        super(file, buffersize);
    }

    public kelondroTree(kelondroRA ra, long buffersize) throws IOException {
        // this opens a file with an existing tree in a kelondroRA
        super(ra, buffersize);
    }

    public void clear() throws IOException {
        super.clear();
        setHandle(root, null); // reset the root value
    }
    
    private void commitNode(Node n) throws IOException {
        Handle left = n.getOHHandle(leftchild);
        Handle right = n.getOHHandle(rightchild);
        if ((left == null) && (right == null)) n.commit(CP_LOW);
        else if (left == null) n.commit(CP_MEDIUM);
        else if (right == null) n.commit(CP_MEDIUM);
        else n.commit(CP_HIGH);
    }

    // Returns the value to which this map maps the specified key.
    public byte[][] get(byte[] key) throws IOException {
        // System.out.println("kelondroTree.get " + new String(key) + " in " + filename);
        byte[][] result = null;
        // writeLock.stay(2000, 1000);
        synchronized (writeSearchObj) {
            writeSearchObj.process(key);
            if (writeSearchObj.found()) {
                result = writeSearchObj.getMatcher().getValues();
            } else {
                result = null;
            }
        }
        // writeLock.release();
        return result;
    }
    
    public long[] getLong(byte[] key) throws IOException {
        byte[][] row = get(key);
        long[] longs = new long[columns() - 1];
        if (row == null) {
            for (int i = 0; i < columns() - 1; i++) {
                longs[i] = 0;
            }            
        } else {
            for (int i = 0; i < columns() - 1; i++) {
                longs[i] = bytes2long(row[i + 1]);
            }
        }
        return longs;
    }
        
    public class Search {

	// a search object combines the results of a search in the tree, which are
	// - the searched object is found, an index pointing to the node can be returned
	// - the object was not found, an index pointing to an appropriate possible parent node can
	//   be returned, together with the information wether the new key shall be left or right child.
	//

	private Node thenode, parentnode;
	private boolean found; // property if node was found
	private byte child;    // -1: left child; 0: root node; 1: right child
        private Handle thisHandle;
        
	protected Search() {
	}

	protected void process(byte[] key) throws IOException {    
	    // searchs the database for the key and stores the result in the thisHandle
	    // if the key was found, then found=true, thisHandle and leftchild is set;
	    // else found=false and thisHandle and leftchild is undefined
	    thisHandle = getHandle(root);
            parentnode = null;
            if (key == null) {
                child = 0;
                if (thisHandle == null) {
                    thenode = null;
                    found = false;
                } else {
                    thenode = getNode(thisHandle, null, 0);
                    found = true;
                }
            } else {
                thenode = null;
                child = 0;
                found = false;
                int c;
		HashMap visitedNodeKeys = new HashMap(); // to detect loops
		String otherkey;
		//System.out.println("Starting Compare Loop in Database " + filename); // debug
                while (thisHandle != null) {
                    try {
                        parentnode = thenode;
                        thenode = getNode(thisHandle, thenode, (child == -1) ? leftchild : rightchild);
                    } catch (IllegalArgumentException e) {
                        logWarning("kelondroTree.Search.process: fixed a broken handle");
                        found = false;
                        return;
                    }
                    if (thenode == null) {
                        throw new kelondroException(filename, "kelondroTree.Search.process: thenode==null");
                    }
                    try {
                        otherkey = new String(thenode.getKey());
                    } catch (NullPointerException e) {
                        throw new kelondroException(filename, "kelondroTree.Search.process: nullpointer" + e.getMessage() +
                                                    "\nNode: " + thenode.toString());
                    }
		    if (visitedNodeKeys.containsKey(otherkey)) {
                        // we have loops in the database.
                        // to fix this, all affected nodes must be patched
                        thenode.setOHByte(magic,   (byte) 1);
                        thenode.setOHByte(balance, (byte) 0);
                        thenode.setOHHandle(parent, null);
                        thenode.setOHHandle(leftchild, null);
                        thenode.setOHHandle(rightchild, null);
                        thenode.commit(CP_NONE);
                        /*
                        Iterator fix = visitedNodeKeys.entrySet().iterator();
                        Map.Entry entry;
                        while (fix.hasNext()) {
                            entry = (Map.Entry) fix.next();
                            thenode = (Node) entry.getValue();
                            thenode.setOHByte(magic,   (byte) 1);
                            thenode.setOHByte(balance, (byte) 0);
                            thenode.setOHHandle(parent, null);
                            thenode.setOHHandle(leftchild, null);
                            thenode.setOHHandle(rightchild, null);
                        }
                        */
                        logWarning("kelondroTree.Search.process: database contains loops; the loop-nodes have been auto-fixed");
                        found = false;
                        return;
                    }
                    //System.out.println("Comparing key = '" + new String(key) + "' with '" + otherkey + "':"); // debug
                    c = objectOrder.compare(key, thenode.getKey());
                    //System.out.println(c); // debug
                    if (c == 0) {
                        found = true;
                        //System.out.println("DEBUG: search for " + new String(key) + " ended with status=" + ((found) ? "found" : "not-found") + ", node=" + ((thenode == null) ? "NULL" : thenode.toString()) + ", parent=" + ((parentnode == null) ? "NULL" : parentnode.toString()));
                        return;
                    } else if (c < 0) {
                        child = -1;
                        thisHandle = thenode.getOHHandle(leftchild);
                    } else {
                        child = 1;
                        thisHandle = thenode.getOHHandle(rightchild);
                    }
		    visitedNodeKeys.put(otherkey, thenode);
                }
            }
            //System.out.println("DEBUG: search for " + new String(key) + " ended with status=" + ((found) ? "found" : "not-found") + ", node=" + ((thenode == null) ? "NULL" : thenode.toString()) + ", parent=" + ((parentnode == null) ? "NULL" : parentnode.toString()));
	    // we reached a node where we must insert the new value
            // the parent of this new value can be obtained by getParent()
	    // all values are set, just return
	}

	public boolean found() {
	    return found;
	}

	public Node getMatcher() {
	    if (found) return thenode; 
	    else throw new IllegalArgumentException("wrong access of matcher");
	}

	public Node getParent() {
	    if (found) return parentnode; else return thenode; 
	}

	public boolean isRoot() {
	    if (found) throw new IllegalArgumentException("wrong access of isRoot");
	    else return (child == 0);
	}

	public boolean isLeft() {
	    if (found) throw new IllegalArgumentException("wrong access of leftchild");
	    else return (child == -1);
	}
        
    public boolean isRight() {
	    if (found) throw new IllegalArgumentException("wrong access of leftchild");
	    else return (child == 1);
	}
    }

    public synchronized boolean isChild(Node childn, Node parentn, int child) {
	if (childn == null) throw new IllegalArgumentException("isLeftChild: Node parameter is NULL");
	Handle lc = parentn.getOHHandle(child);
	if (lc == null) return false;
	return (lc.equals(childn.handle()));
    }
    
    public long[] putLong(byte[] key, long[] newlongs) throws IOException {
        byte[][] newrow = new byte[newlongs.length + 1][];
        newrow[0] = key;
        for (int i = 0; i < newlongs.length; i++) {
            newrow[i + 1] = long2bytes(newlongs[i], columnSize(i + 1));
        }
        byte[][] oldrow = put(newrow);
        long[] oldlongs = new long[columns() - 1];
        if (oldrow == null) {
            for (int i = 0; i < columns() - 1; i++) {
                oldlongs[i] = 0;
            }            
        } else {
            for (int i = 0; i < columns() - 1; i++) {
                oldlongs[i] = bytes2long(oldrow[i + 1]);
            }
        }
        return oldlongs;
    }
    
    // Associates the specified value with the specified key in this map
    public byte[][] put(byte[][] newrow) throws IOException {
        byte[][] result = null;
        //writeLock.stay(2000, 1000);
        if (newrow.length != columns()) throw new IllegalArgumentException("put: wrong row length " + newrow.length + "; must be " + columns());
        // first try to find the key element in the database
        synchronized(writeSearchObj) {
            writeSearchObj.process(newrow[0]);
            if (writeSearchObj.found()) {
                // a node with this key exist. simply overwrite the content and return old content
                Node e = writeSearchObj.getMatcher();
                result = e.setValues(newrow);
                commitNode(e);
            } else if (writeSearchObj.isRoot()) {
                // a node with this key does not exist and there is no node at all
                // this therefore creates the root node if an only if there was no root Node yet
                if (getHandle(root) != null)
                    throw new kelondroException(filename, "tried to create root node twice");
                // we dont have any Nodes in the file, so start here to create one
                Node e = newNode();
                e.setValues(newrow);
                // write the propetries
                e.setOHByte(magic,   (byte) 1);
                e.setOHByte(balance, (byte) 0);
                e.setOHHandle(parent, null);
                e.setOHHandle(leftchild, null);
                e.setOHHandle(rightchild, null);
                // do updates
                e.commit(CP_LOW);
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
                Node parentNode = writeSearchObj.getParent();
                Node theNode = newNode();
                theNode.setValues(newrow);
                theNode.setOHByte(0, (byte) 1); // fresh magic
                theNode.setOHByte(1, (byte) 0); // fresh balance
                theNode.setOHHandle(parent, parentNode.handle());
                theNode.setOHHandle(leftchild, null);
                theNode.setOHHandle(rightchild, null);
                theNode.commit(CP_LOW);
                
                // check consistency and link new node to parent node
                byte parentBalance;
                if (writeSearchObj.isLeft()) {
                    if (parentNode.getOHHandle(leftchild) != null) throw new kelondroException(filename, "tried to create leftchild node twice");
                    parentNode.setOHHandle(leftchild, theNode.handle());
                } else if (writeSearchObj.isRight()) {
                    if (parentNode.getOHHandle(rightchild) != null) throw new kelondroException(filename, "tried to create rightchild node twice");
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
                Handle parentSideHandle;
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
                    increasedHight = ((java.lang.Math.abs((int) parentBalance) - java.lang.Math.abs((int) prevHight)) > 0);
                    parentNode.setOHByte(balance, parentBalance);
                    commitNode(parentNode);
                    
                    // here we either stop because we had no increased hight,
                    // or we have a balance greater then 1 or less than -1 and we do rotation
                    // or we crawl up the tree and change the next balance
                    if (!(increasedHight)) break; // finished
                    
                    // check rotation need
                    if (java.lang.Math.abs((int) parentBalance) > 1) {
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
                            Handle parentHandle = parentNode.handle();
                            LL_RightRotation(theNode, getNode(theNode.getOHHandle(leftchild), theNode, leftchild));
                            parentNode = getNode(parentHandle, null, 0); // reload the parent node
                            RR_LeftRotation(parentNode, getNode(parentNode.getOHHandle(rightchild), parentNode, rightchild));
                            break;
                        }
                        if (path.startsWith("LR")) {
                            Handle parentHandle = parentNode.handle();
                            RR_LeftRotation(theNode, getNode(theNode.getOHHandle(rightchild), theNode, rightchild));
                            parentNode = getNode(parentHandle, null, 0); // reload the parent node
                            LL_RightRotation(parentNode, getNode(parentNode.getOHHandle(leftchild), parentNode, leftchild));
                            break;
                        }
                        break;
                    } else {
                        // crawl up the tree
                        if (parentNode.getOHHandle(parent) == null) {
                            // root reached: stop
                            break;
                        } else {
                            theNode = parentNode;
                            parentNode = getNode(parentNode.getOHHandle(parent), null, 0);
                        }
                    }
                }
                
                result = null;; // that means: no previous stored value present
            }
        }
        //writeLock.release();
        return result;
    }

    private void assignChild(Node parentNode, Node childNode, int childType) throws IOException {
	parentNode.setOHHandle(childType, childNode.handle());
        childNode.setOHHandle(parent, parentNode.handle());
        commitNode(parentNode);
        commitNode(childNode);
    }

    private void replace(Node oldNode, Node oldNodeParent, Node newNode) throws IOException {
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
            Handle parentSideHandle = oldNodeParent.getOHHandle(leftchild);
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
	if (b > 0) return b; else return 0;
    }

    private static byte min0(byte b) {
	if (b < 0) return b; else return 0;
    }

    private void LL_RightRotation(Node parentNode, Node childNode) throws IOException {
	// replace the parent node; the parent is afterwards unlinked
        Handle p2Handle = parentNode.getOHHandle(parent);
        Node p2Node = (p2Handle == null) ? null : getNode(p2Handle, null, 0);
        replace(parentNode, p2Node, childNode);

	// set the left son of the parent to the right son of the childNode
	Handle childOfChild = childNode.getOHHandle(rightchild);
	if (childOfChild == null) {
	    parentNode.setOHHandle(leftchild, null);
	} else {
	    assignChild(parentNode, getNode(childOfChild, childNode, rightchild), leftchild);
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

    private void RR_LeftRotation(Node parentNode, Node childNode) throws IOException {
	// replace the parent node; the parent is afterwards unlinked
        Handle p2Handle = parentNode.getOHHandle(parent);
        Node p2Node = (p2Handle == null) ? null : getNode(p2Handle, null, 0);
	replace(parentNode, p2Node, childNode);

	// set the left son of the parent to the right son of the childNode
	Handle childOfChild = childNode.getOHHandle(leftchild);
	if (childOfChild == null) {
	    parentNode.setOHHandle(rightchild, null);
	} else {
	    assignChild(parentNode, getNode(childOfChild, childNode, leftchild), rightchild);
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
	byte[][] row = new byte[2][];
	row[0] = key;
	row[1] = value;
	byte[][] ret = put(row);
	if (ret == null) return null; else return ret[1];
    }
    
    // Removes the mapping for this key from this map if present (optional operation).
    public byte[][] remove(byte[] key) throws IOException {
        synchronized(writeSearchObj) {
            writeSearchObj.process(key);
            if (writeSearchObj.found()) {
                Node result = writeSearchObj.getMatcher();
                byte[][] values = result.getValues();
                remove(result, writeSearchObj.getParent());
                return values;
            } else {
                return null;
            }
        }
    }

    public synchronized void removeAll() throws IOException {
        while (size() > 0) remove(lastNode(), null);
    }
    
    private void remove(Node node, Node parentOfNode) throws IOException {
	// there are three cases when removing a node
	// - the node is a leaf - it can be removed easily
	// - the node has one child - the child replaces the node
	// - the node has two childs - it can be replaced either
	//   by the greatest node of the left child or the smallest
	//   node of the right child

	Node childnode;
	if ((node.getOHHandle(leftchild) == null) && (node.getOHHandle(rightchild) == null)) {
	    // easy case: the node is a leaf
	    if (parentOfNode == null) {
		// this is the root!
		setHandle(root, null);
	    } else {
                Handle h = parentOfNode.getOHHandle(leftchild);
		if ((h != null) && (h.equals(node.handle()))) parentOfNode.setOHHandle(leftchild, null);
                h = parentOfNode.getOHHandle(rightchild);
		if ((h != null) && (h.equals(node.handle()))) parentOfNode.setOHHandle(rightchild, null);
                commitNode(parentOfNode);
	    }
	} else if ((node.getOHHandle(leftchild) != null) && (node.getOHHandle(rightchild) == null)) {
	    replace(node, parentOfNode, getNode(node.getOHHandle(leftchild), node, leftchild));
	} else if ((node.getOHHandle(leftchild) == null) && (node.getOHHandle(rightchild) != null)) {
	    replace(node, parentOfNode, getNode(node.getOHHandle(rightchild), node, rightchild));
	} else {
	    // difficult case: node has two children
	    Node repl = lastNode(getNode(node.getOHHandle(leftchild), node, leftchild));
	    //System.out.println("last node is " + repl.toString());
	    // we remove that replacement node and put it where the node was
	    // this seems to be recursive, but is not since the replacement
	    // node cannot have two children (it would not have been the smallest or greatest)
	    Node n;
	    Handle h;
            // remove leaf
	    if ((repl.getOHHandle(leftchild) == null) && (repl.getOHHandle(rightchild) == null)) {
		// the replacement cannot be the root, so simply remove from parent node
		n = getNode(repl.getOHHandle(parent), null, 0); // parent node of replacement node
		h = n.getOHHandle(leftchild);
		if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(leftchild, null);
                h = n.getOHHandle(rightchild);
		if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(rightchild, null);
                commitNode(n);
	    } else if ((repl.getOHHandle(leftchild) != null) && (repl.getOHHandle(rightchild) == null)) {
                try {
                    childnode = getNode(repl.getOHHandle(leftchild), repl, leftchild);
                    replace(repl, getNode(repl.getOHHandle(parent), null, 0), childnode);
                } catch (IllegalArgumentException e) {
                    // now treat the situation as if that link had been null before
                    n = getNode(repl.getOHHandle(parent), null, 0); // parent node of replacement node
                    h = n.getOHHandle(leftchild);
                    if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(leftchild, null);
                    h = n.getOHHandle(rightchild);
                    if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(rightchild, null);
                    commitNode(n);
                }
	    } else if ((repl.getOHHandle(leftchild) == null) && (repl.getOHHandle(rightchild) != null)) {
                try {
                    childnode = getNode(repl.getOHHandle(rightchild), repl, rightchild);
                    replace(repl, getNode(repl.getOHHandle(parent), null, 0), childnode);
                } catch (IllegalArgumentException e) {
                    // now treat the situation as if that link had been null before
                    n = getNode(repl.getOHHandle(parent), null, 0); // parent node of replacement node
                    h = n.getOHHandle(leftchild);
                    if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(leftchild, null);
                    h = n.getOHHandle(rightchild);
                    if ((h != null) && (h.equals(repl.handle()))) n.setOHHandle(rightchild, null);
                    commitNode(n);
                }
	    }
	    //System.out.println("node before reload is " + node.toString());
	    node = getNode(node.handle(), null, 0); // reload the node, it is possible that it has been changed
	    //System.out.println("node after reload is " + node.toString());
            
            // now plant in the replha node
	    byte b = node.getOHByte(balance); // save balance of disappearing node
            Handle parentHandle = node.getOHHandle(parent);
            Handle leftchildHandle = node.getOHHandle(leftchild);
            Handle rightchildHandle = node.getOHHandle(rightchild);
	    replace(node, parentOfNode, repl);
	    repl.setOHByte(balance, b); // restore balance
	    repl.setOHHandle(parent, parentHandle); // restore handles
            repl.setOHHandle(leftchild, leftchildHandle);
            repl.setOHHandle(rightchild, rightchildHandle);
            commitNode(repl);
	    // last thing to do: change uplinks of children to this new node
	    if (leftchildHandle != null) {
		n = getNode(leftchildHandle, node, leftchild);
                n.setOHHandle(parent, repl.handle());
                commitNode(n);
	    }
	    if (rightchildHandle != null) {
		n = getNode(rightchildHandle, node, rightchild);
                n.setOHHandle(parent, repl.handle());
                commitNode(n);
	    }
 	}
        // move node to recycling queue
	deleteNode(node.handle());
    }

    private Node firstNode() throws IOException {
	Handle h = getHandle(root);
	if (h == null) return null;
	return firstNode(getNode(h, null, 0));
    }
    
    private Node firstNode(Node node) throws IOException {
        if (node == null) throw new IllegalArgumentException("firstNode: node=null"); 
	Handle h = node.getOHHandle(leftchild);
        HashSet visitedNodeKeys = new HashSet(); // to detect loops
        String nodeKey;
        while (h != null) {
            try {
                node = getNode(h, node, leftchild);
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
    
    private Node lastNode() throws IOException {
	Handle h = getHandle(root);
	if (h == null) return null;
	return lastNode(getNode(h, null, 0));
    }

    private Node lastNode(Node node) throws IOException {
	if (node == null) throw new IllegalArgumentException("lastNode: node=null"); 
        Handle h = node.getOHHandle(rightchild);
        HashSet visitedNodeKeys = new HashSet(); // to detect loops
        String nodeKey;
	while (h != null) {
	    try {
                node = getNode(h, node, rightchild);
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
    
    public synchronized Iterator nodeIterator(boolean up, boolean rotating) {
	// iterates the elements in a sorted way. returns Node - type Objects
	try {
	    return new nodeIterator(up, rotating);
	} catch (IOException e) {
	    throw new RuntimeException("error creating an iteration: " + e.getMessage());
	}
    }

    public synchronized Iterator nodeIterator(boolean up, boolean rotating, byte[] firstKey) {
	// iterates the elements in a sorted way. returns Node - type Objects
	try {
            Search search = new Search();
            search.process(firstKey);
            if (search.found()) {
                Node matcher = search.getMatcher();
                return new nodeIterator(up, rotating, matcher);
            } else {
                Node nn = search.getParent();
                if (nn == null) {
                    return (new HashSet()).iterator(); // an empty iterator
                } else {
                    // the node nn may be greater or smaller than the firstKey
                    // depending on the ordering direction,
                    // we must find the next smaller or greater node
                    return new correctedNodeIterator(up, rotating, nn, firstKey);
                }
            }
	} catch (IOException e) {
	    throw new RuntimeException("error creating an iteration: " + e.getMessage());
	}
    }
    
    private class correctedNodeIterator implements Iterator {
	
        Iterator ii;
        Node nextNode;
        boolean asc, rot;
        Node start;
        byte[] firstKey;
        
        public correctedNodeIterator(boolean up, boolean rotating, Node start, byte[] firstKey) throws IOException {
            asc = up;
            rot = rotating;
            this.start = start;
            this.firstKey = firstKey;
            init();
        }
        
        private void init() throws IOException {
            ii = new nodeIterator(asc, rot, start);
            nextNode = (ii.hasNext()) ? (Node) ii.next() : null;
            if (nextNode != null) {
                int c = objectOrder.compare(firstKey, nextNode.getKey());
                if ((c > 0) && (asc)) {
                    // firstKey > nextNode.getKey()
                    logWarning("CORRECTING ITERATOR: firstKey=" + new String(firstKey) + ", nextNode=" + new String(nextNode.getKey()));
                    nextNode = (ii.hasNext()) ? (Node) ii.next() : null;
                }
                if ((c < 0) && (!(asc))) {
                    nextNode = (ii.hasNext()) ? (Node) ii.next() : null;
                }
            }
        }
        
        public void finalize() {
            ii = null;
            nextNode = null;
        }
            
        public boolean hasNext() {
            return nextNode != null;
        }

        public Object next() {
            Node r = nextNode;
            nextNode = (ii.hasNext()) ? (Node) ii.next() : null;
            if ((nextNode != null) && (asc == (objectOrder.compare(r, nextNode) == 1))) {
                // correct wrong order (this should not happen)
                if (rot) {
                    try {
                        init();
                    } catch (IOException e) {
                        nextNode = null;
                    }
                } else {
                    nextNode = null;
                }
            }
            return r;
        }
        
        public void remove() {
            throw new java.lang.UnsupportedOperationException("kelondroTree: remove in kelondro Tables not yet supported");
	}
    }
    
    private class nodeIterator implements Iterator {
	// we implement an iteration! (not a recursive function as the structure would suggest...)
	// the iterator iterates Node objects
	Node nextNode = null;
        boolean up, rot;
        LinkedList nodeStack;
	int count;
        
	public nodeIterator(boolean up, boolean rotating) throws IOException {
            this(up, rotating, (up) ? firstNode() : lastNode());
	}
        
	public nodeIterator(boolean up, boolean rotating, Node start) throws IOException {
	    this.count = 0;
            this.up = up;
            this.rot = rotating;
            init(start);
	}
        
        private void init(Node start) throws IOException {
            this.nextNode = start;
            
            // fill node stack for start node
            nodeStack = new LinkedList();
            
            Handle searchHandle = getHandle(root);
            if (searchHandle == null) {nextNode = null; return;}

            Node searchNode = getNode(searchHandle, null, 0);            
            byte[] startKey = start.getKey();
            int c, ct;
            while ((c = objectOrder.compare(startKey, searchNode.getKey())) != 0) {
                // the current 'thisNode' is not the start node, put it on the stack
                ct = (c < 0) ? leftchild : rightchild;
                nodeStack.addLast(new Object[]{searchNode, new Integer(ct)});
                
                // go to next node
                searchHandle = searchNode.getOHHandle(ct);
                if (searchHandle == null) throw new kelondroException(filename, "nodeIterator.init: start node does not exist (handle null)");
                searchNode = getNode(searchHandle, searchNode, ct);
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

        public Object next() {
	    count++;
            if ((rot) && (nextNode == null)) try {
                init((up) ? firstNode() : lastNode());
            } catch (IOException e) {
                throw new kelondroException(filename, "io-error while rot");
            }
            if (nextNode == null) throw new kelondroException(filename, "nodeIterator.next: no more entries available");
	    if ((count > size()) && (!(rot))) throw new kelondroException(filename, "nodeIterator.next: internal loopback; database corrupted");
            Object ret = nextNode;
            
            // middle-case
            
            try {
                int childtype = (up) ? rightchild : leftchild;
                Handle childHandle = nextNode.getOHHandle(childtype);
                if (childHandle != null) {
                    //System.out.println("go to other leg, stack size=" + nodeStack.size());
                    // we have walked one leg of the tree; now go to the other one: step down to next child
                    HashSet visitedNodeHandles = new HashSet(); // to detect loops
                    nodeStack.addLast(new Object[]{nextNode, new Integer(childtype)});
                    nextNode = getNode(childHandle, nextNode, childtype);
                    childtype = (up) ? leftchild : rightchild;
                    while ((childHandle = nextNode.getOHHandle(childtype)) != null) {
                        if (visitedNodeHandles.contains(childHandle)) {
                            // try to repair the nextNode
                            nextNode.setOHHandle(childtype, null);
                            nextNode.commit(CP_NONE);
                            logWarning("nodeIterator.next: internal loopback; fixed loop and try to go on");
                            break;
                        }
                        visitedNodeHandles.add(childHandle);
                        try {
                            nodeStack.addLast(new Object[]{nextNode, new Integer(childtype)});
                            nextNode = getNode(childHandle, nextNode, childtype);
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
                        Node parentNode = null;
                        int parentpointer = (up) ? rightchild : leftchild;
                        while ((nodeStack.size() != 0) && (parentpointer == ((up) ? rightchild : leftchild))) {
                            //System.out.println("step up");
                            // go on, walk up further
                            stacktop = (Object[]) nodeStack.removeLast(); // top of stack: Node/parentpointer pair
                            parentNode = (Node) stacktop[0];
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
            throw new java.lang.UnsupportedOperationException("kelondroTree: remove in kelondro Tables not yet supported");
	}
    }

    public synchronized Iterator rows(boolean up, boolean rotating) throws IOException {
	// iterates the rows of the Nodes
	// enumerated objects are of type byte[][]
        // iterates the elements in a sorted way.
	return new rowIterator(nodeIterator(up, rotating));
    }
    
    public synchronized Iterator rows(boolean up, boolean rotating, byte[] firstKey) {
        return new rowIterator((firstKey == null) ? nodeIterator(up, rotating) : nodeIterator(up, rotating, firstKey));
    }
    
    public class rowIterator implements Iterator {
        
        Iterator nodeIterator;
        
        public rowIterator(Iterator nodeIterator) {
            this.nodeIterator = nodeIterator;
        }
        
        public boolean hasNext() {
            return (nodeIterator.hasNext());
        }
        
        public Object next() {
            try {
		Node nextNode = (Node) nodeIterator.next();
		if (nextNode == null) throw new kelondroException(filename, "no more elements available");
                return nextNode.getValues();
            } catch (IOException e) {
                throw new kelondroException(filename, "io-error: " + e.getMessage());
            }
        }
        
        public void remove() {
        }
        
    }

    public synchronized keyIterator keys(boolean up, boolean rotating) {
	// iterates only the keys of the Nodes
	// enumerated objects are of type String
        // iterates the elements in a sorted way.
	return new keyIterator(nodeIterator(up, rotating));
    }
    
    public Iterator keys(boolean up, boolean rotating, byte[] firstKey) {
        return new keyIterator(nodeIterator(up, rotating, firstKey));
    }
    
    public class keyIterator implements Iterator {
        
        Iterator nodeIterator;
        
        public keyIterator(Iterator nodeIterator) {
            this.nodeIterator = nodeIterator;
        }
        
        public void finalize() {
            nodeIterator = null;
        }
                
        public boolean hasNext() {
            return (nodeIterator.hasNext());
        }
        
        public Object next() {
            Node nextNode = (Node) nodeIterator.next();
            if (nextNode == null) throw new kelondroException(filename, "no more elements available");
            return new String(nextNode.getKey());
        }
        
        public void remove() {
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
		    put(buffer);
		    recs++;
		}

	    }
	}
	return recs;
    }

    public synchronized int height() {
        try {
            Handle h = getHandle(root);
            if (h == null) return 0;
            return height(getNode(h, null, 0));
        } catch (IOException e) {
            return 0;
        }
    }
    
    private int height(Node node) throws IOException {
        if (node == null) return 0;
        Handle h = node.getOHHandle(leftchild);
	int hl = (h == null) ? 0 : height(getNode(h, node, leftchild));
        h = node.getOHHandle(rightchild);
        int hr = (h == null) ? 0 : height(getNode(h, node, rightchild));
        if (hl > hr) return hl + 1; else return hr + 1;
    }
        
    public String np(Object n) {
	if (n == null) return "NULL"; else return n.toString();
    }

    public void print() throws IOException {
        super.print(false);
        int height = height();
        System.out.println("HEIGHT = " + height);
        Vector thisline = new Vector();
        thisline.add(getHandle(root));
        Vector nextline;
        Handle handle;
        Node node;
        int linelength, width = (1 << (height - 1)) * (columnSize(0) + 1);
        String key;
        for (int h = 1; h < height; h++) {
            linelength = width / (thisline.size() * 2);
            nextline = new Vector();
            for (int i = 0; i < thisline.size(); i++) {
                handle = (Handle) thisline.elementAt(i);
                if (handle == null) {
                    node = null;
                    key = "[..]";
                } else {
                    node = getNode(handle, null, 0);
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
                handle = (Handle) thisline.elementAt(i);
                if (handle == null) {
                    node = null;
                    key = "NULL";
                } else {
                    node = getNode(handle, null, 0);
                    if (node == null) key = "NULL"; else key = new String(node.getKey());
                }
                System.out.print(key);
                for (int j = 0; j < (linelength - key.length()); j++) System.out.print(" ");
            }
        }
	System.out.println();
    }
    
    private static void cmd(String[] args) {
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
                    kelondroTree fm = new kelondroTree(testFile, 0x100000, 4, 4, true);
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
                    fm.remove("def1".getBytes()); fm.remove("bab1".getBytes());
                    fm.remove("abc2".getBytes()); fm.remove("bcd2".getBytes());
                    fm.remove("def2".getBytes()); fm.remove("bab2".getBytes());
                    fm.put("def1".getBytes(), dummy); fm.put("bab1".getBytes(), dummy);
                    fm.put("abc2".getBytes(), dummy); fm.put("bcd2".getBytes(), dummy);
                    fm.put("def2".getBytes(), dummy); fm.put("bab2".getBytes(), dummy);
                    fm.print();
                    fm.close();
		    ret = null;
		}
	    } else if (args.length == 2) {
		kelondroTree fm = new kelondroTree(new File(args[1]), 0x100000);
		if (args[0].equals("-v")) {
		    fm.print();
		    ret = null;
		}
		fm.close();
	    } else if (args.length == 3) {
		if (args[0].equals("-d")) {
		    kelondroTree fm = new kelondroTree(new File(args[1]), 0x100000);
		    fm.remove(args[2].getBytes());
		    fm.close();
		} else if (args[0].equals("-i")) {
		    kelondroTree fm = new kelondroTree(new File(args[1]), 0x100000);
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
		    kelondroTree fm = new kelondroTree(new File(args[1]), 0x100000);
		    byte[][] ret2 = fm.get(args[2].getBytes());
		    ret = ((ret2 == null) ? null : ret2[1]); 
		    fm.close();
		} else if (args[0].equals("-n")) {
		    kelondroTree fm = new kelondroTree(new File(args[1]), 0x100000);
		    //byte[][] keys = fm.getSequentialKeys(args[2].getBytes(), 500, true);
                    Iterator rowIt = fm.rows(true, false, (args[2].length() == 0) ? null : args[2].getBytes());
                    Vector v = new Vector();
                    while (rowIt.hasNext()) v.add(new String(((byte[][]) rowIt.next())[0]));
                    ret = v.toString().getBytes(); 
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
		    kelondroTree fm = new kelondroTree(f, 0x100000, lens, true);
		    fm.close();
		} else if (args[0].equals("-u")) {
		    kelondroTree fm = new kelondroTree(new File(args[3]), 0x100000);
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
    
    public kelondroOrder order() {
        return this.objectOrder;
    }

    public static void main(String[] args) {
	cmd(args);
        //bigtest(Integer.parseInt(args[0]));
        //randomtest(Integer.parseInt(args[0]));
        //smalltest();
    }
 
    public static String[] permutations(int letters) {
        String p = "";
        for (int i = 0; i < letters; i++) p = p + ((char) (((int)'A') + i));
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
                tt = new kelondroTree(testFile, 200, 4 ,4, true);
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
                        tt.remove(b);
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
            try {tt.print();} catch (IOException ee) {}
            System.out.println("TERMINATED");
        }
    }
    
    public static void smalltest() {
        File f = new File("test.db");
        if (f.exists()) f.delete();
        try {
            kelondroTree tt = new kelondroTree(f, 0, 4, 4, true);
            byte[] b;
            b = testWord('B'); tt.put(b, b); //tt.print();
            b = testWord('C'); tt.put(b, b); //tt.print();
            b = testWord('D'); tt.put(b, b); //tt.print();
            b = testWord('A'); tt.put(b, b); //tt.print();
            b = testWord('D'); tt.remove(b); //tt.print();
            b = testWord('B'); tt.remove(b); //tt.print();
            b = testWord('B'); tt.put(b, b); //tt.print();
            b = testWord('D'); tt.put(b, b);
            b = testWord('E'); tt.put(b, b);
            b = testWord('F'); tt.put(b, b);
            b = testWord('G'); tt.put(b, b);
            //b = testWord('H'); tt.put(b, b);
            b = testWord('I'); tt.put(b, b);
            b = testWord('J'); tt.put(b, b);
            b = testWord('K'); tt.put(b, b);
            b = testWord('L'); tt.put(b, b);
            int c = countElements(tt);
            System.out.println("elements: " + c);
            Iterator i = tt.nodeIterator(true, true, testWord('G'));
            for (int j = 0; j < c; j++) {
                System.out.println("Node " + j + ": " + new String(((Node) i.next()).getKey()));
            }
            System.out.println("TERMINATED");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static kelondroTree testTree(File f, String testentities) throws IOException {
        if (f.exists()) f.delete();
        kelondroTree tt = new kelondroTree(f, 0, 4, 4, true);
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
                        tt.remove(testWord(s[j].charAt(elt)));
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
    
    public static int countElements(kelondroTree t) {
        int count = 0;
        Iterator iter = t.nodeIterator(true, false);
        Node n;
        while (iter.hasNext()) {
            count++;
            n = (Node) iter.next();
            if (n == null) System.out.println("ERROR! null element found");
            //else System.out.println("counted element: " + new String(n.getKey()));
        }
        return count;
    }
}
