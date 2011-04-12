/**
 *  UTF8
 *  Copyright 2011 by Michael Peter Christen
 *  First released 25.2.2011 at http://yacy.net
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

package net.yacy.cora.document;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
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
public class UTF8 {

    public final static Charset charset;
    static {
        charset = Charset.forName("UTF-8");
    }
    
    public final static StringBody StringBody(final byte[] b) {
        return StringBody(UTF8.String(b));
    }
    
    public final static StringBody StringBody(final String s) {
        try {
            return new StringBody(s, charset);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * using the string method with the default charset given as argument should prevent using the charset cache
     * in FastCharsetProvider.java:118 which locks all concurrent threads using a UTF8.String() method
     * @param bytes
     * @return
     */
    public final static String String(final byte[] bytes) {
        return new String(bytes, charset);
    }
    
    public final static String String(final byte[] bytes, final int offset, final int length) {
        return new String(bytes, offset, length, charset);
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
        return s.getBytes(charset);
    }
    
}
