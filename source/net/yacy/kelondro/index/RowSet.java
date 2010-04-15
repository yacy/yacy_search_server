// RowSet.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.06.2006 on http://www.anomic.de
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.index;

import java.util.Iterator;
import java.util.Random;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.NaturalOrder;


public class RowSet extends RowCollection implements ObjectIndex, Iterable<Row.Entry> {

    private static final int collectionReSortLimit = 300;

    public RowSet(final RowSet rs) {
        super(rs);
    }

    public RowSet(final Row rowdef, final int objectCount, final byte[] cache, final int sortBound) {
        super(rowdef, objectCount, cache, sortBound);
        assert rowdef.objectOrder != null;
    }
        
    public RowSet(final Row rowdef, final int objectCount) throws RowSpaceExceededException {
        super(rowdef, objectCount);
        assert rowdef.objectOrder != null;
    }
    
    public RowSet(final Row rowdef) {
        super(rowdef);
        assert rowdef.objectOrder != null;
    }
    
    /**
     * import an exported collection
     * @param rowdef
     * @param exportedCollectionRowEnvironment
     * @param columnInEnvironment
     */
    public RowSet(final Row rowdef, final Row.Entry exportedCollectionRowEnvironment) {
        super(rowdef, exportedCollectionRowEnvironment);
        assert rowdef.objectOrder != null;
    }
    
    public final static RowSet importRowSet(final byte[] b, final Row rowdef) {
    	assert b.length >= exportOverheadSize : "b.length = " + b.length;
    	if (b.length < exportOverheadSize) return new RowSet(rowdef);
        final int size = (int) NaturalOrder.decodeLong(b, 0, 4);
        assert size >= 0 : "size = " + size;
        if (size < 0) return new RowSet(rowdef);
        final int orderbound = (int) NaturalOrder.decodeLong(b, 10, 4);
        assert orderbound >= 0 : "orderbound = " + orderbound;
        if (orderbound < 0) return new RowSet(rowdef);
        final byte[] chunkcache = new byte[size * rowdef.objectsize];
        assert b.length - exportOverheadSize == size * rowdef.objectsize;
        if (b.length - exportOverheadSize != size * rowdef.objectsize) return new RowSet(rowdef);
        System.arraycopy(b, exportOverheadSize, chunkcache, 0, chunkcache.length);
        return new RowSet(rowdef, size, chunkcache, orderbound);
    }
    
    private RowSet(Row rowdef, byte[] chunkcache, int chunkcount, int sortBound, long lastTimeWrote) {
        super(rowdef, chunkcache, chunkcount, sortBound, lastTimeWrote);
    }
    
    public RowSet clone() {
        return new RowSet(super.rowdef, super.chunkcache, super.chunkcount, super.sortBound, super.lastTimeWrote);
    }

	public void reset() {
		super.reset();
	}
   
    public final synchronized boolean has(final byte[] key) {
        final int index = find(key, 0, key.length);
        return index >= 0;
    }
    
    public final synchronized Row.Entry get(final byte[] key) {
        final int index = find(key, 0, key.length);
        if (index < 0) return null;
        return get(index, true);
    }
    
    public final synchronized void put(final Row.Entry entry) throws RowSpaceExceededException {
        assert (entry != null);
        assert (entry.getPrimaryKeyBytes() != null);
        // when reaching a specific amount of un-sorted entries, re-sort all
        if ((this.chunkcount - this.sortBound) > collectionReSortLimit) {
            sort();
        }
        final int index = find(entry.bytes(), 0, super.rowdef.primaryKeyLength);
        if (index < 0) {
            super.addUnique(entry);
        } else {
            final int sb = this.sortBound; // save the sortBound, because it is not altered (we replace at the same place)
            set(index, entry);       // this may alter the sortBound, which we will revert in the next step
            this.sortBound = sb;     // revert a sortBound altering
        }
    }

