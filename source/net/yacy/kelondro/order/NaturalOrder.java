// NaturalOrder.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 29.12.2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.order;

import java.util.Comparator;
import java.util.Iterator;

import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;

public final class NaturalOrder extends AbstractOrder<byte[]> implements ByteOrder, Comparator<byte[]>, Cloneable {
    
    public static final ByteOrder naturalOrder = new NaturalOrder(true);
    public static final Comparator<String> naturalComparator = new StringOrder(naturalOrder);
    public NaturalOrder(final boolean ascending) {
        this.asc = ascending;
        this.zero = null;
    }
    
    public HandleSet getHandleSet(final int keylength, final int space) throws RowSpaceExceededException {
        return new HandleSet(keylength, this, space);
    }
    
    public boolean wellformed(final byte[] a) {
        return true;
    }
    
    public boolean wellformed(final byte[] a, final int astart, final int alength) {
        return true;
    }
    
    public final Order<byte[]> clone() {
        final NaturalOrder o = new NaturalOrder(this.asc);
        o.rotate(this.zero);
        return o;
    }
    
    public static ByteOrder orderBySignature(final String signature) {
        ByteOrder oo = null;
        if (oo == null) oo = NaturalOrder.bySignature(signature);
        if (oo == null) oo = Base64Order.bySignature(signature);
        if (oo == null) oo = new NaturalOrder(true);
        return oo;
    }
    
    public final static ByteOrder bySignature(final String signature) {
        if (signature.equals("nd")) return new NaturalOrder(false);
        if (signature.equals("nu")) return new NaturalOrder(true);
        return null;
    }
    
    public final String signature() {
        if (!asc) return "nd";
        if ( asc) return "nu";
        return null;
    }
    
    private final static long cardinalI(final byte[] key, int off, int len) {
        // returns a cardinal number in the range of 0 .. Long.MAX_VALUE
        long c = 0;
        int lim = off + Math.min(8, len);
        int lim8 = off + 8;
        while (off < lim) c = (c << 8) | ((long) key[off++] & 0xFF);
        while (off++ < lim8) c = (c << 8);
        c = c >>> 1;
        return c;
    }
    
    public final long cardinal(final byte[] key) {
        if (this.zero == null) return cardinalI(key, 0, key.length);
        final long zeroCardinal = cardinalI(this.zero, 0, this.zero.length);
        final long keyCardinal = cardinalI(key, 0, key.length);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }
    
    public long cardinal(final byte[] key, int off, int len) {
        if (this.zero == null) return cardinalI(key, off, len);
        final long zeroCardinal = cardinalI(this.zero, 0, this.zero.length);
        final long keyCardinal = cardinalI(key, off, len);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }
    
    public final static byte[] encodeLong(long c, int length) {
        final byte[] b = new byte[length];
        while (length > 0) {
            b[--length] = (byte) (c & 0xFF);
            c >>= 8;
        }
        return b;
    }

    public final static void encodeLong(long c, final byte[] b, final int offset, int length) {
        assert offset + length <= b.length;
        while (length > 0) {
            b[--length + offset] = (byte) (c & 0xFF);
            c >>= 8;
        }
    }

    public final static long decodeLong(final byte[] s) {
        if (s == null) return 0;
        long c = 0;
        int p = 0;
        while (p < s.length) c = (c << 8) | ((long) s[p++] & 0xFF);
        return c;
    }
    
    public final static long decodeLong(final byte[] s, int offset, final int length) {
        if (s == null) return 0;
        long c = 0;
        final int m = Math.min(s.length, offset + length);
        while (offset < m) c = (c << 8) | ((long) s[offset++] & 0xFF);
        return c;
    }

    private static final int sig(final int x) {
        return (x > 0) ? 1 : (x < 0) ? -1 : 0;
    }
    
    // Compares its two arguments for order.
    // Returns -1, 0, or 1 as the first argument
    // is less than, equal to, or greater than the second.
    // two arrays are also equal if one array is a subset of the other's array
    // with filled-up char(0)-values
    public final int compare(final byte[] a, final byte[] b) {
        if (a.length == b.length) {
            return (asc) ? compare0(a, 0, b, 0, a.length) : compare0(b, 0, a, 0, a.length);
        }
        int length = Math.min(a.length, b.length);
        if (asc) {
            int c = compare0(a, 0, b, 0, length);
            if (c != 0) return c;
            return (a.length > b.length) ? 1 : -1;
        }
        int c = compare0(b, 0, a, 0, length);
        if (c != 0) return c;
        return (a.length > b.length) ? -1 : 1;
    }

