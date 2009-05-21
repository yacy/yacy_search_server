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

package de.anomic.kelondro.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import de.anomic.kelondro.index.Row.Entry;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.MergeIterator;
import de.anomic.kelondro.order.StackIterator;

public class RowSetArray implements ObjectIndex, Iterable<Row.Entry> {

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
    
    private int indexFor(byte[] key) {
        return (int) (this.rowdef.objectOrder.cardinal(key) % ((long) array.length));
    }
    
    private int indexFor(Entry row) {
        return (int) (this.rowdef.objectOrder.cardinal(row.bytes(), 0, row.getPrimaryKeyLength()) % ((long) array.length));
    }
    
    private RowSet accessArray(int i) {
        RowSet r = this.array[i];
        if (r == null) synchronized (this.array) {
            r = new RowSet(this.rowdef, this.objectCount);
            this.array[i] = r;
        }
        return r;
    }
    
    public void addUnique(Entry row) {
        int i = indexFor(row);
        if (i < 0) return;
        accessArray(i).addUnique(row);
    }

    public void addUnique(List<Entry> rows) {
        for (Entry row: rows) addUnique(row);
    }

    public void clear() {
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) this.array[i].clear();
                this.array[i] = null;
            }
        }
    }

    public void close() {
        clear();
    }

    public void deleteOnExit() {
        // no nothing here
    }

    public String filename() {
        // we don't have a file name
        return null;
    }

    public Entry get(byte[] key) {
        int i = indexFor(key);
        if (i < 0) return null;
        RowSet r = this.array[i];
        if (r == null) return null;
        return r.get(key);
    }

    public boolean has(byte[] key) {
        int i = indexFor(key);
        if (i < 0) return false;
        RowSet r = this.array[i];
        if (r == null) return false;
        return r.has(key);
    }

    public CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) {
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

    public void put(Entry row) {
        int i = indexFor(row);
        if (i < 0) return;
        accessArray(i).put(row);
    }

    public Entry remove(byte[] key) {
        int i = indexFor(key);
        if (i < 0) return null;
        return accessArray(i).remove(key);
    }

    public ArrayList<RowCollection> removeDoubles() {
        ArrayList<RowCollection> col = new ArrayList<RowCollection>();
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    col.addAll(this.array[i].removeDoubles());
                    if (this.array[i].size() == 0) this.array[i] = null;
                }
            }
        }
        return col;
    }

    public Entry removeOne() {
        synchronized (this.array) {
            for (int i = 0; i < this.array.length; i++) {
                if (this.array[i] != null) {
                    Entry entry = this.array[i].removeOne();
                    if (this.array[i].size() == 0) this.array[i] = null;
                    return entry;
                }
            }
        }
        return null;
    }

    public Entry replace(Entry row) {
        int i = indexFor(row);
        if (i < 0) return null;
        return accessArray(i).replace(row);
    }

    public Row row() {
        return this.rowdef;
    }

    public CloneableIterator<Entry> rows(boolean up, byte[] firstKey) {
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

    public CloneableIterator<Entry> rows() {
        return rows(true, null);
    }

    public int size() {
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

    public Iterator<Entry> iterator() {
        return this.rows(true, null);
    }

    public long inc(byte[] key, int col, long add, Entry initrow) {
        int i = indexFor(key);
        if (i < 0) return -1;
        return accessArray(i).inc(key, col, add, initrow);
    }
}
