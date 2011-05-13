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

import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;

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
    
    public BufferedObjectIndex(Index backend, int buffersize) {
        this.backend = backend;
        this.buffersize = buffersize;
        this.buffer = new RowSet(backend.row());
        this.entryComparator = new Row.EntryComparator(backend.row().objectOrder);
    }
    
    public byte[] smallestKey() {
        if (this.buffer == null || this.buffer.isEmpty()) return this.backend.smallestKey();
        if (this.backend.isEmpty()) return this.buffer.smallestKey();
        return this.backend.row().getOrdering().smallest(this.buffer.smallestKey(), this.backend.smallestKey());
    }
    
    public byte[] largestKey() {
        if (this.buffer == null || this.buffer.isEmpty()) return this.backend.largestKey();
        if (this.backend.isEmpty()) return this.buffer.largestKey();
        return this.backend.row().getOrdering().largest(this.buffer.largestKey(), this.backend.largestKey());
    }
    
    private final void flushBuffer() throws IOException, RowSpaceExceededException {
        if (this.buffer.size() > 0) {
            for (Row.Entry e: this.buffer) {
                this.backend.put(e);
            }
            this.buffer.clear();
        }
    }
    
    public long mem() {
        return this.backend.mem() + this.buffer.mem();
    }
    
    /**
     * check size of buffer in such a way that a put into the buffer is possible
     * afterwards without exceeding the given maximal buffersize
     * @throws RowSpaceExceededException 
     * @throws IOException 
     */
    private final void checkBuffer() throws IOException, RowSpaceExceededException {
        if (this.buffer.size() >= this.buffersize) flushBuffer();
    }
    
    public void addUnique(Entry row) throws RowSpaceExceededException, IOException {
        synchronized (this.backend) {
            checkBuffer();
            this.buffer.put(row);
        }
    }

    public void clear() throws IOException {
        synchronized (this.backend) {
            this.backend.clear();
            this.buffer.clear();
        }
    }

    public void close() {
        synchronized (this.backend) {
            try {
                flushBuffer();
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
            this.backend.close();
        }
    }

    public void deleteOnExit() {
        this.backend.deleteOnExit();
    }

    public String filename() {
        return this.backend.filename();
    }

    public int size() {
        synchronized (this.backend) {
            return this.buffer.size() + this.backend.size();
        }
    }
    
    public Entry get(byte[] key) throws IOException {
        synchronized (this.backend) {
            Entry entry = this.buffer.get(key);
            if (entry != null) return entry;
            return this.backend.get(key);
        }
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

    public boolean has(byte[] key) {
        synchronized (this.backend) {
            return this.buffer.has(key) || this.backend.has(key);
        }
    }

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
     * @throws RowSpaceExceededException
     */
    public boolean put(Entry row) throws IOException, RowSpaceExceededException {
        synchronized (this.backend) {
            checkBuffer();
            return this.buffer.put(row);
        }
    }

    public Entry remove(byte[] key) throws IOException {
        synchronized (this.backend) {
            Entry entry = this.buffer.remove(key);
            if (entry != null) return entry;
            return this.backend.remove(key);
        }
    }

    public boolean delete(byte[] key) throws IOException {
        synchronized (this.backend) {
            boolean b = this.buffer.delete(key);
            if (b) return true;
            return this.backend.delete(key);
        }
    }

    public List<RowCollection> removeDoubles() throws IOException, RowSpaceExceededException {
        synchronized (this.backend) {
            flushBuffer();
            return this.backend.removeDoubles();
        }
    }

    public List<Row.Entry> top(int count) throws IOException {
        List<Row.Entry> list = new ArrayList<Row.Entry>();
        synchronized (this.backend) {
            List<Row.Entry> list0 = buffer.top(count);
            list.addAll(list0);
            list0 = backend.top(count - list.size());
            list.addAll(list0);
        }
        return list;
    }

    public Entry removeOne() throws IOException {
        synchronized (this.backend) {
            if (!this.buffer.isEmpty()) {
                Entry entry = this.buffer.removeOne();
                if (entry != null) return entry;
            }
            return this.backend.removeOne();
        }
    }

    public Entry replace(Entry row) throws RowSpaceExceededException, IOException {
        synchronized (this.backend) {
            Entry entry = this.buffer.replace(row);
            if (entry != null) return entry;
            return this.backend.replace(row);
        }
    }

    public Row row() {
        return this.buffer.row();
    }

    public CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
        synchronized (this.backend) {
            return new MergeIterator<byte[]>(
                    this.buffer.keys(up, firstKey),
                    this.backend.keys(up, firstKey),
                    this.buffer.rowdef.getOrdering(),
                    MergeIterator.simpleMerge,
                    true);
        }
    }

    public Iterator<Entry> iterator() {
        try {
            return this.rows();
        } catch (IOException e) {
            Log.logException(e);
            return null;
        }
    }

    public CloneableIterator<Entry> rows(boolean up, byte[] firstKey) throws IOException {
        synchronized (this.backend) {
            return new MergeIterator<Entry>(
                    this.buffer.rows(up, firstKey),
                    this.backend.rows(up, firstKey),
                    this.entryComparator,
                    MergeIterator.simpleMerge,
                    true);
        }
    }

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
    public HandleSet keysFromBuffer(int load) throws IOException {
        if (load > this.buffersize) throw new IOException("buffer load size exceeded");
        synchronized (this.backend) {
            int missing = Math.min(this.backend.size(), load - this.buffer.size());
            while (missing-- > 0) {
                try {
                    this.buffer.put(this.backend.removeOne());
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                    break;
                }
            }
            HandleSet handles = new HandleSet(this.buffer.row().primaryKeyLength, this.buffer.row().objectOrder, this.buffer.size());
            Iterator<byte[]> i = this.buffer.keys();
            while (i.hasNext()) {
                try {
                    handles.put(i.next());
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                    break;
                }
            }
            return handles;
        }
    }

}
