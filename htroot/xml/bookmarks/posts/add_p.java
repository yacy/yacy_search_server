// /xml/bookmarks/posts/add_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// last major change: 16.09.2008
// this file is contributed by Stefan Förster
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
import java.util.HashMap;
import java.util.Set;

import de.anomic.data.bookmarksDB;
import de.anomic.data.listManager;
import de.anomic.data.userDB;
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;


public class add_p {
    
	private static final serverObjects prop = new serverObjects();
	private static plasmaSwitchboard sb = null;
	private static userDB.Entry user = null;
	private static boolean isAdmin = false;	
	
	public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        
        sb = (plasmaSwitchboard) env;       
        isAdmin=sb.verifyAuthentication(header, true);
        user = sb.userDB.getUser(header);
        
        // set user name
        String username="";        
        if(user != null) username=user.getUserName();
    	else if(isAdmin) username="admin";
        
        if(post!= null){
    		if(!isAdmin){
    			// force authentication if desired
        			if(post.containsKey("login")){
        				prop.put("AUTHENTICATE","admin log-in");
        			}
        			return prop;
    		} 
    		final String url=post.get("url","");
			final String title=post.get("title",url);
			final String description=post.get("description","");
			String tagsString = post.get("tags","");
			String pathString = post.get("path","/unsorted");
			tagsString=tagsString+","+pathString;
			final Set<String> tags=listManager.string2set(bookmarksDB.cleanTagsString(tagsString)); 
			final bookmarksDB.Bookmark bookmark = sb.bookmarksDB.createBookmark(url, username);
			if(bookmark != null){
				bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_TITLE, title);
				bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
				if(user!=null){ 
					bookmark.setOwner(user.getUserName());
				}
				if((post.get("public")).equals("public")){
					bookmark.setPublic(true);
					publishNews(url, title, description, tagsString);
				}else{
					bookmark.setPublic(false);
				}
				if(post.containsKey("feed") && (post.get("feed")).equals("feed")){
					bookmark.setFeed(true);
				}else{
					bookmark.setFeed(false);
				}
				bookmark.setTags(tags, true);
				sb.bookmarksDB.saveBookmark(bookmark);
				prop.put("result", "1");
			} else {
				// ERROR
				prop.put("result", "0");
			}
        }
        // return rewrite properties
        return prop;
    }
    private static void publishNews(final String url, final String title, final String description, final String tagsString) {
    	// create a news message
    	final HashMap<String, String> map = new HashMap<String, String>();
    	map.put("url", url.replace(',', '|'));
    	map.put("title", title.replace(',', ' '));
    	map.put("description", description.replace(',', ' '));
    	map.put("tags", tagsString.replace(',', ' '));
    	sb.webIndex.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.webIndex.seedDB.mySeed(), yacyNewsPool.CATEGORY_BOOKMARK_ADD, map));
    }
}