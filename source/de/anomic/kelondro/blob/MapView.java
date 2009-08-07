// kelondroMap.java
// -----------------------
// (C) 29.01.2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 as kelondroMap on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package de.anomic.kelondro.blob;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.kelondro.index.SimpleARC;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.order.RotateIterator;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.kelondroException;


public class MapView {

    private BLOB blob;
    private SimpleARC<String, Map<String, String>> cache;
    private final char fillchar;

    
    public MapView(final Heap blob, final int cachesize, char fillchar) {
        this.blob = blob;
        this.cache = new SimpleARC<String, Map<String, String>>(cachesize);
        this.fillchar = fillchar;
        /*
        // debug
        try {
            kelondroCloneableIterator<byte[]> i = keys(true, false);
            int c = 20;
            HashSet<String> t = new HashSet<String>();
            while (i.hasNext()) {
                c--; if (c <= 0) break;
                byte[] b = i.next();
                String s = new String(b);
                System.out.println("*** DEBUG kelondroMap " + blob.name() + " KEY=" + s);
                t.add(s);
            }
            Iterator<String> j = t.iterator();
            while (j.hasNext()) {
                String s = j.next();
                if (this.get(s) == null) System.out.println("*** DEBUG kelondroMap " + blob.name() + " KEY=" + s + " cannot be found.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
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
    public synchronized void clear() throws IOException {
    	this.blob.clear();
        this.cache.clear();
    }

    private static String map2string(final Map<String, String> map, final String comment) {
        final StringBuilder bb = new StringBuilder(map.size() * 40);
        bb.append("# ").append(comment).append("\r\n");
        for (Map.Entry<String, String> entry: map.entrySet()) {
            if (entry.getValue() != null) {
                bb.append(entry.getKey());
                bb.append('=');
                bb.append(entry.getValue());
                bb.append("\r\n");
             }
        }
        bb.append("# EOF\r\n");
        return bb.toString();
    }

    private static Map<String, String> string2map(final String s) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes())));
        final Map<String, String> map = new HashMap<String, String>();
        String line;
        int pos;
        while ((line = br.readLine()) != null) { // very slow readLine????
            line = line.trim();
            if (line.equals("# EOF")) return map;
            if ((line.length() == 0) || (line.charAt(0) == '#')) continue;
            pos = line.indexOf("=");
            if (pos < 0) continue;
            map.put(line.substring(0, pos), line.substring(pos + 1));
        }
        return map;
    }
    
    /**
     * write a whole byte array as Map to the table
     * @param key  the primary key
     * @param newMap
     * @throws IOException
     */
    public void put(String key, final Map<String, String> newMap) throws IOException {
        assert key != null;
        assert key.length() > 0;
        assert newMap != null;
        key = normalizeKey(key);
        assert blob != null;
        synchronized (this) {
            // write entry
        	String s = map2string(newMap, "W" + DateFormatter.formatShortSecond() + " ");
        	assert s != null;
            blob.put(key.getBytes("UTF-8"), s.getBytes("UTF-8"));
    
            // write map to cache
            cache.put(key, newMap);
        }
    }

    /**
     * remove a Map
     * @param key  the primary key
     * @throws IOException
     */
    public void remove(String key) throws IOException {
        // update elementCount
        if (key == null) return;
        key = normalizeKey(key);
        
        synchronized (this) {
            // remove from cache
            cache.remove(key);
    
            // remove from file
            blob.remove(key.getBytes());
        }
    }
    
    /**
     * check if a specific key is in the database
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    public boolean has(String key) throws IOException {
        assert key != null;
        if (cache == null) return false; // case may appear during shutdown
        key = normalizeKey(key);
        synchronized (this) {
            if (this.cache.containsKey(key)) return true;
            return this.blob.has(key.getBytes());
        }
    }

    /**
     * retrieve the whole Map from the table
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    public Map<String, String> get(final String key) throws IOException {
        if (key == null) return null;
        return get(key, true);
    }
    
    private String normalizeKey(String key) {
    	if (key.length() > blob.keylength()) key = key.substring(0, blob.keylength());
        while (key.length() < blob.keylength()) key += fillchar;
        return key;
    }

    protected Map<String, String> get(String key, final boolean storeCache) throws IOException {
        // load map from cache
        assert key != null;
        if (cache == null) return null; // case may appear during shutdown
        key = normalizeKey(key);
        
        synchronized (this) {
            Map<String, String> map = cache.get(key);
            if (map != null) return map;
    
            // load map
            if (!(blob.has(key.getBytes()))) return null;
            
            // read object
            final byte[] b = blob.get(key.getBytes());
            if (b == null) return null;
            map = string2map(new String(b, "UTF-8"));
    
            if (storeCache) {
                // write map to cache
                cache.put(key, map);
            }
    
            // return value
            return map;
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

    /**
     * close the Map table
     */
    public synchronized void close() {
        cache = null;

        // close file
        if (blob != null) blob.close(true);
        blob = null;
    }
    
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
                final Map<String, String> obj = get(new String(nextKey, "UTF-8"));
                if (obj == null) throw new kelondroException("no more elements available");
                return obj;
            } catch (final IOException e) {
                finish = true;
                return null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    } // class mapIterator
    
    public static void main(String[] args) {
        // test the class
        File f = new File("maptest");
        if (f.exists()) FileUtils.deletedelete(f);
        try {
            // make a blob
            Heap blob = new Heap(f, 12, NaturalOrder.naturalOrder, 1024 * 1024);
            // make map
            MapView map = new MapView(blob, 1024, '_');
            // put some values into the map
            Map<String, String> m = new HashMap<String, String>();
            m.put("k", "000"); map.put("123", m);
            m.put("k", "111"); map.put("456", m);
            m.put("k", "222"); map.put("789", m);
            // iterate over keys
            Iterator<byte[]> i = map.keys(true, false);
            while (i.hasNext()) {
                System.out.println("key: " + new String(i.next(), "UTF-8"));
            }
            // clean up
            map.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
