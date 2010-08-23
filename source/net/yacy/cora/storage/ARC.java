/**
 *  ARC
 *  an interface for Adaptive Replacement Caches
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.08.2009 at http://yacy.net
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

public interface ARC<K, V> {

    /**
     * get the size of the ARC. this returns the sum of main and ghost cache
     * @return the complete number of entries in the ARC cache
     */
    public int size();
    
    /**
     * put a value to the cache.
     * @param s
     * @param v
     */
    public void put(K s, V v);
    
    /**
     * get a value from the cache.
     * @param s
     * @return the value
     */
    public V get(K s);
    
    /**
     * check if the map contains the key
     * @param s
     * @return
     */
    public boolean containsKey(K s);
    
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
}
