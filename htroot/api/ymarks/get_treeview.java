import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import de.anomic.data.YMarkTables;
import de.anomic.data.userDB;
import de.anomic.data.YMarkTables.METADATA;
import de.anomic.search.Segments;
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
        	boolean isBookmark = false;
        	boolean isMetadata = false;
        	boolean isWordCount = false;

        	if (post != null){
        		if (post.containsKey(ROOT)) {
            		if (post.get(ROOT).equals(SOURCE) || post.get(ROOT).equals(YMarkTables.FOLDERS_ROOT)) {
            			root = "";
            		} else if (post.get(ROOT).startsWith(YMarkTables.FOLDERS_ROOT)) {
            			root = post.get(ROOT);
            		} else if (post.get(ROOT).startsWith("b:")) {
            			isBookmark = true;
            			isFolder = false;
            		} else if (post.get(ROOT).startsWith("m:")) {
            			isMetadata = true;
            			isFolder = false;
            		} else if (post.get(ROOT).startsWith("w:")) {
            			isWordCount = true;
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
					it = sb.tables.bookmarks.folders.getBookmarkIds(bmk_user, root).iterator();
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
			        		prop.put("folders_"+count+"_hash", "b:"+urlHash);
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
	        } else if(isBookmark) {
	        	try {
					final String urlHash = post.get(ROOT).substring(2);
	        		String url = "";
					bmk_row = sb.tables.select(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), urlHash.getBytes());
					if(bmk_row != null) {
			            it = bmk_row.keySet().iterator();
			            while(it.hasNext()) {
			            	final String key = it.next();
			            	if(key.startsWith("date")) {
				            	final String d = new String(bmk_row.get(key));
				            	if(!d.isEmpty()) {
				            		final String date = DateFormatter.formatISO8601(new Date(Long.parseLong(d)));
					            	prop.put("folders_"+count+"_foldername","<small><b>"+key+":</b> " + date + "</small>");
			    					putProp(count, "date");
			    					count++;
				            	}
			            	} else {
								final String value = new String(bmk_row.get(key));
								if (key.equals("url"))
									url = value;
								prop.put("folders_"+count+"_foldername","<small><b>"+key+":</b> " + value + "</small>");								
								if(YMarkTables.BOOKMARK.contains(key))
									putProp(count, YMarkTables.BOOKMARK.get(key).type());
								else
									putProp(count, "meta");
								count++;	
			            	}
			            }
			            prop.put("folders_"+count+"_foldername","<small><b>MetaData</b></small>");
			            putProp(count, "meta");
			            prop.put("folders_"+count+"_hash", "m:"+url);
			    		prop.put("folders_"+count+"_hasChildren", "true");
			            count++;
			            prop.put("folders_"+count+"_foldername","<small><b>WordCount</b></small>");
			            putProp(count, "meta");
			            prop.put("folders_"+count+"_hash", "w:"+url);
			    		prop.put("folders_"+count+"_hasChildren", "true");
						prop.put("folders_"+count+"_comma", "");
			    		count++;	
		        		prop.put("folders", count);
					}
				} catch (IOException e) {
					Log.logException(e);
				} catch (RowSpaceExceededException e) {
					Log.logException(e);
				}
	        } else if (isWordCount) {
	        	try {
					final Map<String, Integer> words = YMarkTables.getWordFrequencies(post.get(ROOT).substring(2), sb.loader);
					final Iterator<String> iter = words.keySet().iterator();
					while (iter.hasNext()) {
						String key = iter.next();
						int value = words.get(key);
						if(value > 5 && value < 15) {
							prop.put("folders_"+count+"_foldername","<small><b>"+key+":</b> [" + value + "]</small>");
	    					putProp(count, "meta");
	    					count++;	
						}
					}
					count--;
					prop.put("folders_"+count+"_comma", "");
					count++;
	        		prop.put("folders", count);
				} catch (MalformedURLException e) {
					Log.logException(e);
				}
	        } else if (isMetadata) {
				try {
					final String url = post.get(ROOT).substring(2);
					EnumMap<METADATA, String> metadata;
					metadata = YMarkTables.getMetadata(YMarkTables.getBookmarkId(url), sb.indexSegments.segment(Segments.Process.PUBLIC));
					if (metadata.isEmpty())
						metadata = YMarkTables.loadMetadata(url, sb.loader);
					final Iterator<METADATA> iter = metadata.keySet().iterator();
					while (iter.hasNext()) {
						final METADATA key = iter.next();
						final String value = metadata.get(key);
						prop.put("folders_"+count+"_foldername","<small><b>"+key.toString().toLowerCase()+":</b> " + value + "</small>");
						putProp(count, "meta");
						count++;
					}
					count--;
					prop.put("folders_"+count+"_comma", "");
					count++;
	        		prop.put("folders", count);
				} catch (MalformedURLException e) {
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
