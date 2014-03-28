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

import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.util.MergeIterator;
import net.yacy.kelondro.util.StackIterator;


public final class RAMIndexCluster implements Index, Iterable<Row.Entry>, Cloneable {

    private final String             name;
    private final Row                rowdef;
    private final RAMIndex[] cluster;

    public RAMIndexCluster(final String name, final Row rowdef, final int clusterSize) {
        //assert arraySize < 100 : arraySize;
        this.name = name;
        this.cluster = new RAMIndex[clusterSize];
        this.rowdef = rowdef;
        for (int i = 0; i < clusterSize; i++) {
            this.cluster[i] = null; // lazy initialization, the actual initialization is at accessArray()
        }
    }

    private RAMIndexCluster(final String name, final Row rowdef, final RAMIndex[] array) {
        this.name = name;
        this.cluster = array;
        this.rowdef = rowdef;
    }

    @Override
    public void optimize() {
        for (final RAMIndex i: this.cluster) if (i != null) i.optimize();
    }

    @Override
    public RAMIndexCluster clone() {
        final RAMIndex[] a = new RAMIndex[this.cluster.length];
        for (int i = 0; i < this.cluster.length; i++) {
            a[i] = this.cluster[i] == null ? null : this.cluster[i].clone();
        }
        return new RAMIndexCluster(this.name + ".clone", this.rowdef, a);
    }

    private final int indexFor(final byte[] key) {
        return (int) ((this.rowdef.objectOrder.cardinal(key) / 17) % (this.cluster.length));
    }

    private final int indexFor(final Entry row) {
        return (int) ((this.rowdef.objectOrder.cardinal(row.bytes(), 0, row.getPrimaryKeyLength()) / 17) % (this.cluster.length));
    }

    @Override
    public final byte[] smallestKey() {
        final HandleSet keysort = new RowHandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.cluster.length);
        synchronized (this.cluster) {
            for (final RAMIndex rs: this.cluster) try {
                keysort.put(rs.smallestKey());
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
        }
        return keysort.smallestKey();
    }

    @Override
    public final byte[] largestKey() {
        final HandleSet keysort = new RowHandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.cluster.length);
        synchronized (this.cluster) {
            for (final RAMIndex rs: this.cluster) try {
                keysort.put(rs.largestKey());
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
        }
        return keysort.largestKey();
    }

    private final RAMIndex accessArray(final int i) {
        RAMIndex r = this.cluster[i];
        if (r == null) synchronized (this.cluster) {
            r = this.cluster[i];
            if (r == null) {
                r = new RAMIndex(this.name + "." + i, this.rowdef);
                this.cluster[i] = r;
            }
        }
        return r;
    }

    @Override
    public final void addUnique(final Entry row) throws SpaceExceededException {
        final int i = indexFor(row);
        assert i >= 0 : "i = " + i;
        if (i < 0) return;
        accessArray(i).addUnique(row);
    }

    public final void addUnique(final List<Entry> rows) throws SpaceExceededException {
        for (final Entry row: rows) addUnique(row);
    }

    @Override
    public final void clear() {
        synchronized (this.cluster) {
            for (final RAMIndex c: this.cluster) if (c != null) c.clear();
        }
    }

    @Override
	public final void close() {
        synchronized (this.cluster) {
            for (final RAMIndex c: this.cluster) {
                if (c != null) {
                    //Log.logInfo("RAMIndexCluster", "Closing RAM index at " + c.getName() + " with " + c.size() + " entries ...");
                    c.close();
                }
            }
        }
    }

    @Override
    public final void deleteOnExit() {
        // no nothing here
    }

    @Override
    public final String filename() {
        // we don't have a file name
        return null;
    }

