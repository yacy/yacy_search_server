import java.io.IOException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkTables;
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
        	final String bmk_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLES.BOOKMARKS.basename();
        	final String tag_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLES.TAGS.basename();
        	final String folder_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLES.FOLDERS.basename();
        	
            byte[] urlHash = null;
            
            try {
	        	if(post.containsKey(YMarkTables.TABLE_BOOKMARKS_COL_ID)) {
	        		urlHash = post.get(YMarkTables.TABLE_BOOKMARKS_COL_ID).getBytes();
	        	} else if(post.containsKey(YMarkTables.BOOKMARK.URL.key())) {
					urlHash = YMarkTables.getBookmarkId(post.get(YMarkTables.BOOKMARK.URL.key()));
	        	} else {
	        		prop.put("result", "0");
	        		return prop;
	        	}
	            Tables.Row bmk_row = null;
	            bmk_row = sb.tables.select(bmk_table, urlHash);
	            if(bmk_row != null) {
		            final String tagsString = bmk_row.get(YMarkTables.BOOKMARK.TAGS.key(),YMarkTables.BOOKMARK.TAGS.deflt());
		            removeIndexEntry(tag_table, tagsString, urlHash);
		            final String foldersString = bmk_row.get(YMarkTables.BOOKMARK.FOLDERS.key(),YMarkTables.TABLE_FOLDERS_ROOT);
		            removeIndexEntry(folder_table, foldersString, urlHash);
	            }
				sb.tables.delete(bmk_table,urlHash);
	        	prop.put("result", "1");
			} catch (IOException e) {
				Log.logException(e);
			} catch (RowSpaceExceededException e) {
				Log.logException(e);
			}
        } else {
        	prop.put(YMarkTables.TABLE_BOOKMARKS_USER_AUTHENTICATE,YMarkTables.TABLE_BOOKMARKS_USER_AUTHENTICATE_MSG);
        }       
        // return rewrite properties
        return prop;
	}
	
	private static void removeIndexEntry(final String index_table, String keysString, final byte[] urlHash) {
        final String[] keyArray = keysString.split(YMarkTables.TABLE_TAGS_SEPARATOR);                    
        for (final String tag : keyArray) {
        	sb.tables.bookmarks.updateIndexTable(index_table, tag, urlHash, YMarkTables.TABLE_INDEX_ACTION_REMOVE);
        }
	}
}
