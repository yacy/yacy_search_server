// /xml/bookmarks/posts/all.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// last major change: 27.12.2005
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

// You must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT
package xml.bookmarks.posts;
import java.util.Date;
import java.util.Iterator;

import de.anomic.data.bookmarksDB;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class all {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        boolean isAdmin=switchboard.verifyAuthentication(header, true);
        serverObjects prop = new serverObjects();
        
        Iterator<String> it;
        if(post != null && post.containsKey("tag")){
            it=switchboard.bookmarksDB.getBookmarksIterator(post.get("tag"), isAdmin);
        }else{
            it=switchboard.bookmarksDB.getBookmarksIterator(isAdmin);
        }
        
        // if an extended xml should be used
        boolean extendedXML = (post != null && post.containsKey("extendedXML"));
        
        int count=0;
        bookmarksDB.Bookmark bookmark;
        Date date;
        while(it.hasNext()){
            bookmark=switchboard.bookmarksDB.getBookmark(it.next());
            prop.putHTML("posts_"+count+"_url", bookmark.getUrl(), true);
            prop.putHTML("posts_"+count+"_title", bookmark.getTitle(), true);
            prop.putHTML("posts_"+count+"_description", bookmark.getDescription(), true);
            prop.putHTML("posts_"+count+"_md5", serverCodings.encodeMD5Hex(bookmark.getUrl()), true);
            date=new Date(bookmark.getTimeStamp());
            prop.putHTML("posts_"+count+"_time", serverDate.formatISO8601(date), true);
            prop.putHTML("posts_"+count+"_tags", bookmark.getTagsString().replaceAll(","," "), true);
            
            // additional XML tags
            prop.put("posts_"+count+"_isExtended",extendedXML ? "1" : "0");
            if (extendedXML) {
            	prop.put("posts_"+count+"_isExtended_private", Boolean.toString(!bookmark.getPublic()));
            }
            count++;
        }
        prop.put("posts", count);

        // return rewrite properties
        return prop;
    }
    
}
