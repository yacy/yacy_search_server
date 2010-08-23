/**
 *  ComparableARC
 *  an Adaptive Replacement Cache for comparable objects
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 24.08.2010 at http://yacy.net
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

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

public final class ComparableARC<K, V> extends SimpleARC<K, V> implements Map<K, V>, Iterable<Map.Entry<K, V>>, ARC<K, V> {

    
    public ComparableARC(final int cacheSize, Comparator<? super K> comparator) {
        super.cacheSize = cacheSize / 2;
        super.levelA = new LimitedTreeMap<K, V>(this.cacheSize, comparator);
        super.levelB = new LimitedTreeMap<K, V>(this.cacheSize, comparator);
    }
    
    private static class LimitedTreeMap<K, V> extends TreeMap<K, V> {
        private static final long serialVersionUID = -2276429187676080820L;
        int limit;
        LinkedList<K> keys;
        public LimitedTreeMap(final int cacheSize, Comparator<? super K> comparator) {
            super(comparator);
            this.limit = cacheSize;
            this.keys = new LinkedList<K>();
        }
        public V put(K k, V v) {
            V r = super.put(k, v);
            keys.add(k);
            if (keys.size() > this.limit) {
                K w = this.keys.removeFirst();
                V t = super.remove(w);
                assert t != null;
            }
            return r;
        }
        public V remove(Object k) {
            V r = super.remove(k);
            this.keys.remove(k);
            return r;
        }
        public void clear() {
            super.clear();
            this.keys.clear();
        }
    }
    
}
