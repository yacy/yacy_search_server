
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.data.bookmarksDB.Bookmark;


public class addTag_p {
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch env) {

        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        boolean isAdmin = false;
        isAdmin = switchboard.verifyAuthentication(header, true);
        
        prop.put("result", "0");//error
        //rename tags
        if(post != null && isAdmin) {
        	if (post.containsKey("selectTag") && post.containsKey("addTag")) {
                switchboard.bookmarksDB.addTag(post.get("selectTag"), post.get("addTag"));
                prop.put("result", "1");//success       	
        	} else if (post.containsKey("urlhash") && post.containsKey("addTag")) {
                final Bookmark bm = switchboard.bookmarksDB.getBookmark(post.get("urlhash"));
        		bm.addTag(post.get("addTag"));
                prop.put("result", "1");//success 
        	}
        }       
        // return rewrite properties
        return prop;
    }
    
}