    @Override
    public final Entry get(final byte[] key, final boolean forcecopy) {
        final int i = indexFor(key);
        if (i < 0) return null;
        final RAMIndex r = this.cluster[i];
        if (r == null) return null;
        return r.get(key, forcecopy);
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
    public final boolean has(final byte[] key) {
        final int i = indexFor(key);
        if (i < 0) return false;
        final RAMIndex r = this.cluster[i];
        if (r == null) return false;
        return r.has(key);
    }

    @Override
    public final CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        synchronized (this.cluster) {
            final Collection<CloneableIterator<byte[]>> col = new ArrayList<CloneableIterator<byte[]>>();
            for (final RAMIndex element : this.cluster) {
                if (element != null) {
                    col.add(element.keys(up, firstKey));
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
     * @throws SpaceExceededException
     */
    @Override
    public final boolean put(final Entry row) throws SpaceExceededException {
        final int i = indexFor(row);
        assert i >= 0 : "i = " + i;
        if (i < 0) return true;
        return accessArray(i).put(row);
    }

    @Override
    public final boolean delete(final byte[] key) {
        final int i = indexFor(key);
        if (i < 0) return false;
        return accessArray(i).delete(key);
    }

    @Override
    public final Entry remove(final byte[] key) {
        final int i = indexFor(key);
        if (i < 0) return null;
        return accessArray(i).remove(key);
    }

    @Override
    public final ArrayList<RowCollection> removeDoubles() throws SpaceExceededException {
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

    @Override
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

    @Override
    public List<Row.Entry> top(final int count) {
        final List<Row.Entry> list = new ArrayList<Row.Entry>();
        synchronized (this.cluster) {
            for (final RAMIndex element : this.cluster) {
                if (element != null) {
                    try {
                        final List<Row.Entry> list0 = element.top(count - list.size());
                        list.addAll(list0);
                    } catch (final IOException e) {
                        continue;
                    }
                }
                if (list.size() >= count) return list;
            }
        }
        return list;
    }

    @Override
    public List<Row.Entry> random(final int count) {
        final List<Row.Entry> list = new ArrayList<Row.Entry>();
        synchronized (this.cluster) {
            for (final RAMIndex element : this.cluster) {
                if (element != null) {
                    try {
                        final List<Row.Entry> list0 = element.random(count - list.size());
                        list.addAll(list0);
                    } catch (final IOException e) {
                        continue;
                    }
                }
                if (list.size() >= count) return list;
            }
        }
        return list;
    }

    @Override
    public final Entry replace(final Entry row) throws SpaceExceededException {
        final int i = indexFor(row);
        assert i >= 0 : "i = " + i;
        if (i < 0) return null;
        return accessArray(i).replace(row);
    }

    @Override
    public final Row row() {
        return this.rowdef;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) {
        synchronized (this.cluster) {
            final List<CloneableIterator<Entry>> col = new ArrayList<CloneableIterator<Entry>>(this.cluster.length);
            for (RAMIndex element : this.cluster) {
                if (element != null) {
                    col.add(element.rows(up, firstKey));
                }
            }
            return StackIterator.stack(col.toArray(new CloneableIterator[col.size()]));
        }
    }

    @Override
    public final CloneableIterator<Entry> rows() {
        return rows(true, null);
    }

    @Override
    public final int size() {
        int c = 0;
        for (final RAMIndex i: this.cluster) if (i != null) c += i.size();
        return c;
    }

    @Override
    public long mem() {
        long m = 0;
        for (final RAMIndex i: this.cluster) if (i != null)  m += i.mem();
        return m;
    }

    @Override
    public final boolean isEmpty() {
        for (final RAMIndex i: this.cluster) if (i != null && !i.isEmpty()) return false;
        return true;
    }

    @Override
    public final Iterator<Entry> iterator() {
        return this.rows(true, null);
    }

    public final long inc(final byte[] key, final int col, final long add, final Entry initrow) throws SpaceExceededException {
        final int i = indexFor(key);
        if (i < 0) return -1;
        return accessArray(i).inc(key, col, add, initrow);
    }
}
