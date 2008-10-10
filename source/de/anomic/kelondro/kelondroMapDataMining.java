// kelondroMapObjects.java
// -----------------------
// (C) 29.01.2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

public class kelondroMapDataMining extends kelondroMap {

    private final String[] sortfields, longaccfields, doubleaccfields;
    private HashMap<String, kelondroMScoreCluster<String>> sortClusterMap; // a String-kelondroMScoreCluster - relation
    private HashMap<String, Object> accMap; // to store accumulations of specific fields
    
	@SuppressWarnings("unchecked")
	public kelondroMapDataMining(final kelondroBLOB dyn, final int cachesize, final String[] sortfields, final String[] longaccfields, final String[] doubleaccfields, final Method externalInitializer, final Object externalHandler) {
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
                    longaccumulator[i] = Long.valueOf(0);   
                }
            }
            if (doubleaccfields != null) {
                doubleaccumulator = new Double[doubleaccfields.length];
                for (int i = 0; i < doubleaccfields.length; i++) {
                    doubleaccumulator[i] = Double.valueOf(0);   
                }
            }
        }

        // fill cluster and accumulator with values
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) try {
            final kelondroCloneableIterator<byte[]> it = dyn.keys(true, false);
            String mapname;
            Object cell;
            long valuel;
            double valued;
            Map<String, String> map;
            while (it.hasNext()) {
                mapname = new String(it.next());
                map = super.get(mapname);
                if (map == null) break;
                
                if (sortfields != null && cluster != null) for (int i = 0; i < sortfields.length; i++) {
                    cell = map.get(sortfields[i]);
                    if (cell != null) cluster[i].setScore(mapname, kelondroMScoreCluster.object2score(cell));
                }

                if (longaccfields != null && longaccumulator != null) for (int i = 0; i < longaccfields.length; i++) {
                    cell = map.get(longaccfields[i]);
                    valuel = 0;
                    if (cell != null) try {
                        if (cell instanceof Long)   valuel = ((Long) cell).longValue();
                        if (cell instanceof String) valuel = Long.parseLong((String) cell);
                        longaccumulator[i] = Long.valueOf(longaccumulator[i].longValue() + valuel);
                    } catch (final NumberFormatException e) {}
                }
                
                if (doubleaccfields != null && doubleaccumulator != null) for (int i = 0; i < doubleaccfields.length; i++) {
                    cell = map.get(doubleaccfields[i]);
                    valued = 0d;
                    if (cell != null) try {
                        if (cell instanceof Double) valued = ((Double) cell).doubleValue();
                        if (cell instanceof String) valued = Double.parseDouble((String) cell);
                        doubleaccumulator[i] = new Double(doubleaccumulator[i].doubleValue() + valued);
                    } catch (final NumberFormatException e) {}
                }
                
                if ((externalHandler != null) && (externalInitializer != null)) {
                    try {
                        externalInitializer.invoke(externalHandler, new Object[]{mapname, map});
                    } catch (final IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (final IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (final InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (final IOException e) {}

        // fill cluster
        if (sortfields != null && cluster != null) for (int i = 0; i < sortfields.length; i++) sortClusterMap.put(sortfields[i], cluster[i]);

        // fill acc map
        if (longaccfields != null && longaccumulator != null) for (int i = 0; i < longaccfields.length; i++) accMap.put(longaccfields[i], longaccumulator[i]);
        if (doubleaccfields != null && doubleaccumulator != null) for (int i = 0; i < doubleaccfields.length; i++) accMap.put(doubleaccfields[i], doubleaccumulator[i]);
    }

    public synchronized void clear() throws IOException {
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
            		accMap.put(longaccfields[i], Long.valueOf(0));
            	}
            }
        	if (doubleaccfields != null) {
                for (int i = 0; i < doubleaccfields.length; i++) {
            		accMap.put(doubleaccfields[i], new Double(0));
            	}
            }
        }
    }
    
    public synchronized void put(final String key, final HashMap<String, String> newMap) throws IOException {
        assert (key != null);
        assert (key.length() > 0);
        assert (newMap != null);

        // update elementCount
        if ((longaccfields != null) || (doubleaccfields != null)) {
            final Map<String, String> oldMap = super.get(key, false);
            if (oldMap != null) {
                // element exists, update acc
                if ((longaccfields != null) || (doubleaccfields != null)) updateAcc(oldMap, false);
            }
        }
        
        super.put(key, newMap);
        
        // update sortCluster
        if (sortClusterMap != null) updateSortCluster(key, newMap);

        // update accumulators with new values (add)
        if ((longaccfields != null) || (doubleaccfields != null)) updateAcc(newMap, true);
    }
    
    private void updateAcc(final Map<String, String> map, final boolean add) {
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
                        accMap.put(longaccfields[i], Long.valueOf(longaccumulator.longValue() + valuel));
                    } else {
                        accMap.put(longaccfields[i], Long.valueOf(longaccumulator.longValue() - valuel));
                    }
                } catch (final NumberFormatException e) {}
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
                } catch (final NumberFormatException e) {}
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

    public synchronized void remove(final String key) throws IOException {
        if (key == null) return;
        
        // update elementCount
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) {
            final Map<String, String> map = super.get(key);
            if (map != null) {

                // update accumulators (subtract)
                if ((longaccfields != null) || (doubleaccfields != null)) updateAcc(map, false);

                // remove from sortCluster
                if (sortfields != null) deleteSortCluster(key);
            }
        }
        super.remove(key);
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
    
    public synchronized Iterator<byte[]> keys(final boolean up, /* sorted by */ final String field) {
        // sorted iteration using the sortClusters
        if (sortClusterMap == null) return null;
        final kelondroMScoreCluster<String> cluster = sortClusterMap.get(field);
        if (cluster == null) return null; // sort field does not exist
        //System.out.println("DEBUG: cluster for field " + field + ": " + cluster.toString());
        return new string2bytearrayIterator(cluster.scores(up));
    }
    
    public static class string2bytearrayIterator implements Iterator<byte[]> {

        Iterator<String> s;
        
        public string2bytearrayIterator(final Iterator<String> s) {
            this.s = s;
        }
        
        public boolean hasNext() {
            return s.hasNext();
        }

        public byte[] next() {
            final String r = s.next();
            if (r == null) return null;
            return r.getBytes();
        }

        public void remove() {
            s.remove();
        }
        
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
    
    public class mapIterator implements Iterator<Map<String, String>> {
        // enumerates Map-Type elements
        // the key is also included in every map that is returned; it's key is 'key'

        Iterator<byte[]> keyIterator;
        boolean finish;
        Map<String, String> n;

        public mapIterator(final Iterator<byte[]> keyIterator) {
            this.keyIterator = keyIterator;
            this.finish = false;
            this.n = next0();
        }

        public boolean hasNext() {
            return this.n != null;
        }

        public Map<String, String> next() {
            final Map<String, String> n1 = n;
            n = next0();
            return n1;
        }
        
        private Map<String, String> next0() {
            if (finish) return null;
            if (keyIterator == null) return null;
            String nextKey;
            Map<String, String> map;
            while (keyIterator.hasNext()) {
                nextKey = new String(keyIterator.next());
                if (nextKey == null) {
                    finish = true;
                    return null;
                }
                try {
                    map = get(nextKey);
                } catch (final IOException e) {
                    break;
                }
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
