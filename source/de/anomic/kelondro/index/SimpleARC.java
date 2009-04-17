// SimpleARC.java
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

package de.anomic.kelondro.index;

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

public class SimpleARC <K, V> {

    public final static boolean accessOrder = false; // if false, then a insertion-order is used
    private int cacheSize;
    private LinkedHashMap<K, V> levelA, levelB;
    
    public SimpleARC(int cacheSize) {
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
     * put a value to the cache. The value may NOT exist before.
     * This restriction is used here to check possible algorithm logic error cases.
     * @param s
     * @param v
     */
    public synchronized void put(K s, V v) {
        assert this.levelA.get(s) == null;
        assert this.levelB.get(s) == null;
        this.levelA.put(s, v);
        assert (this.levelA.size() <= cacheSize); // the cache should shrink automatically
    }
    
    /**
     * get a value from the cache.
     * @param s
     * @return the value
     */
    public synchronized V get(K s) {
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
     * clear the cache
     */
    public synchronized void clear() {
        this.levelA.clear();
        this.levelB.clear();
    }
}
