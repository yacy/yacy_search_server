
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
	
	public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch env) {
        
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
    	sb.peers.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.peers.mySeed(), yacyNewsPool.CATEGORY_BOOKMARK_ADD, map));
    }
}