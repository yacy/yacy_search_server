/**
 *  ScoreMap
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.10.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.sorting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import net.yacy.cora.util.StringBuilderComparator;

public class OrderedScoreMap<E> extends AbstractScoreMap<E> implements ScoreMap<E> {

    protected final Map<E, AtomicInteger> map; // a mapping from a reference to the cluster key

    public OrderedScoreMap(final Comparator<? super E> comparator)  {
        if (comparator == null) {
            this.map = new HashMap<E, AtomicInteger>();
        } else {
            this.map = new TreeMap<E, AtomicInteger>(comparator);
        }
    }

    @Override
    public Iterator<E> iterator() {
        return this.map.keySet().iterator();
    }

    @Override
    public synchronized void clear() {
        this.map.clear();
    }

    /**
     * shrink the cluster to a demanded size
     * @param maxsize
     */
    @Override
    public void shrinkToMaxSize(final int maxsize) {
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
    @Override
    public void shrinkToMinScore(final int minScore) {
        synchronized (this.map) {
            final Iterator<Map.Entry<E, AtomicInteger>> i = this.map.entrySet().iterator();
            Map.Entry<E, AtomicInteger> entry;
            while (i.hasNext()) {
                entry = i.next();
                if (entry.getValue().intValue() < minScore) i.remove();
            }
        }
    }

    @Override
    public int size() {
        synchronized (this.map) {
            return this.map.size();
        }
    }

    /**
     * return true if the size of the score map is smaller then the given size
     * @param size
     * @return
     */
    @Override
    public boolean sizeSmaller(final int size) {
        if (this.map.size() < size) return true;
        synchronized (this.map) {
            return this.map.size() < size;
        }
    }

    @Override
    public boolean isEmpty() {
        if (this.map.isEmpty()) return true;
        synchronized (this.map) {
            return this.map.isEmpty();
        }
    }

    @Override
    public void inc(final E obj) {
        if (obj == null) return;
        AtomicInteger score = this.map.get(obj);
        if (score != null) {
            score.incrementAndGet();
            return;
        }
        synchronized (this.map) {
            score = this.map.get(obj);
            if (score == null) {
                this.map.put(obj, new AtomicInteger(1));
                return;
            }
        }
        score.incrementAndGet();
    }

    @Override
    public void dec(final E obj) {
        if (obj == null) return;
        AtomicInteger score;
        synchronized (this.map) {
            score = this.map.get(obj);
            if (score == null) {
                this.map.put(obj, new AtomicInteger(-1));
                return;
            }
        }
        score.decrementAndGet();
    }

    @Override
    public void set(final E obj, final int newScore) {
        if (obj == null) return;
        AtomicInteger score;
        synchronized (this.map) {
            score = this.map.get(obj);
            if (score == null) {
                this.map.put(obj, new AtomicInteger(newScore));
                return;
            }
        }
        score.getAndSet(newScore);
    }

    @Override
    public void inc(final E obj, final int incrementScore) {
        if (obj == null) return;
        AtomicInteger score;
        synchronized (this.map) {
            score = this.map.get(obj);
            if (score == null) {
                this.map.put(obj, new AtomicInteger(incrementScore));
                return;
            }
        }
        score.addAndGet(incrementScore);
    }

    @Override
    public void dec(final E obj, final int incrementScore) {
        inc(obj, -incrementScore);
    }

    @Override
    public int delete(final E obj) {
        // deletes entry and returns previous score
        if (obj == null) return 0;
        final AtomicInteger score;
        synchronized (this.map) {
            score = this.map.remove(obj);
            if (score == null) return 0;
        }
        return score.intValue();
    }

    @Override
    public boolean containsKey(final E obj) {
        synchronized (this.map) {
            return this.map.containsKey(obj);
        }
    }

    @Override
    public int get(final E obj) {
        if (obj == null) return 0;
        final AtomicInteger score;
        synchronized (this.map) {
            score = this.map.get(obj);
        }
        if (score == null) return 0;
        return score.intValue();
    }

    public SortedMap<E, AtomicInteger> tailMap(final E obj) {
        if (this.map instanceof TreeMap) {
            return ((TreeMap<E, AtomicInteger>) this.map).tailMap(obj);
        }
        throw new UnsupportedOperationException("map must have comparator");
    }

    private int getMinScore() {
        if (this.map.isEmpty()) return -1;
        int minScore = Integer.MAX_VALUE;
        synchronized (this.map) {
            for (final Map.Entry<E, AtomicInteger> entry: this.map.entrySet()) if (entry.getValue().intValue() < minScore) {
                minScore = entry.getValue().intValue();
            }
        }
        return minScore;
    }

    @Override
    public Iterator<E> keys(final boolean up) {
        synchronized (this.map) {
            // re-organize entries
            final TreeMap<Integer, Set<E>> m = new TreeMap<Integer, Set<E>>();
            Set<E> s;
            for (final Map.Entry<E, AtomicInteger> entry: this.map.entrySet()) {
                s = m.get(entry.getValue().intValue());
                if (s == null) {
                    s = this.map instanceof TreeMap ? new TreeSet<E>(((TreeMap<E, AtomicInteger>) this.map).comparator()) : new HashSet<E>();
                    s.add(entry.getKey());
                    m.put(entry.getValue().intValue(), s);
                } else {
                    s.add(entry.getKey());
                }
            }

            // flatten result
            final List<E> l = new ArrayList<E>(this.map.size());
            for (final Set<E> f: m.values()) {
                for (final E e: f) l.add(e);
            }
            if (up) return l.iterator();

            // optionally reverse list
            final List<E> r = new ArrayList<E>(l.size());
            for (int i = l.size() - 1; i >= 0; i--) r.add(l.get(i));
            return r.iterator();
        }
    }
    
    public static void main(String[] args) {
    	OrderedScoreMap<StringBuilder> w = new OrderedScoreMap<StringBuilder>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);
    	Random r = new Random();
    	for (int i = 0; i < 10000; i++) {
    		w.inc(new StringBuilder("a" + ((char) (('a') + r.nextInt(26)))));
    	}
    	for (StringBuilder s: w) System.out.println(s + ":" + w.get(s));
    	System.out.println("--");
    	w.shrinkToMaxSize(10);
    	for (StringBuilder s: w) System.out.println(s + ":" + w.get(s));
    }
    
}
