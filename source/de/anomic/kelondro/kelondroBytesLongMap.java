// kelondroBytesLongMap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 08.04.2008 on http://yacy.net
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
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class kelondroBytesLongMap {
    
    private final kelondroRow rowdef;
    private kelondroRAMIndex index;
    
    public kelondroBytesLongMap(final int keylength, final kelondroByteOrder objectOrder, final int space) {
        this.rowdef = new kelondroRow(new kelondroColumn[]{new kelondroColumn("key", kelondroColumn.celltype_binary, kelondroColumn.encoder_bytes, keylength, "key"), new kelondroColumn("long c-8 {b256}")}, objectOrder, 0);
        this.index = new kelondroRAMIndex(rowdef, space);
    }
    
    public kelondroRow row() {
        return index.row();
    }
    
    public void clear() throws IOException {
        index.clear();
    }
    
    public synchronized long getl(final byte[] key) throws IOException {
        assert (key != null);
        final kelondroRow.Entry indexentry = index.get(key);
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }
    
    public synchronized long putl(final byte[] key, final long l) throws IOException {
        assert l >= 0 : "l = " + l;
        assert (key != null);
        final kelondroRow.Entry newentry = index.row().newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, l);
        final kelondroRow.Entry oldentry = index.put(newentry);
        if (oldentry == null) return -1;
        return oldentry.getColLong(1);
    }
    
    public synchronized void addl(final byte[] key, final long l) throws IOException {
        assert l >= 0 : "l = " + l;
        assert (key != null);
        final kelondroRow.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, l);
        index.addUnique(newentry);
    }
    
    public synchronized ArrayList<Long[]> removeDoubles() throws IOException {
        final ArrayList<kelondroRowCollection> indexreport = index.removeDoubles();
        final ArrayList<Long[]> report = new ArrayList<Long[]>();
        Long[] is;
        Iterator<kelondroRow.Entry> ei;
        int c;
        for (final kelondroRowCollection rowset: indexreport) {
            is = new Long[rowset.size()];
            ei = rowset.rows();
            c = 0;
            while (ei.hasNext()) {
                is[c++] = Long.valueOf(ei.next().getColLong(1));
            }
            report.add(is);
        }
        return report;
    }
    
    public synchronized long removel(final byte[] key) throws IOException {
        assert (key != null);
        final kelondroRow.Entry indexentry = index.remove(key);
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }

    public synchronized long removeonel() throws IOException {
        final kelondroRow.Entry indexentry = index.removeOne();
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
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
        initDataConsumer initializer = new initDataConsumer(new kelondroBytesLongMap(keylength, objectOrder, space), bufferSize);
        ExecutorService service = Executors.newSingleThreadExecutor();
        initializer.setResult(service.submit(initializer));
        service.shutdown();
        return initializer;
    }

    private static class entry {
        public byte[] key;
        public long l;
        public entry(final byte[] key, final long l) {
            this.key = key;
            this.l = l;
        }
    }
    
    public static class initDataConsumer implements Callable<kelondroBytesLongMap> {

        private BlockingQueue<entry> cache;
        private final entry poison = new entry(new byte[0], 0);
        private kelondroBytesLongMap map;
        private Future<kelondroBytesLongMap> result;
        
        public initDataConsumer(kelondroBytesLongMap map, int bufferCount) {
            this.map = map;
            cache = new ArrayBlockingQueue<entry>(bufferCount);
        }
        
        protected void setResult(Future<kelondroBytesLongMap> result) {
            this.result = result;
        }
        
        /**
         * hand over another entry that shall be inserted into the BytesLongMap with an addl method
         * @param key
         * @param l
         */
        public void consume(final byte[] key, final long l) {
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
        public void finish() {
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
        public kelondroBytesLongMap result() throws InterruptedException, ExecutionException {
            return this.result.get();
        }
        
        public kelondroBytesLongMap call() throws IOException {
            try {
                entry c;
                while ((c = cache.take()) != poison) {
                    map.addl(c.key, c.l);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            map.index.finishInitialization();
            return map;
        }
        
    }
}
