import java.io.IOException;
import java.net.MalformedURLException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkStatics;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class delete_ymark {
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	final String table = (isAuthUser ? user.getUserName() : "admin")+"_"+YMarkStatics.TABLE_BOOKMARKS_BASENAME;
            byte[] pk = null;
        	
        	if(post.containsKey(YMarkStatics.TABLE_BOOKMARKS_COL_ID)) {
        		pk = post.get(YMarkStatics.TABLE_BOOKMARKS_COL_ID).getBytes();
        	} else if(post.containsKey(YMarkStatics.TABLE_BOOKMARKS_COL_URL)) {
            	try {
					pk = YMarkStatics.getBookmarkID(post.get(YMarkStatics.TABLE_BOOKMARKS_COL_URL));
				} catch (MalformedURLException e) {
					Log.logException(e);
				}
        	} else {
        		prop.put("result", "0");
        		return prop;
        	}
            assert pk != null;
            try {
				sb.tables.delete(table,pk);
        		prop.put("result", "1");
			} catch (IOException e) {
				Log.logException(e);
			}
        }        
        // return rewrite properties
        return prop;
	}
}
