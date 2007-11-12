// kelondroRowSet.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 20.06.2006 on http://www.anomic.de
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.kelondro;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import de.anomic.server.logging.serverLog;

public class kelondroRowSet extends kelondroRowCollection implements kelondroIndex {

    private static final int collectionReSortLimit = 300;
    
    private kelondroProfile profile;

    public kelondroRowSet(kelondroRowSet rs) {
        super(rs);
        this.profile = rs.profile;
    }

    public kelondroRowSet(kelondroRow rowdef, int objectCount, byte[] cache, int sortBound) {
        super(rowdef, objectCount, cache, sortBound);
        assert rowdef.objectOrder != null;
        this.profile = new kelondroProfile();
    }
        
    public kelondroRowSet(kelondroRow rowdef, int objectCount) {
        super(rowdef, objectCount);
        assert rowdef.objectOrder != null;
        this.profile = new kelondroProfile();
    }
    
    public kelondroRowSet(kelondroRow rowdef, kelondroRow.Entry exportedCollectionRowEnvironment, int columnInEnvironment) {
        super(rowdef, exportedCollectionRowEnvironment, columnInEnvironment);
        assert rowdef.objectOrder != null;
        this.profile = new kelondroProfile();
    }

    public void setOrdering(kelondroOrder newOrder, int newColumn) {
        assert newOrder != null;
        if ((rowdef.objectOrder == null) ||
            (!(rowdef.objectOrder.signature().equals(newOrder.signature()))) ||
            (newColumn != rowdef.primaryKeyIndex)) {
            rowdef.setOrdering(newOrder, newColumn);
            this.sortBound = 0;
        }
    }
    
	public void reset() {
		super.reset();
		this.profile = new kelondroProfile();
	}
   
    public synchronized boolean has(byte[] key) throws IOException {
        return (get(key) != null);
    }
    
    public synchronized kelondroRow.Entry get(byte[] key) {
        return get(key, 0, key.length);
    }
    
    private kelondroRow.Entry get(byte[] key, int astart, int alength) {
        long handle = profile.startRead();
        int index = find(key, astart, alength);
        kelondroRow.Entry entry = (index >= 0) ? get(index) : null;
        profile.stopRead(handle);
        return entry;
    }
    
    public synchronized void putMultiple(List rows) throws IOException {
        Iterator i = rows.iterator();
        while (i.hasNext()) put((kelondroRow.Entry) i.next());
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) {
        return put(row);
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry entry) {
        assert (entry != null);
        assert (entry.getPrimaryKeyBytes() != null);
        //assert (!(serverLog.allZero(entry.getColBytes(super.sortColumn))));
        long handle = profile.startWrite();
        int index = -1;
        kelondroRow.Entry oldentry = null;
        index = find(entry.bytes(), (rowdef.primaryKeyIndex < 0) ? 0 :super.rowdef.colstart[rowdef.primaryKeyIndex], super.rowdef.primaryKeyLength);
        if (index < 0) {
            super.addUnique(entry);
        } else {
            oldentry = get(index);
            set(index, entry);
        }
        profile.stopWrite(handle);
        return oldentry;
    }

    private synchronized kelondroRow.Entry remove(byte[] a, int start, int length, boolean keepOrder) {
        int index = find(a, start, length);
        if (index < 0) return null;
        //System.out.println("remove: chunk found at index position (before remove) " + index + ", inset=" + serverLog.arrayList(super.chunkcache, super.rowdef.objectsize() * index, length + 10) + ", searchkey=" + serverLog.arrayList(a, start, length));
        kelondroRow.Entry entry = super.get(index);
        super.removeRow(index, keepOrder);
        //System.out.println("remove: chunk found at index position (after  remove) " + index + ", inset=" + serverLog.arrayList(super.chunkcache, super.rowdef.objectsize() * index, length) + ", searchkey=" + serverLog.arrayList(a, start, length));
        int findagainindex = find(a, start, length);
        //System.out.println("kelondroRowSet.remove");
        assert findagainindex < 0 : "remove: chunk found again at index position (after  remove) " + findagainindex + ", index(before) = " + index + ", inset=" + serverLog.arrayList(super.chunkcache, super.rowdef.objectsize * findagainindex, length) + ", searchkey=" + serverLog.arrayList(a, start, length); // check if the remove worked
        return entry;
    }

    public kelondroRow.Entry remove(byte[] a, boolean keepOrder) {
        return remove(a, 0, a.length, keepOrder);
    }

    private int find(byte[] a, int astart, int alength) {
        // returns the chunknumber; -1 if not found
        
        if (rowdef.objectOrder == null) return iterativeSearch(a, astart, alength, 0, this.chunkcount);
        
        // check if a re-sorting makes sense
        if ((this.chunkcount - this.sortBound) > collectionReSortLimit) {
        	sort();
        }
        if ((this.rowdef.objectOrder != null) && (this.rowdef.objectOrder instanceof kelondroBase64Order) && (this.sortBound > 4000)) {
            // first try to find in sorted area
            final byte[] compiledPivot = compilePivot(a, astart, alength);
            int p = binarySearchCompiledPivot(compiledPivot);
            if (p >= 0) return p;
            
            // then find in unsorted area
            return iterativeSearchCompiledPivot(compiledPivot, this.sortBound, this.chunkcount);
        } else {
            // first try to find in sorted area
            int p = binarySearch(a, astart, alength);
            if (p >= 0) return p;
        
            // then find in unsorted area
            return iterativeSearch(a, astart, alength, this.sortBound, this.chunkcount);
        }        
    }
    
