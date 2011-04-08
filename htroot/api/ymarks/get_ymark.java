import java.io.IOException;
import java.util.Iterator;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.data.ymark.YMarkUtil;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class get_ymark {
	
	private static Switchboard sb = null;
	private static serverObjects prop = null;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        sb = (Switchboard) env;
        prop = new serverObjects();
        
        boolean tags = false;
        
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
    	Iterator<Tables.Row> bookmarks = null;
        
        if(isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
        	
	    	if(post.containsKey(YMarkTables.BOOKMARK.TAGS.key())) {
	    		tags = true;
	    		final String[] tagArray = YMarkUtil.cleanTagsString(post.get(YMarkTables.BOOKMARK.TAGS.key())).split(YMarkUtil.TAGS_SEPARATOR);
	    		try {
	    			bookmarks = sb.tables.bookmarks.getBookmarksByTag(bmk_user, tagArray);
				} catch (IOException e) {
					Log.logException(e);
				}
	    	}
	    	/*
	    	if(post.containsKey(YMarkTables.BOOKMARK.FOLDERS.key())) {
	    		final String[] folderArray = YMarkTables.cleanFoldersString(post.get(YMarkTables.BOOKMARK.FOLDERS.key())).split(YMarkTables.TAGS_SEPARATOR);
                try {                	
					if(tags)
						bookmarks.retainAll(sb.tables.bookmarks.folders.getBookmarkIds(bmk_user, folderArray));
					else
						bookmarks.addAll(sb.tables.bookmarks.folders.getBookmarkIds(bmk_user, folderArray));
				} catch (IOException e) {
					Log.logException(e);
				} catch (RowSpaceExceededException e) {
					Log.logException(e);
				}
	    	}
	    	*/
	    	putBookmarks(bookmarks);
	    	
        } else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
	}
	
	private static void putBookmarks(final Iterator<Tables.Row> bit) {		
		int count = 0;
		while(bit.hasNext()) {
			Tables.Row bmk_row = bit.next();
            if (bmk_row != null) {
				prop.putXML("bookmarks_"+count+"_id", UTF8.String(bmk_row.getPK()));
				for (YMarkTables.BOOKMARK bmk : YMarkTables.BOOKMARK.values()) {
					prop.putXML("bookmarks_"+count+"_"+bmk.key(), bmk_row.get(bmk.key(),bmk.deflt()));
				}
			    count++;
			}
		}
		prop.put("bookmarks", count);
	}
}
