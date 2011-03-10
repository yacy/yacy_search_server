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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.order.RotateIterator;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.kelondroException;

public class MapHeap implements Map<byte[], Map<String, String>> {

    private BLOB blob;
    private ARC<byte[], Map<String, String>> cache;
    private final char fillchar;

    
    public MapHeap(
            final File heapFile,
            final int keylength,
            final ByteOrder ordering,
            int buffermax,
            final int cachesize,
            char fillchar) throws IOException {
        this.blob = new Heap(heapFile, keylength, ordering, buffermax);
        this.cache = new ConcurrentARC<byte[], Map<String, String>>(cachesize, Runtime.getRuntime().availableProcessors(), ordering);
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
     * clears the content of the database
     * @throws IOException
     */
    public synchronized void clear() {
    	try {
            this.blob.clear();
        } catch (IOException e) {
            Log.logException(e);
        }
        this.cache.clear();
    }

    private static String map2string(final Map<String, String> map, final String comment) {
        final StringBuilder bb = new StringBuilder(map.size() * 40);
        bb.append("# ").append(comment).append('\r').append('\n');
        for (Map.Entry<String, String> entry: map.entrySet()) {
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

    private static Map<String, String> bytes2map(byte[] b) throws IOException, RowSpaceExceededException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(b)));
        final Map<String, String> map = new ConcurrentHashMap<String, String>();
        String line;
        int pos;
        try {
        while ((line = br.readLine()) != null) { // very slow readLine????
            line = line.trim();
            if (line.equals("# EOF")) return map;
            if ((line.length() == 0) || (line.charAt(0) == '#')) continue;
            pos = line.indexOf("=");
            if (pos < 0) continue;
            map.put(line.substring(0, pos), line.substring(pos + 1));
        }
        } catch (OutOfMemoryError e) {
            throw new RowSpaceExceededException(0, "readLine probably uses too much RAM", e);
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
     * @throws RowSpaceExceededException 
     */
    public void insert(byte[] key, final Map<String, String> newMap) throws IOException, RowSpaceExceededException {
        assert key != null;
        assert key.length > 0;
        assert newMap != null;
        key = normalizeKey(key);
        String s = map2string(newMap, "W" + my_SHORT_SECOND_FORMATTER.format() + " ");
        assert s != null;
        byte[] sb = UTF8.getBytes(s);
        if (cache == null) {
            // write entry
            if (blob != null) blob.insert(key, sb);
        } else {
            synchronized (this) {
                // write entry
                if (blob != null) blob.insert(key, sb);
    
                // write map to cache
                if (MemoryControl.shortStatus()) {
                    cache.clear();
                } else {
                    cache.put(key, newMap);
                }
            }
        }
    }
    
    public Map<String, String> put(byte[] key, final Map<String, String> newMap) {
        Map<String, String> v = null;
        try {
            v = this.get(key);
            insert(key, newMap);
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
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
            if (cache != null) cache.remove(key);
    
            // remove from file
            blob.delete(key);
        }
    }
    
    public Map<String, String> remove(Object key)  {
        Map<String, String> v = null;
        try {
            v = this.get(key);
            this.delete((byte[]) key);
        } catch (IOException e) {
            Log.logException(e);
        }
        return v;
    }
    
    /**
     * check if a specific key is in the database
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    
    public boolean containsKey(Object k) {
        if (!(k instanceof byte[])) return false;
        assert k != null;
        if (cache == null) return false; // case may appear during shutdown
        byte[] key = normalizeKey((byte[]) k);
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
    public Map<String, String> get(final byte[] key) throws IOException, RowSpaceExceededException {
        if (key == null) return null;
        return get(key, true);
    }
    
    public Map<String, String> get(final Object key) {
        if (key == null) return null;
        try {
            if (key instanceof byte[]) return get((byte[]) key);
            if (key instanceof String) return get(UTF8.getBytes((String) key));
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        return null;
    }

    private byte[] normalizeKey(byte[] key) {
        if (blob == null || key == null) return key;
        if (key.length > blob.keylength()) {
            byte[] b = new byte[blob.keylength()];
            System.arraycopy(key, 0, b, 0, blob.keylength());
            return b;
        }
        if (key.length < blob.keylength()) {
            byte[] b = new byte[blob.keylength()];
            System.arraycopy(key, 0, b, 0, key.length);
            for (int i = key.length; i < b.length; i++) b[i] = (byte) fillchar;
            return b;
        }
        return key;
    }

    protected Map<String, String> get(byte[] key, final boolean storeCache) throws IOException, RowSpaceExceededException {
        // load map from cache
        assert key != null;
        if (cache == null) return null; // case may appear during shutdown
        key = normalizeKey(key);
        
        Map<String, String> map;
        if (storeCache) {
            synchronized (this) {
                map = cache.get(key);
                if (map != null) return map;
    
                // read object
                final byte[] b = blob.get(key);
                if (b == null) return null;
                try {
                    map = bytes2map(b);
                } catch (RowSpaceExceededException e) {
                    throw new IOException(e.getMessage());
                }
        
                if (MemoryControl.shortStatus()) {
                    cache.clear();
                } else {
                    // write map to cache
                    cache.put(key, map);
                }
            }
            
            // return value
            return map;
        } else {
            byte[] b;
            synchronized (this) {
                map = cache.get(key);
                if (map != null) return map;
                b = blob.get(key);
            }
            if (b == null) return null;
            try {
                return bytes2map(b);
            } catch (RowSpaceExceededException e) {
                throw new IOException(e.getMessage());
            }
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
        return blob.keys(up, rotating);
    }
    
    /**
     * iterate over all keys
     * @param up
     * @param firstKey
     * @return
     * @throws IOException
     */
    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return keys(up, false, firstKey, null);
    }
    
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final boolean rotating, final byte[] firstKey, final byte[] secondKey) throws IOException {
        // simple enumeration of key names without special ordering
        final CloneableIterator<byte[]> i = blob.keys(up, firstKey);
        if (rotating) return new RotateIterator<byte[]>(i, secondKey, blob.size());
        return i;
    }


