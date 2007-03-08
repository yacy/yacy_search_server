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

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

public class kelondroObjects {

    private kelondroDyn dyn;
    private kelondroMScoreCluster cacheScore;
    private HashMap cache;
    private long startup;
    private int cachesize;


    public kelondroObjects(kelondroDyn dyn, int cachesize) {
        this.dyn = dyn;
        this.cache = new HashMap();
        this.cacheScore = new kelondroMScoreCluster();
        this.startup = System.currentTimeMillis();
        this.cachesize = cachesize;
    }

    public int keySize() {
        return dyn.row().width(0);
    }

    public synchronized void set(String key, kelondroObjectsEntry newMap) throws IOException {
        assert (key != null);
        assert (key.length() > 0);
        assert (newMap != null);
    
        // write entry
        kelondroRA kra = dyn.getRA(key);
        newMap.write(kra);
        kra.close();

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
        dyn.remove(key);
    }

    public synchronized kelondroObjectsEntry get(final String key) throws IOException {
        if (key == null) return null;
        return get(key, true);
    }

    protected synchronized kelondroObjectsEntry get(final String key, final boolean storeCache) throws IOException {
        // load map from cache
        kelondroObjectsEntry map = (kelondroObjectsEntry) cache.get(key);
        if (map != null) return map;

        // load map from kra
        if (!(dyn.existsDyn(key))) return null;
        
        // read object
        kelondroRA kra = dyn.getRA(key);
        map = new kelondroObjectsMapEntry(kra);
        kra.close();

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
        if (cache.size() >= cachesize) {
            // delete one entry
            final String delkey = (String) cacheScore.getMinObject();
            cacheScore.deleteScore(delkey);
            cache.remove(delkey);
        }
    }

    public synchronized kelondroCloneableIterator keys(final boolean up, final boolean rotating) throws IOException {
        // simple enumeration of key names without special ordering
        return dyn.dynKeys(up, rotating);
    }

    public synchronized kelondroCloneableIterator keys(final boolean up, final boolean rotating, final byte[] firstKey) throws IOException {
        // simple enumeration of key names without special ordering
        kelondroCloneableIterator i = dyn.dynKeys(up, firstKey);
        if (rotating) return new kelondroRotateIterator(i); else return i;
    }


    public synchronized objectIterator entries(final boolean up, final boolean rotating) throws IOException {
        return new objectIterator(keys(up, rotating));
    }

    public synchronized objectIterator entries(final boolean up, final boolean rotating, final byte[] firstKey) throws IOException {
        return new objectIterator(keys(up, rotating, firstKey));
    }

    public synchronized int size() {
        try {
            return dyn.sizeDyn();
        } catch (IOException e) {
            return 0;
        }
    }

    public void close() throws IOException {
        // finish queue
        //writeWorker.terminate(true);

        cache = null;
        cacheScore = null;

        // close file
        dyn.close();
    }

    public class objectIterator implements Iterator {
        // enumerates Map-Type elements
        // the key is also included in every map that is returned; it's key is 'key'

        Iterator keyIterator;
        boolean finish;

        public objectIterator(Iterator keyIterator) {
            this.keyIterator = keyIterator;
            this.finish = false;
        }

        public boolean hasNext() {
            return (!(finish)) && (keyIterator.hasNext());
        }

        public Object next() {
            final String nextKey = (String) keyIterator.next();
            if (nextKey == null) {
                finish = true;
                return null;
            }
            try {
                final kelondroObjectsEntry obj = get(nextKey);
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
