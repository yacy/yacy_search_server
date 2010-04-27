// ObjectIndexCache.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.01.2008 on http://yacy.net
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

package net.yacy.kelondro.index;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;
import net.yacy.kelondro.order.StackIterator;


public final class ObjectIndexCache implements ObjectIndex, Iterable<Row.Entry> {
    
    private final Row rowdef;
    private RowSet index0;
    private RowSet index1;
    private final Row.EntryComparator entryComparator;
    //private final int spread;
    
    public ObjectIndexCache(final Row rowdef, final int expectedspace) {
        this.rowdef = rowdef;
        this.entryComparator = new Row.EntryComparator(rowdef.objectOrder);
        //this.spread = Math.max(10, expectedspace / 3000);
        reset();
    }
    
    public ObjectIndexCache(final Row rowdef, final int expectedspace, final int initialspace) throws RowSpaceExceededException {
        this.rowdef = rowdef;
        this.entryComparator = new Row.EntryComparator(rowdef.objectOrder);
        //this.spread = Math.max(10, expectedspace / 3000);
        reset(initialspace);
    }
    
    private ObjectIndexCache(final Row rowdef, RowSet index0, RowSet index1, Row.EntryComparator entryComparator) {
        this.rowdef = rowdef;
        this.index0 = index0;
        this.index1 = index1;
        this.entryComparator = entryComparator;
    }
    
    public ObjectIndexCache clone() {
        return new ObjectIndexCache(this.rowdef, index0.clone(), index1.clone(), entryComparator);
    }
    
    public void clear() {
		reset();
	}
    
    public final synchronized void reset() {
        this.index0 = null; // first flush RAM to make room
        this.index0 = new RowSet(rowdef);
        this.index1 = null; // to show that this is the initialization phase
    }
    
    public final synchronized void reset(final int initialspace) throws RowSpaceExceededException {
        this.index0 = null; // first flush RAM to make room
        this.index0 = new RowSet(rowdef, initialspace);
        this.index1 = null; // to show that this is the initialization phase
    }
    
    public final Row row() {
        return index0.row();
    }
    
    protected final void finishInitialization() {
    	if (index1 == null) {
            // finish initialization phase
            index0.sort();
            index0.uniq();
            index0.trim();
            index1 = new RowSet(rowdef); //new RowSetArray(rowdef, spread);
        }
    }
    
    public final synchronized byte[] smallestKey() {
        final byte[] b0 = index0.smallestKey();
        if (b0 == null) return null;
        if (index1 == null) return b0;
        final byte[] b1 = index0.smallestKey();
        if (b1 == null || rowdef.objectOrder.compare(b1, b0) > 0) return b0;
        return b1;
    }
    
    public final synchronized byte[] largestKey() {
        final byte[] b0 = index0.largestKey();
        if (b0 == null) return null;
        if (index1 == null) return b0;
        final byte[] b1 = index0.largestKey();
        if (b1 == null || rowdef.objectOrder.compare(b0, b1) > 0) return b0;
        return b1;
    }
    
    public final synchronized Row.Entry get(final byte[] key) {
        assert (key != null);
        finishInitialization();
        assert index0.isSorted();
        final Row.Entry indexentry = index0.get(key);
        if (indexentry != null) return indexentry;
        return index1.get(key);
    }

	public final synchronized boolean has(final byte[] key) {
		assert (key != null);
        finishInitialization();
        assert index0.isSorted();
        if (index0.has(key)) return true;
        return index1.has(key);
	}
    
	public final synchronized Row.Entry replace(final Row.Entry entry) throws RowSpaceExceededException {
        assert (entry != null);
        finishInitialization();
        // if the new entry is within the initialization part, just overwrite it
        assert index0.isSorted();
        final byte[] key = entry.getPrimaryKeyBytes();
        if (index0.has(key)) {
            // replace the entry
            return index0.replace(entry);
        }
        // else place it in the index1
        return index1.replace(entry);
    }
   
	public final synchronized void put(final Row.Entry entry) throws RowSpaceExceededException {
        assert (entry != null);
        if (entry == null) return;
        finishInitialization();
        // if the new entry is within the initialization part, just overwrite it
        assert index0.isSorted();
        final byte[] key = entry.getPrimaryKeyBytes();
        if (index0.has(key)) {
            // replace the entry
            index0.put(entry);
        }
        // else place it in the index1
        index1.put(entry);
    }
   
    public final synchronized void addUnique(final Row.Entry entry) throws RowSpaceExceededException {
    	assert (entry != null);
    	if (entry == null) return;
        if (index1 == null) {
            // we are in the initialization phase
        	index0.addUnique(entry);
        	return;
        }
        // initialization is over, add to secondary index
        index1.addUnique(entry);
    }

	public final void addUnique(final List<Entry> rows) throws RowSpaceExceededException {
		final Iterator<Entry> i = rows.iterator();
		while (i.hasNext()) addUnique(i.next());
	}
	
	public final synchronized long inc(final byte[] key, final int col, final long add, final Row.Entry initrow) throws RowSpaceExceededException {
        assert (key != null);
        finishInitialization();
        assert index0.isSorted();
        final long l = index0.inc(key, col, add, null);
        if (l != Long.MIN_VALUE) return l;
        return index1.inc(key, col, add, initrow);
    }    
    
