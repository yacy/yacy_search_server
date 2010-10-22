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
        
        boolean tags = false;
        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
    	final TreeSet<String> bookmarks = new TreeSet<String>();
        
        if(isAdmin || isAuthUser) {
        	final String bmk_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLES.BOOKMARKS.basename();
        	final String tag_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLES.TAGS.basename();
        	final String folder_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLES.FOLDERS.basename();
        	
	    	if(post.containsKey(YMarkTables.BOOKMARK.TAGS.key())) {
	    		tags = true;
	    		final String[] tagArray = YMarkTables.cleanTagsString(post.get(YMarkTables.BOOKMARK.TAGS.key())).split(YMarkTables.TABLE_TAGS_SEPARATOR);
	    		try {
					bookmarks.addAll(sb.tables.bookmarks.getBookmarks(tag_table, tagArray));
				} catch (IOException e) {
					Log.logException(e);
				} catch (RowSpaceExceededException e) {
					Log.logException(e);
				}
	    	} else if(post.containsKey(YMarkTables.BOOKMARK.FOLDERS.key())) {
	    		final String[] folderArray = YMarkTables.cleanFoldersString(post.get(YMarkTables.BOOKMARK.FOLDERS.key())).split(YMarkTables.TABLE_TAGS_SEPARATOR);
                try {                	
					if(tags)
						bookmarks.retainAll(sb.tables.bookmarks.getBookmarks(folder_table, folderArray));
					else
						bookmarks.addAll(sb.tables.bookmarks.getBookmarks(folder_table, folderArray));
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
	            	for (YMarkTables.BOOKMARK bmk : YMarkTables.BOOKMARK.values()) {
	            		prop.putXML("bookmarks_"+count+"_"+bmk.key(), new String(bmk_row.get(bmk.key(),bmk.deflt())));
	            	}
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
