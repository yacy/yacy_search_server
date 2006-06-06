// kelondroCollection.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created: 12.01.2006
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

package de.anomic.kelondro;

import java.util.Iterator;

public class kelondroCollection {

    private byte[] chunkcache;
    private int chunkcount;
    private int chunksize;
    private int sortbound;
    private long lastTimeRead, lastTimeWrote;
    private kelondroOrder order;
    
    public kelondroCollection(int objectSize) {
        this(objectSize, 0, null, new byte[0]);
    }
    
    public kelondroCollection(int objectSize, int objectCount, kelondroOrder ordering, byte[] cache) {
        this.chunksize = objectSize;
        this.chunkcache = cache;
        this.chunkcount = objectCount;
        this.order = ordering;
        this.sortbound = 0;
    }
    
    private void ensureSize(int elements) {
        int needed = elements * chunksize;
        if (chunkcache.length >= needed) return;
        byte[] newChunkcache = new byte[needed * 2];
        System.arraycopy(chunkcache, 0, newChunkcache, 0, chunkcache.length);
        chunkcache = newChunkcache;
        newChunkcache = null;
    }
    
    public void trim() {
        synchronized (chunkcache) {
            int needed = chunkcount * chunksize;
            if (chunkcache.length == needed) return;
            byte[] newChunkcache = new byte[needed];
            System.arraycopy(chunkcache, 0, newChunkcache, 0, newChunkcache.length);
            chunkcache = newChunkcache;
            newChunkcache = null;
        }
    }
    
    public long lastRead() {
        return lastTimeRead;
    }
    
    public long lastWrote() {
        return lastTimeWrote;
    }
    
    public byte[] get(int index) {
        assert (index < chunkcount);
        byte[] a = new byte[chunksize];
        synchronized (chunkcache) {
            System.arraycopy(chunkcache, index * chunksize, a, 0, chunksize);
        }
        return a;
    }
    
    public byte[] get(byte[] key) {
        assert (key.length <= chunksize);
        synchronized (chunkcache) {
            int i = find(key);
            if (i >= 0) return get(i);
        }
        return null;
    }
    