    public final synchronized Row.Entry replace(final Row.Entry entry) throws RowSpaceExceededException {
        assert (entry != null);
        assert (entry.getPrimaryKeyBytes() != null);
        int index = -1;
        Row.Entry oldentry = null;
        // when reaching a specific amount of un-sorted entries, re-sort all
        if ((this.chunkcount - this.sortBound) > collectionReSortLimit) {
            sort();
        }
        index = find(entry.bytes(), 0, super.rowdef.primaryKeyLength);
        if (index < 0) {
            super.addUnique(entry);
        } else {
            oldentry = get(index, true);
            final int sb = this.sortBound; // save the sortBound, because it is not altered (we replace at the same place)
            set(index, entry);       // this may alter the sortBound, which we will revert in the next step
            this.sortBound = sb;     // revert a sortBound altering
        }
        return oldentry;
    }

    public final synchronized long inc(final byte[] key, final int col, final long add, final Row.Entry initrow) throws RowSpaceExceededException {
        final int index = find(key, 0, key.length);
        if (index >= 0) {
            // the entry existed before
            final Row.Entry entry = get(index, false); // no clone necessary
            final long l = entry.incCol(col, add);
            set(index, entry);
            return l;
        } else if (initrow != null) {
            // create new entry
            super.addUnique(initrow);
            return initrow.getColLong(col);
        } else {
            // if initrow == null just do nothing
            // but return a Long.MIN_VALUE
            return Long.MIN_VALUE;
        }
    }

    private final Row.Entry remove(final byte[] a, final int start, final int length) {
        final int index = find(a, start, length);
        if (index < 0) {
            return null;
        }
        final Row.Entry entry = super.get(index, true);
        super.removeRow(index, true); // keep order of collection!
        //int findagainindex = 0;
        //assert (findagainindex = find(a, start, length)) < 0 : "remove: chunk found again at index position (after  remove) " + findagainindex + ", index(before) = " + index + ", inset=" + NaturalOrder.arrayList(super.chunkcache, super.rowdef.objectsize * findagainindex, length) + ", searchkey=" + NaturalOrder.arrayList(a, start, length); // check if the remove worked
        return entry;
    }

    /**
     * remove a byte[] from the set.
     * if the entry was found, return the entry, but delete the entry from the set
     * if the entry was not found, return null.
     */
    public final synchronized Row.Entry remove(final byte[] a) {
        Row.Entry entry = null;
        Row.Entry tmp;
        do {
            tmp = remove(a, 0, a.length);
            if (tmp != null) {
                entry = tmp;
            }
        } while (tmp != null);
        return entry;
    }

    private final int find(final byte[] a, final int astart, final int alength) {
        // returns the chunknumber; -1 if not found
        
        if (rowdef.objectOrder == null) return iterativeSearch(a, astart, alength, 0, this.chunkcount);
        
        if ((this.chunkcount - this.sortBound) > collectionReSortLimit) {
            sort();
        }
        
        if ((this.rowdef.objectOrder != null) && (this.rowdef.objectOrder instanceof Base64Order) && (this.sortBound > 4000)) {
            // first try to find in sorted area
            assert this.rowdef.objectOrder.wellformed(a, astart, alength) : "not wellformed: " + new String(a, astart, alength);
            final int p = binarySearch(a, astart, alength);
            if (p >= 0) return p;
            
            // then find in unsorted area
            return iterativeSearch(a, astart, alength, this.sortBound, this.chunkcount);
        } else {
            // first try to find in sorted area
            final int p = binarySearch(a, astart, alength);
            if (p >= 0) return p;
        
            // then find in unsorted area
            return iterativeSearch(a, astart, alength, this.sortBound, this.chunkcount);
        }        
    }
    
    private final int iterativeSearch(final byte[] key, final int astart, final int alength, final int leftBorder, final int rightBound) {
        // returns the chunknumber        
        for (int i = leftBorder; i < rightBound; i++) {
            if (match(key, astart, alength, i)) return i;
        }
        return -1;
    }
    
