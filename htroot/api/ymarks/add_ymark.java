import java.io.IOException;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkEntry;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.data.ymark.YMarkUtil;
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

            String url = post.get(YMarkEntry.BOOKMARK.URL.key(),YMarkEntry.BOOKMARK.URL.deflt());
			boolean hasProtocol = false;
			for (YMarkTables.PROTOCOLS p : YMarkTables.PROTOCOLS.values()) {
				hasProtocol = url.toLowerCase().startsWith(p.protocol());
			}
			if (!hasProtocol) {
			    url=YMarkTables.PROTOCOLS.HTTP.protocol(url);
			}
    		
        	final YMarkEntry bmk = new YMarkEntry();        
            
        	bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);
        	bmk.put(YMarkEntry.BOOKMARK.TITLE.key(), post.get(YMarkEntry.BOOKMARK.TITLE.key(),YMarkEntry.BOOKMARK.TITLE.deflt()));
        	bmk.put(YMarkEntry.BOOKMARK.DESC.key(), post.get(YMarkEntry.BOOKMARK.DESC.key(),YMarkEntry.BOOKMARK.DESC.deflt()));
        	bmk.put(YMarkEntry.BOOKMARK.PUBLIC.key(), post.get(YMarkEntry.BOOKMARK.PUBLIC.key(),YMarkEntry.BOOKMARK.PUBLIC.deflt()));
        	bmk.put(YMarkEntry.BOOKMARK.TAGS.key(), YMarkUtil.cleanTagsString(post.get(YMarkEntry.BOOKMARK.TAGS.key(),YMarkEntry.BOOKMARK.TAGS.deflt())));
        	bmk.put(YMarkEntry.BOOKMARK.FOLDERS.key(), YMarkUtil.cleanFoldersString(post.get(YMarkEntry.BOOKMARK.FOLDERS.key(),YMarkEntry.FOLDERS_UNSORTED)));
            
            try {
				sb.tables.bookmarks.addBookmark(bmk_user, bmk, false, false);
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
