/**
 *  BufferedObjectIndex
 *  Copyright 2010 by Michael Peter Christen
 *  First released 18.4.2010 at http://yacy.net
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

/**
 * a write buffer for ObjectIndex entries
 * @author Michael Peter Christen
 *
 */
public class BufferedObjectIndex implements Index, Iterable<Row.Entry> {

    private final Index backend;
    private final RowSet buffer;
    private final int buffersize;
    private final Row.EntryComparator entryComparator;

    public BufferedObjectIndex(final Index backend, final int buffersize) {
        this.backend = backend;
        this.buffersize = buffersize;
        this.buffer = new RowSet(backend.row());
        this.entryComparator = new Row.EntryComparator(backend.row().objectOrder);
    }

    @Override
    public byte[] smallestKey() {
        if (this.buffer == null || this.buffer.isEmpty()) return this.backend.smallestKey();
        if (this.backend.isEmpty()) return this.buffer.smallestKey();
        return this.backend.row().getOrdering().smallest(this.buffer.smallestKey(), this.backend.smallestKey());
    }

    @Override
    public byte[] largestKey() {
        if (this.buffer == null || this.buffer.isEmpty()) return this.backend.largestKey();
        if (this.backend.isEmpty()) return this.buffer.largestKey();
        return this.backend.row().getOrdering().largest(this.buffer.largestKey(), this.backend.largestKey());
    }

    private final void flushBuffer() throws IOException, SpaceExceededException {
        if (!this.buffer.isEmpty()) {
            for (final Row.Entry e: this.buffer) {
                this.backend.put(e);
            }
            this.buffer.clear();
        }
    }

    @Override
    public void optimize() {
        this.backend.optimize();
        this.buffer.optimize();
    }
    
    @Override
    public long mem() {
        return this.backend.mem() + this.buffer.mem();
    }

    /**
     * check size of buffer in such a way that a put into the buffer is possible
     * afterwards without exceeding the given maximal buffersize
     * @throws SpaceExceededException
     * @throws IOException
     */
    private final void checkBuffer() throws IOException, SpaceExceededException {
        if (this.buffer.size() >= this.buffersize) flushBuffer();
    }

    @Override
    public void addUnique(final Entry row) throws SpaceExceededException, IOException {
        synchronized (this.backend) {
            checkBuffer();
            this.buffer.put(row);
        }
    }

    @Override
    public void clear() throws IOException {
        synchronized (this.backend) {
            this.backend.clear();
            this.buffer.clear();
        }
    }

