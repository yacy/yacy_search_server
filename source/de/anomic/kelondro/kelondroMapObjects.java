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
    private HashMap<String, kelondroMScoreCluster<String>> sortClusterMap; // a String-kelondroMScoreCluster - relation
    private HashMap<String, Object> accMap; // to store accumulations of specific fields
    private int elementCount;
    
    public kelondroMapObjects(kelondroBLOBTree dyn, int cachesize) {
        this(dyn, cachesize, null, null, null, null, null);
    }
    
	@SuppressWarnings({ "unchecked", "null" })
	public kelondroMapObjects(kelondroBLOBTree dyn, int cachesize, String[] sortfields, String[] longaccfields, String[] doubleaccfields, Method externalInitializer, Object externalHandler) {
        super(dyn, cachesize);
        
        // create fast ordering clusters and acc fields
        this.sortfields = sortfields;
        this.longaccfields = longaccfields;
        this.doubleaccfields = doubleaccfields;

        kelondroMScoreCluster<String>[] cluster = null;
        if (sortfields == null) sortClusterMap = null; else {
            sortClusterMap = new HashMap<String, kelondroMScoreCluster<String>>();
            cluster = new kelondroMScoreCluster[sortfields.length];
            for (int i = 0; i < sortfields.length; i++) {
                cluster[i] = new kelondroMScoreCluster<String>();   
            }
        }

        Long[] longaccumulator = null;
        Double[] doubleaccumulator = null;
        if ((longaccfields == null) && (doubleaccfields == null)) {
        	accMap = null;
        } else {
            accMap = new HashMap<String, Object>();
            if (longaccfields != null) {
                longaccumulator = new Long[longaccfields.length];
                for (int i = 0; i < longaccfields.length; i++) {
                    longaccumulator[i] = new Long(0);   
                }
            }
            if (doubleaccfields != null) {
                doubleaccumulator = new Double[doubleaccfields.length];
                for (int i = 0; i < doubleaccfields.length; i++) {
                    doubleaccumulator[i] = new Double(0);   
                }
            }
        }

        // fill cluster and accumulator with values
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) try {
            kelondroCloneableIterator<String> it = dyn.keys(true, false);
            String mapname;
            Object cell;
            long valuel;
            double valued;
            Map<String, String> map;
            this.elementCount = 0;
            while (it.hasNext()) {
                mapname = it.next();
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

    public void clear() throws IOException {
    	super.clear();
        if (sortfields == null) sortClusterMap = null; else {
            sortClusterMap = new HashMap<String, kelondroMScoreCluster<String>>();
            for (int i = 0; i < sortfields.length; i++) {
            	sortClusterMap.put(sortfields[i], new kelondroMScoreCluster<String>());
            }
        }
        
        if ((longaccfields == null) && (doubleaccfields == null)) {
        	accMap = null;
        } else {
        	accMap = new HashMap<String, Object>();
        	if (longaccfields != null) {
                for (int i = 0; i < longaccfields.length; i++) {
            		accMap.put(longaccfields[i], new Long(0));
            	}
            }
        	if (doubleaccfields != null) {
                for (int i = 0; i < doubleaccfields.length; i++) {
            		accMap.put(doubleaccfields[i], new Double(0));
            	}
            }
        }
        this.elementCount = 0;
    }
    
    public synchronized void set(String key, HashMap<String, String> newMap) throws IOException {
        assert (key != null);
        assert (key.length() > 0);
        assert (newMap != null);

        // update elementCount
        if ((longaccfields != null) || (doubleaccfields != null)) {
            final Map<String, String> oldMap = getMap(key, false);
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
    
    private void updateAcc(Map<String, String> map, boolean add) {
        String value;
        long valuel;
        double valued;
        Long longaccumulator;
        Double doubleaccumulator;
        if (longaccfields != null) for (int i = 0; i < longaccfields.length; i++) {
            value = map.get(longaccfields[i]);
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
            value = map.get(doubleaccfields[i]);
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

    private void updateSortCluster(final String key, final Map<String, String> map) {
        Object cell;
        kelondroMScoreCluster<String> cluster;
        for (int i = 0; i < sortfields.length; i++) {
            cell = map.get(sortfields[i]);
            if (cell != null) {
                cluster = sortClusterMap.get(sortfields[i]);
                cluster.setScore(key, kelondroMScoreCluster.object2score(cell));
                sortClusterMap.put(sortfields[i], cluster);
            }
        }
    }

    public synchronized void remove(String key) throws IOException {
        if (key == null) return;
        
        // update elementCount
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) {
            final Map<String, String> map = getMap(key);
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
    
    public HashMap<String, String> getMap(String key) {
        try {
            kelondroObjectsMapEntry mapEntry = (kelondroObjectsMapEntry) super.get(key);
            if (mapEntry == null) return null;
            return mapEntry.map();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    protected Map<String, String> getMap(String key, boolean cache) {
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
        kelondroMScoreCluster<String> cluster;
        for (int i = 0; i < sortfields.length; i++) {
            cluster = sortClusterMap.get(sortfields[i]);
            cluster.deleteScore(key);
            sortClusterMap.put(sortfields[i], cluster);
        }
    }
    
    public synchronized Iterator<String> keys(final boolean up, /* sorted by */ String field) {
        // sorted iteration using the sortClusters
        if (sortClusterMap == null) return null;
        final kelondroMScoreCluster<String> cluster = sortClusterMap.get(field);
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

    public synchronized mapIterator maps(final boolean up, final boolean rotating, final byte[] firstKey, final byte[] secondKey) throws IOException {
        return new mapIterator(keys(up, rotating, firstKey, secondKey));
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
    
    public void close() {
        // close cluster
        if (sortClusterMap != null) {
            for (int i = 0; i < sortfields.length; i++) sortClusterMap.remove(sortfields[i]);
            sortClusterMap = null;
        }
        
        super.close();
    }
    
    public class mapIterator implements Iterator<HashMap<String, String>> {
        // enumerates Map-Type elements
        // the key is also included in every map that is returned; it's key is 'key'

        Iterator<String> keyIterator;
        boolean finish;
        HashMap<String, String> n;

        public mapIterator(Iterator<String> keyIterator) {
            this.keyIterator = keyIterator;
            this.finish = false;
            this.n = next0();
        }

        public boolean hasNext() {
            return this.n != null;
        }

        public HashMap<String, String> next() {
            HashMap<String, String> n1 = n;
            n = next0();
            return n1;
        }
        
        private HashMap<String, String> next0() {
            if (finish) return null;
            if (keyIterator == null) return null;
            String nextKey;
            HashMap<String, String> map;
            while (keyIterator.hasNext()) {
                nextKey = keyIterator.next();
                if (nextKey == null) {
                    finish = true;
                    return null;
                }
                map = getMap(nextKey);
                if (map == null) continue; // circumvention of a modified exception
                map.put("key", nextKey);
                return map;
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    } // class mapIterator
}
