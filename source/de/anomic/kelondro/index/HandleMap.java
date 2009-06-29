// HandleMap.java
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
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.yacy.dht.FlatWordPartitionScheme;

public class HandleMap implements Iterable<Row.Entry> {
    
    private final Row rowdef;
    private ObjectIndexCache index;
    
    /**
     * initialize a HandleMap
     * This may store a key and a long value for each key.
     * The class is used as index for database files
     * @param keylength
     * @param objectOrder
     * @param space
     */
    public HandleMap(final int keylength, final ByteOrder objectOrder, int idxbytes, final int initialspace, final int expectedspace) {
        this.rowdef = new Row(new Column[]{new Column("key", Column.celltype_binary, Column.encoder_bytes, keylength, "key"), new Column("long c-" + idxbytes + " {b256}")}, objectOrder);
        this.index = new ObjectIndexCache(rowdef, initialspace, expectedspace);
    }

    /**
     * initialize a HandleMap with the content of a dumped index
     * @param keylength
     * @param objectOrder
     * @param file
     * @throws IOException 
     */
    public HandleMap(final int keylength, final ByteOrder objectOrder, int idxbytes, final File file, final int expectedspace) throws IOException {
        this(keylength, objectOrder, idxbytes, (int) (file.length() / (keylength + idxbytes)), expectedspace);
        // read the index dump and fill the index
        InputStream is = new BufferedInputStream(new FileInputStream(file), 1024 * 1024);
        if (file.getName().endsWith(".gz")) is = new GZIPInputStream(is);
        byte[] a = new byte[keylength + idxbytes];
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
    }
    
    public int[] saturation() {
    	int keym = 0;
    	int valm = this.rowdef.width(1);
    	int valc;
    	byte[] lastk = null, thisk;
    	for (Row.Entry row: this) {
    		// check length of key
    		if (lastk == null) {
    			lastk = row.bytes();
    		} else {
    			thisk = row.bytes();
    			keym = Math.max(keym, eq(lastk, thisk));
    			lastk = thisk;
    		}

    		// check length of value
    		for (valc = this.rowdef.primaryKeyLength; valc < this.rowdef.objectsize; valc++) {
    			if (lastk[valc] != 0) break;
    		} // valc is the number of leading zeros plus primaryKeyLength
    		valm = Math.min(valm, valc - this.rowdef.primaryKeyLength); // valm is the number of leading zeros
    	}
    	return new int[]{keym, this.rowdef.width(1) - valm};
    }

    private int eq(byte[] a, byte[] b) {
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
    public int dump(File file) throws IOException {
        // we must use an iterator from the combined index, because we need the entries sorted
        // otherwise we could just write the byte[] from the in kelondroRowSet which would make
        // everything much faster, but this is not an option here.
        File tmp = new File(file.getParentFile(), file.getName() + ".prt");
        Iterator<Row.Entry> i = this.index.rows(true, null);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp), 4 * 1024 * 1024);
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

    public Row row() {
        return index.row();
    }
    
    public void clear() {
        index.clear();
    }
    
    public synchronized boolean has(final byte[] key) {
        assert (key != null);
        return index.has(key);
    }
    
    public synchronized long get(final byte[] key) {
        assert (key != null);
        final Row.Entry indexentry = index.get(key);
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }
    
