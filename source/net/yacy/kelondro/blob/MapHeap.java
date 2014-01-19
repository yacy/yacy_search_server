// MapHeap.java
// -----------------------
// (C) 29.01.2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 as kelondroMap on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.blob;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.RotateIterator;

public class MapHeap implements Map<byte[], Map<String, String>> {

    private final BLOB blob;
    private final ARC<byte[], Map<String, String>> cache;
    private final char fillchar;


    public MapHeap(
            final File heapFile,
            final int keylength,
            final ByteOrder ordering,
            final int buffermax,
            final int cachesize,
            final char fillchar) throws IOException {
        this.blob = new Heap(heapFile, keylength, ordering, buffermax);
        this.cache = new ConcurrentARC<byte[], Map<String, String>>(cachesize, Math.min(32, 2 * Runtime.getRuntime().availableProcessors()), ordering);
        this.fillchar = fillchar;
    }

    /**
     * ask for the length of the primary key
     * @return the length of the key
     */
    public int keylength() {
        return this.blob.keylength();
    }

    /**
     * get the ordering of the primary keys
     * @return
     */
    public ByteOrder ordering() {
        return this.blob.ordering();
    }

    /**
     * clears the content of the database
     * @throws IOException
     */
    @Override
    public synchronized void clear() {
    	try {
            this.blob.clear();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        this.cache.clear();
    }

    private static String map2string(final Map<String, String> map, final String comment) {
        final StringBuilder bb = new StringBuilder(map.size() * 40);
        bb.append("# ").append(comment).append('\r').append('\n');
        for (final Map.Entry<String, String> entry: map.entrySet()) {
            if (entry.getValue() != null) {
                bb.append(entry.getKey());
                bb.append('=');
                bb.append(entry.getValue());
                bb.append('\r').append('\n');
             }
        }
        bb.append("# EOF\r\n");
        return bb.toString();
    }

    private static Map<String, String> bytes2map(final byte[] b) throws IOException, SpaceExceededException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(b)));
        final Map<String, String> map = new ConcurrentHashMap<String, String>();
        String line;
        int pos;
        try {
        while ((line = br.readLine()) != null) { // very slow readLine????
            line = line.trim();
            if (line.equals("# EOF")) return map;
            if ((line.isEmpty()) || (line.charAt(0) == '#')) continue;
            pos = line.indexOf('=');
            if (pos < 0) continue;
            map.put(line.substring(0, pos), line.substring(pos + 1));
        }
        } catch (final OutOfMemoryError e) {
            throw new SpaceExceededException(0, "readLine probably uses too much RAM", e);
        } finally {
            br.close();
        }
        return map;
    }


    // use our own formatter to prevent concurrency locks with other processes
    private final static GenericFormatter my_SHORT_SECOND_FORMATTER  = new GenericFormatter(GenericFormatter.FORMAT_SHORT_SECOND, GenericFormatter.time_second);

    /**
     * write a whole byte array as Map to the table
     * @param key  the primary key
     * @param newMap
     * @throws IOException
     * @throws SpaceExceededException
     */
    public void insert(byte[] key, final Map<String, String> newMap) throws IOException, SpaceExceededException {
        assert key != null;
        assert key.length > 0;
        assert newMap != null;
        key = normalizeKey(key);
        final String s = map2string(newMap, "W" + my_SHORT_SECOND_FORMATTER.format() + " ");
        assert s != null;
        final byte[] sb = UTF8.getBytes(s);
        if (this.cache == null) {
            // write entry
            if (this.blob != null) this.blob.insert(key, sb);
        } else {
            synchronized (this) {
                // write entry
                if (this.blob != null) this.blob.insert(key, sb);

                // write map to cache
                if (MemoryControl.shortStatus()) {
                    this.cache.clear();
                } else {
                    this.cache.insert(key, newMap);
                }
            }
        }
    }

    @Override
    public Map<String, String> put(final byte[] key, final Map<String, String> newMap) {
        Map<String, String> v = null;
        try {
            v = this.get(key);
            insert(key, newMap);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        return v;
    }

    /**
     * remove a Map
     * @param key  the primary key
     * @throws IOException
     */
    public void delete(byte[] key) throws IOException {
        // update elementCount
        if (key == null) return;
        key = normalizeKey(key);

        synchronized (this) {
            // remove from cache
            if (this.cache != null) this.cache.remove(key);

            // remove from file
            if (this.blob != null) this.blob.delete(key);
        }
    }

    @Override
    public Map<String, String> remove(final Object key)  {
        Map<String, String> v = null;
        try {
            v = this.get(key);
            delete((byte[]) key);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        return v;
    }

    /**
     * check if a specific key is in the database
     * @param key  the primary key
     * @return
     * @throws IOException
     */

    @Override
    public boolean containsKey(final Object k) {
        if (!(k instanceof byte[])) return false;
        assert k != null;
        if (this.cache == null) return false; // case may appear during shutdown
        final byte[] key = normalizeKey((byte[]) k);
        boolean h;
        synchronized (this) {
            h = this.cache.containsKey(key) || this.blob.containsKey(key);
        }
        return h;
    }

    /**
     * retrieve the whole Map from the table
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    public Map<String, String> get(final byte[] key) throws IOException, SpaceExceededException {
        if (key == null) return null;
        return get(key, true);
    }

    @Override
    public Map<String, String> get(final Object key) {
        if (key == null) return null;
        try {
            if (key instanceof byte[]) return get((byte[]) key);
            if (key instanceof String) return get(UTF8.getBytes((String) key));
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        return null;
    }

    private byte[] normalizeKey(final byte[] key) {
        if (this.blob == null || key == null) return key;
        if (key.length > this.blob.keylength()) {
            final byte[] b = new byte[this.blob.keylength()];
            System.arraycopy(key, 0, b, 0, this.blob.keylength());
            return b;
        }
        if (key.length < this.blob.keylength()) {
            final byte[] b = new byte[this.blob.keylength()];
            System.arraycopy(key, 0, b, 0, key.length);
            for (int i = key.length; i < b.length; i++) b[i] = (byte) this.fillchar;
            return b;
        }
        return key;
    }

    private byte[] removeFillchar(final byte[] key) {
        if (key == null) return key;
        int p = key.length - 1;
        while (p >= 0 && key[p] == this.fillchar) p--;
        if (p == key.length - 1) return key;
        // copy part of key into new byte[]
        final byte[] k = new byte[p + 1];
        System.arraycopy(key, 0, k, 0, k.length);
        return k;
    }

    protected Map<String, String> get(byte[] key, final boolean storeCache) throws IOException, SpaceExceededException {
        // load map from cache
        assert key != null;
        if (this.cache == null) return null; // case may appear during shutdown
        key = normalizeKey(key);

        if (MemoryControl.shortStatus()) {
            this.cache.clear();
        }

        // if we have the entry in the cache then just return that
        Map<String, String> map = this.cache.get(key);
        if (map != null) return map;

        // in all other cases we must look into the cache again within
        // a synchronization in case that the entry was not in the cache but stored
        // there while another process has taken it from the file system
        if (storeCache) {
            synchronized (this) {
                map = this.cache.get(key);
                if (map != null) return map;

                // read object
                final byte[] b = this.blob.get(key);
                if (b == null) return null;
                try {
                    map = bytes2map(b);
                } catch (final SpaceExceededException e) {
                    throw new IOException(e.getMessage());
                }

                // write map to cache
                this.cache.insert(key, map);

                // return value
                return map;
            }
        }
        byte[] b = null;
        synchronized (this) {
            map = this.cache.get(key);
            if (map != null) return map;
            b = this.blob.get(key);
        }
        if (b == null) return null;
        try {
            return bytes2map(b);
        } catch (final SpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final boolean rotating) throws IOException {
        // simple enumeration of key names without special ordering
        return new KeyIterator(up, rotating, null, null);
    }

    /**
     * return an iteration of the keys in the map
     * the keys in the map are de-normalized which means that the fill-character is removed
     * @param up
     * @param rotating
     * @param firstKey
     * @param secondKey
     * @return
     * @throws IOException
     */
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final boolean rotating, final byte[] firstKey, final byte[] secondKey) throws IOException {
        return new KeyIterator(up, rotating, firstKey, secondKey);
    }

    public synchronized CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
        return this.blob.keys(up, firstKey);
    }

    public class KeyIterator implements CloneableIterator<byte[]>, Iterator<byte[]> {

        private final boolean up, rotating;
        private final byte[] firstKey, secondKey;
        private Iterator<byte[]> iterator;
        final private CloneableIterator<byte[]> blobkeys;

        public KeyIterator(final boolean up, final boolean rotating, final byte[] firstKey, final byte[] secondKey) throws IOException {
            this.up = up;
            this.rotating = rotating;
            this.firstKey = firstKey;
            this.secondKey = secondKey;
            this.blobkeys = MapHeap.this.blob.keys(up, firstKey);
            this.iterator = rotating ? new RotateIterator<byte[]>(this.blobkeys, secondKey, MapHeap.this.blob.size()) : this.blobkeys;
        }

        @Override
        public byte[] next() {
            assert this.iterator != null;
            if (this.iterator == null) return null;
            return removeFillchar(this.iterator.next());
        }

        @Override
        public boolean hasNext() {
            return this.iterator != null && this.iterator.hasNext();
        }

        @Override
        public void remove() {
            this.iterator.remove();
        }

        @Override
        public CloneableIterator<byte[]> clone(final Object modifier) {
            try {
                return new KeyIterator(this.up, this.rotating, this.firstKey, this.secondKey);
            } catch (final IOException e) {
                return null;
            }
        }

        @Override
        public void close() {
            this.blobkeys.close();
        }
    }

    public synchronized Iterator<Map.Entry<byte[], Map<String, String>>> entries(final boolean up, final boolean rotating) throws IOException {
        return new FullMapIterator(keys(up, rotating));
    }

    public synchronized Iterator<Map.Entry<byte[], Map<String, String>>> entries(final boolean up, final boolean rotating, final byte[] firstKey, final byte[] secondKey) throws IOException {
        return new FullMapIterator(keys(up, rotating, firstKey, secondKey));
    }

    /**
     * ask for the number of entries
     * @return the number of entries in the table
     */
    @Override
    public int size() {
        return (this.blob == null) ? 0 : this.blob.size();
    }

    @Override
    public boolean isEmpty() {
        return (this.blob == null) ? true : this.blob.isEmpty();
    }

    /**
     * close the Map table
     */
    public synchronized void close() {
        this.cache.clear();

        // close file
        if (this.blob != null) this.blob.close(true);
    }

    @Override
    public void finalize() {
        close();
    }

    protected class FullMapIterator extends LookAheadIterator<Map.Entry<byte[], Map<String, String>>> implements Iterator<Map.Entry<byte[], Map<String, String>>> {
        // enumerates Map-Type elements
        // the key is also included in every map that is returned; it's key is 'key'

        private final Iterator<byte[]> keyIterator;

        FullMapIterator(final Iterator<byte[]> keyIterator) {
            this.keyIterator = keyIterator;
        }

        @Override
        public Map.Entry<byte[], Map<String, String>> next0() {
            if (this.keyIterator == null) return null;
            byte[] nextKey;
            Map<String, String> map;
            while (this.keyIterator.hasNext()) {
                nextKey = this.keyIterator.next();
                try {
                    map = get(nextKey, false);
                } catch (final IOException e) {
                    ConcurrentLog.warn("MapDataMining", e.getMessage());
                    continue;
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                    continue;
                }
                if (map == null) continue; // circumvention of a modified exception
                // produce entry
                Map.Entry<byte[], Map<String, String>> entry = new AbstractMap.SimpleImmutableEntry<byte[], Map<String, String>>(nextKey, map);
                return entry;
            }
            return null;
        }
    } // class FullMapIterator


    @Override
    public void putAll(final Map<? extends byte[], ? extends Map<String, String>> map) {
        for (final Map.Entry<? extends byte[], ? extends Map<String, String>> me: map.entrySet()) {
            try {
                insert(me.getKey(), me.getValue());
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    @Override
    public Set<byte[]> keySet() {
        final TreeSet<byte[]> set = new TreeSet<byte[]>(this.blob.ordering());
        try {
            final Iterator<byte[]> i = this.blob.keys(true, false);
            while (i.hasNext()) set.add(i.next());
        } catch (final IOException e) {}
        return set;
    }

    public final static byte[] POISON_QUEUE_ENTRY = "POISON".getBytes();
    public BlockingQueue<byte[]> keyQueue(final int size) {
        final ArrayBlockingQueue<byte[]> set = new ArrayBlockingQueue<byte[]>(size);
        (new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("MapHeap.keyQueue:" + size);
                try {
                    final Iterator<byte[]> i = MapHeap.this.blob.keys(true, false);
                    while (i.hasNext())
                        try {
                            set.put(i.next());
                        } catch (final InterruptedException e) {
                            break;
                        }
                } catch (final IOException e) {}
                try {
                    set.put(MapHeap.POISON_QUEUE_ENTRY);
                } catch (final InterruptedException e) {
                }
            }}).start();
        return set;
    }

    @Override
    public Collection<Map<String, String>> values() {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<java.util.Map.Entry<byte[], Map<String, String>>> entrySet() {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsValue(final Object value) {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }

    public static void main(final String[] args) {
        // test the class
        final File f = new File("maptest");
        if (f.exists()) FileUtils.deletedelete(f);
        try {
            // make map
            final MapHeap map = new MapHeap(f, 12, NaturalOrder.naturalOrder, 1024 * 1024, 1024, '_');
            // put some values into the map
            final Map<String, String> m = new HashMap<String, String>();
            m.put("k", "000"); map.insert("123".getBytes(), m);
            m.put("k", "111"); map.insert("456".getBytes(), m);
            m.put("k", "222"); map.insert("789".getBytes(), m);
            // iterate over keys
            final Iterator<byte[]> i = map.keys(true, false);
            while (i.hasNext()) {
                System.out.println("key: " + UTF8.String(i.next()));
            }
            // clean up
            map.close();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
    }
}
