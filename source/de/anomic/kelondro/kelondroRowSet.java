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

import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;

public class kelondroRowSet extends kelondroRowCollection implements kelondroIndex {

    private static final int collectionReSortLimit = 90;
    private static final int removeMaxSize = 100;

    private kelondroProfile profile;
    private TreeMap removeMarker;

    public kelondroRowSet(kelondroRow rowdef, int objectCount, byte[] cache, kelondroOrder sortOrder, int sortColumn, int sortBound) {
        super(rowdef, objectCount, cache, sortOrder, sortColumn, sortBound);
        this.removeMarker = new TreeMap();
        this.profile = new kelondroProfile();
    }
        
    public kelondroRowSet(kelondroRowSet rs) {
        super(rs);
        this.profile = rs.profile;
        this.removeMarker = rs.removeMarker;
    }

    public kelondroRowSet(kelondroRow rowdef, int objectCount) {
        super(rowdef, objectCount);
        this.removeMarker = new TreeMap();
        this.profile = new kelondroProfile();
    }
    
    public kelondroRowSet(kelondroRow rowdef, byte[] exportedCollectionRowinstance) {
        super(rowdef, exportedCollectionRowinstance);
        this.removeMarker = new TreeMap();
        this.profile = new kelondroProfile();
    }
    
    public kelondroRowSet(kelondroRow rowdef, kelondroOrder objectOrder, int orderColumn, int objectCount) {
        this(rowdef, objectCount);
        assert (objectOrder != null);
        setOrdering(objectOrder, orderColumn);
    }
    
    public kelondroRow.Entry get(byte[] key) {
        return get(key, 0, key.length);
    }
    
    private kelondroRow.Entry get(byte[] key, int astart, int alength) {
        long handle = profile.startRead();
        kelondroRow.Entry entry = null;
        synchronized (chunkcache) {
            int index = find(key, astart, alength);
            if ((index >= 0) && (!(isMarkedRemoved(index)))) entry = get(index);
        }
        profile.stopRead(handle);
        return entry;
    }
    
    public void addUnique(kelondroRow.Entry row) {
        if (removeMarker.size() == 0) {
            super.addUnique(row);
        } else {
            this.put(row);
        }
    }
    
    public void addUnique(kelondroRow.Entry row, Date entryDate) {
        if (removeMarker.size() == 0) {
            super.addUnique(row, entryDate);
        } else {
            this.put(row, entryDate);
        }
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) {
        return put(row);
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry entry) {
        assert (entry != null);
        assert (entry.getColBytes(super.sortColumn) != null);
        //assert (!(serverLog.allZero(entry.getColBytes(super.sortColumn))));
        long handle = profile.startWrite();
        int index = -1;
        kelondroRow.Entry oldentry = null;
        synchronized (chunkcache) {
            index = find(entry.bytes(), super.rowdef.colstart[super.sortColumn], super.rowdef.width(super.sortColumn));
            if (isMarkedRemoved(index)) {
                set(index, entry);
                removeMarker.remove(new Integer(index));
            } else if (index < 0) {
                super.addUnique(entry);
            } else {
                oldentry = get(index);
                set(index, entry);
            }
        }
        profile.stopWrite(handle);
        return oldentry;
    }
    
    public int size() {
        return super.size() - removeMarker.size();
    }

    public kelondroRow.Entry remove(byte[] a) {
        return removeMarked(a, 0, a.length);
    }

    private kelondroRow.Entry removeMarked(byte[] a, int astart, int alength) {
        if (chunkcount == 0) return null;
        long handle = profile.startDelete();

        // check if it is contained in chunkcache
        kelondroRow.Entry entry = null;
        synchronized(chunkcache) {
            int p = find(a, astart, alength);
            if (p < 0) {
                // the entry is not there
                profile.stopDelete(handle);
                return null;
            } else {
                // there is an entry
                entry = get(p);
                if (p < sortBound) {
                    // mark entry as to-be-deleted
                    removeMarker.put(new Integer(p), entry.getColBytes(super.sortColumn));
                    if (removeMarker.size() > removeMaxSize) resolveMarkedRemoved();
                } else {
                    // remove directly by swap
                    super.copytop(p);
                    chunkcount--;
                }

                profile.stopDelete(handle);
                return entry;
            }
        }
    }
    
    private boolean isMarkedRemoved(int index) {
        return removeMarker.containsKey(new Integer(index));
    }
    