    public synchronized long put(final byte[] key, final long l) {
        assert l >= 0 : "l = " + l;
        assert (key != null);
        final Row.Entry newentry = index.row().newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, l);
        final Row.Entry oldentry = index.replace(newentry);
        if (oldentry == null) return -1;
        return oldentry.getColLong(1);
    }
    
    public synchronized void putUnique(final byte[] key, final long l) {
        assert l >= 0 : "l = " + l;
        assert (key != null);
        final Row.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, l);
        index.addUnique(newentry);
    }
    
    public synchronized long add(final byte[] key, long a) {
        assert key != null;
        assert a > 0; // it does not make sense to add 0. If this occurres, it is a performance issue

        final Row.Entry indexentry = index.get(key);
        if (indexentry == null) {
            final Row.Entry newentry = this.rowdef.newEntry();
            newentry.setCol(0, key);
            newentry.setCol(1, a);
            index.addUnique(newentry);
            return 1;
        }
        long i = indexentry.getColLong(1) + a;
        indexentry.setCol(1, i);
        index.put(indexentry);
        return i;
    }
    
    public synchronized long inc(final byte[] key) {
        return add(key, 1);
    }
    
    public synchronized long dec(final byte[] key) {
        return add(key, -1);
    }
    
    public synchronized ArrayList<Long[]> removeDoubles() {
        final ArrayList<Long[]> report = new ArrayList<Long[]>();
        Long[] is;
        int c;
        long l;
        final int initialSize = this.size();
        for (final RowCollection rowset: index.removeDoubles()) {
            is = new Long[rowset.size()];
            c = 0;
            for (Row.Entry e: rowset) {
            	l = e.getColLong(1);
            	assert l < initialSize : "l = " + l + ", initialSize = " + initialSize;
                is[c++] = Long.valueOf(l);
            }
            report.add(is);
        }
        return report;
    }
    
    public synchronized long remove(final byte[] key) {
        assert (key != null);
        final Row.Entry indexentry = index.remove(key);
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }

    public synchronized long removeone() {
        final Row.Entry indexentry = index.removeOne();
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }
    
    public synchronized int size() {
        return index.size();
    }
    
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        return index.keys(up, firstKey);
    }

    public synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) {
        return index.rows(up, firstKey);
    }
    
    public synchronized void close() {
        index.close();
        index = null;
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
    public static initDataConsumer asynchronusInitializer(final int keylength, final ByteOrder objectOrder, int idxbytes, final int space, final int expectedspace, int bufferSize) {
        initDataConsumer initializer = new initDataConsumer(new HandleMap(keylength, objectOrder, idxbytes, space, expectedspace), bufferSize);
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
    private static final entry poisonEntry = new entry(new byte[0], 0);
    
    public static class initDataConsumer implements Callable<HandleMap> {

        private BlockingQueue<entry> cache;
        private HandleMap map;
        private Future<HandleMap> result;
        private boolean sortAtEnd;
        
        public initDataConsumer(HandleMap map, int bufferCount) {
            this.map = map;
            cache = new ArrayBlockingQueue<entry>(bufferCount);
            sortAtEnd = false;
        }
        
        protected void setResult(Future<HandleMap> result) {
            this.result = result;
        }
        
        /**
         * hand over another entry that shall be inserted into the HandleMap with an addl method
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
         * had been processed, and the content was sorted. It returns the HandleMap
         * that the user wanted to initialize
         * @return
         * @throws InterruptedException
         * @throws ExecutionException
         */
        public HandleMap result() throws InterruptedException, ExecutionException {
            return this.result.get();
        }
        
        public HandleMap call() throws IOException {
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
    	int count = (args.length == 0) ? 1000000 : Integer.parseInt(args[0]);
    	System.out.println("Starting test with " + count + " objects");
    	System.out.println("expected  memory: " + (count * 16) + " bytes");
    	System.out.println("available memory: " + MemoryControl.available());
        Random r = new Random(0);
        long start = System.currentTimeMillis();

        System.gc(); // for resource measurement
        long a = MemoryControl.available();
        HandleMap idx = new HandleMap(12, Base64Order.enhancedCoder, 4, 0, 150000);
        for (int i = 0; i < count; i++) {
            idx.inc(FlatWordPartitionScheme.positionToHash(r.nextInt(count)));
        }
        long timek = ((long) count) * 1000L / (System.currentTimeMillis() - start);
        System.out.println("Result HandleMap: " + timek + " inc per second");
        System.gc();
        long memk = a - MemoryControl.available();
        System.out.println("Used Memory: " + memk + " bytes");
        System.out.println("x " + idx.get(FlatWordPartitionScheme.positionToHash(0)));
        idx = null;
        
        r = new Random(0);
        start = System.currentTimeMillis();
        byte[] hash;
        Integer d;
        System.gc(); // for resource measurement
        a = MemoryControl.available();
        TreeMap<byte[], Integer> hm = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        for (int i = 0; i < count; i++) {
            hash = FlatWordPartitionScheme.positionToHash(r.nextInt(count));
            d = hm.get(hash);
            if (d == null) hm.put(hash, 1); else hm.put(hash, d + 1);
        }
        long timej =  ((long) count) * 1000L / (System.currentTimeMillis() - start);
        System.out.println("Result   TreeMap: " + timej + " inc per second");
        System.gc();
        long memj = a - MemoryControl.available();
        System.out.println("Used Memory: " + memj + " bytes");
        System.out.println("x " + hm.get(FlatWordPartitionScheme.positionToHash(0)));
        System.out.println("Geschwindigkeitsfaktor j/k: " + ((float) (10 * timej / timek) / 10.0) + " - je kleiner desto besser fuer kelondro");
        System.out.println("Speicherplatzfaktor    j/k: " + ((float) (10 * memj / memk) / 10.0) + " - je groesser desto besser fuer kelondro");
        System.exit(0);
    }

	public Iterator<Row.Entry> iterator() {
		return this.rows(true, null);
	}
}
