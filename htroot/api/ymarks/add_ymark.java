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
        	final String bmk_table = (isAuthUser ? user.getUserName() : "admin")+"_"+YMarkStatics.TABLE_BOOKMARKS_BASENAME;
        	final String tag_table = (isAuthUser ? user.getUserName() : "admin")+"_"+YMarkStatics.TABLE_TAGS_BASENAME;
        	
            byte[] urlHash = null;
    		if(post.containsKey(YMarkStatics.TABLE_BOOKMARKS_COL_URL)) {
                try {
        			urlHash = YMarkStatics.getBookmarkId(post.get(YMarkStatics.TABLE_BOOKMARKS_COL_URL,""));
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
                    final String tagsString = YMarkStatics.cleanTagsString(post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,""));
                    final byte[] date = DateFormatter.formatShortMilliSecond(new Date()).getBytes();
                    
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_URL, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_URL,"").getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,"").getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DESC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,"").getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,"false").getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS, tagsString.getBytes());               
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_ADDED, date);
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_MODIFIED, date);
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_VISITED, date);
                    sb.tables.insert(bmk_table, urlHash, data);
                    

                    final String[] tagArray = tagsString.split(",");                    
                    for (final String tag : tagArray) {
                    	updateTagTable(tag_table, tag, new String(urlHash), YMarkStatics.TABLE_TAGS_ACTION_ADD);
                    } 

                    
                } else {	
                	// modify and update existing entry
                    bmk_row.put(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,"")).getBytes());
                    bmk_row.put(YMarkStatics.TABLE_BOOKMARKS_COL_DESC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,"")).getBytes());
                    bmk_row.put(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,"false")).getBytes());
                	
                    final String tagsString = YMarkStatics.cleanTagsString(post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,""));
                	HashSet<String>old_tagSet = YMarkStatics.getTagSet(bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,""), false);
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
                    	updateTagTable(tag_table, tagIter.next(), new String(urlHash), YMarkStatics.TABLE_TAGS_ACTION_ADD);
                    }
                    
                    new_tagSet = YMarkStatics.getTagSet(tagsString, false);
                    old_tagSet.removeAll(new_tagSet);
                    tagIter=old_tagSet.iterator();
                    while(tagIter.hasNext()) {
                    	updateTagTable(tag_table, tagIter.next(), new String(urlHash), YMarkStatics.TABLE_TAGS_ACTION_REMOVE);
                    }                    
                }
            } catch (IOException e) {
                Log.logException(e);
            }
            prop.put("result", "1");
        } else {
        	prop.put("AUTHENTICATE","Authentication required!");
        }
        // return rewrite properties
        return prop;
    }
	
	private static boolean updateTagTable(final String tag_table, final String tag, final String urlHash, final int action) {
		Tables.Row tag_row = null;
        final byte[] tagHash = YMarkStatics.getTagHash(tag);
        HashSet<String>urlSet = new HashSet<String>();
		try {
			tag_row = sb.tables.select(tag_table, tagHash);
	        if(tag_row == null) {
	            switch (action) {
	            case YMarkStatics.TABLE_TAGS_ACTION_ADD:
	            	urlSet.add(urlHash);
	            	break;
	            default:
	            	return false;
	            }
	            Data tagEntry = new Data();
	            tagEntry.put(YMarkStatics.TABLE_TAGS_COL_TAG, tag.getBytes());
	            tagEntry.put(YMarkStatics.TABLE_TAGS_COL_URLS, YMarkStatics.keySetToBytes(urlSet));
	            sb.tables.insert(tag_table, tagHash, tagEntry);
	            return true;
	        } else {
	        	urlSet = YMarkStatics.keysStringToKeySet(new String(tag_row.get(YMarkStatics.TABLE_TAGS_COL_URLS)));
	        	if(urlSet.contains(urlHash))
	        		Log.logInfo(YMarkStatics.TABLE_BOOKMARKS_LOG, "ok, urlHash found!");
	        	switch (action) {
	            case YMarkStatics.TABLE_TAGS_ACTION_ADD:
	            	urlSet.add(urlHash);
	            	break;
	            case YMarkStatics.TABLE_TAGS_ACTION_REMOVE:
	            	urlSet.remove(urlHash);
	            	if(urlSet.isEmpty()) {
	            		sb.tables.delete(tag_table, tagHash);
	            		return true;
	            	}
	            	break;
	            default:
	            	return false;
	            }
	        	tag_row.put(YMarkStatics.TABLE_TAGS_COL_URLS, YMarkStatics.keySetToBytes(urlSet));
	        	sb.tables.update(tag_table, tag_row);
	        	return true;
	        }
		} catch (IOException e) {
            Log.logException(e);
		} catch (RowSpaceExceededException e) {
            Log.logException(e);
		}
        return false;
	}
	
	
}