    private final int binarySearch(final byte[] key, final int astart, final int alength) {
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

    protected final int binaryPosition(final byte[] key, final int astart, final int alength) {
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
    
    public final synchronized Iterator<byte[]> keys() {
        sort();
        return super.keys(true);
    }
    
    public final synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        return new keyIterator(up, firstKey);
    }
    
    public final class keyIterator implements CloneableIterator<byte[]> {

        private final boolean up;
        private final byte[] first;
        private int p;
        final int bound;
        
        public keyIterator(final boolean up, final byte[] firstKey) {
            // see that all elements are sorted
            sort();
            this.up = up;
            this.first = firstKey;
            this.bound = sortBound;
            if (first == null) {
                p = 0;
            } else {
                p = binaryPosition(first, 0, first.length); // check this to find bug in DHT selection enumeration
            }
        }
        
		public final keyIterator clone(final Object second) {
            return new keyIterator(up, (byte[]) second);
        }
        
        public final boolean hasNext() {
        	if (p < 0) return false;
        	if (p >= size()) return false;
            if (up) {
                return p < bound;
            } else {
                return p >= 0;
            }
        }

        public final byte[] next() {
            final byte[] key = getKey(p);
            if (up) p++; else p--;
            return key;
        }
        
        public final void remove() {
            throw new UnsupportedOperationException();
        }
    }
    
    public final synchronized Iterator<Row.Entry> iterator() {
        // iterates kelondroRow.Entry - type entries
        sort();
        return super.iterator();
    }
    
    public final synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) {
        return new rowIterator(up, firstKey);
    }
    
    public final synchronized CloneableIterator<Row.Entry> rows() {
        return new rowIterator(true, null);
    }
    
    public final class rowIterator implements CloneableIterator<Row.Entry> {

        private final boolean up;
        private final byte[] first;
        private int p;
        final int bound;
        
        public rowIterator(final boolean up, final byte[] firstKey) {
            // see that all elements are sorted
            sort();
            this.up = up;
            this.first = firstKey;
            this.bound = sortBound;
            if (first == null) {
                p = 0;
            } else {
                p = binaryPosition(first, 0, first.length); // check this to find bug in DHT selection enumeration
            }
        }
        
		public final rowIterator clone(final Object second) {
            return new rowIterator(up, (byte[]) second);
        }
        
        public final boolean hasNext() {
        	if (p < 0) return false;
        	if (p >= size()) return false;
            if (up) {
                return p < bound;
            } else {
                return p >= 0;
            }
        }

        public final Row.Entry next() {
            final Row.Entry entry = get(p, true);
            if (up) p++; else p--;
            return entry;
        }
        
        public final void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * merge this row collection with another row collection.
     * The resulting collection is sorted and does not contain any doubles, which are also removed during the merge.
     * The new collection may be a copy of one of the old one, or can be an alteration of one of the input collections
     * After this merge, none of the input collections should be used, because they can be altered 
     * @param c
     * @return
     * @throws RowSpaceExceededException 
     */
    public final RowSet merge(final RowSet c) throws RowSpaceExceededException {
        assert c != null;
        /*
        if (this.isSorted() && this.size() >= c.size()) {
            return mergeInsert(this, c);
        }*/
        return mergeEnum(this, c);
    }
    /*
    private static kelondroRowSet mergeInsert(kelondroRowSet sorted, kelondroRowCollection small) {
        assert sorted.rowdef == small.rowdef;
        assert sorted.isSorted();
        assert small.size() <= sorted.size();
        sorted.ensureSize(sorted.size() + small.size());
        for (int i = 0; i < small.size(); i++) {
            
        }
        
        return sorted;
    }
*/
    
