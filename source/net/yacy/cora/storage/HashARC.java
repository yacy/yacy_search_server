/**
 *  HashARC
 *  an Adaptive Replacement Cache for objects that can be compared using hashing
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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class HashARC<K, V> extends SimpleARC<K, V> implements Map<K, V>, Iterable<Map.Entry<K, V>>, ARC<K, V> {

    final static boolean accessOrder = false; // if false, then a insertion-order is used

    public HashARC(final int cacheSize) {
        this.cacheSize = cacheSize / 2;
        super.levelA = Collections.synchronizedMap(new LinkedLRUHashMap());
        this.levelB = Collections.synchronizedMap(new LinkedLRUHashMap());
    }


    private final class LinkedLRUHashMap extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;

        LinkedLRUHashMap() {
            super(1, 0.99f, HashARC.accessOrder);
        }

        @Override protected boolean removeEldestEntry(final Entry<K, V> eldest) {
            return size() > HashARC.this.cacheSize;
        }
    }
}
