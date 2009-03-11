// kelondroBytesIntMap.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 18.06.2006 on http://www.anomic.de
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.yacy.dht.FlatWordPartitionScheme;

public class IntegerHandleIndex {
    
    private final Row rowdef;
    private ObjectIndexCache index;
    
    public IntegerHandleIndex(final int keylength, final ByteOrder objectOrder, final int space) {
        this.rowdef = new Row(new Column[]{new Column("key", Column.celltype_binary, Column.encoder_bytes, keylength, "key"), new Column("int c-4 {b256}")}, objectOrder, 0);
        this.index = new ObjectIndexCache(rowdef, space);
    }

    /**
     * initialize a BytesLongMap with the content of a dumped index
     * @param keylength
     * @param objectOrder
     * @param file
     * @throws IOException 
     */
    public IntegerHandleIndex(final int keylength, final ByteOrder objectOrder, final File file) throws IOException {
        this(keylength, objectOrder, (int) (file.length() / (keylength + 8)));
        // read the index dump and fill the index
        InputStream is = new BufferedInputStream(new FileInputStream(file), 1024 * 1024);
        byte[] a = new byte[keylength + 4];
        int c;
        while (true) {
            c = is.read(a);
            if (c <= 0) break;
            this.index.addUnique(this.rowdef.newEntry(a));
        }
        is.close();
        assert this.index.size() == file.length() / (keylength + 4);
    }

