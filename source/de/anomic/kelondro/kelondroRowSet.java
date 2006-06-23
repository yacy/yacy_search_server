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

import java.util.TreeSet;
import java.util.Iterator;
import java.util.Random;

public class kelondroRowSet extends kelondroRowCollection {

    private static final int collectionReSortLimit = 90;
    private static final int removeMaxSize = 100;

    private kelondroProfile profile;
    private TreeSet removeMarker;
    
    public kelondroRowSet(kelondroRow rowdef) {
        super(rowdef);
        this.removeMarker = new TreeSet();
        this.profile = new kelondroProfile();
    }

    public kelondroRowSet(kelondroRow rowdef, int objectCount) {
        super(rowdef, objectCount);
        this.removeMarker = new TreeSet();
        this.profile = new kelondroProfile();
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
    
    public kelondroRow.Entry put(kelondroRow.Entry entry) {
        long handle = profile.startWrite();
        int index = -1;
        kelondroRow.Entry oldentry = null;
        synchronized (chunkcache) {
            index = find(entry.bytes(), super.rowdef.colstart[super.sortColumn], super.rowdef.width(super.sortColumn));
            if (isMarkedRemoved(index)) {
                set(index, entry);
                removeMarker.remove(new Integer(index));
            } else if (index < 0) {
                add(entry);
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

    public kelondroRow.Entry removeMarked(byte[] a) {
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
            }
            
            // there is an entry
            entry = get(p);
            if (p < sortBound) {
                removeMarker.add(new Integer(p));
            } else {
                super.swap(p, --chunkcount, 0);
            }
        
            // check case when complete chunkcache is marked as deleted
            if (removeMarker.size() == chunkcount) {
                this.clear();
                removeMarker.clear();
            }
        }
        
        // check if removeMarker is full
        if (removeMarker.size() >= removeMaxSize) resolveMarkedRemoved();

        profile.stopDelete(handle);
        return entry;
    }
    
    private boolean isMarkedRemoved(int index) {
        return removeMarker.contains(new Integer(index));
    }
    
    public void shape() {
        //System.out.println("SHAPE");
        synchronized (chunkcache) {
            resolveMarkedRemoved();
            super.sort();
        }
    }
    
    /*
    private void resolveMarkedRemoved1() {
        //long start = System.currentTimeMillis();
        //int c = removeMarker.size();
        Integer idx = new Integer(sortBound);
        while (removeMarker.size() > 0) {
            idx = (Integer) removeMarker.last();
            removeMarker.remove(idx);
            chunkcount--;
            if (idx.intValue() < chunkcount) {
                super.swap(idx.intValue(), chunkcount, 0);
            }
        }
        if (idx.intValue() < sortBound) sortBound = idx.intValue();
        removeMarker.clear();
        //System.out.println("RESOLVED " + c + " entries in " + (System.currentTimeMillis() - start) + " milliseconds");
    }
   */
    
    private void resolveMarkedRemoved() {
        if (removeMarker.size() == 0) return;
        Integer nxt = (Integer) removeMarker.first();
        removeMarker.remove(nxt);
        int idx = nxt.intValue();
        int d = 1;
        while (removeMarker.size() > 0) {
            nxt = (Integer) removeMarker.first();
            removeMarker.remove(nxt);
            super.removeShift(idx, d, nxt.intValue());
            idx = nxt.intValue() - d;
            d++;
        }
        super.removeShift(idx, d, chunkcount);
        chunkcount -= d;
        removeMarker.clear();
    }

    
    protected kelondroRow.Entry removeShift(byte[] a) {
        return removeShift(a, 0, a.length);
    }
    
    private kelondroRow.Entry removeShift(byte[] a, int astart, int alength) {
        // the byte[] a may be shorter than the chunksize
        if (chunkcount == 0) return null;
        long handle = profile.startDelete();
        kelondroRow.Entry entry = null;
        synchronized(chunkcache) {
            int p = find(a, astart, alength);
            if (p < 0) return null;
            entry = get(p);
            if (p < sortBound) {
                removeShift(p);
            } else {
                super.swap(p, --chunkcount, 0);
            }
        }
        profile.stopDelete(handle);
        return entry;
    }
    
    public void removeMarkedAll(kelondroRowCollection c) {
        long handle = profile.startDelete();
        Iterator i = c.elements();
        byte[] b;
        while (i.hasNext()) {
            b = (byte[]) i.next();
            removeMarked(b, 0, b.length);
        }
        profile.stopDelete(handle);
    }
    
    public kelondroOrder getOrdering() {
        return this.sortOrder;
    }

    public void setOrdering(kelondroOrder newOrder, int newColumn) {
        if ((this.sortOrder == null) ||
                (!(this.sortOrder.signature().equals(newOrder.signature()))) ||
                (newColumn != this.sortColumn)) {
            this.sortOrder = newOrder;
            this.sortBound = 0;
            this.sortColumn = newColumn;
        }
    }
    

    private int find(byte[] a, int astart, int alength) {
        // returns the chunknumber; -1 if not found
        
        if (this.sortOrder == null) return iterativeSearch(a, astart, alength);
        
        // check if a re-sorting make sense
        if ((this.chunkcount - this.sortBound) > collectionReSortLimit) shape();
        
        // first try to find in sorted area
        int p = binarySearch(a, astart, alength);
        if (p >= 0) return p;
        
        // then find in unsorted area
        return iterativeSearch(a, astart, alength);
        
    }
    
    private int iterativeSearch(byte[] key, int astart, int alength) {
        // returns the chunknumber
        
        if (this.sortOrder == null) {
            for (int i = this.sortBound; i < this.chunkcount; i++) {
                if (match(key, astart, alength, i)) return i;
            }
            return -1;
        } else {
            for (int i = this.sortBound; i < this.chunkcount; i++) {
                if (compare(key, astart, alength, i) == 0) return i;
            }
            return -1;
        }
    }
    
    private int binarySearch(byte[] key, int astart, int alength) {
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

    private int compare(byte[] a, int astart, int alength, int chunknumber) {
        assert (chunknumber < chunkcount);
        int l = Math.min(this.rowdef.width(0), Math.min(a.length - astart, alength));
        return this.sortOrder.compare(a, astart, l, chunkcache, chunknumber * this.rowdef.objectsize(), l);
    }
    
    private boolean match(byte[] a, int astart, int alength, int chunknumber) {
        if (chunknumber >= chunkcount) return false;
        int i = 0;
        int p = chunknumber * this.rowdef.objectsize();
        final int len = Math.min(this.rowdef.width(0), Math.min(alength, a.length - astart));
        while (i < len) if (a[astart + i++] != chunkcache[p++]) return false;
        return true;
    }
    
    public kelondroProfile profile() {
        return profile;
    }
    
    public static void main(String[] args) {
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
    }
}
