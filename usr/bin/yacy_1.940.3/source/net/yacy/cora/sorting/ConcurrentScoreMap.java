/**
 *  ConcurrentScoreMap
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 13.03.2011 at https://yacy.net
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

package net.yacy.cora.sorting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;



public class ConcurrentScoreMap<E> extends AbstractScoreMap<E> implements ScoreMap<E> {

	/** a mapping from a reference to the cluster key */
    protected final ConcurrentHashMap<E, AtomicInteger> map;
    
    /** sum of all scores */
    private long gcount;
    
	/** Eventual registered object listening on map updates */
	private ScoreMapUpdatesListener updatesListener;

    public ConcurrentScoreMap()  {
        this(null);
    }
    
    /**
     * @param updatesListener an eventual object listening on score map updates
     */
    public ConcurrentScoreMap(final ScoreMapUpdatesListener updatesListener)  {
        this.map = new ConcurrentHashMap<E, AtomicInteger>();
        this.gcount = 0;
        this.updatesListener = updatesListener;
    }
    
    /**
     * Dispatch the update event to the eventually registered listener.
     */
    private void dispatchUpdateToListener() {
        if(this.updatesListener != null) {
        	this.updatesListener.updatedScoreMap();
        }
    }

    @Override
    public Iterator<E> iterator() {
        return this.map.keySet().iterator();
    }

    @Override
    public synchronized void clear() {
        this.map.clear();
        this.gcount = 0;
        dispatchUpdateToListener();
    }

    @Override
    public int shrinkToMaxSize(final int maxsize) {
        if (this.map.size() <= maxsize) {
        	return 0;
        }
        int deletedNb = 0;
        int minScore = getMinScore();
        while (this.map.size() > maxsize) {
            minScore++;
            deletedNb += shrinkToMinScore(minScore);
        }
        // No need to dispatch to listener, it is already done in shrinkToMinScore()
        return deletedNb;
    }

    @Override
    public int shrinkToMinScore(final int minScore) {
        final Iterator<Map.Entry<E, AtomicInteger>> i = this.map.entrySet().iterator();
        Map.Entry<E, AtomicInteger> entry;
        int deletedNb = 0;
        while (i.hasNext()) {
            entry = i.next();
            if (entry.getValue().intValue() < minScore) {
            	i.remove();
            	deletedNb++;
            }
        }
        if(deletedNb > 0) {
        	dispatchUpdateToListener();
        }
        return deletedNb;
    }

    public long totalCount() {
        return this.gcount;
    }

    @Override
    public int size() {
        return this.map.size();
    }

    @Override
    public boolean sizeSmaller(final int size) {
        return this.map.size() < size;
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    @Override
    public void inc(final E obj) {
        if (obj == null) return;

        // use atomic operations
        this.map.putIfAbsent(obj, new AtomicInteger(0));
        this.map.get(obj).incrementAndGet();

        // increase overall counter
        this.gcount++;
        
        dispatchUpdateToListener();
    }

    @Override
    public void dec(final E obj) {
        if (obj == null) return;

        // use atomic operations
        this.map.putIfAbsent(obj, new AtomicInteger(0));
        this.map.get(obj).decrementAndGet();

        // increase overall counter
        this.gcount--;
        
        dispatchUpdateToListener();
    }

    @Override
    public void set(final E obj, final int newScore) {
        if (obj == null) return;

        // use atomic operations
        final AtomicInteger old = this.map.putIfAbsent(obj, new AtomicInteger(newScore));
        // adjust overall counter if value replaced
        if (old != null) {
            this.gcount -= old.intValue(); // must use old before setting a new value (it's a object reference)
            this.map.get(obj).set(newScore);
        }
        // increase overall counter
        this.gcount += newScore;
        
        dispatchUpdateToListener();
    }

    @Override
    public void inc(final E obj, final int incrementScore) {
        if (obj == null) return;

        // use atomic operations
        this.map.putIfAbsent(obj, new AtomicInteger(0));
        this.map.get(obj).addAndGet(incrementScore);

        // increase overall counter
        this.gcount += incrementScore;
        
        dispatchUpdateToListener();
    }

    @Override
    public void dec(final E obj, final int decrementScore) {
        inc(obj, -decrementScore);
    }

    @Override
    public int delete(final E obj) {
        // deletes entry and returns previous score
        if (obj == null) return 0;
        final AtomicInteger score = this.map.remove(obj);
        if (score == null) return 0;

        // decrease overall counter
        this.gcount -= score.intValue();
        
        dispatchUpdateToListener();
        
        return score.intValue();
    }

    @Override
    public boolean containsKey(final E obj) {
        return this.map.containsKey(obj);
    }

    @Override
    public int get(final E obj) {
        if (obj == null) return 0;
        final AtomicInteger score = this.map.get(obj);
        if (score == null) return 0;
        return score.intValue();
    }

    public int getMinScore() {
        if (this.map.isEmpty()) return -1;
        int minScore = Integer.MAX_VALUE;
        for (final Map.Entry<E, AtomicInteger> entry : this.map.entrySet())
            if (entry.getValue().intValue() < minScore) {
                minScore = entry.getValue().intValue();
            }
        return minScore;
    }

    public int getMaxScore() {
        if (this.map.isEmpty())
            return -1;
        int maxScore = Integer.MIN_VALUE;
        for (final Map.Entry<E, AtomicInteger> entry : this.map.entrySet())
            if (entry.getValue().intValue() > maxScore) {
                maxScore = entry.getValue().intValue();
            }
        return maxScore;
    }

    @Override
    public String toString() {
        return this.map.toString();
    }

    /**
     * Creates and returns a sorted view to the keys. Sortorder is the score value.
     * @param up true = asc order, false = reverse order
     * @return iterator accessing the keys in order of score values
     */
    @Override
    public Iterator<E> keys(final boolean up) {
        // re-organize entries
        final TreeMap<Integer, Set<E>> m = new TreeMap<Integer, Set<E>>();
        Set<E> s;
        Integer is;
        for (final Map.Entry<E, AtomicInteger> entry: this.map.entrySet()) {
            is = entry.getValue().intValue();
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
        final List<E> l = new ArrayList<E>(m.size());
        for (final Set<E> f: m.values()) {
            for (final E e: f) l.add(e);
        }
        if (up) return l.iterator();

        // optionally reverse list
        final List<E> r = new ArrayList<E>(l.size());
        for (int i = l.size() - 1; i >= 0; i--) r.add(l.get(i));
        return r.iterator();
    }
    
    /**
     * Creates and returns a sorted view of the keys, sorted by their own natural order.
     * @param up true = asc order, false = reverse order
     * @return iterator accessing the keys in natural order
     */
    public Iterator<E> keysByNaturalOrder(final boolean up) {
    	TreeSet<E> sortedKeys;
    	if(up) {
    		sortedKeys = new TreeSet<>();
    	} else {
    		sortedKeys = new TreeSet<>(Collections.reverseOrder());
    	}
    	for(E key : this.map.keySet()) {
    		sortedKeys.add(key);
    	}
    	return sortedKeys.iterator();
    }
    
    /**
     * @param updatesListener an eventual object which wants to listen to successful updates on this score map
     */
    public void setUpdatesListener(final ScoreMapUpdatesListener updatesListener) {
		this.updatesListener = updatesListener;
	}

    public static void main(final String[] args) {
        final ConcurrentScoreMap<String> a = new ConcurrentScoreMap<String>();
        a.set("a", 55);
        a.set("b", 3);
        a.set("c", 80);
        final Iterator<String> i = a.keys(true);
        while (i.hasNext()) System.out.println(i.next());
    }

}
