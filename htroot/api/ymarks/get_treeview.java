import java.io.IOException;
import java.util.Date;
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import de.anomic.data.YMarkTables;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get_treeview {    
	
	public static final String ROOT = "root";
	public static final String SOURCE = "source";
		
	static serverObjects prop;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		prop = new serverObjects();        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);

        	
        	String root = YMarkTables.FOLDERS_ROOT;  	
        	String[] foldername = null;
        	boolean isFolder = true;

        	if (post != null){
        		if (post.containsKey(ROOT)) {
            		if (post.get(ROOT).equals(SOURCE) || post.get(ROOT).equals(YMarkTables.FOLDERS_ROOT)) {
            			root = "";
            		} else if (post.get(ROOT).startsWith(YMarkTables.FOLDERS_ROOT)) {
            			root = post.get(ROOT);
            		} else {
            			// root = YMarkTables.FOLDERS_ROOT + post.get(ROOT);
            			isFolder = false;
            		}
        		}
        	}
        	
        	Iterator<String> it = null;
        	Tables.Row bmk_row = null;
        	int count = 0;
        	
        	if(isFolder) {
	        	// loop through folderList  	
	        	try {
					it = sb.tables.bookmarks.folders.getFolders(bmk_user, root);
				} catch (IOException e) {
					Log.logException(e);
				}
	        	int n = root.split(YMarkTables.FOLDERS_SEPARATOR).length;
	        	if (n == 0) n = 1;
	        	while (it.hasNext()) {    		   		
	        		String folder = it.next();
	        		foldername = folder.split(YMarkTables.FOLDERS_SEPARATOR);
	        		if (foldername.length == n+1) {
	        			prop.put("folders_"+count+"_foldername", foldername[n]);
	    	    		prop.put("folders_"+count+"_expanded", "false");
	    	    		prop.put("folders_"+count+"_type", "folder");
	    	    		prop.put("folders_"+count+"_hash", folder);				//TODO: switch from pathString to folderHash
	    	    		prop.put("folders_"+count+"_url", "");					//TODO: insert folder url
	    	    		prop.put("folders_"+count+"_hasChildren", "true");		//TODO: determine if folder has children
	    	    		prop.put("folders_"+count+"_comma", ",");
	    	    		count++;
	        		}
	        	}
	        	// loop through bookmarkList
	        	try {
					it = sb.tables.bookmarks.folders.getBookmarks(bmk_user, root).iterator();
		        	while (it.hasNext()) {
		        		final String urlHash = new String(it.next());
		        		bmk_row = sb.tables.select(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), urlHash.getBytes());
			        	if(bmk_row != null) {
			        		final String url = new String(bmk_row.get(YMarkTables.BOOKMARK.URL.key()));
			        		final String title = new String(bmk_row.get(YMarkTables.BOOKMARK.TITLE.key(), YMarkTables.BOOKMARK.TITLE.deflt()));
			        			
			        		// TODO: get_treeview - get rid of bmtype
			        		if (post.containsKey("bmtype")) {    			 
			        			if (post.get("bmtype").equals("title")) {
			        				prop.put("folders_"+count+"_foldername", title);
			        			} else if (post.get("bmtype").equals("href")) {
			        				prop.put("folders_"+count+"_foldername", 
			        						"<a href='"+url+" 'target='_blank'>"+title+"</a>");
			        			} 
			        		} else {
			        				prop.put("folders_"+count+"_foldername", url);
		        			}
			        		prop.put("folders_"+count+"_expanded", "false");
			        		prop.put("folders_"+count+"_url", url);
			        		prop.put("folders_"+count+"_type", "file");
			        		prop.put("folders_"+count+"_hash", urlHash);
			        		prop.put("folders_"+count+"_hasChildren", "true");
			        		prop.put("folders_"+count+"_comma", ",");
			        		count++;   
			        	}
		        	} 
		        	count--;
		        	prop.put("folders_"+count+"_comma", "");
		        	count++;
		        	prop.put("folders", count);
				} catch (IOException e) {
					Log.logException(e);
				} catch (RowSpaceExceededException e) {
					Log.logException(e);
				}
	        } else {
	        	try {
					bmk_row = sb.tables.select(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), post.get(ROOT).getBytes());
					if(bmk_row != null) {
			            it = bmk_row.keySet().iterator();
			            while(it.hasNext()) {
			            	final String key = it.next();
			            	if(key.startsWith("date")) {
				            	final String date = DateFormatter.formatISO8601(new Date(Long.parseLong(new String(bmk_row.get(key)))));
				            	prop.put("folders_"+count+"_foldername","<small><b>"+key+":</b> " + date + "</small>");
		    					putProp(count, "date");
		    					count++;
			            	} else {
								final String value = new String(bmk_row.get(key));
								prop.put("folders_"+count+"_foldername","<small><b>"+key+":</b> " + value + "</small>");								
								if(YMarkTables.BOOKMARK.contains(key))
									putProp(count, YMarkTables.BOOKMARK.get(key).type());
								else
									putProp(count, "meta");
								count++;	
			            	}
			            }
			            count--;
    		        	prop.put("folders_"+count+"_comma", "");
    		        	count++;	
		        		prop.put("folders", count);
					}
				} catch (IOException e) {
					Log.logException(e);
				} catch (RowSpaceExceededException e) {
					Log.logException(e);
				}
	        }
        } else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }  
        // return rewrite properties
        return prop;
	}
	public static void putProp(final int count, final String type) {
		prop.put("folders_"+count+"_expanded", "false");
		prop.put("folders_"+count+"_url", "");
		prop.put("folders_"+count+"_type", type);
		prop.put("folders_"+count+"_hash", "");
		prop.put("folders_"+count+"_hasChildren", "false");
		prop.put("folders_"+count+"_comma", ",");
	}
}
