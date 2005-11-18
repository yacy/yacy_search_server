// serverCodings.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 29.04.2004
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

package de.anomic.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.HashMap;
import java.util.StringTokenizer;


public final class serverCodings {

    // this provides encoding and decoding of long cardinals into a 6-bit - based number format
    // expressed by a string. This is probably the most compact form to encode numbers as strings.
    // the resulting string is filename-friendly, it contains no special character that is not
    // suitable for file names.

    public static final serverCodings standardCoder = new serverCodings(true);
    public static final serverCodings enhancedCoder = new serverCodings(false);

    final boolean rfc1113compliant;
    public final char[] alpha;
    public final byte[] ahpla;

    public serverCodings(boolean rfc1113compliant) {
	// if we choose not to be rfc1113compliant,
	// then we get shorter base64 results which are also filename-compatible
	this.rfc1113compliant = rfc1113compliant;
	alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
	if (!(rfc1113compliant)) {
	    alpha[62] = '-';
	    alpha[63] = '_';
	}
	ahpla = new byte[256];
	for (int i = 0; i < 256; i++) ahpla[i] = -1;
	for (int i = 0; i < alpha.length; i++) ahpla[alpha[i]] = (byte) i;
    }

    
    public char encodeBase64Byte(byte b) {
        return (char) alpha[b];
    }

    public byte decodeBase64Byte(char b) {
        return ahpla[b];
    }
    
    public String encodeBase64LongSmart(long c, int length) {
        if (c >= maxBase64(length)) {
            StringBuffer s = new StringBuffer(length);
            s.setLength(length);
            while (length > 0) {
                s.setCharAt(--length, alpha[0]);
            }
            return s.toString();
        } else {
            return encodeBase64Long(c, length);
        }
    }
    
    /*
    public String encodeBase64Long(long c, int length) {
        if (length < 0) length = 0;
        StringBuffer s = new StringBuffer(length); //String s = "";
        if (c == 0) {
            s.insert(0,alpha[0]); //s = alpha[0] + s;
        } else {
            while (c > 0) {
                s.insert(0,alpha[(byte) (c & 0x3F)]); //s = alpha[(byte) (c & 0x3F)] + s;
                c >>= 6;
            }
        }
        if ((length != 0) && (s.length() > length))
            throw new RuntimeException("encodeBase64 result '" + s + "' exceeds demanded length of " + length + " digits");
        if (length == 0) length = 1; // rare exception for the case that c == 0
        while (s.length() < length) s.insert(0,alpha[0]); //s = alpha[0] + s;
        return s.toString();
    }
    */
    
    public String encodeBase64Long(long c, int length) {
        StringBuffer s = new StringBuffer(length);
        s.setLength(length);
        while (length > 0) {
            s.setCharAt(--length, alpha[(byte) (c & 0x3F)]);
            c >>= 6;
        }
        return s.toString();
    }

    public long decodeBase64Long(String s) {
	while (s.endsWith("=")) s = s.substring(0, s.length() - 1);
	long c = 0;
	for (int i = 0; i < s.length(); i++) {
	    c <<= 6;
	    c += ahpla[s.charAt(i)];
	}
	return c;
    }

    public static long maxBase64(int len) {
        // computes the maximum number that can be coded with a base64-encoded String of base len
        long c = 0;
	for (int i = 0; i < len; i++) {
	    c <<= 6;
	    c += 63;
	}
	return c;
    }
    
    public String encodeBase64String(String in) {
	return encodeBase64(in.getBytes());
    }

    // we will use this encoding to encode strings with 2^8 values to b64-Strings
    // we will do that by grouping each three input bytes to four output bytes.
    public String encodeBase64(byte[] in) {
	StringBuffer out = new StringBuffer(in.length / 3 * 4 + 3);
	int pos = 0;
	long l;
	while (in.length - pos >= 3) {
	    l = ((((0XffL & (long) in[pos]) << 8) + (0XffL & (long) in[pos + 1])) << 8) + (0XffL & (long) in[pos + 2]);
	    pos += 3;
	    out = out.append(encodeBase64Long(l, 4));
	}
	// now there may be remaining bytes
	if (in.length % 3 != 0)
	    out = out.append(
		    (in.length % 3 == 2) ?
		    encodeBase64Long((((0XffL & (long) in[pos]) << 8) + (0XffL & (long) in[pos + 1])) << 8, 4).substring(0,3) :
		    encodeBase64Long((((0XffL & (long) in[pos])) << 8) << 8, 4).substring(0, 2));
	if (rfc1113compliant) while (out.length() % 4 > 0) out.append("=");
	// return result
	return out.toString();
    }

