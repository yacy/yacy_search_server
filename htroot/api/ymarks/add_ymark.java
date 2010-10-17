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
import de.anomic.data.YMarkStatics;
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
        	final String bmk_table = (isAuthUser ? user.getUserName() : YMarkStatics.TABLE_BOOKMARKS_USER_ADMIN)+YMarkStatics.TABLE_BOOKMARKS_BASENAME;
        	final String tag_table = (isAuthUser ? user.getUserName() : YMarkStatics.TABLE_BOOKMARKS_USER_ADMIN)+YMarkStatics.TABLE_TAGS_BASENAME;
        	
            byte[] urlHash = null;
    		if(post.containsKey(YMarkStatics.TABLE_BOOKMARKS_COL_URL)) {
                try {
        			urlHash = YMarkStatics.getBookmarkId(post.get(YMarkStatics.TABLE_BOOKMARKS_COL_URL,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT));
        		} catch (MalformedURLException e) {
        			Log.logException(e);
        		}
    		} else if (post.containsKey(YMarkStatics.TABLE_BOOKMARKS_COL_ID)) {
    			urlHash = post.get(YMarkStatics.TABLE_BOOKMARKS_COL_ID).getBytes();
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
                    final String tagsString = YMarkStatics.cleanTagsString(post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT));
                    final byte[] date = DateFormatter.formatShortMilliSecond(new Date()).getBytes();
                    
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_URL, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_URL,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT).getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT).getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DESC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT).getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,"false").getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS, tagsString.getBytes());               
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_ADDED, date);
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_MODIFIED, date);
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_VISITED, date);
                    sb.tables.insert(bmk_table, urlHash, data);
                    

                    final String[] tagArray = tagsString.split(YMarkStatics.TABLE_TAGS_SEPARATOR);                    
                    for (final String tag : tagArray) {
                    	sb.tables.updateTAGTable(tag_table, tag, urlHash, YMarkStatics.TABLE_TAGS_ACTION_ADD);
                    } 

                    
                } else {	
                	// modify and update existing entry
                    bmk_row.put(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT)).getBytes());
                    bmk_row.put(YMarkStatics.TABLE_BOOKMARKS_COL_DESC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT)).getBytes());
                    bmk_row.put(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC_FALSE)).getBytes());
                	
                    final String tagsString = YMarkStatics.cleanTagsString(post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT));
                	HashSet<String>old_tagSet = YMarkStatics.getTagSet(bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,YMarkStatics.TABLE_BOOKMARKS_COL_DEFAULT), false);
                	HashSet<String>new_tagSet = YMarkStatics.getTagSet(tagsString, false);
                    bmk_row.put(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS, tagsString.getBytes());
                	            	
                    // modify date attribute
                    bmk_row.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_MODIFIED, DateFormatter.formatShortMilliSecond(new Date()).getBytes());                
                    
                    // update bmk_table
                    sb.tables.update(bmk_table, bmk_row);
                    
                    //update tag_table
                    Iterator <String> tagIter;
                    
                    new_tagSet.removeAll(old_tagSet);
                    tagIter = new_tagSet.iterator();
                    while(tagIter.hasNext()) {
                    	sb.tables.updateTAGTable(tag_table, tagIter.next(), urlHash, YMarkStatics.TABLE_TAGS_ACTION_ADD);
                    }
                    
                    new_tagSet = YMarkStatics.getTagSet(tagsString, false);
                    old_tagSet.removeAll(new_tagSet);
                    tagIter=old_tagSet.iterator();
                    while(tagIter.hasNext()) {
                    	sb.tables.updateTAGTable(tag_table, tagIter.next(), urlHash, YMarkStatics.TABLE_TAGS_ACTION_REMOVE);
                    }                    
                }
            } catch (IOException e) {
                Log.logException(e);
            }
            prop.put("result", "1");
        } else {
        	prop.put(YMarkStatics.TABLE_BOOKMARKS_USER_AUTHENTICATE,YMarkStatics.TABLE_BOOKMARKS_USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
    }	
}
