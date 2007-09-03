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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;

public class kelondroRowCollection {

    public static final double growfactor = 1.4;
    
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
        this.chunkcache = new byte[objectCount * rowdef.objectsize()];
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
        kelondroOrder oldOrder = null;
        if ((sortOrderKey == null) || (sortOrderKey.equals("__"))) {
            oldOrder = null;
        } else {
            oldOrder = kelondroNaturalOrder.bySignature(sortOrderKey);
            if (oldOrder == null) oldOrder = kelondroBase64Order.bySignature(sortOrderKey);
        }
        if ((rowdef.objectOrder != null) && (oldOrder != null) && (!(rowdef.objectOrder.signature().equals(oldOrder.signature()))))
            throw new kelondroException("old collection order does not match with new order");
        if (rowdef.primaryKey != (int) exportedCollection.getColLong(exp_order_col))
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
        entry.setCol(exp_order_col, this.rowdef.primaryKey);
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
        int needed = elements * rowdef.objectsize();
        if (chunkcache.length >= needed) return;
        byte[] newChunkcache = new byte[(int) (needed * growfactor)]; // increase space
        System.arraycopy(chunkcache, 0, newChunkcache, 0, chunkcache.length);
        chunkcache = newChunkcache;
        newChunkcache = null;
    }
    
    /*
    private static final Object[] arraydepot = new Object[]{new byte[0]};
    
    private final void ensureSize(int elements) {
        int needed = elements * rowdef.objectsize();
        if (chunkcache.length >= needed) return;
        long neededRAM = (long) (needed * growfactor);
        long availableRAM = serverMemory.available();
        //if ((safemode) && (neededRAM > availableRAM)) throw new kelondroMemoryProtectionException("rowCollection temporary chunkcache", neededRAM, availableRAM);

        if (neededRAM > availableRAM) {
        	// go into safe mode: use the arraydepot
        	synchronized (arraydepot) {
        		if (((byte[]) arraydepot[0]).length >= neededRAM) {
        			System.out.println("ensureSize case 1");
        			// use the depot to increase the chunkcache
        			byte[] newChunkcache = (byte[]) arraydepot[0];
                	System.arraycopy(chunkcache, 0, newChunkcache, 0, chunkcache.length);
                	// safe the chunkcache for later use in arraydepot
                	arraydepot[0] = chunkcache;
                	chunkcache = newChunkcache;
                	newChunkcache = null;
        		} else {
        			System.out.println("ensureSize case 2");
        			// this is the critical part: we need more RAM.
        			// do a buffering using the arraydepot
        			byte[] buffer0 = (byte[]) arraydepot[0];
        			byte[] buffer1 = new byte[chunkcache.length - buffer0.length];
                	// first copy the previous chunkcache to the two buffers
        			System.arraycopy(chunkcache, 0, buffer0, 0, buffer0.length);
        			System.arraycopy(chunkcache, buffer0.length, buffer1, 0, buffer1.length);
        			// then free the previous chunkcache and replace it with a new array at target size
        			chunkcache = null; // hand this over to GC
        			chunkcache = new byte[(int) neededRAM];
        			System.arraycopy(buffer0, 0, chunkcache, 0, buffer0.length);
        			System.arraycopy(buffer1, 0, chunkcache, buffer0.length, buffer1.length);
        			// then move the bigger buffer into the arraydepot
        			if (buffer0.length > buffer1.length) {
        				arraydepot[0] = buffer0;
        			} else {
        				arraydepot[1] = buffer1;
        			}
        			buffer0 = null;
    				buffer1 = null;
        		}
        	}
        } else {
        	// there is enough memory available
        	byte[] newChunkcache = new byte[(int) neededRAM]; // increase space
        	System.arraycopy(chunkcache, 0, newChunkcache, 0, chunkcache.length);
        	// safe the chunkcache for later use in arraydepot
        	synchronized (arraydepot) {
        		if (((byte[]) arraydepot[0]).length < chunkcache.length) {
        			System.out.println("ensureSize case 0");
        			arraydepot[0] = chunkcache;
        		}
        	}
        	chunkcache = newChunkcache;
        	newChunkcache = null;
        }
    }
    */
    
    public final long memoryNeededForGrow() {
        return (long) ((((long) (chunkcount + 1)) * ((long) rowdef.objectsize())) * growfactor);
    }
    
    public synchronized void trim(boolean plusGrowFactor) {
        if (chunkcache.length == 0)
            return;
        int needed = chunkcount * rowdef.objectsize();
        if (plusGrowFactor)
            needed = (int) (needed * growfactor);
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
    
    public synchronized final kelondroRow.Entry get(int index) {
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < chunkcount) : "get: access with index " + index + " is above chunkcount " + chunkcount + "; sortBound = " + sortBound;
        assert (index * rowdef.objectsize < chunkcache.length);
        if ((chunkcache == null) || (rowdef == null)) return null; // case may appear during shutdown
        if (index >= chunkcount) return null;
        if (index * rowdef.objectsize() >= chunkcache.length) return null;
        this.lastTimeRead = System.currentTimeMillis();
        return rowdef.newEntry(chunkcache, index * rowdef.objectsize(), true);
    }
    
    public synchronized final void set(int index, kelondroRow.Entry a) {
        assert (index >= 0) : "set: access with index " + index + " is below zero";
        ensureSize(index + 1);
        a.writeToArray(chunkcache, index * rowdef.objectsize());
        if (index >= chunkcount) chunkcount = index + 1;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public final void insertUnique(int index, kelondroRow.Entry a) {
        assert (a != null);

        if (index < chunkcount) {
            // make room
            ensureSize(chunkcount + 1);
            System.arraycopy(chunkcache, rowdef.objectsize() * index, chunkcache, rowdef.objectsize() * (index + 1), (chunkcount - index) * rowdef.objectsize());
            chunkcount++;
        }
        // insert entry into gap
        set(index, a);
    }
    
    
    public synchronized void addUnique(kelondroRow.Entry row) {
        byte[] r = row.bytes();
        addUnique(r, 0, r.length);
    }

    public synchronized void addUniqueMultiple(List rows) {
        assert this.sortBound == 0 : "sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount;
        Iterator i = rows.iterator();
        while (i.hasNext()) addUnique((kelondroRow.Entry) i.next());
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
        int l = Math.min(rowdef.objectsize(), Math.min(alength, a.length - astart));
        ensureSize(chunkcount + 1);
        System.arraycopy(a, astart, chunkcache, rowdef.objectsize() * chunkcount, l);
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
        assert(rowdef.objectsize() == c.rowdef.objectsize());
        ensureSize(chunkcount + c.size());
        System.arraycopy(c.chunkcache, 0, chunkcache, rowdef.objectsize() * chunkcount, rowdef.objectsize() * c.size());
        chunkcount += c.size();
    }
    
    protected synchronized final void removeRow(int p) {
        assert p >= 0 : "p = " + p;
        assert p < chunkcount : "p = " + p + ", chunkcount = " + chunkcount;
        assert chunkcount > 0 : "chunkcount = " + chunkcount;
        assert sortBound <= chunkcount : "sortBound = " + sortBound + ", chunkcount = " + chunkcount;
        if (p < sortBound) {
        	// remove by shift
        	System.arraycopy(
        			chunkcache, (p + 1) * this.rowdef.objectsize(),
                    chunkcache, p * this.rowdef.objectsize(),
                    (chunkcount - p - 1) * this.rowdef.objectsize());
            sortBound--;
        } else {
        	// remove by copying the top-element to the remove position
        	if (p != chunkcount - 1) {
        		System.arraycopy(
        			chunkcache, (chunkcount - 1) * this.rowdef.objectsize(),
        			chunkcache, p * this.rowdef.objectsize(),
        			this.rowdef.objectsize());
        	}
        }
        chunkcount--;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public synchronized kelondroRow.Entry removeOne() {
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
    
    public synchronized Iterator rows() {
        // iterates kelondroRow.Entry - type entries
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
            removeRow(p);
        }
    }
    
    public synchronized void select(Set keys) {
        // removes all entries but the ones given by urlselection
        if ((keys == null) || (keys.size() == 0)) return;
        Iterator i = rows();
        kelondroRow.Entry row;
        while (i.hasNext()) {
            row = (kelondroRow.Entry) i.next();
            if (!(keys.contains(row.getColString(0, null)))) i.remove();
        }
    }
    
    public synchronized final void sort() {
        assert (this.rowdef.objectOrder != null);
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

    private final int swap(int i, int j, int p) {
        if (i == j) return p;
        if ((this.chunkcount + 1) * this.rowdef.objectsize() < this.chunkcache.length) {
            // there is space in the chunkcache that we can use as buffer
            System.arraycopy(chunkcache, this.rowdef.objectsize() * i, chunkcache, chunkcache.length - this.rowdef.objectsize(), this.rowdef.objectsize());
            System.arraycopy(chunkcache, this.rowdef.objectsize() * j, chunkcache, this.rowdef.objectsize() * i, this.rowdef.objectsize());
            System.arraycopy(chunkcache, chunkcache.length - this.rowdef.objectsize(), chunkcache, this.rowdef.objectsize() * j, this.rowdef.objectsize());
        } else {
            // allocate a chunk to use as buffer
            byte[] a = new byte[this.rowdef.objectsize()];
            System.arraycopy(chunkcache, this.rowdef.objectsize() * i, a, 0, this.rowdef.objectsize());
            System.arraycopy(chunkcache, this.rowdef.objectsize() * j, chunkcache, this.rowdef.objectsize() * i, this.rowdef.objectsize());
            System.arraycopy(a, 0, chunkcache, this.rowdef.objectsize() * j, this.rowdef.objectsize());
        }
        if (i == p) return j; else if (j == p) return i; else return p;
    }

    public synchronized void uniq(long maxtime) {
        assert (this.rowdef.objectOrder != null);
        // removes double-occurrences of chunks
        // this works only if the collection was ordered with sort before
        // if the collection is large and the number of deletions is also large,
        // then this method may run a long time with 100% CPU load which is caused
        // by the large number of memory movements. Therefore it is possible
        // to assign a runtime limitation
        long start = System.currentTimeMillis();
        if (chunkcount <= 1) return;
        int i = 0;
        while (i < chunkcount - 1) {
        	//System.out.println("ENTRY0: " + serverLog.arrayList(chunkcache, rowdef.objectsize*i, rowdef.objectsize));
        	//System.out.println("ENTRY1: " + serverLog.arrayList(chunkcache, rowdef.objectsize*(i+1), rowdef.objectsize));
            if (compare(i, i + 1) == 0) {
                removeRow(i); // this decreases the chunkcount
            } else {
                i++;
            }
            if ((maxtime > 0) && (start + maxtime < System.currentTimeMillis())) break;
        }
    }
    
    public synchronized String toString() {
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
        assert (this.rowdef.objectOrder != null);
        if (i == j) return 0;
        assert (this.rowdef.primaryKey == 0) : "this.sortColumn = " + this.rowdef.primaryKey;
        int keylength = this.rowdef.width(this.rowdef.primaryKey);
        int colstart  = this.rowdef.colstart[this.rowdef.primaryKey];
        if (bugappearance(chunkcache, i * this.rowdef.objectsize() + colstart, keylength)) throw new kelondroException("bugappearance i");
        if (bugappearance(chunkcache, j * this.rowdef.objectsize() + colstart, keylength)) throw new kelondroException("bugappearance j");
        int c = this.rowdef.objectOrder.compare(
                chunkcache,
                i * this.rowdef.objectsize() + colstart,
                keylength,
                chunkcache,
                j * this.rowdef.objectsize() + colstart,
                keylength);
        return c;
    }

    protected synchronized int compare(byte[] a, int astart, int alength, int chunknumber) {
        assert (chunknumber < chunkcount);
        int l = Math.min(this.rowdef.width(rowdef.primaryKey), Math.min(a.length - astart, alength));
        return rowdef.objectOrder.compare(a, astart, l, chunkcache, chunknumber * this.rowdef.objectsize() + this.rowdef.colstart[rowdef.primaryKey], this.rowdef.width(rowdef.primaryKey));
    }
    
    protected synchronized boolean match(byte[] a, int astart, int alength, int chunknumber) {
        if (chunknumber >= chunkcount) return false;
        int i = 0;
        int p = chunknumber * this.rowdef.objectsize() + this.rowdef.colstart[rowdef.primaryKey];
        final int len = Math.min(this.rowdef.width(rowdef.primaryKey), Math.min(alength, a.length - astart));
        while (i < len) if (a[astart + i++] != chunkcache[p++]) return false;
        return ((len == this.rowdef.width(rowdef.primaryKey)) || (chunkcache[len] == 0)) ;
    }
    
    public synchronized void close() {
        chunkcache = null;
    }
    
    public static void main(String[] args) {
        System.out.println(new java.util.Date(10957 * day));
        System.out.println(new java.util.Date(0));
        System.out.println(daysSince2000(System.currentTimeMillis()));
    }
}
