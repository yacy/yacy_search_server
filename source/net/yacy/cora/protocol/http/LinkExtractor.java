/**
 *  LinkExtractor
 *  Copyright 2011 by Michael Peter Christen
 *  First released 2.01.2011 at http://yacy.net
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

package net.yacy.cora.protocol.http;

import java.net.MalformedURLException;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.document.id.MultiProtocolURL;

public class LinkExtractor {
    
    private static final char lb = '<', rb = '>', dquotes = '"', space = ' ';
    private static final Object PRESENT = new Object();
    
    private WeakHashMap<MultiProtocolURL, Object> links;
    private Pattern blackpattern;
    
    public LinkExtractor(Pattern blackpattern) {
        this.links = new WeakHashMap<MultiProtocolURL, Object>();
        this.blackpattern = blackpattern;
    }
    
    public void scrape(String text) {
        text = text.replace(lb, space).replace(rb, space).replace(dquotes, space);
        int p, q, s = 0;
        String u;
        while (s < text.length()) {
            p = Math.min(find(text, "smb://", s), Math.min(find(text, "ftp://", s), Math.min(find(text, "http://", s), find(text, "https://", s))));
            if (p == Integer.MAX_VALUE) break;
            q = text.indexOf(" ", p + 1);
            u = text.substring(p, q < 0 ? text.length() : q);
            if (u.endsWith(".")) u = u.substring(0, u.length() - 1); // remove the '.' that was appended above
            s = p + 1;
            if (this.blackpattern.matcher(u).matches()) continue;
            try {links.put(new MultiProtocolURL(u), PRESENT);} catch (final MalformedURLException e) {}
        }
    }

    /**
     * return the links in the text in the order as they appear
     * @return a list of urls
     */
    public MultiProtocolURL[] getLinks() {
        MultiProtocolURL[] urls = new MultiProtocolURL[this.links.size()];
        int i = 0;
        for (MultiProtocolURL uri: this.links.keySet()) urls[i++] = uri;
        return urls;
    }
    
    private static final int find(final String s, final String m, final int start) {
        final int p = s.indexOf(m, start);
        return (p < 0) ? Integer.MAX_VALUE : p;
    }

}
