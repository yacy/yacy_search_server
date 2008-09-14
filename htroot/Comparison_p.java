//Comparison_p.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This File is contributed by Marc Nause
//last major change: 13.09.2008
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


//You must compile this file with
//javac -classpath .:../Classes Message.java
//if the shell's current path is HTROOT


import de.anomic.http.httpRequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;
import java.util.Hashtable;
import java.util.Map;

public class Comparison_p{
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final servletProperties prop = new servletProperties();

        Map<String, String> searchengines = new Hashtable<String, String>();
        searchengines.put("YaCy", "yacysearch.html?display=2&amp;query=");
        searchengines.put("google.de", "http://www.google.de/search?q=");
        searchengines.put("metager.de", "http://www.metager.de/meta/cgi-bin/meta.ger1?eingabe=");
        
        if (post != null) {
            prop.put("search", 1);
            prop.put("search_query", post.get("query", ""));
            prop.put("search_left", searchengines.get(post.get("left", searchengines.get("YaCy"))));
            prop.put("search_right", searchengines.get(post.get("right", searchengines.get("YaCy"))));
        } else {
            
            prop.put("search", 0);
            prop.put("search_query", "");
           
        }
        
        prop.put("searchengines", searchengines.size());
        int i = 0;
        for(String name : searchengines.keySet()){
            prop.put("searchengines_" + i + "_searchengine", name);
	    if(post != null && post.get("left").equals(name)) {
		    prop.put("searchengines_" + i + "_leftengine", 1);
	    } else {
		    prop.put("searchengines_" + i + "_leftengine", 0);
	    }
	    if(post != null && post.get("right").equals(name)) {
		    prop.put("searchengines_" + i + "_rightengine", 1);
	    } else {
		    prop.put("searchengines_" + i + "_rightengine", 0);
	    }
            i++;
        }

        // return rewrite properties
        return prop;
    }
}
