// serverCodings.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;

public final class serverCodings {

    public static String encodeHex(long in, int length) {
        String s = Long.toHexString(in);
        while (s.length() < length) s = "0" + s;
        return s;
    }
    
    public static String encodeOctal(byte[] in) {
        if (in == null) return "";
        StringBuffer result = new StringBuffer(in.length * 8 / 3);
        for (int i = 0; i < in.length; i++) {
            if ((0Xff & in[i]) < 8) result.append('0');
            result.append(Integer.toOctalString(0Xff & in[i]));
        }
        return new String(result);
    }
    
    public static String encodeHex(byte[] in) {
        if (in == null) return "";
        StringBuffer result = new StringBuffer(in.length * 2);
        for (int i = 0; i < in.length; i++) {
            if ((0Xff & in[i]) < 16) result.append('0');
            result.append(Integer.toHexString(0Xff & in[i]));
        }
        return new String(result);
    }

    public static byte[] decodeHex(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (16 * Integer.parseInt(hex.charAt(i * 2) + "", 16) + Integer.parseInt(hex.charAt(i * 2 + 1) + "", 16));
        }
        return result;
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

    public static byte[] encodeMD5Raw(String key) {
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

    public static byte[] encodeMD5Raw(File file) {
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
	    e.printStackTrace();
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
    
    public static HashMap<String, String> string2map(String string, String separator) {
        // this can be used to parse a Map.toString() into a Map again
        if (string == null) return null;
        HashMap<String, String> map = new HashMap<String, String>();
        int pos;
        if ((pos = string.indexOf("{")) >= 0) string = string.substring(pos + 1).trim();
        if ((pos = string.lastIndexOf("}")) >= 0) string = string.substring(0, pos).trim();
        StringTokenizer st = new StringTokenizer(string, separator);
        String token;
        while (st.hasMoreTokens()) {
            token = st.nextToken().trim();
            pos = token.indexOf("=");
            if (pos > 0) map.put(token.substring(0, pos).trim(), token.substring(pos + 1).trim());
        }
        return map;
    }

    public static String map2string(Map<String, String> m, String separator, boolean braces) {
        final StringBuffer buf = new StringBuffer(20 * m.size());
        if (braces) { buf.append("{"); }
        final Iterator<Map.Entry<String, String>> i = m.entrySet().iterator();
        while (i.hasNext()) {
            final Entry<String, String> e = i.next();
            buf.append(e.getKey()).append('=');
            if (e.getValue() != null) { buf.append(e.getValue()); }
            buf.append(separator);
        }
        if (buf.length() > 1) { buf.setLength(buf.length() - 1); } // remove last separator
        if (braces) { buf.append("}"); }
        return new String(buf);
    }

    public static Set<String> string2set(String string, String separator) {
        // this can be used to parse a Map.toString() into a Map again
        if (string == null) return null;
        Set<String> set = Collections.synchronizedSet(new HashSet<String>());
        int pos;
        if ((pos = string.indexOf("{")) >= 0) string = string.substring(pos + 1).trim();
        if ((pos = string.lastIndexOf("}")) >= 0) string = string.substring(0, pos).trim();
        StringTokenizer st = new StringTokenizer(string, separator);
        while (st.hasMoreTokens()) {
            set.add(st.nextToken().trim());
        }
        return set;
    }
    
    public static String set2string(Set<String> s, String separator, boolean braces) {
        StringBuffer buf = new StringBuffer();
        if (braces) buf.append("{");
        Iterator<String> i = s.iterator();
        boolean hasNext = i.hasNext();
        while (hasNext) {
            buf.append(i.next().toString());
            hasNext = i.hasNext();
            if (hasNext) buf.append(separator);
        }
        if (braces) buf.append("}");
        return new String(buf);
    }
    
    public static void main(String[] s) {
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
