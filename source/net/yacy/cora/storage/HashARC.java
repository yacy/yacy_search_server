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

    public final static boolean accessOrder = false; // if false, then a insertion-order is used

    public HashARC(final int cacheSize) {
        this.cacheSize = cacheSize / 2;
        super.levelA = Collections.synchronizedMap(new LinkedHashMap<K, V>(1, 0.1f, accessOrder) {
            private static final long serialVersionUID = 1L;
            @Override protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
                return size() > HashARC.this.cacheSize;
            }
        });
        this.levelB = Collections.synchronizedMap(new LinkedHashMap<K, V>(1, 0.1f, accessOrder) {
            private static final long serialVersionUID = 1L;
            @Override protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
                return size() > HashARC.this.cacheSize;
            }
        });
    }

    public static void main(final String[] args) {
        final Random r = new Random();
        final int testsize = 10000;
        final ARC<String, String> a = new HashARC<String, String>(testsize * 2);
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