    /**
     * merge this row collection with another row collection using an simultanous iteration of the input collections
     * the current collection is not altered in any way, the returned collection is a new collection with copied content.
     * @param c
     * @return
     * @throws RowSpaceExceededException 
     */
    protected final static RowSet mergeEnum(final RowCollection c0, final RowCollection c1) throws RowSpaceExceededException {
        assert c0.rowdef == c1.rowdef : c0.rowdef.toString() + " != " + c1.rowdef.toString();
        final RowSet r = new RowSet(c0.rowdef, c0.size() + c1.size());
        try {
        	c0.sort();
        } catch (Exception e) {
        	Log.logSevere("RowSet", "collection corrupted. cleaned. " + e.getMessage(), e);
        	c0.clear();
        }
        try {
        	c1.sort();
        } catch (Exception e) {
        	Log.logSevere("RowSet", "collection corrupted. cleaned. " + e.getMessage(), e);
        	c1.clear();
        }
        int c0i = 0, c1i = 0;
        int c0p, c1p;
        int o;
        final int objectsize = c0.rowdef.objectsize;
        while (c0i < c0.size() && c1i < c1.size()) {
            c0p = c0i * objectsize;
            c1p = c1i * objectsize;
            o = c0.rowdef.objectOrder.compare(
                    c0.chunkcache, c0p, c0.rowdef.primaryKeyLength,
                    c1.chunkcache, c1p, c0.rowdef.primaryKeyLength);
            if (o == 0) {
                r.addSorted(c0.chunkcache, c0p, objectsize);
                c0i++;
                c1i++;
                continue;
            }
            if (o < 0) {
                r.addSorted(c0.chunkcache, c0p, objectsize);
                c0i++;
                continue;
            }
            if (o > 0) {
                r.addSorted(c1.chunkcache, c1p, objectsize);
                c1i++;
                continue;
            }
        }
        while (c0i < c0.size()) {
            r.addSorted(c0.chunkcache, c0i * objectsize, objectsize);
            c0i++;
        }
        while (c1i < c1.size()) {
            r.addSorted(c1.chunkcache, c1i * objectsize, objectsize);
            c1i++;
        }
        return r;
    }
    
    public static void main(final String[] args) {
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
        
        final String[] test = {
        		"eins......xxxx", 
        		"zwei......xxxx", 
        		"drei......xxxx", 
        		"vier......xxxx", 
        		"fuenf.....xxxx", 
        		"sechs.....xxxx", 
        		"sieben....xxxx", 
        		"acht......xxxx", 
        		"neun......xxxx", 
        		"zehn......xxxx" };
        final RowSet d = new RowSet(new Row("byte[] key-10, Cardinal x-4 {b256}", NaturalOrder.naturalOrder));
        for (int ii = 0; ii < test.length; ii++)
            try {
                d.add(test[ii].getBytes());
            } catch (RowSpaceExceededException e) {
                e.printStackTrace();
            }
        for (int ii = 0; ii < test.length; ii++)
            try {
                d.add(test[ii].getBytes());
            } catch (RowSpaceExceededException e) {
                e.printStackTrace();
            }
        d.sort();
        d.remove("fuenf".getBytes());
        final Iterator<Row.Entry> ii = d.iterator();
        String s;
        System.out.print("INPUT-ITERATOR: ");
        Row.Entry entry;
        while (ii.hasNext()) {
            entry = ii.next();
            s = new String(entry.getColBytes(0, true)).trim();
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
        final long start = System.currentTimeMillis();
        final RowSet c = new RowSet(new Row("byte[] a-12, byte[] b-12", Base64Order.enhancedCoder));
        byte[] key;
        final int testsize = 5000;
        final byte[][] delkeys = new byte[testsize / 5][];
        Random random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            if (i % 5 != 0) continue;
            delkeys[i / 5] = key;
        }
        random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            try {
                c.put(c.rowdef.newEntry(new byte[][]{key, key}));
            } catch (RowSpaceExceededException e) {
                e.printStackTrace();
            }
            if (i % 1000 == 0) {
                for (int j = 0; j < delkeys.length; j++) c.remove(delkeys[j]);
                c.sort();
            }
        }
        for (int j = 0; j < delkeys.length; j++) c.remove(delkeys[j]);
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
        return (Base64Order.enhancedCoder.encodeLong(Math.abs(r0), 11).substring(5) +
                Base64Order.enhancedCoder.encodeLong(Math.abs(r1), 11).substring(5)).getBytes();
    }
    public static byte[] randomHash(final Random r) {
        return randomHash(r.nextLong(), r.nextLong());
    }

    public String filename() {
        return null;
    }

    public void deleteOnExit() {
        // do nothing, there is no file
    }

}
