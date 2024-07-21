/**
 *  UTF8
 *  Copyright 2011 by Michael Peter Christen
 *  First released 25.2.2011 at https://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.document.encoding;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.content.StringBody;

/**
 * convenience class to produce UTF-8 encoding StringBodies and to provide a default
 * UTF-8 Charset object.
 * Reason: if this is not used in StringBody-Class initialization, a default charset name is parsed.
 * This is a synchronized process and all classes using default charsets synchronize at that point
 * Synchronization is omitted if this class is used
 * @author admin
 *
 */
public class UTF8 implements Comparator<String> {

    private final static ContentType contentType = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);

    public static final UTF8 insensitiveUTF8Comparator = new UTF8(true);
    public static final UTF8 identityUTF8Comparator = new UTF8(false);

    public boolean insensitive;

    public UTF8(final boolean insensitive) {
        this.insensitive = insensitive;
    }

    @Override
    public int compare(final String o0, final String o1) {
        final int l0 = o0.length();
        final int l1 = o1.length();
        final int ml = Math.min(l0, l1);
        char c0, c1;
        for (int i = 0; i < ml; i++) {
            if (this.insensitive) {
                c0 = Character.toLowerCase(o0.charAt(i));
                c1 = Character.toLowerCase(o1.charAt(i));
            } else {
                c0 = o0.charAt(i);
                c1 = o1.charAt(i);
            }
            if (c0 == c1) continue;
            return c0 - c1;
        }
        return l0 - l1;
    }

    public boolean equals(final String o0, final String o1) {
        final int l0 = o0.length();
        final int l1 = o1.length();
        if (l0 != l1) return false;
        return equals(o0, o1, l1);
    }

    private boolean equals(final String o0, final String o1, final int l) {
        char c0, c1;
        for (int i = 0; i < l; i++) {
            if (this.insensitive) {
                c0 = Character.toLowerCase(o0.charAt(i));
                c1 = Character.toLowerCase(o1.charAt(i));
            } else {
                c0 = o0.charAt(i);
                c1 = o1.charAt(i);
            }
            if (c0 == c1) continue;
            return false;
        }
        return true;
    }

    public final static StringBody StringBody(final byte[] b) {
        return StringBody(UTF8.String(b));
    }

    public final static StringBody StringBody(final String s) {
        return new StringBody(s == null ? "" : s, contentType);
    }

    /**
     * using the string method with the default charset given as argument should prevent using the charset cache
     * in FastCharsetProvider.java:118 which locks all concurrent threads using a UTF8.String() method
     * @param bytes
     * @return
     */
    public final static String String(final byte[] bytes) {
        return new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
    }

    public final static String String(final byte[] bytes, final int offset, final int length) {
        return new String(bytes, offset, length, StandardCharsets.UTF_8);
    }

    /**
     * getBytes() as method for String synchronizes during the look-up for the
     * Charset object for the default charset as given with a default charset name.
     * This is the normal process:

    public byte[] getBytes() {
    return StringCoding.encode(value, offset, count);
    }

    static byte[] encode(char[] ca, int off, int len) {
    String csn = Charset.defaultCharset().name();
    try {
        return encode(csn, ca, off, len);
        ...

    static byte[] encode(String charsetName, char[] ca, int off, int len)
    throws UnsupportedEncodingException
    {
    StringEncoder se = (StringEncoder)deref(encoder);
    String csn = (charsetName == null) ? "ISO-8859-1" : charsetName;
    if ((se == null) || !(csn.equals(se.requestedCharsetName())
                  || csn.equals(se.charsetName()))) {
        se = null;
        try {
        Charset cs = lookupCharset(csn);
        ....

    private static Charset lookupCharset(String csn) {
    if (Charset.isSupported(csn)) {
        try {
        return Charset.forName(csn);
        ....

    public static Charset forName(String charsetName) {
    Charset cs = lookup(charsetName);
    ....

    private static Charset lookup(String charsetName) {
    if (charsetName == null)
        throw new IllegalArgumentException("Null charset name");

    Object[] a;
    if ((a = cache1) != null && charsetName.equals(a[0]))
        return (Charset)a[1];
    // We expect most programs to use one Charset repeatedly.
    // We convey a hint to this effect to the VM by putting the
    // level 1 cache miss code in a separate method.
    return lookup2(charsetName);
    }

    private static Charset lookup2(String charsetName) {
    Object[] a;
    if ((a = cache2) != null && charsetName.equals(a[0])) {
        cache2 = cache1;
        cache1 = a;
        return (Charset)a[1];
    }

    Charset cs;
    if ((cs = standardProvider.charsetForName(charsetName)) != null ||
        (cs = lookupExtendedCharset(charsetName))           != null ||
        (cs = lookupViaProviders(charsetName))              != null)
    {
        cache(charsetName, cs);
        ....

    At this point the getBytes() call synchronizes at one of the methods
    standardProvider.charsetForName
    lookupExtendedCharset
    lookupViaProviders

     * with our call using a given charset object, the call is much easier to perform
     * and it omits the synchronization for the charset lookup.
     *
     * @param s
     * @return
     */
    public final static byte[] getBytes(final String s) {
        if (s == null) return null;
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public final static byte[] getBytes(final StringBuilder s) {
        if (s == null) return null;
        return s.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decodes a <code>application/x-www-form-urlencoded</code> string using a specific
     * encoding scheme.
     * for url query part only   application/x-www-form-urlencoded (+ -> space) is applied
     */
    public static String decodeURL(final String s) {
        boolean needToChange = false;
        final int numChars = s.length();
        final StringBuilder sb = new StringBuilder(numChars > 500 ? numChars / 2 : numChars);
        int i = 0;
        boolean insearchpart = false;
        char c;
        byte[] bytes = null;
        while (i < numChars) {
            c = s.charAt(i);
            switch (c) {
            case '?' : // mark start of query part (to start x-www-form-urlencoded)
                sb.append(c);
                i++;
                insearchpart = true; // flag to start x-www-form + decoding
                break;
            case '+': //application/x-www-form-urlencoded  (in searchpart)
                if (insearchpart) {
                    sb.append(' ');
                    needToChange = true;
                } else {
                    sb.append(c);
                }
                i++;
                break;
            case '%':
                try {
                    if (bytes == null) bytes = new byte[(numChars-i)/3];
                    int pos = 0;
                    while (((i+2) < numChars) && (c=='%')) {
                        final int v = Integer.parseInt(s.substring(i+1,i+3),16);
                        if (v < 0) {
                            return s;
                            //throw new IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - negative value");
                        }
                        bytes[pos++] = (byte) v;
                        i+= 3;
                        if (i < numChars) c = s.charAt(i);
                    }
                    if ((i < numChars) && (c=='%')) {
                        return s;
                        //throw new IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern");
                    }
                    sb.append(new String(bytes, 0, pos, StandardCharsets.UTF_8));
                } catch (final NumberFormatException e) {
                    return s;
                    //throw new IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - " + e.getMessage());
                }
                needToChange = true;
                break;
            default:
                sb.append(c);
                i++;
                break;
            }
        }

        return (needToChange? sb.toString() : s);
    }

	/**
	 * Utility wrapper around the standard JDK {@link URLEncoder#encode(String)}
	 * function, using the UTF-8 character set and catching
	 * {@link UnsupportedEncodingException} exception.
	 *
	 * @param str a string to encode. Must not be null.
	 * @return str encoded in application/x-www-form-urlencoded format
	 */
	public static String encodeUrl(final String str) {
		try {
			return URLEncoder.encode(str, StandardCharsets.UTF_8.name());
		} catch (final UnsupportedEncodingException e) {
			/* Should not happen : UTF-8 support is required for any Java platform */
			return str;
		}
	}

}
