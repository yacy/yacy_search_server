/**
 *  ConcurrentARC
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;


/**
 * This is a simple cache using two generations of hashtables to store the content with a LFU strategy.
 * The Algorithm is described in a slightly more complex version as Adaptive Replacement Cache, "ARC".
 * For details see http://www.almaden.ibm.com/cs/people/dmodha/ARC.pdf
 * or http://en.wikipedia.org/wiki/Adaptive_Replacement_Cache
 * This version omits the ghost entry handling which is described in ARC, and keeps both cache levels
 * at the same size.
 */

public final class ConcurrentARC<K, V> extends AbstractMap<K, V> implements Map<K, V>, Iterable<Map.Entry<K, V>>, ARC<K, V> {

    private final int mask;
    private final ARC<K, V> arc[];

    /**
     * create a concurrent ARC based on a HashARC. The type of the key elements must implement a hashing function
     * @param cacheSize the number of maximum entries
     * @param partitions the number of partitions
     */
    @SuppressWarnings("unchecked")
	public ConcurrentARC(final int cacheSize, final int partitions) {
    	int m = 1;
    	while (m < partitions) m = m * 2;
        int partitionSize = cacheSize / m;
        if (partitionSize < 4) partitionSize = 4;
    	this.arc = new HashARC[m];
    	for (int i = 0; i < this.arc.length; i++) this.arc[i] = new HashARC<K, V>(partitionSize);
    	m -= 1;
    	this.mask = m;
    }

    /**
     * create a concurrent ARC based on a ComparableARC
     * @param cacheSize the number of maximum entries
     * @param partitions the number of partitions
     * @param comparator a comparator for the key object which may be of type byte[]
     */
    @SuppressWarnings("unchecked")
    public ConcurrentARC(final int cacheSize, final int partitions, final Comparator<? super K> comparator) {
        int m = 1;
        while (m < partitions) m = m * 2;
        int partitionSize = cacheSize / m;
        if (partitionSize < 4) partitionSize = 4;
        this.arc = new ComparableARC[m];
        for (int i = 0; i < this.arc.length; i++) this.arc[i] = new ComparableARC<K, V>(partitionSize, comparator);
        m -= 1;
        this.mask = m;
    }

    /**
     * put a value to the cache.
     * @param s
     * @param v
     */
    @Override
    public final void insert(final K s, final V v) {
        this.arc[getPartition(s)].insert(s, v);
    }

    /**
     * put a value to the cache if there was not an entry before
     * do not return a previous content value
     * @param s
     * @param v
     */
    @Override
    public void insertIfAbsent(final K s, final V v) {
        this.arc[getPartition(s)].insertIfAbsent(s, v);
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
        return this.arc[getPartition(s)].putIfAbsent(s, v);
    }

    /**
     * put a value to the cache.
     * @param s
     * @param v
     */
    @Override
    public final V put(final K s, final V v) {
        return this.arc[getPartition(s)].put(s, v);
    }

    /**
     * get a value from the cache.
     * @param s
     * @return the value
     */
    @SuppressWarnings("unchecked")
    @Override
    public final V get(final Object s) {
    	return this.arc[getPartition(s)].get((K) s);
    }

    /**
     * check if the map contains the value
     * @param value
     * @return the keys that have the given value
     */
    @Override
    public Collection<K> getKeys(final V value) {
        final ArrayList<K> keys = new ArrayList<K>();
        for (final ARC<K, V> element : this.arc)
            keys.addAll(element.getKeys(value));
        return keys;
    }

    /**
     * check if the map contains the key
     * @param s
     * @return
     */
    @SuppressWarnings("unchecked")
    @Override
    public final boolean containsKey(final Object s) {
    	return this.arc[getPartition(s)].containsKey((K) s);
    }

    /**
     * remove an entry from the cache
     * @param s
     * @return the old value
     */
    @SuppressWarnings("unchecked")
    @Override
    public final V remove(final Object s) {
    	return this.arc[getPartition(s)].remove((K) s);
    }

    /**
     * clear the cache
     */
    @Override
    public final void clear() {
    	for (final ARC<K, V> a: this.arc) a.clear();
    }

    /**
     * get the size of the ARC.
     * @return the complete number of entries in the ARC cache
     */
    @Override
    public final int size() {
        int s = 0;
        for (final ARC<K, V> a: this.arc) s += a.size();
        return s;
    }

    /**
     * iterator implements the Iterable interface
     */
    @Override
    public Iterator<java.util.Map.Entry<K, V>> iterator() {
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
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        final Set<Map.Entry<K, V>> m = new HashSet<Map.Entry<K, V>>();
        for (final ARC<K, V> a: this.arc) {
            for (final Map.Entry<K, V> entry: a.entrySet()) m.add(entry);
        }
        return m;
    }

    /**
     * a hash code for this ARC
     * @return a hash code
     */
    @Override
    public int hashCode() {
        return this.arc.hashCode();
    }

    //private static String latestObject = "";
    /**
     * return in which partition the Object belongs
     * This function uses the objects hashCode() function
     * except for byte[] keys
     * @return the partition number
     */
    private int getPartition(final Object x) {
        if (x instanceof byte[]) {
            int h = 0;
            for (final byte c: (byte[])x) h = 31 * h + (c & 0xFF);
            final int p = h & this.mask;
            //String o = UTF8.String((byte[]) x); try { if (o.equals(latestObject)) throw new RuntimeException("ConcurrentARC: p = " + p + ", objectb = " + o); } catch (final Exception e) { Log.logException(e); } latestObject = o;
            return p;
        }
        final int p = x.hashCode() & this.mask;
        //String o = x.toString(); try { if (o.equals(latestObject)) throw new RuntimeException("ConcurrentARC: p = " + p + ", objecto = " + o); } catch (final Exception e) { Log.logException(e); } latestObject = o;
        return p;
    }

    public static void main(final String[] args) {
        final Random r = new Random();
        final int testsize = 10000;
        final ARC<String, String> a = new ConcurrentARC<String, String>(testsize * 3, Runtime.getRuntime().availableProcessors());
        final Map<String, String> b = new HashMap<String, String>();
        String key, value;
        for (int i = 0; i < testsize; i++) {
            key = "k" + r.nextInt();
            value = "v" + r.nextInt();
            a.insertIfAbsent(key, value);
            b.put(key, value);
        }

        // now put half of the entries AGAIN into the ARC
        int h = testsize / 2;
        for (final Map.Entry<String, String> entry: b.entrySet()) {
            a.put(entry.getKey(), entry.getValue());
            if (h-- <= 0) break;
        }

        // test correctness
        for (final Map.Entry<String, String> entry: b.entrySet()) {
            if (!a.containsKey(entry.getKey())) {
                System.out.println("missing: " + entry.getKey());
                continue;
            }
            if (!a.get(entry.getKey()).equals(entry.getValue())) {
                System.out.println("wrong: a = " + entry.getKey() + "," + a.get(entry.getKey()) + "; v = " + entry.getValue());
            }
        }
        System.out.println("finished test!");
    }

}