    public void add(byte[] a) {
        assert (a.length <= chunksize);
        synchronized (chunkcache) {
            ensureSize(chunkcount + 1);
            System.arraycopy(a, 0, chunkcache, chunksize * chunkcount, a.length);
            chunkcount++;
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public void addAll(kelondroCollection c) {
        assert(this.chunksize >= c.chunksize);
        synchronized(chunkcache) {
            ensureSize(chunkcount + c.size());
        }
        Iterator i = c.elements();
        while (i.hasNext()) {
            add((byte[]) i.next());
        }
    }
    
    public void remove(byte[] a) {
        // the byte[] a may be shorter than the chunksize
        if (chunkcount == 0) return;
        synchronized(chunkcache) {
            int p = find(a);
            remove(p);
        }
    }
    
    public void remove(byte[] a, kelondroOrder ko) {
        // the byte[] a may be shorter than the chunksize
        if (chunkcount == 0) return;
        synchronized(chunkcache) {
            int p = find(a);
            remove(p);
        }
    }
    
    private void remove(int p) {
        if (chunkcount == 0) return;
        if ((p < 0) || (p >= chunkcount)) return; // out of bounds, nothing to delete
        System.arraycopy(chunkcache, (p + 1) * chunksize, chunkcache, p * chunksize, (chunkcount - p - 1) * chunksize);
        chunkcount--;
        if (p < sortbound) sortbound--;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public void removeAll(kelondroCollection c) {
        Iterator i = c.elements();
        while (i.hasNext()) remove((byte[]) i.next());
    }
    
    public void clear() {
        this.chunkcount = 0;
        this.chunkcache = new byte[0];
        this.order = null;
    }
    
    public int size() {
        return chunkcount;
    }
    
    public Iterator elements() {  // iterates byte[] - objects
        return new chunkIterator();
    }
    
    public class chunkIterator implements Iterator {

        int c = 0;
        
        public chunkIterator() {
            c = 0;
        }
        
        public boolean hasNext() {
            return c < chunkcount;
        }

        public Object next() {
            byte[] chunk = new byte[chunksize];
            System.arraycopy(chunkcache, c * chunksize, chunk, 0, chunksize);
            c++;
            return chunk;
        }

        public void remove() {
            c--;
            System.arraycopy(chunkcache, (c + 1) * chunksize, chunkcache, c * chunksize, (chunkcount - c - 1) * chunksize);
            chunkcount--;
        }
        
    }
    
    public kelondroOrder getOrdering() {
        return this.order;
    }

    private int find(byte[] a) {
        // returns the chunknumber; -1 if not found
        
        if (this.order == null) return iterativeSearch(a);
        
        // check if a re-sorting make sense
        if (this.chunkcount - this.sortbound > 800) sort();
        //if ((this.chunkcount - this.sortbound) / (this.chunkcount + 1) * 100 > 20) sort();
        
        // first try to find in sorted area
        int p = iterativeSearch(a);
        if (p >= 0) return p;
        
        // then find in unsorted area
        return binarySearch(a);
        
    }
    
    private int iterativeSearch(byte[] key) {
        // returns the chunknumber
        
        if (this.order == null) {
            for (int i = this.sortbound; i < this.chunkcount; i++) {
                if (match(key, i)) return i;
            }
            return -1;
        } else {
            for (int i = this.sortbound; i < this.chunkcount; i++) {
                if (compare(key, i) == 0) return i;
            }
            return -1;
        }
    }
    
    private int binarySearch(byte[] key) {
        assert (this.order != null);
        int l = 0;
        int rbound = this.sortbound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = l + ((rbound - l) >> 1);
            d = compare(key, p);
            if (d == 0) return p;
            else if (d < 0) rbound = p;
            else l = p + 1;
        }
        return -1;
    }

    public void sort() {
        if (this.sortbound == this.chunkcount) return; // this is already sorted
        //System.out.println("SORT");
        if (this.sortbound > 1) qsort(0, this.sortbound, this.chunkcount);
        else qsort(0, this.chunkcount);
        this.sortbound = this.chunkcount;
    }

    private void qsort(int l, int sbound, int rbound) {
        //System.out.println("QSORT: chunkcache.length=" + chunkcache.length + ", chunksize=" + chunksize + ", l=" + l + ", sbound=" + sbound + ", rbound=" + rbound);
        assert (sbound <= rbound);
        if (l >= rbound - 1) return;
        
        if (rbound - l < 1000) {
            isort(l, rbound);
            return;
         }
        
        int p = l + ((sbound - l) / 2);
        int q = sbound;
        int qs = q;
        byte[] a = new byte[chunksize];
        try {
        System.arraycopy(chunkcache, p * chunksize, a, 0, chunksize);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("EXCEPTION: chunkcache.length=" + chunkcache.length + ", p=" + p + ", chunksize=" + chunksize + ", l=" + l + ", sbound=" + sbound + ", rbound=" + rbound);
            System.exit(-1);
        }
        p++;
        int ps = p;
        while (q < rbound) {
            if (compare(a, q) < 1) {
                q++;
            } else {
                swap(p, q);
                p++;
                q++;
            }
        }
        if (qs < p) qs = p;
        if ((ps - l) <= ((p - l) / 2)) qsort(l, p); else qsort(l, ps, p);
        if ((qs - p) <= ((q - p) / 2)) qsort(p, q); else qsort(p, qs, q);
    }

    private void qsort(int l, int rbound) {
        if (l >= rbound - 1) return;
        
        if (rbound - l < 10) {
            isort(l, rbound);
            return;
         }
        
        int i = l;
        int j = rbound - 1;
        byte[] a = new byte[chunksize];
        int pivot = (i + j) / 2;
        System.arraycopy(chunkcache, pivot * chunksize, a, 0, chunksize);
        while (i <= j) {
            while (compare(a, i) == 1) i++; // chunkAt[i] < keybuffer
            while (compare(a, j) == -1) j--; // chunkAt[j] > keybuffer
            if (i <= j) {
                swap(i, j);
                i++;
                j--;
            }
        }
        qsort(l, i);
        qsort(i, rbound);
    }

    private void isort(int l, int rbound) {
        for (int i = l + 1; i < rbound; i++)
            for (int j = i; j > l && compare(j - 1, j) > 0; j--)
                swap(j, j - 1);
    }

    private void swap(int i, int j) {
        byte[] a = new byte[chunksize];
        System.arraycopy(chunkcache, chunksize * i, a, 0, chunksize);
        System.arraycopy(chunkcache, chunksize * j , chunkcache, chunksize * i, chunksize);
        System.arraycopy(a, 0, chunkcache, chunksize * j, chunksize);
    }

    public void uniq() {
        assert (this.order != null);
        // removes double-occurrences of chunks
        // this works only if the collection was ordered with sort before
        synchronized (chunkcache) {
            if (chunkcount <= 1) return;
            int i = 0;
            while (i < chunkcount - 1) {
                if (compare(i, i + 1) == 0) {
                    remove(i);
                } else {
                    i++;
                }
            }
        }
    }
    
    public String toString() {
        StringBuffer s = new StringBuffer();
        Iterator i = elements();
        if (i.hasNext()) s.append(new String((byte[]) i.next()).trim());
        while (i.hasNext())  s.append(", " + new String((byte[]) i.next()).trim());
        return new String(s);
    }
    
    public byte[] toByteArray() {
        return this.chunkcache;
    }
    
    public boolean match(byte[] a, int chunknumber) {
        if (chunknumber >= chunkcount) return false;
        int i = 0;
        int p = chunknumber * chunksize;
        final int len = a.length;
        if (len > chunksize) return false;
        while (i < len)
            if (a[i++] != chunkcache[p++]) return false;
        return true;
    }

    public int compare(byte[] a, int chunknumber) {
        assert (chunknumber < chunkcount);
        int l = Math.min(a.length, chunksize);
        return this.order.compare(a, 0, a.length, chunkcache, chunknumber * chunksize, l);
    }

    public int compare(int i, int j) {
        // this can be enhanced
        assert (i < chunkcount);
        assert (j < chunkcount);
        return this.order.compare(chunkcache, i * chunksize, chunksize, chunkcache, j * chunksize, chunksize);
    }
    
    public static void main(String[] args) {
        String[] test = { "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn" };
        kelondroCollection c = new kelondroCollection(10, 0, kelondroNaturalOrder.naturalOrder, new byte[0]);
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes());
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes());
        c.sort();
        c.remove("fuenf".getBytes());
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
        c = new kelondroCollection(10, 0, kelondroNaturalOrder.naturalOrder, new byte[0]);
        long start = System.currentTimeMillis();
        long t, d = 0;
        byte[] w;
        for (long k = 0; k < 200000; k++) {
            t = System.currentTimeMillis();
            w = ("a" + Long.toString((t % 13775) + k)).getBytes();
            if (c.get(w) == null) c.add(w); else d++;
            if (k % 1000 == 0)
                System.out.println("added " + k + " entries in " +
                    ((t - start) / 1000) + " seconds, " +
                    (((t - start) > 1000) ? (k / ((t - start) / 1000)) : 0) +
                    " entries/second, " + d + " double, size = " + c.size() + 
                    ", sum = " + (c.size() + d));
        }
    }
    
}
