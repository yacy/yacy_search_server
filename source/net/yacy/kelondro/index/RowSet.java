/**
 *  RowSet
 *  Copyright 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 20.06.2006 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.index;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.util.MemoryControl;


public class RowSet extends RowCollection implements Index, Iterable<Row.Entry>, Serializable {

    private static final long serialVersionUID=-6036029762440788566L;

    public RowSet(final RowSet rs) {
        super(rs);
    }

    public RowSet(final Row rowdef, final int objectCount, final byte[] cache, final int sortBound) {
        super(rowdef, objectCount, cache, sortBound);
        assert rowdef.objectOrder != null;
    }

    public RowSet(final Row rowdef, final int objectCount) throws SpaceExceededException {
        super(rowdef, objectCount);
        assert rowdef.objectOrder != null;
    }

    public RowSet(final Row rowdef) {
        super(rowdef);
        assert rowdef.objectOrder != null;
    }

    /**
     * import an exported collection
     * @param rowdef
     * @param exportedCollectionRowEnvironment
     * @param columnInEnvironment
     */
    public RowSet(final Row rowdef, final Row.Entry exportedCollectionRowEnvironment) {
        super(rowdef, exportedCollectionRowEnvironment);
        assert rowdef.objectOrder != null;
    }

    public final static RowSet importRowSet(final byte[] b, final Row rowdef) throws SpaceExceededException {
    	assert b.length >= exportOverheadSize : "b.length = " + b.length;
    	if (b.length < exportOverheadSize) return new RowSet(rowdef, 0);
        final int size = (int) NaturalOrder.decodeLong(b, 0, 4);
        assert size >= 0 : "size = " + size;
        if (size < 0) return new RowSet(rowdef, 0);
        final int orderbound = (int) NaturalOrder.decodeLong(b, 10, 4);
        assert orderbound >= 0 : "orderbound = " + orderbound;
        if (orderbound < 0) return new RowSet(rowdef, 0); // error
        final long alloc = ((long) size) * ((long) rowdef.objectsize);
        assert alloc <= Integer.MAX_VALUE : "alloc = " + alloc;
        if (alloc > Integer.MAX_VALUE) throw new SpaceExceededException((int) alloc, "importRowSet: alloc > Integer.MAX_VALUE");
        assert alloc == b.length - exportOverheadSize;
        if (alloc != b.length - exportOverheadSize) throw new SpaceExceededException((int) alloc, "importRowSet: alloc != b.length - exportOverheadSize");
        MemoryControl.request((int) alloc, true);
        final byte[] chunkcache;
        try {
            chunkcache = new byte[(int) alloc];
        } catch (final OutOfMemoryError e) {
            throw new SpaceExceededException((int) alloc, "importRowSet: OutOfMemoryError");
        }
        //assert b.length - exportOverheadSize == size * rowdef.objectsize : "b.length = " + b.length + ", size * rowdef.objectsize = " + size * rowdef.objectsize;
        if (b.length - exportOverheadSize != alloc) {
            ConcurrentLog.severe("RowSet", "exportOverheadSize wrong: b.length = " + b.length + ", size * rowdef.objectsize = " + size * rowdef.objectsize);
            return new RowSet(rowdef, 0);
        }
        System.arraycopy(b, (int) exportOverheadSize, chunkcache, 0, chunkcache.length);
        return new RowSet(rowdef, size, chunkcache, orderbound);
    }

    public final static int importRowCount(final long blength, final Row rowdef) {
        assert blength >= exportOverheadSize : "blength = " + blength;
        if (blength < exportOverheadSize) return 0;
        final int c = (int) ((blength - exportOverheadSize) / rowdef.objectsize);
        assert c >= 0;
        return c;
    }

    private RowSet(final Row rowdef, final byte[] chunkcache, final int chunkcount, final int sortBound, final long lastTimeWrote) {
        super(rowdef, chunkcache, chunkcount, sortBound, lastTimeWrote);
    }

    @Override
    public RowSet clone() {
        return new RowSet(super.rowdef, super.chunkcache, super.chunkcount, super.sortBound, super.lastTimeWrote);
    }

	@Override
    public void reset() {
		super.reset();
	}

    @Override
    public final synchronized boolean has(final byte[] key) {
        assert key.length == this.rowdef.primaryKeyLength;
        final int index = find(key, 0);
        return index >= 0;
    }

    @Override
    public final synchronized Row.Entry get(final byte[] key, final boolean forcecopy) {
        assert key.length == this.rowdef.primaryKeyLength;
        final int index = find(key, 0);
        if (index < 0) return null;
        return get(index, forcecopy);
    }

    @Override
    public Map<byte[], Row.Entry> get(final Collection<byte[]> keys, final boolean forcecopy) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(row().objectOrder);
        Row.Entry entry;
        for (final byte[] key: keys) {
            entry = get(key, forcecopy);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }

    /**
     * Adds the row to the index. The row is identified by the primary key of the row.
     * @param row a index row
     * @return true if this set did _not_ already contain the given row.
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public final boolean put(final Row.Entry entry) throws SpaceExceededException {
        assert (entry != null);
        final byte[] key = entry.getPrimaryKeyBytes();
        assert (key != null);
        final byte[] entrybytes = entry.bytes();
        assert entrybytes.length >= this.rowdef.primaryKeyLength;
        synchronized (this) {
            final int index = find(key, 0);
            if (index < 0) {
                super.addUnique(entry);
                return true;
            }
            final int sb = this.sortBound; // save the sortBound, because it is not altered (we replace at the same place)
            set(index, entry);       // this may alter the sortBound, which we will revert in the next step
            this.sortBound = sb;     // revert a sortBound altering
            return false;
        }
    }

    private final int collectionReSortLimit() {
        return Math.min(3000, Math.max(100, this.chunkcount / 3));
    }
    
    @Override
    public final Row.Entry replace(final Row.Entry entry) throws SpaceExceededException {
        assert (entry != null);
        final byte[] key = entry.getPrimaryKeyBytes();
        assert (key != null);
        final byte[] entrybytes = entry.bytes();
        assert entrybytes.length >= this.rowdef.primaryKeyLength;
        synchronized (this) {
            int index = -1;
            Row.Entry oldentry = null;
            // when reaching a specific amount of un-sorted entries, re-sort all
            if ((this.chunkcount - this.sortBound) > collectionReSortLimit()) {
                sort();
            }
            index = find(key, 0);
            if (index < 0) {
                super.addUnique(entry);
            } else {
                oldentry = get(index, true);
                final int sb = this.sortBound; // save the sortBound, because it is not altered (we replace at the same place)
                set(index, entry);       // this may alter the sortBound, which we will revert in the next step
                this.sortBound = sb;     // revert a sortBound altering
            }
            return oldentry;
        }
    }

    public final synchronized long inc(final byte[] key, final int col, final long add, final Row.Entry initrow) throws SpaceExceededException {
        assert key.length == this.rowdef.primaryKeyLength;
        final int index = find(key, 0);
        if (index >= 0) {
            // the entry existed before
            final Row.Entry entry = get(index, false); // no clone necessary
            final long l = entry.incCol(col, add);
            set(index, entry);
            return l;
        } else if (initrow != null) {
            // create new entry
            super.addUnique(initrow);
            return initrow.getColLong(col);
        } else {
            // if initrow == null just do nothing
            // but return a Long.MIN_VALUE
            return Long.MIN_VALUE;
        }
    }

    /**
     * remove a byte[] from the set.
     * if the entry was found, return the entry, but delete the entry from the set
     * if the entry was not found, return null.
     */
    @Override
    public final synchronized boolean delete(final byte[] a) {
        boolean exists = false;
        int index;
        assert a.length == this.rowdef.primaryKeyLength;
        while (true) {
            index = find(a, 0);
            if (index < 0) {
                return exists;
            }
            exists = true;
            super.removeRow(index, true); // keep order of collection!
        }
    }

    // perhaps not used - see ReferenceContainer.shrinkReferences()
    public final synchronized void delete(final List<byte[]> keys) {
        final int[] indexes = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            indexes[i] = find(keys.get(i), 0);
        }
        // we will delete the entries in backward order
        // That means it is necessary that the order below the indexes is stable
        // therefore we can delete without keeping the order (since it still is stable below the deleted index)
        Arrays.sort(indexes);
        for (int i = indexes.length - 1; i >= 0; i--) {
            if (indexes[i] < 0) break;
            super.removeRow(indexes[i], false);
        }
    }

    @Override
    public final synchronized Row.Entry remove(final byte[] a) {
        Row.Entry entry = null;
        int index;
        assert a.length == this.rowdef.primaryKeyLength;
        while (true) {
            index = find(a, 0);
            if (index < 0) {
                return entry;
            }
            entry = super.get(index, true);
            super.removeRow(index, true); // keep order of collection!
        }
    }

    private final int find(final byte[] a, final int astart) {
        // returns the chunknumber; -1 if not found

        if (this.rowdef.objectOrder == null) return iterativeSearch(a, astart, 0, this.chunkcount);

        if ((this.chunkcount - this.sortBound) > collectionReSortLimit()) {
            sort();
        }

        if (this.rowdef.objectOrder != null && this.rowdef.objectOrder instanceof Base64Order) {
            // first try to find in sorted area
            assert this.rowdef.objectOrder.wellformed(a, astart, this.rowdef.primaryKeyLength) : "not wellformed: " + ASCII.String(a, astart, this.rowdef.primaryKeyLength);
        }

        // first try to find in sorted area
        final int p = binarySearch(a, astart);
        if (p >= 0) return p;

        // then find in unsorted area
        return iterativeSearch(a, astart, this.sortBound, this.chunkcount);
    }

    private final int iterativeSearch(final byte[] key, final int astart, final int leftBorder, final int rightBound) {
        // returns the chunknumber
        for (int i = leftBorder; i < rightBound; i++) {
            assert key.length - astart >= this.rowdef.primaryKeyLength;
            if (match(key, astart, i)) return i;
        }
        return -1;
    }

    private final int binarySearch(final byte[] key, final int astart) {
        // returns the exact position of the key if the key exists,
        // or -1 if the key does not exist
        assert (this.rowdef.objectOrder != null);
        int l = 0;
        int rbound = this.sortBound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = (l + rbound) >> 1;
            assert key.length - astart >= this.rowdef.primaryKeyLength;
            d = compare(key, astart, p);
            if (d == 0) return p;
            if (d < 0) rbound = p; else l = p + 1;
        }
        return -1;
    }

    protected final int binaryPosition(final byte[] key, final int astart) {
        // returns the exact position of the key if the key exists,
        // or a position of an entry that is greater than the key if the
        // key does not exist
        assert (this.rowdef.objectOrder != null);
        int l = 0;
        int rbound = this.sortBound;
        int p = 0;
        int d;
        while (l < rbound) {
            p = (l + rbound) >> 1;
            assert key.length - astart >= this.rowdef.primaryKeyLength;
            d = compare(key, astart, p);
            if (d == 0) return p;
            if (d < 0) rbound = p; else l = p + 1;
        }
        return l;
    }

    public final synchronized Iterator<byte[]> keys() {
        sort();
        return super.keys(true);
    }

    @Override
    public final synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        return new keyIterator(up, firstKey);
    }

    public final class keyIterator implements CloneableIterator<byte[]> {

        private final boolean up;
        private final byte[] first;
        private int p;
        final int bound;

        public keyIterator(final boolean up, byte[] firstKey) {
            // see that all elements are sorted
            sort();
            this.up = up;
            if (firstKey != null && firstKey.length == 0) firstKey = null;
            this.first = firstKey;
            this.bound = RowSet.this.sortBound;
            if (this.first == null) {
                this.p = 0;
            } else {
                assert this.first.length == RowSet.this.rowdef.primaryKeyLength : "first.length = " + this.first.length + ", rowdef.primaryKeyLength = " + RowSet.this.rowdef.primaryKeyLength;
                this.p = binaryPosition(this.first, 0); // check this to find bug in DHT selection enumeration
            }
        }

		@Override
        public final keyIterator clone(final Object second) {
            return new keyIterator(this.up, (byte[]) second);
        }

        @Override
        public final boolean hasNext() {
        	if (this.p < 0) return false;
        	if (this.p >= size()) return false;
            return (this.up) ? this.p < this.bound : this.p >= 0;
        }

        @Override
        public final byte[] next() {
            final byte[] key = getKey(this.p);
            if (this.up) this.p++; else this.p--;
            return key;
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }

    @Override
    public final synchronized Iterator<Row.Entry> iterator() {
        // iterates kelondroRow.Entry - type entries
        sort();
        return super.iterator();
    }

    @Override
    public final synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) {
        return new rowIterator(up, firstKey);
    }

    @Override
    public final synchronized CloneableIterator<Row.Entry> rows() {
        return new rowIterator(true, null);
    }

    public final class rowIterator implements CloneableIterator<Row.Entry> {

        private final boolean up;
        private final byte[] first;
        private int p;
        final int bound;

        public rowIterator(final boolean up, final byte[] firstKey) {
            // see that all elements are sorted
            sort();
            this.up = up;
            this.first = firstKey;
            this.bound = RowSet.this.sortBound;
            if (this.first == null) {
                this.p = 0;
            } else {
                assert this.first.length == RowSet.this.rowdef.primaryKeyLength;
                this.p = binaryPosition(this.first, 0); // check this to find bug in DHT selection enumeration
            }
        }

		@Override
        public final rowIterator clone(final Object second) {
            return new rowIterator(this.up, (byte[]) second);
        }

        @Override
        public final boolean hasNext() {
        	if (this.p < 0) return false;
        	if (this.p >= size()) return false;
            return (this.up) ? this.p < this.bound : this.p >= 0;
        }

        @Override
        public final Row.Entry next() {
            final Row.Entry entry = get(this.p, true);
            if (this.up) this.p++; else this.p--;
            return entry;
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }

    /**
     * merge this row collection with another row collection.
     * The resulting collection is sorted and does not contain any doubles, which are also removed during the merge.
     * The new collection may be a copy of one of the old one, or can be an alteration of one of the input collections
     * After this merge, none of the input collections should be used, because they can be altered
     * @param c
     * @return
     * @throws SpaceExceededException
     */
    public final RowSet merge(final RowSet c) throws SpaceExceededException {
        assert c != null;
        return mergeEnum(this, c);
    }

    /**
     * merge this row collection with another row collection using an simultanous iteration of the input collections
     * the current collection is not altered in any way, the returned collection is a new collection with copied content.
     * @param c
     * @return
     * @throws SpaceExceededException
     */
    protected final static RowSet mergeEnum(final RowCollection c0, final RowCollection c1) throws SpaceExceededException {
        assert c0.rowdef == c1.rowdef : c0.rowdef.toString() + " != " + c1.rowdef.toString();
        final RowSet r = new RowSet(c0.rowdef, c0.size() + c1.size());
        try {
        	c0.sort();
        } catch (final Throwable e) {
        	ConcurrentLog.severe("RowSet", "collection corrupted. cleaned. " + e.getMessage(), e);
        	c0.clear();
        }
        try {
        	c1.sort();
        } catch (final Throwable e) {
        	ConcurrentLog.severe("RowSet", "collection corrupted. cleaned. " + e.getMessage(), e);
        	c1.clear();
        }
        int c0i = 0, c1i = 0;
        int c0p, c1p;
        int o;
        final int objectsize = c0.rowdef.objectsize;
        final int c0s = c0.size();
        final int c1s = c1.size();
        while (c0i < c0s && c1i < c1s) {
            c0p = c0i * objectsize;
            c1p = c1i * objectsize;
            o = c0.rowdef.objectOrder.compare(
                    c0.chunkcache, c0p,
                    c1.chunkcache, c1p, c0.rowdef.primaryKeyLength);
            if (o == 0) {
                r.addSorted(c0.chunkcache, c0p, objectsize);
                c0i++;
                c1i++;
                continue;
            }
            if (o < 0) {
                r.addSorted(c0.chunkcache, c0p, objectsize);
                c0i++;
                continue;
            }
            if (o > 0) {
                r.addSorted(c1.chunkcache, c1p, objectsize);
                c1i++;
                continue;
            }
        }
        while (c0i < c0.size()) {
            r.addSorted(c0.chunkcache, c0i * objectsize, objectsize);
            c0i++;
        }
        while (c1i < c1.size()) {
            r.addSorted(c1.chunkcache, c1i * objectsize, objectsize);
            c1i++;
        }
        return r;
    }

    public static void main(final String[] args) {
    	// sort/uniq-test
        /*
    	kelondroRow rowdef = new kelondroRow("Cardinal key-4 {b256}, byte[] payload-1", kelondroNaturalOrder.naturalOrder, 0);
    	kelondroRowSet rs = new kelondroRowSet(rowdef, 0);
        Random random = new Random(0);
        kelondroRow.Entry entry;
        for (int i = 0; i < 10000000; i++) {
        	entry = rowdef.newEntry();
        	entry.setCol(0, Math.abs(random.nextLong() % 1000000));
        	entry.setCol(1, "a".getBytes());
        	rs.addUnique(entry);
        }
        System.out.println("before sort, size = " + rs.size());
        rs.sort();
        System.out.println("after sort, before uniq, size = " + rs.size());
        rs.uniq(10000);
        System.out.println("after uniq, size = " + rs.size());
        */

        final String[] test = {
        		"eins......xxxx",
        		"zwei......xxxx",
        		"drei......xxxx",
        		"vier......xxxx",
        		"fuenf.....xxxx",
        		"sechs.....xxxx",
        		"sieben....xxxx",
        		"acht......xxxx",
        		"neun......xxxx",
        		"zehn......xxxx" };
        final RowSet d = new RowSet(new Row("byte[] key-10, Cardinal x-4 {b256}", NaturalOrder.naturalOrder));
        for (final String element : test)
            try {
                d.add(element.getBytes());
            } catch (final SpaceExceededException e) {
                e.printStackTrace();
            }
        for (final String element : test)
            try {
                d.add(element.getBytes());
            } catch (final SpaceExceededException e) {
                e.printStackTrace();
            }
        d.sort();
        d.delete("fuenf".getBytes());
        final Iterator<Row.Entry> ii = d.iterator();
        String s;
        System.out.print("INPUT-ITERATOR: ");
        Row.Entry entry;
        while (ii.hasNext()) {
            entry = ii.next();
            s = entry.getPrimaryKeyASCII().trim();
            System.out.print(s + ", ");
            if (s.equals("drei")) ii.remove();
        }
        System.out.println("");
        System.out.println("INPUT-TOSTRING: " + d.toString());
        d.sort();
        System.out.println("SORTED        : " + d.toString());
        d.uniq();
        System.out.println("UNIQ          : " + d.toString());
        d.trim();
        System.out.println("TRIM          : " + d.toString());

        // second test
        final Row row = new Row("byte[] key-10, Cardinal x-3 {b256}", NaturalOrder.naturalOrder);
        RowSet c = new RowSet(row);
        final Random rand = new Random(0);
        long start = System.currentTimeMillis();
        long t;
        String w;
        for (long k = 1; k <= 60000; k++) {
            t = System.currentTimeMillis();
            w = "a" + Long.toString(rand.nextLong());
            try {
                c.put(row.newEntry(new byte[][]{w.getBytes(), "000".getBytes()}));
                //c.add(w.getBytes());
            } catch (final SpaceExceededException e) {
                e.printStackTrace();
            }
            if (k % 10000 == 0)
                System.out.println("added " + k + " entries in " +
                    ((t - start) / 1000) + " seconds, " +
                    (((t - start) > 1000) ? (k / ((t - start) / 1000)) : k) +
                    " entries/second, size = " + c.size());
        }
        System.out.println("bevore sort: " + (System.currentTimeMillis() - start) + " milliseconds, size: " + c.size());
        c.sort();
        System.out.println("after sort: " + (System.currentTimeMillis() - start) + " milliseconds, size: " + c.size());
        c.uniq();
        System.out.println("after uniq: " + (System.currentTimeMillis() - start) + " milliseconds, size: " + c.size());
        System.out.println();

        // remove test
        start = System.currentTimeMillis();
        c = new RowSet(new Row("byte[] a-12, byte[] b-12", Base64Order.enhancedCoder));
        byte[] key;
        final int testsize = 5000;
        final byte[][] delkeys = new byte[testsize / 5][];
        Random random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            if (i % 5 != 0) continue;
            delkeys[i / 5] = key;
        }
        random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            try {
                c.put(c.rowdef.newEntry(new byte[][]{key, key}));
            } catch (final SpaceExceededException e) {
                e.printStackTrace();
            }
            if (i % 1000 == 0) {
                for (final byte[] delkey : delkeys)
                    c.delete(delkey);
                c.sort();
            }
        }
        for (final byte[] delkey : delkeys)
            c.delete(delkey);
        c.sort();
        random = new Random(0);
        for (int i = 0; i < testsize; i++) {
            key = randomHash(random);
            if (i % 5 == 0) continue;
            if (c.get(key, true) == null) System.out.println("missing entry " + UTF8.String(key));
        }
        c.sort();
        System.out.println("RESULT SIZE: " + c.size());
        System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        System.exit(0);
    }

    public static byte[] randomHash(final long r0, final long r1) {
        // a long can have 64 bit, but a 12-byte hash can have 6 * 12 = 72 bits
        // so we construct a generic Hash using two long values
        return ASCII.getBytes(
                Base64Order.enhancedCoder.encodeLongSB(Math.abs(r0), 11).substring(5) +
                Base64Order.enhancedCoder.encodeLongSB(Math.abs(r1), 11).substring(5));
    }
    public static byte[] randomHash(final Random r) {
        return randomHash(r.nextLong(), r.nextLong());
    }

    @Override
    public String filename() {
        return null;
    }

    @Override
    public void deleteOnExit() {
        // do nothing, there is no file
    }

}
