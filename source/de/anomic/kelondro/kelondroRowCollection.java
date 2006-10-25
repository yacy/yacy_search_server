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

import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import de.anomic.server.logging.serverLog;

public class kelondroRowCollection {

    protected byte[]        chunkcache;
    protected int           chunkcount;
    protected long          lastTimeRead, lastTimeWrote;    
    protected kelondroRow   rowdef;
    protected int           sortBound;
    protected kelondroOrder sortOrder;
    protected int           sortColumn;

    private static final int exp_chunkcount  = 0;
    private static final int exp_last_read   = 1;
    private static final int exp_last_wrote  = 2;
    private static final int exp_order_type  = 3;
    private static final int exp_order_col   = 4;
    private static final int exp_order_bound = 5;
    private static final int exp_collection  = 6;
    
    public kelondroRowCollection(kelondroRowCollection rc) {
        this.rowdef = rc.rowdef;
        this.chunkcache = rc.chunkcache;
        this.chunkcount = rc.chunkcount;
        this.sortColumn = rc.sortColumn;
        this.sortOrder = rc.sortOrder;
        this.sortBound = rc.sortBound;
        this.lastTimeRead = rc.lastTimeRead;
        this.lastTimeWrote = rc.lastTimeWrote;
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
    
    public kelondroRowCollection(kelondroRow rowdef, int objectCount, byte[] cache, kelondroOrder sortOrder, int sortColumn, int sortBound) {
        this.rowdef = rowdef;
        this.chunkcache = cache;
        this.chunkcount = objectCount;
        this.sortColumn = sortColumn;
        this.sortOrder = sortOrder;
        this.sortBound = sortBound;
        this.lastTimeRead = System.currentTimeMillis();
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public kelondroRowCollection(kelondroRow rowdef, byte[] exportedCollectionRowinstance) {
        this.rowdef = rowdef;
        int chunkcachelength = exportedCollectionRowinstance.length - exportOverheadSize;
        kelondroRow.Entry exportedCollection = exportRow(chunkcachelength).newEntry(exportedCollectionRowinstance);
        this.chunkcount = (int) exportedCollection.getColLong(exp_chunkcount);
        assert (this.chunkcount <= chunkcachelength / rowdef.objectsize) : "chunkcount = " + this.chunkcount + ", chunkcachelength = " + chunkcachelength + ", rowdef.objectsize = " + rowdef.objectsize;
        this.lastTimeRead = (exportedCollection.getColLong(exp_last_read) + 10957) * day;
        this.lastTimeWrote = (exportedCollection.getColLong(exp_last_wrote) + 10957) * day;
        String sortOrderKey = exportedCollection.getColString(exp_order_type, null);
        if ((sortOrderKey == null) || (sortOrderKey.equals("__"))) {
            this.sortOrder = null;
        } else {
            this.sortOrder = kelondroNaturalOrder.bySignature(sortOrderKey);
            if (this.sortOrder == null) this.sortOrder = kelondroBase64Order.bySignature(sortOrderKey);
        }
        this.sortColumn = (int) exportedCollection.getColLong(exp_order_col);
        this.sortBound = (int) exportedCollection.getColLong(exp_order_bound);
        this.chunkcache = exportedCollection.getColBytes(exp_collection);        
    }
    
    private static final long day = 1000 * 60 * 60 * 24;
    
    public static int daysSince2000(long time) {
        return (int) (time / day) - 10957;
    }
    
    private kelondroRow exportRow(int chunkcachelength) {
        // find out the size of this collection
        return new kelondroRow(
                "int size-4 {b256}," +
                "short lastread-2 {b256}," + // as daysSince2000
                "short lastwrote-2 {b256}," + // as daysSince2000
                "byte[] orderkey-2," +
                "short ordercol-2 {b256}," +
                "short orderbound-2 {b256}," +
                "byte[] collection-" + chunkcachelength
                );
    }
    
    public static final int exportOverheadSize = 14;
    
    public byte[] exportCollection() {
        // returns null if the collection is empty
        trim();
        kelondroRow row = exportRow(chunkcache.length);
        kelondroRow.Entry entry = row.newEntry();
        entry.setCol(exp_chunkcount, size());
        entry.setCol(exp_last_read, daysSince2000(this.lastTimeRead));
        entry.setCol(exp_last_wrote, daysSince2000(this.lastTimeWrote));
        entry.setCol(exp_order_type, (this.sortOrder == null) ? "__".getBytes() : this.sortOrder.signature().getBytes());
        entry.setCol(exp_order_col, this.sortColumn);
        entry.setCol(exp_order_bound, this.sortBound);
        entry.setCol(exp_collection, chunkcache);
        return entry.bytes();
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
            System.arraycopy(chunkcache, 0, newChunkcache, 0, Math.min(chunkcache.length, newChunkcache.length));
            chunkcache = newChunkcache;
            newChunkcache = null;
        }
    }
    /*
    public void implantRows(byte[] b) {
        assert (b.length % rowdef.objectsize() == 0);
        synchronized (chunkcache) {
            chunkcache = b;
            chunkcount = b.length / rowdef.objectsize();
            sortBound = 0;
            lastTimeWrote = System.currentTimeMillis();
        }
    }
    */
    public final long lastRead() {
        return lastTimeRead;
    }
    
    public final long lastWrote() {
        return lastTimeWrote;
    }
    
    public final kelondroRow.Entry get(int index) {
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < chunkcount) : "get: access with index " + index + " is above chunkcount " + chunkcount;
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
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < chunkcount) : "get: access with index " + index + " is above chunkcount " + chunkcount;
        int l = Math.min(rowdef.objectsize(), Math.min(alength, a.length - astart));
        synchronized (chunkcache) {
            System.arraycopy(a, astart, chunkcache, index * rowdef.objectsize(), l);
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public void addUnique(kelondroRow.Entry row) {
        add(row.bytes(), 0, row.bytes().length);
    }
    
    public void addUnique(kelondroRow.Entry row, Date entryDate) {
        addUnique(row);
    }

    public void add(byte[] a) {
        add(a, 0, a.length);
    }
    
    private final void add(byte[] a, int astart, int alength) {
        assert (a != null);
        assert (astart >= 0) && (astart < a.length) : " astart = " + a;
        assert (!(serverLog.allZero(a, astart, alength))) : "a = " + serverLog.arrayList(a, astart, alength);
        assert (alength > 0);
        assert (astart + alength <= a.length);
        int l = Math.min(rowdef.objectsize(), Math.min(alength, a.length - astart));
        synchronized (chunkcache) {
            ensureSize(chunkcount + 1);
            System.arraycopy(a, astart, chunkcache, rowdef.objectsize() * chunkcount, l);
            chunkcount++;
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public final void addAll(kelondroRowCollection c) {
        assert(rowdef.objectsize() >= c.rowdef.objectsize());
        synchronized(chunkcache) {
            ensureSize(chunkcount + c.size());
        }
        Iterator i = c.rows();
        kelondroRow.Entry entry;
        while (i.hasNext()) {
            entry = (kelondroRow.Entry) i.next();
            addUnique(entry);
        }
    }
    
    protected final void removeShift(int pos, int dist, int upBound) {
        System.arraycopy(chunkcache, (pos + dist) * rowdef.objectsize(),
                         chunkcache, pos * rowdef.objectsize(),
                         (upBound - pos - dist) * rowdef.objectsize());
    }
    
    public final void removeShift(int p) {
        assert ((p >= 0) && (p < chunkcount) && (chunkcount > 0));
        //System.out.println("REMOVE at pos " + p + ", chunkcount=" + chunkcount + ", sortBound=" + sortBound);
        synchronized (chunkcache) {
            if (p < sortBound) sortBound--;
            removeShift(p, 1, chunkcount--);
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public kelondroRow.Entry removeOne() {
        synchronized (chunkcache) {
            if (chunkcount == 0) return null;
            kelondroRow.Entry r = get(chunkcount - 1);
            if (chunkcount == sortBound) sortBound--;
            chunkcount--;
            this.lastTimeWrote = System.currentTimeMillis();
            return r;
        }
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
    
    public Iterator rows() {
        return new rowIterator();
    }
    
    public class rowIterator implements Iterator {

        private int p;
        
        public rowIterator() {
            p = 0;
        }
        
        public boolean hasNext() {
            return p < chunkcount;
        }

        public Object next() {
            return get(p++);
        }
        
        public void remove() {
            p--;
            System.arraycopy(chunkcache, (p + 1) * rowdef.objectsize(), chunkcache, p * rowdef.objectsize(), (chunkcount - p - 1) * rowdef.objectsize());
            if (chunkcount == sortBound) sortBound--;
            chunkcount--;
        }
    }
    
    public void select(Set keys) {
        // removes all entries but the ones given by urlselection
        if ((keys == null) || (keys.size() == 0)) return;
        synchronized (this) {
            Iterator i = rows();
            kelondroRow.Entry row;
            while (i.hasNext()) {
                row = (kelondroRow.Entry) i.next();
                if (!(keys.contains(row.getColString(0, null)))) i.remove();
            }
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
        assert (S <= R) : "S > R: S = " + S + ", R = " + R;
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
        Iterator i = rows();
        if (i.hasNext()) s.append(((kelondroRow.Entry) i.next()).toString());
        while (i.hasNext()) s.append(", " + ((kelondroRow.Entry) i.next()).toString());
        return new String(s);
    }

    private final int compare(int i, int j) {
        assert (chunkcount * this.rowdef.objectsize() <= chunkcache.length) : "chunkcount = " + chunkcount + ", objsize = " + this.rowdef.objectsize() + ", chunkcache.length = " + chunkcache.length;
        assert (i >= 0) && (i < chunkcount) : "i = " + i + ", chunkcount = " + chunkcount;
        assert (j >= 0) && (j < chunkcount) : "j = " + j + ", chunkcount = " + chunkcount;
        if (i == j) return 0;
        assert (this.sortColumn == 0) : "this.sortColumn = " + this.sortColumn;
        int keylength = this.rowdef.width(this.sortColumn);
        assert (keylength <= 12) : "keylength = " + keylength;
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

    public static void main(String[] args) {
        System.out.println(new java.util.Date(10957 * day));
        System.out.println(new java.util.Date(0));
        System.out.println(daysSince2000(System.currentTimeMillis()));
    }
}