    public String decodeBase64String(String in) {
	try {
	    return new String(decodeBase64(in), "ISO-8859-1");
	} catch (java.io.UnsupportedEncodingException e) {
	    System.out.println("internal error in base64: " + e.getMessage());
	    return null;
	}
    }

    public byte[] decodeBase64(String in) {
	try {
	    int posIn = 0;
	    int posOut = 0;
	    if (rfc1113compliant) while (in.charAt(in.length() - 1) == '=') in = in.substring(0, in.length() - 1);
	    byte[] out = new byte[in.length() / 4 * 3 + (((in.length() % 4) == 0) ? 0 : in.length() % 4 - 1)];
	    long l;
	    char c1, c2, c3;
	    while (posIn + 3 < in.length()) {
		l = decodeBase64Long(in.substring(posIn, posIn + 4));
		out[posOut+2] = (byte) (l % 256); l = l / 256;
		out[posOut+1] = (byte) (l % 256); l = l / 256;
		out[posOut  ] = (byte) (l % 256); l = l / 256;
		posIn  += 4;
		posOut += 3;
	    }
	    if (posIn < in.length()) {
		if (in.length() - posIn == 3) {
		    l = decodeBase64Long(in.substring(posIn) + "A");
		    l = l / 256;
		    out[posOut+1] = (byte) (l % 256); l = l / 256;
		    out[posOut  ] = (byte) (l % 256); l = l / 256;
		} else {
		    l = decodeBase64Long(in.substring(posIn) + "AA");
		    l = l / 256 / 256;
		    out[posOut  ] = (byte) (l % 256); l = l / 256;
		}
	    }
	    return out;
	} catch (ArrayIndexOutOfBoundsException e) {
	    // maybe the input was not base64
            throw new RuntimeException("input probably not base64");
	}
    }
    
    public static String encodeHex(long in, int length) {
        String s = Long.toHexString(in);
        while (s.length() < length) s = "0" + s;
        return s;
    }
    
    public static String encodeHex(byte[] in) {
	if (in == null) return "";
	String result = "";
	for (int i = 0; i < in.length; i++)
	    result = result + (((0Xff & (int) in[i]) < 16) ? "0" : "") + Integer.toHexString(0Xff & (int) in[i]);
	return result;
    }

    public static byte[] decodeHex(String hex) {
	byte[] result = new byte[hex.length() / 2];
	for (int i = 0; i < result.length; i++) {
	    result[i] = (byte) (16 * Integer.parseInt(hex.charAt(i * 2) + "", 16) + Integer.parseInt(hex.charAt(i * 2 + 1) + "", 16));
	}
	return result;
    }

    public static String encodeMD5B64(String key, boolean enhanced) {
	if (enhanced)
	    return enhancedCoder.encodeBase64(encodeMD5Raw(key));
	else
	    return standardCoder.encodeBase64(encodeMD5Raw(key));
    }

    public static String encodeMD5B64(File file, boolean enhanced) {
	if (enhanced)
	    return enhancedCoder.encodeBase64(encodeMD5Raw(file));
	else
	    return standardCoder.encodeBase64(encodeMD5Raw(file));
    }

    public static String encodeMD5Hex(String key) {
	// generate a hex representation from the md5 of a string
	return encodeHex(encodeMD5Raw(key));
    }

    public static String encodeMD5Hex(File file) {
	// generate a hex representation from the md5 of a file
	return encodeHex(encodeMD5Raw(file));
    }

    public static String encodeMD5Hex(byte[] b) {
	// generate a hex representation from the md5 of a byte-array
	return encodeHex(encodeMD5Raw(b));
    }

