/**
 *  ConcurrentARC
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 17.04.2009 at http://yacy.net
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


/**
 * This is a simple cache using two generations of hashtables to store the content with a LFU strategy.
 * The Algorithm is described in a slightly more complex version as Adaptive Replacement Cache, "ARC".
 * For details see http://www.almaden.ibm.com/cs/people/dmodha/ARC.pdf
 * or http://en.wikipedia.org/wiki/Adaptive_Replacement_Cache
 * This version omits the ghost entry handling which is described in ARC, and keeps both cache levels
 * at the same size.
 */

public final class ConcurrentARC<K, V> implements ARC<K, V> {

    private final int mask;
    private final ARC<K, V> arc[];
    
    @SuppressWarnings("unchecked")
	public ConcurrentARC(final int cacheSize, final int partitions) {
    	int m = 1;
    	while (m < partitions) m = m * 2;
    	this.arc = new SimpleARC[m];
    	for (int i = 0; i < this.arc.length; i++) this.arc[i] = new SimpleARC<K, V>(cacheSize / m);
    	m -= 1;
    	this.mask = m;
    }
    
    /**
     * put a value to the cache.
     * @param s
     * @param v
     */
    public final void put(final K s, final V v) {
    	this.arc[s.hashCode() & mask].put(s, v);
    }
    
    /**
     * get a value from the cache.
     * @param s
     * @return the value
     */
    public final V get(final K s) {
    	return this.arc[s.hashCode() & mask].get(s);
    }
    
    /**
     * check if the map contains the key
     * @param s
     * @return
     */
    public final boolean containsKey(final K s) {
    	return this.arc[s.hashCode() & mask].containsKey(s);
    }
    
    /**
     * remove an entry from the cache
     * @param s
     * @return the old value
     */
    public final V remove(final K s) {
    	return this.arc[s.hashCode() & mask].remove(s);
    }
    
    /**
     * clear the cache
     */
    public final void clear() {
    	for (ARC<K, V> a: this.arc) a.clear();
    }

    public final int size() {
        int s = 0;
        for (ARC<K, V> a: this.arc) s += a.size();
        return s;
    }
}
