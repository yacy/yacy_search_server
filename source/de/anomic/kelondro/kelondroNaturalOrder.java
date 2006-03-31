// kelondroNaturalOrder.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 29.12.2005
//
// $LastChangedDate: 2005-09-22 22:01:26 +0200 (Thu, 22 Sep 2005) $
// $LastChangedRevision: 774 $
// $LastChangedBy: orbiter $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.kelondro;

import java.util.Comparator;

public class kelondroNaturalOrder extends kelondroAbstractOrder implements kelondroOrder, Comparator, Cloneable {
    
    public static final kelondroOrder naturalOrder = new kelondroNaturalOrder(true);
    
    public kelondroNaturalOrder(boolean ascending) {
        this.asc = ascending;
        this.zero = null;
    }
    
    public Object clone() {
        kelondroNaturalOrder o = new kelondroNaturalOrder(this.asc);
        o.rotate(this.zero);
        return o;
    }
    
    public static kelondroOrder bySignature(String signature) {
        if (signature.equals("nd")) return new kelondroNaturalOrder(false);
        if (signature.equals("nu")) return new kelondroNaturalOrder(true);
        return null;
    }
    
    public String signature() {
        if (!asc) return "nd";
        if ( asc) return "nu";
        return null;
    }
    
    private static long cardinalI(byte[] key) {
        // returns a cardinal number in the range of 0 .. Long.MAX_VALUE
        long c = 0;
        int p = 0;
        while ((p < 8) && (p < key.length)) c = (c << 8) | ((long) key[p++] & 0xFF);
        while (p++ < 8) c = (c << 8);
        c = c >>> 1;
        return c;
    }
    
    public long cardinal(byte[] key) {
        if (this.zero == null) return cardinalI(key);
        long zeroCardinal = cardinalI(this.zero);
        long keyCardinal = cardinalI(key);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal + 1;
    }

    public static byte[] encodeLong(long c, int length) {
        byte[] b = new byte[length];
        while (length > 0) {
            b[--length] = (byte) (c & 0xFF);
            c >>= 8;
        }
        return b;
    }

    public static long decodeLong(byte[] s) {
        long c = 0;
        int p = 0;
        while ((p < 8) && (p < s.length)) c = (c << 8) | ((long) s[p++] & 0xFF);
        return c;
    }


    // Compares its two arguments for order.
    // Returns -1, 0, or 1 as the first argument
    // is less than, equal to, or greater than the second.
    // two arrays are also equal if one array is a subset of the other's array
    // with filled-up char(0)-values
    public int compare(byte[] a, byte[] b) {
        return (asc) ? compare0(a, b) : compare0(b, a);
    }

    public int compare0(byte[] a, byte[] b) {
        if (zero == null) return compares(a, b);
        // we have an artificial start point. check all combinations
        int az = compares(a, zero); // -1 if a < z; 0 if a == z; 1 if a > z
        int bz = compares(b, zero); // -1 if b < z; 0 if b == z; 1 if b > z
        if ((az ==  0) && (bz ==  0)) return 0;
        if  (az ==  0) return -1;
        if  (bz ==  0) return  1;
        if  (az == bz) return compares(a, b);
        return bz;
    }
    
    public static final int compares(byte[] a, byte[] b) {
        int i = 0;
        final int al = a.length;
        final int bl = b.length;
        final int len = (al > bl) ? bl : al;
        while (i < len) {
            if (a[i] > b[i]) return 1;
            if (a[i] < b[i]) return -1;
            // else the bytes are equal and it may go on yet undecided
            i++;
        }
        // check if we have a zero-terminated equality
        if ((i == al) && (i < bl) && (b[i] == 0)) return 0;
        if ((i == bl) && (i < al) && (a[i] == 0)) return 0;
        // no, decide by length
        if (al > bl) return 1;
        if (al < bl) return -1;
        // no, they are equal
        return 0;
    }

    public static void main(String[] args) {
        byte[] t = new byte[12];
        for (int i = 0; i < 12; i++) t[i] = (byte) 255;
        t[0] = (byte) 127;
        kelondroOrder o = new kelondroNaturalOrder(true);
        System.out.println(o.partition(t, 16));
    }
    
}
