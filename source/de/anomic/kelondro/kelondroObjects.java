// kelondroObjects.java
// -----------------------
// (C) 29.01.2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
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

package de.anomic.kelondro;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class kelondroObjects {

    private kelondroBLOB blob;
    private kelondroMScoreCluster<String> cacheScore;
    private HashMap<String, HashMap<String, String>> cache;
    private long startup;
    private int cachesize;


    public kelondroObjects(kelondroBLOB blob, int cachesize) {
        this.blob = blob;
        this.cache = new HashMap<String, HashMap<String, String>>();
        this.cacheScore = new kelondroMScoreCluster<String>();
        this.startup = System.currentTimeMillis();
        this.cachesize = cachesize;
    }
    
    public void clear() throws IOException {
    	this.blob.clear();
        this.cache = new HashMap<String, HashMap<String, String>>();
        this.cacheScore = new kelondroMScoreCluster<String>();
    }

    public int keySize() {
        return blob.keylength();
    }


    private static String map2string(final Map<String, String> map, final String comment) throws IOException {
        final Iterator<Map.Entry<String, String>> iter = map.entrySet().iterator();
        Map.Entry<String, String> entry;
        final StringBuffer bb = new StringBuffer(map.size() * 40);
        bb.append("# ").append(comment).append("\r\n");
        while (iter.hasNext()) {
            entry = iter.next();
            bb.append(entry.getKey()).append('=');
            if (entry.getValue() != null) { bb.append(entry.getValue()); }
            bb.append("\r\n");
        }
        bb.append("# EOF\r\n");
        return bb.toString();
    }

    private static HashMap<String, String> string2map(String s) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes())));
        final HashMap<String, String> map = new HashMap<String, String>();
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
    
    public synchronized void set(String key, HashMap<String, String> newMap) throws IOException {
        assert (key != null);
        assert (key.length() > 0);
        assert (newMap != null);
        if (cacheScore == null) return; // may appear during shutdown

        // write entry
        blob.put(key, map2string(newMap, "").getBytes());

        // check for space in cache
        checkCacheSpace();

        // write map to cache
        cacheScore.setScore(key, (int) ((System.currentTimeMillis() - startup) / 1000));
        cache.put(key, newMap);
    }

    public synchronized void remove(String key) throws IOException {
        // update elementCount
        if (key == null) return;
        
        // remove from cache
        cacheScore.deleteScore(key);
        cache.remove(key);

        // remove from file
        blob.remove(key);
    }

    public synchronized HashMap<String, String> get(final String key) throws IOException {
        if (key == null) return null;
        return get(key, true);
    }

    protected synchronized HashMap<String, String> get(final String key, final boolean storeCache) throws IOException {
        // load map from cache
        assert key != null;
        if (cache == null) return null; // case may appear during shutdown
        HashMap<String, String> map = cache.get(key);
        if (map != null) return map;

        // load map from kra
        if (!(blob.has(key))) return null;
        
        // read object
        byte[] b = blob.get(key);
        if (b == null) return null;
        map = string2map(new String(b));

        if (storeCache) {
            // cache it also
            checkCacheSpace();
            // write map to cache
            cacheScore.setScore(key, (int) ((System.currentTimeMillis() - startup) / 1000));
            cache.put(key, map);
        }

        // return value
        return map;
    }
    
    private synchronized void checkCacheSpace() {
        // check for space in cache
        if (cache == null) return; // may appear during shutdown
        if (cache.size() >= cachesize) {
            // delete one entry
            final String delkey = cacheScore.getMinObject();
            cacheScore.deleteScore(delkey);
            cache.remove(delkey);
        }
    }

    public synchronized kelondroCloneableIterator<String> keys(final boolean up, final boolean rotating) throws IOException {
        // simple enumeration of key names without special ordering
        return blob.keys(up, rotating);
    }

    public synchronized kelondroCloneableIterator<String> keys(final boolean up, final boolean rotating, final byte[] firstKey, final byte[] secondKey) throws IOException {
        // simple enumeration of key names without special ordering
        kelondroCloneableIterator<String> i = blob.keys(up, firstKey);
        if (rotating) return new kelondroRotateIterator<String>(i, secondKey, blob.size()); else return i;
    }


    public synchronized objectIterator entries(final boolean up, final boolean rotating) throws IOException {
        return new objectIterator(keys(up, rotating));
    }

    public synchronized objectIterator entries(final boolean up, final boolean rotating, final byte[] firstKey, final byte[] secondKey) throws IOException {
        return new objectIterator(keys(up, rotating, firstKey, secondKey));
    }

    public synchronized int size() {
        return blob.size();
    }

    public void close() {
        // finish queue
        //writeWorker.terminate(true);

        cache = null;
        cacheScore = null;

        // close file
        blob.close();
    }

    public class objectIterator implements Iterator<HashMap<String, String>> {
        // enumerates Map-Type elements
        // the key is also included in every map that is returned; it's key is 'key'

        Iterator<String> keyIterator;
        boolean finish;

        public objectIterator(Iterator<String> keyIterator) {
            this.keyIterator = keyIterator;
            this.finish = false;
        }

        public boolean hasNext() {
            return (!(finish)) && (keyIterator.hasNext());
        }

        public HashMap<String, String> next() {
            final String nextKey = keyIterator.next();
            if (nextKey == null) {
                finish = true;
                return null;
            }
            try {
                final HashMap<String, String> obj = get(nextKey);
                if (obj == null) throw new kelondroException("no more elements available");
                return obj;
            } catch (IOException e) {
                finish = true;
                return null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    } // class mapIterator
}
