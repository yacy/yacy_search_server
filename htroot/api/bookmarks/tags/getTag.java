
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.BookmarksDB;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class getTag {
	final static int SORT_ALPHA = 1;
	final static int SORT_SIZE = 2;
	final static int SHOW_ALL = -1;

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard switchboard = (Switchboard) env;
        final boolean isAdmin = switchboard.verifyAuthentication(header);
        final serverObjects prop = new serverObjects();
        Iterator<BookmarksDB.Tag> it = null;
        String tagName = "";
        int top = SHOW_ALL;
        int comp = SORT_ALPHA;


    	if (post != null) {
            if (!isAdmin) {
                // force authentication if desired
                if(post.containsKey("login")){
                	prop.authenticationRequired();
                }
            }

            if (post.containsKey("top")) {
                top = post.getInt("top", SHOW_ALL);
            }
            if (post.containsKey("sort")) {
                if ("size".equals(post.get("sort"))) {
                    comp = SORT_SIZE;
                }
            }
        }

    	if (post != null && post.containsKey("tag")) {
    	    tagName=post.get("tag");
    	    if (!tagName.isEmpty()) {
    	        it = switchboard.bookmarksDB.getTagIterator(tagName, isAdmin, comp, top);
    	    }
    	} else {
    	    it = switchboard.bookmarksDB.getTagIterator(isAdmin, comp, top);
    	}

        // Iterator<bookmarksDB.Tag> it = switchboard.bookmarksDB.getTagIterator(isAdmin);

        int count = 0;
        if (it != null) {
            BookmarksDB.Tag tag;
            while (it.hasNext()) {
                tag = it.next();
                if(!tag.getTagName().startsWith("/")) {				// ignore folder tags
                    prop.putXML("tags_" + count + "_name", tag.getTagName());
                    prop.put("tags_" + count + "_count", tag.size());
                    count++;
                }
            }
        }
        prop.put("tags", count);

        // return rewrite properties
        return prop;
    }

}