    public void shape() {
        assert (this.sortOrder != null); // we cannot shape without an object order
        synchronized (chunkcache) {
            resolveMarkedRemoved();
            super.sort();
            //if (super.rowdef.column(0).cellwidth() == 4) System.out.println("TABLE OF " + super.rowdef.toString() + "\n" + serverLog.table(super.chunkcache, super.rowdef.objectsize, 0)); // DEBUG
        }
    }
    
    private void resolveMarkedRemoved() {
        if (removeMarker.size() == 0) return;
        
        // check case when complete chunkcache is marked as deleted
        if (removeMarker.size() == chunkcount) {
            this.clear();
            removeMarker.clear();
            return;
        }
        
        Integer nxt = (Integer) removeMarker.firstKey();
        removeMarker.remove(nxt);
        int idx = nxt.intValue();
        assert (idx < sortBound) : "idx = " + idx + ", sortBound = " + sortBound;
        int d = 1;
        byte[] a;
        while (removeMarker.size() > 0) {
            nxt = (Integer) removeMarker.firstKey();
            a = (byte[]) removeMarker.remove(nxt);
            assert kelondroNaturalOrder.compares(a, 0, a.length, get(nxt.intValue()).getColBytes(this.sortColumn), 0, a.length) == 0;
            assert (nxt.intValue() < sortBound);
            super.removeShift(idx, d, nxt.intValue());
            idx = nxt.intValue() - d;
            d++;
        }
        super.removeShift(idx, d, chunkcount);
        chunkcount -= d;
        sortBound -= d; // there are all markers below the sortBound
        removeMarker.clear();
    }
    
    public void removeMarkedAll(kelondroRowCollection c) {
        long handle = profile.startDelete();
        Iterator i = c.rows();
        kelondroRow.Entry entry;
        while (i.hasNext()) {
            entry = (kelondroRow.Entry) i.next();
            removeMarked(entry.bytes(), 0, entry.bytes().length);
        }
        profile.stopDelete(handle);
    }
    
    public void setOrdering(kelondroOrder newOrder, int newColumn) {
        if ((this.sortOrder == null) ||
                (!(this.sortOrder.signature().equals(newOrder.signature()))) ||
                (newColumn != this.sortColumn)) {
            resolveMarkedRemoved();
            this.sortOrder = newOrder;
            this.sortBound = 0;
            this.sortColumn = newColumn;
        }
    }
    
    public kelondroOrder order() {
        return this.sortOrder;
    }

    public int primarykey() {
        return this.sortColumn;
    }


    private int find(byte[] a, int astart, int alength) {
        // returns the chunknumber; -1 if not found
        
        if (this.sortOrder == null) return iterativeSearch(a, astart, alength, 0, this.chunkcount);
        
        // check if a re-sorting make sense
        if ((this.chunkcount - this.sortBound) > collectionReSortLimit) shape();
        
        // first try to find in sorted area
        int p = binarySearch(a, astart, alength);
        if (p >= 0) return p;
        
        // then find in unsorted area
        return iterativeSearch(a, astart, alength, this.sortBound, this.chunkcount);
        
    }
    
    private int iterativeSearch(byte[] key, int astart, int alength, int leftBorder, int rightBound) {
        // returns the chunknumber
        
        if (this.sortOrder == null) {
            for (int i = leftBorder; i < rightBound; i++) {
                if (match(key, astart, alength, i)) return i;
            }
            return -1;
        } else {
            for (int i = leftBorder; i < rightBound; i++) {
                if (compare(key, astart, alength, i) == 0) return i;
            }
            return -1;
        }
    }
    
