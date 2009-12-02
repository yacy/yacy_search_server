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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;
import net.yacy.kelondro.order.StackIterator;


public final class RowSetArray implements ObjectIndex, Iterable<Row.Entry> {

    private final int      objectCount;
    private final Row      rowdef;
    private final RowSet[] array;
    
    public RowSetArray(final Row rowdef, final int objectCount, final int arraySize) {
        this.array = new RowSet[arraySize];
        for (int i = 0; i < arraySize; i++) {
            this.array[i] = null;
        }
        this.rowdef = rowdef;
        this.objectCount = objectCount / arraySize;
    }
    
    private final int indexFor(byte[] key) {
        return (int) (this.rowdef.objectOrder.cardinal(key) % ((long) array.length));
    }
    
    private final int indexFor(Entry row) {
        return (int) (this.rowdef.objectOrder.cardinal(row.bytes(), 0, row.getPrimaryKeyLength()) % ((long) array.length));
    }
    
    private final RowSet accessArray(int i) {
        RowSet r = this.array[i];
        if (r == null) synchronized (this.array) {
            r = new RowSet(this.rowdef, this.objectCount);
            this.array[i] = r;
        }
        return r;
    }
    
    public final void addUnique(Entry row) {
        int i = indexFor(row);
        if (i < 0) return;
        accessArray(i).addUnique(row);
    }

    public final void addUnique(List<Entry> rows) {
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

    public final Entry get(byte[] key) {
        int i = indexFor(key);
        if (i < 0) return null;
        RowSet r = this.array[i];
        if (r == null) return null;
        return r.get(key);
    }

    public final boolean has(byte[] key) {
        int i = indexFor(key);
        if (i < 0) return false;
        RowSet r = this.array[i];
        if (r == null) return false;
        return r.has(key);
    }

    public final CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) {
        synchronized (this.array) {
            Collection<CloneableIterator<byte[]>> col = new ArrayList<CloneableIterator<byte[]>>();
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    this.array[i].sort();
                    col.add(this.array[i].keys(up, firstKey));
                }
            }
            return MergeIterator.cascade(col, this.rowdef.objectOrder, MergeIterator.simpleMerge, up);
        }            
    }

    public final void put(Entry row) {
        int i = indexFor(row);
        if (i < 0) return;
        accessArray(i).put(row);
    }

    public final Entry remove(byte[] key) {
        int i = indexFor(key);
        if (i < 0) return null;
        return accessArray(i).remove(key);
    }

    public final ArrayList<RowCollection> removeDoubles() {
        ArrayList<RowCollection> col = new ArrayList<RowCollection>();
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
                    Entry entry = this.array[i].removeOne();
                    if (this.array[i].isEmpty()) this.array[i] = null;
                    return entry;
                }
            }
        }
        return null;
    }

    public final Entry replace(Entry row) {
        int i = indexFor(row);
        if (i < 0) return null;
        return accessArray(i).replace(row);
    }

    public final Row row() {
        return this.rowdef;
    }

    public final CloneableIterator<Entry> rows(boolean up, byte[] firstKey) {
        synchronized (this.array) {
            Collection<CloneableIterator<Entry>> col = new ArrayList<CloneableIterator<Entry>>();
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    this.array[i].sort();
                    col.add(this.array[i].rows(up, firstKey));
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

    public final long inc(byte[] key, int col, long add, Entry initrow) {
        int i = indexFor(key);
        if (i < 0) return -1;
        return accessArray(i).inc(key, col, add, initrow);
    }
}