    public synchronized MapIterator entries(final boolean up, final boolean rotating) throws IOException {
        return new MapIterator(keys(up, rotating));
    }

    public synchronized MapIterator entries(final boolean up, final boolean rotating, final byte[] firstKey, final byte[] secondKey) throws IOException {
        return new MapIterator(keys(up, rotating, firstKey, secondKey));
    }

    /**
     * ask for the number of entries
     * @return the number of entries in the table
     */
    public synchronized int size() {
        return (blob == null) ? 0 : blob.size();
    }

    public synchronized boolean isEmpty() {
        return (blob == null) ? true : blob.isEmpty();
    }
    
    /**
     * close the Map table
     */
    public synchronized void close() {
        cache = null;

        // close file
        if (blob != null) blob.close(true);
        blob = null;
    }
    
    @Override
    public void finalize() {
        close();
    }

    public class MapIterator implements Iterator<Map<String, String>> {
        // enumerates Map-Type elements
        // the key is also included in every map that is returned; it's key is 'key'

        Iterator<byte[]> keyIterator;
        boolean finish;

        public MapIterator(final Iterator<byte[]> keyIterator) {
            this.keyIterator = keyIterator;
            this.finish = false;
        }

        public boolean hasNext() {
            return (!(finish)) && (keyIterator.hasNext());
        }

        public Map<String, String> next() {
            final byte[] nextKey = keyIterator.next();
            if (nextKey == null) {
                finish = true;
                return null;
            }
            try {
                final Map<String, String> obj = get(nextKey, false);
                if (obj == null) throw new kelondroException("no more elements available");
                return obj;
            } catch (final IOException e) {
                finish = true;
                return null;
            } catch (final RowSpaceExceededException e) {
                finish = true;
                return null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    } // class mapIterator

    public void putAll(Map<? extends byte[], ? extends Map<String, String>> map) {
        for (Map.Entry<? extends byte[], ? extends Map<String, String>> me: map.entrySet()) {
            try {
                this.insert(me.getKey(), me.getValue());
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }

    public Set<byte[]> keySet() {
        TreeSet<byte[]> set = new TreeSet<byte[]>(this.blob.ordering());
        try {
            Iterator<byte[]> i = this.blob.keys(true, false);
            while (i.hasNext()) set.add(i.next());
        } catch (IOException e) {}
        return set;
    }

    public Collection<Map<String, String>> values() {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }

    public Set<java.util.Map.Entry<byte[], Map<String, String>>> entrySet() {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }

    public boolean containsValue(Object value) {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) {
        // test the class
        File f = new File("maptest");
        if (f.exists()) FileUtils.deletedelete(f);
        try {
            // make map
            MapHeap map = new MapHeap(f, 12, NaturalOrder.naturalOrder, 1024 * 1024, 1024, '_');
            // put some values into the map
            Map<String, String> m = new HashMap<String, String>();
            m.put("k", "000"); map.insert("123".getBytes(), m);
            m.put("k", "111"); map.insert("456".getBytes(), m);
            m.put("k", "222"); map.insert("789".getBytes(), m);
            // iterate over keys
            Iterator<byte[]> i = map.keys(true, false);
            while (i.hasNext()) {
                System.out.println("key: " + UTF8.String(i.next()));
            }
            // clean up
            map.close();
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
    }
}
