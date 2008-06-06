// kelondroBase64Order.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 03.01.2006
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

import java.io.UnsupportedEncodingException;
import java.util.Comparator;

import de.anomic.server.logging.serverLog;

public class kelondroBase64Order extends kelondroAbstractOrder<byte[]> implements kelondroByteOrder, kelondroCoding, Comparator<byte[]> {

    protected static final char[] alpha_standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    protected static final char[] alpha_enhanced = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
    protected static final byte[] ahpla_standard = new byte[128];
    protected static final byte[] ahpla_enhanced = new byte[128];

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

    private final serverLog log;

    public static final kelondroBase64Order standardCoder = new kelondroBase64Order(true, true);
    public static final kelondroBase64Order enhancedCoder = new kelondroBase64Order(true, false);
    public static final Comparator<String> standardComparator = new kelondroByteOrder.StringOrder(standardCoder);
    public static final Comparator<String> enhancedComparator = new kelondroByteOrder.StringOrder(enhancedCoder);

    private boolean rfc1113compliant;
    private final char[] alpha;
    private final byte[] ahpla;

    public kelondroBase64Order(boolean up, boolean rfc1113compliant) {
        // if we choose not to be rfc1113compliant,
        // then we get shorter base64 results which are also filename-compatible
        this.rfc1113compliant = rfc1113compliant;
        this.asc = up;
        alpha = (rfc1113compliant) ? alpha_standard : alpha_enhanced;
        ahpla = (rfc1113compliant) ? ahpla_standard : ahpla_enhanced;

        this.log = new serverLog("BASE64");
    }

    public static byte[] zero(int length) {
        byte[] z = new byte[length];
        while (length > 0) { length--; z[length] = (byte) alpha_standard[0]; }
        return z;
    }
    
    public kelondroOrder<byte[]> clone() {
        kelondroBase64Order o = new kelondroBase64Order(this.asc, this.rfc1113compliant);
        o.rotate(zero);
        return o;
    }
    
    public final boolean wellformed(byte[] a) {
        return wellformed(a, 0, a.length);
    }
    
    public final boolean wellformed(byte[] a, int astart, int alength) {
        assert (astart + alength <= a.length) : "astart = " + astart + ", alength = " + alength + ", a.length = " + a.length;
        int b;
        for (int i = astart + alength - 1; i >= astart; i--) {
            b = a[i];
            if ((b < 0) || (b >= 128) || (ahpla[b] == -1)) return false;
        }
        return true;
    }
    
    public final static kelondroByteOrder bySignature(String signature) {
        if (signature.equals("Bd")) return new kelondroBase64Order(false, false);
        if (signature.equals("bd")) return new kelondroBase64Order(false, true);
        if (signature.equals("Bu")) return new kelondroBase64Order(true, false);
        if (signature.equals("bu")) return new kelondroBase64Order(true, true);
        return null;
    }
    
    public final String signature() {
        if ((!asc) && (!rfc1113compliant)) return "Bd";
        if ((!asc) && ( rfc1113compliant)) return "bd";
        if (( asc) && (!rfc1113compliant)) return "Bu";
        if (( asc) && ( rfc1113compliant)) return "bu";
        return null;
    }
    
    public final char encodeByte(byte b) {
        return alpha[b];
    }

    public final byte decodeByte(char b) {
        return ahpla[b];
    }

    public final String encodeLongSmart(long c, int length) {
        if (c >= max(length)) {
            StringBuffer s = new StringBuffer(length);
            s.setLength(length);
            while (length > 0) s.setCharAt(--length, alpha[63]);
            return new String(s);
        }
        return encodeLong(c, length);
    }

    public final String encodeLong(long c, int length) {
        StringBuffer s = new StringBuffer(length);
        s.setLength(length);
        while (length > 0) {
            s.setCharAt(--length, alpha[(byte) (c & 0x3F)]);
            c >>= 6;
        }
        return new String(s);
    }

    public final void encodeLong(long c, byte[] b, int offset, int length) {
        assert offset + length <= b.length;
        while (length > 0) {
            b[--length + offset] = (byte) alpha[(byte) (c & 0x3F)];
            c >>= 6;
        }
    }
    
    public final long decodeLong(String s) {
        while (s.endsWith("=")) s = s.substring(0, s.length() - 1);
        long c = 0;
        for (int i = 0; i < s.length(); i++) c = (c << 6) | ahpla[s.charAt(i)];
        return c;
    }

    public final long decodeLong(byte[] s, int offset, int length) {
        while ((length > 0) && (s[offset + length - 1] == '=')) length--;
        long c = 0;
        for (int i = 0; i < length; i++) c = (c << 6) | ahpla[s[offset + i]];
        return c;
    }

