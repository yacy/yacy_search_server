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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.sorting.Array;
import net.yacy.cora.sorting.Sortable;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.kelondroException;


public class RowCollection implements Sortable<Row.Entry>, Iterable<Row.Entry>, Cloneable, Serializable {

    private static final long serialVersionUID=-4670634138825982705L;
    private static final byte[] EMPTY_CACHE = new byte[0];

    public  static final long growfactorLarge100 = 140L;
    public  static final long growfactorSmall100 = 110L;
    private static final int isortlimit = 20;

    private static final int exp_chunkcount  = 0;
    private static final int exp_last_read   = 1;
    private static final int exp_last_wrote  = 2;
    private static final int exp_order_type  = 3;
    private static final int exp_order_bound = 4;
    private static final int exp_collection  = 5;

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
       	this.chunkcache = EMPTY_CACHE;
        this.chunkcount = 0;
    }

    public RowCollection(final Row rowdef, final int objectCount) throws SpaceExceededException {
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
            ConcurrentLog.warn("RowCollection", "corrected wrong chunkcount; chunkcount = " + this.chunkcount + ", chunkcachelength = " + chunkcachelength + ", rowdef.objectsize = " + rowdef.objectsize);
            this.chunkcount = chunkcachelength / rowdef.objectsize; // patch problem
        }
        this.lastTimeWrote = (exportedCollection.getColLong(exp_last_wrote) + 10957) * day;
        final String sortOrderKey = exportedCollection.getColASCII(exp_order_type);
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
        if (this.sortBound > this.chunkcount) {
            ConcurrentLog.warn("RowCollection", "corrected wrong sortBound; sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount);
            this.sortBound = this.chunkcount;
        }
        this.chunkcache = exportedCollection.getColBytes(exp_collection, false);
    }

    protected RowCollection(final Row rowdef, final byte[] chunkcache, final int chunkcount, final int sortBound, final long lastTimeWrote) {
        this.rowdef = rowdef;
        this.chunkcache = new byte[chunkcache.length];
        System.arraycopy(chunkcache, 0, this.chunkcache, 0, chunkcache.length);
        this.chunkcount = chunkcount;
        this.sortBound = sortBound;
        this.lastTimeWrote = lastTimeWrote;
    }

    @Override
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

    private static Column exportColumn0, exportColumn1, exportColumn2, exportColumn3, exportColumn4, collectionColumnProducer;

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
        if (collectionColumnProducer == null) collectionColumnProducer = new Column("byte[] collection-1");
        /*
         * because of a strange bug these objects cannot be initialized as normal
         * static final. If I try that, they are not initialized and are assigned null. why?
         */
        
        Column collectionColumn = (Column) collectionColumnProducer.clone();
        collectionColumn.setCellwidth(chunkcachelength);
        final Row er = new Row(new Column[]{
                    exportColumn0, exportColumn1, exportColumn2, exportColumn3, exportColumn4,
                    collectionColumn
                },
                NaturalOrder.naturalOrder
        );
        assert er.objectsize == chunkcachelength + exportOverheadSize;
        return er;
    }

    public synchronized byte[] exportCollection() {
        // returns null if the collection is empty
        sort(); // experimental; supervise CPU load
        //uniq();
        //trim();
        assert this.sortBound == this.chunkcount; // on case the collection is sorted
        assert size() * this.rowdef.objectsize <= this.chunkcache.length : "this.size() = " + size() + ", objectsize = " + this.rowdef.objectsize + ", chunkcache.length = " + this.chunkcache.length;
        final Row row = exportRow(size() * this.rowdef.objectsize);
        final Row.Entry entry = row.newEntry();
        assert (this.sortBound <= this.chunkcount) : "sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount;
        assert (this.chunkcount <= this.chunkcache.length / this.rowdef.objectsize) : "chunkcount = " + this.chunkcount + ", chunkcache.length = " + this.chunkcache.length + ", rowdef.objectsize = " + this.rowdef.objectsize;
        entry.setCol(exp_chunkcount, this.chunkcount);
        entry.setCol(exp_last_read, daysSince2000(System.currentTimeMillis()));
        entry.setCol(exp_last_wrote, daysSince2000(this.lastTimeWrote));
        entry.setCol(exp_order_type, (this.rowdef.objectOrder == null) ? ASCII.getBytes("__") : ASCII.getBytes(this.rowdef.objectOrder.signature()));
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

    private final long neededSpaceForEnsuredSize(final int elements) {
        assert elements > 0 : "elements = " + elements;
        final long needed = elements * this.rowdef.objectsize;
        if (this.chunkcache.length >= needed) return 0;
        assert needed > 0 : "needed = " + needed;
        long allocram = Math.max(1024, (needed * growfactorLarge100) / 100L);
        allocram -= allocram % this.rowdef.objectsize;
        assert allocram > 0 : "elements = " + elements + ", new = " + allocram;
        if (allocram <= Integer.MAX_VALUE && MemoryControl.request(allocram, false)) return allocram;
        allocram = (needed * growfactorSmall100) / 100L;
        allocram -= allocram % this.rowdef.objectsize;
        assert allocram >= 0 : "elements = " + elements + ", new = " + allocram;
        return allocram;
    }

    private final void ensureSize(final int elements) throws SpaceExceededException {
        if (elements == 0) return;
        final long allocram = neededSpaceForEnsuredSize(elements);
        if (allocram == 0) return;
        assert this.chunkcache.length < elements * this.rowdef.objectsize : "wrong alloc computation (1): elements * rowdef.objectsize = " + (elements * this.rowdef.objectsize) + ", chunkcache.length = " + this.chunkcache.length;
        assert allocram > this.chunkcache.length : "wrong alloc computation (2): allocram = " + allocram + ", chunkcache.length = " + this.chunkcache.length;
        if (allocram > Integer.MAX_VALUE || !MemoryControl.request(allocram + 32, true))
        	throw new SpaceExceededException(allocram + 32, "RowCollection grow");
        try {
            final byte[] newChunkcache = new byte[(int) allocram]; // increase space
            System.arraycopy(this.chunkcache, 0, newChunkcache, 0, this.chunkcache.length);
            this.chunkcache = newChunkcache;
        } catch (final OutOfMemoryError e) {
        	// lets try again after a forced gc()
        	System.gc();
        	try {
                final byte[] newChunkcache = new byte[(int) allocram]; // increase space
                System.arraycopy(this.chunkcache, 0, newChunkcache, 0, this.chunkcache.length);
                this.chunkcache = newChunkcache;
            } catch (final OutOfMemoryError ee) {
                throw new SpaceExceededException(allocram, "RowCollection grow after OutOfMemoryError " + ee.getMessage());
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
        return neededSpaceForEnsuredSize(this.chunkcount + 1);
    }

    @Override
    public int compare(final Entry o1, final Entry o2) {
        return o1.compareTo(o2);
    }

    @Override
    public Entry buffer() {
        return row().newEntry();
    }

    @Override
    public void swap(final int i, final int j, final Entry buffer) {
        if (i == j) return;
        final byte[] swapspace = buffer.bytes();
        System.arraycopy(this.chunkcache, this.rowdef.objectsize * i, swapspace, 0, this.rowdef.objectsize);
        System.arraycopy(this.chunkcache, this.rowdef.objectsize * j, this.chunkcache, this.rowdef.objectsize * i, this.rowdef.objectsize);
        System.arraycopy(swapspace, 0, this.chunkcache, this.rowdef.objectsize * j, this.rowdef.objectsize);
    }

    private final void checkShrink() {
        final long allocram = this.rowdef.objectsize * this.chunkcount;
        if (allocram < this.chunkcache.length / 2 && MemoryControl.request(allocram + 32, true)) trim();
    }
    
    protected synchronized void trim() {
        if (this.chunkcache.length == 0) return;
        final long needed = this.chunkcount * this.rowdef.objectsize;
        assert needed <= this.chunkcache.length;
        if (needed >= this.chunkcache.length)
            return; // in case that the growfactor causes that the cache would
                    // grow instead of shrink, simply ignore the growfactor
        if (MemoryControl.available() + 1000 < needed)
            return; // if the swap buffer is not available, we must give up.
                    // This is not critical. Otherwise we provoke a serious
                    // problem with OOM
        try {
            final byte[] newChunkcache = new byte[(int) needed];
            System.arraycopy(this.chunkcache, 0, newChunkcache, 0, newChunkcache.length);
            this.chunkcache = newChunkcache;
        } catch (final OutOfMemoryError e) {
            // lets try again after a forced gc()
            System.gc();
            try {
                final byte[] newChunkcache = new byte[(int) needed];
                System.arraycopy(this.chunkcache, 0, newChunkcache, 0, newChunkcache.length);
                this.chunkcache = newChunkcache;
            } catch (final OutOfMemoryError ee) {
            }
        }
    }

    public final long lastWrote() {
        return this.lastTimeWrote;
    }

    protected synchronized final byte[] getKey(final int index) {
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < this.chunkcount) : "get: access with index " + index + " is above chunkcount " + this.chunkcount + "; sortBound = " + this.sortBound;
        assert (index * this.rowdef.objectsize < this.chunkcache.length);
        if ((this.chunkcache == null) || (this.rowdef == null)) return null; // case may appear during shutdown
        if (index >= this.chunkcount) return null;
        if ((index + 1) * this.rowdef.objectsize > this.chunkcache.length) return null; // the whole chunk does not fit into the chunkcache
        final byte[] b = new byte[this.rowdef.primaryKeyLength];
        System.arraycopy(this.chunkcache, index * this.rowdef.objectsize, b, 0, b.length);
        return b;
    }

    @Override
    public synchronized final Row.Entry get(final int index, final boolean clone) {
        assert (index >= 0) : "get: access with index " + index + " is below zero";
        assert (index < this.chunkcount) : "get: access with index " + index + " is above chunkcount " + this.chunkcount + "; sortBound = " + this.sortBound;
        assert (this.chunkcache != null && index * this.rowdef.objectsize < this.chunkcache.length);
        assert this.sortBound <= this.chunkcount : "sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount;
        if ((this.chunkcache == null) || (this.rowdef == null)) return null; // case may appear during shutdown
        Row.Entry entry;
        final int addr = index * this.rowdef.objectsize;
        if (index >= this.chunkcount) return null;
        if (addr + this.rowdef.objectsize > this.chunkcache.length) return null; // the whole chunk does not fit into the chunkcache
        entry = this.rowdef.newEntry(this.chunkcache, addr, clone);
        return entry;
    }

    public synchronized final void set(final int index, final Row.Entry a) throws SpaceExceededException {
        assert (index >= 0) : "set: access with index " + index + " is below zero";
        ensureSize(index + 1);
        final byte[] column = a.bytes();
        assert a.cellwidth(0) == this.rowdef.primaryKeyLength;
        assert column.length >= this.rowdef.primaryKeyLength;
        final boolean sameKey = match(column, 0, index);
        //if (sameKey) System.out.print("$");
        a.writeToArray(this.chunkcache, index * this.rowdef.objectsize);
        if (index >= this.chunkcount) this.chunkcount = index + 1;
        if (!sameKey && index < this.sortBound) this.sortBound = index;
        this.lastTimeWrote = System.currentTimeMillis();
    }

    public final void insertUnique(final int index, final Row.Entry a) throws SpaceExceededException {
        assert (a != null);

        if (index < this.chunkcount) {
            // make room
            ensureSize(this.chunkcount + 1);
            System.arraycopy(this.chunkcache, this.rowdef.objectsize * index, this.chunkcache, this.rowdef.objectsize * (index + 1), (this.chunkcount - index) * this.rowdef.objectsize);
            this.chunkcount++;
        }
        // insert entry into gap
        set(index, a);
    }

    public synchronized void addUnique(final Row.Entry row) throws SpaceExceededException {
        final byte[] r = row.bytes();
        addUnique(r, 0, r.length);
    }

    public synchronized void addUnique(final List<Row.Entry> rows) throws SpaceExceededException {
        assert this.sortBound == 0 : "sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount;
        final Iterator<Row.Entry> i = rows.iterator();
        while (i.hasNext()) addUnique(i.next());
    }

    public synchronized void add(final byte[] a) throws SpaceExceededException {
        assert a.length == this.rowdef.objectsize : "a.length = " + a.length + ", objectsize = " + this.rowdef.objectsize;
        addUnique(a, 0, a.length);
    }

    private final void addUnique(final byte[] a, final int astart, final int alength) throws SpaceExceededException {
        assert (a != null);
        assert (astart >= 0) && (astart < a.length) : " astart = " + astart;
        assert (!(allZero(a, astart, alength))) : "a = " + NaturalOrder.arrayList(a, astart, alength);
        assert (alength > 0);
        assert (astart + alength <= a.length);
        assert alength == this.rowdef.objectsize : "alength =" + alength + ", rowdef.objectsize = " + this.rowdef.objectsize;
        final int l = Math.min(this.rowdef.objectsize, Math.min(alength, a.length - astart));
        ensureSize(this.chunkcount + 1);
        System.arraycopy(a, astart, this.chunkcache, this.rowdef.objectsize * this.chunkcount, l);
        this.chunkcount++;
        // if possible, increase the sortbound value to suppress unnecessary sorting
        if (this.chunkcount == 1) {
            assert this.sortBound == 0;
            this.sortBound = 1;
        } else if (
                this.sortBound + 1 == this.chunkcount &&
                this.rowdef.objectOrder.compare(this.chunkcache, this.rowdef.objectsize * (this.chunkcount - 2),
                                                this.chunkcache, this.rowdef.objectsize * (this.chunkcount - 1), this.rowdef.primaryKeyLength) == -1) {
            this.sortBound = this.chunkcount;
        }
        this.lastTimeWrote = System.currentTimeMillis();
    }

    protected final void addSorted(final byte[] a, final int astart, final int alength) throws SpaceExceededException {
        assert (a != null);
        assert (astart >= 0) && (astart < a.length) : " astart = " + astart;
        assert (!(allZero(a, astart, alength))) : "a = " + NaturalOrder.arrayList(a, astart, alength);
        assert (alength > 0);
        assert (astart + alength <= a.length);
        assert alength == this.rowdef.objectsize : "alength =" + alength + ", rowdef.objectsize = " + this.rowdef.objectsize;
        final int l = Math.min(this.rowdef.objectsize, Math.min(alength, a.length - astart));
        ensureSize(this.chunkcount + 1);
        System.arraycopy(a, astart, this.chunkcache, this.rowdef.objectsize * this.chunkcount, l);
        this.chunkcount++;
        this.sortBound = this.chunkcount;
        this.lastTimeWrote = System.currentTimeMillis();
    }

    private final static boolean allZero(final byte[] a, final int astart, final int alength) {
        for (int i = 0; i < alength; i++) if (a[astart + i] != 0) return false;
        return true;
    }
    
    public synchronized final void addAllUnique(final RowCollection c) throws SpaceExceededException {
        if (c == null) return;
        assert(this.rowdef.objectsize == c.rowdef.objectsize);
        ensureSize(this.chunkcount + c.size());
        System.arraycopy(c.chunkcache, 0, this.chunkcache, this.rowdef.objectsize * this.chunkcount, this.rowdef.objectsize * c.size());
        this.chunkcount += c.size();
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
        assert p < this.chunkcount : "p = " + p + ", chunkcount = " + this.chunkcount;
        assert this.chunkcount > 0 : "chunkcount = " + this.chunkcount;
        assert this.sortBound <= this.chunkcount : "sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount;
        if (keepOrder && (p < this.sortBound)) {
            // remove by shift (quite expensive for big collections)
            final int addr = p * this.rowdef.objectsize;
            System.arraycopy(
                    this.chunkcache, addr + this.rowdef.objectsize,
                    this.chunkcache, addr,
                    (this.chunkcount - p - 1) * this.rowdef.objectsize);
            this.sortBound--; // this is only correct if p < sortBound, but this was already checked above
        } else {
            // remove by copying the top-element to the remove position
            if (p != this.chunkcount - 1) {
                System.arraycopy(
                        this.chunkcache, (this.chunkcount - 1) * this.rowdef.objectsize,
                        this.chunkcache, p * this.rowdef.objectsize,
                        this.rowdef.objectsize);
            }
            // we moved the last element to the remove position: (p+1)st element
            // only the first p elements keep their order (element p is already outside the order)
            if (this.sortBound > p) this.sortBound = p;
        }
        this.chunkcount--;
        this.lastTimeWrote = System.currentTimeMillis();
        
        // check if the chunkcache can shrink
        checkShrink();
    }


    @Override
    public final void delete(final int p) {
        removeRow(p, true);
    }

    /**
     * removes the last entry from the collection
     * @return
     */
    public synchronized Row.Entry removeOne() {
        if (this.chunkcount == 0) return null;
        final Row.Entry r = get(this.chunkcount - 1, true);
        if (this.chunkcount == this.sortBound) this.sortBound--;
        this.chunkcount--;
        this.lastTimeWrote = System.currentTimeMillis();

        // check if the chunkcache can shrink
        checkShrink();
        return r;
    }

    public synchronized List<Row.Entry> top(int count) {
        if (count > this.chunkcount) count = this.chunkcount;
        final ArrayList<Row.Entry> list = new ArrayList<Row.Entry>();
        if (this.chunkcount == 0 || count == 0) return list;
        Row.Entry entry;
        int cursor = this.chunkcount - 1;
        while (count > 0 && cursor >= 0) {
            entry = get(cursor, true);
            list.add(entry);
            count--;
            cursor--;
        }
        return list;
    }
    
    public synchronized List<Row.Entry> random(int count) {
        if (count > this.chunkcount) count = this.chunkcount;
        final ArrayList<Row.Entry> list = new ArrayList<Row.Entry>();
        if (this.chunkcount == 0 || count == 0) return list;
        Row.Entry entry;
        int cursor = 0;
        int stepsize = this.chunkcount / count;
        while (count > 0 && cursor < this.chunkcount) {
            entry = get(cursor, true);
            list.add(entry);
            count--;
            cursor += stepsize;
        }
        return list;
    }

    public synchronized byte[] smallestKey() {
        if (this.chunkcount == 0) return null;
        sort();
        final Row.Entry r = get(0, false);
        final byte[] b = r.getPrimaryKeyBytes();
        return b;
    }

    public synchronized byte[] largestKey() {
        if (this.chunkcount == 0) return null;
        sort();
        final Row.Entry r = get(this.chunkcount - 1, false);
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

    @Override
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

        @Override
        public boolean hasNext() {
            return this.p < RowCollection.this.chunkcount;
        }

        @Override
        public byte[] next() {
            return getKey(this.p++);
        }

        @Override
        public void remove() {
            this.p--;
            removeRow(this.p, this.keepOrderWhenRemoving);
        }
    }

    /**
     * return an iterator for the row entries in this object
     */
    @Override
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
            this.p = 0;
        }

        @Override
        public boolean hasNext() {
            return this.p < RowCollection.this.chunkcount;
        }

        @Override
        public Row.Entry next() {
            return get(this.p++, true);
        }

        @Override
        public void remove() {
            this.p--;
            removeRow(this.p, true);
        }

    }

    public void optimize() {
        sort();
        trim();
    }
    
    public final void sort() {
        if (this.sortBound == this.chunkcount) return; // this is sorted
        synchronized (this) {
            if (this.sortBound == this.chunkcount) return; // check again
            //Log.logInfo("RowCollection.sort()", "sorting array of size " + this.chunkcount + ", sortBound = " + this.sortBound);
            net.yacy.cora.sorting.Array.sort(this);
            this.sortBound = this.chunkcount;
        }
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

        @Override
        public Integer call() throws Exception {
            return Integer.valueOf(this.rc.partition(this.L, this.R, this.S, new byte[this.rc.rowdef.objectsize]));
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

    private final int swap(final int i, final int j, final int p, final byte[] swapspace) {
        if (i == j) return p;
        System.arraycopy(this.chunkcache, this.rowdef.objectsize * i, swapspace, 0, this.rowdef.objectsize);
        System.arraycopy(this.chunkcache, this.rowdef.objectsize * j, this.chunkcache, this.rowdef.objectsize * i, this.rowdef.objectsize);
        System.arraycopy(swapspace, 0, this.chunkcache, this.rowdef.objectsize * j, this.rowdef.objectsize);
        if (i == p) return j; else if (j == p) return i; else return p;
    }

    protected synchronized void uniq() {
        Array.uniq(this);
    }

    public synchronized ArrayList<RowCollection> removeDoubles() throws SpaceExceededException {
        assert (this.rowdef.objectOrder != null);
        // removes double-occurrences of chunks
        // in contrast to uniq() this removes also the remaining, non-double entry that had a double-occurrence to the others
        // all removed chunks are returned in an array
        sort();
        final ArrayList<RowCollection> report = new ArrayList<RowCollection>();
        if (this.chunkcount < 2) return report;
        int i = this.chunkcount - 2;
        boolean u = true;
        RowCollection collection = new RowCollection(this.rowdef, 2);
        try {
            while (i >= 0) {
                if (match(i, i + 1)) {
                    collection.addUnique(get(i + 1, false));
                    removeRow(i + 1, false);
                    if (i + 1 < this.chunkcount - 1) u = false;
                } else if (!collection.isEmpty()) {
                    // finish collection of double occurrences
                    collection.addUnique(get(i + 1, false));
                    removeRow(i + 1, false);
                    if (i + 1 < this.chunkcount - 1) u = false;
                    collection.trim();
                    report.add(collection);
                    collection = new RowSet(this.rowdef, 2);
                }
                i--;
            }
        } catch (final RuntimeException e) {
            ConcurrentLog.warn("kelondroRowCollection", e.getMessage(), e);
        } finally {
            if (!u) sort();
        }
        return report;
    }

    public synchronized boolean isSorted() {
        assert (this.rowdef.objectOrder != null);
        if (this.chunkcount <= 1) return true;
        if (this.chunkcount != this.sortBound) return false;
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

    @Override
    public synchronized String toString() {
        final StringBuilder s = new StringBuilder(80);
        final Iterator<Row.Entry> i = iterator();
        if (i.hasNext()) s.append(i.next().toString());
        while (i.hasNext()) s.append(", " + (i.next()).toString());
        return s.toString();
    }

    private final int compare(final int i, final int j) {
        assert (this.chunkcount * this.rowdef.objectsize <= this.chunkcache.length) : "chunkcount = " + this.chunkcount + ", objsize = " + this.rowdef.objectsize + ", chunkcache.length = " + this.chunkcache.length;
        assert (i >= 0) && (i < this.chunkcount) : "i = " + i + ", chunkcount = " + this.chunkcount;
        assert (j >= 0) && (j < this.chunkcount) : "j = " + j + ", chunkcount = " + this.chunkcount;
        assert (this.rowdef.objectOrder != null);
        if (i == j) return 0;
        //assert (!bugappearance(chunkcache, i * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength));
        //assert (!bugappearance(chunkcache, j * this.rowdef.objectsize + colstart, this.rowdef.primaryKeyLength));
        final int c = this.rowdef.objectOrder.compare(
                this.chunkcache,
                i * this.rowdef.objectsize,
                this.chunkcache,
                j * this.rowdef.objectsize,
                this.rowdef.primaryKeyLength);
        return c;
    }

    protected int compare(final byte[] a, final int astart, final int chunknumber) {
        assert (chunknumber < this.chunkcount);
        assert a.length - astart >= this.rowdef.primaryKeyLength;
        final int len = Math.min(a.length - astart, this.rowdef.primaryKeyLength);
        return this.rowdef.objectOrder.compare(a, astart, this.chunkcache, chunknumber * this.rowdef.objectsize, len);
    }

    protected final boolean match(final int i, final int j) {
        assert (this.chunkcount * this.rowdef.objectsize <= this.chunkcache.length) : "chunkcount = " + this.chunkcount + ", objsize = " + this.rowdef.objectsize + ", chunkcache.length = " + this.chunkcache.length;
        assert (i >= 0) && (i < this.chunkcount) : "i = " + i + ", chunkcount = " + this.chunkcount;
        assert (j >= 0) && (j < this.chunkcount) : "j = " + j + ", chunkcount = " + this.chunkcount;
        if (i >= this.chunkcount) return false;
        if (j >= this.chunkcount) return false;
        assert (this.rowdef.objectOrder != null);
        if (i == j) return true;
        int astart = i * this.rowdef.objectsize;
        int bstart = j * this.rowdef.objectsize;
        int k = this.rowdef.primaryKeyLength;
        while (k-- != 0) {
            if (this.chunkcache[astart++] != this.chunkcache[bstart++]) return false;
        }
        return true;
    }

    protected boolean match(final byte[] a, int astart, final int chunknumber) {
        if (chunknumber >= this.chunkcount) return false;
        assert a.length - astart >= this.rowdef.primaryKeyLength;
        for (int p = chunknumber * this.rowdef.objectsize,
             len = Math.min(a.length - astart, this.rowdef.primaryKeyLength);
             len != 0;
             len--, astart++, p++) {
            if (a[astart] != this.chunkcache[p]) return false;
        }
        return true;
    }

    public synchronized void close() {
        this.chunkcache = null;
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

    public static void test(final int testsize) throws SpaceExceededException {
    	final Row r = new Row(new Column[]{
    			new Column("hash", Column.celltype_string, Column.encoder_bytes, 12, "hash")},
    			Base64Order.enhancedCoder);

    	// test compare method
    	random = new Random(0);
    	for (int i = 0; i < testsize; i++) {
    	    final byte[] a = ASCII.getBytes(randomHash());
    	    final byte[] b = ASCII.getBytes(randomHash());
    	    final int c = Base64Order.enhancedCoder.compare(a, b);
            if (c == 0 && Base64Order.enhancedCoder.compare(b, a) != 0)
                System.out.println("compare failed / =; a = " + ASCII.String(a) + ", b = " + ASCII.String(b));
            if (c == -1 && Base64Order.enhancedCoder.compare(b, a) != 1)
                System.out.println("compare failed / =; a < " + ASCII.String(a) + ", b = " + ASCII.String(b));
            if (c == 1 && Base64Order.enhancedCoder.compare(b, a) != -1)
                System.out.println("compare failed / =; a > " + ASCII.String(a) + ", b = " + ASCII.String(b));
    	}

    	// test sorting methods
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
        for (int i = 0; i < testsize / 2; i++) a.add(randomHash().getBytes());
        //System.out.println("check: after first random feed"); for (final Row.Entry w: a) System.out.println("1 check-row " + ASCII.String(w.getPrimaryKeyBytes()));
        random = new Random(0);
        for (int i = 0; i < testsize / 2; i++) a.add(randomHash().getBytes());
        //System.out.println("check: after second random feed"); for (final Row.Entry w: a) System.out.println("2 check-row " + ASCII.String(w.getPrimaryKeyBytes()));
        a.sort();
        //System.out.println("check: after sort"); for (final Row.Entry w: a) System.out.println("3 check-row " + ASCII.String(w.getPrimaryKeyBytes()));
        a.uniq();
        //System.out.println("check: after sort uniq"); for (final Row.Entry w: a) System.out.println("4 check-row " + ASCII.String(w.getPrimaryKeyBytes()));
        // check order that the element have
        for (int i = 0; i < a.size() - 1; i++) {
            if (a.get(i, false).compareTo(a.get(i + 1, false)) >= 0) System.out.println("Compare error at pos " + i + ": a.get(i)=" + a.get(i, false) + ", a.get(i + 1)=" + a.get(i + 1, false));
        }

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
            if (e.get(rh.getBytes(), true) == null) {
                allfound = false;
                System.out.println("not found hash " + rh + " at attempt " + i);
                break;
            }
        }
        final long t13 = System.nanoTime();
        System.out.println("e allfound = " + ((allfound) ? "true" : "false") + ": " + (t13 - t12) + " nanoseconds");
        boolean noghosts = true;
        for (int i = 0; i < testsize; i++) {
            if (e.get(randomHash().getBytes(), true) != null) {
                noghosts = false;
                break;
            }
        }
        final long t14 = System.nanoTime();
        System.out.println("e noghosts = " + ((noghosts) ? "true" : "false") + ": " + (t14 - t13) + " nanoseconds");
        System.out.println("Result size: c = " + c.size() + ", d = " + d.size() + ", e = " + e.size());
    	System.out.println();
    }

    public static void main(final String[] args) {
    	try {
            test(500000);
            //test(1000);
            //test(50000);
            //test(100000);
            //test(1000000);
            ConcurrentLog.shutdown();
            Array.terminate();
        } catch (final SpaceExceededException e) {
            e.printStackTrace();
        }
    }

}

/*
neues sort
[{hash=BBBBBBBBBBBB}, {hash=BBBBBBBBBBBB}, {hash=BBBBBBBBBBBB}]rows double
AAAAAAAAAAAA
CCCCCCCCCCCC
kelondroRowCollection test with size = 50000
create a   : 550687000 nanoseconds, 0 entries/nanoseconds; a.size() = 25000
create c   : 31556000 nanoseconds, 0 entries/nanoseconds
copy c -> d: 13798000 nanoseconds, 0 entries/nanoseconds
sort c (1) : 80845000 nanoseconds, 0 entries/nanoseconds
sort d (2) : 79981000 nanoseconds, 0 entries/nanoseconds
uniq c     : 3697000 nanoseconds, 0 entries/nanoseconds
uniq d     : 3649000 nanoseconds, 0 entries/nanoseconds
create e   : 5719968000 nanoseconds, 0 entries/nanoseconds
sort e (2) : 65563000 nanoseconds, 0 entries/nanoseconds
uniq e     : 3540000 nanoseconds, 0 entries/nanoseconds
c isSorted = true: 119000 nanoseconds
d isSorted = true: 90000 nanoseconds
e isSorted = true: 94000 nanoseconds
e allfound = true: 64049000 nanoseconds
e noghosts = true: 57150000 nanoseconds
Result size: c = 50000, d = 50000, e = 50000

altes plus concurrency
[{hash=BBBBBBBBBBBB}, {hash=BBBBBBBBBBBB}, {hash=BBBBBBBBBBBB}]rows double
AAAAAAAAAAAA
CCCCCCCCCCCC
kelondroRowCollection test with size = 50000
Compare error at pos 23548: a.get(i)={hash=8dV7ACC_D1ir}, a.get(i + 1)={hash=8Ypevst5u_tV}
create a   : 507683000 nanoseconds, 0 entries/nanoseconds; a.size() = 25001
create c   : 38420000 nanoseconds, 0 entries/nanoseconds
copy c -> d: 12995000 nanoseconds, 0 entries/nanoseconds
sort c (1) : 20805000 nanoseconds, 0 entries/nanoseconds
sort d (2) : 18935000 nanoseconds, 0 entries/nanoseconds
uniq c     : 3712000 nanoseconds, 0 entries/nanoseconds
uniq d     : 3604000 nanoseconds, 0 entries/nanoseconds
create e   : 1333761000 nanoseconds, 0 entries/nanoseconds
sort e (2) : 16124000 nanoseconds, 0 entries/nanoseconds
uniq e     : 3453000 nanoseconds, 0 entries/nanoseconds
c isSorted = true: 115000 nanoseconds
d isSorted = true: 89000 nanoseconds
e isSorted = true: 94000 nanoseconds
e allfound = true: 58685000 nanoseconds
e noghosts = true: 59132000 nanoseconds
Result size: c = 50000, d = 50000, e = 50000

altes ohne concurrency
[{hash=BBBBBBBBBBBB}, {hash=BBBBBBBBBBBB}, {hash=BBBBBBBBBBBB}]rows double
AAAAAAAAAAAA
CCCCCCCCCCCC
kelondroRowCollection test with size = 50000
Compare error at pos 23548: a.get(i)={hash=8dV7ACC_D1ir}, a.get(i + 1)={hash=8Ypevst5u_tV}
create a   : 502494000 nanoseconds, 0 entries/nanoseconds; a.size() = 25001
create c   : 36062000 nanoseconds, 0 entries/nanoseconds
copy c -> d: 16164000 nanoseconds, 0 entries/nanoseconds
sort c (1) : 32442000 nanoseconds, 0 entries/nanoseconds
sort d (2) : 32025000 nanoseconds, 0 entries/nanoseconds
uniq c     : 3581000 nanoseconds, 0 entries/nanoseconds
uniq d     : 3561000 nanoseconds, 0 entries/nanoseconds
create e   : 1788591000 nanoseconds, 0 entries/nanoseconds
sort e (2) : 22318000 nanoseconds, 0 entries/nanoseconds
uniq e     : 3438000 nanoseconds, 0 entries/nanoseconds
c isSorted = true: 113000 nanoseconds
d isSorted = true: 89000 nanoseconds
e isSorted = true: 94000 nanoseconds
e allfound = true: 64161000 nanoseconds
e noghosts = true: 55975000 nanoseconds
Result size: c = 50000, d = 50000, e = 50000

*/