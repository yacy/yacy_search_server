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
import java.util.Random;

public class kelondroCollection {

    protected byte[] chunkcache;
    private int chunkcount;
    private int chunksize;
    private int sortbound;
    private long lastTimeRead, lastTimeWrote;
    private kelondroOrder order;
    
    public kelondroCollection(int objectSize) {
        this(objectSize, 0);
    }
    
    public kelondroCollection(int objectSize, int objectCount) {
        this.chunksize = objectSize;
        this.chunkcache = new byte[objectCount * objectSize];
        this.chunkcount = 0;
        this.order = null;
        this.sortbound = 0;
    }
    
    public kelondroCollection(int objectSize, int objectCount, byte[] cache) {
        this.chunksize = objectSize;
        this.chunkcache = cache;
        this.chunkcount = objectCount;
        this.order = null;
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
        return get(key, key.length);
    }
    
    public byte[] get(byte[] key, int length) {
        synchronized (chunkcache) {
            int i = find(key, length);
            if (i >= 0) return get(i);
        }
        return null;
    }
    
    protected void set(int index, byte[] a) {
        set(index, a, a.length);
    }
    
    protected void set(int index, byte[] a, int length) {
        assert (index < this.chunkcount);
        int l = Math.min(this.chunksize, Math.min(length, a.length));
        synchronized (chunkcache) {
            System.arraycopy(a, 0, chunkcache, chunksize * index, l);
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public void add(byte[] a) {
        add(a, a.length);
    }
    
    public void add(byte[] a, int length) {
        int l = Math.min(this.chunksize, Math.min(length, a.length));
        synchronized (chunkcache) {
            ensureSize(chunkcount + 1);
            System.arraycopy(a, 0, chunkcache, chunksize * chunkcount, l);
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
        byte[] b;
        while (i.hasNext()) {
            b = (byte[]) i.next();
            add(b, b.length);
        }
    }
    
    public byte[] remove(byte[] a) {
        return remove(a, a.length);
    }
    
    public byte[] remove(byte[] a, int length) {
        // the byte[] a may be shorter than the chunksize
        if (chunkcount == 0) return null;
        byte[] b = null;
        synchronized(chunkcache) {
            int p = find(a, length);
            if (p < 0) return null;
            b = get(p);
            remove(p);
        }
        return b;
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
        byte[] b;
        while (i.hasNext()) {
            b = (byte[]) i.next();
            remove(b, b.length);
        }
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

    public void setOrdering(kelondroOrder newOrder) {
        if (this.order == null) {
            this.order = newOrder;
            this.sortbound = 0;
        } else if (!(this.order.signature().equals(newOrder.signature()))) {
            this.order = newOrder;
            this.sortbound = 0;
        }
    }
    
    protected int find(byte[] a, int length) {
        // returns the chunknumber; -1 if not found
        
        if (this.order == null) return iterativeSearch(a, length);
        
        // check if a re-sorting make sense
        if (this.chunkcount - this.sortbound > 1200) sort(Math.min(a.length, this.chunksize));
        //if ((this.chunkcount - this.sortbound) / (this.chunkcount + 1) * 100 > 20) sort();
        
        // first try to find in sorted area
        int p = iterativeSearch(a, length);
        if (p >= 0) return p;
        
        // then find in unsorted area
        return binarySearch(a, length);
        
    }
    
    private int iterativeSearch(byte[] key, int length) {
        // returns the chunknumber
        
        if (this.order == null) {
            for (int i = this.sortbound; i < this.chunkcount; i++) {
                if (match(key, length, i)) return i;
            }
            return -1;
        } else {
            for (int i = this.sortbound; i < this.chunkcount; i++) {
                if (compare(key, length, i) == 0) return i;
            }
            return -1;
        }
    }
    
    private int binarySearch(byte[] key, int length) {
        assert (this.order != null);
        int l = 0;
        int rbound = this.sortbound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = l + ((rbound - l) >> 1);
            d = compare(key, length, p);
            if (d == 0) return p;
            else if (d < 0) rbound = p;
            else l = p + 1;
        }
        return -1;
    }

    public void sort(kelondroOrder newOrder, int keylen) {
        if (this.order == null) {
            this.order = newOrder;
            this.sortbound = 0;
        } else if (!(this.order.signature().equals(newOrder.signature()))) {
            this.order = newOrder;
            this.sortbound = 0;
        }
        sort(keylen);
    }

    public void sort(int keylen) {
        assert (this.order != null);
        if (this.sortbound == this.chunkcount) return; // this is already sorted
        //System.out.println("SORT");
        if (this.sortbound > 1) {
            qsort(keylen, 0, this.sortbound, this.chunkcount);
        } else {
            qsort(keylen, 0, this.chunkcount);
        }
        this.sortbound = this.chunkcount;
    }

    private void qsort(int keylen, int L, int S, int R) {
        //System.out.println("QSORT: chunkcache.length=" + chunkcache.length + ", chunksize=" + chunksize + ", L=" + L + ", S=" + S + ", R=" + R);
        assert (S <= R);
        if (L >= R - 1) return;
        if (S >= R) return;

        if (R - L < 20) {
            isort(keylen, L, R);
            return;
         }
        
        int p = L + ((S - L) / 2);
        int ps = p;
        int q = S;
        int qs = q;
        int pivot = p;
        while (q < R) {
            if (compare(pivot, q, keylen) < 1) {
                q++;
            } else {
                pivot = swap(p, q, pivot);
                p++;
                q++;
            }
        }
        if ((ps - L) <= ((p - L) / 2)) qsort(keylen, L, p); else qsort(keylen, L, ps, p);
        if ((qs - p) <= ((R - p) / 2)) qsort(keylen, p, R); else qsort(keylen, p, qs, R);
    }

    private void qsort(int keylen, int L, int R) {
        //System.out.println("QSORT: chunkcache.length=" + chunkcache.length + ", chunksize=" + chunksize + ", L=" + L + "/" + new String(this.chunkcache, L * this.chunksize, this.chunksize) + ", R=" + R + "/" + new String(this.chunkcache, (R - 1) * this.chunksize, this.chunksize));
        /*
        if ((L == 190) && (R == 258)) {
            for (int i = L; i < R; i++) {
                System.out.print(new String(this.chunkcache, L * this.chunksize, this.chunksize) + ", ");
            }
            System.out.println();
        }
        */
        if (L >= R - 1) return;
        
        if (R - L < 20) {
            isort(keylen, L, R);
            return;
         }
        
        int i = L;
        int j = R - 1;
        int pivot = (i + j) / 2;
        while (i <= j) {
            while (compare(pivot, i, keylen) ==  1) i++; // chunkAt[i] < keybuffer
            while (compare(pivot, j, keylen) == -1) j--; // chunkAt[j] > keybuffer
            if (i <= j) {
                pivot = swap(i, j, pivot);
                i++;
                j--;
            }
        }
        qsort(keylen, L, i);
        qsort(keylen, i, R);
    }

    private void isort(int keylen, int L, int R) {
        for (int i = L + 1; i < R; i++)
            for (int j = i; j > L && compare(j - 1, j, keylen) > 0; j--)
                swap(j, j - 1, 0);
    }

    private int swap(int i, int j, int p) {
        if (i == j) return p;
        if (this.chunkcount * this.chunksize < this.chunkcache.length) {
            // there is space in the chunkcache that we can use as buffer
            System.arraycopy(chunkcache, chunksize * i, chunkcache, chunkcache.length - chunksize, chunksize);
            System.arraycopy(chunkcache, chunksize * j , chunkcache, chunksize * i, chunksize);
            System.arraycopy(chunkcache, chunkcache.length - chunksize, chunkcache, chunksize * j, chunksize);
        } else {
            // allocate a chunk to use as buffer
            byte[] a = new byte[chunksize];
            System.arraycopy(chunkcache, chunksize * i, a, 0, chunksize);
            System.arraycopy(chunkcache, chunksize * j , chunkcache, chunksize * i, chunksize);
            System.arraycopy(a, 0, chunkcache, chunksize * j, chunksize);
        }
        if (i == p) return j; else if (j == p) return i; else return p;
    }

    public void uniq(int keylength) {
        assert (this.order != null);
        // removes double-occurrences of chunks
        // this works only if the collection was ordered with sort before
        synchronized (chunkcache) {
            if (chunkcount <= 1) return;
            int i = 0;
            while (i < chunkcount - 1) {
                if (compare(i, i + 1, Math.min(keylength, this.chunksize)) == 0) {
                    //System.out.println("DOUBLE: " + new String(this.chunkcache, this.chunksize * i, this.chunksize));
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
    
    public boolean match(byte[] a, int length, int chunknumber) {
        if (chunknumber >= chunkcount) return false;
        int i = 0;
        int p = chunknumber * chunksize;
        final int len = Math.min(length, a.length);
        while (i < len) if (a[i++] != chunkcache[p++]) return false;
        return true;
    }

    public int compare(byte[] a, int length, int chunknumber) {
        assert (chunknumber < chunkcount);
        int l = Math.min(this.chunksize, Math.min(a.length, length));
        return this.order.compare(a, 0, l, chunkcache, chunknumber * chunksize, l);
    }

    public int compare(int i, int j, int keylength) {
        // this can be enhanced
        assert (i < chunkcount);
        assert (j < chunkcount);
        if (i == j) return 0;
        return this.order.compare(chunkcache, i * chunksize, keylength, chunkcache, j * chunksize, keylength);
    }
    
    public static void main(String[] args) {
        String[] test = { "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn" };
        kelondroCollection c = new kelondroCollection(10, 0);
        c.setOrdering(kelondroNaturalOrder.naturalOrder);
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes(), 10);
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes(), 10);
        c.sort(10);
        c.remove("fuenf".getBytes(), 5);
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
        c.sort(10);
        System.out.println("SORTED        : " + c.toString());
        c.uniq(10);
        System.out.println("UNIQ          : " + c.toString());
        c.trim();
        System.out.println("TRIM          : " + c.toString());
        
        // second test
        c = new kelondroCollection(10, 20);
        c.setOrdering(kelondroNaturalOrder.naturalOrder);
        Random rand = new Random(0);
        long start = System.currentTimeMillis();
        long t, d = 0;
        String w;
        for (long k = 0; k < 60000; k++) {
            t = System.currentTimeMillis();
            w = "a" + Long.toString(rand.nextLong());
            c.add(w.getBytes(), 10);
            if (k % 10000 == 0)
                System.out.println("added " + k + " entries in " +
                    ((t - start) / 1000) + " seconds, " +
                    (((t - start) > 1000) ? (k / ((t - start) / 1000)) : k) +
                    " entries/second, size = " + c.size());
        }
        System.out.println("bevore sort: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        c.sort(10);
        System.out.println("after sort: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        c.uniq(10);
        System.out.println("after uniq: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        System.out.println("RESULT SIZE: " + c.size());
        System.out.println();
        
        // third test
        c = new kelondroCollection(10, 60000);
        c.setOrdering(kelondroNaturalOrder.naturalOrder);
        rand = new Random(0);
        start = System.currentTimeMillis();
        d = 0;
        for (long k = 0; k < 60000; k++) {
            t = System.currentTimeMillis();
            w = "a" + Long.toString(rand.nextLong());
            if (c.get(w.getBytes(), 10) == null) c.add(w.getBytes(), 10); else d++;
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
