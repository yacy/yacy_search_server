// /xml/bookmarks/posts/delete_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// last major change: 05.02.2006
// this file is contributed by Alexander Schier
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

package xml.bookmarks.posts;
import java.net.MalformedURLException;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class delete_p {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        if(post!= null){
        	try {
                if( post.containsKey("url") && switchboard.bookmarksDB.removeBookmark((new yacyURL(post.get("url", "nourl"), null)).hash())) {
                	prop.put("result", "1");
                }else if(post.containsKey("urlhash") && switchboard.bookmarksDB.removeBookmark(post.get("urlhash", "nohash"))){
                	prop.put("result", "1");
                }else{
                	prop.put("result", "0");
                }
            } catch (MalformedURLException e) {
                prop.put("result", "0");
            }
        }else{
        	prop.put("result", "0");
        }
        // return rewrite properties
        return prop;
    }
    
}



