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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import net.yacy.cora.document.encoding.ASCII;
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
    private final   byte[] keyBuf;

    protected RowCollection(final RowCollection rc) {
        this.rowdef = rc.rowdef;
        this.chunkcache = rc.chunkcache;
        this.chunkcount = rc.chunkcount;
        this.sortBound = rc.sortBound;
        this.lastTimeWrote = rc.lastTimeWrote;
        this.keyBuf = new byte[this.rowdef.primaryKeyLength];
    }

    protected RowCollection(final Row rowdef) {
        this.rowdef = rowdef;
        this.sortBound = 0;
        this.lastTimeWrote = System.currentTimeMillis();
       	this.chunkcache = EMPTY_CACHE;
        this.chunkcount = 0;
        this.keyBuf = new byte[this.rowdef.primaryKeyLength];
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
        this.keyBuf = new byte[this.rowdef.primaryKeyLength];
    }

    protected RowCollection(final Row rowdef, final Row.Entry exportedCollectionRowEnvironment) {
    	final int payloadWidth = exportedCollectionRowEnvironment.cellwidth(1);
        if (payloadWidth < exportOverheadSize) {
            throw new kelondroException("RowCollection import: payload too small: cellwidth(1)=" + payloadWidth + " < overhead=" + exportOverheadSize);
        }
        final int chunkcachelength = payloadWidth - (int) exportOverheadSize;
        if (rowdef.objectsize <= 0 || chunkcachelength % rowdef.objectsize != 0) {
            ConcurrentLog.warn("KELONDRO", "RowCollection import: payload not aligned to objectsize; payload=" + chunkcachelength + ", objectsize=" + rowdef.objectsize + " (continuing)");
        }
        
        final Row.Entry exportedCollection = exportRow(chunkcachelength).newEntry(exportedCollectionRowEnvironment, 1);

        this.rowdef = rowdef;
        this.chunkcount = (int) exportedCollection.getColLong(exp_chunkcount);
        if ((this.chunkcount > chunkcachelength / rowdef.objectsize)) {
            ConcurrentLog.warn("KELONDRO", "RowCollection: corrected wrong chunkcount; chunkcount = " + this.chunkcount + ", chunkcachelength = " + chunkcachelength + ", rowdef.objectsize = " + rowdef.objectsize);
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
            ConcurrentLog.warn("KELONDRO", "RowCollection: corrected wrong sortBound; sortBound = " + this.sortBound + ", chunkcount = " + this.chunkcount);
            this.sortBound = this.chunkcount;
        }
        this.chunkcache = exportedCollection.getColBytes(exp_collection, false);
        this.keyBuf = new byte[this.rowdef.primaryKeyLength];
    }

    protected RowCollection(final Row rowdef, final byte[] chunkcache, final int chunkcount, final int sortBound, final long lastTimeWrote) {
        this.rowdef = rowdef;
        this.chunkcache = new byte[chunkcache.length];
        System.arraycopy(chunkcache, 0, this.chunkcache, 0, chunkcache.length);
        this.chunkcount = chunkcount;
        this.sortBound = sortBound;
        this.lastTimeWrote = lastTimeWrote;
        this.keyBuf = new byte[this.rowdef.primaryKeyLength];
    }

    @Override
    public RowCollection clone() {
        return new RowCollection(this.rowdef, this.chunkcache, this.chunkcount, this.sortBound, this.lastTimeWrote);
    }

	public void reset() {
		this.chunkcache = EMPTY_CACHE;
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

    protected static final long exportOverheadSize = 14;

    private static Row exportRow(final int chunkcachelength) {
        final Column c0 = new Column("int size-4 {b256}");
        final Column c1 = new Column("short lastread-2 {b256}");
        final Column c2 = new Column("short lastwrote-2 {b256}");
        final Column c3 = new Column("byte[] orderkey-2");
        final Column c4 = new Column("int orderbound-4 {b256}");
        final Column c5 = new Column("byte[] collection-" + chunkcachelength);
        final Row er = new Row(new Column[]{ c0, c1, c2, c3, c4, c5 }, NaturalOrder.naturalOrder);
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
    
    protected synchronized final void getKeyInto(final int index, final byte[] dst, final int off) {
        assert dst.length - off >= this.rowdef.primaryKeyLength;
        final int addr = index * this.rowdef.objectsize;
        System.arraycopy(this.chunkcache, addr, dst, off, this.rowdef.primaryKeyLength);
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
        final byte[] column = this.keyBuf; // we do not allocate a key buffer for each call; reuse the same buffer
        a.writeToArray(0, column, 0);
        assert a.cellwidth(0) == this.rowdef.primaryKeyLength;
        assert column.length >= this.rowdef.primaryKeyLength;
        final boolean sameKey = match(column, 0, index);
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
        this.chunkcache = EMPTY_CACHE;
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
            ConcurrentLog.warn("KELONDRO", "kelondroRowCollection: " + e.getMessage(), e);
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
        assert (a.length - astart) >= this.rowdef.primaryKeyLength : "short key compare not allowed";
        return this.rowdef.objectOrder.compare(a, astart, this.chunkcache, chunknumber * this.rowdef.objectsize, this.rowdef.primaryKeyLength);
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
        assert (a.length - astart) >= this.rowdef.primaryKeyLength : "short key match not allowed";
        int p = chunknumber * this.rowdef.objectsize;
        for (int len = this.rowdef.primaryKeyLength; len-- != 0; astart++, p++) {
            if (a[astart] != this.chunkcache[p]) return false;
        }
        return true;
    }

    public synchronized void close() {
        this.chunkcache = null;
    }

    private static long d(final long a, final long b) {
    	if (b == 0) return a;
    	return 1000 * a / b;
    }

    private static Random random = null;
    private static String randomHash() {
    	return
    		Base64Order.enhancedCoder.encodeLongSB(random.nextLong(), 4).toString() +
    		Base64Order.enhancedCoder.encodeLongSB(random.nextLong(), 4).toString() +
    		Base64Order.enhancedCoder.encodeLongSB(random.nextLong(), 4).toString();
    }

    public static void test(final int testsize) throws SpaceExceededException {
        final Row r = new Row(
            new Column[]{ new Column("hash", Column.celltype_string, Column.encoder_bytes, 12, "hash") },
            Base64Order.enhancedCoder
        );

        // --------- Helpers ----------
        final java.util.function.Supplier<Long> nano = System::nanoTime;
        final java.util.function.Supplier<Long> usedMem = () -> {
            final Runtime rt = Runtime.getRuntime();
            return (rt.totalMemory() - rt.freeMemory());
        };
        final java.util.function.BiFunction<String,Runnable,Long> measure = (name, run) -> {
            final long m0 = usedMem.get();
            final long t0 = nano.get();
            run.run();
            final long t1 = nano.get();
            final long m1 = usedMem.get();
            System.out.println(String.format("%-24s : %12d ns  | Δmem %,12d B", name, (t1 - t0), (m1 - m0)));
            return t1 - t0;
        };
        final java.util.function.Supplier<String> sep = () -> "--------------------------------------------------------------------------------";

        final java.util.Random J = new java.util.Random(0);
        random = new Random(0);
        final java.util.function.Supplier<byte[]> rnd12 = () -> ASCII.getBytes(randomHash());
        final java.util.function.Function<Long,byte[]> mono12 = (Long x) ->
            ASCII.getBytes(Base64Order.enhancedCoder.encodeLongSB(x, 12).toString());

        // --------- (0) Korrektheit: Order-Compare ----------
        System.out.println(sep.get());
        System.out.println("compare() sanity checks");
        random = new Random(0);
        for (int i = 0; i < Math.min(10_000, testsize); i++) {
            final byte[] a = ASCII.getBytes(randomHash());
            final byte[] b = ASCII.getBytes(randomHash());
            final int c = Base64Order.enhancedCoder.compare(a, b);
            if (c == 0 && Base64Order.enhancedCoder.compare(b, a) != 0)
                System.out.println("compare failed / =; a = " + ASCII.String(a) + ", b = " + ASCII.String(b));
            if (c == -1 && Base64Order.enhancedCoder.compare(b, a) != 1)
                System.out.println("compare failed / <; a = " + ASCII.String(a) + ", b = " + ASCII.String(b));
            if (c == 1 && Base64Order.enhancedCoder.compare(b, a) != -1)
                System.out.println("compare failed / >; a = " + ASCII.String(a) + ", b = " + ASCII.String(b));
        }

        // --------- (1) Original-Block (leicht aufgeräumt) ----------
        System.out.println(sep.get());
        System.out.println("kelondroRowCollection baseline test with size = " + testsize);

        RowCollection a = new RowCollection(r, testsize);
        long t0 = nano.get();
        random = new Random(0);
        for (int i = 0; i < testsize / 2; i++) a.add(rnd12.get());
        random = new Random(0);
        for (int i = 0; i < testsize / 2; i++) a.add(rnd12.get());
        a.sort();
        a.uniq();
        for (int i = 0; i < a.size() - 1; i++) {
            if (a.get(i, false).compareTo(a.get(i + 1, false)) >= 0)
                System.out.println("Compare error at pos " + i + ": a.get(i)=" + a.get(i, false) + ", a.get(i + 1)=" + a.get(i + 1, false));
        }
        long t1 = nano.get();
        System.out.println("create+sort+uniq a : " + (t1 - t0) + " ns, " + d(testsize, (t1 - t0)) + " entries/ms; a.size() = " + a.size());

        final RowCollection c = new RowCollection(r, testsize);
        random = new Random(0);
        t0 = nano.get();
        for (int i = 0; i < testsize; i++) c.add(rnd12.get());
        t1 = nano.get();
        System.out.println("create c           : " + (t1 - t0) + " ns, " + d(testsize, (t1 - t0)) + " entries/ms");

        final RowCollection d = new RowCollection(r, testsize);
        for (int i = 0; i < testsize; i++) d.add(c.get(i, false).getPrimaryKeyBytes());
        final long t2 = nano.get();
        System.out.println("copy c -> d        : " + (t2 - t1) + " ns, " + d(testsize, (t2 - t1)) + " entries/ms");

        c.sort(); final long t3 = nano.get();
        System.out.println("sort c             : " + (t3 - t2) + " ns, " + d(testsize, (t3 - t2)) + " entries/ms");

        d.sort(); final long t4 = nano.get();
        System.out.println("sort d             : " + (t4 - t3) + " ns, " + d(testsize, (t4 - t3)) + " entries/ms");

        c.uniq(); final long t5 = nano.get();
        System.out.println("uniq c             : " + (t5 - t4) + " ns, " + d(testsize, (t5 - t4)) + " entries/ms");

        d.uniq(); final long t6 = nano.get();
        System.out.println("uniq d             : " + (t6 - t5) + " ns, " + d(testsize, (t6 - t5)) + " entries/ms");

        // --------- (2) Wachstum: voralloziert vs. inkrementell ----------
        System.out.println(sep.get());
        System.out.println("Growth behaviour: preallocation vs incremental");

        measure.apply("incremental add()", () -> {
            try {
                RowCollection inc = new RowCollection(r, 1);
                random = new Random(1);
                for (int i = 0; i < testsize; i++) inc.add(rnd12.get());
                // touch ensureSize path by adding a bit more
                for (int i = 0; i < Math.min(1000, Math.max(10, testsize/1000)); i++) inc.add(rnd12.get());
                System.out.println("inc.mem()=" + inc.mem() + " bytes; size=" + inc.size());
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        measure.apply("preallocated add()", () -> {
            try {
                RowCollection pre = new RowCollection(r, testsize + 1024);
                random = new Random(1);
                for (int i = 0; i < testsize; i++) pre.add(rnd12.get());
                System.out.println("pre.mem()=" + pre.mem() + " bytes; size=" + pre.size());
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        // --------- (3) sortBound-Nutzen: bereits sortierte Daten ----------
        System.out.println(sep.get());
        System.out.println("sortBound effectiveness");

        measure.apply("addSorted()+sort() (no-op expected)", () -> {
            try {
                RowCollection sb = new RowCollection(r, testsize);
                for (int i = 0; i < testsize; i++) sb.addSorted(mono12.apply((long)i), 0, 12);
                long tS0 = nano.get();
                sb.sort(); // sollte nahezu no-op sein
                long tS1 = nano.get();
                System.out.println("sorted? " + sb.isSorted() + " | sort time = " + (tS1 - tS0) + " ns");
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        measure.apply("partially sorted then random tail", () -> {
            try {
                RowCollection ps = new RowCollection(r, testsize);
                int sortedPart = Math.max(1, testsize * 3 / 4);
                for (int i = 0; i < sortedPart; i++) ps.addSorted(mono12.apply((long)i), 0, 12);
                random = new Random(2);
                for (int i = sortedPart; i < testsize; i++) ps.add(rnd12.get());
                long tS0 = nano.get();
                ps.sort(); // sollte schneller sein als vollständig random
                long tS1 = nano.get();
                System.out.println("sorted? " + ps.isSorted() + " | sort time = " + (tS1 - tS0) + " ns");
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        // --------- (4) Entfernen: keepOrder=true vs. false (+ Shrink) ----------
        System.out.println(sep.get());
        System.out.println("removeRow performance (keepOrder true/false) and shrink");

        final int removeCount = Math.max(1, testsize / 10);

        measure.apply("remove front (keepOrder=true)", () -> {
            try {
                RowCollection rm = new RowCollection(r, testsize);
                random = new Random(3);
                for (int i = 0; i < testsize; i++) rm.add(rnd12.get());
                long m0 = rm.mem();
                long tR0 = nano.get();
                for (int i = 0; i < removeCount; i++) rm.removeRow(0, true); // teuer: shift
                long tR1 = nano.get();
                System.out.println("mem before=" + m0 + " after=" + rm.mem() + " | time=" + (tR1 - tR0) + " ns | size=" + rm.size());
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        measure.apply("remove random (keepOrder=false)", () -> {
            try {
                RowCollection rm = new RowCollection(r, testsize);
                random = new Random(4);
                for (int i = 0; i < testsize; i++) rm.add(rnd12.get());
                long tR0 = nano.get();
                for (int i = 0; i < removeCount; i++) {
                    int p = J.nextInt(Math.max(1, rm.size()));
                    rm.removeRow(Math.min(p, Math.max(0, rm.size() - 1)), false);
                }
                long tR1 = nano.get();
                System.out.println("time=" + (tR1 - tR0) + " ns | size=" + rm.size());
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        // --------- (5) Alias-Retention-Check: get(index,false) hält alte Arrays fest ----------
        System.out.println(sep.get());
        System.out.println("alias retention check (demonstrates risk of keeping Entry views across growth)");

        try {
            RowCollection rc = new RowCollection(r, 16);
            for (int i = 0; i < 10_000; i++) rc.add(rnd12.get());
            WeakReference<byte[]> wr = new WeakReference<>(rc.chunkcache);
            Row.Entry alias = rc.get(0, false); // hält Referenz auf aktuelles chunkcache
            // erzeuge Realloc
            for (int i = 0; i < 200_000; i++) rc.add(rnd12.get());
            // drop starke Referenzen außer alias/wr
            System.gc(); try { Thread.sleep(10); } catch (InterruptedException ie) {}
            boolean retained = (wr.get() != null);
            System.out.println("old array retained while alias alive? " + retained);
            alias = null; // loslassen
            System.gc(); try { Thread.sleep(10); } catch (InterruptedException ie) {}
            System.out.println("old array after alias=null: " + (wr.get() == null ? "GC'ed" : "still retained"));
        } catch (SpaceExceededException ex) {
            System.out.println("Alias check skipped due to SpaceExceededException: " + ex.getMessage());
        }

        // --------- (6) exportCollection() + Import roundtrip ----------
        System.out.println(sep.get());
        System.out.println("export/import roundtrip");

        measure.apply("export + import (with env row)", () -> {
            try {
                RowCollection x = new RowCollection(r, testsize);
                random = new Random(5);
                for (int i = 0; i < testsize; i++) x.add(rnd12.get());
                x.sort();
                byte[] blob = x.exportCollection();

                // optional: Sanity-Check des Headers (ASCII-Signatur sollte lesbar sein)
                Row ex = exportRow(x.size() * r.objectsize);
                Row.Entry eb = ex.newEntry(blob);
                String sig = eb.getColASCII(exp_order_type); // z.B. "__" oder 2-Byte Signatur
                // System.out.println("export order signature = " + sig);

                // Environment-Row: Spalte 1 besitzt GENAU blob.length Breite und enthält die Export-Payload
                Row env = new Row(new Column[] {
                    new Column("pad",     Column.celltype_string,  Column.encoder_bytes, 1,           "pad"),
                    new Column("payload", Column.celltype_binary,  Column.encoder_bytes, blob.length, "payload")
                }, NaturalOrder.naturalOrder);

                Row.Entry envEntry = env.newEntry();
                envEntry.setCol(1, blob); // Payload in Spalte 1 ablegen

                // Jetzt korrekt importieren:
                RowCollection y = new RowCollection(r, envEntry);

                boolean ok = (y.size() == x.size()) && y.isSorted();
                for (int i = 0; ok && i < Math.min(1000, x.size()); i += Math.max(1, x.size()/1000)) {
                    ok &= java.util.Arrays.equals(
                        x.get(i, false).getPrimaryKeyBytes(),
                        y.get(i, false).getPrimaryKeyBytes()
                    );
                }
                System.out.println("roundtrip ok? " + ok + " | x.size=" + x.size() + " y.size=" + y.size());
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });

        // --------- (7) top() / random() Kosten ----------
        System.out.println(sep.get());
        System.out.println("top()/random() cloning cost");

        measure.apply("top(k) with cloning", () -> {
            try {
                RowCollection t = new RowCollection(r, testsize);
                random = new Random(6);
                for (int i = 0; i < testsize; i++) t.add(rnd12.get());
                int k = Math.min(10_000, Math.max(100, testsize/20));
                long tt0 = nano.get();
                List<Row.Entry> top = t.top(k);
                long tt1 = nano.get();
                System.out.println("k=" + k + " | time=" + (tt1 - tt0) + " ns | result=" + top.size());
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        measure.apply("random(k) with cloning", () -> {
            try {
                RowCollection t = new RowCollection(r, testsize);
                random = new Random(7);
                for (int i = 0; i < testsize; i++) t.add(rnd12.get());
                int k = Math.min(10_000, Math.max(100, testsize/20));
                long tt0 = nano.get();
                List<Row.Entry> rnd = t.random(k);
                long tt1 = nano.get();
                System.out.println("k=" + k + " | time=" + (tt1 - tt0) + " ns | result=" + rnd.size());
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        // --------- (8) Iterator.remove() vs. delete() ----------
        System.out.println(sep.get());
        System.out.println("iterator remove() cost (keep order)");

        measure.apply("rowIterator remove half (keepOrder=true)", () -> {
            try {
                RowCollection itc = new RowCollection(r, testsize);
                random = new Random(8);
                for (int i = 0; i < testsize; i++) itc.add(rnd12.get());
                int target = itc.size() / 2;
                long tIt0 = nano.get();
                Iterator<Row.Entry> it = itc.iterator();
                int removed = 0;
                while (it.hasNext() && removed < target) { it.next(); it.remove(); removed++; }
                long tIt1 = nano.get();
                System.out.println("removed=" + removed + " | time=" + (tIt1 - tIt0) + " ns | size=" + itc.size());
            } catch (SpaceExceededException ex) { throw new RuntimeException(ex); }
        });

        // --------- (9) Abschluss ----------
        System.out.println(sep.get());
        System.out.println("Result size recap: c=" + c.size() + ", d=" + d.size());
        System.out.println();
    }

    public static void main(final String[] args) {
    	try {
            test(100000);
            ConcurrentLog.shutdown();
        } catch (final SpaceExceededException e) {
            e.printStackTrace();
        }
    }

}

/*
--------------------------------------------------------------------------------
compare() sanity checks
--------------------------------------------------------------------------------
kelondroRowCollection baseline test with size = 100000
create+sort+uniq a : 453714042 ns, 0 entries/ms; a.size() = 50000
create c           : 10972458 ns, 9 entries/ms
copy c -> d        : 7571209 ns, 13 entries/ms
sort c             : 46137250 ns, 2 entries/ms
sort d             : 38058125 ns, 2 entries/ms
uniq c             : 2139791 ns, 46 entries/ms
uniq d             : 2056000 ns, 48 entries/ms
--------------------------------------------------------------------------------
Growth behaviour: preallocation vs incremental
inc.mem()=1233012 bytes; size=100100
incremental add()        :     10449292 ns  | Δmem   30.940.520 B
pre.mem()=1697196 bytes; size=100000
preallocated add()       :      9191375 ns  | Δmem   23.195.552 B
--------------------------------------------------------------------------------
sortBound effectiveness
sorted? true | sort time = 13750 ns
addSorted()+sort() (no-op expected) :     11407875 ns  | Δmem   17.649.160 B
sorted? true | sort time = 38264209 ns
partially sorted then random tail :     47743500 ns  | Δmem   18.499.312 B
--------------------------------------------------------------------------------
removeRow performance (keepOrder true/false) and shrink
mem before=1680000 after=1680000 | time=1772167 ns | size=90000
remove front (keepOrder=true) :     19521000 ns  | Δmem  -99.698.848 B
time=2279625 ns | size=90000
remove random (keepOrder=false) :     10725917 ns  | Δmem   16.777.216 B
--------------------------------------------------------------------------------
alias retention check (demonstrates risk of keeping Entry views across growth)
old array retained while alias alive? false
old array after alias=null: GC'ed
--------------------------------------------------------------------------------
export/import roundtrip
roundtrip ok? true | x.size=100000 y.size=100000
export + import (with env row) :     46651417 ns  | Δmem   15.882.984 B
--------------------------------------------------------------------------------
top()/random() cloning cost
k=5000 | time=262792 ns | result=5000
top(k) with cloning      :     12602875 ns  | Δmem   -6.101.432 B
k=5000 | time=300833 ns | result=5000
random(k) with cloning   :      9101917 ns  | Δmem   25.163.776 B
--------------------------------------------------------------------------------
iterator remove() cost (keep order)
removed=50000 | time=3463667 ns | size=50000
rowIterator remove half (keepOrder=true) :     12654834 ns  | Δmem   33.554.432 B
--------------------------------------------------------------------------------
Result size recap: c=100000, d=100000
*/