// serverCodings.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 29.04.2004
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

package de.anomic.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;

public final class serverCodings {

    public static String encodeHex(final long in, final int length) {
        String s = Long.toHexString(in);
        while (s.length() < length) s = "0" + s;
        return s;
    }
    
    public static String encodeOctal(final byte[] in) {
        if (in == null) return "";
        final StringBuffer result = new StringBuffer(in.length * 8 / 3);
        for (int i = 0; i < in.length; i++) {
            if ((0Xff & in[i]) < 8) result.append('0');
            result.append(Integer.toOctalString(0Xff & in[i]));
        }
        return new String(result);
    }
    
    public static String encodeHex(final byte[] in) {
        if (in == null) return "";
        final StringBuffer result = new StringBuffer(in.length * 2);
        for (int i = 0; i < in.length; i++) {
            if ((0Xff & in[i]) < 16) result.append('0');
            result.append(Integer.toHexString(0Xff & in[i]));
        }
        return new String(result);
    }

    public static byte[] decodeHex(final String hex) {
        final byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (16 * Integer.parseInt(hex.charAt(i * 2) + "", 16) + Integer.parseInt(hex.charAt(i * 2 + 1) + "", 16));
        }
        return result;
    }
    
    public static String encodeMD5Hex(final String key) {
        // generate a hex representation from the md5 of a string
        return encodeHex(encodeMD5Raw(key));
    }

    public static String encodeMD5Hex(final File file) {
        // generate a hex representation from the md5 of a file
        return encodeHex(encodeMD5Raw(file));
    }

    public static String encodeMD5Hex(final byte[] b) {
        // generate a hex representation from the md5 of a byte-array
        return encodeHex(encodeMD5Raw(b));
    }

    public static byte[] encodeMD5Raw(final String key) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.reset();
            byte[] keyBytes;
            try {
                keyBytes = key.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                keyBytes = key.getBytes();
            }
            digest.update(keyBytes);
            return digest.digest();
        } catch (final java.security.NoSuchAlgorithmException e) {
            System.out.println("Internal Error at md5:" + e.getMessage());
        }
        return null;
    }

    public static byte[] encodeMD5Raw(final File file) {
	try {
	    final MessageDigest digest = MessageDigest.getInstance("MD5");
	    digest.reset();
	    final InputStream  in = new BufferedInputStream(new FileInputStream(file), 2048);
	    final byte[] buf = new byte[2048];
	    int n;
	    while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
	    in.close();
	    // now compute the hex-representation of the md5 digest
	    return digest.digest();
	} catch (final java.security.NoSuchAlgorithmException e) {
	    System.out.println("Internal Error at md5:" + e.getMessage());
	} catch (final java.io.FileNotFoundException e) {
	    System.out.println("file not found:" + file.toString());
	    e.printStackTrace();
	} catch (final java.io.IOException e) {
	    System.out.println("file error with " + file.toString() + ": " + e.getMessage());
	}
	return null;
    }

    private static byte[] encodeMD5Raw(final byte[] b) {
	try {
	    final MessageDigest digest = MessageDigest.getInstance("MD5");
	    digest.reset();
	    final InputStream  in = new ByteArrayInputStream(b);
	    final byte[] buf = new byte[2048];
	    int n;
	    while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
	    in.close();
	    // now compute the hex-representation of the md5 digest
	    return digest.digest();
	} catch (final java.security.NoSuchAlgorithmException e) {
	    System.out.println("Internal Error at md5:" + e.getMessage());
	} catch (final java.io.IOException e) {
	    System.out.println("byte[] error: " + e.getMessage());
	}
	return null;
    }

    public static Properties s2p(final String s) {
	final Properties p = new Properties();
	int pos;
	final StringTokenizer st = new StringTokenizer(s, ",");
	String token;
	while (st.hasMoreTokens()) {
	    token = st.nextToken().trim();
	    pos = token.indexOf("=");
	    if (pos > 0) p.setProperty(token.substring(0, pos).trim(), token.substring(pos + 1).trim());
	}
	return p;
    }
    
    public static HashMap<String, String> string2map(String string, final String separator) {
        // this can be used to parse a Map.toString() into a Map again
        if (string == null) return null;
        final HashMap<String, String> map = new HashMap<String, String>();
        int pos;
        if ((pos = string.indexOf("{")) >= 0) string = string.substring(pos + 1).trim();
        if ((pos = string.lastIndexOf("}")) >= 0) string = string.substring(0, pos).trim();
        final StringTokenizer st = new StringTokenizer(string, separator);
        String token;
        while (st.hasMoreTokens()) {
            token = st.nextToken().trim();
            pos = token.indexOf("=");
            if (pos > 0) map.put(token.substring(0, pos).trim(), token.substring(pos + 1).trim());
        }
        return map;
    }

    public static String map2string(final Map<String, String> m, final String separator, final boolean braces) {
        // m must be synchronized to prevent that a ConcurrentModificationException occurs
        synchronized (m) {
            final StringBuffer buf = new StringBuffer(20 * m.size());
            if (braces) { buf.append("{"); }
            int retry = 10;
            critical: while (retry > 0) {
                try {
                    for (final Entry<String, String> e: m.entrySet()) {
                        buf.append(e.getKey()).append('=');
                        if (e.getValue() != null) { buf.append(e.getValue()); }
                        buf.append(separator);
                    }
                    break critical; // success
                } catch (final ConcurrentModificationException e) {
                    // retry
                    buf.setLength(1);
                    retry--;
                }
                buf.setLength(1); // fail
            }
            if (buf.length() > 1) { buf.setLength(buf.length() - 1); } // remove last separator
            if (braces) { buf.append("}"); }
            return new String(buf);
        }
    }

    public static Set<String> string2set(String string, final String separator) {
        // this can be used to parse a Map.toString() into a Map again
        if (string == null) return null;
        final Set<String> set = Collections.synchronizedSet(new HashSet<String>());
        int pos;
        if ((pos = string.indexOf("{")) >= 0) string = string.substring(pos + 1).trim();
        if ((pos = string.lastIndexOf("}")) >= 0) string = string.substring(0, pos).trim();
        final StringTokenizer st = new StringTokenizer(string, separator);
        while (st.hasMoreTokens()) {
            set.add(st.nextToken().trim());
        }
        return set;
    }
    
    public static String set2string(final Set<String> s, final String separator, final boolean braces) {
        final StringBuffer buf = new StringBuffer();
        if (braces) buf.append("{");
        final Iterator<String> i = s.iterator();
        boolean hasNext = i.hasNext();
        while (hasNext) {
            buf.append(i.next());
            hasNext = i.hasNext();
            if (hasNext) buf.append(separator);
        }
        if (braces) buf.append("}");
        return new String(buf);
    }
    
    public static void main(final String[] s) {
        if (s.length == 0) {
            System.out.println("usage: -[ec|dc|es|ds|s2m] <arg>");
            System.exit(0);
        }

        if (s[0].equals("-s2m")) {
            // generate a b64 decoding from a given string
            System.out.println(string2map(s[1], ",").toString());
        }
    }

}
