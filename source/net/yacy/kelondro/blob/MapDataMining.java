// kelondroMapDataMining.java
// -----------------------
// (C) 29.01.2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 as part of kelondroMap on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.storage.ScoreCluster;
import net.yacy.cora.storage.StaticScore;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.util.LookAheadIterator;


public class MapDataMining extends MapHeap {
    
    private final static Long LONG0 = Long.valueOf(0);
    private final static Double DOUBLE0 = Double.valueOf(0.0);

    private final String[] sortfields, longaccfields, doubleaccfields;
    private Map<String, StaticScore<String>> sortClusterMap; // a String-kelondroMScoreCluster - relation
    private Map<String, Long>   accLong; // to store accumulations of Long cells
    private Map<String, Double> accDouble; // to store accumulations of Double cells
    
	@SuppressWarnings("unchecked")
	public MapDataMining(final File heapFile,
            final int keylength,
            final ByteOrder ordering,
            int buffermax,
            final int cachesize,
            final String[] sortfields,
            final String[] longaccfields,
            final String[] doubleaccfields,
            final Object externalHandler) throws IOException {
        super(heapFile, keylength, ordering, buffermax, cachesize, '_');
        
        // create fast ordering clusters and acc fields
        this.sortfields = sortfields;
        this.longaccfields = longaccfields;
        this.doubleaccfields = doubleaccfields;

        ScoreCluster<String>[] cluster = null;
        if (sortfields == null) sortClusterMap = null; else {
            sortClusterMap = new ConcurrentHashMap<String, StaticScore<String>>();
            cluster = new ScoreCluster[sortfields.length];
            for (int i = 0; i < sortfields.length; i++) {
                cluster[i] = new ScoreCluster<String>();   
            }
        }

        Long[] longaccumulator = null;
        Double[] doubleaccumulator = null;
        if (longaccfields == null) {
        	accLong = null;
        } else {
            accLong = new ConcurrentHashMap<String, Long>();
            longaccumulator = new Long[longaccfields.length];
            for (int i = 0; i < longaccfields.length; i++) {
                longaccumulator[i] = LONG0;   
            }
        }
        if (doubleaccfields == null) {
            accDouble = null;
        } else {
            accDouble = new ConcurrentHashMap<String, Double>();
            doubleaccumulator = new Double[doubleaccfields.length];
            for (int i = 0; i < doubleaccfields.length; i++) {
                doubleaccumulator[i] = DOUBLE0;   
            }
        }

        // fill cluster and accumulator with values
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) try {
            final CloneableIterator<byte[]> it = super.keys(true, false);
            byte[] mapnameb;
            String cell;
            long valuel;
            double valued;
            Map<String, String> map;
            while (it.hasNext()) {
                mapnameb = it.next();
                try {
                    map = super.get(mapnameb);
                } catch (RowSpaceExceededException e) {
                    Log.logWarning("MapDataMining", e.getMessage());
                    break;
                }
                if (map == null) break;
                
                if (sortfields != null && cluster != null) for (int i = 0; i < sortfields.length; i++) {
                    cell = map.get(sortfields[i]);
                    if (cell != null) cluster[i].setScore(new String(mapnameb), ScoreCluster.object2score(cell));
                }

                if (longaccfields != null && longaccumulator != null) for (int i = 0; i < longaccfields.length; i++) {
                    cell = map.get(longaccfields[i]);
                    valuel = 0;
                    if (cell != null) try {
                        valuel = Long.parseLong(cell);
                        longaccumulator[i] = Long.valueOf(longaccumulator[i].longValue() + valuel);
                    } catch (final NumberFormatException e) {}
                }
                
                if (doubleaccfields != null && doubleaccumulator != null) for (int i = 0; i < doubleaccfields.length; i++) {
                    cell = map.get(doubleaccfields[i]);
                    valued = 0d;
                    if (cell != null) try {
                        valued = Double.parseDouble(cell);
                        doubleaccumulator[i] = new Double(doubleaccumulator[i].doubleValue() + valued);
                    } catch (final NumberFormatException e) {}
                }
            }
        } catch (final IOException e) {}

        // fill cluster
        if (sortfields != null && cluster != null) for (int i = 0; i < sortfields.length; i++) sortClusterMap.put(sortfields[i], cluster[i]);