    /**
     * write a dump of the index to a file. All entries are written in order
     * which makes it possible to read them again in a fast way
     * @param file
     * @return the number of written entries
     * @throws IOException
     */
    public int dump(File file) throws IOException {
        // we must use an iterator from the combined index, because we need the entries sorted
        // otherwise we could just write the byte[] from the in kelondroRowSet which would make
        // everything much faster, but this is not an option here.
        Iterator<Row.Entry> i = this.index.rows(true, null);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(file), 1024 * 1024);
        int c = 0;
        while (i.hasNext()) {
            os.write(i.next().bytes());
            c++;
        }
        os.flush();
        os.close();
        return c;
    }
    
    public Row row() {
        return index.row();
    }
    
    public void clear() throws IOException {
        this.index.clear();
    }
    
    public synchronized boolean has(final byte[] key) {
        assert (key != null);
        return index.has(key);
    }
    
    public synchronized int get(final byte[] key) throws IOException {
        assert (key != null);
        final Row.Entry indexentry = index.get(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }
    
    public synchronized int put(final byte[] key, final int i) throws IOException {
        assert i >= 0 : "i = " + i;
        assert (key != null);
        final Row.Entry newentry = index.row().newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, i);
        final Row.Entry oldentry = index.replace(newentry);
        if (oldentry == null) return -1;
        return (int) oldentry.getColLong(1);
    }

    public synchronized int inc(final byte[] key, int a) throws IOException {
        assert key != null;
        assert a > 0; // it does not make sense to add 0. If this occurres, it is a performance issue

        final Row.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, a);
        long l = index.inc(key, 1, a, newentry);
        return (int) l;
    }
    /*
    public synchronized int inc(final byte[] key, int a) throws IOException {
        assert key != null;
        assert a > 0; // it does not make sense to add 0. If this occurres, it is a performance issue

        final Row.Entry indexentry = index.get(key);
        if (indexentry == null) {
            final Row.Entry newentry = this.rowdef.newEntry();
            newentry.setCol(0, key);
            newentry.setCol(1, a);
            index.addUnique(newentry);
            return 1;
        } else {
            long l = indexentry.incCol(1, a);
            index.put(indexentry);
            return (int) l;
        }
    }
    */
    
    public synchronized void putUnique(final byte[] key, final int i) throws IOException {
        assert i >= 0 : "i = " + i;
        assert (key != null);
        final Row.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, i);
        index.addUnique(newentry);
    }
    
    public synchronized ArrayList<Integer[]> removeDoubles() throws IOException {
        final ArrayList<Integer[]> report = new ArrayList<Integer[]>();
        Integer[] is;
        int c, i;
        final int initialSize = this.size();
        for (final RowCollection delset: index.removeDoubles()) {
            is = new Integer[delset.size()];
            c = 0;
            for (Row.Entry e : delset) {
                i = (int) e.getColLong(1);
                assert i < initialSize : "i = " + i + ", initialSize = " + initialSize;
                is[c++] = Integer.valueOf(i);
            }
            report.add(is);
        }
        return report;
    }
    
    public synchronized int remove(final byte[] key) throws IOException {
        assert (key != null);
        final Row.Entry indexentry = index.remove(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }

    public synchronized int removeone() throws IOException {
        final Row.Entry indexentry = index.removeOne();
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }
    
    public synchronized int size() {
        return index.size();
    }
    
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return index.keys(up, firstKey);
    }

    public synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return index.rows(up, firstKey);
    }
    
    public synchronized void close() {
        index.close();
        index = null;
    }
    
    private static class entry {
        public byte[] key;
        public int l;
        public entry(final byte[] key, final int l) {
            this.key = key;
            this.l = l;
        }
    }
    private static final entry poisonEntry = new entry(new byte[0], 0);
    
    /**
     * this method creates a concurrent thread that can take entries that are used to initialize the map
     * it should be used when a bytesLongMap is initialized when a file is read. Concurrency of FileIO and
     * map creation will speed up the initialization process.
     * @param keylength
     * @param objectOrder
     * @param space
     * @param bufferSize
     * @return
     */
    public static initDataConsumer asynchronusInitializer(final int keylength, final ByteOrder objectOrder, final int space, int bufferSize) {
        initDataConsumer initializer = new initDataConsumer(new IntegerHandleIndex(keylength, objectOrder, space), bufferSize);
        ExecutorService service = Executors.newSingleThreadExecutor();
        initializer.setResult(service.submit(initializer));
        service.shutdown();
        return initializer;
    }
    
    public static class initDataConsumer implements Callable<IntegerHandleIndex> {

        private BlockingQueue<entry> cache;
        private IntegerHandleIndex map;
        private Future<IntegerHandleIndex> result;
        private boolean sortAtEnd;
        
        public initDataConsumer(IntegerHandleIndex map, int bufferCount) {
            this.map = map;
            cache = new ArrayBlockingQueue<entry>(bufferCount);
            sortAtEnd = false;
        }
        
        protected void setResult(Future<IntegerHandleIndex> result) {
            this.result = result;
        }
        
        /**
         * hand over another entry that shall be inserted into the BytesLongMap with an addl method
         * @param key
         * @param l
         */
        public void consume(final byte[] key, final int l) {
            try {
                cache.put(new entry(key, l));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        /**
         * to signal the initialization thread that no more entries will be sublitted with consumer()
         * this method must be called. The process will not terminate if this is not called before.
         */
        public void finish(boolean sortAtEnd) {
            this.sortAtEnd = sortAtEnd;
            try {
                cache.put(poisonEntry);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        /**
         * this must be called after a finish() was called. this method blocks until all entries
         * had been processed, and the content was sorted. It returns the kelondroBytesLongMap
         * that the user wanted to initialize
         * @return
         * @throws InterruptedException
         * @throws ExecutionException
         */
        public IntegerHandleIndex result() throws InterruptedException, ExecutionException {
            return this.result.get();
        }
        
        public IntegerHandleIndex call() throws IOException {
            try {
                entry c;
                while ((c = cache.take()) != poisonEntry) {
                    map.putUnique(c.key, c.l);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (sortAtEnd && map.index instanceof ObjectIndexCache) {
                ((ObjectIndexCache) map.index).finishInitialization();
            }
            return map;
        }
        
    }
    
    public static void main(String[] args) {
        int count = (args.length == 0) ? 100000 : Integer.parseInt(args[0]);
        IntegerHandleIndex idx = new IntegerHandleIndex(12, Base64Order.enhancedCoder, 100000);
        Random r = new Random(0);
        long start = System.currentTimeMillis();
        try {
            for (int i = 0; i < count; i++) {
                idx.inc(FlatWordPartitionScheme.positionToHash(r.nextInt(count / 32)).getBytes(), 1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Result: " + (((long) count) * 1000L / (System.currentTimeMillis() - start)) + " inc per second; " + count + " loops.");
    }
}
