// /xml/bookmarks/posts/all.java
// -------------------------------
// (C) 2006 Alexander Schier
// part of yacy
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
// Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

package xml.bookmarks.posts;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import de.anomic.data.bookmarksDB;
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get {
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final boolean isAdmin=switchboard.verifyAuthentication(header, true);
        final serverObjects prop = new serverObjects();
        String tag=null;
        String date;
        //String url=""; //urlfilter not yet implemented
        
        if(post != null && post.containsKey("tag")){
            tag=post.get("tag");
        }
        if(post != null && post.containsKey("date")){
            date=post.get("date");
        }else{
            date=serverDate.formatISO8601(new Date(System.currentTimeMillis()));
        }
        
        // if an extended xml should be used
        final boolean extendedXML = (post != null && post.containsKey("extendedXML"));
        
        int count=0;
        
        Date parsedDate = null; 
        try {
			parsedDate = serverDate.parseISO8601(date);
		} catch (final ParseException e) {
			parsedDate = new Date();
		}
        
        final ArrayList<String> bookmark_hashes=switchboard.bookmarksDB.getDate(Long.toString(parsedDate.getTime())).getBookmarkList();
        final Iterator<String> it=bookmark_hashes.iterator();
        bookmarksDB.Bookmark bookmark=null;
        while(it.hasNext()){
            bookmark=switchboard.bookmarksDB.getBookmark(it.next());
            if(serverDate.formatISO8601(new Date(bookmark.getTimeStamp())).equals(date) &&
                    tag==null || bookmark.getTags().contains(tag) &&
                    isAdmin || bookmark.getPublic()){
                prop.putHTML("posts_"+count+"_url", bookmark.getUrl());
                prop.putHTML("posts_"+count+"_title", bookmark.getTitle());
                prop.putHTML("posts_"+count+"_description", bookmark.getDescription());
                prop.put("posts_"+count+"_md5", serverCodings.encodeMD5Hex(bookmark.getUrl()));
                prop.put("posts_"+count+"_time", date);
                prop.putHTML("posts_"+count+"_tags", bookmark.getTagsString().replaceAll(","," "));
                
                // additional XML tags
                prop.put("posts_"+count+"_isExtended",extendedXML ? "1" : "0");
                if (extendedXML) {
                	prop.put("posts_"+count+"_isExtended_private", Boolean.toString(!bookmark.getPublic()));
                }
                count++;
            }
        }
        prop.put("posts", count);

        // return rewrite properties
        return prop;
    }
    
}
