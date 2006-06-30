// kelondroRowCollection.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 12.01.2006 on http://www.anomic.de
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

public class kelondroRowCollection {

    protected byte[]        chunkcache;
    protected int           chunkcount;
    protected long          lastTimeRead, lastTimeWrote;    
    protected kelondroRow   rowdef;
    protected int           sortBound;
    protected kelondroOrder sortOrder;
    protected int           sortColumn;
    
    public kelondroRowCollection(kelondroRow rowdef) {
        this(rowdef, 0);
    }
    
    public kelondroRowCollection(kelondroRow rowdef, int objectCount) {
        this.rowdef = rowdef;
        this.chunkcache = new byte[objectCount * rowdef.objectsize()];
        this.chunkcount = 0;
        this.sortColumn = 0;
        this.sortOrder = null;
        this.sortBound = 0;
        this.lastTimeRead = System.currentTimeMillis();
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public kelondroRowCollection(kelondroRow rowdef, int objectCount, byte[] cache) {
        this.rowdef = rowdef;
        this.chunkcache = cache;
        this.chunkcount = objectCount;
        this.sortColumn = 0;
        this.sortOrder = null;
        this.sortBound = 0;
        this.lastTimeRead = System.currentTimeMillis();
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public kelondroRow row() {
        return this.rowdef;
    }
    
    private final void ensureSize(int elements) {
        int needed = elements * rowdef.objectsize();
        if (chunkcache.length >= needed) return;
        byte[] newChunkcache = new byte[needed * 2];
        System.arraycopy(chunkcache, 0, newChunkcache, 0, chunkcache.length);
        chunkcache = newChunkcache;
        newChunkcache = null;
    }
    
    public void trim() {
        if (chunkcache.length == 0) return;
        synchronized (chunkcache) {
            int needed = chunkcount * rowdef.objectsize();
            if (chunkcache.length == needed) return;
            byte[] newChunkcache = new byte[needed];
            System.arraycopy(chunkcache, 0, newChunkcache, 0, newChunkcache.length);
            chunkcache = newChunkcache;
            newChunkcache = null;
        }
    }
    
    public final long lastRead() {
        return lastTimeRead;
    }
    
    public final long lastWrote() {
        return lastTimeWrote;
    }
    
    public final kelondroRow.Entry get(int index) {
        assert (index < chunkcount);
        byte[] a = new byte[rowdef.objectsize()];
        synchronized (chunkcache) {
            System.arraycopy(chunkcache, index * rowdef.objectsize(), a, 0, rowdef.objectsize());
        }
        this.lastTimeRead = System.currentTimeMillis();
        return rowdef.newEntry(a);
    }
    
    public final void set(int index, kelondroRow.Entry a) {
        set(index, a.bytes(), 0, a.bytes().length);
    }
    
    public final void set(int index, byte[] a, int astart, int alength) {
        assert (index < this.chunkcount);
        int l = Math.min(rowdef.objectsize(), Math.min(alength, a.length - astart));
        synchronized (chunkcache) {
            System.arraycopy(a, astart, chunkcache, index * rowdef.objectsize(), l);
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public void add(kelondroRow.Entry a) {
        add(a.bytes(), 0, a.bytes().length);
    }
    
    public void add(byte[] a) {
        add(a, 0, a.length);
    }
    
    private final void add(byte[] a, int astart, int alength) {
        int l = Math.min(rowdef.objectsize(), Math.min(alength, a.length - astart));
        synchronized (chunkcache) {
            ensureSize(chunkcount + 1);
            System.arraycopy(a, 0, chunkcache, rowdef.objectsize() * chunkcount, l);
            chunkcount++;
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public final void addAll(kelondroRowCollection c) {
        assert(rowdef.objectsize() >= c.rowdef.objectsize());
        synchronized(chunkcache) {
            ensureSize(chunkcount + c.size());
        }
        Iterator i = c.elements();
        byte[] b;
        while (i.hasNext()) {
            b = (byte[]) i.next();
            add(b, 0, b.length);
        }
    }
    
    protected final void removeShift(int pos, int dist, int upBound) {
        System.arraycopy(chunkcache, (pos + dist) * rowdef.objectsize(),
                         chunkcache, pos * rowdef.objectsize(),
                         (upBound - pos - dist) * rowdef.objectsize());
        if ((pos < sortBound) && (upBound >= sortBound)) sortBound -= dist;
    }
    
    public final void removeShift(int p) {
        assert ((p >= 0) && (p < chunkcount) && (chunkcount > 0));
        //System.out.println("REMOVE at pos " + p + ", chunkcount=" + chunkcount + ", sortBound=" + sortBound);
        synchronized (chunkcache) {
            removeShift(p, 1, chunkcount--);
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public void removeOne() {
        if (chunkcount == 0) return;
        if (chunkcount == sortBound) sortBound--;
        chunkcount--;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public void clear() {
        this.chunkcache = new byte[0];
        this.chunkcount = 0;
        this.sortBound = 0;
        this.lastTimeWrote = System.currentTimeMillis();
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
            byte[] chunk = new byte[rowdef.objectsize()];
            System.arraycopy(chunkcache, c * rowdef.objectsize(), chunk, 0, rowdef.objectsize());
            c++;
            return chunk;
        }

        public void remove() {
            c--;
            System.arraycopy(chunkcache, (c + 1) * rowdef.objectsize(), chunkcache, c * rowdef.objectsize(), (chunkcount - c - 1) * rowdef.objectsize());
            chunkcount--;
        }
        
    }

    protected final void sort(kelondroOrder newOrder, int newColumn) {
        if ((this.sortOrder == null) ||
            (!(this.sortOrder.signature().equals(newOrder.signature()))) ||
            (newColumn != this.sortColumn)) {
            this.sortOrder = newOrder;
            this.sortBound = 0;
            this.sortColumn = newColumn;
        }
        sort();
    }

    protected final void sort() {
        assert (this.sortOrder != null);
        if (this.sortBound == this.chunkcount) return; // this is already sorted
        //System.out.println("SORT(chunkcount=" + this.chunkcount + ", sortBound=" + this.sortBound + ")");
        if (this.sortBound > 1) {
            qsort(0, this.sortBound, this.chunkcount);
        } else {
            qsort(0, this.chunkcount);
        }
        this.sortBound = this.chunkcount;
    }

    private final void qsort(int L, int S, int R) {
        //System.out.println("QSORT: chunkcache.length=" + chunkcache.length + ", chunksize=" + chunksize + ", L=" + L + ", S=" + S + ", R=" + R);
        assert (S <= R);
        if (L >= R - 1) return;
        if (S >= R) return;

        if (R - L < 20) {
            isort(L, R);
            return;
         }
        
        int p = L + ((S - L) / 2);
        int ps = p;
        int q = S;
        int qs = q;
        int pivot = p;
        while (q < R) {
            if (compare(pivot, q) < 1) {
                q++;
            } else {
                pivot = swap(p, q, pivot);
                p++;
                q++;
            }
        }
        if ((ps - L) <= ((p - L) / 2)) qsort(L, p); else qsort(L, ps, p);
        if ((qs - p) <= ((R - p) / 2)) qsort(p, R); else qsort(p, qs, R);
    }

    private final void qsort(int L, int R) {
        //System.out.println("QSORT: chunkcache.length=" + chunkcache.length + ", L=" + L + "/" + new String(this.chunkcache, L * this.rowdef.objectsize(), this.rowdef.width(0)) + ", R=" + R + "/" + new String(this.chunkcache, (R - 1) * this.rowdef.objectsize(), this.rowdef.width(0)));
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
            isort(L, R);
            return;
         }
        
        int i = L;
        int j = R - 1;
        int pivot = (i + j) / 2;
        //System.out.println("Pivot=" + pivot + "/" + new String(this.chunkcache, pivot * this.rowdef.objectsize(), this.rowdef.width(0)));
        while (i <= j) {
            while (compare(pivot, i) ==  1) i++; // chunkAt[i] < keybuffer
            while (compare(pivot, j) == -1) j--; // chunkAt[j] > keybuffer
            //if (L == 6693) System.out.println(i + ", " + j);
            if (i <= j) {
                pivot = swap(i, j, pivot);
                i++;
                j--;
            }
        }
        //if (L == 6693) System.out.println(i);
        qsort(L, i);
        qsort(i, R);
    }

    private final void isort(int L, int R) {
        for (int i = L + 1; i < R; i++)
            for (int j = i; j > L && compare(j - 1, j) > 0; j--)
                swap(j, j - 1, 0);
    }

    protected final int swap(int i, int j, int p) {
        if (i == j) return p;
        if (this.chunkcount * this.rowdef.objectsize() < this.chunkcache.length) {
            // there is space in the chunkcache that we can use as buffer
            System.arraycopy(chunkcache, this.rowdef.objectsize() * i, chunkcache, chunkcache.length - this.rowdef.objectsize(), this.rowdef.objectsize());
            System.arraycopy(chunkcache, this.rowdef.objectsize() *j , chunkcache, this.rowdef.objectsize() * i, this.rowdef.objectsize());
            System.arraycopy(chunkcache, chunkcache.length - this.rowdef.objectsize(), chunkcache, this.rowdef.objectsize() * j, this.rowdef.objectsize());
        } else {
            // allocate a chunk to use as buffer
            byte[] a = new byte[this.rowdef.objectsize()];
            System.arraycopy(chunkcache, this.rowdef.objectsize() * i, a, 0, this.rowdef.objectsize());
            System.arraycopy(chunkcache, this.rowdef.objectsize() * j , chunkcache, this.rowdef.objectsize() * i, this.rowdef.objectsize());
            System.arraycopy(a, 0, chunkcache, this.rowdef.objectsize() * j, this.rowdef.objectsize());
        }
        if (i == p) return j; else if (j == p) return i; else return p;
    }

    public void uniq() {
        assert (this.sortOrder != null);
        // removes double-occurrences of chunks
        // this works only if the collection was ordered with sort before
        synchronized (chunkcache) {
            if (chunkcount <= 1) return;
            int i = 0;
            while (i < chunkcount - 1) {
                if (compare(i, i + 1) == 0) {
                    //System.out.println("DOUBLE: " + new String(this.chunkcache, this.chunksize * i, this.chunksize));
                    removeShift(i);
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

    private final int compare(int i, int j) {
        assert (i < chunkcount);
        assert (j < chunkcount);
        if (i == j) return 0;
        int keylength = this.rowdef.width(this.sortColumn);
        int colstart  = this.rowdef.colstart[this.sortColumn];
        int c = this.sortOrder.compare(
                chunkcache,
                i * this.rowdef.objectsize() + colstart,
                keylength,
                chunkcache,
                j * this.rowdef.objectsize() + colstart,
                keylength);
        /*
        System.out.println("COMPARE(" +
                new String(this.chunkcache, i * this.rowdef.objectsize(), this.rowdef.width(0)) +
                ", " +
                new String(this.chunkcache, j * this.rowdef.objectsize(), this.rowdef.width(0)) +
                ")=" + c);
                */
        return c;
    }

}
