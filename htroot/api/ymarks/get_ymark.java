import java.io.IOException;
import java.util.Iterator;
import java.util.TreeSet;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkTables;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class get_ymark {
	
	private static Switchboard sb = null;
	private static serverObjects prop = null;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        sb = (Switchboard) env;
        prop = new serverObjects();
        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
    	final TreeSet<String> bookmarks = new TreeSet<String>();
        
        if(isAdmin || isAuthUser) {
	    	final String bmk_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLE_BOOKMARKS_BASENAME;
	    	final String tag_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLE_TAGS_BASENAME;
	    	final String folder_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLE_FOLDERS_BASENAME;
	    	
	    	if(post.containsKey(YMarkTables.TABLE_BOOKMARKS_COL_TAGS)) {
	    		final String[] tagArray = YMarkTables.cleanTagsString(post.get(YMarkTables.TABLE_BOOKMARKS_COL_TAGS)).split(YMarkTables.TABLE_TAGS_SEPARATOR);
	    		try {
					bookmarks.addAll(sb.tables.bookmarks.getBookmarks(tag_table, tagArray));
				} catch (IOException e) {
					Log.logException(e);
				} catch (RowSpaceExceededException e) {
					Log.logException(e);
				}
	    	} else if(post.containsKey(YMarkTables.TABLE_BOOKMARKS_COL_FOLDERS)) {
	    		final String[] folderArray = YMarkTables.cleanFoldersString(post.get(YMarkTables.TABLE_BOOKMARKS_COL_FOLDERS)).split(YMarkTables.TABLE_TAGS_SEPARATOR);
                try {
					bookmarks.retainAll(sb.tables.bookmarks.getBookmarks(folder_table, folderArray));
				} catch (IOException e) {
					Log.logException(e);
				} catch (RowSpaceExceededException e) {
					Log.logException(e);
				}
	    	}
	    	
	    	putBookmarks(bookmarks, bmk_table);
	    	
        } else {
        	prop.put(YMarkTables.TABLE_BOOKMARKS_USER_AUTHENTICATE,YMarkTables.TABLE_BOOKMARKS_USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
	}
	
	private static void putBookmarks(final TreeSet<String> urlSet, final String bmk_table) {
		final Iterator<String>urlIter = urlSet.iterator();
		int count = 0;
		while(urlIter.hasNext()) {
			final byte[] urlHash = urlIter.next().getBytes();
			Tables.Row bmk_row = null;
            try {
				bmk_row = sb.tables.select(bmk_table, urlHash);
	            if (bmk_row != null) {
		   			prop.putXML("bookmarks_"+count+"_id", new String(urlHash));
		   			prop.putXML("bookmarks_"+count+"_url", new String(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_URL,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)));
		   			prop.putXML("bookmarks_"+count+"_title", new String(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_TITLE,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)));
		   			prop.putXML("bookmarks_"+count+"_desc", new String(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_DESC,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)));
		   			prop.putXML("bookmarks_"+count+"_added", new String(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_DATE_ADDED,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)));
		   			prop.putXML("bookmarks_"+count+"_modified", new String(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_DATE_MODIFIED,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)));
		   			prop.putXML("bookmarks_"+count+"_visited", new String(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_DATE_VISITED,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)));
		   			prop.putXML("bookmarks_"+count+"_public", new String(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC,YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC_FALSE)));
		   			prop.putXML("bookmarks_"+count+"_tags", new String(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_TAGS,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)));
		            count++;
	            }
			} catch (IOException e) {
                Log.logException(e);
			} catch (RowSpaceExceededException e) {
                Log.logException(e);
			}
		}
		prop.put("bookmarks", count);
	}
}