    private static byte[] encodeMD5Raw(String key) {
	try {
	    MessageDigest digest = MessageDigest.getInstance("MD5");
	    digest.reset();
	    digest.update(key.getBytes());
	    return digest.digest();
	} catch (java.security.NoSuchAlgorithmException e) {
	    System.out.println("Internal Error at md5:" + e.getMessage());
	}
	return null;
    }

    private static byte[] encodeMD5Raw(File file) {
	try {
	    MessageDigest digest = MessageDigest.getInstance("MD5");
	    digest.reset();
	    InputStream  in = new BufferedInputStream(new FileInputStream(file), 2048);
	    byte[] buf = new byte[2048];
	    int n;
	    while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
	    in.close();
	    // now compute the hex-representation of the md5 digest
	    return digest.digest();
	} catch (java.security.NoSuchAlgorithmException e) {
	    System.out.println("Internal Error at md5:" + e.getMessage());
	} catch (java.io.FileNotFoundException e) {
	    System.out.println("file not found:" + file.toString());
	} catch (java.io.IOException e) {
	    System.out.println("file error with " + file.toString() + ": " + e.getMessage());
	}
	return null;
    }

    private static byte[] encodeMD5Raw(byte[] b) {
	try {
	    MessageDigest digest = MessageDigest.getInstance("MD5");
	    digest.reset();
	    InputStream  in = new ByteArrayInputStream(b);
	    byte[] buf = new byte[2048];
	    int n;
	    while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
	    in.close();
	    // now compute the hex-representation of the md5 digest
	    return digest.digest();
	} catch (java.security.NoSuchAlgorithmException e) {
	    System.out.println("Internal Error at md5:" + e.getMessage());
	} catch (java.io.IOException e) {
	    System.out.println("byte[] error: " + e.getMessage());
	}
	return null;
    }

    public static Properties s2p(String s) {
	Properties p = new Properties();
	int pos;
	StringTokenizer st = new StringTokenizer(s, ",");
	String token;
	while (st.hasMoreTokens()) {
	    token = st.nextToken().trim();
	    pos = token.indexOf("=");
	    if (pos > 0) p.setProperty(token.substring(0, pos).trim(), token.substring(pos + 1).trim());
	}
	return p;
    }
    
    public static HashMap string2map(String string) {
        // this can be used to parse a Map.toString() into a Map again
	if (string == null) return null;
	HashMap map = new HashMap();
	int pos;
	pos = string.indexOf("{"); if (pos >= 0) string = string.substring(pos + 1).trim();
	pos = string.lastIndexOf("}"); if (pos >= 0) string = string.substring(0, pos).trim();
	StringTokenizer st = new StringTokenizer(string, ",");
	String token;
	while (st.hasMoreTokens()) {
	    token = st.nextToken().trim();
	    pos = token.indexOf("=");
	    if (pos > 0) map.put(token.substring(0, pos).trim(), token.substring(pos + 1).trim());
	}
	return map;
    }
    
    public static void main(String[] s) {
	serverCodings b64 = new serverCodings(true);
	if (s.length == 0) {System.out.println("usage: -[ec|dc|es|ds|s2m] <arg>"); System.exit(0);}
	if (s[0].equals("-ec")) {
	    // generate a b64 encoding from a given cardinal
	    System.out.println(b64.encodeBase64Long(Long.parseLong(s[1]), 4));
	}
	if (s[0].equals("-dc")) {
	    // generate a b64 decoding from a given cardinal
	    System.out.println(b64.decodeBase64Long(s[1]));
	}
	if (s[0].equals("-es")) {
	    // generate a b64 encoding from a given string
	    System.out.println(b64.encodeBase64String(s[1]));
	}
	if (s[0].equals("-ds")) {
	    // generate a b64 decoding from a given string
	    System.out.println(b64.decodeBase64String(s[1]));
	}
	if (s[0].equals("-s2m")) {
	    // generate a b64 decoding from a given string
	    System.out.println(string2map(s[1]).toString());
	}
    }

}
