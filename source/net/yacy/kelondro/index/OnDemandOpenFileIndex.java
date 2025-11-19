/**
 *  OnDemandOpenFileIndex
 *  Copyright 2014 by Michael Christen
 *  First released 16.04.2014 at https://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.kelondroException;

/**
 * a write buffer for ObjectIndex entries
 * @author Michael Peter Christen
 *
 */
public class OnDemandOpenFileIndex implements Index, Iterable<Row.Entry> {

    private final File file;
    private final Row rowdef;
    private int sizecache;
    private final boolean exceed134217727;

    public OnDemandOpenFileIndex(final File file, Row rowdef, final boolean exceed134217727) {
        this.file = file;
        this.rowdef = rowdef;
        this.exceed134217727 = exceed134217727;
        this.sizecache = -1;
    }

    private Index getIndex() {
        try {
            return new Table(this.file, this.rowdef, 1000, 0, false, this.exceed134217727, false);
        } catch (final kelondroException e) {
            ConcurrentLog.logException(e);
            return null;
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    @Override
    public synchronized byte[] smallestKey() {
        final Index index = this.getIndex();
        if (index == null) return null;
        final byte[] b = index.smallestKey();
        index.close();
        return b;
    }

    @Override
    public synchronized byte[] largestKey() {
        final Index index = this.getIndex();
        if (index == null) return null;
        final byte[] b = index.largestKey();
        index.close();
        return b;
    }

    @Override
    public synchronized void optimize() {
        final Index index = this.getIndex();
        if (index == null) return;
        index.optimize();
        index.close();
    }

    @Override
    public synchronized long mem() {
        final Index index = this.getIndex();
        if (index == null) return 0;
        final long l = index.mem();
        index.close();
        return l;
    }

    @Override
    public synchronized void addUnique(final Entry row) throws SpaceExceededException, IOException {
        final Index index = this.getIndex();
        if (index == null) return;
        try {
            index.addUnique(row);
            if (this.sizecache >= 0) this.sizecache++;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized void clear() throws IOException {
        final Index index = this.getIndex();
        if (index == null) return;
        try {
            index.clear();
            this.sizecache = 0;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized void close() {
        // there is actually nothing to do here because this class does not hold any data
    }

    @Override
    public synchronized void deleteOnExit() {
        final Index index = this.getIndex();
        if (index == null) return;
        index.deleteOnExit();
        index.close();
    }

    @Override
    public String filename() {
        return this.file.toString();
    }

    @Override
    public synchronized int size() {
        if (this.sizecache >= 0) return this.sizecache;
        final Index index = this.getIndex();
        if (index == null) return 0;
        final int i = index.size();
        index.close();
        this.sizecache = i;
        return i;
    }

    @Override
    public synchronized Entry get(final byte[] key, final boolean forcecopy) throws IOException {
        if (this.sizecache == 0) return null;
        final Index index = this.getIndex();
        if (index == null) return null;
        try {
            return index.get(key, forcecopy);
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Map<byte[], Row.Entry> getMap(final Collection<byte[]> keys, final boolean forcecopy) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<>(this.row().objectOrder);
        if (this.sizecache == 0) return map;
        Row.Entry entry;
        for (final byte[] key: keys) {
            entry = this.get(key, forcecopy);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }

    @Override
    public synchronized List<Row.Entry> getList(final Collection<byte[]> keys, final boolean forcecopy) throws IOException, InterruptedException {
        final List<Row.Entry> list = new ArrayList<>(keys.size());
        if (this.sizecache == 0) return list;
        Row.Entry entry;
        for (final byte[] key: keys) {
            entry = this.get(key, forcecopy);
            if (entry != null) list.add(entry);
        }
        return list;
    }

    @Override
    public synchronized boolean has(final byte[] key) {
        if (this.sizecache == 0) return false;
        final Index index = this.getIndex();
        if (index == null) return false;
        final boolean b = index.has(key);
        index.close();
        return b;
    }

    @Override
    public synchronized boolean isEmpty() {
        if (this.sizecache == 0) return true;
        final Index index = this.getIndex();
        if (index == null) return true;
        final boolean b = index.isEmpty();
        if (b) this.sizecache = 0;
        index.close();
        return b;
    }

    /**
     * Adds the row to the index. The row is identified by the primary key of the row.
     * @param row a index row
     * @return true if this set did _not_ already contain the given row.
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public synchronized boolean put(final Entry row) throws IOException, SpaceExceededException {
        final Index index = this.getIndex();
        if (index == null) return false;
        try {
            final boolean b = index.put(row);
            if (this.sizecache >= 0 && b) this.sizecache++;
            return b;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    /**
     * Mass-put method in case that a larger amount of rows should be stored.
     * This is the case in the BufferedObjectIndex class where a write buffer is flushed at once.
     * Without a mass-backend to store the data, the put would be called many times where each time the file is opened and closed.
     * This should speed-up the process.
     * @param rowset
     * @throws IOException
     * @throws SpaceExceededException
     */
    public synchronized void put(final RowSet rowset) throws IOException, SpaceExceededException {
        final Index index = this.getIndex();
        if (index == null) return;
        try {
            for (final Row.Entry row: rowset) {
                final boolean b = index.put(row);
                if (this.sizecache >= 0 && b) this.sizecache++;
            }
            return;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Entry remove(final byte[] key) throws IOException {
        final Index index = this.getIndex();
        if (index == null) return null;
        try {
            final Entry e = index.remove(key);
            if (this.sizecache >= 0 && e != null) this.sizecache--;
            return e;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized boolean delete(final byte[] key) throws IOException {
        final Index index = this.getIndex();
        if (index == null) return false;
        try {
            final boolean b = index.delete(key);
            if (this.sizecache >= 0 && b) this.sizecache--;
            return b;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized List<RowCollection> removeDoubles() throws IOException, SpaceExceededException {
        final Index index = this.getIndex();
        if (index == null) return null;
        try {
            final List<RowCollection> l = index.removeDoubles();
            this.sizecache = index.size();
            return l;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized List<Row.Entry> top(final int count) throws IOException {
        final Index index = this.getIndex();
        if (index == null) return null;
        try {
            return index.top(count);
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized List<Row.Entry> random(final int count) throws IOException {
        final Index index = this.getIndex();
        if (index == null) return null;
        try {
            return index.random(count);
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Entry removeOne() throws IOException {
        final Index index = this.getIndex();
        if (index == null) return null;
        try {
            final Entry e = index.removeOne();
            if (this.sizecache >= 0 && e != null) this.sizecache--;
            return e;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Entry replace(final Entry row) throws SpaceExceededException, IOException {
        final Index index = this.getIndex();
        if (index == null) return null;
        try {
            final Entry e = index.replace(row);
            if (this.sizecache >= 0 && e == null) this.sizecache++;
            return e;
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public Row row() {
        return this.rowdef;
    }

    @Override
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        final Index index = this.getIndex();
        if (index == null) return null;
        try {
            return index.keys(up, firstKey);
        } catch (final IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Iterator<Entry> iterator() {
        final Index index = this.getIndex();
        if (index == null) return null;
        final List<Entry> list = new ArrayList<>();
        final Iterator<Entry> i = index.iterator();
        while (i.hasNext()) list.add(i.next());
        index.close();
        return list.iterator();
    }

    @Override
    public synchronized CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        final Index index = this.getIndex();
        if (index == null) return null;
        final List<Entry> list = new ArrayList<>();
        final Iterator<Entry> i = index.rows(up, firstKey);
        while (i.hasNext()) list.add(i.next());
        index.close();
        final Iterator<Entry> li = list.iterator();
        return new CloneableIterator<>(){
            @Override
            public boolean hasNext() {
                return li.hasNext();
            }
            @Override
            public Entry next() {
                return li.next();
            }
            @Override
            public void remove() {
                li.remove();
            }
            @Override
            public CloneableIterator<Entry> clone(Object modifier) {
                return null;
            }
            @Override
            public void close() {
            }
        };
    }

    @Override
    public synchronized CloneableIterator<Entry> rows() throws IOException {
        final Index index = this.getIndex();
        if (index == null) return null;
        final List<Entry> list = new ArrayList<>();
        final Iterator<Entry> i = index.rows();
        while (i.hasNext()) list.add(i.next());
        index.close();
        final Iterator<Entry> li = list.iterator();
        return new CloneableIterator<>(){
            @Override
            public boolean hasNext() {
                return li.hasNext();
            }
            @Override
            public Entry next() {
                return li.next();
            }
            @Override
            public void remove() {
                li.remove();
            }
            @Override
            public CloneableIterator<Entry> clone(Object modifier) {
                return null;
            }
            @Override
            public void close() {
            }
        };
    }

}
