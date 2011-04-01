import java.io.IOException;
import java.util.HashMap;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkTables;
import de.anomic.data.UserDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class add_ymark {
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);

            String url = post.get(YMarkTables.BOOKMARK.URL.key(),YMarkTables.BOOKMARK.URL.deflt());
			boolean hasProtocol = false;
			for (YMarkTables.PROTOCOLS p : YMarkTables.PROTOCOLS.values()) {
				hasProtocol = url.toLowerCase().startsWith(p.protocol());
			}
			if (!hasProtocol) {
			    url=YMarkTables.PROTOCOLS.HTTP.protocol(url);
			}
    		
        	final HashMap<String,String> data = new HashMap<String,String>();        
            
            data.put(YMarkTables.BOOKMARK.URL.key(), url);
            data.put(YMarkTables.BOOKMARK.TITLE.key(), post.get(YMarkTables.BOOKMARK.TITLE.key(),YMarkTables.BOOKMARK.TITLE.deflt()));
            data.put(YMarkTables.BOOKMARK.DESC.key(), post.get(YMarkTables.BOOKMARK.DESC.key(),YMarkTables.BOOKMARK.DESC.deflt()));
            data.put(YMarkTables.BOOKMARK.PUBLIC.key(), post.get(YMarkTables.BOOKMARK.PUBLIC.key(),YMarkTables.BOOKMARK.PUBLIC.deflt()));
            data.put(YMarkTables.BOOKMARK.TAGS.key(), YMarkTables.cleanTagsString(post.get(YMarkTables.BOOKMARK.TAGS.key(),YMarkTables.BOOKMARK.TAGS.deflt())));
            data.put(YMarkTables.BOOKMARK.FOLDERS.key(), YMarkTables.cleanFoldersString(post.get(YMarkTables.BOOKMARK.FOLDERS.key(),YMarkTables.FOLDERS_UNSORTED)));
            
            try {
				sb.tables.bookmarks.addBookmark(bmk_user, data, false);
				} catch (IOException e) {
				    Log.logException(e);
				} catch (RowSpaceExceededException e) {
			}
            prop.put("result", "1");
        } else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
    }
}
