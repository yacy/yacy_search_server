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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public class kelondroRowCollection {

    public  static final double growfactor = 1.4;
    private static final int isortlimit = 20;
    
    
    protected byte[]        chunkcache;
    protected int           chunkcount;
    protected long          lastTimeRead, lastTimeWrote;    
    protected kelondroRow   rowdef;
    protected int           sortBound;

    private static final int exp_chunkcount  = 0;
    private static final int exp_last_read   = 1;
    private static final int exp_last_wrote  = 2;
    private static final int exp_order_type  = 3;
    private static final int exp_order_col   = 4;
    private static final int exp_order_bound = 5;
    private static final int exp_collection  = 6;
    
    private static int processors = Runtime.getRuntime().availableProcessors();
    
    public kelondroRowCollection(kelondroRowCollection rc) {
        this.rowdef = rc.rowdef;
        this.chunkcache = rc.chunkcache;
        this.chunkcount = rc.chunkcount;
        this.sortBound = rc.sortBound;
        this.lastTimeRead = rc.lastTimeRead;
        this.lastTimeWrote = rc.lastTimeWrote;
    }
    
    public kelondroRowCollection(kelondroRow rowdef, int objectCount) {
        this.rowdef = rowdef;
        this.chunkcache = new byte[objectCount * rowdef.objectsize];
        this.chunkcount = 0;
        this.sortBound = 0;
        this.lastTimeRead = System.currentTimeMillis();
        this.lastTimeWrote = System.currentTimeMillis();
    }
     
    public kelondroRowCollection(kelondroRow rowdef, int objectCount, byte[] cache, int sortBound) {
        this.rowdef = rowdef;
        this.chunkcache = cache;
        this.chunkcount = objectCount;
        this.sortBound = sortBound;
        this.lastTimeRead = System.currentTimeMillis();
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public kelondroRowCollection(kelondroRow rowdef, kelondroRow.Entry exportedCollectionRowEnvironment, int columnInEnvironment) {
        this.rowdef = rowdef;
        int chunkcachelength = exportedCollectionRowEnvironment.cellwidth(columnInEnvironment) - exportOverheadSize;
        kelondroRow.Entry exportedCollection = exportRow(chunkcachelength).newEntry(exportedCollectionRowEnvironment, columnInEnvironment);
        this.chunkcount = (int) exportedCollection.getColLong(exp_chunkcount);
        //assert (this.chunkcount <= chunkcachelength / rowdef.objectsize) : "chunkcount = " + this.chunkcount + ", chunkcachelength = " + chunkcachelength + ", rowdef.objectsize = " + rowdef.objectsize;
        if ((this.chunkcount > chunkcachelength / rowdef.objectsize)) {
            serverLog.logWarning("RowCollection", "corrected wrong chunkcount; chunkcount = " + this.chunkcount + ", chunkcachelength = " + chunkcachelength + ", rowdef.objectsize = " + rowdef.objectsize);
            this.chunkcount = chunkcachelength / rowdef.objectsize; // patch problem
        }
        this.lastTimeRead = (exportedCollection.getColLong(exp_last_read) + 10957) * day;
        this.lastTimeWrote = (exportedCollection.getColLong(exp_last_wrote) + 10957) * day;
        String sortOrderKey = exportedCollection.getColString(exp_order_type, null);
        kelondroByteOrder oldOrder = null;
        if ((sortOrderKey == null) || (sortOrderKey.equals("__"))) {
            oldOrder = null;
        } else {
            oldOrder = kelondroNaturalOrder.bySignature(sortOrderKey);
            if (oldOrder == null) oldOrder = kelondroBase64Order.bySignature(sortOrderKey);
        }
        if ((rowdef.objectOrder != null) && (oldOrder != null) && (!(rowdef.objectOrder.signature().equals(oldOrder.signature()))))
            throw new kelondroException("old collection order does not match with new order; objectOrder.signature = " + rowdef.objectOrder.signature() + ", oldOrder.signature = " + oldOrder.signature());
        if (rowdef.primaryKeyIndex != (int) exportedCollection.getColLong(exp_order_col))
            throw new kelondroException("old collection primary key does not match with new primary key");
        this.sortBound = (int) exportedCollection.getColLong(exp_order_bound);
        //assert (sortBound <= chunkcount) : "sortBound = " + sortBound + ", chunkcount = " + chunkcount;
        if (sortBound > chunkcount) {
            serverLog.logWarning("RowCollection", "corrected wrong sortBound; sortBound = " + sortBound + ", chunkcount = " + chunkcount);
            this.sortBound = chunkcount;
        }
        this.chunkcache = exportedCollection.getColBytes(exp_collection);        
    }

	public void reset() {
		this.chunkcache = new byte[0];
        this.chunkcount = 0;
        this.sortBound = 0;
	}
   
    private static final kelondroRow exportMeasureRow = exportRow(0 /* no relevance */);

    protected static final int sizeOfExportedCollectionRows(kelondroRow.Entry exportedCollectionRowEnvironment, int columnInEnvironment) {
    	kelondroRow.Entry exportedCollectionEntry = exportMeasureRow.newEntry(exportedCollectionRowEnvironment, columnInEnvironment);
    	int chunkcount = (int) exportedCollectionEntry.getColLong(exp_chunkcount);
        return chunkcount;
    }
    
    private static final long day = 1000 * 60 * 60 * 24;
    
    public static int daysSince2000(long time) {
        return (int) (time / day) - 10957;
    }
    
    private static kelondroRow exportRow(int chunkcachelength) {
        // find out the size of this collection
        return new kelondroRow(
                "int size-4 {b256}," +
                "short lastread-2 {b256}," + // as daysSince2000
                "short lastwrote-2 {b256}," + // as daysSince2000
                "byte[] orderkey-2," +
                "short ordercol-2 {b256}," +
                "short orderbound-2 {b256}," +
                "byte[] collection-" + chunkcachelength,
                kelondroNaturalOrder.naturalOrder, 0
                );
    }
    
    public static final int exportOverheadSize = 14;

    public synchronized byte[] exportCollection() {
        // returns null if the collection is empty
        trim(false);
        kelondroRow row = exportRow(chunkcache.length);
        kelondroRow.Entry entry = row.newEntry();
        assert (sortBound <= chunkcount) : "sortBound = " + sortBound + ", chunkcount = " + chunkcount;
        assert (this.chunkcount <= chunkcache.length / rowdef.objectsize) : "chunkcount = " + this.chunkcount + ", chunkcache.length = " + chunkcache.length + ", rowdef.objectsize = " + rowdef.objectsize;
        entry.setCol(exp_chunkcount, this.chunkcount);
        entry.setCol(exp_last_read, daysSince2000(this.lastTimeRead));
        entry.setCol(exp_last_wrote, daysSince2000(this.lastTimeWrote));
        entry.setCol(exp_order_type, (this.rowdef.objectOrder == null) ? "__".getBytes() :this.rowdef.objectOrder.signature().getBytes());
        entry.setCol(exp_order_col, this.rowdef.primaryKeyIndex);
        entry.setCol(exp_order_bound, this.sortBound);
        entry.setCol(exp_collection, this.chunkcache);
        return entry.bytes();
    }
    
    public void saveCollection(File file) throws IOException {
        serverFileUtils.write(exportCollection(), file);
    }

    public kelondroRow row() {
        return this.rowdef;
    }
    
    private final void ensureSize(int elements) {
        int needed = elements * rowdef.objectsize;
        if (chunkcache.length >= needed) return;
        byte[] newChunkcache = new byte[(int) (needed * growfactor)]; // increase space
        System.arraycopy(chunkcache, 0, newChunkcache, 0, chunkcache.length);
        chunkcache = newChunkcache;
        newChunkcache = null;
    }
    
    public final long memoryNeededForGrow() {
        return (long) ((((long) (chunkcount + 1)) * ((long) rowdef.objectsize)) * growfactor);
    }
    
    public synchronized void trim(boolean plusGrowFactor) {
        if (chunkcache.length == 0) return;
        int needed = chunkcount * rowdef.objectsize;
        if (plusGrowFactor) needed = (int) (needed * growfactor);
        if (needed >= chunkcache.length)
            return; // in case that the growfactor causes that the cache would
                    // grow instead of shrink, simply ignore the growfactor
        if (serverMemory.available() + 1000 < needed)
            return; // if the swap buffer is not available, we must give up.
                    // This is not critical. Othervise we provoke a serious
                    // problem with OOM
        byte[] newChunkcache = new byte[needed];
        System.arraycopy(chunkcache, 0, newChunkcache, 0, Math.min(
                chunkcache.length, newChunkcache.length));
        chunkcache = newChunkcache;
        newChunkcache = null;
    }

    public final long lastRead() {
        return lastTimeRead;
    }
    
    public final long lastWrote() {
        return lastTimeWrote;
    }
    
    public synchronized final byte[] getKey(int index) {
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < chunkcount) : "get: access with index " + index + " is above chunkcount " + chunkcount + "; sortBound = " + sortBound;
        assert (index * rowdef.objectsize < chunkcache.length);
        if ((chunkcache == null) || (rowdef == null)) return null; // case may appear during shutdown
        if (index >= chunkcount) return null;
        if (index * rowdef.objectsize >= chunkcache.length) return null;
        this.lastTimeRead = System.currentTimeMillis();
        byte[] b = new byte[this.rowdef.width(0)];
        System.arraycopy(chunkcache, index * rowdef.objectsize, b, 0, b.length);
        return b;
    }
    
    public synchronized final kelondroRow.Entry get(int index) {
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < chunkcount) : "get: access with index " + index + " is above chunkcount " + chunkcount + "; sortBound = " + sortBound;
        assert (index * rowdef.objectsize < chunkcache.length);
        if ((chunkcache == null) || (rowdef == null)) return null; // case may appear during shutdown
        if (index >= chunkcount) return null;
        if (index * rowdef.objectsize >= chunkcache.length) return null;
        this.lastTimeRead = System.currentTimeMillis();
        return rowdef.newEntry(chunkcache, index * rowdef.objectsize, true);
    }
    
    public synchronized final void set(int index, kelondroRow.Entry a) {
        assert (index >= 0) : "set: access with index " + index + " is below zero";
        ensureSize(index + 1);
        a.writeToArray(chunkcache, index * rowdef.objectsize);
        if (index >= chunkcount) chunkcount = index + 1;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public final void insertUnique(int index, kelondroRow.Entry a) {
        assert (a != null);

        if (index < chunkcount) {
            // make room
            ensureSize(chunkcount + 1);
            System.arraycopy(chunkcache, rowdef.objectsize * index, chunkcache, rowdef.objectsize * (index + 1), (chunkcount - index) * rowdef.objectsize);
            chunkcount++;
        }
        // insert entry into gap
        set(index, a);
    }
    
    public synchronized void addUnique(kelondroRow.Entry row) {
        byte[] r = row.bytes();
        addUnique(r, 0, r.length);
    }

    public synchronized void addUniqueMultiple(List<kelondroRow.Entry> rows) {
        assert this.sortBound == 0 : "sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount;
        Iterator<kelondroRow.Entry> i = rows.iterator();
        while (i.hasNext()) addUnique(i.next());
    }
    
    public synchronized void add(byte[] a) {
        addUnique(a, 0, a.length);
    }
    
    private final void addUnique(byte[] a, int astart, int alength) {
        assert (a != null);
        assert (astart >= 0) && (astart < a.length) : " astart = " + a;
        assert (!(serverLog.allZero(a, astart, alength))) : "a = " + serverLog.arrayList(a, astart, alength);
        assert (alength > 0);
        assert (astart + alength <= a.length);
        if (bugappearance(a, astart, alength)) {
            System.out.println("*** DEBUG: patched wrong a = " + serverLog.arrayList(a, astart, alength));
            return; // TODO: this is temporary; remote peers may still submit bad entries
        }
        assert (!(bugappearance(a, astart, alength))) : "a = " + serverLog.arrayList(a, astart, alength);
        int l = Math.min(rowdef.objectsize, Math.min(alength, a.length - astart));
        ensureSize(chunkcount + 1);
        System.arraycopy(a, astart, chunkcache, rowdef.objectsize * chunkcount, l);
        chunkcount++;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    private static boolean bugappearance(byte[] a, int astart, int alength) {
        // check strange appearances of '@[B', which is not a b64-value or any other hash fragment
        if (astart + 3 > alength) return false;
        loop: for (int i = astart; i <= alength - 3; i++) {
            if (a[i    ] != 64) continue loop;
            if (a[i + 1] != 91) continue loop;
            if (a[i + 2] != 66) continue loop;
            return true;
        }
        return false;
    }

    public synchronized final void addAllUnique(kelondroRowCollection c) {
        if (c == null) return;
        assert(rowdef.objectsize == c.rowdef.objectsize);
        ensureSize(chunkcount + c.size());
        System.arraycopy(c.chunkcache, 0, chunkcache, rowdef.objectsize * chunkcount, rowdef.objectsize * c.size());
        chunkcount += c.size();
    }
    
    /**
     * This method removes the entry at position p ensuring the order of the remaining
     * entries if specified by keepOrder.
     * Note: Keeping the order is expensive. If you want to remove more than one element in
     * a batch with this method, it'd be better to do the removes without order keeping and doing
     * the sort after all the removes are done.
     * 
     * @param p element at this position will be removed
     * @param keepOrder keep the order of remaining entries
     */
    protected synchronized final void removeRow(int p, boolean keepOrder) {
        assert p >= 0 : "p = " + p;
        assert p < chunkcount : "p = " + p + ", chunkcount = " + chunkcount;
        assert chunkcount > 0 : "chunkcount = " + chunkcount;
        assert sortBound <= chunkcount : "sortBound = " + sortBound + ", chunkcount = " + chunkcount;
        if (keepOrder && (p < sortBound)) {
            // remove by shift (quite expensive for big collections)
            System.arraycopy(
                    chunkcache, (p + 1) * this.rowdef.objectsize,
                    chunkcache, p * this.rowdef.objectsize,
                    (chunkcount - p - 1) * this.rowdef.objectsize);
            sortBound--;
        } else {
            // remove by copying the top-element to the remove position
            if (p != chunkcount - 1) {
                System.arraycopy(
                        chunkcache, (chunkcount - 1) * this.rowdef.objectsize,
                        chunkcache, p * this.rowdef.objectsize,
                        this.rowdef.objectsize);
            }
            // we moved the last element to the remove position: (p+1)st element
            // only the first p elements keep their order (element p is already outside the order)
            if (sortBound >= p) sortBound = p;
        }
        chunkcount--;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public synchronized kelondroRow.Entry removeOne() {
    	// removes the last entry from the collection
        if (chunkcount == 0) return null;
        kelondroRow.Entry r = get(chunkcount - 1);
        if (chunkcount == sortBound) sortBound--;
        chunkcount--;
        this.lastTimeWrote = System.currentTimeMillis();
        return r;
    }
    
    public synchronized void clear() {
        if (this.chunkcache.length == 0) return;
        this.chunkcache = new byte[0];
        this.chunkcount = 0;
        this.sortBound = 0;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public int size() {
        return chunkcount;
    }
    
    public synchronized Iterator<byte[]> keys() {
        // iterates byte[] - type entries
        return new keyIterator();
    }
    
    /**
     * Iterator for kelondroRowCollection.
     * It supports remove() though it doesn't contain the order of the underlying
     * collection during removes.
     *
     */
    public class keyIterator implements Iterator<byte[]> {

        private int p;
        
        public keyIterator() {
            p = 0;
        }
        
        public boolean hasNext() {
            return p < chunkcount;
        }

        public byte[] next() {
            return getKey(p++);
        }
        
        public void remove() {
            p--;
            removeRow(p, false);
        }
    }
    
    public synchronized Iterator<kelondroRow.Entry> rows() {
        // iterates kelondroRow.Entry - type entries
        return new rowIterator();
    }
    
    /**
     * Iterator for kelondroRowCollection.
     * It supports remove() though it doesn't contain the order of the underlying
     * collection during removes.
     *
     */
    public class rowIterator implements Iterator<kelondroRow.Entry> {

        private int p;
        
        public rowIterator() {
            p = 0;
        }
        
        public boolean hasNext() {
            return p < chunkcount;
        }

        public kelondroRow.Entry next() {
            return get(p++);
        }
        
        public void remove() {
            p--;
            removeRow(p, false);
        }
    }
    
    public synchronized void select(Set<String> keys) {
        // removes all entries but the ones given by urlselection
        if ((keys == null) || (keys.size() == 0)) return;
        Iterator<kelondroRow.Entry> i = rows();
        kelondroRow.Entry row;
        while (i.hasNext()) {
            row = (kelondroRow.Entry) i.next();
            if (!(keys.contains(row.getColString(0, null)))) i.remove();
        }
    }
    
    public synchronized final void sort() {
        assert (this.rowdef.objectOrder != null);
        if (this.sortBound == this.chunkcount) return; // this is already sorted
        if (this.chunkcount < isortlimit) {
            isort(0, this.chunkcount, new byte[this.rowdef.objectsize]);
            this.sortBound = this.chunkcount;
            assert this.isSorted();
            return;
        }
        byte[] swapspace = new byte[this.rowdef.objectsize];
        int p = partition(0, this.chunkcount, this.sortBound, swapspace);
        if ((processors > 1) && (this.chunkcount >= 10000)) {
        	// sort this using multi-threading; use one second thread
        	qsortthread qs = new qsortthread(0, p, 0);
            qs.start();
        	qsort(p, this.chunkcount, 0, swapspace);
        	try {qs.join();} catch (InterruptedException e) {e.printStackTrace();}
        } else {
        	qsort(0, p, 0, swapspace);
        	qsort(p, this.chunkcount, 0, swapspace);
        }
        this.sortBound = this.chunkcount;
        //assert this.isSorted();
    }

    private class qsortthread extends Thread {
    	private int sl, sr, sb;
    	public qsortthread(int L, int R, int S) {
    		this.sl = L;
    		this.sr = R;
    		this.sb = S;
    	}
    	public void run() {
    		qsort(sl, sr, sb, new byte[rowdef.objectsize]);
    	}
    }
    
    private final void qsort(int L, int R, int S, byte[] swapspace) {
    	if (R - L < isortlimit) {
            isort(L, R, swapspace);
            return;
        }
    	assert R > L;
		int p = partition(L, R, S, swapspace);
		assert p > L;
		assert p < R;
		qsort(L, p, 0, swapspace);
		qsort(p, R, 0, swapspace);
	}

	private final int partition(int L, int R, int S, byte[] swapspace) {
		// returns {partition-point, new-S}
        assert (L < R - 1);
        assert (R - L >= isortlimit);
        
        int p = L;
        int q = R - 1;
        int pivot = (L + R - 1) / 2;
        int oldpivot = -1;
        byte[] compiledPivot = null;
        if (this.rowdef.objectOrder instanceof kelondroBase64Order) {
        	while (p <= q) {
        		// wenn pivot < S: pivot befindet sich in sortierter Sequenz von L bis S - 1
        		// d.h. alle Werte von L bis pivot sind kleiner als das pivot
        		// zu finden ist ein minimales p <= q so dass chunk[p] >= pivot        		
        		if (oldpivot != pivot) {
        			compiledPivot = compilePivot(pivot);
        			oldpivot = pivot;
        		}
        		if ((pivot < S) && (p < pivot)) {
        			//System.out.println("+++ saved " + (pivot - p) + " comparisments");
        			p = pivot;
        			S = 0;
        		} else {
        			while (comparePivot(compiledPivot, p) ==  1) p++; // chunkAt[p] < pivot
        		}
        		// nun gilt chunkAt[p] >= pivot
        		while (comparePivot(compiledPivot, q) == -1) q--; // chunkAt[q] > pivot
        		if (p <= q) {
        			oldpivot = pivot;
        			pivot = swap(p, q, pivot, swapspace);
        			p++;
        			q--;
        		}
        	}
        } else {
        	while (p <= q) {
        		if ((pivot < S) && (p < pivot)) {
        			p = pivot;
        			S = 0;
        		} else {
        			while (compare(pivot, p) ==  1) p++; // chunkAt[p] < pivot
        		}
        		while (compare(pivot, q) == -1) q--; // chunkAt[q] > pivot
        		if (p <= q) {
        			pivot = swap(p, q, pivot, swapspace);
        			p++;
        			q--;
        		}
        	}
        }
        return p;
    }
	
    private final void isort(int L, int R, byte[] swapspace) {
        for (int i = L + 1; i < R; i++)
            for (int j = i; j > L && compare(j - 1, j) > 0; j--)
                swap(j, j - 1, 0, swapspace);
    }

    private final int swap(int i, int j, int p, byte[] swapspace) {
        if (i == j) return p;
        System.arraycopy(chunkcache, this.rowdef.objectsize * i, swapspace, 0, this.rowdef.objectsize);
        System.arraycopy(chunkcache, this.rowdef.objectsize * j, chunkcache, this.rowdef.objectsize * i, this.rowdef.objectsize);
        System.arraycopy(swapspace, 0, chunkcache, this.rowdef.objectsize * j, this.rowdef.objectsize);
        if (i == p) return j; else if (j == p) return i; else return p;
    }

    public synchronized void uniq() {
        assert (this.rowdef.objectOrder != null);
        // removes double-occurrences of chunks
        // this works only if the collection was ordered with sort before
        // if the collection is large and the number of deletions is also large,
        // then this method may run a long time with 100% CPU load which is caused
        // by the large number of memory movements.
        if (chunkcount < 2) return;
        int i = chunkcount - 2;
        long t = System.currentTimeMillis(); // for time-out
        int d = 0;
        boolean u = true;
        try {
            while (i >= 0) {
                if (compare(i, i + 1) == 0) {
                    removeRow(i + 1, false);
                    d++;
                    if (i + 1 < chunkcount - 1) u = false;
                }
                i--;
                if (System.currentTimeMillis() - t > 10000) {
                    throw new RuntimeException("uniq() time-out at " + i + " (backwards) from " + chunkcount + " elements after " + (System.currentTimeMillis() - t) + " milliseconds; " + d + " deletions so far");
                }
            }
        } catch (RuntimeException e) {
            serverLog.logWarning("kelondroRowCollection", e.getMessage(), e);
        } finally {
            if (!u) this.sort();
        }
    }
    
    public synchronized ArrayList<kelondroRowSet> removeDoubles() {
        assert (this.rowdef.objectOrder != null);
        // removes double-occurrences of chunks
        // in contrast to uniq() this removes also the remaining, non-double entry that had a double-occurrance to the others
        // all removed chunks are returned in an array
        this.sort();
        ArrayList<kelondroRowSet> report = new ArrayList<kelondroRowSet>();
        if (chunkcount < 2) return report;
        int i = chunkcount - 2;
        int d = 0;
        boolean u = true;
        kelondroRowSet collection = new kelondroRowSet(this.rowdef, 2);
        try {
            while (i >= 0) {
                if (compare(i, i + 1) == 0) {
                    collection.addUnique(get(i + 1));
                    removeRow(i + 1, false);
                    d++;
                    if (i + 1 < chunkcount - 1) u = false;
                } else if (collection.size() > 0) {
                    // finish collection of double occurrences
                    collection.addUnique(get(i + 1));
                    removeRow(i + 1, false);
                    d++;
                    if (i + 1 < chunkcount - 1) u = false;
                    collection.trim(false);
                    report.add(collection);
                    collection = new kelondroRowSet(this.rowdef, 2);
                }
                i--;
            }
        } catch (RuntimeException e) {
            serverLog.logWarning("kelondroRowCollection", e.getMessage(), e);
        } finally {
            if (!u) this.sort();
        }
        return report;
    }
    
    public synchronized boolean isSorted() {
        assert (this.rowdef.objectOrder != null);
        if (chunkcount <= 1) return true;
        if (chunkcount != this.sortBound) return false;
        for (int i = 0; i < chunkcount - 1; i++) {
        	//System.out.println("*" + new String(get(i).getColBytes(0)));
        	if (compare(i, i + 1) > 0) {
        		System.out.println("?" + new String(get(i+1).getColBytes(0)));
        		return false;
        	}
        }
        return true;
    }
    
    public synchronized String toString() {
        StringBuffer s = new StringBuffer();
        Iterator<kelondroRow.Entry> i = rows();
        if (i.hasNext()) s.append(i.next().toString());
        while (i.hasNext()) s.append(", " + ((kelondroRow.Entry) i.next()).toString());
        return new String(s);
    }

    private final int compare(int i, int j) {
        assert (chunkcount * this.rowdef.objectsize <= chunkcache.length) : "chunkcount = " + chunkcount + ", objsize = " + this.rowdef.objectsize + ", chunkcache.length = " + chunkcache.length;
        assert (i >= 0) && (i < chunkcount) : "i = " + i + ", chunkcount = " + chunkcount;
        assert (j >= 0) && (j < chunkcount) : "j = " + j + ", chunkcount = " + chunkcount;
        assert (this.rowdef.objectOrder != null);
        if (i == j) return 0;
        assert (this.rowdef.primaryKeyIndex == 0) : "this.sortColumn = " + this.rowdef.primaryKeyIndex;
        int colstart = (this.rowdef.primaryKeyIndex < 0) ? 0 : this.rowdef.colstart[this.rowdef.primaryKeyIndex];
        assert (!bugappearance(chunkcache, i * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength));
        assert (!bugappearance(chunkcache, j * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength));
        int c = this.rowdef.objectOrder.compare(
                chunkcache,
                i * this.rowdef.objectsize + colstart,
                this.rowdef.primaryKeyLength,
                chunkcache,
                j * this.rowdef.objectsize + colstart,
                this.rowdef.primaryKeyLength);
        return c;
    }
    
    protected final byte[] compilePivot(int i) {
        assert (i >= 0) && (i < chunkcount) : "i = " + i + ", chunkcount = " + chunkcount;
        assert (this.rowdef.objectOrder != null);
        assert (this.rowdef.objectOrder instanceof kelondroBase64Order);
        assert (this.rowdef.primaryKeyIndex == 0) : "this.sortColumn = " + this.rowdef.primaryKeyIndex;
        int colstart = (this.rowdef.primaryKeyIndex < 0) ? 0 : this.rowdef.colstart[this.rowdef.primaryKeyIndex];
        assert (!bugappearance(chunkcache, i * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength));
        return ((kelondroBase64Order) this.rowdef.objectOrder).compilePivot(chunkcache, i * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength);
    }
    
    protected final byte[] compilePivot(byte[] a, int astart, int alength) {
        assert (this.rowdef.objectOrder != null);
        assert (this.rowdef.objectOrder instanceof kelondroBase64Order);
        assert (this.rowdef.primaryKeyIndex == 0) : "this.sortColumn = " + this.rowdef.primaryKeyIndex;
        return ((kelondroBase64Order) this.rowdef.objectOrder).compilePivot(a, astart, alength);
    }
    
    protected final int comparePivot(byte[] compiledPivot, int j) {
        assert (chunkcount * this.rowdef.objectsize <= chunkcache.length) : "chunkcount = " + chunkcount + ", objsize = " + this.rowdef.objectsize + ", chunkcache.length = " + chunkcache.length;
        assert (j >= 0) && (j < chunkcount) : "j = " + j + ", chunkcount = " + chunkcount;
        assert (this.rowdef.objectOrder != null);
        assert (this.rowdef.objectOrder instanceof kelondroBase64Order);
        assert (this.rowdef.primaryKeyIndex == 0) : "this.sortColumn = " + this.rowdef.primaryKeyIndex;
        int colstart = (this.rowdef.primaryKeyIndex < 0) ? 0 : this.rowdef.colstart[this.rowdef.primaryKeyIndex];
        assert (!bugappearance(chunkcache, j * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength));
        int c = ((kelondroBase64Order) this.rowdef.objectOrder).comparePivot(
        		compiledPivot,
                chunkcache,
                j * this.rowdef.objectsize + colstart,
                this.rowdef.primaryKeyLength);
        return c;
    }

    protected synchronized int compare(byte[] a, int astart, int alength, int chunknumber) {
        assert (chunknumber < chunkcount);
        int l = Math.min(this.rowdef.primaryKeyLength, Math.min(a.length - astart, alength));
        return rowdef.objectOrder.compare(a, astart, l, chunkcache, chunknumber * this.rowdef.objectsize + ((rowdef.primaryKeyIndex < 0) ? 0 : this.rowdef.colstart[rowdef.primaryKeyIndex]), this.rowdef.primaryKeyLength);
    }
    
    protected synchronized boolean match(byte[] a, int astart, int alength, int chunknumber) {
        if (chunknumber >= chunkcount) return false;
        int i = 0;
        int p = chunknumber * this.rowdef.objectsize + ((rowdef.primaryKeyIndex < 0) ? 0 : this.rowdef.colstart[rowdef.primaryKeyIndex]);
        final int len = Math.min(this.rowdef.primaryKeyLength, Math.min(alength, a.length - astart));
        while (i < len) if (a[astart + i++] != chunkcache[p++]) return false;
        return ((len == this.rowdef.primaryKeyLength) || (chunkcache[len] == 0)) ;
    }
    
    public synchronized void close() {
        chunkcache = null;
    }
    
    private static long d(long a, long b) {
    	if (b == 0) return a; else return a / b;
    }
    
    private static Random random;
    private static String randomHash() {
    	return
    		kelondroBase64Order.enhancedCoder.encodeLong(random.nextLong(), 4) +
    		kelondroBase64Order.enhancedCoder.encodeLong(random.nextLong(), 4) +
    		kelondroBase64Order.enhancedCoder.encodeLong(random.nextLong(), 4);
    }
    
    public static void test(int testsize) {
    	kelondroRow r = new kelondroRow(new kelondroColumn[]{
    			new kelondroColumn("hash", kelondroColumn.celltype_string, kelondroColumn.encoder_bytes, yacySeedDB.commonHashLength, "hash")},
    			kelondroBase64Order.enhancedCoder, 0);
    	
    	kelondroRowCollection a = new kelondroRowCollection(r, testsize);
    	a.add("AAAAAAAAAAAA".getBytes());
    	a.add("BBBBBBBBBBBB".getBytes());
    	a.add("BBBBBBBBBBBB".getBytes());
    	a.add("BBBBBBBBBBBB".getBytes());
    	a.add("CCCCCCCCCCCC".getBytes());
    	ArrayList<kelondroRowSet> del = a.removeDoubles();
    	System.out.println(del + "rows double");
    	Iterator<kelondroRow.Entry> j = a.rows();
    	while (j.hasNext()) System.out.println(new String(j.next().bytes()));
    	
        System.out.println("kelondroRowCollection test with size = " + testsize);
        a = new kelondroRowCollection(r, testsize);
        long t0 = System.currentTimeMillis();
        random = new Random(0);
        for (int i = 0; i < testsize; i++) a.add(randomHash().getBytes());
        random = new Random(0);
        for (int i = 0; i < testsize; i++) a.add(randomHash().getBytes());
        a.sort();
        a.uniq();
        long t1 = System.currentTimeMillis();
        System.out.println("create a   : " + (t1 - t0) + " milliseconds, " + d(testsize, (t1 - t0)) + " entries/millisecond; a.size() = " + a.size());
        
    	kelondroRowCollection c = new kelondroRowCollection(r, testsize);
    	random = new Random(0);
    	t0 = System.currentTimeMillis();
    	for (int i = 0; i < testsize; i++) {
    		c.add(randomHash().getBytes());
    	}
    	t1 = System.currentTimeMillis();
    	System.out.println("create c   : " + (t1 - t0) + " milliseconds, " + d(testsize, (t1 - t0)) + " entries/millisecond");
    	kelondroRowCollection d = new kelondroRowCollection(r, testsize);
    	for (int i = 0; i < testsize; i++) {
    		d.add(c.get(i).getColBytes(0));
    	}
    	long t2 = System.currentTimeMillis();
    	System.out.println("copy c -> d: " + (t2 - t1) + " milliseconds, " + d(testsize, (t2 - t1)) + " entries/millisecond");
    	processors = 1;
    	c.sort();
    	long t3 = System.currentTimeMillis();
    	System.out.println("sort c (1) : " + (t3 - t2) + " milliseconds, " + d(testsize, (t3 - t2)) + " entries/millisecond");
    	processors = 2;
    	d.sort();
    	long t4 = System.currentTimeMillis();
    	System.out.println("sort d (2) : " + (t4 - t3) + " milliseconds, " + d(testsize, (t4 - t3)) + " entries/millisecond");
    	c.uniq();
    	long t5 = System.currentTimeMillis();
    	System.out.println("uniq c     : " + (t5 - t4) + " milliseconds, " + d(testsize, (t5 - t4)) + " entries/millisecond");
    	d.uniq();
    	long t6 = System.currentTimeMillis();
    	System.out.println("uniq d     : " + (t6 - t5) + " milliseconds, " + d(testsize, (t6 - t5)) + " entries/millisecond");
    	random = new Random(0);
    	kelondroRowSet e = new kelondroRowSet(r, testsize);
    	for (int i = 0; i < testsize; i++) {
    		e.put(r.newEntry(randomHash().getBytes()));
    	}
    	long t7 = System.currentTimeMillis();
    	System.out.println("create e   : " + (t7 - t6) + " milliseconds, " + d(testsize, (t7 - t6)) + " entries/millisecond");
    	e.sort();
    	long t8 = System.currentTimeMillis();
    	System.out.println("sort e (2) : " + (t8 - t7) + " milliseconds, " + d(testsize, (t8 - t7)) + " entries/millisecond");
    	e.uniq();
    	long t9 = System.currentTimeMillis();
    	System.out.println("uniq e     : " + (t9 - t8) + " milliseconds, " + d(testsize, (t9 - t8)) + " entries/millisecond");
    	boolean cis = c.isSorted();
    	long t10 = System.currentTimeMillis();
    	System.out.println("c isSorted = " + ((cis) ? "true" : "false") + ": " + (t10 - t9) + " milliseconds");
    	boolean dis = d.isSorted();
    	long t11 = System.currentTimeMillis();
    	System.out.println("d isSorted = " + ((dis) ? "true" : "false") + ": " + (t11 - t10) + " milliseconds");
    	boolean eis = e.isSorted();
    	long t12 = System.currentTimeMillis();
    	System.out.println("e isSorted = " + ((eis) ? "true" : "false") + ": " + (t12 - t11) + " milliseconds");
    	random = new Random(0);
    	boolean allfound = true;
        for (int i = 0; i < testsize; i++) {
            if (e.get(randomHash().getBytes()) == null) {
                allfound = false;
                break;
            }
        }
        long t13 = System.currentTimeMillis();
        System.out.println("e allfound = " + ((allfound) ? "true" : "false") + ": " + (t13 - t12) + " milliseconds");
        boolean noghosts = true;
        for (int i = 0; i < testsize; i++) {
            if (e.get(randomHash().getBytes()) != null) {
                noghosts = false;
                break;
            }
        }
        long t14 = System.currentTimeMillis();
        System.out.println("e noghosts = " + ((noghosts) ? "true" : "false") + ": " + (t14 - t13) + " milliseconds");
        System.out.println("Result size: c = " + c.size() + ", d = " + d.size() + ", e = " + e.size());
    	System.out.println();
    }
    
    public static void main(String[] args) {
    	//test(1000);
    	test(10000);
    	test(100000);
    	//test(1000000);
    	
    	/*   	
        System.out.println(new java.util.Date(10957 * day));
        System.out.println(new java.util.Date(0));
        System.out.println(daysSince2000(System.currentTimeMillis()));
        */
    }
}