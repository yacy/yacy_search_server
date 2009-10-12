// SimpleARC.java
// a Simple Adaptive Replacement Cache
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 17.04.2009 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.index;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This is a simple cache using two generations of hashtables to store the content with a LFU strategy.
 * The Algorithm is described in a slightly more complex version as Adaptive Replacement Cache, "ARC".
 * For details see http://www.almaden.ibm.com/cs/people/dmodha/ARC.pdf
 * or http://en.wikipedia.org/wiki/Adaptive_Replacement_Cache
 * This version omits the ghost entry handling which is described in ARC, and keeps both cache levels
 * at the same size.
 */

public final class SimpleARC<K, V> implements ARC<K, V> {

    public    final static boolean accessOrder = false; // if false, then a insertion-order is used
    
    protected final int cacheSize;
    private   final Map<K, V> levelA, levelB;
    
    public SimpleARC(final int cacheSize) {
        this.cacheSize = cacheSize / 2;
        this.levelA = new LinkedHashMap<K, V>(cacheSize, 0.1f, accessOrder) {
            private static final long serialVersionUID = 1L;
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > SimpleARC.this.cacheSize;
            }
        };
        this.levelB = new LinkedHashMap<K, V>(cacheSize, 0.1f, accessOrder) {
            private static final long serialVersionUID = 1L;
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > SimpleARC.this.cacheSize;
            }
        };
    }
    
    /**
     * put a value to the cache.
     * @param s
     * @param v
     */
    public final synchronized void put(K s, V v) {
        if (this.levelB.containsKey(s)) {
        	this.levelB.put(s, v);
            assert (this.levelB.size() <= cacheSize); // the cache should shrink automatically
        } else {
        	this.levelA.put(s, v);
            assert (this.levelA.size() <= cacheSize); // the cache should shrink automatically
        }
    }
    
    /**
     * get a value from the cache.
     * @param s
     * @return the value
     */
    public final synchronized V get(K s) {
        V v = this.levelB.get(s);
        if (v != null) return v;
        v = this.levelA.remove(s);
        if (v == null) return null;
        // move value from A to B; since it was already removed from A, just put it to B
        //System.out.println("ARC: moving A->B, size(A) = " + this.levelA.size() + ", size(B) = " + this.levelB.size());
        this.levelB.put(s, v);
        assert (this.levelB.size() <= cacheSize); // the cache should shrink automatically
        return v;
    }
    
    /**
     * check if the map contains the key
     * @param s
     * @return
     */
    public final synchronized boolean containsKey(K s) {
        if (this.levelB.containsKey(s)) return true;
        return this.levelA.containsKey(s);
    }
    
    /**
     * remove an entry from the cache
     * @param s
     * @return the old value
     */
    public final synchronized V remove(K s) {
        V r = this.levelB.remove(s);
        if (r != null) return r;
        return this.levelA.remove(s);
    }
    
    /**
     * clear the cache
     */
    public final synchronized void clear() {
        this.levelA.clear();
        this.levelB.clear();
    }
}
