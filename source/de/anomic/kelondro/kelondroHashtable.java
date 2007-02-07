// kelondroHashtable.java
// ------------------
// part of the Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 21.06.2005
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
 
 we implement a hashtable based on folded binary trees
 each hight in these binary trees represents one step of rehasing
 the re-hashing is realised by extending the number of relevant bits in the given hash
 We construct the binary tree as follows
 - there exists no root node
 - at height-1 are 2 nodes, and can be accessed by using only the least significant bit of the hash
 - at height-2 are 4 nodes, addresses by (hash & 3) - mapping the 2 lsb of the hash
 - at height-3 are 8 nodes, addresses by (hash & 7) 
 - .. and so on.
 The number of nodes N(k) that are needed for a tree of height-k is

   N(k) = 2**k + N(k-1) = 2**(k + 1) - 2   [where k > 0]
 
 We fold this tree by putting all heights of the tree in a sequence
 
 Computation of the position (the index) of a node:
 given:
   hash h, with k significant bits (representing a height-k): h|k
 then the position of a node node(h,k) is
 
   node(h,k) = N(k - 1) + h|k   [where k > 0]
 
 We use these nodes to sequentially store a hash h at position node(h, 1), and
 if that fails on node(h, 2), node(h, 3) and so on.
 
 This is highly inefficient for the most heights k = 1, ..., (?)
 The 'inefficient-border' depends on the number of elements that we want to store.
 
 We therefore introduce an offset o which is the number of bits that are not used
 at the beginning of (re-)hashing. But even if these o re-hasing steps are not done,
 all bits of the hash are relevant.
 Now the number of nodes N(k) that are needed is computed by N(k,o):
 
   N(k,o) = N(k) - N(o) = 2**(k + 1) - 2**(o + 1)   [where k > o, o >= 0]
 
 When o=0 then this is equivalent to N(k).

 The node-formula must be adopted as well
 
  node(h,k,o) = N(k - 1, o) + h|k   [where k > o, o >= 0]
 
 So if you set an offset o, this leads to a minimum number of nodes
 at level k=o+1: node(0,o + 1,o) = N(o, o) = 0 (position of the first entry)
 
 Computatiion of the maxlen 'maxk', the maximum height of the tree for a given
 number of maximum entries 'maxsize' in the hashtable:
 maxk shall be computed in such a way, that N(k,o) <= maxsize, for any o or k
 This means paricualary, that
 
   node(h,k,o) < maxsize
 
 for h|k we must consider the worst case:
 
   h|k (by maxk) = 2**k - 1
 
 therefore
 
   node(h,maxk,o) < maxsize
   N(maxk - 1, o) + h|maxk < maxsize  [where maxk > o, o >= 0]
   2**maxk - 2**(o + 1) + 2**maxk - 1 < maxsize  [where maxk > o, o >= 0]
   2**maxk - 2**(o + 1) + 2**maxk < maxsize + 1  [where maxk > o, o >= 0]
   2**maxk + 2**maxk < maxsize + 2**(o + 1) + 1 [where maxk > o, o >= 0]
   2**(maxk+1) < maxsize + 2**(o + 1) + 1  [where maxk > o, o >= 0]
   maxk < log2(maxsize + 2**(o + 1) + 1)  [where maxk > o, o >= 0]
 
 setting maxk to
 
   maxk = log2(maxsize)
 
 will make this relation true in any case, even if maxk = log2(maxsize) + 1
 would also be correct in some cases

 Now we can use the following formula to create the folded binary hash tree:
 
   node(h,k,o) = 2**k - 2**(o + 1) + h|k
 
 to compute the node index and
 
   maxk = log2(maxsize)
 
 to compute the upper limit of re-hashing
 
 
 */

package de.anomic.kelondro;


import java.io.File;
import java.io.IOException;

public class kelondroHashtable {
    
    private   kelondroFixedWidthArray hashArray;
    protected int offset;
    protected int maxk;
    private   int maxrehash;
    private   kelondroRow.Entry dummyRow;
    
    private   static final byte[] dummyKey = kelondroBase64Order.enhancedCoder.encodeLong(0, 5).getBytes();