    public static long max(int len) {
        // computes the maximum number that can be coded with a base64-encoded
        // String of base len
        long c = 0;
        for (int i = 0; i < len; i++) c = (c << 6) | 63;
        return c;
    }

    public final String encodeString(String in) {
        try {
            return encode(in.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    // we will use this encoding to encode strings with 2^8 values to
    // b64-Strings
    // we will do that by grouping each three input bytes to four output bytes.
    public final String encode(byte[] in) {
        if (in.length == 0) return "";
        StringBuffer out = new StringBuffer(in.length / 3 * 4 + 3);
        int pos = 0;
        long l;
        while (in.length - pos >= 3) {
            l = ((((0XffL & in[pos]) << 8) + (0XffL & in[pos + 1])) << 8) + (0XffL & in[pos + 2]);
            pos += 3;
            out = out.append(encodeLong(l, 4));
        }
        // now there may be remaining bytes
        if (in.length % 3 != 0) out = out.append((in.length % 3 == 2) ? encodeLong((((0XffL & in[pos]) << 8) + (0XffL & in[pos + 1])) << 8, 4).substring(0, 3) : encodeLong((((0XffL & in[pos])) << 8) << 8, 4).substring(0, 2));
        if (rfc1113compliant) while (out.length() % 4 > 0) out.append("=");
        // return result
        return new String(out);
    }

    public final String decodeString(String in, String info) {
        try {
            //return new String(decode(in), "ISO-8859-1");
            return new String(decode(in, info), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("internal error in base64: " + e.getMessage());
            return null;
        }
    }

    public final byte[] decode(String in, String info) {
        if ((in == null) || (in.length() == 0)) return new byte[0];
        try {
            int posIn = 0;
            int posOut = 0;
            if (rfc1113compliant) while (in.charAt(in.length() - 1) == '=') in = in.substring(0, in.length() - 1);
            byte[] out = new byte[in.length() / 4 * 3 + (((in.length() % 4) == 0) ? 0 : in.length() % 4 - 1)];
            long l;
            while (posIn + 3 < in.length()) {
                l = decodeLong(in.substring(posIn, posIn + 4));
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
                    l = decodeLong(in.substring(posIn) + "A");
                    l = l / 256;
                    out[posOut + 1] = (byte) (l % 256);
                    l = l / 256;
                    out[posOut] = (byte) (l % 256);
                    l = l / 256;
                } else {
                    l = decodeLong(in.substring(posIn) + "AA");
                    l = l / 256 / 256;
                    out[posOut] = (byte) (l % 256);
                    l = l / 256;
                }
            }
            return out;
        } catch (ArrayIndexOutOfBoundsException e) {
            // maybe the input was not base64
            // throw new RuntimeException("input probably not base64");
            if (this.log.isFine()) this.log.logFine("wrong string receive: " + in + ", call: " + info);
            return new byte[0];
        }
    }

    private final long cardinalI(byte[] key) {
        // returns a cardinal number in the range of 0 .. Long.MAX_VALUE
        long c = 0;
        int p = 0;
        while ((p < 10) && (p < key.length)) c = (c << 6) | ahpla[key[p++]];
        while (p++ < 10) c = (c << 6);
        c = c << 3;
        return c;
    }

    public final long cardinal(byte[] key) {
        if (this.zero == null) return cardinalI(key);
        long zeroCardinal = cardinalI(this.zero);
        long keyCardinal = cardinalI(key);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }
    
    private static final int sig(int x) {
        return (x > 0) ? 1 : (x < 0) ? -1 : 0;
    }
    
    public final int compare(byte[] a, byte[] b) {
        return (asc) ? compare0(a, 0, a.length, b, 0, b.length) : compare0(b, 0, b.length, a, 0, a.length);
    }

    public final int compare(byte[] a, int aoffset, int alength, byte[] b, int boffset, int blength) {
        return (asc) ? compare0(a, aoffset, alength, b, boffset, blength) : compare0(b, boffset, blength, a, aoffset, alength);
    }
    
    public final int compare0(byte[] a, int aoffset, int alength, byte[] b, int boffset, int blength) {
        if (zero == null) return compares(a, aoffset, alength, b, boffset, blength);
        // we have an artificial start point. check all combinations
        int az = compares(a, aoffset, alength, zero, 0, Math.min(alength, zero.length)); // -1 if a < z; 0 if a == z; 1 if a > z
        int bz = compares(b, boffset, blength, zero, 0, Math.min(blength, zero.length)); // -1 if b < z; 0 if b == z; 1 if b > z
        if (az == bz) return compares(a, aoffset, alength, b, boffset, blength);
        return sig(az - bz);
    }
    
    public final int compares(byte[] a, int aoffset, int alength, byte[] b, int boffset, int blength) {
        assert (aoffset + alength <= a.length) : "a.length = " + a.length + ", aoffset = " + aoffset + ", alength = " + alength;
        assert (boffset + blength <= b.length) : "b.length = " + b.length + ", boffset = " + boffset + ", blength = " + blength;
        assert (ahpla.length == 128);
        int i = 0;
        final int al = Math.min(alength, a.length - aoffset);
        final int bl = Math.min(blength, b.length - boffset);
        byte ac, bc;
        byte acc, bcc;
        while ((i < al) && (i < bl)) {
            assert (i + aoffset < a.length) : "i = " + i + ", aoffset = " + aoffset + ", a.length = " + a.length + ", a = " + serverLog.arrayList(a, aoffset, al);
            assert (i + boffset < b.length) : "i = " + i + ", boffset = " + boffset + ", b.length = " + b.length + ", b = " + serverLog.arrayList(b, boffset, al);
            ac = a[aoffset + i];
            assert (ac >= 0) && (ac < 128) : "ac = " + ac + ", a = " + serverLog.arrayList(a, aoffset, al);
            bc = b[boffset + i];
            if ((ac == 0) && (bc == 0)) return 0; // zero-terminated length
            assert (bc >= 0) && (bc < 128) : "bc = " + bc + ", b = " + serverLog.arrayList(b, boffset, al);
            acc = ahpla[ac];
            assert (acc >= 0) : "acc = " + acc + ", a = " + serverLog.arrayList(a, aoffset, al) + "/" + new String(a, aoffset, al) + ", aoffset = 0x" + Integer.toHexString(aoffset) + ", i = " + i + "\n" + serverLog.table(a, 16, aoffset);
            bcc = ahpla[bc];
            assert (bcc >= 0) : "bcc = " + bcc + ", b = " + serverLog.arrayList(b, boffset, al) + "/" + new String(b, boffset, al) + ", boffset = 0x" + Integer.toHexString(boffset) + ", i = " + i + "\n" + serverLog.table(b, 16, boffset);
            if (acc > bcc) return 1;
            if (acc < bcc) return -1;
            // else the bytes are equal and it may go on yet undecided
            i++;
        }
        // compare length
        if (al > bl) return 1;
        if (al < bl) return -1;
        // they are equal
        return 0;
    }
    
    public final int comparePivot(byte[] compiledPivot, byte[] b, int boffset, int blength) {
        assert zero == null;
        assert asc;
        assert (boffset + blength <= b.length) : "b.length = " + b.length + ", boffset = " + boffset + ", blength = " + blength;
        int i = 0;
        final int bl = Math.min(blength, b.length - boffset);
        byte acc, bcc;
        while ((i < compiledPivot.length) && (i < bl)) {
            acc = compiledPivot[i];
            bcc = ahpla[b[boffset + i]];
            if (acc > bcc) return 1;
            if (acc < bcc) return -1;
            // else the bytes are equal and it may go on yet undecided
            i++;
        }
        // compare length
        if (compiledPivot.length > bl) return 1;
        if (compiledPivot.length < bl) return -1;
        // they are equal
        return 0;
    }
    
    public final byte[] compilePivot(byte[] a, int aoffset, int alength) {
        assert (aoffset + alength <= a.length) : "a.length = " + a.length + ", aoffset = " + aoffset + ", alength = " + alength;
        byte[] cp = new byte[Math.min(alength, a.length - aoffset)];
        for (int i = cp.length - 1; i >= 0; i--) {
            cp[i] = ahpla[a[aoffset + i]];
            assert cp[i] != -1;
        }
        return cp;
    }

    public static void main(String[] s) {
        kelondroBase64Order b64 = new kelondroBase64Order(true, true);
        if (s.length == 0) {
            System.out.println("usage: -[ec|dc|es|ds|s2m] <arg>");
            System.exit(0);
        }
        if (s[0].equals("-ec")) {
            // generate a b64 encoding from a given cardinal
            System.out.println(b64.encodeLong(Long.parseLong(s[1]), 4));
        }
        if (s[0].equals("-dc")) {
            // generate a b64 decoding from a given cardinal
            System.out.println(b64.decodeLong(s[1]));
        }
        if (s[0].equals("-es")) {
            // generate a b64 encoding from a given string
            System.out.println(b64.encodeString(s[1]));
        }
        if (s[0].equals("-ds")) {
            // generate a b64 decoding from a given string
            System.out.println(b64.decodeString(s[1], ""));
        }
    }
}
