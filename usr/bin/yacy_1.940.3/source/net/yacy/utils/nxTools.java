// nxTools.java
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
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package net.yacy.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

public class nxTools {

    public static Vector<String> grep(final Vector<String> list, final int afterContext, final String pattern) {
    	final Enumeration<String> i = list.elements();
    	int ac = 0;
		String line;
		final Vector<String> result = new Vector<String>();
		while (i.hasMoreElements()) {
			line = i.nextElement();
			if (line.indexOf(pattern) >= 0) {
				result.add(line);
				ac = afterContext + 1;
			} else if (ac > 0) {
				result.add(line);
			}
			ac--;
		}
		return result;
	}

    public static ArrayList<String> grep(final ArrayList<String> list, final int afterContext, final String pattern) {
        final Iterator<String> i = list.iterator();
        int ac = 0;
        String line;
        final ArrayList<String> result = new ArrayList<String>();
        while (i.hasNext()) {
            line = i.next();
            if (line.indexOf(pattern) >= 0) {
            result.add(line);
            ac = afterContext + 1;
            } else if (ac > 0) {
            result.add(line);
            }
            ac--;
        }
        return result;
        }

    public static String tail1(final Vector<String> list) {
    	if (list == null || list.isEmpty()) return "";
    	return list.lastElement();
    }

    public static String tail1(final ArrayList<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.get(list.size()-1);
    }

    public static String awk(String sentence, final String separator, int count) {
	// returns the nth word of sentence, where count is the counter and the first word has the number 1
	// the words are separated by the separator
	if ((sentence == null) || (separator == null) || (count < 1)) return null;
	int pos;
	while ((count >= 1) && (sentence.length() > 0)) {
	    pos = sentence.indexOf(separator);
	    if (pos < 0) {
		if (count == 1) return sentence;
		return null;
	    }
	    if (count == 1) return sentence.substring(0, pos);
	    sentence = sentence.substring(pos + separator.length());
	    count--;
	}
        return null;
    }

    public static String line(final byte[] a, final int lineNr) {
        final InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(a));
        final LineNumberReader lnr = new LineNumberReader(r);
        String theLine = null;
        while (lnr.getLineNumber() < lineNr) {
            try {
                theLine = lnr.readLine();
            } catch (final IOException e) {
                return null;
            }
            if (theLine == null) return null;
        }
        return theLine;
    }

    /**
     * This function shorten URL Strings<br>
     *
     * Example returns:<br>
     * <dl><dt>normal domain:</dt><dd>http://domain.net/leftpath..rightpath</dd>
     * <dt>long domain:</dt><dd>http://very_very_long_domain.net/le..</dd></dl>
     * @param url String like a URL
     * @param len
     * @return the shorten or the old String
     */
    public static String shortenURLString(final String url, final int len) {
        // This is contributed by Thomas Quella (borg-0300)
        if (url == null) { return null; }
        int urlLen = url.length();
        if (urlLen > len) {
            int cpos;
            cpos = url.indexOf("://",0);
            if (cpos >= 0) {
                cpos = url.indexOf("/", cpos + 3);
                if (cpos < 0) { // very crazy domain or very short len
                    return url.substring(0, len - 2).concat("..");
                }
                if (cpos >= len-(len / 3)) {
                    return url.substring(0, len - 2).concat("..");
                }
                // at least 1/3 characters for the path
                final int lb = ((len - cpos) / 2) - 1;
                if (lb * 2 + 2 + cpos < len) { urlLen--; } // if smaller(odd), half right path + 1
                return url.substring(0, cpos + lb).concat("..").concat(url.substring(urlLen - lb));
            } // NO URL !?
        } // URL < len
        return url;
    }

}
