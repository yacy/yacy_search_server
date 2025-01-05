/**
 *  Base64Order
 *  (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 03.01.2006 on http://yacy.net
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

package net.yacy.cora.order;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Random;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;

public class Base64Order extends AbstractOrder<byte[]> implements ByteOrder, Comparator<byte[]>, Cloneable, Serializable {

    private static final long serialVersionUID=980647587445343851L;

    public  static final byte[] alpha_standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes();
    public  static final byte[] alpha_enhanced = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".getBytes();
    private static final byte[] ahpla_standard = new byte[128];
    private static final byte[] ahpla_enhanced = new byte[128];

    static {
        for (int i = 0; i < 128; i++) {
            ahpla_standard[i] = -1;
            ahpla_enhanced[i] = -1;
        }
        for (int i = 0; i < alpha_standard.length; i++) {
            ahpla_standard[alpha_standard[i]] = (byte) i;
            ahpla_enhanced[alpha_enhanced[i]] = (byte) i;
        }
    }

    public static final Base64Order standardCoder = new Base64Order(true, true);
    public static final Base64Order enhancedCoder = new Base64Order(true, false);

    private final boolean rfc1521compliant;
    private final byte[] alpha;
    private final byte[] ahpla;
    private final byte[] ab; // decision table for comparisments

    public Base64Order(final boolean up, final boolean rfc1521compliant) {
        // if we choose not to be rfc1521compliant,
        // then we get shorter base64 results which are also filename-compatible
        this.rfc1521compliant = rfc1521compliant;
        this.asc = up;
        this.alpha = (rfc1521compliant) ? alpha_standard : alpha_enhanced;
        this.ahpla = (rfc1521compliant) ? ahpla_standard : ahpla_enhanced;
        this.ab = new byte[1 << 14];
        byte acc, bcc;
        byte c;
        // pre-compute comparisment results: this omits one single ahpla lookup during comparisment
        for (final byte ac: this.alpha) {
            for (final byte bc: this.alpha) {
                acc = this.ahpla[ac];
                bcc = this.ahpla[bc];
                c = 0;
                if (acc > bcc) c = 1;
                if (acc < bcc) c = -1;

                this.ab[(ac << 7) | bc] = c;
            }
        }
    }

    public static byte[] zero(int length) {
        final byte[] z = new byte[length];
        while (length > 0) {
            length--; z[length] = alpha_standard[0];
        }
        return z;
    }

    @Override
    public Order<byte[]> clone() {
        final Base64Order o = new Base64Order(this.asc, this.rfc1521compliant);
        o.rotate(this.zero);
        return o;
    }

    @Override
    public final boolean wellformed(final byte[] a) {
        return this.wellformed(a, 0, a.length);
    }

    @Override
    public final boolean wellformed(final byte[] a, final int astart, final int alength) {
        assert (astart + alength <= a.length) : "astart = " + astart + ", alength = " + alength + ", a.length = " + a.length;
        int b;
        for (int i = astart + alength - 1; i >= astart; i--) {
            b = a[i];
            if ((b < 0) || (b >= 128) || (this.ahpla[b] == -1)) return false;
        }
        return true;
    }

    public final static ByteOrder bySignature(final String signature) {
        if ("Bd".equals(signature)) return new Base64Order(false, false);
        if ("bd".equals(signature)) return new Base64Order(false, true);
        if ("Bu".equals(signature)) return new Base64Order(true, false);
        if ("bu".equals(signature)) return new Base64Order(true, true);
        return null;
    }

    @Override
    public final String signature() {
        if ((!this.asc) && (!this.rfc1521compliant)) return "Bd";
        if ((!this.asc) && ( this.rfc1521compliant)) return "bd";
        if (( this.asc) && (!this.rfc1521compliant)) return "Bu";
        if (( this.asc) && ( this.rfc1521compliant)) return "bu";
        return null;
    }

    public final char encodeByte(final byte b) {
        return (char) this.alpha[b];
    }

    public final byte decodeByte(final byte b) {
        return this.ahpla[b];
    }

    public final byte decodeByte(final char b) {
        return this.ahpla[b];
    }

    public final StringBuilder encodeLongSB(long c, int length) {
        final StringBuilder s = new StringBuilder(length);
        s.setLength(length);
        while (length > 0) {
            s.setCharAt(--length, (char) this.alpha[(byte) (c & 0x3F)]);
            c >>= 6;
        }
        return s;
    }

    public final byte[] encodeLongBA(long c, int length) {
        final byte[] s = new byte[length];
        while (length > 0) {
            s[--length] = this.alpha[(byte) (c & 0x3F)];
            c >>= 6;
        }
        return s;
    }

    public final void encodeLong(long c, final byte[] b, final int offset, int length) {
        assert offset + length <= b.length;
        while (length > 0) {
            b[--length + offset] = this.alpha[(byte) (c & 0x3F)];
            c >>= 6;
        }
    }

    public final long decodeLong(String s) {
        while (s.endsWith("=")) s = s.substring(0, s.length() - 1);
        long c = 0;
        for (int i = 0; i < s.length(); i++) c = (c << 6) | this.ahpla[s.charAt(i)];
        return c;
    }

    public final long decodeLong(final byte[] s, final int offset, int length) {
        while ((length > 0) && (s[offset + length - 1] == '=')) length--;
        long c = 0;
        for (int i = 0; i < length; i++) c = (c << 6) | this.ahpla[s[offset + i]];
        return c;
    }

    public static long max(final int len) {
        // computes the maximum number that can be coded with a base64-encoded
        // String of base len
        long c = 0;
        for (int i = 0; i < len; i++) c = (c << 6) | 63;
        return c;
    }

    public final String encodeString(final String in) {
        return this.encode(UTF8.getBytes(in));
    }

    /**
     * encode arbitrary 2^8 bit-values to b64 strings. we will do that by grouping each three input bytes to four output bytes.
     * @param in
     * @return a base64-encoding of the input if rfc1521compliant = true, otherwise a b64-encoding of the input.
     */
    public final String encode(final byte[] in) {
        int rfc1521compliantLength = ((in.length + 2) / 3) * 4; // (bytes/bits/chars) = {(0/0/0), (1/8/4), (2/16/4), (3/24/4), (4/32/8), (5/40/8), (6/48/8), (7/56/12), (8/64/12), (9/72/12), ..}
        if (!this.rfc1521compliant) rfc1521compliantLength -= in.length % 3 == 2 ? 1 : in.length % 3 == 1 ? 2 : 0; // non-compliant are shorter (!)
        return ASCII.String(this.encodeSubstring(in, rfc1521compliantLength));
    }

    public final byte[] encodeSubstring(final byte[] in, final int sublen) {
        if (in.length == 0) return new byte[0];
        assert sublen <= ((in.length + 2) / 3) * 4 : "sublen = " + sublen + ", expected: " + ((in.length + 2) / 3) * 4;
        final byte[] out = new byte[sublen];
        int writepos = 0;
        int pos = 0;
        long l;
        while (in.length - pos >= 3 && writepos < sublen) {
            l = ((((0XffL & in[pos++]) << 8) | (0XffL & in[pos++])) << 8) | (0XffL & in[pos++]);
            this.encodeLong(l, out, writepos, 4);
            writepos += 4;
        }
        // now there may be remaining bytes
        if (in.length % 3 != 0 && writepos < sublen) {
            if (in.length % 3 == 2) {
                long c = (((0XffL & in[pos]) << 8) + (0XffL & in[pos + 1])) << 2;
                out[writepos + 2] = this.alpha[(byte) (c & 0x3F)]; c >>= 6;
                out[writepos + 1] = this.alpha[(byte) (c & 0x3F)]; c >>= 6;
                out[writepos    ] = this.alpha[(byte) (c & 0x3F)]; c >>= 6;
                writepos += 3;
                if (this.rfc1521compliant && writepos < sublen) out[writepos++] = '=';
            } else {
                long c = (0XffL & in[pos]) << 4;
                out[writepos + 1] = this.alpha[(byte) (c & 0x3F)]; c >>= 6;
                out[writepos    ] = this.alpha[(byte) (c & 0x3F)]; c >>= 6;
                writepos += 2;
                if (this.rfc1521compliant) {if (writepos < sublen) out[writepos++] = '='; if (writepos < sublen) out[writepos++] = '=';}
            }
        }
        return out;
    }

    public final String decodeString(final String in) {
        return UTF8.String(this.decode(in));
    }

    final static Pattern cr = Pattern.compile("\n");
    public final byte[] decode(String in) {
        if ((in == null) || (in.isEmpty())) return new byte[0];
        in = cr.matcher(in).replaceAll("");
        try {
            int posIn = 0;
            int posOut = 0;
            if (this.rfc1521compliant) while (in.charAt(in.length() - 1) == '=') in = in.substring(0, in.length() - 1);
            final byte[] out = new byte[in.length() / 4 * 3 + (((in.length() % 4) == 0) ? 0 : in.length() % 4 - 1)];
            long l;
            while (posIn + 3 < in.length()) {
                l = this.decodeLong(in.substring(posIn, posIn + 4));
                out[posOut + 2] = (byte) (l % 256);
                l = l / 256;
                out[posOut + 1] = (byte) (l % 256);
                l = l / 256;
                out[posOut] = (byte) (l % 256);
                l = l / 256;
                posIn += 4;
                posOut += 3;
            }
            if (posIn < in.length()) {
                if (in.length() - posIn == 3) {
                    l = this.decodeLong(in.substring(posIn) + "A");
                    l = l / 256;
                    out[posOut + 1] = (byte) (l % 256);
                    l = l / 256;
                    out[posOut] = (byte) (l % 256);
                    l = l / 256;
                } else {
                    l = this.decodeLong(in.substring(posIn) + "AA");
                    l = l / 256 / 256;
                    out[posOut] = (byte) (l % 256);
                    l = l / 256;
                }
            }
            return out;
        } catch (final ArrayIndexOutOfBoundsException e) {
            // maybe the input was not base64
            // TODO: Throw exception again
            // throw new RuntimeException("input probably not base64");
            System.err.println("wrong string receive: " + in);
            return new byte[0];
        }
    }

    private final long cardinalI(final String key) {
        // returns a cardinal number in the range of 0 .. Long.MAX_VALUE
        long c = 0;
        int p = 0;
        byte b;
        while ((p < 10) && (p < key.length())) {
            b = this.ahpla[key.charAt(p++)];
            if (b < 0) return -1;
            c = (c << 6) | b;
        }
        while (p++ < 10) c = (c << 6);
        c = (c << 3) | 7;
        assert c >= 0;
        return c;
    }

    private final long cardinalI(final byte[] key, int off, final int len) {
        // returns a cardinal number in the range of 0 .. Long.MAX_VALUE
        long c = 0;
        final int lim = off + Math.min(10, len);
        final int lim10 = off + 10;
        byte b;
        while (off < lim) {
            b = key[off++];
            if (b < 0) return -1;
            b = this.ahpla[b];
            if (b < 0) return -1;
            c = (c << 6) | b;
        }
        while (off++ < lim10) c = (c << 6);
        c = (c << 3) | 7;
        assert c >= 0;
        return c;
    }

    public final byte[] uncardinal(long c) {
        c = c >> 3;
        final byte[] b = new byte[12];
        for (int p = 9; p >= 0; p--) {
            b[p] = this.alpha[(int) (c & 0x3fL)];
            c = c >> 6;
        }
        b[10] = this.alpha[0x3f];
        b[11] = this.alpha[0x3f];
        return b;
    }

    @Override
    public final long cardinal(final byte[] key) {
        if (this.zero == null) return this.cardinalI(key, 0, key.length);
        final long zeroCardinal = this.cardinalI(this.zero, 0, this.zero.length);
        final long keyCardinal = this.cardinalI(key, 0, key.length);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }

    @Override
    public final long cardinal(final byte[] key, final int off, final int len) {
        if (this.zero == null) return this.cardinalI(key, off, len);
        final long zeroCardinal = this.cardinalI(this.zero, 0, this.zero.length);
        final long keyCardinal = this.cardinalI(key, off, len);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }

    public final long cardinal(final String key) {
        if (this.zero == null) return this.cardinalI(key);
        final long zeroCardinal = this.cardinalI(this.zero, 0, this.zero.length);
        final long keyCardinal = this.cardinalI(key);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }

    private static final int sig(final int x) {
        return (x > 0) ? 1 : (x < 0) ? -1 : 0;
    }

    @Override
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

    @Override
    public final boolean equal(final byte[] a, int astart, final byte[] b, int bstart, int length) {
        if ((a == null) && (b == null)) return true;
        if ((a == null) || (b == null)) return false;
        while (length-- != 0) {
            if (a[astart++] != b[bstart++]) return false;
        }
        return true;
    }

    @Override
    public final int compare(final byte[] a, final byte[] b) {
        try {
        return (this.asc) ?
                ((this.zero == null) ? this.compares(a, b) : this.compare0(a, b, a.length))
                :
                ((this.zero == null) ? this.compares(b, a) : this.compare0(b, a, a.length));
        } catch (final Throwable e) {
            // if a or b is not well-formed, an ArrayIndexOutOfBoundsException may occur
            // in that case we don't want that the exception makes databse functions
            // unusable and effective creates a showstopper. In such cases we apply
            // a different order on the objects and treat not well-formed objects
            // as bigger as all others. If both object are not well-formed, they are
            // compared with the natural order.
            final boolean wfa = this.wellformed(a);
            final boolean wfb = this.wellformed(b);
            if (wfa && wfb) {
                // uh strange. throw the exception
                if (e instanceof ArrayIndexOutOfBoundsException) throw (ArrayIndexOutOfBoundsException) e;
                throw new RuntimeException(e.getMessage());
            }
            if (wfa) return (this.asc) ? -1 :  1;
            if (wfb) return (this.asc) ?  1 : -1;
            return ((this.asc) ? 1 : -1) * NaturalOrder.naturalOrder.compare(a, b);
        }
    }

    @Override
    public final int compare(final byte[] a, final byte[] b, final int length) {
        try {
            return (this.asc) ?
                    this.compare0(a, b, length)
                    :
                    this.compare0(b, a, length);
        } catch (final Throwable e) {
            // same handling as in simple compare method above
            final boolean wfa = this.wellformed(a, 0, length);
            final boolean wfb = this.wellformed(b, 0, length);
            if (wfa && wfb) {
                // uh strange. throw the exception
                if (e instanceof ArrayIndexOutOfBoundsException) throw (ArrayIndexOutOfBoundsException) e;
                throw new RuntimeException(e.getMessage());
            }
            if (wfa) return (this.asc) ? -1 :  1;
            if (wfb) return (this.asc) ?  1 : -1;
            return ((this.asc) ? 1 : -1) * NaturalOrder.naturalOrder.compare(a, b, length);
        }
    }

    @Override
    public final int compare(final byte[] a, final int aoffset, final byte[] b, final int boffset, final int length) {
        try {
            return (this.asc) ?
                    this.compare0(a, aoffset, b, boffset, length)
                    :
                    this.compare0(b, boffset, a, aoffset, length);
        } catch (final Throwable e) {
            // same handling as in simple compare method above
            final boolean wfa = this.wellformed(a, aoffset, length);
            final boolean wfb = this.wellformed(b, boffset, length);
            if (wfa && wfb) {
                // uh strange. throw the exception
                if (e instanceof ArrayIndexOutOfBoundsException) throw (ArrayIndexOutOfBoundsException) e;
                throw new RuntimeException(e.getMessage());
            }
            if (wfa) return (this.asc) ? -1 :  1;
            if (wfb) return (this.asc) ?  1 : -1;
            return ((this.asc) ? 1 : -1) * NaturalOrder.naturalOrder.compare(a, aoffset, b, boffset, length);
        }
    }

    private final int compare0(final byte[] a, final byte[] b, int length) {
        if (this.zero == null) return this.compares(a, b, length);

        // we have an artificial start point. check all combinations
        if (this.zero.length < length) length = this.zero.length;
        final int az = this.compares(a, this.zero, length); // -1 if a < z; 0 if a == z; 1 if a > z
        final int bz = this.compares(b, this.zero, length); // -1 if b < z; 0 if b == z; 1 if b > z
        if (az == bz) return this.compares(a, b, length);
        return sig(az - bz);
    }

    private final int compare0(final byte[] a, final int aoffset, final byte[] b, final int boffset, int length) {
        if (this.zero == null) return this.compares(a, aoffset, b, boffset, length);

        // we have an artificial start point. check all combinations
        if (this.zero.length < length) length = this.zero.length;
        final int az = this.compares(a, aoffset, this.zero, 0, length); // -1 if a < z; 0 if a == z; 1 if a > z
        final int bz = this.compares(b, boffset, this.zero, 0, length); // -1 if b < z; 0 if b == z; 1 if b > z
        if (az == bz) return this.compares(a, aoffset, b, boffset, length);
        return sig(az - bz);
    }

    private final int compares(final byte[] a, final byte[] b) {
        //assert (this.ahpla.length == 128);
        short i = 0;
        final int al = a.length;
        final int bl = b.length;
        final short ml = (short) Math.min(al, bl);
        byte ac, bc;
        while (i < ml) { // trace point
            //assert (i < a.length) : "i = " + i + ", aoffset = " + 0 + ", a.length = " + a.length + ", a = " + NaturalOrder.arrayList(a, 0, al);
            //assert (i < b.length) : "i = " + i + ", boffset = " + 0 + ", b.length = " + b.length + ", b = " + NaturalOrder.arrayList(b, 0, al);
            ac = a[i];
            //assert (ac >= 0) && (ac < 128) : "ac = " + ac + ", a = " + NaturalOrder.arrayList(a, 0, al);
            bc = b[i];
            //assert (bc >= 0) && (bc < 128) : "bc = " + bc + ", b = " + NaturalOrder.arrayList(b, 0, al);
            //assert ac != 0;
            //assert bc != 0;
            if (ac != bc) return this.ab[(ac << 7) | bc];
            i++;
        }
        // compare length
        if (al > bl) return 1;
        if (al < bl) return -1;
        // they are equal
        return 0;
    }

    private final int compares(final byte[] a, final byte[] b, final int length) {
        //assert (length <= a.length) : "a.length = " + a.length + ", alength = " + length;
        //assert (length <= b.length) : "b.length = " + b.length + ", blength = " + length;
        //assert (this.ahpla.length == 128);
        short i = 0;
        byte ac, bc;
        while (i < length) {
            //assert (i < a.length) : "i = " + i + ", a.length = " + a.length + ", a = " + NaturalOrder.arrayList(a, 0, length);
            //assert (i < b.length) : "i = " + i + ", b.length = " + b.length + ", b = " + NaturalOrder.arrayList(b, 0, length);
            ac = a[i];
            //assert (ac >= 0) && (ac < 128) : "ac = " + ac + ", a = " + NaturalOrder.arrayList(a, 0, length);
            bc = b[i];
            //assert (bc >= 0) && (bc < 128) : "bc = " + bc + ", b = " + NaturalOrder.arrayList(b, 0, length);
            //assert ac != 0;
            //assert bc != 0;
            if (ac != bc) return this.ab[(ac << 7) | bc];
            i++;
        }
        // they are equal
        return 0;
    }

    private final int compares(final byte[] a, final int aoffset, final byte[] b, final int boffset, final int length) {
        //assert (aoffset + length <= a.length) : "a.length = " + a.length + ", aoffset = " + aoffset + ", alength = " + length;
        //assert (boffset + length <= b.length) : "b.length = " + b.length + ", boffset = " + boffset + ", blength = " + length;
        //assert (this.ahpla.length == 128);
        short i = 0;
        byte ac, bc;
        while (i < length) {
            //assert (i + aoffset < a.length) : "i = " + i + ", aoffset = " + aoffset + ", a.length = " + a.length + ", a = " + NaturalOrder.arrayList(a, aoffset, length);
            //assert (i + boffset < b.length) : "i = " + i + ", boffset = " + boffset + ", b.length = " + b.length + ", b = " + NaturalOrder.arrayList(b, boffset, length);
            ac = a[aoffset + i];
            //assert (ac >= 0) && (ac < 128) : "ac = " + ac + ", a = " + NaturalOrder.arrayList(a, aoffset, length);
            bc = b[boffset + i];
            //assert (bc >= 0) && (bc < 128) : "bc = " + bc + ", b = " + NaturalOrder.arrayList(b, boffset, length);
            //assert ac != 0;
            //assert bc != 0;
            if (ac != bc) return this.ab[(ac << 7) | bc];
            i++;
        }
        // they are equal
        return 0;
    }

    public static void main(final String[] s) {
        // java -classpath classes de.anomic.kelondro.kelondroBase64Order
        final Base64Order b64 = new Base64Order(true, true);
        if (s.length == 0) {
            System.out.println("usage: -[ec|dc|es|ds|clcn] <arg>");
            System.exit(0);
        }
        if ("-ec".equals(s[0])) {
            // generate a b64 encoding from a given cardinal
            System.out.println(b64.encodeLongSB(Long.parseLong(s[1]), 4));
        }
        if ("-dc".equals(s[0])) {
            // generate a b64 decoding from a given cardinal
            System.out.println(b64.decodeLong(s[1]));
        }
        if ("-es".equals(s[0])) {
            // generate a b64 encoding from a given string
            System.out.println(b64.encodeString(s[1]));
        }
        if ("-ds".equals(s[0])) {
            // generate a b64 decoding from a given string
            System.out.println(b64.decodeString(s[1]));
        }
        if ("-cl".equals(s[0])) {
            // return the cardinal of a given string as long value with the enhanced encoder
            System.out.println(Base64Order.enhancedCoder.cardinal(s[1].getBytes()));
        }
        if ("-cn".equals(s[0])) {
            // return the cardinal of a given string as normalized float 0 .. 1 with the enhanced encoder
            System.out.println(((double) Base64Order.enhancedCoder.cardinal(s[1].getBytes())) / ((double) Long.MAX_VALUE));
        }
        if ("-test".equals(s[0])) {
            System.out.println("Pid: " + ManagementFactory.getRuntimeMXBean().getName());
            // do some checks
            final Random r = new Random(0); // not real random to be able to reproduce the test

            try {
                // use the class loader to call sun.misc.BASE64Encoder, the sun base64 encoder
                // we do not instantiate that class here directly since that provokes a
                // "warning: sun.misc.BASE64Encoder is internal proprietary API and may be removed in a future release"

                final Class<?> rfc1521Decoder_class = Class.forName("sun.misc.BASE64Decoder");
                final Object rfc1521Decoder = rfc1521Decoder_class.getDeclaredConstructor().newInstance();
                final Method rfc1521Decoder_decodeBuffer = rfc1521Decoder_class.getMethod("decodeBuffer", String.class);
                final Class<?> rfc1521Encoder_class = Class.forName("sun.misc.BASE64Encoder");
                final Object rfc1521Encoder = rfc1521Encoder_class.getDeclaredConstructor().newInstance();
                final Method rfc1521Encoder_encode = rfc1521Encoder_class.getMethod("encode", byte[].class);

                System.out.println("preparing tests..");
                // prepare challenges and results with rfc1521Encoder
                final int count = 100000;
                final String[] challenges = new String[count];
                final String[] rfc1521Encoded = new String[count];
                for (int i = 0; i < count; i++) {
                    final int len = r.nextInt(10000);
                    final StringBuilder challenge = new StringBuilder(len);
                    for (int j = 0; j < len; j++) challenge.append((char) (32 + r.nextInt(64)));
                    challenges[i] = challenge.toString();
                    rfc1521Encoded[i] = (String) rfc1521Encoder_encode.invoke(rfc1521Encoder, UTF8.getBytes(challenges[i]));
                }

                // starting tests
                final long start = System.currentTimeMillis();
                for (final boolean rfc1521Compliant: new boolean[]{false, true}) {
                    System.out.println("starting tests, rfc1521Compliant = " + rfc1521Compliant + " ...");
                    String eb64, rfc1521;
                    // encode with enhancedCoder, decode with standard RFC 1521 base64
                    for (int i = 0; i < count; i++) {
                        if (rfc1521Compliant) {
                            rfc1521 = Base64Order.standardCoder.encode(UTF8.getBytes(challenges[i]));
                        } else {
                            eb64 = Base64Order.enhancedCoder.encode(UTF8.getBytes(challenges[i]));
                            rfc1521 = new String(eb64);
                            while (rfc1521.length() % 4 != 0) rfc1521 += "=";
                            rfc1521 = rfc1521.replace('-', '+').replace('_', '/');
                        }
                        final String rfc1521Decoded = UTF8.String((byte[]) rfc1521Decoder_decodeBuffer.invoke(rfc1521Decoder, rfc1521));
                        if (!rfc1521Decoded.equals(challenges[i])) System.out.println("Encode enhancedB64 + Decode RFC1521: Fail for " + challenges[i]);
                    }

                    // encode with enhancedCoder, decode with standard RFC 1521 base64
                    // sun.misc.BASE64Encoder rfc1521Encoder = new sun.misc.BASE64Encoder();
                    for (int i = 0; i < count; i++) {
                        // encode with enhancedCoder, decode with standard RFC 1521 base64
                        rfc1521 = new String(rfc1521Encoded[i]);
                        if (rfc1521Compliant) {
                            final String standardCoderDecoded = UTF8.String(Base64Order.standardCoder.decode(rfc1521));
                            if (!standardCoderDecoded.equals(challenges[i])) System.out.println("Encode RFC1521 + Decode enhancedB64: Fail for " + rfc1521);
                        } else {
                            eb64 = new String(rfc1521);
                            while (eb64.endsWith("=")) eb64 = eb64.substring(0, eb64.length() - 1);
                            eb64 = eb64.replace('+', '-').replace('/', '_');
                            final String enhancedCoderDecoded = UTF8.String(Base64Order.enhancedCoder.decode(eb64));
                            if (!enhancedCoderDecoded.equals(challenges[i])) System.out.println("Encode RFC1521 + Decode enhancedB64: Fail for " + eb64);
                        }
                    }
                }
                final long time = System.currentTimeMillis() - start;
                System.out.println("time: " + (time / 1000) + " seconds, " + (1000 * time / count) + " ms / 1000 steps");
            } catch (final Throwable e) {
                e.printStackTrace();
            }

        }
    }
}
