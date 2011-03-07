/**
 *  UTF8
 *  Copyright 2011 by Michael Peter Christen
 *  First released 25.2.2011 at http://yacy.net
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

    public static Charset charset;
    static {
        charset = Charset.forName("UTF-8");
    }
    
    public static StringBody StringBody(String s) {
        try {
            return new StringBody(s, charset);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * using the string method with the default charset given as argument should prevent using the charset cache
     * in FastCharsetProvider.java:118 which locks all concurrent threads using a new String() method
     * @param bytes
     * @return
     */
    public static String String(byte[] bytes) {
        return new String(bytes, charset);
    }
    
    public static String String(byte[] bytes, int offset, int length) {
        return new String(bytes, charset);
    }
}
