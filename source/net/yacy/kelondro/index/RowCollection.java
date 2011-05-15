// RowCollection.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.01.2006 on http://www.anomic.de
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.index;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.NamePrefixThreadFactory;
import net.yacy.kelondro.util.kelondroException;


public class RowCollection implements Iterable<Row.Entry>, Cloneable {

    public  static final long growfactorLarge100 = 140L;
    public  static final long growfactorSmall100 = 120L;
    private static final int isortlimit = 20;
    private static final int availableCPU = Runtime.getRuntime().availableProcessors();
    
    private static final int exp_chunkcount  = 0;
    private static final int exp_last_read   = 1;
    private static final int exp_last_wrote  = 2;
    private static final int exp_order_type  = 3;
    private static final int exp_order_bound = 4;
    private static final int exp_collection  = 5;
    
    public static final ExecutorService sortingthreadexecutor =
        (availableCPU > 1)
        ? new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Integer.MAX_VALUE,
                120L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new NamePrefixThreadFactory("sorting"),
                new ThreadPoolExecutor.CallerRunsPolicy())
        : null;
    
    private static final ExecutorService partitionthreadexecutor =
        (availableCPU > 1)
        ? new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Integer.MAX_VALUE,
                120L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new NamePrefixThreadFactory("partition"),
                new ThreadPoolExecutor.CallerRunsPolicy())
        : null;
    
    protected final Row    rowdef;
    protected       byte[] chunkcache;
    protected       int    chunkcount;
    protected       int    sortBound;
    protected       long   lastTimeWrote;
    
    protected RowCollection(final RowCollection rc) {
        this.rowdef = rc.rowdef;
        this.chunkcache = rc.chunkcache;
        this.chunkcount = rc.chunkcount;
        this.sortBound = rc.sortBound;
        this.lastTimeWrote = rc.lastTimeWrote;
    }
    
    protected RowCollection(final Row rowdef) {
        this.rowdef = rowdef;
        this.sortBound = 0;
        this.lastTimeWrote = System.currentTimeMillis();
        this.chunkcache = new byte[0];
        this.chunkcount = 0;
    }
     
    public RowCollection(final Row rowdef, final int objectCount) throws RowSpaceExceededException {
        this(rowdef);
        ensureSize(objectCount);
    }
     
    protected RowCollection(final Row rowdef, final int objectCount, final byte[] cache, final int sortBound) {
        this.rowdef = rowdef;
        this.chunkcache = cache;
        this.chunkcount = objectCount;
        this.sortBound = sortBound;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    protected RowCollection(final Row rowdef, final Row.Entry exportedCollectionRowEnvironment) {
        final int chunkcachelength = exportedCollectionRowEnvironment.cellwidth(1) - (int) exportOverheadSize;
        final Row.Entry exportedCollection = exportRow(chunkcachelength).newEntry(exportedCollectionRowEnvironment, 1);
        
        this.rowdef = rowdef;
        this.chunkcount = (int) exportedCollection.getColLong(exp_chunkcount);
        if ((this.chunkcount > chunkcachelength / rowdef.objectsize)) {
            Log.logWarning("RowCollection", "corrected wrong chunkcount; chunkcount = " + this.chunkcount + ", chunkcachelength = " + chunkcachelength + ", rowdef.objectsize = " + rowdef.objectsize);
            this.chunkcount = chunkcachelength / rowdef.objectsize; // patch problem
        }
        this.lastTimeWrote = (exportedCollection.getColLong(exp_last_wrote) + 10957) * day;
        final String sortOrderKey = exportedCollection.getColString(exp_order_type);
        ByteOrder oldOrder = null;
        if ((sortOrderKey == null) || (sortOrderKey.equals("__"))) {
            oldOrder = null;
        } else {
            oldOrder = NaturalOrder.bySignature(sortOrderKey);
            if (oldOrder == null) oldOrder = Base64Order.bySignature(sortOrderKey);
        }
        if ((rowdef.objectOrder != null) && (oldOrder != null) && (!(rowdef.objectOrder.signature().equals(oldOrder.signature()))))
            throw new kelondroException("old collection order does not match with new order; objectOrder.signature = " + rowdef.objectOrder.signature() + ", oldOrder.signature = " + oldOrder.signature());
        this.sortBound = (int) exportedCollection.getColLong(exp_order_bound);
        if (sortBound > chunkcount) {
            Log.logWarning("RowCollection", "corrected wrong sortBound; sortBound = " + sortBound + ", chunkcount = " + chunkcount);
            this.sortBound = chunkcount;
        }
        this.chunkcache = exportedCollection.getColBytes(exp_collection, false);        
    }
    
    protected RowCollection(Row rowdef, byte[] chunkcache, int chunkcount, int sortBound, long lastTimeWrote) {
        this.rowdef = rowdef;
        this.chunkcache = new byte[chunkcache.length];
        System.arraycopy(chunkcache, 0, this.chunkcache, 0, chunkcache.length);
        this.chunkcount = chunkcount;
        this.sortBound = sortBound;
        this.lastTimeWrote = lastTimeWrote;
    }
    
    public RowCollection clone() {
        return new RowCollection(this.rowdef, this.chunkcache, this.chunkcount, this.sortBound, this.lastTimeWrote);
    }

	public void reset() {
		this.chunkcache = new byte[0];
        this.chunkcount = 0;
        this.sortBound = 0;
	}
	
	/**
	 * calculate the memory that the structure occupies in ram
	 * @return number of bytes in use
	 */
	public long mem() {
	    return this.chunkcache.length;
	}
   
    private static final Row exportMeasureRow = exportRow(0 /* no relevance */);

    public static final int sizeOfExportedCollectionRows(final Row.Entry exportedCollectionRowEnvironment, final int columnInEnvironment) {
    	final Row.Entry exportedCollectionEntry = exportMeasureRow.newEntry(exportedCollectionRowEnvironment, columnInEnvironment);
    	final int chunkcount = (int) exportedCollectionEntry.getColLong(exp_chunkcount);
        return chunkcount;
    }
    
    private static final long day = 1000 * 60 * 60 * 24;
    
    private static int daysSince2000(final long time) {
        return (int) (time / day) - 10957;
    }
    
    private static Column exportColumn0, exportColumn1, exportColumn2, exportColumn3, exportColumn4;

    protected static final long exportOverheadSize = 14;

    private static Row exportRow(final int chunkcachelength) {
        /*
        return new Row(
                "int size-4 {b256}," +
                "short lastread-2 {b256}," + // as daysSince2000
                "short lastwrote-2 {b256}," + // as daysSince2000
                "byte[] orderkey-2," +
                "int orderbound-4 {b256}," +
                "byte[] collection-" + chunkcachelength,
                NaturalOrder.naturalOrder
                );
         */
        
        if (exportColumn0 == null) exportColumn0 = new Column("int size-4 {b256}");
        if (exportColumn1 == null) exportColumn1 = new Column("short lastread-2 {b256}");
        if (exportColumn2 == null) exportColumn2 = new Column("short lastwrote-2 {b256}");
        if (exportColumn3 == null) exportColumn3 = new Column("byte[] orderkey-2");
        if (exportColumn4 == null) exportColumn4 = new Column("int orderbound-4 {b256}");
        /*
         * because of a strange bug these objects cannot be initialized as normal
         * static final. If I try that, they are not initialized and are assigned null. why?
         */
        Row er = new Row(new Column[]{
                    exportColumn0, exportColumn1, exportColumn2, exportColumn3, exportColumn4,
                    new Column("byte[] collection-" + chunkcachelength)
                },
                NaturalOrder.naturalOrder
        );
        assert er.objectsize == chunkcachelength +exportOverheadSize;
        return er;
    }
    
    public synchronized byte[] exportCollection() {
        // returns null if the collection is empty
        sort(); // experimental; supervise CPU load
        //uniq();
        //trim();
        assert this.sortBound == this.chunkcount; // on case the collection is sorted
        assert this.size() * this.rowdef.objectsize <= this.chunkcache.length : "this.size() = " + this.size() + ", objectsize = " + this.rowdef.objectsize + ", chunkcache.length = " + this.chunkcache.length;
        final Row row = exportRow(this.size() * this.rowdef.objectsize);
        final Row.Entry entry = row.newEntry();
        assert (sortBound <= chunkcount) : "sortBound = " + sortBound + ", chunkcount = " + chunkcount;
        assert (this.chunkcount <= chunkcache.length / rowdef.objectsize) : "chunkcount = " + this.chunkcount + ", chunkcache.length = " + chunkcache.length + ", rowdef.objectsize = " + rowdef.objectsize;
        entry.setCol(exp_chunkcount, this.chunkcount);
        entry.setCol(exp_last_read, daysSince2000(System.currentTimeMillis()));
        entry.setCol(exp_last_wrote, daysSince2000(this.lastTimeWrote));
        entry.setCol(exp_order_type, (this.rowdef.objectOrder == null) ? UTF8.getBytes("__") : UTF8.getBytes(this.rowdef.objectOrder.signature()));
        entry.setCol(exp_order_bound, this.sortBound);
        entry.setCol(exp_collection, this.chunkcache);
        return entry.bytes();
    }
    
    public void saveCollection(final File file) throws IOException {
        FileUtils.copy(exportCollection(), file);
    }

    public Row row() {
        return this.rowdef;
    }
    
    private final long neededSpaceForEnsuredSize(final int elements, final boolean forcegc) {
        assert elements > 0 : "elements = " + elements;
        final long needed = elements * rowdef.objectsize;
        if (chunkcache.length >= needed) return 0;
        assert needed > 0 : "needed = " + needed;
        long allocram = needed * growfactorLarge100 / 100L;
        allocram -= allocram % rowdef.objectsize;
        assert allocram > 0 : "elements = " + elements + ", new = " + allocram;
        if (allocram <= Integer.MAX_VALUE && MemoryControl.request(allocram, false)) return allocram;
        allocram = needed * growfactorSmall100 / 100L;
        allocram -= allocram % rowdef.objectsize;
        assert allocram >= 0 : "elements = " + elements + ", new = " + allocram;
        if (allocram <= Integer.MAX_VALUE && MemoryControl.request(allocram, forcegc)) return allocram;
        return needed;
    }
    
    private final void ensureSize(final int elements) throws RowSpaceExceededException {
        if (elements == 0) return;
        final long allocram = neededSpaceForEnsuredSize(elements, true);
        if (allocram == 0) return;
        assert chunkcache.length < elements * rowdef.objectsize : "wrong alloc computation (1): elements * rowdef.objectsize = " + (elements * rowdef.objectsize) + ", chunkcache.length = " + chunkcache.length;
        assert allocram > chunkcache.length : "wrong alloc computation (2): allocram = " + allocram + ", chunkcache.length = " + chunkcache.length;
        if (allocram > Integer.MAX_VALUE || !MemoryControl.request(allocram + 32, true))
        	throw new RowSpaceExceededException(allocram + 32, "RowCollection grow");
        try {
            final byte[] newChunkcache = new byte[(int) allocram]; // increase space
            System.arraycopy(chunkcache, 0, newChunkcache, 0, chunkcache.length);
            chunkcache = newChunkcache;
        } catch (OutOfMemoryError e) {
        	// lets try again after a forced gc()
        	System.gc();
        	try {
                final byte[] newChunkcache = new byte[(int) allocram]; // increase space
                System.arraycopy(chunkcache, 0, newChunkcache, 0, chunkcache.length);
                chunkcache = newChunkcache;
            } catch (OutOfMemoryError ee) {
                throw new RowSpaceExceededException(allocram, "RowCollection grow after OutOfMemoryError " + ee.getMessage());
            }
        }
    }
    
    /**
     * compute the needed memory in case of a cache extension. That is, if the cache is full and must
     * be copied into a new cache which is larger. In such a case the Collection needs more than the double size
     * than is necessary to store the data. This method computes the extra memory that is needed to perform this task.
     * @return
     */
    protected final long memoryNeededForGrow() {
        return neededSpaceForEnsuredSize(chunkcount + 1, false);
    }
    
    protected synchronized void trim() {
        if (chunkcache.length == 0) return;
        long needed = chunkcount * rowdef.objectsize;
        assert needed <= chunkcache.length;
        if (needed >= chunkcache.length)
            return; // in case that the growfactor causes that the cache would
                    // grow instead of shrink, simply ignore the growfactor
        if (MemoryControl.available() + 1000 < needed)
            return; // if the swap buffer is not available, we must give up.
                    // This is not critical. Otherwise we provoke a serious
                    // problem with OOM
        final byte[] newChunkcache = new byte[(int) needed];
        System.arraycopy(chunkcache, 0, newChunkcache, 0, Math.min(chunkcache.length, newChunkcache.length));
        chunkcache = newChunkcache;
    }
    
    public final long lastWrote() {
        return lastTimeWrote;
    }
    
    protected synchronized final byte[] getKey(final int index) {
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < chunkcount) : "get: access with index " + index + " is above chunkcount " + chunkcount + "; sortBound = " + sortBound;
        assert (index * rowdef.objectsize < chunkcache.length);
        if ((chunkcache == null) || (rowdef == null)) return null; // case may appear during shutdown
        if (index >= chunkcount) return null;
        if ((index + 1) * rowdef.objectsize > chunkcache.length) return null; // the whole chunk does not fit into the chunkcache
        final byte[] b = new byte[this.rowdef.primaryKeyLength];
        System.arraycopy(chunkcache, index * rowdef.objectsize, b, 0, b.length);
        return b;
    }
    
    public synchronized final Row.Entry get(final int index, final boolean clone) {
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < chunkcount) : "get: access with index " + index + " is above chunkcount " + chunkcount + "; sortBound = " + sortBound;
        assert (chunkcache != null && index * rowdef.objectsize < chunkcache.length);
        assert sortBound <= chunkcount : "sortBound = " + sortBound + ", chunkcount = " + chunkcount;
        if ((chunkcache == null) || (rowdef == null)) return null; // case may appear during shutdown
        Row.Entry entry;
        final int addr = index * rowdef.objectsize;
        if (index >= chunkcount) return null;
        if (addr + rowdef.objectsize > chunkcache.length) return null; // the whole chunk does not fit into the chunkcache
        entry = rowdef.newEntry(chunkcache, addr, clone);
        return entry;
    }
    
    public synchronized final void set(final int index, final Row.Entry a) throws RowSpaceExceededException {
        assert (index >= 0) : "set: access with index " + index + " is below zero";
        ensureSize(index + 1);
        byte[] column = a.bytes();
        assert a.cellwidth(0) == this.rowdef.primaryKeyLength;
        assert column.length >= this.rowdef.primaryKeyLength;
        final boolean sameKey = match(column, 0, index);
        //if (sameKey) System.out.print("$");
        a.writeToArray(chunkcache, index * rowdef.objectsize);
        if (index >= this.chunkcount) this.chunkcount = index + 1;
        if (!sameKey && index < this.sortBound) this.sortBound = index;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public final void insertUnique(final int index, final Row.Entry a) throws RowSpaceExceededException {
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
    
    public synchronized void addUnique(final Row.Entry row) throws RowSpaceExceededException {
        final byte[] r = row.bytes();
        addUnique(r, 0, r.length);
    }

    public synchronized void addUnique(final List<Row.Entry> rows) throws RowSpaceExceededException {
        assert this.sortBound == 0 : "sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount;
        final Iterator<Row.Entry> i = rows.iterator();
        while (i.hasNext()) addUnique(i.next());
    }
    
    public synchronized void add(final byte[] a) throws RowSpaceExceededException {
        assert a.length == this.rowdef.objectsize : "a.length = " + a.length + ", objectsize = " + this.rowdef.objectsize;
        addUnique(a, 0, a.length);
    }
    
    private final void addUnique(final byte[] a, final int astart, final int alength) throws RowSpaceExceededException {
        assert (a != null);
        assert (astart >= 0) && (astart < a.length) : " astart = " + astart;
        assert (!(Log.allZero(a, astart, alength))) : "a = " + NaturalOrder.arrayList(a, astart, alength);
        assert (alength > 0);
        assert (astart + alength <= a.length);
        assert alength == rowdef.objectsize : "alength =" + alength + ", rowdef.objectsize = " + rowdef.objectsize;
        final int l = Math.min(rowdef.objectsize, Math.min(alength, a.length - astart));
        ensureSize(chunkcount + 1);
        System.arraycopy(a, astart, chunkcache, rowdef.objectsize * chunkcount, l);
        chunkcount++;
        // if possible, increase the sortbound value to suppress unnecessary sorting
        if (this.chunkcount == 1) {
            assert this.sortBound == 0;
            this.sortBound = 1;
        } else if (
                this.sortBound + 1 == chunkcount &&
                this.rowdef.objectOrder.compare(chunkcache, rowdef.objectsize * (chunkcount - 2),
                                                chunkcache, rowdef.objectsize * (chunkcount - 1), rowdef.primaryKeyLength) == -1) {
            this.sortBound = chunkcount;
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    protected final void addSorted(final byte[] a, final int astart, final int alength) throws RowSpaceExceededException {
        assert (a != null);
        assert (astart >= 0) && (astart < a.length) : " astart = " + astart;
        assert (!(Log.allZero(a, astart, alength))) : "a = " + NaturalOrder.arrayList(a, astart, alength);
        assert (alength > 0);
        assert (astart + alength <= a.length);
        assert alength == rowdef.objectsize : "alength =" + alength + ", rowdef.objectsize = " + rowdef.objectsize;
        final int l = Math.min(rowdef.objectsize, Math.min(alength, a.length - astart));
        ensureSize(chunkcount + 1);
        System.arraycopy(a, astart, chunkcache, rowdef.objectsize * chunkcount, l);
        this.chunkcount++;
        this.sortBound = this.chunkcount;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public synchronized final void addAllUnique(final RowCollection c) throws RowSpaceExceededException {
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
    public synchronized final void removeRow(final int p, final boolean keepOrder) {
        assert p >= 0 : "p = " + p;
        assert p < chunkcount : "p = " + p + ", chunkcount = " + chunkcount;
        assert chunkcount > 0 : "chunkcount = " + chunkcount;
        assert sortBound <= chunkcount : "sortBound = " + sortBound + ", chunkcount = " + chunkcount;
        if (keepOrder && (p < sortBound)) {
            // remove by shift (quite expensive for big collections)
            final int addr = p * this.rowdef.objectsize;
            System.arraycopy(
                    chunkcache, addr + this.rowdef.objectsize,
                    chunkcache, addr,
                    (chunkcount - p - 1) * this.rowdef.objectsize);
            sortBound--; // this is only correct if p < sortBound, but this was already checked above
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
            if (sortBound > p) sortBound = p;
        }
        chunkcount--;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    /**
     * removes the last entry from the collection
     * @return
     */
    public synchronized Row.Entry removeOne() {
        if (chunkcount == 0) return null;
        final Row.Entry r = get(chunkcount - 1, true);
        if (chunkcount == sortBound) sortBound--;
        chunkcount--;
        this.lastTimeWrote = System.currentTimeMillis();
        return r;
    }
    
    public synchronized List<Row.Entry> top(int count) {
        ArrayList<Row.Entry> list = new ArrayList<Row.Entry>();
        if (chunkcount == 0) return list;
        Row.Entry entry;
        int cursor = chunkcount - 1;
        while (count > 0 && cursor >= 0) {
            entry = get(cursor, true);
            list.add(entry);
            count--;
            cursor--;
        }
        return list;
    }
    
    public synchronized byte[] smallestKey() {
        if (chunkcount == 0) return null;
        this.sort();
        final Row.Entry r = get(0, false);
        final byte[] b = r.getPrimaryKeyBytes();
        return b;
    }
    
    public synchronized byte[] largestKey() {
        if (chunkcount == 0) return null;
        this.sort();
        final Row.Entry r = get(chunkcount - 1, false);
        final byte[] b = r.getPrimaryKeyBytes();
        return b;
    }
    
    public synchronized void clear() {
        if (this.chunkcache.length == 0) return;
        this.chunkcache = new byte[0];
        this.chunkcount = 0;
        this.sortBound = 0;
        this.lastTimeWrote = System.currentTimeMillis();
    }
    
    public int size() {
        return this.chunkcount;
    }
    
    public boolean isEmpty() {
        return this.chunkcount == 0;
    }
    
    public int sorted() {
        return this.sortBound;
    }
    
    public synchronized Iterator<byte[]> keys(final boolean keepOrderWhenRemoving) {
        // iterates byte[] - type entries
        return new keyIterator(keepOrderWhenRemoving);
    }
    
    /**
     * Iterator for kelondroRowCollection.
     * It supports remove() though it doesn't contain the order of the underlying
     * collection during removes.
     *
     */
    private class keyIterator implements Iterator<byte[]> {

        private int p;
        private final boolean keepOrderWhenRemoving;
        
        private keyIterator(final boolean keepOrderWhenRemoving) {
            this.p = 0;
            this.keepOrderWhenRemoving = keepOrderWhenRemoving;
        }
        
        public boolean hasNext() {
            return p < chunkcount;
        }

        public byte[] next() {
            return getKey(p++);
        }
        
        public void remove() {
            p--;
            removeRow(p, keepOrderWhenRemoving);
        }
    }    

    /**
     * return an iterator for the row entries in this object
     */
    public Iterator<Row.Entry> iterator() {
        // iterates kelondroRow.Entry - type entries
        return new rowIterator();
    }
    
    /**
     * Iterator for kelondroRowCollection.
     * It supports remove() and keeps the order of the underlying
     * collection during removes.
     */
    private class rowIterator implements Iterator<Row.Entry> {

        private int p;
        
        public rowIterator() {
            p = 0;
        }
        
        public boolean hasNext() {
            return p < chunkcount;
        }

        public Row.Entry next() {
            return get(p++, true);
        }
        
        public void remove() {
            p--;
            removeRow(p, true);
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
        final byte[] swapspace = new byte[this.rowdef.objectsize];
        final int p = partition(0, this.chunkcount, this.sortBound, swapspace);
        if (sortingthreadexecutor != null &&
            !sortingthreadexecutor.isShutdown() &&
            availableCPU > 1 && 
            this.chunkcount > 8000 &&
            p > isortlimit * 5 &&
            this.chunkcount - p > isortlimit * 5
            ) {
        	// sort this using multi-threading
            Future<Integer> part0, part1;
            int p0 = -1, p1 = -1;
            try {
                part0 = partitionthreadexecutor.submit(new partitionthread(this, 0, p, 0));
            } catch (RejectedExecutionException e) {
                part0 = null;
                try {p0 = new partitionthread(this, 0, p, 0).call().intValue();} catch (Exception ee) {}
            }
            try {
                part1 = partitionthreadexecutor.submit(new partitionthread(this, p, this.chunkcount, p));
            } catch (RejectedExecutionException e) {
                part1 = null;
                try {p1 = new partitionthread(this, p, this.chunkcount, p).call().intValue();} catch (Exception ee) {}
            }
            try {
                if (part0 != null) p0 = part0.get().intValue();
                Future<Object> sort0, sort1, sort2, sort3;
                try {
                    sort0 = sortingthreadexecutor.submit(new qsortthread(this, 0, p0, 0));
                } catch (RejectedExecutionException e) {
                    sort0 = null;
                    try {new qsortthread(this, 0, p0, 0).call();} catch (Exception ee) {}
                }
                try {
                    sort1 = sortingthreadexecutor.submit(new qsortthread(this, p0, p, p0));
                } catch (RejectedExecutionException e) {
                    sort1 = null;
                    try {new qsortthread(this, p0, p, p0).call();} catch (Exception ee) {}
                }
                if (part1 != null) p1 = part1.get().intValue();
                try {
                    sort2 = sortingthreadexecutor.submit(new qsortthread(this, p, p1, p));
                } catch (RejectedExecutionException e) {
                    sort2 = null;
                    try {new qsortthread(this, p, p1, p).call();} catch (Exception ee) {}
                }
                try {
                    sort3 = sortingthreadexecutor.submit(new qsortthread(this, p1, this.chunkcount, p1));
                } catch (RejectedExecutionException e) {
                    sort3 = null;
                    try {new qsortthread(this, p1, this.chunkcount, p1).call();} catch (Exception ee) {}
                }
                // wait for all results
                if (sort0 != null) sort0.get();
                if (sort1 != null) sort1.get();
                if (sort2 != null) sort2.get();
                if (sort3 != null) sort3.get();
            } catch (final InterruptedException e) {
                Log.logSevere("RowCollection", "", e);
            } catch (final ExecutionException e) {
                Log.logSevere("RowCollection", "", e);
            }
        } else {
        	qsort(0, p, 0, swapspace);
        	qsort(p + 1, this.chunkcount, 0, swapspace);
        }
        this.sortBound = this.chunkcount;
        //assert this.isSorted();
    }

    /*
    public synchronized final void sort2() {
        assert (this.rowdef.objectOrder != null);
        if (this.sortBound == this.chunkcount) return; // this is already sorted
        if (this.chunkcount < isortlimit) {
            isort(0, this.chunkcount, new byte[this.rowdef.objectsize]);
            this.sortBound = this.chunkcount;
            assert this.isSorted();
            return;
        }
        final byte[] swapspace = new byte[this.rowdef.objectsize];
        final int p = partition(0, this.chunkcount, this.sortBound, swapspace);
        if ((sortingthreadexecutor != null) &&
            (!sortingthreadexecutor.isShutdown()) &&
            (availableCPU > 1) && 
            (this.chunkcount > 4000)) {
            // sort this using multi-threading
            final Future<Object> part = sortingthreadexecutor.submit(new qsortthread(this, 0, p, 0));
            //CompletionService<Object> sortingthreadcompletion = new ExecutorCompletionService<Object>(sortingthreadexecutor);
            //Future<Object> part = sortingthreadcompletion.submit(new qsortthread(this, 0, p, 0));
            qsort(p + 1, this.chunkcount, 0, swapspace);
            try {
                part.get();
            } catch (final InterruptedException e) {
                Log.logSevere("RowCollection", "", e);
            } catch (final ExecutionException e) {
                Log.logSevere("RowCollection", "", e);
            }
        } else {
            qsort(0, p, 0, swapspace);
            qsort(p + 1, this.chunkcount, 0, swapspace);
        }
        this.sortBound = this.chunkcount;
        //assert this.isSorted();
    }
    */
    
    private static class qsortthread implements Callable<Object> {
        private RowCollection rc;
        int L, R, S;
        
    	public qsortthread(final RowCollection rc, final int L, final int R, final int S) {
    	    this.rc = rc;
    	    this.L = L;
    	    this.R = R;
    	    this.S = S;
        }
    	
    	public Object call() throws Exception {
    	    rc.qsort(L, R, S, new byte[rc.rowdef.objectsize]);
            return null;
        }
    }
    
    final void qsort(final int L, final int R, final int S, final byte[] swapspace) {
    	if (R - L < isortlimit) {
            isort(L, R, swapspace);
            return;
        }
    	assert R > L: "L = " + L + ", R = " + R + ", S = " + S;
		final int p = partition(L, R, S, swapspace);
		assert p >= L: "L = " + L + ", R = " + R + ", S = " + S + ", p = " + p;
		assert p < R: "L = " + L + ", R = " + R + ", S = " + S + ", p = " + p;
		qsort(L, p, 0, swapspace);
		qsort(p + 1, R, 0, swapspace);
	}
    
    public static class partitionthread implements Callable<Integer> {
        RowCollection rc;
        int L, R, S;
        
        public partitionthread(final RowCollection rc, final int L, final int R, final int S) {
            this.rc = rc;
            this.L = L;
            this.R = R;
            this.S = S;
        }
        
        public Integer call() throws Exception {
            return Integer.valueOf(rc.partition(L, R, S, new byte[rc.rowdef.objectsize]));
        }
    }
    
    /**
     * @param L is the first element in the sequence
     * @param R is the right bound of the sequence, and outside of the sequence
     * @param S is the bound of the sorted elements in the sequence
     * @param swapspace
     * @return
     */
    final int partition(final int L, final int R, int S, final byte[] swapspace) {
        assert (L < R - 1): "L = " + L + ", R = " + R + ", S = " + S;
        assert (R - L >= isortlimit): "L = " + L + ", R = " + R + ", S = " + S + ", isortlimit = " + isortlimit;
        
        int p = L;
        int q = R - 1;
        int pivot = pivot(L, R, S);
        if (this.rowdef.objectOrder instanceof Base64Order) {
        	while (p <= q) {
        		// wenn pivot < S: pivot befindet sich in sortierter Sequenz von L bis S - 1
        		// d.h. alle Werte von L bis pivot sind kleiner als das pivot
        		// zu finden ist ein minimales p <= q so dass chunk[p] >= pivot
        		if ((pivot < S) && (p < pivot)) {
        			//System.out.println("+++ saved " + (pivot - p) + " comparisments");
        			p = pivot;
        			S = 0;
        		} else {
        			while ((p < R - 1) && (compare(pivot, p) >= 0)) p++; // chunkAt[p] < pivot
        		}
        		// nun gilt chunkAt[p] >= pivot
        		while ((q > L) && (compare(pivot, q) <= 0)) q--; // chunkAt[q] > pivot
        		if (p <= q) {
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
        			while ((p < R - 1) && (compare(pivot, p) >= 0)) p++; // chunkAt[p] < pivot
        		}
        		while ((q > L) && (compare(pivot, q) <= 0)) q--; // chunkAt[q] > pivot
        		if (p <= q) {
        			pivot = swap(p, q, pivot, swapspace);
        			p++;
        			q--;
        		}
        	}
        }
        // now p is the beginning of the upper sequence
        // finally, the pivot element should be exactly between the two sequences
        // distinguish two cases: pivot in lower and upper sequence
        // to do this it is sufficient to compare the index, not the entry content
        if (pivot < p) {
            // switch the pivot with the element _below_ p, the element in p belongs to the upper sequence
            // and does not fit into the lower sequence
            swap(pivot, p - 1, pivot, swapspace);
            return p - 1;
        } else if (pivot > p) {
            // switch the pivot with p, they are both in the same sequence
            swap(pivot, p, pivot, swapspace);
            return p;
        }
        assert pivot == p;
        return p;
    }
	
    private final int pivot(final int L, final int R, final int S) {
        if (S == 0 || S < L) {
            // the collection has no ordering
            // or
            // the collection has an ordering, but this is not relevant for this pivot
            // because the ordered zone is outside of ordering zone
            final int m = picMiddle(L, (3 * L + R - 1) / 4, (L + R - 1) / 2, (L + 3 * R - 3) / 4, R - 1);
            assert L <= m;
            assert m < R;
            return m;
        }
        if (S < R) {
            // the collection has an ordering
            // and part of the ordered zone is inside the to-be-ordered zone
            final int m = picMiddle(L, L + (S - L) / 3, (L + R - 1) / 2, S, R - 1);
            assert L <= m;
            assert m < R;
            return m;
        }
        // use the sorted set to find good pivot:
        // the sort range is fully inside the sorted area:
        // the middle element must be the best
        // (however, it should be skipped because there is no point in sorting this)
        return (L + R - 1) / 2;
    }

    private final int picMiddle(final int a, final int b, final int c, final int d, final int e) {
        return picMiddle(picMiddle(a, b, c), d, e);
    }
    
    private final int picMiddle(final int a, final int b, final int c) {
        if (compare(a, b) > 0) {
            if (compare(c, a) > 0) return a;
            if (compare(b, c) > 0) return b;
        } else {
            if (compare(a, c) > 0) return a;
            if (compare(c, b) > 0) return b;
        }
        return c;
        //if (c < a && a < b || a > b && c > a) return a;
        //if (a < b && c > b || c < b && a > b) return b;
    }
    
    /*
    private final int picMiddle(final int[] list, int len) {
        assert len % 2 != 0;
        assert len <= list.length;
        final int cut = list.length / 2;
        for (int i = 0; i < cut; i++) {remove(list, len, min(list, len)); len--;}
        for (int i = 0; i < cut; i++) {remove(list, len, max(list, len)); len--;}
        // the remaining element must be the middle element
        assert len == 1;
        return list[0];
    }
    private final void remove(final int[] list, final int len, final int idx) {
        if (idx == len - 1) return;
        list[idx] = list[len - 1]; // shift last element to front
    }
    
    private final int min(final int[] list, int len) {
        assert len > 0;
        int f = 0;
        while (len-- > 0) {
            if (compare(list[f], list[len]) > 0) f = len;
        }
        return f;
    }
    
    private final int max(final int[] list, int len) {
        assert len > 0;
        int f = 0;
        while (len-- > 0) {
            if (compare(list[f], list[len]) < 0) f = len;
        }
        return f;
    }
    */
    
    private final void isort(final int L, final int R, final byte[] swapspace) {
        for (int i = L + 1; i < R; i++)
            for (int j = i; j > L && compare(j - 1, j) > 0; j--)
                swap(j, j - 1, 0, swapspace);
    }

    private final int swap(final int i, final int j, final int p, final byte[] swapspace) {
        if (i == j) return p;
        System.arraycopy(chunkcache, this.rowdef.objectsize * i, swapspace, 0, this.rowdef.objectsize);
        System.arraycopy(chunkcache, this.rowdef.objectsize * j, chunkcache, this.rowdef.objectsize * i, this.rowdef.objectsize);
        System.arraycopy(swapspace, 0, chunkcache, this.rowdef.objectsize * j, this.rowdef.objectsize);
        if (i == p) return j; else if (j == p) return i; else return p;
    }

    protected synchronized void uniq() {
        assert (this.rowdef.objectOrder != null);
        // removes double-occurrences of chunks
        // this works only if the collection was ordered with sort before
        // if the collection is large and the number of deletions is also large,
        // then this method may run a long time with 100% CPU load which is caused
        // by the large number of memory movements.
        if (chunkcount < 2) return;
        int i = chunkcount - 2;
        final long t = System.currentTimeMillis(); // for time-out
        int d = 0;
        try {
            while (i >= 0) {
                if (match(i, i + 1)) {
                    removeRow(i + 1, true);
                    d++;
                }
                i--;
                if (System.currentTimeMillis() - t > 60000) {
                	Log.logWarning("RowCollection", "uniq() time-out at " + i + " (backwards) from " + chunkcount + " elements after " + (System.currentTimeMillis() - t) + " milliseconds; " + d + " deletions so far");
                	return;
                }
            }
        } catch (final RuntimeException e) {
            Log.logWarning("RowCollection", e.getMessage(), e);
        }
    }
    
    public synchronized ArrayList<RowCollection> removeDoubles() throws RowSpaceExceededException {
        assert (this.rowdef.objectOrder != null);
        // removes double-occurrences of chunks
        // in contrast to uniq() this removes also the remaining, non-double entry that had a double-occurrence to the others
        // all removed chunks are returned in an array
        this.sort();
        final ArrayList<RowCollection> report = new ArrayList<RowCollection>();
        if (chunkcount < 2) return report;
        int i = chunkcount - 2;
        boolean u = true;
        RowCollection collection = new RowCollection(this.rowdef, 2);
        try {
            while (i >= 0) {
                if (match(i, i + 1)) {
                    collection.addUnique(get(i + 1, false));
                    removeRow(i + 1, false);
                    if (i + 1 < chunkcount - 1) u = false;
                } else if (!collection.isEmpty()) {
                    // finish collection of double occurrences
                    collection.addUnique(get(i + 1, false));
                    removeRow(i + 1, false);
                    if (i + 1 < chunkcount - 1) u = false;
                    collection.trim();
                    report.add(collection);
                    collection = new RowSet(this.rowdef, 2);
                }
                i--;
            }
        } catch (final RuntimeException e) {
            Log.logWarning("kelondroRowCollection", e.getMessage(), e);
        } finally {
            if (!u) this.sort();
        }
        return report;
    }
    
    public synchronized boolean isSorted() {
        assert (this.rowdef.objectOrder != null);
        if (chunkcount <= 1) return true;
        if (chunkcount != this.sortBound) return false;
        /*
        for (int i = 0; i < chunkcount - 1; i++) {
        	//System.out.println("*" + UTF8.String(get(i).getPrimaryKeyBytes()));
        	if (compare(i, i + 1) > 0) {
        		System.out.println("?" + UTF8.String(get(i + 1, false).getPrimaryKeyBytes()));
        		return false;
        	}
        }
        */
        return true;
    }
    
    public synchronized String toString() {
        final StringBuilder s = new StringBuilder(80);
        final Iterator<Row.Entry> i = iterator();
        if (i.hasNext()) s.append(i.next().toString());
        while (i.hasNext()) s.append(", " + (i.next()).toString());
        return s.toString();
    }

    private final int compare(final int i, final int j) {
        assert (chunkcount * this.rowdef.objectsize <= chunkcache.length) : "chunkcount = " + chunkcount + ", objsize = " + this.rowdef.objectsize + ", chunkcache.length = " + chunkcache.length;
        assert (i >= 0) && (i < chunkcount) : "i = " + i + ", chunkcount = " + chunkcount;
        assert (j >= 0) && (j < chunkcount) : "j = " + j + ", chunkcount = " + chunkcount;
        assert (this.rowdef.objectOrder != null);
        if (i == j) return 0;
        //assert (!bugappearance(chunkcache, i * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength));
        //assert (!bugappearance(chunkcache, j * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength));
        final int c = this.rowdef.objectOrder.compare(
                chunkcache,
                i * this.rowdef.objectsize,
                chunkcache,
                j * this.rowdef.objectsize,
                this.rowdef.primaryKeyLength);
        return c;
    }

    protected synchronized int compare(final byte[] a, final int astart, final int chunknumber) {
        assert (chunknumber < chunkcount);
        assert a.length - astart >= this.rowdef.primaryKeyLength;
        final int len = Math.min(a.length - astart, this.rowdef.primaryKeyLength);
        return rowdef.objectOrder.compare(a, astart, chunkcache, chunknumber * this.rowdef.objectsize, len);
    }
    
    protected final boolean match(final int i, final int j) {
        assert (chunkcount * this.rowdef.objectsize <= chunkcache.length) : "chunkcount = " + chunkcount + ", objsize = " + this.rowdef.objectsize + ", chunkcache.length = " + chunkcache.length;
        assert (i >= 0) && (i < chunkcount) : "i = " + i + ", chunkcount = " + chunkcount;
        assert (j >= 0) && (j < chunkcount) : "j = " + j + ", chunkcount = " + chunkcount;
        if (i >= chunkcount) return false;
        if (j >= chunkcount) return false;
        assert (this.rowdef.objectOrder != null);
        if (i == j) return true;
        int astart = i * this.rowdef.objectsize;
        int bstart = j * this.rowdef.objectsize;
        int k = this.rowdef.primaryKeyLength;
        while (k-- != 0) {
            if (chunkcache[astart++] != chunkcache[bstart++]) return false;
        }
        return true;
    }
    
    protected synchronized boolean match(final byte[] a, int astart, final int chunknumber) {
        if (chunknumber >= chunkcount) return false;
        int p = chunknumber * this.rowdef.objectsize;
        assert a.length - astart >= this.rowdef.primaryKeyLength;
        int len = Math.min(a.length - astart, this.rowdef.primaryKeyLength);
        while (len-- != 0) {
            if (a[astart++] != chunkcache[p++]) return false;
        }
        return true;
    }

    public synchronized void close() {
        chunkcache = null;
    }
    
    private static long d(final long a, final long b) {
    	if (b == 0) return a;
    	return a / b;
    }
    
    private static Random random = null;
    private static String randomHash() {
    	return
    		Base64Order.enhancedCoder.encodeLongSB(random.nextLong(), 4).toString() +
    		Base64Order.enhancedCoder.encodeLongSB(random.nextLong(), 4).toString() +
    		Base64Order.enhancedCoder.encodeLongSB(random.nextLong(), 4).toString();
    }
    
    public static void test(final int testsize) throws RowSpaceExceededException {
    	final Row r = new Row(new Column[]{
    			new Column("hash", Column.celltype_string, Column.encoder_bytes, 12, "hash")},
    			Base64Order.enhancedCoder);
    	
    	RowCollection a = new RowCollection(r, testsize);
    	a.add("AAAAAAAAAAAA".getBytes());
    	a.add("BBBBBBBBBBBB".getBytes());
    	a.add("BBBBBBBBBBBB".getBytes());
    	a.add("BBBBBBBBBBBB".getBytes());
    	a.add("CCCCCCCCCCCC".getBytes());
    	final ArrayList<RowCollection> del = a.removeDoubles();
    	System.out.println(del + "rows double");
    	final Iterator<Row.Entry> j = a.iterator();
    	while (j.hasNext()) System.out.println(UTF8.String(j.next().bytes()));
    	
        System.out.println("kelondroRowCollection test with size = " + testsize);
        a = new RowCollection(r, testsize);
        long t0 = System.nanoTime();
        random = new Random(0);
        for (int i = 0; i < testsize; i++) a.add(randomHash().getBytes());
        random = new Random(0);
        for (int i = 0; i < testsize; i++) a.add(randomHash().getBytes());
        a.sort();
        a.uniq();
        long t1 = System.nanoTime();
        System.out.println("create a   : " + (t1 - t0) + " nanoseconds, " + d(testsize, (t1 - t0)) + " entries/nanoseconds; a.size() = " + a.size());
        
    	final RowCollection c = new RowCollection(r, testsize);
    	random = new Random(0);
    	t0 = System.nanoTime();
    	for (int i = 0; i < testsize; i++) {
    		c.add(randomHash().getBytes());
    	}
    	t1 = System.nanoTime();
    	System.out.println("create c   : " + (t1 - t0) + " nanoseconds, " + d(testsize, (t1 - t0)) + " entries/nanoseconds");
    	final RowCollection d = new RowCollection(r, testsize);
    	for (int i = 0; i < testsize; i++) {
    		d.add(c.get(i, false).getPrimaryKeyBytes());
    	}
    	final long t2 = System.nanoTime();
    	System.out.println("copy c -> d: " + (t2 - t1) + " nanoseconds, " + d(testsize, (t2 - t1)) + " entries/nanoseconds");
    	//availableCPU = 1;
    	c.sort();
    	final long t3 = System.nanoTime();
    	System.out.println("sort c (1) : " + (t3 - t2) + " nanoseconds, " + d(testsize, (t3 - t2)) + " entries/nanoseconds");
    	//availableCPU = 2;
    	d.sort();
    	final long t4 = System.nanoTime();
    	System.out.println("sort d (2) : " + (t4 - t3) + " nanoseconds, " + d(testsize, (t4 - t3)) + " entries/nanoseconds");
    	c.uniq();
    	final long t5 = System.nanoTime();
    	System.out.println("uniq c     : " + (t5 - t4) + " nanoseconds, " + d(testsize, (t5 - t4)) + " entries/nanoseconds");
    	d.uniq();
    	final long t6 = System.nanoTime();
    	System.out.println("uniq d     : " + (t6 - t5) + " nanoseconds, " + d(testsize, (t6 - t5)) + " entries/nanoseconds");
    	random = new Random(0);
    	final RowSet e = new RowSet(r, testsize);
    	for (int i = 0; i < testsize; i++) {
    		e.put(r.newEntry(randomHash().getBytes()));
    	}
    	final long t7 = System.nanoTime();
    	System.out.println("create e   : " + (t7 - t6) + " nanoseconds, " + d(testsize, (t7 - t6)) + " entries/nanoseconds");
    	e.sort();
    	final long t8 = System.nanoTime();
    	System.out.println("sort e (2) : " + (t8 - t7) + " nanoseconds, " + d(testsize, (t8 - t7)) + " entries/nanoseconds");
    	e.uniq();
    	final long t9 = System.nanoTime();
    	System.out.println("uniq e     : " + (t9 - t8) + " nanoseconds, " + d(testsize, (t9 - t8)) + " entries/nanoseconds");
    	final boolean cis = c.isSorted();
    	final long t10 = System.nanoTime();
    	System.out.println("c isSorted = " + ((cis) ? "true" : "false") + ": " + (t10 - t9) + " nanoseconds");
    	final boolean dis = d.isSorted();
    	final long t11 = System.nanoTime();
    	System.out.println("d isSorted = " + ((dis) ? "true" : "false") + ": " + (t11 - t10) + " nanoseconds");
    	final boolean eis = e.isSorted();
    	final long t12 = System.nanoTime();
    	System.out.println("e isSorted = " + ((eis) ? "true" : "false") + ": " + (t12 - t11) + " nanoseconds");
    	random = new Random(0);
    	boolean allfound = true;
        for (int i = 0; i < testsize; i++) {
            final String rh = randomHash();
            if (e.get(rh.getBytes()) == null) {
                allfound = false;
                System.out.println("not found hash " + rh + " at attempt " + i);
                break;
            }
        }
        final long t13 = System.nanoTime();
        System.out.println("e allfound = " + ((allfound) ? "true" : "false") + ": " + (t13 - t12) + " nanoseconds");
        boolean noghosts = true;
        for (int i = 0; i < testsize; i++) {
            if (e.get(randomHash().getBytes()) != null) {
                noghosts = false;
                break;
            }
        }
        final long t14 = System.nanoTime();
        System.out.println("e noghosts = " + ((noghosts) ? "true" : "false") + ": " + (t14 - t13) + " nanoseconds");
        System.out.println("Result size: c = " + c.size() + ", d = " + d.size() + ", e = " + e.size());
    	System.out.println();
    	if (sortingthreadexecutor != null) sortingthreadexecutor.shutdown();
    }
    
    public static void main(final String[] args) {
    	//test(1000);
    	try {
            test(50000);
        } catch (RowSpaceExceededException e) {
            e.printStackTrace();
        }
    	//test(100000);
    	//test(1000000);
    	
    	/*   	
        System.out.println(new java.util.Date(10957 * day));
        System.out.println(new java.util.Date(0));
        System.out.println(daysSince2000(System.currentTimeMillis()));
        */
    }
}