    public final int compare(final byte[] a, final int aoffset, final byte[] b, final int boffset, final int length) {
        return (asc) ? compare0(a, aoffset, b, boffset, length) : compare0(b, boffset, a, aoffset, length);
    }

    public final int compare0(final byte[] a, final int aoffset, final byte[] b, final int boffset, final int length) {
        if (zero == null) return compares(a, aoffset, b, boffset, length);
        // we have an artificial start point. check all combinations
        assert length == zero.length;
        final int az = compares(a, aoffset, zero, 0, length); // -1 if a < z; 0 if a == z; 1 if a > z
        final int bz = compares(b, boffset, zero, 0, length); // -1 if b < z; 0 if b == z; 1 if b > z
        if (az == bz) return compares(a, aoffset, b, boffset, length);
        return sig(az - bz);
    }

    public final boolean equal(final byte[] a, final byte[] b) {
        if ((a == null) && (b == null)) return true;
        if ((a == null) || (b == null)) return false;
        if (a.length != b.length) return false;
        int astart = 0;
        int bstart = 0;
        int length = a.length;
        while (length-- != 0) {
            if (a[astart++] != b[bstart++]) return false;
        }
        return true;
    }
    
    public final boolean equal(final byte[] a, int astart, final byte[] b, int bstart, int length) {
        if ((a == null) && (b == null)) return true;
        if ((a == null) || (b == null)) return false;
        while (length-- != 0) {
            if (a[astart++] != b[bstart++]) return false;
        }
        return true;
    }
   
    public static final int compares(final byte[] a, final int aoffset, final byte[] b, final int boffset, final int length) {
        int i = 0;
        int aa, bb;
        while (i < length) {
            aa = 0xff & a[i + aoffset];
            bb = 0xff & b[i + boffset];
            if (aa > bb) return 1;
            if (aa < bb) return -1;
            // else the bytes are equal and it may go on yet undecided
            i++;
        }
        // they are equal
        return 0;
    }

    public static final String arrayList(final byte[] b, final int start, int length) {
        if (b == null) return "NULL";
        if (b.length == 0) return "[]";
        length = Math.min(length, b.length - start);
        final StringBuilder sb = new StringBuilder(b.length * 4);
        sb.append('[').append(Integer.toString(b[start])).append(',');
        for (int i = 1; i < length; i++) sb.append(' ').append(Integer.toString(b[start + i])).append(',');
        sb.append(']');
        return sb.toString();
    }
    
    public static final String table(final byte[] b, final int linewidth) {
        if (b == null) return "NULL";
        if (b.length == 0) return "[]";
        final StringBuilder sb = new StringBuilder(b.length * 4);
        for (int i = 0; i < b.length; i++) {
            if (i % linewidth == 0)
                sb.append('\n').append("# ").append(Integer.toHexString(i)).append(": ");
            else
                sb.append(',');
            sb.append(' ').append(Integer.toString(0xff & b[i]));
            if (i >= 65535) break;
        }
        sb.append('\n');
        return sb.toString();
    }
    
    public static Iterator<Long> LongIterator(Iterator<byte[]> b256Iterator) {
        return new LongIter(b256Iterator);
    }
    
    public static class LongIter implements Iterator<Long> {

        private final Iterator<byte[]> b256Iterator;
        
        public LongIter(Iterator<byte[]> b256Iterator) {
            this.b256Iterator = b256Iterator;
        }
        
        public boolean hasNext() {
            return this.b256Iterator.hasNext();
        }

        public Long next() {
            byte[] b = this.b256Iterator.next();
            assert (b != null);
            if (b == null) return null;
            return Long.valueOf(decodeLong(b));
        }

        public void remove() {
            this.b256Iterator.remove();
        }
        
    }
    
    public static void main(final String[] args) {
        final byte[] t = new byte[12];
        for (int i = 0; i < 12; i++) t[i] = (byte) 255;
        t[0] = (byte) 127;
        final Order<byte[]> o = new NaturalOrder(true);
        System.out.println(o.partition(t, 16));
    }
    
}
