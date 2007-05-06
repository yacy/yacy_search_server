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
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        boolean isAdmin=switchboard.verifyAuthentication(header, true);
        serverObjects prop = new serverObjects();
        String tag=null;
        String date;
        //String url=""; //urlfilter not yet implemented
        
        if(post != null && post.containsKey("tag")){
            tag=(String) post.get("tag");
        }
        if(post != null && post.containsKey("date")){
            date=(String)post.get("date");
        }else{
            date=serverDate.dateToiso8601(new Date(System.currentTimeMillis()));
        }
        int count=0;
        
        Date parsedDate = null; 
        try {
			parsedDate = serverDate.iso8601ToDate(date);
		} catch (ParseException e) {
			parsedDate = new Date();
		}
        
        ArrayList bookmark_hashes=switchboard.bookmarksDB.getDate(Long.toString(parsedDate.getTime())).getBookmarkList();
        Iterator it=bookmark_hashes.iterator();
        bookmarksDB.Bookmark bookmark=null;
        while(it.hasNext()){
            bookmark=switchboard.bookmarksDB.getBookmark((String) it.next());
            if(serverDate.dateToiso8601(new Date(bookmark.getTimeStamp())) == date &&
                    tag==null || bookmark.getTags().contains(tag) &&
                    isAdmin || bookmark.getPublic()){
                prop.putSafeXML("posts_"+count+"_url", bookmark.getUrl());
                prop.putSafeXML("posts_"+count+"_title", bookmark.getTitle());
                prop.putSafeXML("posts_"+count+"_description", bookmark.getDescription());
                prop.putSafeXML("posts_"+count+"_md5", serverCodings.encodeMD5Hex(bookmark.getUrl()));
                prop.putSafeXML("posts_"+count+"_time", date);
                prop.putSafeXML("posts_"+count+"_tags", bookmark.getTagsString().replaceAll(","," "));
                count++;
            }
        }
        prop.put("posts", count);

        // return rewrite properties
        return prop;
    }
    
}
