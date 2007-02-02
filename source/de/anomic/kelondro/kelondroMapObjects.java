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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class kelondroMapObjects extends kelondroObjects {

    private String[] sortfields, longaccfields, doubleaccfields;
    private HashMap sortClusterMap; // a String-kelondroMScoreCluster - relation
    private HashMap accMap; // to store accumulations of specific fields
    private int elementCount;
    
    public kelondroMapObjects(kelondroDyn dyn, int cachesize) {
        this(dyn, cachesize, null, null, null, null, null);
    }
    
    public kelondroMapObjects(kelondroDyn dyn, int cachesize, String[] sortfields, String[] longaccfields, String[] doubleaccfields, Method externalInitializer, Object externalHandler) {
        super(dyn, cachesize);
        
        // create fast ordering clusters and acc fields
        this.sortfields = sortfields;
        this.longaccfields = longaccfields;
        this.doubleaccfields = doubleaccfields;

        kelondroMScoreCluster[] cluster = null;
        if (sortfields == null) sortClusterMap = null; else {
            sortClusterMap = new HashMap();
            cluster = new kelondroMScoreCluster[sortfields.length];
            for (int i = 0; i < sortfields.length; i++) {
                cluster[i] = new kelondroMScoreCluster();   
            }
        }

        Long[] longaccumulator = null;
        if (longaccfields == null) accMap = null; else {
            accMap = new HashMap();
            longaccumulator = new Long[longaccfields.length];
            for (int i = 0; i < longaccfields.length; i++) {
                longaccumulator[i] = new Long(0);   
            }
        }
        
        Double[] doubleaccumulator = null;
        if (doubleaccfields == null) accMap = null; else {
            accMap = new HashMap();
            doubleaccumulator = new Double[doubleaccfields.length];
            for (int i = 0; i < doubleaccfields.length; i++) {
                doubleaccumulator[i] = new Double(0);   
            }
        }

        // fill cluster and accumulator with values
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) try {
            kelondroDyn.dynKeyIterator it = dyn.dynKeys(true, false);
            String mapname;
            Object cell;
            long valuel;
            double valued;
            Map map;
            while (it.hasNext()) {
                mapname = (String) it.next();
                map = getMap(mapname);
                if (map == null) break;
                
                if (sortfields != null) for (int i = 0; i < sortfields.length; i++) {
                    cell = map.get(sortfields[i]);
                    if (cell != null) cluster[i].setScore(mapname, kelondroMScoreCluster.object2score(cell));
                }

                if (longaccfields != null) for (int i = 0; i < longaccfields.length; i++) {
                    cell = map.get(longaccfields[i]);
                    valuel = 0;
                    if (cell != null) try {
                        if (cell instanceof Long)   valuel = ((Long) cell).longValue();
                        if (cell instanceof String) valuel = Long.parseLong((String) cell);
                        longaccumulator[i] = new Long(longaccumulator[i].longValue() + valuel);
                    } catch (NumberFormatException e) {}
                }
                
                if (doubleaccfields != null) for (int i = 0; i < doubleaccfields.length; i++) {
                    cell = map.get(doubleaccfields[i]);
                    valued = 0d;
                    if (cell != null) try {
                        if (cell instanceof Double) valued = ((Double) cell).doubleValue();
                        if (cell instanceof String) valued = Double.parseDouble((String) cell);
                        doubleaccumulator[i] = new Double(doubleaccumulator[i].doubleValue() + valued);
                    } catch (NumberFormatException e) {}
                }
                
                if ((externalHandler != null) && (externalInitializer != null)) {
                    try {
                        externalInitializer.invoke(externalHandler, new Object[]{mapname, map});
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
                elementCount++;
            }
        } catch (IOException e) {}

        // fill cluster
        if (sortfields != null) for (int i = 0; i < sortfields.length; i++) sortClusterMap.put(sortfields[i], cluster[i]);

        // fill acc map
        if (longaccfields != null) for (int i = 0; i < longaccfields.length; i++) accMap.put(longaccfields[i], longaccumulator[i]);
        if (doubleaccfields != null) for (int i = 0; i < doubleaccfields.length; i++) accMap.put(doubleaccfields[i], doubleaccumulator[i]);
    }
    
    public synchronized void set(String key, Map newMap) throws IOException {
        assert (key != null);
        assert (key.length() > 0);
        assert (newMap != null);

        // update elementCount
        if ((longaccfields != null) || (doubleaccfields != null)) {
            final Map oldMap = getMap(key, false);
            if (oldMap == null) {
                // new element
                elementCount++;
            } else {
                // element exists, update acc
                if ((longaccfields != null) || (doubleaccfields != null)) updateAcc(oldMap, false);
            }
        }
        
        super.set(key, new kelondroObjectsMapEntry(newMap));
        
        // update sortCluster
        if (sortClusterMap != null) updateSortCluster(key, newMap);

        // update accumulators with new values (add)
        if ((longaccfields != null) || (doubleaccfields != null)) updateAcc(newMap, true);
    }
    
    private void updateAcc(Map map, boolean add) {
        String value;
        long valuel;
        double valued;
        Long longaccumulator;
        Double doubleaccumulator;
        if (longaccfields != null) for (int i = 0; i < longaccfields.length; i++) {
            value = (String) map.get(longaccfields[i]);
            if (value != null) {
                try {
                    valuel = Long.parseLong(value);
                    longaccumulator = (Long) accMap.get(longaccfields[i]);
                    if (add) {
                        accMap.put(longaccfields[i], new Long(longaccumulator.longValue() + valuel));
                    } else {
                        accMap.put(longaccfields[i], new Long(longaccumulator.longValue() - valuel));
                    }
                } catch (NumberFormatException e) {}
            }
        }
        if (doubleaccfields != null) for (int i = 0; i < doubleaccfields.length; i++) {
            value = (String) map.get(doubleaccfields[i]);
            if (value != null) {
                try {
                    valued = Double.parseDouble(value);
                    doubleaccumulator = (Double) accMap.get(doubleaccfields[i]);
                    if (add) {
                        accMap.put(doubleaccfields[i], new Double(doubleaccumulator.doubleValue() + valued));
                    } else {
                        accMap.put(doubleaccfields[i], new Double(doubleaccumulator.doubleValue() - valued));
                    }
                } catch (NumberFormatException e) {}
            }
        }
    }

    private void updateSortCluster(final String key, final Map map) {
        Object cell;
        kelondroMScoreCluster cluster;
        for (int i = 0; i < sortfields.length; i++) {
            cell = map.get(sortfields[i]);
            if (cell != null) {
                cluster = (kelondroMScoreCluster) sortClusterMap.get(sortfields[i]);
                cluster.setScore(key, kelondroMScoreCluster.object2score(cell));
                sortClusterMap.put(sortfields[i], cluster);
            }
        }
    }

    public synchronized void remove(String key) throws IOException {
        if (key == null) return;
        
        // update elementCount
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) {
            final Map map = getMap(key);
            if (map != null) {
                // update count
                elementCount--;

                // update accumulators (subtract)
                if ((longaccfields != null) || (doubleaccfields != null)) updateAcc(map, false);

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
    
    public synchronized long getLongAcc(final String field) {
        final Long accumulator = (Long) accMap.get(field);
        if (accumulator == null) return -1;
        return accumulator.longValue();
    }
    
    public synchronized double getDoubleAcc(final String field) {
        final Double accumulator = (Double) accMap.get(field);
        if (accumulator == null) return -1;
        return accumulator.doubleValue();
    }
    
    public synchronized int size() {
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) return elementCount;
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
