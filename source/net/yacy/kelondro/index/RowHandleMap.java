/**
 *  HandleMap
 *  Copyright 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 08.04.2008 at http://yacy.net
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.storage.HandleMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.workflow.WorkflowProcessor;


public final class RowHandleMap implements HandleMap, Iterable<Map.Entry<byte[], Long>> {

    private   final Row rowdef;
    private RAMIndexCluster index;

    /**
     * initialize a HandleMap
     * This may store a key and a long value for each key.
     * The class is used as index for database files
     * @param keylength
     * @param objectOrder
     * @param space
     */
    public RowHandleMap(final int keylength, final ByteOrder objectOrder, final int idxbytes, final int expectedspace, final String name) {
        this.rowdef = new Row(new Column[]{new Column("key", Column.celltype_binary, Column.encoder_bytes, keylength, "key"), new Column("long c-" + idxbytes + " {b256}")}, objectOrder);
        this.index = new RAMIndexCluster(name, this.rowdef, spread(expectedspace));
    }

    /**
     * initialize a HandleMap with the content of a dumped index
     * @param keylength
     * @param objectOrder
     * @param file
     * @throws IOException
     * @throws SpaceExceededException
     */
    public RowHandleMap(final int keylength, final ByteOrder objectOrder, final int idxbytes, final File file) throws IOException, SpaceExceededException {
        this(keylength, objectOrder, idxbytes, (int) (file.length() / (keylength + idxbytes)), file.getAbsolutePath());
        // read the index dump and fill the index
        InputStream is;
        try {
            is = new BufferedInputStream(new FileInputStream(file), 1024 * 1024);
        } catch (final OutOfMemoryError e) {
            is = new FileInputStream(file);
        }
        if (file.getName().endsWith(".gz")) is = new GZIPInputStream(is);
        final byte[] a = new byte[keylength + idxbytes];
        int c;
        Row.Entry entry;
        while (true) {
            c = is.read(a);
            if (c <= 0) break;
            entry = this.rowdef.newEntry(a); // may be null if a is not well-formed
            if (entry != null) this.index.addUnique(entry);
        }
        is.close();
        is = null;
        assert this.index.size() == file.length() / (keylength + idxbytes);
        optimize();
    }

    @Override
    public void optimize() {
        this.index.optimize();
    }

    @Override
    public long mem() {
        return this.index.mem();
    }

    private static final int spread(final int expectedspace) {
        return Math.min(WorkflowProcessor.availableCPU, Math.max(WorkflowProcessor.availableCPU, expectedspace / 8000));
    }

    /**
     * Caclulate a statistic about the possible saving in the data structure when the data structure would be compressed.
     * A tuple of two values is computed:
     * @return {<the maximum length of consecutive equal-beginning bytes in the key>, <the minimum number of leading zeros in the second column>}
     */
    public final int[] saturation() {
    	int valm = this.rowdef.width(1);
    	byte[] lastk = null, thisk;
        int keym = 0; // will be the maximum length of consecutive equal-beginning byte[]
        Iterator<Row.Entry> i = rows(true, null);
        Row.Entry row;
    	while (i.hasNext()) {
    	    row = i.next();
    		// check length of key
    		if (lastk == null) {
    			lastk = row.bytes();
    		} else {
    			thisk = row.bytes();
    			keym = Math.max(keym, eq(lastk, thisk));
    			lastk = thisk;
    		}

    		// check length of value
            int valc; // will be the maximum length of leading zeros
    		for (valc = this.rowdef.primaryKeyLength; valc < this.rowdef.objectsize; valc++) {
    			if (lastk[valc] != 0) break;
    		} // valc is the number of leading zeros plus primaryKeyLength
    		valm = Math.min(valm, valc - this.rowdef.primaryKeyLength); // valm is the number of leading zeros after the primary key
    	}
    	// return two values:
    	// keym = the maximum length of consecutive equal-beginning byte[]
    	// this.rowdef.width(1) - valm = the minimum number of leading zeros in the second column
    	return new int[]{keym, this.rowdef.width(1) - valm};
    }

    /**
     * check length of equal leading bytes
     * @param a
     * @param b
     * @return the length of bytes from both arrays which are equal
     */
    private final static int eq(final byte[] a, final byte[] b) {
    	for (int i = 0; i < a.length; i++) {
    		if (a[i] != b[i]) return i;
    	}
    	return a.length;
    }

    /**
     * write a dump of the index to a file. All entries are written in order
     * which makes it possible to read them again in a fast way
     * @param file
     * @return the number of written entries
     * @throws IOException
     */
    @Override
    public final int dump(final File file) throws IOException {
        // we must use an iterator from the combined index, because we need the entries sorted
        // otherwise we could just write the byte[] from the in kelondroRowSet which would make
        // everything much faster, but this is not an option here.
        final File tmp = new File(file.getParentFile(), file.getName() + ".prt");
        final Iterator<Row.Entry> i = this.index.rows(true, null);
        OutputStream os;
        try {
            os = new BufferedOutputStream(new FileOutputStream(tmp), 4 * 1024 * 1024);
        } catch (final OutOfMemoryError e) {
            os = new FileOutputStream(tmp);
        }
        if (file.getName().endsWith(".gz")) os = new GZIPOutputStream(os);
        int c = 0;
        while (i.hasNext()) {
            os.write(i.next().bytes());
            c++;
        }
        os.flush();
        os.close();
        tmp.renameTo(file);
        assert file.exists() : file.toString();
        assert !tmp.exists() : tmp.toString();
        return c;
    }

    public final Row row() {
        return this.index.row();
    }

    @Override
    public final void clear() {
        this.index.clear();
    }

    @Override
    public final byte[] smallestKey() {
        return this.index.smallestKey();
    }

    @Override
    public final byte[] largestKey() {
        return this.index.largestKey();
    }

    @Override
    public final boolean has(final byte[] key) {
        assert (key != null);
        return this.index.has(key);
    }

    @Override
    public final long get(final byte[] key) {
        assert (key != null);
        final Row.Entry indexentry = this.index.get(key, false);
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }

    /**
     * Adds the key-value pair to the index.
     * @param key the index key
     * @param l the value
     * @return the previous entry of the index
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public final long put(final byte[] key, final long l) throws SpaceExceededException {
        assert l >= 0 : "l = " + l;
        assert (key != null);
        final Row.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, l);
        final Row.Entry oldentry = this.index.replace(newentry);
        if (oldentry == null) return -1;
        return oldentry.getColLong(1);
    }

    @Override
    public final void putUnique(final byte[] key, final long l) throws SpaceExceededException {
        assert l >= 0 : "l = " + l;
        assert (key != null);
        final Row.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, l);
        this.index.addUnique(newentry);
    }

    @Override
    public final long add(final byte[] key, final long a) throws SpaceExceededException {
        assert key != null;
        assert a >= 0; // it does not make sense to add 0. If this occurres, it is a performance issue
        synchronized (this.index) {
            final Row.Entry indexentry = this.index.get(key, true);
            if (indexentry == null) {
                final Row.Entry newentry = this.rowdef.newEntry();
                newentry.setCol(0, key);
                newentry.setCol(1, a);
                this.index.addUnique(newentry);
                return 1;
            }
            final long i = indexentry.getColLong(1) + a;
            indexentry.setCol(1, i);
            this.index.put(indexentry);
            return i;
        }
    }

    @Override
    public final long inc(final byte[] key) throws SpaceExceededException {
        return add(key, 1);
    }

    @Override
    public final long dec(final byte[] key) throws SpaceExceededException {
        return add(key, -1);
    }

    @Override
    public final ArrayList<long[]> removeDoubles() throws SpaceExceededException {
        final ArrayList<long[]> report = new ArrayList<long[]>();
        long[] is;
        int c;
        long l;
        final int initialSize = size();
        final ArrayList<RowCollection> rd = this.index.removeDoubles();
        for (final RowCollection rowset: rd) {
            is = new long[rowset.size()];
            c = 0;
            for (final Row.Entry e: rowset) {
            	l = e.getColLong(1);
            	assert l < initialSize : "l = " + l + ", initialSize = " + initialSize;
                is[c++] = l;
            }
            report.add(is);
        }
        return report;
    }

    @Override
    public final ArrayList<byte[]> top(final int count) {
        final List<Row.Entry> list0 = this.index.top(count);
        final ArrayList<byte[]> list = new ArrayList<byte[]>();
        for (final Row.Entry entry: list0) {
            list.add(entry.getPrimaryKeyBytes());
        }
        return list;
    }

    @Override
    public final synchronized long remove(final byte[] key) {
        assert (key != null);
        final Row.Entry indexentry;
        synchronized (this.index) {
            final boolean exist = this.index.has(key);
            if (!exist) return -1;
            //final int s = this.index.size();
            //final long m = this.index.mem();
            indexentry = this.index.remove(key);
            assert (indexentry != null);
            //assert this.index.size() < s : "s = " + s + ", index.size() = " + this.index.size();
            //assert this.index.mem() <= m : "m = " + m + ", index.mem() = " + this.index.mem();
        }
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }

    @Override
    public final long removeone() {
        final Row.Entry indexentry = this.index.removeOne();
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }

    @Override
    public final int size() {
        return this.index.size();
    }

    @Override
    public final boolean isEmpty() {
        return this.index.isEmpty();
    }

    @Override
    public final CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        return this.index.keys(up, firstKey);
    }

    public final CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) {
        return this.index.rows(up, firstKey);
    }

    @Override
    public final void close() {
        this.index.close();
        this.index = null;
    }

    /**
     * this method creates a concurrent thread that can take entries that are used to initialize the map
     * it should be used when a HandleMap is initialized when a file is read. Concurrency of FileIO and
     * map creation will speed up the initialization process.
     * @param keylength
     * @param objectOrder
     * @param space
     * @param bufferSize
     * @return
     */
    public final static initDataConsumer asynchronusInitializer(final String name, final int keylength, final ByteOrder objectOrder, final int idxbytes, final int expectedspace) {
        final initDataConsumer initializer = new initDataConsumer(new RowHandleMap(keylength, objectOrder, idxbytes, expectedspace, name));
        final ExecutorService service = Executors.newSingleThreadExecutor();
        initializer.setResult(service.submit(initializer));
        service.shutdown();
        return initializer;
    }

    private final static class entry {
        public byte[] key;
        public long l;
        public entry(final byte[] key, final long l) {
            this.key = key;
            this.l = l;
        }
    }

    protected static final entry poisonEntry = new entry(new byte[0], 0);

    public final static class initDataConsumer implements Callable<RowHandleMap> {

        private final BlockingQueue<entry> cache;
        private final RowHandleMap map;
        private Future<RowHandleMap> result;

        public initDataConsumer(final RowHandleMap map) {
            this.map = map;
            this.cache = new LinkedBlockingQueue<entry>();
        }

        protected final void setResult(final Future<RowHandleMap> result) {
            this.result = result;
        }

        /**
         * hand over another entry that shall be inserted into the HandleMap with an addl method
         * @param key
         * @param l
         */
        public final void consume(final byte[] key, final long l) {
            while (true) try {
                this.cache.put(new entry(key, l));
                break;
            } catch (final InterruptedException e) {
                continue;
            }
        }

        /**
         * to signal the initialization thread that no more entries will be submitted with consumer()
         * this method must be called. The process will not terminate if this is not called before.
         */
        public final void finish() {
            while (true) try {
                this.cache.put(poisonEntry);
                break;
            } catch (final InterruptedException e) {
                continue;
            }
        }

        /**
         * this must be called after a finish() was called. this method blocks until all entries
         * had been processed, and the content was sorted. It returns the HandleMap
         * that the user wanted to initialize
         * @return
         * @throws InterruptedException
         * @throws ExecutionException
         */
        public final RowHandleMap result() throws InterruptedException, ExecutionException {
            return this.result.get();
        }

        @Override
        public final RowHandleMap call() throws IOException {
            try {
                finishloop: while (true) {
                    entry c;
                    try {
                        while ((c = this.cache.take()) != poisonEntry) {
                            this.map.putUnique(c.key, c.l);
                        }
                        break finishloop;
                    } catch (final InterruptedException e) {
                        continue finishloop;
                    }
                }
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
            return this.map;
        }

        public synchronized void close() {
            this.map.close();
        }
    }

    @Override
    public Iterator<Entry<byte[], Long>> iterator() {
        final Iterator<Row.Entry> i = this.index.iterator();
        return new Iterator<Entry<byte[], Long>>() {

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Entry<byte[], Long> next() {
                Row.Entry row = i.next();
                return new AbstractMap.SimpleEntry<byte[], Long>(row.getPrimaryKeyBytes(), row.getColLong(1));
            }

            @Override
            public void remove() {
                i.remove();
            }

        };
    }

}
