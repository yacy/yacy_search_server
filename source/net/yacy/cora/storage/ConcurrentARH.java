/**
 *  ConcurrentARH
 *  an interface for Adaptive Replacement Handles
 *  Copyright 2014 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 15.01.2014 at http://yacy.net
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

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * An ARH is set for handles in respect to the ARC construction: an Adaptive Replacement Handle Cache.
 */
public class ConcurrentARH<K>  extends AbstractSet<K> implements Set<K>, Iterable<K>, ARH<K> {

    private static final Object _EXIST = new Object();
    
    private final ConcurrentARC<K, Object> cache;
    
    public ConcurrentARH(final int cacheSize, final int partitions) {
        this.cache = new ConcurrentARC<K, Object>(cacheSize, partitions);
    }
    
    @Override
    public int size() {
        return this.cache.size();
    }

    @Override
    public boolean contains(Object o) {
        return this.cache.containsKey(o);
    }

    @Override
    public void clear() {
        this.cache.clear();
    }

    @Override
    public Set<K> set() {
        return this.cache.keySet();
    }

    @Override
    public Iterator<K> iterator() {
        return this.cache.keySet().iterator();
    }

    @Override
    public boolean isEmpty() {
        return this.cache.isEmpty();
    }

    @Override
    public boolean add(K e) {
        Object o = this.cache.put(e, _EXIST);
        return o == null;
    }

    @Override
    public void delete(Object o) {
        this.cache.remove(o);
    }

}
