// ByteArray.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.03.2007 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.util;

import java.util.HashMap;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.order.ByteOrder;


/**
 * this class is a experimental replacement of byte[].
 * It can be used if a byte[] shall be stored within a HashMap or HashSet
 * which is faster than TreeMap but is generally not possible because storing
 * a byte[] in a Hashtable does not work because the hash computation does not
 * work for byte[]. This class extends byte[] with a cached hashing function,
 * so it can be used in hashtables.
 */

public class ByteArray {
    
    private final byte[] buffer;
    private int hash;

    
    public ByteArray(final byte[] bb) {
        this.buffer = bb;
        this.hash = 0;
    }

    public int length() {
        return buffer.length;
    }
    
    public byte[] asBytes() {
        return this.buffer;
    }
    
    public byte readByte(final int pos) {
        return buffer[pos];
    }
    
    public static boolean startsWith(final byte[] buffer, final byte[] pattern) {
        // compares two byte arrays: true, if pattern appears completely at offset position
        if (buffer == null && pattern == null) return true;
        if (buffer == null || pattern == null) return false;
        if (buffer.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (buffer[i] != pattern[i]) return false;
        return true;
    }
    
    public int compareTo(final ByteArray b, final ByteOrder order) {
        assert this.buffer.length == b.buffer.length;
        return order.compare(this.buffer, b.buffer);
    }
    
    public int compareTo(final int aoffset, final int alength, final ByteArray b, final int boffset, final int blength, final ByteOrder order) {
        assert alength == blength;
        return order.compare(this.buffer, aoffset, b.buffer, boffset, blength);
    }
    
    @Override
    public int hashCode() {
        if (this.hash != 0) return this.hash;        
        this.hash = hashCode(this.buffer);
        return this.hash;
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
        if (buffer == null && b == null) return true;
        if (buffer == null || b == null) return false;
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
            if (i > 1) return result; else throw new NumberFormatException(UTF8.String(s));
        } else {
            return -result;
        }
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
