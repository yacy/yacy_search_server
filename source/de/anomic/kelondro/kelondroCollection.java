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

import java.util.Comparator;
import java.util.Iterator;

public class kelondroCollection {

    private byte[] chunkcache;
    private int chunkcount;
    private int chunksize;
    private long lastTimeRead, lastTimeWrote;
    private String orderkey;
    
    public kelondroCollection(int objectSize) {
        this(objectSize, 0, null, new byte[0]);
    }
    
    public kelondroCollection(int objectSize, int objectCount, String signature, byte[] collectioncache) {
        assert (collectioncache.length % objectSize == 0);
        assert (objectCount <= collectioncache.length / objectSize);
        this.chunksize = objectSize;
        this.chunkcache = collectioncache;
        this.chunkcount = objectCount;
        this.orderkey = signature; // no current ordering
    }
    
    private void ensureSize(int elements) {
        int needed = elements * chunksize;
        if (chunkcache.length >= needed) return;
        byte[] newChunkcache = new byte[needed];
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
    
    public void add(byte[] a) {
        assert (a.length <= chunksize);
        synchronized (chunkcache) {
            ensureSize(chunkcount + 1);
            System.arraycopy(a, 0, chunkcache, chunksize * chunkcount, a.length);
            chunkcount++;
            this.orderkey = null;
        }
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
    
    public void remove(byte[] a, Comparator c) {
        // the byte[] a may be shorter than the chunksize
        if (chunkcount == 0) return;
        synchronized(chunkcache) {
            int p = find(a, c);
            remove(p);
        }
    }
    
    private void remove(int p) {
        if (chunkcount == 0) return;
        if ((p < 0) || (p >= chunkcount)) return; // out of bounds, nothing to delete
        System.arraycopy(chunkcache, (p + 1) * chunksize, chunkcache, p * chunksize, (chunkcount - p - 1) * chunksize);
        chunkcount--;
    }
    
    private int find(byte[] a) {
        // returns the chunknumber
        for (int i = 0; i < chunkcount; i++) {
            if (match(a, i)) return i;
        }
        return -1;
    }
    
    private int find(byte[] a, Comparator c) {
        // returns the chunknumber
        for (int i = 0; i < chunkcount; i++) {
            if (compare(a, i, c) == 0) return i;
        }
        return -1;
    }
    
    public void removeAll(kelondroCollection c) {
        Iterator i = c.elements();
        while (i.hasNext()) remove((byte[]) i.next());
    }
    
    public void clear() {
        this.chunkcount = 0;
        this.chunkcache = new byte[0];
        this.orderkey = null;
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
    
    public String getOrderingSignature() {
        return this.orderkey;
    }

    public int binarySearch(byte[] key, Comparator c) {
        assert (this.orderkey != null);
        int l = 0;
        int r = chunkcount - 1;
        int p = 0;
        int d;
        while (l <= r) {
            p = (l + r) >> 1;
            d = compare(key, p, c);
            if (d == 0) return p;
            else if (d < 0) r = p - 1;
            else l = ++p;
        }
        return -p - 1;
    }

    public void sort(kelondroOrder ko) {
        if (this.orderkey == ko.signature()) return; // this is already sorted
        qsort(0, chunkcount - 1, (Comparator) ko);
        this.orderkey = ko.signature();
    }

    public void sort(int fromIndex, int toIndex, Comparator c) {
        assert (fromIndex <= toIndex);
        assert (fromIndex >= 0);
        synchronized(chunkcache) {
            qsort(fromIndex, toIndex, c);
        }
    }

    private void swap(int i, int j) {
        byte[] a = new byte[chunksize];
        System.arraycopy(chunkcache, chunksize * i, a, 0, chunksize);
        System.arraycopy(chunkcache, chunksize * j , chunkcache, chunksize * i, chunksize);
        System.arraycopy(a, 0, chunkcache, chunksize * j, chunksize);
    }

    private void isort(int l, int r, Comparator c) {
        for (int i = l + 1; i <= r; i++)
            for (int j = i; j > l && compare(j - 1, j, c) > 0; j--)
                swap(j, j - 1);
    }

    private void qsort(int l, int r, Comparator c) {
        if (l >= r) return;
        
        if (r - l < 10) {
            isort(l, r, c);
            return;
         }
        
        int i = l;
        int j = r;
        byte[] a = new byte[chunksize];
        int pivot = (i + j) / 2;
        System.arraycopy(chunkcache, pivot * chunksize, a, 0, chunksize);
        while (i <= j) {
            while (compare(a, i, c) == 1) i++; // chunkAt[i] < keybuffer
            while (compare(a, j, c) == -1) j--; // chunkAt[j] > keybuffer
            if (i <= j) {
                swap(i, j);
                i++;
                j--;
            }
        }
        qsort(l, j, c);
        qsort(i, r, c);
    }

    public void uniq(Comparator c) {
        assert (this.orderkey != null);
        // removes double-occurrences of chunks
        // this works only if the collection was ordered with sort before
        synchronized (chunkcache) {
            if (chunkcount <= 1) return;
            int i = 0;
            while (i < chunkcount - 1) {
                if (compare(i, i + 1, c) == 0) {
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
        if (chunknumber >= chunkcount)
            return false;
        int i = 0;
        int p = chunknumber * chunksize;
        final int len = a.length;
        if (len > chunksize)
            return false;
        while (i < len)
            if (a[i++] != chunkcache[p++])
                return false;
        return true;
    }

    public int compare(byte[] a, int chunknumber, Comparator c) {
        // this can be enhanced
        assert (chunknumber < chunkcount);
        byte[] b = new byte[chunksize];
        System.arraycopy(chunkcache, chunknumber * chunksize, b, 0, chunksize);
        return c.compare(a, b);
    }

    public int compare(int i, int j, Comparator c) {
        // this can be enhanced
        assert (i < chunkcount);
        assert (j < chunkcount);
        byte[] a = new byte[chunksize];
        byte[] b = new byte[chunksize];
        System.arraycopy(chunkcache, i * chunksize, a, 0, chunksize);
        System.arraycopy(chunkcache, j * chunksize, b, 0, chunksize);
        return c.compare(a, b);
    }

    public static void main(String[] args) {
        String[] test = { "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn" };
        kelondroCollection c = new kelondroCollection(10);
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes());
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes());
        c.remove("fuenf".getBytes());
        Iterator i = c.elements();
        String s;
        while (i.hasNext()) {
            s = new String((byte[]) i.next()).trim();
            System.out.print(s + ", ");
            if (s.equals("drei")) i.remove();
        }
        System.out.println("");
        System.out.println(c.toString());
        c.sort(kelondroNaturalOrder.naturalOrder);
        System.out.println(c.toString());
        c.uniq(kelondroNaturalOrder.naturalOrder);
        System.out.println(c.toString());
        c.trim();
        System.out.println(c.toString());
    }
    
}
