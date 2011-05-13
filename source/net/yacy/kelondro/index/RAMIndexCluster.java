/**
 *  RAMIndexCluster
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 12.03.2009 at http://yacy.net
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;
import net.yacy.kelondro.order.StackIterator;


public final class RAMIndexCluster implements Index, Iterable<Row.Entry>, Cloneable {

    private final String             name;
    private final Row                rowdef;
    private final RAMIndex[] cluster;
    
    public RAMIndexCluster(String name, final Row rowdef, final int clusterSize) {
        //assert arraySize < 100 : arraySize;
        this.name = name;
        this.cluster = new RAMIndex[clusterSize];
        this.rowdef = rowdef;
        for (int i = 0; i < clusterSize; i++) {
            this.cluster[i] = new RAMIndex(name + "." + i, rowdef, 0);
        }
    }
    
    private RAMIndexCluster(String name, final Row rowdef, final RAMIndex[] array) {
        this.name = name;
        this.cluster = array;
        this.rowdef = rowdef;
    }
    
    public void trim() {
        for (RAMIndex i: this.cluster) if (i != null)  i.trim();
    }
    
    @Override
    public RAMIndexCluster clone() {
        RAMIndex[] a = new RAMIndex[this.cluster.length];
        for (int i = 0; i < this.cluster.length; i++) {
            a[i] = this.cluster[i].clone();
        }
        return new RAMIndexCluster(this.name + ".clone", this.rowdef, a);
    }
    
    private final int indexFor(final byte[] key) {
        return (int) ((this.rowdef.objectOrder.cardinal(key) / 17) % ((long) cluster.length));
    }
    
    private final int indexFor(final Entry row) {
        return (int) ((this.rowdef.objectOrder.cardinal(row.bytes(), 0, row.getPrimaryKeyLength()) / 17) % ((long) cluster.length));
    }
    
    public final byte[] smallestKey() {
        HandleSet keysort = new HandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.cluster.length);
        synchronized (this.cluster) {
            for (RAMIndex rs: this.cluster) try {
                keysort.put(rs.smallestKey());
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
        }
        return keysort.smallestKey();
    }
    
    public final byte[] largestKey() {
        HandleSet keysort = new HandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.cluster.length);
        synchronized (this.cluster) {
            for (RAMIndex rs: this.cluster) try {
                keysort.put(rs.largestKey());
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
        }
        return keysort.largestKey();
    }
    
    private final RAMIndex accessArray(final int i) {
        RAMIndex r = this.cluster[i];
        if (r == null) synchronized (this.cluster) {
            r = new RAMIndex(name + "." + i, this.rowdef, 0);
            this.cluster[i] = r;
        }
        return r;
    }
    
    public final void addUnique(final Entry row) throws RowSpaceExceededException {
        final int i = indexFor(row);
        assert i >= 0 : "i = " + i;
        if (i < 0) return;
        accessArray(i).addUnique(row);
    }

    public final void addUnique(final List<Entry> rows) throws RowSpaceExceededException {
        for (Entry row: rows) addUnique(row);
    }

    public final void clear() {
        synchronized (this.cluster) {
            for (RAMIndex c: this.cluster) if (c != null) c.clear();
        }
    }

    public final void close() {
        clear();
        synchronized (this.cluster) {
            for (RAMIndex c: this.cluster) if (c != null) c.close();
        }
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
        final RAMIndex r = this.cluster[i];
        if (r == null) return null;
        return r.get(key);
    }

    public Map<byte[], Row.Entry> get(Collection<byte[]> keys) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(this.row().objectOrder);
        Row.Entry entry;
        for (byte[] key: keys) {
            entry = get(key);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }

    public final boolean has(final byte[] key) {
        final int i = indexFor(key);
        if (i < 0) return false;
        final RAMIndex r = this.cluster[i];
        if (r == null) return false;
        return r.has(key);
    }

    public final CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        synchronized (this.cluster) {
            final Collection<CloneableIterator<byte[]>> col = new ArrayList<CloneableIterator<byte[]>>();
            for (int i = 0; i < this.cluster.length; i++) {
                if (this.cluster[i] != null) {
                    col.add(this.cluster[i].keys(up, firstKey));
                }
            }
            return MergeIterator.cascade(col, this.rowdef.objectOrder, MergeIterator.simpleMerge, up);
        }            
    }
    
    /**
     * Adds the row to the index. The row is identified by the primary key of the row.
     * @param row a index row
     * @return true if this set did _not_ already contain the given row. 
     * @throws IOException
     * @throws RowSpaceExceededException
     */
    public final boolean put(final Entry row) throws RowSpaceExceededException {
        final int i = indexFor(row);
        assert i >= 0 : "i = " + i;
        if (i < 0) return true;
        return accessArray(i).put(row);
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
        synchronized (this.cluster) {
            for (int i = 0; i < this.cluster.length; i++) {
                if (this.cluster[i] != null) {
                    col.addAll(this.cluster[i].removeDoubles());
                    if (this.cluster[i].isEmpty()) this.cluster[i] = null;
                }
            }
        }
        return col;
    }

    public final Entry removeOne() {
        synchronized (this.cluster) {
            for (int i = 0; i < this.cluster.length; i++) {
                if (this.cluster[i] != null) {
                    final Entry entry = this.cluster[i].removeOne();
                    if (this.cluster[i].isEmpty()) this.cluster[i] = null;
                    return entry;
                }
            }
        }
        return null;
    }

    public List<Row.Entry> top(int count) {
        List<Row.Entry> list = new ArrayList<Row.Entry>();
        synchronized (this.cluster) {
            for (int i = 0; i < this.cluster.length; i++) {
                if (this.cluster[i] != null) {
                    try {
                        List<Row.Entry> list0 = this.cluster[i].top(count - list.size());
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
        assert i >= 0 : "i = " + i;
        if (i < 0) return null;
        return accessArray(i).replace(row);
    }

    public final Row row() {
        return this.rowdef;
    }

    @SuppressWarnings("unchecked")
    public final CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) {
        synchronized (this.cluster) {
            final CloneableIterator<Entry>[] col = new CloneableIterator[this.cluster.length];
            for (int i = 0; i < this.cluster.length; i++) {
                if (this.cluster[i] == null) {
                    col[i] = null;
                } else {
                    col[i] = this.cluster[i].rows(up, firstKey);
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
        synchronized (this.cluster) {
            for (RAMIndex i: this.cluster) if (i != null) c += i.size();
        }
        return c;
    }

    public long mem() {
        long m = 0;
        synchronized (this.cluster) {
            for (RAMIndex i: this.cluster) if (i != null)  m += i.mem();
        }
        return m;
    }
    
    public final boolean isEmpty() {
        synchronized (this.cluster) {
            for (RAMIndex i: this.cluster) if (i != null && !i.isEmpty()) return false;
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
