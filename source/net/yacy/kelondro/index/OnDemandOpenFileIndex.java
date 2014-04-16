/**
 *  OnDemandOpenFileIndex
 *  Copyright 2014 by Michael Christen
 *  First released 16.04.2014 at http://yacy.net
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
            return new Table(file, rowdef, 1000, 0, false, exceed134217727, false);
        } catch (kelondroException e) {
            ConcurrentLog.logException(e);
            return null;
        } catch (SpaceExceededException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }
    
    @Override
    public synchronized byte[] smallestKey() {
        Index index = getIndex();
        if (index == null) return null;
        byte[] b = index.smallestKey();
        index.close();
        return b;
    }

    @Override
    public synchronized byte[] largestKey() {
        Index index = getIndex();
        if (index == null) return null;
        byte[] b = index.largestKey();
        index.close();
        return b;
    }

    @Override
    public synchronized void optimize() {
        Index index = getIndex();
        if (index == null) return;
        index.optimize();
        index.close();
    }
    
    @Override
    public synchronized long mem() {
        Index index = getIndex();
        if (index == null) return 0;
        long l = index.mem();
        index.close();
        return l;
    }

    @Override
    public synchronized void addUnique(final Entry row) throws SpaceExceededException, IOException {
        Index index = getIndex();
        if (index == null) return;
        try {
            index.addUnique(row);
            if (this.sizecache >= 0) this.sizecache++;
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized void clear() throws IOException {
        Index index = getIndex();
        if (index == null) return;
        try {
        index.clear();
        this.sizecache = 0;
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized void close() {
    }

    @Override
    public synchronized void deleteOnExit() {
        Index index = getIndex();
        index.deleteOnExit();
        index.close();
    }

    @Override
    public String filename() {
        return this.file.toString();
    }

    @Override
    public synchronized int size() {
        if (sizecache >= 0) return sizecache;
        Index index = getIndex();
        if (index == null) return 0;
        int i = index.size();
        index.close();
        this.sizecache = i;
        return i;
    }

    @Override
    public synchronized Entry get(final byte[] key, final boolean forcecopy) throws IOException {
        Index index = getIndex();
        if (index == null) return null;
        try {
            return index.get(key, forcecopy);
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Map<byte[], Row.Entry> get(final Collection<byte[]> keys, final boolean forcecopy) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(row().objectOrder);
        Row.Entry entry;
        for (final byte[] key: keys) {
            entry = get(key, forcecopy);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }

    @Override
    public synchronized boolean has(final byte[] key) {
        Index index = getIndex();
        if (index == null) return false;
        boolean b = index.has(key);
        index.close();
        return b;
    }

    @Override
    public synchronized boolean isEmpty() {
        Index index = getIndex();
        if (index == null) return true;
        boolean b = index.isEmpty();
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
        Index index = getIndex();
        if (index == null) return false;
        try {
            boolean b = index.put(row);
            if (this.sizecache >= 0 && b) this.sizecache++;
            return b;
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Entry remove(final byte[] key) throws IOException {
        Index index = getIndex();
        if (index == null) return null;
        try {
            Entry e = index.remove(key);
            if (this.sizecache >= 0 && e != null) this.sizecache--;
            return e;
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized boolean delete(final byte[] key) throws IOException {
        Index index = getIndex();
        if (index == null) return false;
        try {
            boolean b = index.delete(key);
            if (this.sizecache >= 0 && b) this.sizecache--;
            return b;
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized List<RowCollection> removeDoubles() throws IOException, SpaceExceededException {
        Index index = getIndex();
        if (index == null) return null;
        try {
            List<RowCollection> l = index.removeDoubles();
            this.sizecache = index.size();
            return l;
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized List<Row.Entry> top(final int count) throws IOException {
        Index index = getIndex();
        if (index == null) return null;
        try {
            return index.top(count);
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized List<Row.Entry> random(final int count) throws IOException {
        Index index = getIndex();
        if (index == null) return null;
        try {
            return index.random(count);
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Entry removeOne() throws IOException {
        Index index = getIndex();
        if (index == null) return null;
        try {
            Entry e = index.removeOne();
            if (this.sizecache >= 0 && e != null) this.sizecache--;
            return e;
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Entry replace(final Entry row) throws SpaceExceededException, IOException {
        Index index = getIndex();
        if (index == null) return null;
        try {
            Entry e = index.replace(row);
            if (this.sizecache >= 0 && e == null) this.sizecache++;
            return e;
        } catch (IOException e) {
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
        Index index = getIndex();
        if (index == null) return null;
        try {
            return index.keys(up, firstKey);
        } catch (IOException e) {
            throw e;
        } finally {
            index.close();
        }
    }

    @Override
    public synchronized Iterator<Entry> iterator() {
        Index index = getIndex();
        if (index == null) return null;
        List<Entry> list = new ArrayList<Entry>();
        Iterator<Entry> i = index.iterator();
        while (i.hasNext()) list.add(i.next());
        index.close();
        return list.iterator();
    }

    @Override
    public synchronized CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        Index index = getIndex();
        if (index == null) return null;
        final List<Entry> list = new ArrayList<Entry>();
        final Iterator<Entry> i = index.rows(up, firstKey);
        while (i.hasNext()) list.add(i.next());
        index.close();
        final Iterator<Entry> li = list.iterator();
        return new CloneableIterator<Entry>(){
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
        Index index = getIndex();
        if (index == null) return null;
        final List<Entry> list = new ArrayList<Entry>();
        final Iterator<Entry> i = index.rows();
        while (i.hasNext()) list.add(i.next());
        index.close();
        final Iterator<Entry> li = list.iterator();
        return new CloneableIterator<Entry>(){
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
