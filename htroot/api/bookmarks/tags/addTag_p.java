
import java.io.IOException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.BookmarksDB.Bookmark;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class addTag_p {
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final Switchboard switchboard = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        boolean isAdmin = false;
        isAdmin = switchboard.verifyAuthentication(header);

        prop.put("result", "0");//error
        //rename tags
        if(post != null && isAdmin) {
        	if (post.containsKey("selectTag") && post.containsKey("addTag")) {
                switchboard.bookmarksDB.addTag(post.get("selectTag"), post.get("addTag"));
                prop.put("result", "1");//success
        	} else if (post.containsKey("urlhash") && post.containsKey("addTag")) {
                Bookmark bm;
                try {
                    bm = switchboard.bookmarksDB.getBookmark(post.get("urlhash"));
                    bm.addTag(post.get("addTag"));
                    prop.put("result", "1");//success
                } catch (final IOException e) {
                    prop.put("result", "0");//success
                }
        	}
        }
        // return rewrite properties
        return prop;
    }

}