    public kelondroHashtable(File file, kelondroRow rowdef, int offset, int maxsize, int maxrehash) throws IOException {
        // this creates a new hashtable
        // the key element is not part of the columns array
        // this is unlike the kelondroTree, where the key is part of a row
        // the offset is a number of bits that is omitted in the folded tree hierarchy
        // a good number for offset is 8
        // the maxsize number is the maximum number of elements in the hashtable
        // this number is needed to omit grow of the table in case of re-hashing
        // the maxsize is re-computed to a virtual folding height and will result in a tablesize
        // less than the given maxsize. The actual maxsize can be retrieved by maxsize()
        boolean fileExisted = file.exists();
        this.hashArray = new kelondroFixedWidthArray(file, extCol(rowdef), 6);
        if (fileExisted) {
            this.offset    = hashArray.geti(0);
            this.maxk      = hashArray.geti(1);
            this.maxrehash = hashArray.geti(2);
        } else {
            this.offset = offset;
            this.maxk = kelondroMSetTools.log2a(maxsize); // equal to |log2(maxsize)| + 1
            if (this.maxk >= kelondroMSetTools.log2a(maxsize + power2(offset + 1) + 1) - 1) this.maxk--;
            this.maxrehash = maxrehash;
            dummyRow = this.hashArray.row().newEntry();
            dummyRow.setCol(0, dummyKey);
            //for (int i = 0; i < hashArray.row().columns(); i++)
            hashArray.seti(0, this.offset);
            hashArray.seti(1, this.maxk);
            hashArray.seti(2, this.maxrehash);
        }
    }
    
    private kelondroRow extCol(kelondroRow rowdef) {
        kelondroColumn[] newCol = new kelondroColumn[rowdef.columns() + 1];
        newCol[0] = new kelondroColumn("Cardinal key-4 {b256}");
        for (int i = 0; i < rowdef.columns(); i++) newCol[i + 1] = rowdef.column(i);
        return new kelondroRow(newCol, rowdef.objectOrder, rowdef.primaryKey);
    } 
    
    public static int power2(int x) {
	int p = 1;
	while (x > 0) {p = p << 1; x--;}
	return p;
    }

    public synchronized byte[][] get(int key) throws IOException {
        Object[] search = search(new Hash(key));
        if (search[1] == null) return null;
        byte[][] row = (byte[][]) search[1];
        byte[][] result = new byte[row.length - 1][];
        System.arraycopy(row, 1, result, 0, row.length - 1);
        return result;
    }

    public synchronized kelondroRow.Entry put(int key, kelondroRow.Entry rowentry) throws IOException {
        Hash hash = new Hash(key);
        
        // find row
        Object[] search = search(hash);
        kelondroRow.Entry oldhkrow;
        int rowNumber = ((Integer) search[0]).intValue();
        if (search[1] == null) {
            oldhkrow = null;
        } else {
            oldhkrow = (kelondroRow.Entry) search[1];
        }
        
        // make space
        while (rowNumber >= hashArray.size()) hashArray.overwrite(hashArray.size(), dummyRow);
        
        // write row
        kelondroRow.Entry newhkrow = hashArray.row().newEntry();
        newhkrow.setCol(0, hash.key());
        newhkrow.setCol(1, rowentry.bytes());
        hashArray.overwrite(rowNumber, newhkrow);
        return hashArray.row().newEntry(oldhkrow.getColBytes(1));
    }
    
    private Object[] search(Hash hash) throws IOException {
        kelondroRow.Entry hkrow;
        int rowKey;
        int rowNumber;
        do {
            rowNumber = hash.node();
            if (rowNumber >= hashArray.size()) return new Object[]{new Integer(rowNumber), null};
            hkrow = hashArray.get(rowNumber);
            rowKey = (int) hkrow.getColLong(0);
            if (rowKey == 0) return new Object[]{new Integer(rowNumber), null};
            hash.rehash();
        } while (rowKey != hash.key());
        return new Object[]{new Integer(rowNumber), hkrow};
    }
    
    
    private class Hash {
        int key;
        int hash;
        int depth;
        public Hash(int key) {
            this.key = key;
            this.hash = key;
            this.depth = offset + 1;
        }
        public int key() {
            return key;
        }
        private int hash() {
            return hash & (power2(depth) - 1); // apply mask
        }
        public int depth() {
            return depth;
        }
        public void rehash() {
            depth++;
            if (depth > maxk) {
                depth = offset + 1;
                hash = (int) ((5 * (long) hash - 7) / 3 + 13);
            }
        }
        public int node() {
            // node(h,k,o) = 2**k - 2**(o + 1) + h|k
            return power2(depth) - power2(offset + 1) + hash();
        }
    }
}
