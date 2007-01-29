// kelondroMapObjects.java
// -----------------------
// (C) 29.01.2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2004 as part of kelondroMap on http://www.anomic.de
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
import java.util.Map;

public class kelondroMapObjects extends kelondroObjects {

    private String[] sortfields, accfields;
    private HashMap sortClusterMap; // a String-kelondroMScoreCluster - relation
    private HashMap accMap; // to store accumulations of specific fields
    private int elementCount;
    
    public kelondroMapObjects(kelondroDyn dyn, int cachesize) {
        this(dyn, cachesize, null, null);
    }
    
    public kelondroMapObjects(kelondroDyn dyn, int cachesize, String[] sortfields, String[] accfields) {
        super(dyn, cachesize);
        
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
                map = getMap(key);
                if (map == null) break;
                
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
    
    public synchronized void set(String key, Map newMap) throws IOException {
        assert (key != null);
        assert (key.length() > 0);
        assert (newMap != null);

        // update elementCount
        if ((sortfields != null) || (accfields != null)) {
            final Map oldMap = getMap(key, false);
            if (oldMap == null) {
                // new element
                elementCount++;
            } else {
                // element exists, update acc
                if (accfields != null) updateAcc(oldMap, false);
            }
        }
        
        super.set(key, new kelondroObjectsMapEntry(newMap));
        
        // update sortCluster
        if (sortClusterMap != null) updateSortCluster(key, newMap);

        // update accumulators with new values (add)
        if (accfields != null) updateAcc(newMap, true);
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
        if (key == null) return;
        
        // update elementCount
        if ((sortfields != null) || (accfields != null)) {
            final Map map = getMap(key);
            if (map != null) {
                // update count
                elementCount--;

                // update accumulators (subtract)
                if (accfields != null) updateAcc(map, false);

                // remove from sortCluster
                if (sortfields != null) deleteSortCluster(key);
            }
        }
        super.remove(key);
    }
    
    public Map getMap(String key) {
        try {
            kelondroObjectsMapEntry mapEntry = (kelondroObjectsMapEntry) super.get(key);
            if (mapEntry == null) return null;
            return mapEntry.map();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    protected Map getMap(String key, boolean cache) {
        try {
            kelondroObjectsMapEntry mapEntry = (kelondroObjectsMapEntry) super.get(key, cache);
            if (mapEntry == null) return null;
            return mapEntry.map();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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
    
    public synchronized Iterator keys(final boolean up, /* sorted by */ String field) {
        // sorted iteration using the sortClusters
        if (sortClusterMap == null) return null;
        final kelondroMScoreCluster cluster = (kelondroMScoreCluster) sortClusterMap.get(field);
        if (cluster == null) return null; // sort field does not exist
        //System.out.println("DEBUG: cluster for field " + field + ": " + cluster.toString());
        return cluster.scores(up);
    }
    
    public synchronized mapIterator maps(final boolean up, final String field) {
        return new mapIterator(keys(up, field));
    }
    
    public synchronized mapIterator maps(final boolean up, final boolean rotating) throws IOException {
        return new mapIterator(keys(up, rotating));
    }

    public synchronized mapIterator maps(final boolean up, final boolean rotating, final byte[] firstKey) throws IOException {
        return new mapIterator(keys(up, rotating, firstKey));
    }
    
    public synchronized long getAcc(final String field) {
        final Long accumulator = (Long) accMap.get(field);
        if (accumulator == null) return -1;
        return accumulator.longValue();
    }
    
    public synchronized int size() {
        if ((sortfields != null) || (accfields != null)) return elementCount;
        return super.size();
    }
    
    public void close() throws IOException {
        // close cluster
        if (sortClusterMap != null) {
            for (int i = 0; i < sortfields.length; i++) sortClusterMap.remove(sortfields[i]);
            sortClusterMap = null;
        }
        
        super.close();
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
            return (!(finish)) && (keyIterator != null) && (keyIterator.hasNext());
        }

        public Object next() {
            final String nextKey = (String) keyIterator.next();
            if (nextKey == null) {
                finish = true;
                return null;
            }
            final Map map = getMap(nextKey);
            //assert (map != null) : "nextKey = " + nextKey;
            if (map == null) throw new kelondroException("no more elements available");
            map.put("key", nextKey);
            return map;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    } // class mapIterator
}