    @Override
    public synchronized void close() {
        synchronized (this.backend) {
            try {
                flushBuffer();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
            this.backend.close();
        }
    }

    @Override
    public void deleteOnExit() {
        this.backend.deleteOnExit();
    }

    @Override
    public String filename() {
        return this.backend.filename();
    }

    @Override
    public int size() {
        synchronized (this.backend) {
            return this.buffer.size() + this.backend.size();
        }
    }

    @Override
    public Entry get(final byte[] key, final boolean forcecopy) throws IOException {
        synchronized (this.backend) {
            final Entry entry = this.buffer.get(key, forcecopy);
            if (entry != null) return entry;
            return this.backend.get(key, forcecopy);
        }
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
    public boolean has(final byte[] key) {
        synchronized (this.backend) {
            return this.buffer.has(key) || this.backend.has(key);
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (this.backend) {
            return this.buffer.isEmpty() && this.backend.isEmpty();
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
    public boolean put(final Entry row) throws IOException, SpaceExceededException {
        synchronized (this.backend) {
            checkBuffer();
            return this.buffer.put(row);
        }
    }

    @Override
    public Entry remove(final byte[] key) throws IOException {
        synchronized (this.backend) {
            final Entry entry = this.buffer.remove(key);
            if (entry != null) return entry;
            return this.backend.remove(key);
        }
    }

    @Override
    public boolean delete(final byte[] key) throws IOException {
        synchronized (this.backend) {
            final boolean b = this.buffer.delete(key);
            if (b) return true;
            return this.backend.delete(key);
        }
    }

    @Override
    public List<RowCollection> removeDoubles() throws IOException, SpaceExceededException {
        synchronized (this.backend) {
            flushBuffer();
            return this.backend.removeDoubles();
        }
    }

    @Override
    public List<Row.Entry> top(final int count) throws IOException {
        final List<Row.Entry> list = new ArrayList<Row.Entry>();
        synchronized (this.backend) {
            List<Row.Entry> list0 = this.buffer.top(count);
            list.addAll(list0);
            list0 = this.backend.top(count - list.size());
            list.addAll(list0);
        }
        return list;
    }

    @Override
    public List<Row.Entry> random(final int count) throws IOException {
        List<Row.Entry> list0, list1;
        synchronized (this.backend) {
            list0 = this.buffer.random(Math.max(1, count / 2));
            list1 = this.backend.random(count - list0.size());
        }
        // multiplex the lists
        final List<Row.Entry> list = new ArrayList<Row.Entry>();
        Iterator<Row.Entry> i0 = list0.iterator();
        Iterator<Row.Entry> i1 = list1.iterator();
        while (i0.hasNext() || i1.hasNext()) {
            if (i0.hasNext()) list.add(i0.next());
            if (i1.hasNext()) list.add(i1.next());
        }
        return list;
    }

    @Override
    public Entry removeOne() throws IOException {
        synchronized (this.backend) {
            if (!this.buffer.isEmpty()) {
                final Entry entry = this.buffer.removeOne();
                if (entry != null) return entry;
            }
            return this.backend.removeOne();
        }
    }

    @Override
    public Entry replace(final Entry row) throws SpaceExceededException, IOException {
        synchronized (this.backend) {
            final Entry entry = this.buffer.replace(row);
            if (entry != null) return entry;
            return this.backend.replace(row);
        }
    }

    @Override
    public Row row() {
        return this.buffer.row();
    }

    @Override
    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        synchronized (this.backend) {
            CloneableIterator<byte[]> a = this.buffer.keys(up, firstKey);
            CloneableIterator<byte[]> b = this.backend.keys(up, firstKey);
            if (b == null) return a;
            if (a == null) return b;
            return new MergeIterator<byte[]>(a, b, this.buffer.rowdef.getOrdering(), MergeIterator.simpleMerge, true);
        }
    }

    @Override
    public Iterator<Entry> iterator() {
        try {
            return this.rows();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    @Override
    public CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        synchronized (this.backend) {
            return new MergeIterator<Entry>(
                    this.buffer.rows(up, firstKey),
                    this.backend.rows(up, firstKey),
                    this.entryComparator,
                    MergeIterator.simpleMerge,
                    true);
        }
    }

    @Override
    public CloneableIterator<Entry> rows() throws IOException {
        synchronized (this.backend) {
            return new MergeIterator<Entry>(
                    this.buffer.rows(),
                    this.backend.rows(),
                    this.entryComparator,
                    MergeIterator.simpleMerge,
                    true);
        }
    }

    /**
     * special iterator for BufferedObjectIndex:
     * iterates only objects from the buffer. The use case for this iterator is given
     * if first elements are iterated and then all iterated elements are deleted from the index.
     * To minimize the IO load the buffer is filled from the backend in such a way that
     * it creates a minimum of Read/Write-Head operations which is done using the removeOne() method.
     * The buffer will be filled with the demanded number of records. The given load value does
     * not denote the number of removeOne() operations but the number of records that are missing in the
     * buffer to provide the give load number of record entries.
     * The given load number must not exceed the maximal number of entries in the buffer.
     * To give room for put()-inserts while the iterator is running it is recommended to set the load
     * value at maximum  to the maximum number of entries in the buffer divided by two.
     * @param load number of records that shall be in the buffer when returning the buffer iterator
     * @return an iterator of the elements in the buffer.
     * @throws IOException
     */
    public HandleSet keysFromBuffer(final int load) throws IOException {
        if (load > this.buffersize) throw new IOException("buffer load size exceeded");
        synchronized (this.backend) {
            int missing = Math.min(this.backend.size(), load - this.buffer.size());
            while (missing-- > 0) {
                try {
                    this.buffer.put(this.backend.removeOne());
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                    break;
                }
            }
            final HandleSet handles = new RowHandleSet(this.buffer.row().primaryKeyLength, this.buffer.row().objectOrder, this.buffer.size());
            final Iterator<byte[]> i = this.buffer.keys();
            while (i.hasNext()) {
                try {
                    handles.put(i.next());
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                    break;
                }
            }
            handles.optimize();
            return handles;
        }
    }

}
