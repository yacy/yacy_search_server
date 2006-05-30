// kelondroBase64Order.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 03.01.2006
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

public class kelondroBase64Order extends kelondroAbstractOrder implements kelondroOrder, kelondroCoding, Comparator {

    private static final char[] alpha_standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final char[] alpha_enhanced = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
    private static final byte[] ahpla_standard = new byte[256];
    private static final byte[] ahpla_enhanced = new byte[256];
    
    static {
        for (int i = 0; i < 256; i++) {
            ahpla_standard[i] = -1;
            ahpla_enhanced[i] = -1;
        }
        for (int i = 0; i < alpha_standard.length; i++) {
            ahpla_standard[alpha_standard[i]] = (byte) i;
            ahpla_enhanced[alpha_enhanced[i]] = (byte) i;
        }
    }
    
    public static final kelondroBase64Order standardCoder = new kelondroBase64Order(true, true);
    public static final kelondroBase64Order enhancedCoder = new kelondroBase64Order(true, false);

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
    }

    public Object clone() {
        kelondroBase64Order o = new kelondroBase64Order(this.asc, this.rfc1113compliant);
        o.rotate(this.zero);
        return o;
    }
    
    public static kelondroOrder bySignature(String signature) {
        if (signature.equals("Bd")) return new kelondroBase64Order(false, false);
        if (signature.equals("bd")) return new kelondroBase64Order(false, true);
        if (signature.equals("Bu")) return new kelondroBase64Order(true, false);
        if (signature.equals("bu")) return new kelondroBase64Order(true, true);
        return null;
    }
    
    public String signature() {
        if ((!asc) && (!rfc1113compliant)) return "Bd";
        if ((!asc) && ( rfc1113compliant)) return "bd";
        if (( asc) && (!rfc1113compliant)) return "Bu";
        if (( asc) && ( rfc1113compliant)) return "bu";
        return null;
    }
    
    public char encodeByte(byte b) {
        return (char) alpha[b];
    }

    public byte decodeByte(char b) {
        return ahpla[b];
    }

    public String encodeLongSmart(long c, int length) {
        if (c >= max(length)) {
            StringBuffer s = new StringBuffer(length);
            s.setLength(length);
            while (length > 0) s.setCharAt(--length, alpha[63]);
            return s.toString();
        } else {
            return encodeLong(c, length);
        }
    }

    public String encodeLong(long c, int length) {
        StringBuffer s = new StringBuffer(length);
        s.setLength(length);
        while (length > 0) {
            s.setCharAt(--length, alpha[(byte) (c & 0x3F)]);
            c >>= 6;
        }
        return s.toString();
    }

    public long decodeLong(String s) {
        while (s.endsWith("=")) s = s.substring(0, s.length() - 1);
        long c = 0;
        for (int i = 0; i < s.length(); i++) c = (c << 6) | ahpla[s.charAt(i)];
        return c;
    }

    public long decodeLong(byte[] s, int offset, int length) {
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

    public String encodeString(String in) {
        return encode(in.getBytes());
    }

    // we will use this encoding to encode strings with 2^8 values to
    // b64-Strings
    // we will do that by grouping each three input bytes to four output bytes.
    public String encode(byte[] in) {
        StringBuffer out = new StringBuffer(in.length / 3 * 4 + 3);
        int pos = 0;
        long l;
        while (in.length - pos >= 3) {
            l = ((((0XffL & (long) in[pos]) << 8) + (0XffL & (long) in[pos + 1])) << 8) + (0XffL & (long) in[pos + 2]);
            pos += 3;
            out = out.append(encodeLong(l, 4));
        }
        // now there may be remaining bytes
        if (in.length % 3 != 0)
            out = out.append((in.length % 3 == 2) ? encodeLong((((0XffL & (long) in[pos]) << 8) + (0XffL & (long) in[pos + 1])) << 8, 4).substring(0, 3) : encodeLong((((0XffL & (long) in[pos])) << 8) << 8, 4).substring(0, 2));
        if (rfc1113compliant)
            while (out.length() % 4 > 0)
                out.append("=");
        // return result
        return out.toString();
    }

    public String decodeString(String in) {
        try {
            //return new String(decode(in), "ISO-8859-1");
            return new String(decode(in), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("internal error in base64: " + e.getMessage());
            return null;
        }
    }

    public byte[] decode(String in) {
        try {
            int posIn = 0;
            int posOut = 0;
            if (rfc1113compliant)
                while (in.charAt(in.length() - 1) == '=')
                    in = in.substring(0, in.length() - 1);
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
            throw new RuntimeException("input probably not base64");
        }
    }

    private long cardinalI(byte[] key) {
        // returns a cardinal number in the range of 0 .. Long.MAX_VALUE
        long c = 0;
        int p = 0;
        while ((p < 10) && (p < key.length)) c = (c << 6) | ahpla[key[p++]];
        while (p++ < 10) c = (c << 6);
        c = c << 3;
        return c;
    }

    public long cardinal(byte[] key) {
        if (this.zero == null) return cardinalI(key);
        long zeroCardinal = cardinalI(this.zero);
        long keyCardinal = cardinalI(key);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal + 1;
    }
    
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
    
    public int compares(byte[] a, byte[] b) {
        int i = 0;
        final int al = a.length;
        final int bl = b.length;
        final int len = (al > bl) ? bl : al;
        while (i < len) {
            if (ahpla[a[i]] > ahpla[b[i]]) return 1;
            if (ahpla[a[i]] < ahpla[b[i]]) return -1;
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
            System.out.println(b64.decodeString(s[1]));
        }
    }

    
}
