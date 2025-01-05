/**
 *  ByteArray
 *  Copyright 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 30.03.2007 on http://yacy.net
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

package net.yacy.cora.util;

import java.util.HashMap;

import net.yacy.cora.document.encoding.UTF8;


/**
 * this class is a experimental replacement of byte[].
 * It can be used if a byte[] shall be stored within a HashMap or HashSet
 * which is faster than TreeMap but is generally not possible because storing
 * a byte[] in a Hashtable does not work because the hash computation does not
 * work for byte[]. This class extends byte[] with a cached hashing function,
 * so it can be used in hashtables.
 * //FIXME: so does storing byte[] in HashMap help? as I'm moving use of Hashtable to
 * //FIXME: HashMap, if so, please remove this class- or notify me
 */

public class ByteArray {

    private final byte[] buffer;


    public ByteArray(final byte[] bb) {
        this.buffer = bb;
    }
    
    public long usedMemory() {
        return this.buffer.length;
    }

    public byte[] asBytes() {
        return this.buffer;
    }

    public static boolean startsWith(final byte[] buffer, final byte[] pattern) {
        // compares two byte arrays: true, if pattern appears completely at offset position
        if (buffer == null && pattern == null) return true;
        if (buffer == null || pattern == null) return false;
        if (buffer.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (buffer[i] != pattern[i]) return false;
        return true;
    }

    private int hashCache = Integer.MIN_VALUE; // if this is used in a compare method many times, a cache is useful

    @Override
    public int hashCode() {
        if (this.hashCache == Integer.MIN_VALUE) {
            this.hashCache = ByteArray.hashCode(this.buffer);
        }
        return this.hashCache;
    }

    /**
     * compute a hash code that is equal to the hash computation of String()
     * @param b
     * @return a hash number
     */
    public static int hashCode(byte[] b) {
        int h = 0;
        for (byte c: b) h = 31 * h + (c & 0xFF);
        return h;
    }

    @Override
    public boolean equals(Object other) {
        ByteArray b = (ByteArray) other;
        if (this.buffer == null && b == null) return true;
        if (this.buffer == null || b == null) return false;
        if (this.buffer.length != b.buffer.length) return false;
        int l = this.buffer.length;
        while (--l >= 0) if (this.buffer[l] != b.buffer[l]) return false;
        return true;
    }

    public static long parseDecimal(final byte[] s) throws NumberFormatException {
        if (s == null) throw new NumberFormatException("null");

        long result = 0;
        boolean negative = false;
        int i = 0, max = s.length;
        long limit;
        long multmin;
        long digit;

        if (max <= 0) throw new NumberFormatException(UTF8.String(s));
        if (s[0] == '-') {
            negative = true;
            limit = Long.MIN_VALUE;
            i++;
        } else {
            limit = -Long.MAX_VALUE;
        }
        multmin = limit / 10;
        if (i < max) {
            digit = s[i++] - 48;
            if (digit < 0) throw new NumberFormatException(UTF8.String(s));
            result = -digit;
        }
        while (i < max) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = s[i++] - 48;
            if (digit < 0) throw new NumberFormatException(UTF8.String(s));
            if (result < multmin) throw new NumberFormatException(UTF8.String(s));
            result *= 10;
            if (result < limit + digit) throw new NumberFormatException(UTF8.String(s));
            result -= digit;
        }
        if (negative) {
            if (i > 1) return result;
            throw new NumberFormatException(UTF8.String(s));
        }
        return -result;
    }

    public static void main(String[] args) {
        ByteArray a0 = new ByteArray("abc".getBytes());
        ByteArray a1 = new ByteArray("abc".getBytes());
        ByteArray b  = new ByteArray("bbb".getBytes());
        System.out.println("a0 " + ((a0.equals(a1)) ? "=" : "!=") + " a1");
        System.out.println("a0 " + ((a0.equals(b)) ? "=" : "!=") + " b");
        HashMap<ByteArray, Integer> map = new HashMap<ByteArray, Integer>();
        map.put(a0, 1);
        //map.put(a1, 1);
        map.put(b, 2);
        System.out.println("map.size() = " + map.size());
        System.out.println("hashCode(a0) = " + a0.hashCode());
        System.out.println("UTF8.String(a0).hashCode = " + (UTF8.String(a0.asBytes())).hashCode());
        System.out.println("hashCode(a1) = " + a1.hashCode());
        System.out.println("UTF8.String(a1).hashCode = " + (UTF8.String(a1.asBytes())).hashCode());
        System.out.println("hashCode(b) = " + b.hashCode());
        System.out.println("a0 " + ((map.containsKey(a0)) ? "in" : "not in") + " map");
        System.out.println("a1 " + ((map.containsKey(a1)) ? "in" : "not in") + " map");
        System.out.println("b " + ((map.containsKey(b)) ? "in" : "not in") + " map");
        System.out.println("parseIntDecimal " + parseDecimal("6543".getBytes()));
    }
}