    private int iterativeSearch(byte[] key, int astart, int alength, int leftBorder, int rightBound) {
        // returns the chunknumber        
        if (rowdef.objectOrder == null) {
            for (int i = leftBorder; i < rightBound; i++) {
                if (match(key, astart, alength, i)) return i;
            }
            return -1;
        } else {
            // we dont do a special handling of kelondroBase64Order here, because tests showed that this produces too much overhead
            for (int i = leftBorder; i < rightBound; i++) {
                if (compare(key, astart, alength, i) == 0) return i;
            }
            return -1;
        }
    }
    
    private int iterativeSearchCompiledPivot(byte[] compiledPivot, int leftBorder, int rightBound) {
        // returns the chunknumber
        assert (rowdef.objectOrder != null);
        assert (rowdef.objectOrder instanceof kelondroBase64Order);
        for (int i = leftBorder; i < rightBound; i++) {
            if (comparePivot(compiledPivot, i) == 0) return i;
        }
        return -1;
    }
    
    private int binarySearch(byte[] key, int astart, int alength) {
        // returns the exact position of the key if the key exists,
        // or -1 if the key does not exist
        assert (rowdef.objectOrder != null);
        int l = 0;
        int rbound = this.sortBound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = l + ((rbound - l) >> 1);
            d = compare(key, astart, alength, p);
            if (d == 0) return p;
            if (d < 0) rbound = p; else l = p + 1;
        }
        return -1;
    }
    
    private int binarySearchCompiledPivot(byte[] compiledPivot) {
        // returns the exact position of the key if the key exists,
        // or -1 if the key does not exist
        assert (rowdef.objectOrder != null);
        assert (rowdef.objectOrder instanceof kelondroBase64Order);
        int l = 0;
        int rbound = this.sortBound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = l + ((rbound - l) >> 1);
            d = comparePivot(compiledPivot, p);
            if (d == 0) return p;
            if (d < 0) rbound = p; else l = p + 1;
        }
        return -1;
    }

    public int binaryPosition(byte[] key, int astart, int alength) {
        // returns the exact position of the key if the key exists,
        // or a position of an entry that is greater than the key if the
        // key does not exist
        assert (rowdef.objectOrder != null);
        int l = 0;
        int rbound = this.sortBound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = l + ((rbound - l) >> 1);
            d = compare(key, astart, alength, p);
            if (d == 0) return p;
            if (d < 0) rbound = p; else l = p + 1;
        }
        return l;
    }
    
    public kelondroProfile profile() {
        return profile;
    }
    
    public synchronized Iterator rows() {
        // iterates kelondroRow.Entry - type entries
        sort();
        return super.rows();
    }
    
    public synchronized kelondroCloneableIterator rows(boolean up, byte[] firstKey) {
        return new rowIterator(up, firstKey);
    }
    
    public class rowIterator implements kelondroCloneableIterator {

        private boolean up;
        private byte[] first;
        private int p, bound;
        
        public rowIterator(boolean up, byte[] firstKey) {
            // see that all elements are sorted
            sort();
            this.up = up;
            this.first = firstKey;
            this.bound = sortBound;
            if (first == null) {
                p = 0;
            } else {
                p = binaryPosition(first, 0, first.length); // check this to find bug in DHT selection enumeration
                //System.out.println("binaryposition for key " + new String(firstKey) + " is " + p);
            }
        }
        
        public Object clone(Object second) {
            return new rowIterator(up, (byte[]) second);
        }
        
        public boolean hasNext() {
        	if (p < 0) return false;
        	if (p >= size()) return false;
            if (up) {
                return p < bound;
            } else {
                return p >= 0;
            }
        }

        public Object next() {
            kelondroRow.Entry entry = get(p);
            if (up) p++; else p--;
            return entry;
        }
        
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    
    public static void main(String[] args) {
    	// sort/uniq-test
        /*
    	kelondroRow rowdef = new kelondroRow("Cardinal key-4 {b256}, byte[] payload-1", kelondroNaturalOrder.naturalOrder, 0);
    	kelondroRowSet rs = new kelondroRowSet(rowdef, 0);
        Random random = new Random(0);
        kelondroRow.Entry entry;
        for (int i = 0; i < 10000000; i++) {
        	entry = rowdef.newEntry();
        	entry.setCol(0, Math.abs(random.nextLong() % 1000000));
        	entry.setCol(1, "a".getBytes());
        	rs.addUnique(entry);
        }
        System.out.println("before sort, size = " + rs.size());
        rs.sort();
        System.out.println("after sort, before uniq, size = " + rs.size());
        rs.uniq(10000);
        System.out.println("after uniq, size = " + rs.size());
        */
        
        String[] test = { "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn" };
        kelondroRowSet d = new kelondroRowSet(new kelondroRow("byte[] key-10, Cardinal x-4 {b256}", kelondroNaturalOrder.naturalOrder, 0), 0);
        d.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
        for (int ii = 0; ii < test.length; ii++) d.add(test[ii].getBytes());
        for (int ii = 0; ii < test.length; ii++) d.add(test[ii].getBytes());
        d.sort();
        d.remove("fuenf".getBytes(), 0, 5, false);
        Iterator ii = d.rows();
        String s;
        System.out.print("INPUT-ITERATOR: ");
        kelondroRow.Entry entry;
        while (ii.hasNext()) {
            entry = (kelondroRow.Entry) ii.next();
            s = new String((byte[]) entry.getColBytes(0)).trim();
            System.out.print(s + ", ");
            if (s.equals("drei")) ii.remove();
        }
        System.out.println("");
        System.out.println("INPUT-TOSTRING: " + d.toString());
        d.sort();
        System.out.println("SORTED        : " + d.toString());
        d.uniq();
        System.out.println("UNIQ          : " + d.toString());
        d.trim(false);
        System.out.println("TRIM          : " + d.toString());
        
        
        /*
        // second test
        c = new kelondroRowSet(new kelondroRow(new int[]{10, 3}));
        c.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
        Random rand = new Random(0);
        long start = System.currentTimeMillis();
        long t, d = 0;
        String w;
        for (long k = 0; k < 60000; k++) {
            t = System.currentTimeMillis();
            w = "a" + Long.toString(rand.nextLong());
            c.add(w.getBytes());
            if (k % 10000 == 0)
                System.out.println("added " + k + " entries in " +
                    ((t - start) / 1000) + " seconds, " +
                    (((t - start) > 1000) ? (k / ((t - start) / 1000)) : k) +
                    " entries/second, size = " + c.size());
        }
        System.out.println("bevore sort: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        c.shape();
        System.out.println("after sort: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        c.uniq();
        System.out.println("after uniq: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        System.out.println("RESULT SIZE: " + c.size());
        System.out.println();
        
        // third test
        c = new kelondroRowSet(new kelondroRow(new int[]{10, 3}), 60000);
        c.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
        rand = new Random(0);
        start = System.currentTimeMillis();
        d = 0;
        for (long k = 0; k < 60000; k++) {
            t = System.currentTimeMillis();
            w = "a" + Long.toString(rand.nextLong());
            if (c.get(w.getBytes(), 0, 10) == null) c.add(w.getBytes()); else d++;
            if (k % 10000 == 0)
                System.out.println("added " + k + " entries in " +
                    ((t - start) / 1000) + " seconds, " +
                    (((t - start) > 1000) ? (k / ((t - start) / 1000)) : k) +
                    " entries/second, " + d + " double, size = " + c.size() + 
                    ", sum = " + (c.size() + d));
        }
        System.out.println("RESULT SIZE: " + c.size());
        */
        /*
        // performance test for put
        long start = System.currentTimeMillis();
        kelondroRowSet c = new kelondroRowSet(new kelondroRow("byte[] a-12, byte[] b-12"), 0);
        Random random = new Random(0);
        byte[] key;
        for (int i = 0; i < 100000; i++) {
            key = randomHash(random);
            c.put(c.rowdef.newEntry(new byte[][]{key, key}));
            if (i % 1000 == 0) System.out.println(i + " entries. ");
        }
        System.out.println("RESULT SIZE: " + c.size());
        System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        */
        
        // remove test
        long start = System.currentTimeMillis();
        kelondroRowSet c = new kelondroRowSet(new kelondroRow("byte[] a-12, byte[] b-12", kelondroBase64Order.enhancedCoder, 0), 0);
        byte[] key;
        int testsize = 5000;
        byte[][] delkeys = new byte[testsize / 5][];
        Random random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            if (i % 5 != 0) continue;
            delkeys[i / 5] = key;
        }
        random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            c.put(c.rowdef.newEntry(new byte[][]{key, key}));
            if (i % 1000 == 0) {
                for (int j = 0; j < delkeys.length; j++) c.remove(delkeys[j], true);
                c.sort();
            }
        }
        for (int j = 0; j < delkeys.length; j++) c.remove(delkeys[j], true);
        c.sort();
        random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            if (i % 5 == 0) continue;
            if (c.get(key) == null) System.out.println("missing entry " + new String(key));
        }
        c.sort();
        System.out.println("RESULT SIZE: " + c.size());
        System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
    }
    
    public static byte[] randomHash(final long r0, final long r1) {
        // a long can have 64 bit, but a 12-byte hash can have 6 * 12 = 72 bits
        // so we construct a generic Hash using two long values
        return (kelondroBase64Order.enhancedCoder.encodeLong(Math.abs(r0), 11).substring(5) +
                kelondroBase64Order.enhancedCoder.encodeLong(Math.abs(r1), 11).substring(5)).getBytes();
    }
    public static byte[] randomHash(Random r) {
        return randomHash(r.nextLong(), r.nextLong());
    }

    public String filename() {
        return null;
    }

}
