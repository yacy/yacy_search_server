// RowSetArray.java
// --------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2009
// last major change: 12.03.2009
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;
import net.yacy.kelondro.order.StackIterator;


public final class RowSetArray implements ObjectIndex, Iterable<Row.Entry>, Cloneable {

    private final Row      rowdef;
    private final ObjectIndexCache[] array;
    
    public RowSetArray(final Row rowdef, final int arraySize) {
        //assert arraySize < 100 : arraySize;
        this.array = new ObjectIndexCache[arraySize];
        this.rowdef = rowdef;
        for (int i = 0; i < arraySize; i++) {
            this.array[i] = new ObjectIndexCache(rowdef, 0);
        }
    }
    
    private RowSetArray(final Row rowdef, final ObjectIndexCache[] array) {
        this.array = array;
        this.rowdef = rowdef;
    }
    
    public RowSetArray clone() {
        ObjectIndexCache[] a = new ObjectIndexCache[this.array.length];
        for (int i = 0; i < this.array.length; i++) {
            a[i] = this.array[i].clone();
        }
        return new RowSetArray(this.rowdef, a);
    }
    
    private final int indexFor(final byte[] key) {
        return (int) (this.rowdef.objectOrder.cardinal(key) % ((long) array.length));
    }
    
    private final int indexFor(final Entry row) {
        return (int) (this.rowdef.objectOrder.cardinal(row.bytes(), 0, row.getPrimaryKeyLength()) % ((long) array.length));
    }
    
    public final byte[] smallestKey() {
        HandleSet keysort = new HandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.array.length);
        synchronized (this.array) {
            for (ObjectIndexCache rs: this.array) try {
                keysort.put(rs.smallestKey());
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
        }
        return keysort.smallestKey();
    }
    
    public final byte[] largestKey() {
        HandleSet keysort = new HandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.array.length);
        synchronized (this.array) {
            for (ObjectIndexCache rs: this.array) try {
                keysort.put(rs.largestKey());
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
        }
        return keysort.largestKey();
    }
    
    private final ObjectIndexCache accessArray(final int i) {
        ObjectIndexCache r = this.array[i];
        if (r == null) synchronized (this.array) {
            r = new ObjectIndexCache(this.rowdef, 0);
            this.array[i] = r;
        }
        return r;
    }
    
    public final void addUnique(final Entry row) throws RowSpaceExceededException {
        final int i = indexFor(row);
        if (i < 0) return;
        accessArray(i).addUnique(row);
    }

    public final void addUnique(final List<Entry> rows) throws RowSpaceExceededException {
        for (Entry row: rows) addUnique(row);
    }

    public final void clear() {
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) this.array[i].clear();
                this.array[i] = null;
            }
        }
    }

    public final void close() {
        clear();
    }

    public final void deleteOnExit() {
        // no nothing here
    }

    public final String filename() {
        // we don't have a file name
        return null;
    }

    public final Entry get(final byte[] key) {
        final int i = indexFor(key);
        if (i < 0) return null;
        final ObjectIndexCache r = this.array[i];
        if (r == null) return null;
        return r.get(key);
    }

    public final boolean has(final byte[] key) {
        final int i = indexFor(key);
        if (i < 0) return false;
        final ObjectIndexCache r = this.array[i];
        if (r == null) return false;
        return r.has(key);
    }

    public final CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        synchronized (this.array) {
            final Collection<CloneableIterator<byte[]>> col = new ArrayList<CloneableIterator<byte[]>>();
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    col.add(this.array[i].keys(up, firstKey));
                }
            }
            return MergeIterator.cascade(col, this.rowdef.objectOrder, MergeIterator.simpleMerge, up);
        }            
    }

    public final void put(final Entry row) throws RowSpaceExceededException {
        final int i = indexFor(row);
        if (i < 0) return;
        accessArray(i).put(row);
    }

    public final boolean delete(final byte[] key) {
        final int i = indexFor(key);
        if (i < 0) return false;
        return accessArray(i).delete(key);
    }

    public final Entry remove(final byte[] key) {
        final int i = indexFor(key);
        if (i < 0) return null;
        return accessArray(i).remove(key);
    }

    public final ArrayList<RowCollection> removeDoubles() throws RowSpaceExceededException {
        final ArrayList<RowCollection> col = new ArrayList<RowCollection>();
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    col.addAll(this.array[i].removeDoubles());
                    if (this.array[i].isEmpty()) this.array[i] = null;
                }
            }
        }
        return col;
    }

    public final Entry removeOne() {
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    final Entry entry = this.array[i].removeOne();
                    if (this.array[i].isEmpty()) this.array[i] = null;
                    return entry;
                }
            }
        }
        return null;
    }

    public List<Row.Entry> top(int count) {
        List<Row.Entry> list = new ArrayList<Row.Entry>();
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    try {
                        List<Row.Entry> list0 = this.array[i].top(count - list.size());
                        list.addAll(list0);
                    } catch (IOException e) {
                        continue;
                    }
                }
                if (list.size() >= count) return list;
            }
        }
        return list;
    }
    
    public final Entry replace(final Entry row) throws RowSpaceExceededException {
        final int i = indexFor(row);
        if (i < 0) return null;
        return accessArray(i).replace(row);
    }

    public final Row row() {
        return this.rowdef;
    }

    @SuppressWarnings("unchecked")
    public final CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) {
        synchronized (this.array) {
            final CloneableIterator<Entry>[] col = new CloneableIterator[this.array.length];
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] == null) {
                    col[i] = null;
                } else {
                    col[i] = this.array[i].rows(up, firstKey);
                }
            }
            return StackIterator.stack(col);
        }
    }

    public final CloneableIterator<Entry> rows() {
        return rows(true, null);
    }

    public final int size() {
        int c = 0;
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    c += this.array[i].size();
                }
            }
        }
        return c;
    }

    public final boolean isEmpty() {
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    if (!this.array[i].isEmpty()) return false;
                }
            }
        }
        return true;
    }
    
    public final Iterator<Entry> iterator() {
        return this.rows(true, null);
    }

    public final long inc(final byte[] key, final int col, final long add, final Entry initrow) throws RowSpaceExceededException {
        final int i = indexFor(key);
        if (i < 0) return -1;
        return accessArray(i).inc(key, col, add, initrow);
    }
}
