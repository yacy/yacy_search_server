// crypt.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

package net.yacy.utils;

import java.util.Random;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ConcurrentLog;


public class crypt {

    // --------------------------------------------------------
    // Section: random salt generation
    // --------------------------------------------------------

    private static long saltcounter = 0;
    private static Random saltrandom = new Random(System.currentTimeMillis());

    public static String randomSalt() {
        // generate robust 48-bit random number
        final long salt = (saltrandom.nextLong() & 0XffffffffffffL) + (System.currentTimeMillis() & 0XffffffffffffL) + ((1001 * saltcounter) & 0XffffffffffffL);
        saltcounter++;
        // we generate 48-bit salt values, that are represented as 8-character
        // b64-encoded strings
        return Base64Order.standardCoder.encodeLongSB(salt & 0XffffffffffffL, 8).toString();
    }

    // --------------------------------------------------------
    // Section: PBE + PublicKey based on passwords encryption
    // --------------------------------------------------------

    public static final String vDATE = "20030925";
    public static final String copyright = "[ 'crypt' v" + vDATE + " by Michael Christen / www.anomic.de ]";
    public static final String magicString = "crypt|anomic.de|0"; // magic identifier inside every '.crypt' - file

    String cryptMethod; // one of ["TripleDES", "Blowfish", "DESede", "DES"]
    //private static final String defaultMethod = "PBEWithMD5AndDES"; //"DES";

    // --------------------------------------------------------
    // Section: simple Codings
    // --------------------------------------------------------

    public static String simpleEncode(final String content) {
    return simpleEncode(content, null, 'b');
    }

    public static String simpleEncode(final String content, final String key) {
    return simpleEncode(content, key, 'b');
    }

    public static String simpleEncode(final String content, String key, final char method) {
    if (key == null) { key = "NULL"; }
    switch (method) {
    case 'b' : return "b|" + Base64Order.enhancedCoder.encodeString(content);
    case 'z' : return "z|" + Base64Order.enhancedCoder.encode(gzip.gzipString(content));
    case 'p' : return "p|" + content;
    default  : return null;
    }
    }

    public static String simpleDecode(final String encoded) {
        if (encoded == null || encoded.length() < 3) {
            return null;
        }
        if (encoded.charAt(1) != '|') {
            return encoded;
        } // not encoded
        switch (encoded.charAt(0)) {
        case 'b': {
            return Base64Order.enhancedCoder.decodeString(encoded.substring(2));
        }
        case 'z':
            try {
                return gzip.gunzipString(Base64Order.enhancedCoder.decode(encoded.substring(2)));
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
                return null;
            }
        case 'p': {
            return encoded.substring(2);
        }
        default: {
            return null;
        }
        }
    }

    public static void main(final String[] args) {
        final String teststring="1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        System.out.println("Teststring   = " + teststring);
        System.out.println("enc-b result = " + simpleDecode(simpleEncode(teststring, null, 'b')));
        System.out.println("enc-z result = " + simpleDecode(simpleEncode(teststring, null, 'z')));
    }
}