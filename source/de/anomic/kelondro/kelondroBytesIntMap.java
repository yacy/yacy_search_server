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

package de.anomic.kelondro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class kelondroBytesIntMap {
    
    private final kelondroRow rowdef;
    private kelondroIndex index;
    
    public kelondroBytesIntMap(final int keylength, final kelondroByteOrder objectOrder, final int space) {
        this.rowdef = new kelondroRow(new kelondroColumn[]{new kelondroColumn("key", kelondroColumn.celltype_binary, kelondroColumn.encoder_bytes, keylength, "key"), new kelondroColumn("int c-4 {b256}")}, objectOrder, 0);
        this.index = new kelondroRAMIndex(rowdef, space);
    }
    
    public kelondroRow row() {
        return index.row();
    }
    
    public void clear() throws IOException {
        this.index.clear();
    }
    
    public synchronized boolean has(final byte[] key) {
        assert (key != null);
        return index.has(key);
    }
    
    public synchronized int geti(final byte[] key) throws IOException {
        assert (key != null);
        final kelondroRow.Entry indexentry = index.get(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }
    
    public synchronized int puti(final byte[] key, final int i) throws IOException {
        assert i >= 0 : "i = " + i;
        assert (key != null);
        final kelondroRow.Entry newentry = index.row().newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, i);
        final kelondroRow.Entry oldentry = index.put(newentry);
        if (oldentry == null) return -1;
        return (int) oldentry.getColLong(1);
    }
    
    public synchronized void addi(final byte[] key, final int i) throws IOException {
        assert i >= 0 : "i = " + i;
        assert (key != null);
        final kelondroRow.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, i);
        index.addUnique(newentry);
    }
    
    public synchronized ArrayList<Integer[]> removeDoubles() throws IOException {
        final ArrayList<Integer[]> report = new ArrayList<Integer[]>();
        Integer[] is;
        int c, i;
        final int initialSize = this.size();
        for (final kelondroRowCollection delset: index.removeDoubles()) {
            is = new Integer[delset.size()];
            c = 0;
            for (kelondroRow.Entry e : delset) {
                i = (int) e.getColLong(1);
                assert i < initialSize : "i = " + i + ", initialSize = " + initialSize;
                is[c++] = Integer.valueOf(i);
            }
            report.add(is);
        }
        return report;
    }
    
    public synchronized int removei(final byte[] key) throws IOException {
        assert (key != null);
        final kelondroRow.Entry indexentry = index.remove(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }

    public synchronized int removeonei() throws IOException {
        final kelondroRow.Entry indexentry = index.removeOne();
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }
    
    public synchronized int size() {
        return index.size();
    }
    
    public synchronized kelondroCloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return index.keys(up, firstKey);
    }

    public synchronized kelondroCloneableIterator<kelondroRow.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return index.rows(up, firstKey);
    }
    
    public kelondroProfile profile() {
        return index.profile();
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
    public static initDataConsumer asynchronusInitializer(final int keylength, final kelondroByteOrder objectOrder, final int space, int bufferSize) {
        initDataConsumer initializer = new initDataConsumer(new kelondroBytesIntMap(keylength, objectOrder, space), bufferSize);
        ExecutorService service = Executors.newSingleThreadExecutor();
        initializer.setResult(service.submit(initializer));
        service.shutdown();
        return initializer;
    }
    
    public static class initDataConsumer implements Callable<kelondroBytesIntMap> {

        private BlockingQueue<entry> cache;
        private final entry poison = new entry(new byte[0], 0);
        private kelondroBytesIntMap map;
        private Future<kelondroBytesIntMap> result;
        private boolean sortAtEnd;
        
        public initDataConsumer(kelondroBytesIntMap map, int bufferCount) {
            this.map = map;
            cache = new ArrayBlockingQueue<entry>(bufferCount);
            sortAtEnd = false;
        }
        
        protected void setResult(Future<kelondroBytesIntMap> result) {
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
                cache.put(poison);
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
        public kelondroBytesIntMap result() throws InterruptedException, ExecutionException {
            return this.result.get();
        }
        
        public kelondroBytesIntMap call() throws IOException {
            try {
                entry c;
                while ((c = cache.take()) != poison) {
                    map.addi(c.key, c.l);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (sortAtEnd && map.index instanceof kelondroRAMIndex) {
                ((kelondroRAMIndex) map.index).finishInitialization();
            }
            return map;
        }
        
    }
}
