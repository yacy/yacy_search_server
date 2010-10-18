import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import de.anomic.data.YMarkTables;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class add_ymark {
	
	private static Switchboard sb = null;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	final String bmk_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLE_BOOKMARKS_BASENAME;
        	final String tag_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLE_TAGS_BASENAME;
        	
            byte[] urlHash = null;
            String url ="";
    		if(post.containsKey(YMarkTables.TABLE_BOOKMARKS_COL_URL)) {
                try {
                	url = post.get(YMarkTables.TABLE_BOOKMARKS_COL_URL,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT);
                	if (!url.toLowerCase().startsWith(YMarkTables.TABLE_BOOKMARKS_URL_PROTOCOL_HTTP) && !url.toLowerCase().startsWith(YMarkTables.TABLE_BOOKMARKS_URL_PROTOCOL_HTTPS)) {
                        url=YMarkTables.TABLE_BOOKMARKS_URL_PROTOCOL_HTTP+url;
                    }
                	urlHash = YMarkTables.getBookmarkId(url);
        		} catch (MalformedURLException e) {
        			Log.logException(e);
        		}
    		} else if (post.containsKey(YMarkTables.TABLE_BOOKMARKS_COL_ID)) {
    			urlHash = post.get(YMarkTables.TABLE_BOOKMARKS_COL_ID).getBytes();
    		}
    		if(urlHash == null) {
    			prop.put("result", "0");
    			return prop;
    		}

            // read old entry from the bookmarks table (if exists)
            Tables.Row bmk_row = null;
            try {
                bmk_row = sb.tables.select(bmk_table, urlHash);
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
            
            // insert or update entry
            try {
                if (bmk_row == null) {
                    // create and insert new entry
                    Data data = new Data();
                    final String tagsString = YMarkTables.cleanTagsString(post.get(YMarkTables.TABLE_BOOKMARKS_COL_TAGS,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT));
                    final byte[] date = DateFormatter.formatShortMilliSecond(new Date()).getBytes();
                    
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_URL, url.getBytes());
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_TITLE, post.get(YMarkTables.TABLE_BOOKMARKS_COL_TITLE,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT).getBytes());
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_DESC, post.get(YMarkTables.TABLE_BOOKMARKS_COL_DESC,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT).getBytes());
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC, post.get(YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC,YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC_FALSE).getBytes());
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_TAGS, tagsString.getBytes());
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_VISITS, YMarkTables.TABLE_BOOKMARKS_COL_VISITS_ZERO.getBytes());
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_FOLDER, post.get(YMarkTables.TABLE_BOOKMARKS_COL_FOLDER,YMarkTables.TABLE_FOLDERS_UNSORTED).getBytes());
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_DATE_ADDED, date);
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_DATE_MODIFIED, date);
                    data.put(YMarkTables.TABLE_BOOKMARKS_COL_DATE_VISITED, date);
                    sb.tables.insert(bmk_table, urlHash, data);
                    

                    final String[] tagArray = tagsString.split(YMarkTables.TABLE_TAGS_SEPARATOR);                    
                    for (final String tag : tagArray) {
                    	sb.tables.bookmarks.updateTAGTable(tag_table, tag, urlHash, YMarkTables.TABLE_TAGS_ACTION_ADD);
                    } 

                    
                } else {	
                	// modify and update existing entry
                    bmk_row.put(YMarkTables.TABLE_BOOKMARKS_COL_TITLE, post.get(YMarkTables.TABLE_BOOKMARKS_COL_TITLE,bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_TITLE,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)).getBytes());
                    bmk_row.put(YMarkTables.TABLE_BOOKMARKS_COL_DESC, post.get(YMarkTables.TABLE_BOOKMARKS_COL_DESC,bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_DESC,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT)).getBytes());
                    bmk_row.put(YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC, post.get(YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC,bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC,YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC_FALSE)).getBytes());
                    bmk_row.put(YMarkTables.TABLE_BOOKMARKS_COL_FOLDER, post.get(YMarkTables.TABLE_BOOKMARKS_COL_FOLDER,bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_FOLDER,YMarkTables.TABLE_FOLDERS_UNSORTED)).getBytes());
                    
                    final String tagsString = YMarkTables.cleanTagsString(post.get(YMarkTables.TABLE_BOOKMARKS_COL_TAGS,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT));
                	HashSet<String>old_tagSet = YMarkTables.getTagSet(bmk_row.get(YMarkTables.TABLE_BOOKMARKS_COL_TAGS,YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT), false);
                	HashSet<String>new_tagSet = YMarkTables.getTagSet(tagsString, false);
                    bmk_row.put(YMarkTables.TABLE_BOOKMARKS_COL_TAGS, tagsString.getBytes());
                	            	
                    // modify date attribute
                    bmk_row.put(YMarkTables.TABLE_BOOKMARKS_COL_DATE_MODIFIED, DateFormatter.formatShortMilliSecond(new Date()).getBytes());                
                    
                    // update bmk_table
                    sb.tables.update(bmk_table, bmk_row);
                    
                    //update tag_table
                    Iterator <String> tagIter;
                    
                    new_tagSet.removeAll(old_tagSet);
                    tagIter = new_tagSet.iterator();
                    while(tagIter.hasNext()) {
                    	sb.tables.bookmarks.updateTAGTable(tag_table, tagIter.next(), urlHash, YMarkTables.TABLE_TAGS_ACTION_ADD);
                    }
                    
                    new_tagSet = YMarkTables.getTagSet(tagsString, false);
                    old_tagSet.removeAll(new_tagSet);
                    tagIter=old_tagSet.iterator();
                    while(tagIter.hasNext()) {
                    	sb.tables.bookmarks.updateTAGTable(tag_table, tagIter.next(), urlHash, YMarkTables.TABLE_TAGS_ACTION_REMOVE);
                    }                    
                }
            } catch (IOException e) {
                Log.logException(e);
            }
            prop.put("result", "1");
        } else {
        	prop.put(YMarkTables.TABLE_BOOKMARKS_USER_AUTHENTICATE,YMarkTables.TABLE_BOOKMARKS_USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
    }	
}
