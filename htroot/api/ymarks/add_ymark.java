import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
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
        	final String bmk_table = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN)+YMarkTables.TABLES.BOOKMARKS.basename();
        	final String tag_table = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN)+YMarkTables.TABLES.TAGS.basename();
        	final String folder_table = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN)+YMarkTables.TABLES.FOLDERS.basename();
        	
            byte[] urlHash = null;
            String url ="";
    		if(post.containsKey(YMarkTables.BOOKMARK.URL.key())) {
                try {
                	url = post.get(YMarkTables.BOOKMARK.URL.key(),YMarkTables.BOOKMARK.URL.deflt());
                	boolean hasProtocol = false;
                	for (YMarkTables.PROTOCOLS p : YMarkTables.PROTOCOLS.values()) {
                		hasProtocol = url.toLowerCase().startsWith(p.protocol());
                	}
                	if (!hasProtocol) {
                        url=YMarkTables.PROTOCOLS.HTTP.protocol(url);
                    }
                	urlHash = YMarkTables.getBookmarkId(url);
        		} catch (MalformedURLException e) {
        			Log.logException(e);
        		}
    		} else if (post.containsKey(YMarkTables.BOOKMARKS_ID)) {
    			urlHash = post.get(YMarkTables.BOOKMARKS_ID).getBytes();
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
            final byte[] date = String.valueOf(System.currentTimeMillis()).getBytes();
            try {
                if (bmk_row == null) {
                    // create and insert new entry
                    Data data = new Data();          
                    final String tagsString = YMarkTables.cleanTagsString(post.get(YMarkTables.BOOKMARK.TAGS.key(),YMarkTables.BOOKMARK.TAGS.deflt()));                
                    final String foldersString = YMarkTables.cleanFoldersString(post.get(YMarkTables.BOOKMARK.FOLDERS.key(),YMarkTables.FOLDERS_UNSORTED));
                    
                    data.put(YMarkTables.BOOKMARK.URL.key(), url.getBytes());
                    data.put(YMarkTables.BOOKMARK.TITLE.key(), post.get(YMarkTables.BOOKMARK.TITLE.key(),YMarkTables.BOOKMARK.TITLE.deflt()));
                    data.put(YMarkTables.BOOKMARK.DESC.key(), post.get(YMarkTables.BOOKMARK.DESC.key(),YMarkTables.BOOKMARK.DESC.deflt()));
                    data.put(YMarkTables.BOOKMARK.PUBLIC.key(), post.get(YMarkTables.BOOKMARK.PUBLIC.key(),YMarkTables.BOOKMARK.PUBLIC.deflt()));
                    data.put(YMarkTables.BOOKMARK.TAGS.key(), tagsString.getBytes());
                    data.put(YMarkTables.BOOKMARK.VISITS.key(), YMarkTables.BOOKMARK.VISITS.deflt().getBytes());
                    data.put(YMarkTables.BOOKMARK.FOLDERS.key(), foldersString.getBytes());
                    data.put(YMarkTables.BOOKMARK.DATE_ADDED.key(), date);
                    data.put(YMarkTables.BOOKMARK.DATE_MODIFIED.key(), date);
                    data.put(YMarkTables.BOOKMARK.DATE_VISITED.key(), date);
                    sb.tables.insert(bmk_table, urlHash, data);                    

                    final String[] folderArray = foldersString.split(YMarkTables.TAGS_SEPARATOR);                    
                    for (final String folder : folderArray) {
                    	sb.tables.bookmarks.updateIndexTable(folder_table, folder, urlHash, YMarkTables.INDEX_ACTION.ADD);
                    } 
                    
                    final String[] tagArray = tagsString.split(YMarkTables.TAGS_SEPARATOR);                    
                    for (final String tag : tagArray) {
                    	sb.tables.bookmarks.updateIndexTable(tag_table, tag, urlHash, YMarkTables.INDEX_ACTION.ADD);
                    } 

                    
                } else {	
                	// modify and update existing entry
                    bmk_row.put(YMarkTables.BOOKMARK.TITLE.key(), post.get(YMarkTables.BOOKMARK.TITLE.key(),bmk_row.get(YMarkTables.BOOKMARK.TITLE.key(),YMarkTables.BOOKMARK.TITLE.deflt())).getBytes());
                    bmk_row.put(YMarkTables.BOOKMARK.DESC.key(), post.get(YMarkTables.BOOKMARK.DESC.key(),bmk_row.get(YMarkTables.BOOKMARK.DESC.key(),YMarkTables.BOOKMARK.DESC.deflt())).getBytes());
                    bmk_row.put(YMarkTables.BOOKMARK.PUBLIC.key(), post.get(YMarkTables.BOOKMARK.PUBLIC.key(),bmk_row.get(YMarkTables.BOOKMARK.PUBLIC.key(),YMarkTables.BOOKMARK.PUBLIC.deflt())).getBytes());
                   
                    HashSet<String> oldSet;
                    HashSet<String>newSet;
                    
                    final String foldersString = YMarkTables.cleanFoldersString(post.get(YMarkTables.BOOKMARK.FOLDERS.key(),YMarkTables.BOOKMARK.FOLDERS.deflt()));
                	oldSet = YMarkTables.keysStringToSet(bmk_row.get(YMarkTables.BOOKMARK.FOLDERS.key(),YMarkTables.BOOKMARK.FOLDERS.deflt()));
                	newSet = YMarkTables.keysStringToSet(foldersString);
                    updateIndex(folder_table, urlHash, oldSet, newSet);
                	bmk_row.put(YMarkTables.BOOKMARK.FOLDERS.key(), foldersString.getBytes());
                    
                    final String tagsString = YMarkTables.cleanTagsString(post.get(YMarkTables.BOOKMARK.TAGS.key(),YMarkTables.BOOKMARK.TAGS.deflt()));
                	oldSet = YMarkTables.keysStringToSet(bmk_row.get(YMarkTables.BOOKMARK.TAGS.key(),YMarkTables.BOOKMARK.TAGS.deflt()));
                	newSet = YMarkTables.keysStringToSet(tagsString);
                	updateIndex(tag_table, urlHash, oldSet, newSet);
                	bmk_row.put(YMarkTables.BOOKMARK.TAGS.key(), tagsString.getBytes());
                	            	
                    // modify date attribute
                    bmk_row.put(YMarkTables.BOOKMARK.DATE_MODIFIED.key(), date);                
                    
                    // update bmk_table
                    sb.tables.update(bmk_table, bmk_row);                 
                }
            } catch (IOException e) {
                Log.logException(e);
            }
            prop.put("result", "1");
        } else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
    }
	
	private static void updateIndex(final String index_table, final byte[] urlHash, final HashSet<String> oldSet, final HashSet<String> newSet) {
        Iterator <String> tagIter;        
        HashSet<String> urlSet = new HashSet<String>(newSet);
        
        newSet.removeAll(oldSet);
        tagIter = newSet.iterator();
        while(tagIter.hasNext()) {
        	sb.tables.bookmarks.updateIndexTable(index_table, tagIter.next(), urlHash, YMarkTables.INDEX_ACTION.ADD);
        }
        
        oldSet.removeAll(urlSet);
        tagIter=oldSet.iterator();
        while(tagIter.hasNext()) {
        	sb.tables.bookmarks.updateIndexTable(index_table, tagIter.next(), urlHash, YMarkTables.INDEX_ACTION.REMOVE);
        }  
	}
}
