// MapTools.java
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

package net.yacy.kelondro.util;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

public final class MapTools {

    public static Properties s2p(final String s) {
        final Properties p = new Properties();
        int pos;
        final StringTokenizer st = new StringTokenizer(s, ",");
        String token;
        while (st.hasMoreTokens()) {
            token = st.nextToken().trim();
            pos = token.indexOf('=');
            if (pos > 0) p.setProperty(token.substring(0, pos).trim(), token.substring(pos + 1).trim());
        }
        return p;
    }

    public static ConcurrentHashMap<String, String> string2map(String string, final String separator) {
        // this can be used to parse a Map.toString() into a Map again
        if (string == null) return null;
        final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<String, String>();
        int pos;
        if ((pos = string.indexOf('{')) >= 0) string = string.substring(pos + 1).trim();
        if ((pos = string.lastIndexOf('}')) >= 0) string = string.substring(0, pos).trim();
        final StringTokenizer st = new StringTokenizer(string, separator);
        String token;
        while (st.hasMoreTokens()) {
            token = st.nextToken().trim();
            pos = token.indexOf('=');
            if (pos > 0) map.put(token.substring(0, pos).trim(), token.substring(pos + 1).trim());
        }
        return map;
    }

    public static String map2string(final Map<String, String> m, final String separator, final boolean braces) {
        // m must be synchronized to prevent that a ConcurrentModificationException occurs
        synchronized (m) {
            final StringBuilder buf = new StringBuilder(30 * m.size());
            if (braces) { buf.append('{'); }
            int retry = 10;
            critical: while (retry > 0) {
                try {
                    for (final Entry<String, String> e: m.entrySet()) {
                        if (e.getValue() == null) continue;
                        buf.append(e.getKey()).append('=').append(e.getValue()).append(separator);
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
            if (braces) { buf.append('}'); }
            return buf.toString();
        }
    }

    public static Set<String> string2set(String string, final String separator) {
        // this can be used to parse a Map.toString() into a Map again
        if (string == null) return null;
        final Set<String> set = Collections.synchronizedSet(new HashSet<String>());
        int pos;
        if ((pos = string.indexOf('{')) >= 0) string = string.substring(pos + 1).trim();
        if ((pos = string.lastIndexOf('}')) >= 0) string = string.substring(0, pos).trim();
        final StringTokenizer st = new StringTokenizer(string, separator);
        while (st.hasMoreTokens()) {
            set.add(st.nextToken().trim());
        }
        return set;
    }

    public static String set2string(final Set<String> s, final String separator, final boolean braces) {
        final StringBuilder buf = new StringBuilder(s.size() * 40 + 1);
        if (braces) buf.append('{');
        final Iterator<String> i = s.iterator();
        boolean hasNext = i.hasNext();
        while (hasNext) {
            buf.append(i.next());
            hasNext = i.hasNext();
            if (hasNext) buf.append(separator);
        }
        if (braces) buf.append('}');
        return new String(buf);
    }


}
