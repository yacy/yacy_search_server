/**
 *  ScoreMap
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.10.2010 at http://yacy.net
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


public class ScoreMap<E> implements DynamicScore<E> {

    protected final Map<E, IntScore> map; // a mapping from a reference to the cluster key
    private long gcount;
    
    public ScoreMap()  {
        this(null);
    }
    
    public ScoreMap(Comparator<? super E> comparator)  {
        if (comparator == null) {
            map = new HashMap<E, IntScore>();
        } else {
            map = new TreeMap<E, IntScore>(comparator);
        }
        gcount = 0;
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
        synchronized (this) {
            Iterator<Map.Entry<E, IntScore>> i = this.map.entrySet().iterator();
            Map.Entry<E, IntScore> entry;
            while (i.hasNext()) {
                entry = i.next();
                if (entry.getValue().intValue() < minScore) i.remove();
            }
        }
    }
    
    public synchronized long totalCount() {
        return gcount;
    }
    
    public synchronized int size() {
        return map.size();
    }
    
    public synchronized boolean isEmpty() {
        return map.isEmpty();
    }
    
    public void inc(final E obj) {
        if (obj == null) return;
        synchronized (this) {
            IntScore score = this.map.get(obj);
            if (score == null) {
                this.map.put(obj, IntScore.ONE);
            } else {
                score.inc();
            }
        }        
        // increase overall counter
        gcount++;
    }
    
    public void dec(final E obj) {
        if (obj == null) return;
        synchronized (this) {
            IntScore score = this.map.get(obj);
            if (score == null) {
                this.map.put(obj, IntScore.valueOf(-1));
            } else {
                score.dec();
            }
        }        
        // increase overall counter
        gcount--;
    }
    
    public void set(final E obj, final int newScore) {
        if (obj == null) return;
        synchronized (this) {
            IntScore score = this.map.get(obj);
            if (score == null) {
                this.map.put(obj, IntScore.ONE);
            } else {
                gcount -= score.intValue();
                score.set(newScore);
            }
        }        
        // increase overall counter
        gcount += newScore;
    }
    
    public void inc(final E obj, final int incrementScore) {
        if (obj == null) return;
        synchronized (this) {
            IntScore score = this.map.get(obj);
            if (score == null) {
                this.map.put(obj, IntScore.valueOf(incrementScore));
            } else {
                score.inc(incrementScore);
            }
        }        
        // increase overall counter
        gcount += incrementScore;
    }
    
    public void dec(final E obj, final int incrementScore) {
        inc(obj, -incrementScore);
    }
    
    public int delete(final E obj) {
        // deletes entry and returns previous score
        if (obj == null) return 0;
        final IntScore score;
        synchronized (this) {
            score = map.remove(obj);
            if (score == null) return 0;
        }

        // decrease overall counter
        gcount -= score.intValue();
        return score.intValue();
    }

    public synchronized boolean containsKey(final E obj) {
        return map.containsKey(obj);
    }
    
    public int get(final E obj) {
        if (obj == null) return 0;
        final IntScore score;
        synchronized (this) {
            score = map.get(obj);
        }
        if (score == null) return 0;
        return score.intValue();
    }
    
    public int getMaxScore() {
        if (map.isEmpty()) return -1;
        int maxScore = Integer.MIN_VALUE;
        synchronized (this) {
            for (Map.Entry<E, IntScore> entry: this.map.entrySet()) if (entry.getValue().intValue() > maxScore) {
                maxScore = entry.getValue().intValue();
            }
        }
        return maxScore;
    }

    public int getMinScore() {
        if (map.isEmpty()) return -1;
        int minScore = Integer.MAX_VALUE;
        synchronized (this) {
            for (Map.Entry<E, IntScore> entry: this.map.entrySet()) if (entry.getValue().intValue() < minScore) {
                minScore = entry.getValue().intValue();
            }
        }
        return minScore;
    }

    public E getMaxKey() {
        if (map.isEmpty()) return null;
        E maxObject = null;
        int maxScore = Integer.MIN_VALUE;
        synchronized (this) {
            for (Map.Entry<E, IntScore> entry: this.map.entrySet()) if (entry.getValue().intValue() > maxScore) {
                maxScore = entry.getValue().intValue();
                maxObject = entry.getKey();
            }
        }
        return maxObject;
    }
    
    public E getMinKey() {
        if (map.isEmpty()) return null;
        E minObject = null;
        int minScore = Integer.MAX_VALUE;
        synchronized (this) {
            for (Map.Entry<E, IntScore> entry: this.map.entrySet()) if (entry.getValue().intValue() < minScore) {
                minScore = entry.getValue().intValue();
                minObject = entry.getKey();
            }
        }
        return minObject;
    }
    
    public String toString() {
        return map.toString();
    }

    public Iterator<E> keys(boolean up) {
        synchronized (this) {
            // re-organize entries
            TreeMap<IntScore, Set<E>> m = new TreeMap<IntScore, Set<E>>();
            Set<E> s;
            for (Map.Entry<E, IntScore> entry: this.map.entrySet()) {
                s = m.get(entry.getValue());
                if (s == null) {
                    s = this.map instanceof TreeMap ? new TreeSet<E>(((TreeMap<E, IntScore>) this.map).comparator()) : new HashSet<E>();
                    s.add(entry.getKey());
                    m.put(entry.getValue(), s);
                } else {
                    s.add(entry.getKey());
                }
            }
            
            // flatten result
            List<E> l = new ArrayList<E>(this.map.size());
            for (Set<E> f: m.values()) {
                for (E e: f) l.add(e);
            }
            if (up) return l.iterator();
            
            // optionally reverse list
            List<E> r = new ArrayList<E>(l.size());
            for (int i = l.size() - 1; i >= 0; i--) r.add(r.get(i));
            return r.iterator();
        }
    }
    
}
