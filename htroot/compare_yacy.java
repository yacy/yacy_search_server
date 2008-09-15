// compare_yacy.java
// (C) 2008 by Marc Nause
// first published 13.09.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

import java.util.HashMap;
import java.util.Map;

public class compare_yacy {
    
    private static final String defaultsearch = "YaCy";
    private static final String[] order = {defaultsearch, "YaCy (local)", "google.de", "google.com", "metager.de", "yahoo.com", "romso.de", "search.live.com", "Wikipedia English", "Wikipedia Deutsch"};
    private static final Map<String, String> searchengines = new HashMap<String, String>();
    static {
        searchengines.put(defaultsearch, "yacysearch.html?display=2&verify=true&resource=global&query=");
        searchengines.put("YaCy (local)", "yacysearch.html?display=2&verify=true&resource=local&query=");
        searchengines.put("google.de", "http://www.google.de/search?q=");
        searchengines.put("google.com", "http://www.google.com/search?q=");
        searchengines.put("metager.de", "http://www.metager.de/meta/cgi-bin/meta.ger1?eingabe=");
        searchengines.put("yahoo.com", "http://search.yahoo.com/search?p=");
        searchengines.put("romso.de", "http://romso.de/?q=");
        searchengines.put("search.live.com", "http://search.live.com/results.aspx?q=");
        searchengines.put("Wikipedia English", "http://en.wikipedia.org/wiki/");
        searchengines.put("Wikipedia Deutsch", "http://de.wikipedia.org/wiki/");
    }
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        final servletProperties prop = new servletProperties();
        
        prop.put("display", display);
        
        String default_left = sb.getConfig("compare_yacy.left", defaultsearch);
        String default_right = sb.getConfig("compare_yacy.right", defaultsearch);
        
        if (post != null) {
            prop.put("search", 1);
            prop.putHTML("search_query", post.get("query", ""));
            prop.putHTML("search_left", searchengines.get(post.get("left", default_left)));
            prop.putHTML("search_right", searchengines.get(post.get("right", default_right)));
        } else {
            prop.put("search", 0);
            prop.put("search_query", "");
        }
        
        
        prop.put("searchengines", order.length);
        String name;
        for (int i = 0; i < order.length; i++) {
            name = order[i];
            prop.putHTML("searchengines_" + i + "_searchengine", name);
            if (post != null && post.get("left", default_left).equals(name)) {
                prop.put("searchengines_" + i + "_leftengine", 1);
                sb.setConfig("compare_yacy.left", name);
            } else {
                prop.put("searchengines_" + i + "_leftengine", 0);
            }
            if (post != null && post.get("right", default_right).equals(name)) {
                prop.put("searchengines_" + i + "_rightengine", 1);
                sb.setConfig("compare_yacy.right", name);
            } else {
                prop.put("searchengines_" + i + "_rightengine", 0);
            }
        }

        // return rewrite properties
        return prop;
    }
}
