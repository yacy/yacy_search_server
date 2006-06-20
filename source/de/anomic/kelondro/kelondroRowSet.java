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

import java.util.Iterator;
import java.util.Random;

public class kelondroRowSet extends kelondroRowCollection {

    public kelondroRowSet(kelondroRow rowdef) {
        super(rowdef);
    }

    public kelondroRowSet(kelondroRow rowdef, int objectCount) {
        super(rowdef, objectCount);
    }
    
    public kelondroRow.Entry get(byte[] key) {
        return get(key, 0, key.length);
    }
    
    public kelondroRow.Entry get(byte[] key, int astart, int alength) {
        synchronized (chunkcache) {
            int i = find(key, astart, alength);
            if (i >= 0) return get(i);
        }
        return null;
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry entry) {
        int index = -1;
        synchronized (chunkcache) {
            index = find(entry.bytes(), super.rowdef.colstart[super.sortColumn], super.rowdef.width(super.sortColumn));
            if (index < 0) {
                add(entry);
                return null;
            } else {
                kelondroRow.Entry oldentry = get(index);
                set(index, entry);
                return oldentry;
            }
        }
    }
    
    public kelondroRow.Entry remove(byte[] a) {
        return remove(a, 0, a.length);
    }
    
    public kelondroRow.Entry remove(byte[] a, int astart, int alength) {
        // the byte[] a may be shorter than the chunksize
        if (chunkcount == 0) return null;
        kelondroRow.Entry b = null;
        synchronized(chunkcache) {
            int p = find(a, astart, alength);
            if (p < 0) return null;
            b = get(p);
            remove(p);
        }
        return b;
    }
    
    public void removeAll(kelondroRowCollection c) {
        Iterator i = c.elements();
        byte[] b;
        while (i.hasNext()) {
            b = (byte[]) i.next();
            remove(b, 0, b.length);
        }
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
    

    protected int find(byte[] a, int astart, int alength) {
        // returns the chunknumber; -1 if not found
        
        if (this.sortOrder == null) return iterativeSearch(a, astart, alength);
        
        // check if a re-sorting make sense
        if ((this.chunkcount - this.sortBound) > 90) sort();
        
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
    
    public static void main(String[] args) {
        String[] test = { "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn" };
        kelondroRowSet c = new kelondroRowSet(new kelondroRow(new int[]{10, 3}));
        c.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes(), 0, 10);
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes(), 0, 10);
        c.sort();
        c.remove("fuenf".getBytes(), 0, 5);
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
        c.sort();
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
            c.add(w.getBytes(), 0, 10);
            if (k % 10000 == 0)
                System.out.println("added " + k + " entries in " +
                    ((t - start) / 1000) + " seconds, " +
                    (((t - start) > 1000) ? (k / ((t - start) / 1000)) : k) +
                    " entries/second, size = " + c.size());
        }
        System.out.println("bevore sort: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        c.sort();
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
            if (c.get(w.getBytes(), 0, 10) == null) c.add(w.getBytes(), 0, 10); else d++;
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
