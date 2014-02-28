/**
 *  RAMIndex
 *  Copyright 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 07.01.2008 at http://yacy.net
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.util.MergeIterator;
import net.yacy.kelondro.util.StackIterator;


public final class RAMIndex implements Index, Iterable<Row.Entry> {

    private static final Map<String, RAMIndex> objectTracker = Collections.synchronizedSortedMap(new TreeMap<String, RAMIndex>());

    private final String name;
    private final Row rowdef;
    private RowSet index0;
    private RowSet index1;
    private final Row.EntryComparator entryComparator;
    //private final int spread;

    public RAMIndex(final String name, final Row rowdef) {
        this.name = name;
        this.rowdef = rowdef;
        this.entryComparator = new Row.EntryComparator(rowdef.objectOrder);
        reset();
        objectTracker.put(name, this);
    }

    private RAMIndex(final String name, final Row rowdef, final RowSet index0, final RowSet index1, final Row.EntryComparator entryComparator) {
        this.name = name;
        this.rowdef = rowdef;
        this.index0 = index0;
        this.index1 = index1;
        this.entryComparator = entryComparator;
        objectTracker.put(name, this);
    }

    public static final Iterator<Map.Entry<String, RAMIndex>> objects() {
        return objectTracker.entrySet().iterator();
    }

    @Override
    public RAMIndex clone() {
        return new RAMIndex(this.name + ".clone", this.rowdef, this.index0.clone(), this.index1.clone(), this.entryComparator);
    }

    @Override
    public void clear() {
		reset();
	}

    @Override
    public void optimize() {
        if (this.index0 != null) this.index0.optimize();
        if (this.index1 != null) this.index1.optimize();
    }

    public final synchronized void reset() {
        this.index0 = null; // first flush RAM to make room
        this.index0 = new RowSet(this.rowdef);
        this.index1 = null; // to show that this is the initialization phase
    }

    public final synchronized void reset(final int initialspace) throws SpaceExceededException {
        this.index0 = null; // first flush RAM to make room
        this.index0 = new RowSet(this.rowdef, initialspace);
        this.index1 = null; // to show that this is the initialization phase
    }

    @Override
    public final Row row() {
        return this.index0.row();
    }

    protected final void finishInitialization() {
    	if (this.index1 == null) {
            // finish initialization phase
            this.index0.sort();
            this.index0.uniq();
            this.index0.trim();
            this.index1 = new RowSet(this.rowdef); //new RowSetArray(rowdef, spread);
        }
    }

    @Override
    public final synchronized byte[] smallestKey() {
        final byte[] b0 = this.index0.smallestKey();
        if (b0 == null) return null;
        if (this.index1 == null) return b0;
        final byte[] b1 = this.index0.smallestKey();
        if (b1 == null || this.rowdef.objectOrder.compare(b1, b0) > 0) return b0;
        return b1;
    }

    @Override
    public final synchronized byte[] largestKey() {
        final byte[] b0 = this.index0.largestKey();
        if (b0 == null) return null;
        if (this.index1 == null) return b0;
        final byte[] b1 = this.index0.largestKey();
        if (b1 == null || this.rowdef.objectOrder.compare(b0, b1) > 0) return b0;
        return b1;
    }

    @Override
    public final synchronized Row.Entry get(final byte[] key, final boolean forceclone) {
        assert (key != null);
        finishInitialization();
        assert this.index0.isSorted();
        final Row.Entry indexentry = this.index0.get(key, forceclone);
        if (indexentry != null) return indexentry;
        return this.index1.get(key, forceclone);
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

    @Override
    public final synchronized boolean has(final byte[] key) {
		assert (key != null);
        finishInitialization();
        assert this.index0.isSorted();
        if (this.index0.has(key)) return true;
        return this.index1.has(key);
	}

	@Override
    public final synchronized Row.Entry replace(final Row.Entry entry) throws SpaceExceededException {
        assert (entry != null);
        finishInitialization();
        // if the new entry is within the initialization part, just overwrite it
        assert this.index0.isSorted();
        final byte[] key = entry.getPrimaryKeyBytes();
        if (this.index0.has(key)) {
            // replace the entry
            return this.index0.replace(entry);
        }
        // else place it in the index1
        return this.index1.replace(entry);
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
        if (entry == null) return true;
        synchronized (this) {
            finishInitialization();
            // if the new entry is within the initialization part, just overwrite it
            assert this.index0.isSorted();
            final byte[] key = entry.getPrimaryKeyBytes();
            if (this.index0.has(key)) {
                // replace the entry
                this.index0.put(entry);
                return false;
            }
            // else place it in the index1
            return this.index1.put(entry);
        }
    }

    @Override
    public final void addUnique(final Row.Entry entry) throws SpaceExceededException {
    	assert (entry != null);
    	if (entry == null) return;
    	synchronized (this) {
            if (this.index1 == null) {
                // we are in the initialization phase
            	this.index0.addUnique(entry);
            	return;
            }
            // initialization is over, add to secondary index
            this.index1.addUnique(entry);
    	}
    }

	public final void addUnique(final List<Entry> rows) throws SpaceExceededException {
		final Iterator<Entry> i = rows.iterator();
		while (i.hasNext()) addUnique(i.next());
	}

	public final synchronized long inc(final byte[] key, final int col, final long add, final Row.Entry initrow) throws SpaceExceededException {
        assert (key != null);
        finishInitialization();
        assert this.index0.isSorted();
        final long l = this.index0.inc(key, col, add, null);
        if (l != Long.MIN_VALUE) return l;
        return this.index1.inc(key, col, add, initrow);
    }

    @Override
    public final synchronized ArrayList<RowCollection> removeDoubles() throws SpaceExceededException {
	    // finish initialization phase explicitely
        this.index0.sort();
	    if (this.index1 == null) {
	        return this.index0.removeDoubles();
	    }
	    final ArrayList<RowCollection> d0 = this.index0.removeDoubles();
	    final ArrayList<RowCollection> d1 = this.index1.removeDoubles();
        d0.addAll(d1);
        return d0;
	}

    @Override
    public final synchronized boolean delete(final byte[] key) {
        finishInitialization();
        // if the new entry is within the initialization part, just delete it
        boolean b = this.index0.delete(key);
        if (b) {
            assert !this.index0.has(key); // check if remove worked
            return true;
        }
        // else remove it from the index1
        b = this.index1.delete(key);
        assert this.index1.has(key) : "removed " + ((b) ? " true" : " false") + ", and index entry still exists"; // check if remove worked
        return b;
    }

    @Override
    public final synchronized Row.Entry remove(final byte[] key) {
        finishInitialization();
        // if the new entry is within the initialization part, just delete it
        int s = this.index0.size();
        final Row.Entry indexentry = this.index0.remove(key);
        if (indexentry != null) {
            assert this.index0.size() < s: "s = " + s + ", index0.size() = " + this.index0.size();
            assert !this.index0.has(key); // check if remove worked
            return indexentry;
        }
        // else remove it from the index1
        s = this.index1.size();
        final Row.Entry removed = this.index1.remove(key);
        assert removed == null || this.index1.size() < s: "s = " + s + ", index1.size() = " + this.index1.size();
        assert !this.index1.has(key) : "removed " + ((removed == null) ? " is null" : " is not null") + ", and index entry still exists"; // check if remove worked
        return removed;
    }

    @Override
    public final synchronized Row.Entry removeOne() {
        if (this.index1 != null && !this.index1.isEmpty()) {
            return this.index1.removeOne();
        }
        if (this.index0 != null && !this.index0.isEmpty()) {
        	return this.index0.removeOne();
        }
        return null;
    }

    @Override
    public synchronized List<Row.Entry> top(final int count) throws IOException {
        final List<Row.Entry> list = new ArrayList<Row.Entry>();
        List<Row.Entry> list0 = this.index1.top(count);
        list.addAll(list0);
        list0 = this.index0.top(count - list.size());
        list.addAll(list0);
        return list;
    }
    
    @Override
    public synchronized List<Row.Entry> random(final int count) throws IOException {
        final List<Row.Entry> list = new ArrayList<Row.Entry>();
        List<Row.Entry> list0 = this.index1.random(count);
        list.addAll(list0);
        list0 = this.index0.random(count - list.size());
        list.addAll(list0);
        return list;
    }

    @Override
    public long mem() {
        if (this.index0 != null && this.index1 == null) {
            return this.index0.mem();
        }
        if (this.index0 == null && this.index1 != null) {
            return this.index1.mem();
        }
        assert (this.index0 != null && this.index1 != null);
        return this.index0.mem() + this.index1.mem();
    }

    @Override
    public final int size() {
        if (this.index0 != null && this.index1 == null) {
            return this.index0.size();
        }
        if (this.index0 == null && this.index1 != null) {
            return this.index1.size();
        }
        assert (this.index0 != null && this.index1 != null);
        return this.index0.size() + this.index1.size();
    }

    @Override
    public final boolean isEmpty() {
        if (this.index0 != null && this.index1 == null) {
            return this.index0.isEmpty();
        }
        if (this.index0 == null && this.index1 != null) {
            return this.index1.isEmpty();
        }
        assert (this.index0 != null && this.index1 != null);
        if (!this.index0.isEmpty()) return false;
        if (!this.index1.isEmpty()) return false;
        return true;
    }


    @Override
    public final synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        // returns the key-iterator of the underlying kelondroIndex
        if (this.index1 == null) {
            // finish initialization phase
            this.index0.sort();
            this.index0.uniq();
            this.index1 = new RowSet(this.rowdef); //new RowSetArray(rowdef, spread);
            return this.index0.keys(up, firstKey);
        }
        assert (this.index1 != null);
        if (this.index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return this.index1.keys(up, firstKey);
        }
        // index0 should be sorted
        // sort index1 to enable working of the merge iterator
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        final CloneableIterator<byte[]> k0 = this.index0.keys(up, firstKey);
        final CloneableIterator<byte[]> k1 = this.index1.keys(up, firstKey);
        if (k0 == null) return k1;
        if (k1 == null) return k0;
        return new MergeIterator<byte[]>(
                k0,
                k1,
                this.rowdef.objectOrder,
                MergeIterator.simpleMerge,
                true);
    }

    @Override
    public final synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) {
        // returns the row-iterator of the underlying kelondroIndex
        if (this.index1 == null) {
            // finish initialization phase
            this.index0.sort();
            this.index0.uniq();
            this.index1 = new RowSet(this.rowdef); //new RowSetArray(rowdef, spread);
            return this.index0.rows(up, firstKey);
        }
        assert (this.index1 != null);
        if (this.index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return this.index1.rows(up, firstKey);
        }
        // index0 should be sorted
        // sort index1 to enable working of the merge iterator
        //index1.sort();
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        final CloneableIterator<Row.Entry> k0 = this.index0.rows(up, firstKey);
        final CloneableIterator<Row.Entry> k1 = this.index1.rows(up, firstKey);
        if (k0 == null) return k1;
        if (k1 == null) return k0;
        return new MergeIterator<Row.Entry>(
                k0,
                k1,
                this.entryComparator,
                MergeIterator.simpleMerge,
                true);
    }

    @Override
    public final Iterator<Entry> iterator() {
        return rows();
    }

    @Override
    public final synchronized CloneableIterator<Row.Entry> rows() {
        // returns the row-iterator of the underlying kelondroIndex
        if (this.index1 == null) {
            // finish initialization phase
            this.index0.sort();
            this.index0.uniq();
            this.index1 = new RowSet(this.rowdef); //new RowSetArray(rowdef, spread);
            return this.index0.rows();
        }
        assert (this.index1 != null);
        if (this.index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return this.index1.rows();
        }
        // index0 should be sorted
        // sort index1 to enable working of the merge iterator
        //index1.sort();
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return new StackIterator<Row.Entry>(this.index0.rows(), this.index1.rows());
    }

    @Override
    public final synchronized void close() {
        if (this.index0 != null) this.index0.close();
        if (this.index1 != null) this.index1.close();
        objectTracker.remove(this.name);
    }

	@Override
    public final String filename() {
		return null; // this does not have a file name
	}

	@Override
    public final void deleteOnExit() {
        // do nothing, there is no file
    }

}