    public final synchronized ArrayList<RowCollection> removeDoubles() throws RowSpaceExceededException {
	    // finish initialization phase explicitely
        index0.sort();
	    if (index1 == null) {
	        return index0.removeDoubles();
	    }
	    final ArrayList<RowCollection> d0 = index0.removeDoubles();
	    final ArrayList<RowCollection> d1 = index1.removeDoubles();
        d0.addAll(d1);
        return d0;
	}
	
    public final synchronized boolean delete(final byte[] key) {
        finishInitialization();
        // if the new entry is within the initialization part, just delete it
        boolean b = index0.delete(key);
        if (b) {
            assert index0.get(key) == null; // check if remove worked
            return true;
        }
        // else remove it from the index1
        b = index1.delete(key);
        assert index1.get(key) == null : "removed " + ((b) ? " true" : " false") + ", and index entry still exists"; // check if remove worked
        return b;
    }

    public final synchronized Row.Entry remove(final byte[] key) {
        finishInitialization();
        // if the new entry is within the initialization part, just delete it
        final Row.Entry indexentry = index0.remove(key);
        if (indexentry != null) {
            assert index0.get(key) == null; // check if remove worked
            return indexentry;
        }
        // else remove it from the index1
        final Row.Entry removed = index1.remove(key);
        assert index1.get(key) == null : "removed " + ((removed == null) ? " is null" : " is not null") + ", and index entry still exists"; // check if remove worked
        return removed;
    }

    public final synchronized Row.Entry removeOne() {
        if (index1 != null && !index1.isEmpty()) {
            return index1.removeOne();
        }
        if (index0 != null && !index0.isEmpty()) {
        	return index0.removeOne();
        }
        return null;
    }
    
    public final synchronized int size() {
        if (index0 != null && index1 == null) {
            return index0.size();
        }
        if (index0 == null && index1 != null) {
            return index1.size();
        }
        assert (index0 != null && index1 != null);
        return index0.size() + index1.size();
    }
    
    public final synchronized boolean isEmpty() {
        if (index0 != null && index1 == null) {
            return index0.isEmpty();
        }
        if (index0 == null && index1 != null) {
            return index1.isEmpty();
        }
        assert (index0 != null && index1 != null);
        if (!index0.isEmpty()) return false;
        if (!index1.isEmpty()) return false;
        return true;
    }
    
    
    public final synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        // returns the key-iterator of the underlying kelondroIndex
        if (index1 == null) {
            // finish initialization phase
            index0.sort();
            index0.uniq();
            index1 = new RowSet(rowdef); //new RowSetArray(rowdef, spread);
            return index0.keys(up, firstKey);
        }
        assert (index1 != null);
        if (index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index1.keys(up, firstKey);
        }
        // index0 should be sorted
        // sort index1 to enable working of the merge iterator
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        final CloneableIterator<byte[]> k0 = index0.keys(up, firstKey);
        final CloneableIterator<byte[]> k1 = index1.keys(up, firstKey);
        if (k0 == null) return k1;
        if (k1 == null) return k0;
        return new MergeIterator<byte[]>(
                k0,
                k1,
                rowdef.objectOrder,
                MergeIterator.simpleMerge,
                true);
    }

    public final synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) {
        // returns the row-iterator of the underlying kelondroIndex
        if (index1 == null) {
            // finish initialization phase
            index0.sort();
            index0.uniq();
            index1 = new RowSet(rowdef); //new RowSetArray(rowdef, spread);
            return index0.rows(up, firstKey);
        }
        assert (index1 != null);
        if (index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index1.rows(up, firstKey);
        }
        // index0 should be sorted
        // sort index1 to enable working of the merge iterator
        //index1.sort();
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        final CloneableIterator<Row.Entry> k0 = index0.rows(up, firstKey);
        final CloneableIterator<Row.Entry> k1 = index1.rows(up, firstKey);
        if (k0 == null) return k1;
        if (k1 == null) return k0;
        return new MergeIterator<Row.Entry>(
                k0,
                k1,
                entryComparator,
                MergeIterator.simpleMerge,
                true);
    }
    
    public final Iterator<Entry> iterator() {
        return rows();
    }
    
    public final synchronized CloneableIterator<Row.Entry> rows() {
        // returns the row-iterator of the underlying kelondroIndex
        if (index1 == null) {
            // finish initialization phase
            index0.sort();
            index0.uniq();
            index1 = new RowSet(rowdef); //new RowSetArray(rowdef, spread);
            return index0.rows();
        }
        assert (index1 != null);
        if (index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index1.rows();
        }
        // index0 should be sorted
        // sort index1 to enable working of the merge iterator
        //index1.sort();
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return new StackIterator<Row.Entry>(index0.rows(), index1.rows());
    }
    
    public final synchronized void close() {
        if (index0 != null) index0.close();
        if (index1 != null) index1.close();
    }

	public final String filename() {
		return null; // this does not have a file name
	}
	
	public final void deleteOnExit() {
        // do nothing, there is no file
    }

}
