import java.io.IOException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkStatics;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class delete_ymark {
	
	private static Switchboard sb = null;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	final String bmk_table = (isAuthUser ? user.getUserName() : YMarkStatics.TABLE_BOOKMARKS_USER_ADMIN)+YMarkStatics.TABLE_BOOKMARKS_BASENAME;
        	final String tag_table = (isAuthUser ? user.getUserName() : YMarkStatics.TABLE_BOOKMARKS_USER_ADMIN)+YMarkStatics.TABLE_TAGS_BASENAME;
        	
            byte[] urlHash = null;
            
            try {
	        	if(post.containsKey(YMarkStatics.TABLE_BOOKMARKS_COL_ID)) {
	        		urlHash = post.get(YMarkStatics.TABLE_BOOKMARKS_COL_ID).getBytes();
	        	} else if(post.containsKey(YMarkStatics.TABLE_BOOKMARKS_COL_URL)) {
					urlHash = YMarkStatics.getBookmarkId(post.get(YMarkStatics.TABLE_BOOKMARKS_COL_URL));
	        	} else {
	        		prop.put("result", "0");
	        		return prop;
	        	}
	            Tables.Row bmk_row = null;
	            bmk_row = sb.tables.select(bmk_table, urlHash);
	            if(bmk_row != null) {
		            final String tagsString = bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT);
		            final String[] tagArray = tagsString.split(YMarkStatics.TABLE_TAGS_SEPARATOR);                    
	                for (final String tag : tagArray) {
	                	sb.tables.updateTAGTable(tag_table, tag, urlHash,YMarkStatics.TABLE_TAGS_ACTION_REMOVE);
	                }
	            }
				sb.tables.delete(bmk_table,urlHash);
	        	prop.put("result", "1");
			} catch (IOException e) {
				Log.logException(e);
			} catch (RowSpaceExceededException e) {
				Log.logException(e);
			}
        } else {
        	prop.put(YMarkStatics.TABLE_BOOKMARKS_USER_AUTHENTICATE,YMarkStatics.TABLE_BOOKMARKS_USER_AUTHENTICATE_MSG);
        }       
        // return rewrite properties
        return prop;
	}
}
