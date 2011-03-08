/**
 *  ComparableARC
 *  an Adaptive Replacement Cache for comparable objects
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 24.08.2010 at http://yacy.net
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

import java.util.Comparator;
import java.util.Iterator;
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
        @Override
        public synchronized V get(Object k) {
            return super.get(k);
        }
        @Override
        public synchronized V put(K k, V v) {
            V r = super.put(k, v);
            if (r == null) keys.add(k);
            if (keys.size() > this.limit) {
                K w = this.keys.removeFirst();
                assert w != null;
                V t = super.remove(w);
                assert t != null : "keys.size() = " + keys.size() + ", limit = " + this.limit;
            }
            return r;
        }
        @Override
        public void putAll(Map<? extends K, ? extends V> map) {
            for (Map.Entry<? extends K, ? extends V> entry: map.entrySet()) put(entry.getKey(), entry.getValue());
        }
        @Override
        public synchronized V remove(Object k) {
            V r = super.remove(k);
            if (r == null) return null;
            @SuppressWarnings("unchecked")
            boolean removed = removeFromKeys((K) k);
            assert removed;
            return r;
        }
        @Override
        public synchronized Map.Entry<K,V> pollFirstEntry() {
            Map.Entry<K,V> entry = super.pollFirstEntry();
            boolean removed = removeFromKeys(entry.getKey());
            assert removed;
            return entry;
        }
        @Override
        public synchronized Map.Entry<K,V> pollLastEntry() {
            Map.Entry<K,V> entry = super.pollLastEntry();
            boolean removed = removeFromKeys(entry.getKey());
            assert removed;
            return entry;
        }
        @Override
        public synchronized void clear() {
            super.clear();
            this.keys.clear();
        }
        private boolean removeFromKeys(K k) {
            assert k != null;
            Iterator<K> i = keys.iterator();
            K x;
            while (i.hasNext()) {
                x = i.next();
                if (super.comparator().compare(k, x) == 0) {
                    i.remove();
                    return true;
                }
            }
            return false;
        }
    }
    
}
