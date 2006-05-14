// kelondroMap.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.kelondro;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class kelondroMap {

    private static final int cachesize = 500;

    private kelondroDyn dyn;
    private kelondroMScoreCluster cacheScore;
    private HashMap cache;
    private long startup;
    private String[] sortfields, accfields;
    private HashMap sortClusterMap; // a String-kelondroMScoreCluster - relation
    private HashMap accMap; // to store accumulations of specific fields
    private int elementCount;

    public kelondroMap(kelondroDyn dyn) {
        this(dyn, null, null);
    }

    public kelondroMap(kelondroDyn dyn, String[] sortfields, String[] accfields) {
        this.dyn = dyn;
        this.cache = new HashMap();
        this.cacheScore = new kelondroMScoreCluster();
        this.startup = System.currentTimeMillis();
        this.elementCount = 0;

        // create fast ordering clusters and acc fields
        this.sortfields = sortfields;
        this.accfields = accfields;

        kelondroMScoreCluster[] cluster = null;
        if (sortfields == null) sortClusterMap = null; else {
            sortClusterMap = new HashMap();
            cluster = new kelondroMScoreCluster[sortfields.length];
            for (int i = 0; i < sortfields.length; i++) {
                cluster[i] = new kelondroMScoreCluster();   
            }
        }

        Long[] accumulator = null;
        if (accfields == null) accMap = null; else {
            accMap = new HashMap();
            accumulator = new Long[accfields.length];
            for (int i = 0; i < accfields.length; i++) {
                accumulator[i] = new Long(0);   
            }
        }

        // fill cluster and accumulator with values
        if ((sortfields != null) || (accfields != null)) try {
            kelondroDyn.dynKeyIterator it = dyn.dynKeys(true, false);
            String key, value;
            long valuel;
            Map map;
            while (it.hasNext()) {
                key = (String) it.next();
//              System.out.println("kelondroMap: enumerating key " + key);
                map = get(key);

                if (sortfields != null) for (int i = 0; i < sortfields.length; i++) {
                    value = (String) map.get(sortfields[i]);
                    if (value != null) cluster[i].setScore(key, kelondroMScoreCluster.string2score(value));
                }

                if (accfields != null) for (int i = 0; i < accfields.length; i++) {
                    value = (String) map.get(accfields[i]);
                    if (value != null) try {
                        valuel = Long.parseLong(value);
                        accumulator[i] = new Long(accumulator[i].longValue() + valuel);
                    } catch (NumberFormatException e) {}
                }
                elementCount++;
            }
        } catch (IOException e) {}

        // fill cluster
        if (sortfields != null) for (int i = 0; i < sortfields.length; i++) sortClusterMap.put(sortfields[i], cluster[i]);

        // fill acc map
        if (accfields != null) for (int i = 0; i < accfields.length; i++) accMap.put(accfields[i], accumulator[i]);
    }

    public int keySize() {
        return dyn.columnSize(0);
    }

    public long[] cacheNodeChunkSize() {
        return dyn.cacheNodeChunkSize();
    }

    public long[] cacheNodeFillStatus() {
        return dyn.cacheNodeFillStatus();
    }
    
    public String[] cacheObjectStatus() {
        return dyn.cacheObjectStatus();
    }

    public synchronized void set(String key, Map newMap) throws IOException {
        // update elementCount
        if ((sortfields != null) || (accfields != null)) {
            final Map oldMap = get(key, false);
            if (oldMap == null) {
                // new element
                elementCount++;
            } else {
                // element exists, update acc
                if (accfields != null) updateAcc(oldMap, false);
            }
        }
        // write entry
        writeKra(key, newMap, "");

        // check for space in cache
        checkCacheSpace();

        // write map to cache
        cacheScore.setScore(key, (int) ((System.currentTimeMillis() - startup) / 1000));
        cache.put(key, newMap);

        // update sortCluster
        if (sortClusterMap != null) updateSortCluster(key, newMap);

        // update accumulators with new values (add)
        if (accfields != null) updateAcc(newMap, true);
    }

    private synchronized void writeKra(final String key, final Map newMap, String comment) throws IOException {
        // write map to kra
        final kelondroRA kra = dyn.getRA(key);
        kra.writeMap(newMap, comment);
        kra.close();
    }

    private void updateAcc(Map map, boolean add) {
        String value;
        long valuel;
        Long accumulator;
        for (int i = 0; i < accfields.length; i++) {
            value = (String) map.get(accfields[i]);
            if (value != null) {
                try {
                    valuel = Long.parseLong(value);
                    accumulator = (Long) accMap.get(accfields[i]);
                    if (add) {
                        accMap.put(accfields[i], new Long(accumulator.longValue() + valuel));
                    } else {
                        accMap.put(accfields[i], new Long(accumulator.longValue() - valuel));
                    }
                } catch (NumberFormatException e) {}
            }
        }
    }

    private void updateSortCluster(final String key, final Map map) {
        String value;
        kelondroMScoreCluster cluster;
        for (int i = 0; i < sortfields.length; i++) {
            value = (String) map.get(sortfields[i]);
            if (value != null) {
                cluster = (kelondroMScoreCluster) sortClusterMap.get(sortfields[i]);
                cluster.setScore(key, kelondroMScoreCluster.string2score(value));
                sortClusterMap.put(sortfields[i], cluster);
            }
        }
    }

    public synchronized void remove(String key) throws IOException {
        // update elementCount
        if (key == null) return;
        if ((sortfields != null) || (accfields != null)) {
            final Map map = get(key);
            if (map != null) {
                // update count
                elementCount--;

                // update accumulators (subtract)
                if (accfields != null) updateAcc(map, false);

                // remove from sortCluster
                if (sortfields != null) deleteSortCluster(key);
            }
        }
        // remove from cache
        cacheScore.deleteScore(key);
        cache.remove(key);

        // remove from file
        dyn.remove(key);
    }

    private void deleteSortCluster(final String key) {
        if (key == null) return;
        kelondroMScoreCluster cluster;
        for (int i = 0; i < sortfields.length; i++) {
            cluster = (kelondroMScoreCluster) sortClusterMap.get(sortfields[i]);
            cluster.deleteScore(key);
            sortClusterMap.put(sortfields[i], cluster);
        }
    }

    public synchronized Map get(final String key) throws IOException {
        if (key == null) return null;
        return get(key, true);
    }

    private synchronized Map get(final String key, final boolean storeCache) throws IOException {
        // load map from cache
        Map map = (Map) cache.get(key);
        if (map != null) return map;

        // load map from kra
        if (!(dyn.existsDyn(key))) return null;
        //final kelondroRA kra = new kelondroBufferedRA(dyn.getRA(key), dyn.cacheObjectChunkSize(), 0);
        final kelondroRA kra = dyn.getRA(key);
        map = kra.readMap();
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

    public synchronized kelondroDyn.dynKeyIterator keys(final boolean up, final boolean rotating) throws IOException {
        // simple enumeration of key names without special ordering
        return dyn.dynKeys(up, rotating);
    }

    public synchronized kelondroDyn.dynKeyIterator keys(final boolean up, final boolean rotating, final byte[] firstKey) throws IOException {
        // simple enumeration of key names without special ordering
        return dyn.dynKeys(up, rotating, firstKey);
    }

    public synchronized Iterator keys(final boolean up, /* sorted by */ String field) {
        // sorted iteration using the sortClusters
        if (sortClusterMap == null) return null;
        final kelondroMScoreCluster cluster = (kelondroMScoreCluster) sortClusterMap.get(field);
        if (cluster == null) return null; // sort field does not exist
        //System.out.println("DEBUG: cluster for field " + field + ": " + cluster.toString());
        return cluster.scores(up);
    }

    public synchronized mapIterator maps(final boolean up, final boolean rotating) throws IOException {
        return new mapIterator(keys(up, rotating));
    }

    public synchronized mapIterator maps(final boolean up, final boolean rotating, final byte[] firstKey) throws IOException {
        return new mapIterator(keys(up, rotating, firstKey));
    }

    public synchronized mapIterator maps(final boolean up, final String field) {
        return new mapIterator(keys(up, field));
    }

    public synchronized long getAcc(final String field) {
        final Long accumulator = (Long) accMap.get(field);
        if (accumulator == null) return -1;
        return accumulator.longValue();
    }

    public synchronized int size() {
        if ((sortfields != null) || (accfields != null)) return elementCount;
        return dyn.size();
    }

    public void close() throws IOException {
        // finish queue
        //writeWorker.terminate(true);

        // close cluster
        if (sortClusterMap != null) {
            for (int i = 0; i < sortfields.length; i++) sortClusterMap.remove(sortfields[i]);
            sortClusterMap = null;
        }
        cache = null;
        cacheScore = null;

        // close file
        dyn.close();
    }

    public class mapIterator implements Iterator {
        // enumerates Map-Type elements
        // the key is also included in every map that is returned; it's key is 'key'

        Iterator keyIterator;
        boolean finish;

        public mapIterator(Iterator keyIterator) {
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
                final Map map = get(nextKey);
                if (map == null) throw new kelondroException(dyn.filename, "no more elements available");
                map.put("key", nextKey);
                return map;
            } catch (IOException e) {
                finish = true;
                return null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    } // class mapIterator

} // class kelondroMap
