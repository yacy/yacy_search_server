
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.BookmarkHelper;
import net.yacy.data.BookmarksDB;
import net.yacy.data.ListManager;
import net.yacy.data.UserDB;
import net.yacy.peers.NewsPool;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class add_p {

	private static final serverObjects prop = new serverObjects();
	private static Switchboard sb = null;
	private static UserDB.Entry user = null;
	private static boolean isAdmin = false;

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        sb = (Switchboard) env;
        isAdmin=sb.verifyAuthentication(header);
        user = sb.userDB.getUser(header);

        // set user name
        String username="";
        if(user != null) username=user.getUserName();
    	else if(isAdmin) username="admin";

        if (post != null) {
            if (!isAdmin) {
            // force authentication if desired
                if(post.containsKey("login")){
                	prop.authenticationRequired();
                }
                return prop;
            }
            final String url=post.get("url","");
            final String title=post.get("title",url);
            final String description=post.get("description","");
            String tagsString = post.get("tags","");
            final String pathString = post.get("path","/unsorted");
            tagsString= tagsString + "," + pathString;
            final Set<String> tags = ListManager.string2set(BookmarkHelper.cleanTagsString(tagsString));
            final BookmarksDB.Bookmark bookmark = sb.bookmarksDB.createBookmark(url, username);
            if(bookmark != null){
                bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_TITLE, title);
                bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
                if(user!=null){
                    bookmark.setOwner(user.getUserName());
                }
                if("public".equals(post.get("public"))){
                    bookmark.setPublic(true);
                    publishNews(url, title, description, tagsString);
                }else{
                    bookmark.setPublic(false);
                }
                if(post.containsKey("feed") && "feed".equals(post.get("feed"))){
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
        if (sb.isRobinsonMode()) return;
    	final Map<String, String> map = new HashMap<String, String>(8);
    	map.put("url", url.replace(',', '|'));
    	map.put("title", title.replace(',', ' '));
    	map.put("description", description.replace(',', ' '));
    	map.put("tags", tagsString.replace(',', ' '));
    	sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_BOOKMARK_ADD, map);
    }
}