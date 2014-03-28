/**
 *  ARC
 *  an interface for Adaptive Replacement Caches
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.08.2009 at http://yacy.net
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

import java.util.Collection;
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
 */

public interface ARC<K, V> extends Iterable<Map.Entry<K, V>> {

    /**
     * get the size of the ARC. this returns the sum of main and ghost cache
     * @return the complete number of entries in the ARC cache
     */
    public int size();
    
    /**
     * put a value to the cache.
     * do not return a previous content value
     * @param s
     * @param v
     */
    public void insert(K s, V v);
    
    /**
     * put a value to the cache if there was not an entry before
     * do not return a previous content value
     * @param s
     * @param v
     */
    public void insertIfAbsent(K s, V v);

    /**
     * put a value to the cache if there was not an entry before
     * return a previous content value
     * @param s
     * @param v
     * @return the value before inserting the new value
     */
    public V putIfAbsent(K s, V v);

    /**
     * put a value to the cache.
     * @param s
     * @param v
     */
    public V put(K s, V v);

    
    /**
     * get a value from the cache.
     * @param s
     * @return the value
     */
    public V get(K s);

    /**
     * check if the map contains the value
     * @param value
     * @return the keys that have the given value
     */
    public Collection<K> getKeys(V value);
    
    /**
     * check if the map contains the key
     * @param key
     * @return true if the map contains the key
     */
    public boolean containsKey(K key);

    /**
     * remove an entry from the cache
     * @param s
     * @return the old value
     */
    public V remove(K s);
    
    /**
     * clear the cache
     */
    public void clear();
    
    /**
     * iterator implements the Iterable interface
     * the method can easily be implemented using the entrySet method
     */
    @Override
    public Iterator<Map.Entry<K, V>> iterator();

    /**
     * Return a Set view of the mappings contained in this map.
     * This method is the basis for all methods that are implemented
     * by a AbstractMap implementation
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K, V>> entrySet();
    
    /**
     * a hash code for this ARC
     * @return a hash code
     */
    @Override
    int hashCode();
}