        // fill acc map
        if (longaccfields != null && longaccumulator != null) for (int i = 0; i < longaccfields.length; i++) accLong.put(longaccfields[i], longaccumulator[i]);
        if (doubleaccfields != null && doubleaccumulator != null) for (int i = 0; i < doubleaccfields.length; i++) accDouble.put(doubleaccfields[i], doubleaccumulator[i]);
    }

    @Override
    public synchronized void clear() {
    	super.clear();
        if (sortfields == null) sortClusterMap = null; else {
            sortClusterMap = new HashMap<String, StaticScore<String>>();
            for (int i = 0; i < sortfields.length; i++) {
            	sortClusterMap.put(sortfields[i], new ScoreCluster<String>());
            }
        }
        
        if (longaccfields == null) {
            accLong = null;
        } else {
            accLong = new HashMap<String, Long>();
            for (int i = 0; i < longaccfields.length; i++) {
                accLong.put(longaccfields[i], LONG0);
            }
        }
        if (doubleaccfields == null) {
            accDouble = null;
        } else {
            accDouble = new HashMap<String, Double>();
            for (int i = 0; i < doubleaccfields.length; i++) {
                accDouble.put(doubleaccfields[i], DOUBLE0);
            }
        }
    }
    
    @Override
    public synchronized void insert(final byte[] key, final Map<String, String> newMap) throws IOException, RowSpaceExceededException {
        assert (key != null);
        assert (key.length > 0);
        assert (newMap != null);

        // update elementCount
        if ((longaccfields != null) || (doubleaccfields != null)) {
            final Map<String, String> oldMap = super.get(key, false);
            if (oldMap != null) {
                // element exists, update acc
                updateAcc(oldMap, false);
            }
        
            // update accumulators with new values (add)
            updateAcc(newMap, true);
        }

        super.insert(key, newMap);
        
        // update sortCluster
        if (sortClusterMap != null) updateSortCluster(new String(key), newMap);
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
                    longaccumulator = accLong.get(longaccfields[i]);
                    if (add) {
                        accLong.put(longaccfields[i], Long.valueOf(longaccumulator.longValue() + valuel));
                    } else {
                        accLong.put(longaccfields[i], Long.valueOf(longaccumulator.longValue() - valuel));
                    }
                } catch (final NumberFormatException e) {}
            }
        }
        if (doubleaccfields != null) for (int i = 0; i < doubleaccfields.length; i++) {
            value = map.get(doubleaccfields[i]);
            if (value != null) {
                try {
                    valued = Double.parseDouble(value);
                    doubleaccumulator = accDouble.get(doubleaccfields[i]);
                    if (add) {
                        accDouble.put(doubleaccfields[i], Double.valueOf(doubleaccumulator.doubleValue() + valued));
                    } else {
                        accDouble.put(doubleaccfields[i], Double.valueOf(doubleaccumulator.doubleValue() - valued));
                    }
                } catch (final NumberFormatException e) {}
            }
        }
    }

    private void updateSortCluster(final String key, final Map<String, String> map) {
        Object cell;
        StaticScore<String> cluster;
        for (int i = 0; i < sortfields.length; i++) {
            cell = map.get(sortfields[i]);
            if (cell != null) {
                cluster = sortClusterMap.get(sortfields[i]);
                cluster.setScore(key, ScoreCluster.object2score(cell));
                sortClusterMap.put(sortfields[i], cluster);
            }
        }
    }

    @Override
    public synchronized void delete(final byte[] key) throws IOException {
        if (key == null) return;
        
        // update elementCount
        if ((sortfields != null) || (longaccfields != null) || (doubleaccfields != null)) {
            Map<String, String> map;
            try {
                map = super.get(key);
                if (map != null) {

                    // update accumulators (subtract)
                    if ((longaccfields != null) || (doubleaccfields != null)) updateAcc(map, false);

                    // remove from sortCluster
                    if (sortfields != null) deleteSortCluster(new String(key));
                }
            } catch (RowSpaceExceededException e) {
                map = null;
                Log.logException(e);
            }
        }
        super.delete(key);
    }
    
    private void deleteSortCluster(final String key) {
        if (key == null) return;
        StaticScore<String> cluster;
        for (int i = 0; i < sortfields.length; i++) {
            cluster = sortClusterMap.get(sortfields[i]);
            cluster.deleteScore(key);
            sortClusterMap.put(sortfields[i], cluster);
        }
    }
    
    public synchronized Iterator<byte[]> keys(final boolean up, /* sorted by */ final String field) {
        // sorted iteration using the sortClusters
        if (sortClusterMap == null) return null;
        final StaticScore<String> cluster = sortClusterMap.get(field);
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
        final Long accumulator = accLong.get(field);
        if (accumulator == null) return -1;
        return accumulator.longValue();
    }
    
    public synchronized double getDoubleAcc(final String field) {
        final Double accumulator = accDouble.get(field);
        if (accumulator == null) return -1;
        return accumulator.doubleValue();
    }
    
    @Override
    public synchronized int size() {
        return super.size();
    }
    
    @Override
    public synchronized boolean isEmpty() {
        return super.isEmpty();
    }
    
    @Override
    public synchronized void close() {
        // close cluster
        if (sortClusterMap != null) {
            for (int i = 0; i < sortfields.length; i++) sortClusterMap.remove(sortfields[i]);
            sortClusterMap = null;
        }
        
        super.close();
    }
    
    public class mapIterator extends LookAheadIterator<Map<String, String>> implements Iterator<Map<String, String>> {
        // enumerates Map-Type elements
        // the key is also included in every map that is returned; it's key is 'key'

        Iterator<byte[]> keyIterator;

        public mapIterator(final Iterator<byte[]> keyIterator) {
            this.keyIterator = keyIterator;
        }
        
        public Map<String, String> next0() {
            if (keyIterator == null) return null;
            byte[] nextKey;
            Map<String, String> map;
            while (keyIterator.hasNext()) {
                nextKey = keyIterator.next();
                try {
                    map = get(nextKey);
                } catch (final IOException e) {
                    Log.logWarning("MapDataMining", e.getMessage());
                    continue;
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                    continue;
                }
                if (map == null) continue; // circumvention of a modified exception
                map.put("key", new String(nextKey));
                return map;
            }
            return null;
        }
    } // class mapIterator
}