    private int binarySearch(byte[] key, int astart, int alength) {
        // returns the exact position of the key if the key exists,
        // or -1 if the key does not exist
        assert (this.sortOrder != null);
        int l = 0;
        int rbound = this.sortBound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = l + ((rbound - l) >> 1);
            d = compare(key, astart, alength, p);
            if (d == 0) return p;
            else if (d < 0) rbound = p;
            else l = p + 1;
        }
        return -1;
    }

    public int binaryPosition(byte[] key, int astart, int alength) {
        // returns the exact position of the key if the key exists,
        // or a position of an entry that is greater than the key if the
        // key does not exist
        assert (this.sortOrder != null);
        int l = 0;
        int rbound = this.sortBound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = l + ((rbound - l) >> 1);
            d = compare(key, astart, alength, p);
            if (d == 0) return p;
            else if (d < 0) rbound = p;
            else l = p + 1;
        }
        return l;
    }

    private int compare(byte[] a, int astart, int alength, int chunknumber) {
        assert (chunknumber < chunkcount);
        int l = Math.min(this.rowdef.width(this.sortColumn), Math.min(a.length - astart, alength));
        return this.sortOrder.compare(a, astart, l, chunkcache, chunknumber * this.rowdef.objectsize() + this.rowdef.colstart[this.sortColumn], this.rowdef.width(this.sortColumn));
    }
    
    private boolean match(byte[] a, int astart, int alength, int chunknumber) {
        if (chunknumber >= chunkcount) return false;
        int i = 0;
        int p = chunknumber * this.rowdef.objectsize() + this.rowdef.colstart[this.sortColumn];
        final int len = Math.min(this.rowdef.width(this.sortColumn), Math.min(alength, a.length - astart));
        while (i < len) if (a[astart + i++] != chunkcache[p++]) return false;
        return ((len == this.rowdef.width(this.sortColumn)) || (chunkcache[len] == 0)) ;
    }
    
    public kelondroProfile profile() {
        return profile;
    }
    
    public Iterator rows() {
        shape();
        return super.rows();
    }
    
    public Iterator rows(boolean up, boolean rotating, byte[] firstKey) {
        return new rowIterator(up, rotating, firstKey);
    }
    
    public class rowIterator implements Iterator {

        private boolean up, rot;
        private byte[] first;
        private int p, bound;
        
        public rowIterator(boolean up, boolean rotating, byte[] firstKey) {
            // see that all elements are sorted
            shape();
            this.up = up;
            this.rot = rotating;
            this.first = firstKey;
            this.bound = sortBound;
            if (first == null) {
                p = 0;
            } else {
                p = binaryPosition(first, 0, first.length);
            }
        }
        
        public boolean hasNext() {
            if (rot) return true;
            if (up) {
                return p < bound;
            } else {
                return p >= 0;
            }
        }

        public Object next() {
            kelondroRow.Entry entry = get(p);
            if (up) p++; else p--;
            if (rot) {
                if (p == bound) p = 0;
                if (p < 0) p = bound - 1;
            }
            return entry;
        }
        
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    
    public void close() {
        // just for compatibility with kelondroIndex interface; do nothing
    }
    
    public final int cacheObjectChunkSize() {
        // dummy method
        return -1;
    }
    
    public long[] cacheObjectStatus() {
        // dummy method
        return null;
    }
    
    public final int cacheNodeChunkSize() {
        // returns the size that the node cache uses for a single entry
        return -1;
    }
    
    public final int[] cacheNodeStatus() {
        // a collection of different node cache status values
        return new int[]{0,0,0,0,0,0,0,0,0,0};
    }
    
    public static kelondroIndex getRAMIndex(kelondroRow rowdef, int initSize) {
        return new kelondroRowSet(rowdef, kelondroNaturalOrder.naturalOrder, 0, initSize);
    }
    
    public static void main(String[] args) {
        /*
        String[] test = { "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn" };
        kelondroRowSet c = new kelondroRowSet(new kelondroRow(new int[]{10, 3}));
        c.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes());
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes());
        c.shape();
        c.removeMarked("fuenf".getBytes(), 0, 5);
        Iterator i = c.elements();
        String s;
        System.out.print("INPUT-ITERATOR: ");
        while (i.hasNext()) {
            s = new String((byte[]) i.next()).trim();
            System.out.print(s + ", ");
            if (s.equals("drei")) i.remove();
        }
        System.out.println("");
        System.out.println("INPUT-TOSTRING: " + c.toString());
        c.shape();
        System.out.println("SORTED        : " + c.toString());
        c.uniq();
        System.out.println("UNIQ          : " + c.toString());
        c.trim();
        System.out.println("TRIM          : " + c.toString());
        */
        
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
        kelondroRowSet c = new kelondroRowSet(new kelondroRow("byte[] a-12, byte[] b-12"), 0);
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
                for (int j = 0; j < delkeys.length; j++) c.remove(delkeys[j]);
                c.shape();
            }
        }
        for (int j = 0; j < delkeys.length; j++) c.remove(delkeys[j]);
        c.shape();
        random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            if (i % 5 == 0) continue;
            if (c.get(key) == null) System.out.println("missing entry " + new String(key));
        }
        c.shape();
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
}
