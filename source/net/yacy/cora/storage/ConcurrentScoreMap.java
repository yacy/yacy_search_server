/**
 *  ConcurrentScoreMap
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 13.03.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-03-08 02:51:51 +0100 (Di, 08 Mrz 2011) $
 *  $LastChangedRevision: 7567 $
 *  $LastChangedBy: low012 $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class ConcurrentScoreMap<E> extends AbstractScoreMap<E> implements ScoreMap<E> {

    protected final ConcurrentHashMap<E, AtomicLong> map; // a mapping from a reference to the cluster key
    private long gcount;
    
    public ConcurrentScoreMap()  {
        map = new ConcurrentHashMap<E, AtomicLong>();
        gcount = 0;
    }

    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }
    
    public synchronized void clear() {
        map.clear();
        gcount = 0;
    }
    
    /**
     * shrink the cluster to a demanded size
     * @param maxsize
     */
    public void shrinkToMaxSize(int maxsize) {
        if (this.map.size() <= maxsize) return;
        int minScore = getMinScore();
        while (this.map.size() > maxsize) {
            minScore++;
            shrinkToMinScore(minScore);
        }
    }
    
    /**
     * shrink the cluster in such a way that the smallest score is equal or greater than a given minScore
     * @param minScore
     */
    public void shrinkToMinScore(int minScore) {
        Iterator<Map.Entry<E, AtomicLong>> i = this.map.entrySet().iterator();
        Map.Entry<E, AtomicLong> entry;
        while (i.hasNext()) {
            entry = i.next();
            if (entry.getValue().intValue() < minScore) i.remove();
        }
    }
    
    public long totalCount() {
        return gcount;
    }
    
    public int size() {
        return map.size();
    }
    
    public boolean sizeSmaller(int size) {
        return map.size() < size;
    }
    
    public boolean isEmpty() {
        return map.isEmpty();
    }
    
    public void inc(final E obj) {
        if (obj == null) return;
        
        // use atomic operations
        this.map.putIfAbsent(obj, new AtomicLong(0));
        this.map.get(obj).incrementAndGet();
        
        // increase overall counter
        gcount++;
    }
    
    public void dec(final E obj) {
        if (obj == null) return;
        
        // use atomic operations
        this.map.putIfAbsent(obj, new AtomicLong(0));
        this.map.get(obj).decrementAndGet();
        
        // increase overall counter
        gcount--;
    }
    
    public void set(final E obj, final int newScore) {
        if (obj == null) return;
        
        // use atomic operations
        this.map.putIfAbsent(obj, new AtomicLong(0));
        this.map.get(obj).set(newScore);
        
        // increase overall counter
        gcount += newScore;
    }
    
    public void inc(final E obj, final int incrementScore) {
        if (obj == null) return;
        
        // use atomic operations
        this.map.putIfAbsent(obj, new AtomicLong(0));
        this.map.get(obj).addAndGet(incrementScore);
        
        // increase overall counter
        gcount += incrementScore;
    }
    
    public void dec(final E obj, final int decrementScore) {
        inc(obj, -decrementScore);
    }
    
    public int delete(final E obj) {
        // deletes entry and returns previous score
        if (obj == null) return 0;
        final AtomicLong score = this.map.remove(obj);
        if (score == null) return 0;

        // decrease overall counter
        gcount -= score.intValue();
        return score.intValue();
    }

    public boolean containsKey(final E obj) {
        return this.map.containsKey(obj);
    }
    
    public int get(final E obj) {
        if (obj == null) return 0;
        final AtomicLong score = this.map.get(obj);
        if (score == null) return 0;
        return score.intValue();
    }
    
    private int getMinScore() {
        if (this.map.isEmpty()) return -1;
        int minScore = Integer.MAX_VALUE;
        for (Map.Entry<E, AtomicLong> entry: this.map.entrySet()) if (entry.getValue().intValue() < minScore) {
            minScore = entry.getValue().intValue();
        }
        return minScore;
    }
    
    @Override
    public String toString() {
        return map.toString();
    }

    public Iterator<E> keys(boolean up) {
        // re-organize entries
        TreeMap<Integer, Set<E>> m = new TreeMap<Integer, Set<E>>();
        Set<E> s;
        Integer is;
        for (Map.Entry<E, AtomicLong> entry: this.map.entrySet()) {
            is = new Integer(entry.getValue().intValue());
            s = m.get(is);
            if (s == null) {
                s = new HashSet<E>();
                s.add(entry.getKey());
                m.put(is, s);
            } else {
                s.add(entry.getKey());
            }
        }
        
        // flatten result
        List<E> l = new ArrayList<E>(m.size());
        for (Set<E> f: m.values()) {
            for (E e: f) l.add(e);
        }
        if (up) return l.iterator();
        
        // optionally reverse list
        List<E> r = new ArrayList<E>(l.size());
        for (int i = l.size() - 1; i >= 0; i--) r.add(l.get(i));
        return r.iterator();
    }
    
}
