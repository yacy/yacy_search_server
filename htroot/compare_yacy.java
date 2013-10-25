// compare_yacy.java
// (C) 2008 by Marc Nause
// first published 13.09.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import java.util.LinkedHashMap;
import java.util.Map;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

public class compare_yacy {

    public static final String defaultsearchL = "YaCy";
    public static final String defaultsearchR = "startpage.com";
    private static final Map<String, String> searchengines = new LinkedHashMap<String, String>();
    static {
        searchengines.put(defaultsearchL, "yacysearch.html?display=2&resource=global&query=");
        searchengines.put("YaCy (local)", "yacysearch.html?display=2&resource=local&query=");
        //searchengines.put("google.com", "https://www.google.com/#q=");
        searchengines.put("startpage.com", "https://startpage.com/do/search?cat=web&query=");
        searchengines.put("bing.com", "http://www.bing.com/search?q=");
        searchengines.put("metager.de", "http://www.metager.de/meta/cgi-bin/meta.ger1?eingabe=");
        searchengines.put("metager2.de (web)", "http://www.metager2.de/search.php?ses=web&q=");
        searchengines.put("metager2.de (international)", "http://www.metager2.de/search.php?ses=international&q=");
        searchengines.put("yahoo.com", "http://search.yahoo.com/search?p=");
        searchengines.put("romso.de", "http://romso.de/?q=");
        searchengines.put("Wikipedia English", "http://en.wikipedia.org/wiki/");
        searchengines.put("Wikipedia Deutsch", "http://de.wikipedia.org/wiki/");
        searchengines.put("Sciencenet", "http://sciencenet.fzk.de:8080/yacysearch.html?verify=true&resource=global&nav=all&display=2&meanCount=5&query=");
        searchengines.put("dbpedia", "http://dbpedia.neofonie.de/browse/~:");
        searchengines.put("wolfram alpha", "http://www.wolframalpha.com/input/?i=");
        searchengines.put("OAIster@OCLC", "http://oaister.worldcat.org/search?q=");
        searchengines.put("oai.yacy.net", "http://oai.yacy.net/yacysearch.html?verify=true&resource=local&nav=all&display=2&meanCount=5&query=");
    }

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        final servletProperties prop = new servletProperties();

        prop.put("display", display);

        String default_left = sb.getConfig("compare_yacy.left", defaultsearchL);
        if (!searchengines.containsKey(default_left)) default_left = defaultsearchL;
        String default_right = sb.getConfig("compare_yacy.right", defaultsearchR);
        if (!searchengines.containsKey(default_right)) default_right = defaultsearchR;

        if (post != null) {
            if (searchengines.get(post.get("left", default_left)) != null) {
                default_left = post.get("left", default_left);
                sb.setConfig("compare_yacy.left", default_left);
            }
            if (searchengines.get(post.get("right", default_right)) != null) {
                default_right = post.get("right", default_right);
                sb.setConfig("compare_yacy.right", default_right);
            }
        }

        prop.put("searchengines", searchengines.size());
        int i = 0;
        for (String name: searchengines.keySet()) {
            prop.putHTML("searchengines_" + i + "_searchengine", name);
            prop.put("searchengines_" + i + "_leftengine", name.equals(default_left) ? 1 : 0);
            prop.put("searchengines_" + i + "_rightengine", name.equals(default_right) ? 1 : 0);
            i++;
        }

        prop.putHTML("search_left", searchengines.get(default_left));
        prop.putHTML("search_right", searchengines.get(default_right));

        if (post == null || post.get("query", "").isEmpty()) {
            prop.put("search", 0);
            prop.put("search_query", "");
            return prop;
        }

        prop.put("search", 1);
        prop.putHTML("search_query", post.get("query", ""));

        // return rewrite properties
        return prop;
    }
}
