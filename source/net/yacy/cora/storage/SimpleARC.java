/**
 *  SimpleARC
 *  a Simple Adaptive Replacement Cache
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 17.04.2009 at http://yacy.net
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

package net.yacy.cora.storage;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * This is a simple cache using two generations of hashtables to store the content with a LFU strategy.
 * The Algorithm is described in a slightly more complex version as Adaptive Replacement Cache, "ARC".
 * For details see http://www.almaden.ibm.com/cs/people/dmodha/ARC.pdf
 * or http://en.wikipedia.org/wiki/Adaptive_Replacement_Cache
 * This version omits the ghost entry handling which is described in ARC, and keeps both cache levels
 * at the same size.
 *
 * This class is defined abstract because it shall be used with either the HashARC or the ComparableARC classes
 */

abstract class SimpleARC<K, V> extends AbstractMap<K, V> implements Map<K, V>, Iterable<Map.Entry<K, V>>, ARC<K, V> {

    protected int cacheSize;
    protected Map<K, V> levelA, levelB; // we can assume that these maps are synchronized

    /**
     * put a value to the cache.
     * @param s
     * @param v
     */
    @Override
    public final synchronized void insert(final K s, final V v) {
        if (this.levelB.containsKey(s)) {
        	this.levelB.put(s, v);
            assert (this.levelB.size() <= this.cacheSize); // the cache should shrink automatically
        } else {
        	this.levelA.put(s, v);
            assert (this.levelA.size() <= this.cacheSize); // the cache should shrink automatically
        }
    }

    /**
     * put a value to the cache if there was not an entry before
     * do not return a previous content value
     * @param s
     * @param v
     */
    @Override
    public void insertIfAbsent(final K s, final V v) {
        if (this.levelB.containsKey(s)) {
            return;
        } else if (this.levelA.containsKey(s)) {
            return;
        } else {
            synchronized (this) {
                // we must repeat the tests again because we did this in a not synchronized environment
                if (this.levelB.containsKey(s)) {
                    return;
                } else if (this.levelA.containsKey(s)) {
                    return;
                } else {
                    this.levelA.put(s, v);
                    assert (this.levelA.size() <= this.cacheSize); // the cache should shrink automatically
                }
            }
        }
    }

    /**
     * put a value to the cache if there was not an entry before
     * return a previous content value
     * @param s
     * @param v
     * @return the value before inserting the new value
     */
    @Override
    public V putIfAbsent(final K s, final V v) {
        synchronized (this) {
            V o = this.levelB.get(s);
            if (o != null) return o;
            o = this.levelA.get(s);
            if (o != null) return o;
            this.levelA.put(s, v);
            assert (this.levelA.size() <= this.cacheSize); // the cache should shrink automatically
            return null;
        }
    }

    /**
     * put a value to the cache.
     * @param s
     * @param v
     */
    @Override
    public final synchronized V put(final K s, final V v) {
        if (this.levelB.containsKey(s)) {
            final V r = this.levelB.put(s, v);
            assert (this.levelB.size() <= this.cacheSize); // the cache should shrink automatically
            return r;
        }
        final V r = this.levelA.put(s, v);
        assert (this.levelA.size() <= this.cacheSize); // the cache should shrink automatically
        return r;
    }

    /**
     * get a value from the cache.
     * @param s
     * @return the value
     */
    @SuppressWarnings("unchecked")
    @Override
    public final V get(final Object s) {
        V v = this.levelB.get(s);
        if (v != null) return v;
        synchronized (this) {
            // we must repeat the get here because another thread may have moved the
            // entry from A to B meanwhile
            v = this.levelB.get(s);
            if (v != null) return v;
            // now get and move the entry to B
            v = this.levelA.remove(s);
            if (v == null) return null;
            // move value from A to B; since it was already removed from A, just put it to B
            //System.out.println("ARC: moving A->B, size(A) = " + this.levelA.size() + ", size(B) = " + this.levelB.size());
            this.levelB.put((K) s, v);
            assert (this.levelB.size() <= this.cacheSize); // the cache should shrink automatically
        }
        return v;
    }

    /**
     * check if the map contains the value
     * @param value
     * @return the keys that have the given value
     */
    @Override
    public Collection<K> getKeys(final V value) {
        final ArrayList<K> keys = new ArrayList<K>();
        synchronized (this.levelB) {
            for (final Map.Entry<K, V> entry: this.levelB.entrySet()) {
                if (value.equals(entry.getValue())) keys.add(entry.getKey());
            }
        }
        synchronized (this) {
            for (final Map.Entry<K, V> entry: this.levelA.entrySet()) {
                if (value.equals(entry.getValue())) keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /**
     * check if the map contains the key
     * @param s
     * @return
     */
    @Override
    public final boolean containsKey(final Object s) {
        if (this.levelB.containsKey(s)) return true;
        return this.levelA.containsKey(s);
    }


    /**
     * remove an entry from the cache
     * @param s
     * @return the old value
     */
    @Override
    public final synchronized V remove(final Object s) {
        final V r = this.levelB.remove(s);
        if (r != null) return r;
        return this.levelA.remove(s);
    }

    /**
     * clear the cache
     */
    @Override
    public final synchronized void clear() {
        this.levelA.clear();
        this.levelB.clear();
    }

    /**
     * get the size of the ARC. this returns the sum of main and ghost cache
     * @return the complete number of entries in the ARC cache
     */
    @Override
    public final synchronized int size() {
        return this.levelA.size() + this.levelB.size();
    }

    /**
     * iterator implements the Iterable interface
     */
    @Override
    public final Iterator<Map.Entry<K, V>> iterator() {
        return entrySet().iterator();
    }

    /**
     * Return a Set view of the mappings contained in this map.
     * This method is the basis for all methods that are implemented
     * by a AbstractMap implementation
     *
     * @return a set view of the mappings contained in this map
     */
    @Override
    public final synchronized Set<Map.Entry<K, V>> entrySet() {
        final Set<Map.Entry<K, V>> m = new HashSet<Map.Entry<K, V>>();
        for (final Map.Entry<K, V> entry: this.levelA.entrySet()) m.add(entry);
        for (final Map.Entry<K, V> entry: this.levelB.entrySet()) m.add(entry);
        return m;
    }

    /**
     * a hash code for this ARC
     * @return the hash code of one of the ARC partial hash tables
     */
    @Override
    public final int hashCode() {
        return this.levelA.hashCode();
    }

}
