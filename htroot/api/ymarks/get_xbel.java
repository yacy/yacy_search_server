import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkDate;
import de.anomic.data.ymark.YMarkEntry;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.data.ymark.YMarkUtil;
import de.anomic.data.ymark.YMarkXBELImporter;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get_xbel {
	public static final String ROOT = "root";
	public static final String SOURCE = "source";

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		final serverObjects prop = new serverObjects();        
		final HashSet<String> alias = new HashSet<String>();
		final StringBuilder buffer = new StringBuilder(250);
		final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
		final String bmk_user;
        
        if(isAdmin || isAuthUser) {
        	bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
        	
        	String root = YMarkTables.FOLDERS_ROOT;  	
        	String[] foldername = null;
        	
        	// TODO: better handling of query
        	if (post != null){
        		if (post.containsKey(ROOT)) {
            		if (post.get(ROOT).equals(SOURCE) || post.get(ROOT).equals(YMarkTables.FOLDERS_ROOT)) {
            			root = "";
            		} else if (post.get(ROOT).startsWith(YMarkTables.FOLDERS_ROOT)) {
            			root = post.get(ROOT);
            		} else {
            			root = "";            			
            		}
        		}
        	} else {
        		root = "";
        	}
        	
        	final int root_depth = root.split(YMarkUtil.FOLDERS_SEPARATOR).length;
    		Iterator<String> fit = null;
        	Iterator<Tables.Row> bit = null;
        	int count = 0;    		
        	int n = root_depth;
        	
        	try {
				// fit = sb.tables.bookmarks.folders.getFolders(bmk_user, root);
        		fit = sb.tables.bookmarks.getFolders(bmk_user, root).iterator();
			} catch (IOException e) {
				Log.logException(e);
			}
			
			while (fit.hasNext()) {    		   		
        		String folder = fit.next();
        		foldername = folder.split(YMarkUtil.FOLDERS_SEPARATOR); 
        		if (n != root_depth && foldername.length <= n) {
					prop.put("xbel_"+count+"_elements", "</folder>");
            		count++;
        		}
        		if (foldername.length >= n) {
        			n = foldername.length;
        			if(n != root_depth) {
                		prop.put("xbel_"+count+"_elements", "<folder id=\"f:"+UTF8.String(YMarkUtil.getKeyId(foldername[n-1]))+"\">");
                		count++;
                		prop.put("xbel_"+count+"_elements", "<title>" + CharacterCoding.unicode2xml(foldername[n-1], true) + "</title>");   		
                		count++;	
        			}
            		// bit = sb.tables.bookmarks.folders.getBookmarkIds(bmk_user, folder).iterator();
					try {
						bit = sb.tables.bookmarks.getBookmarksByFolder(bmk_user, folder);
					} catch (IOException e) {
						// TODO: better error handling (avoid NPE)
						bit = null;
					}
					Tables.Row bmk_row = null;
					String urlHash;
					final YMarkDate date = new YMarkDate();
					while(bit.hasNext()){			
						// urlHash = bit.next();
						bmk_row = bit.next();
						urlHash = new String(bmk_row.getPK());
						
						if(alias.contains(urlHash)) {
							buffer.setLength(0);
							buffer.append(YMarkXBELImporter.XBEL.ALIAS.startTag(true));
							buffer.append(" ref=\"b:");
							buffer.append(urlHash);
							buffer.append("\"/>");            	    			
							prop.put("xbel_"+count+"_elements", buffer.toString()); 		
							count++;  	
						} else {
							alias.add(urlHash);
							// bmk_row = sb.tables.select(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), urlHash.getBytes());
					    	if(bmk_row != null) {
					    		buffer.setLength(0);
					    		
					    		buffer.append(YMarkXBELImporter.XBEL.BOOKMARK.startTag(true));
					    		buffer.append(" id=\"b:");
					    		buffer.append(urlHash);
					    		
					    		buffer.append(YMarkEntry.BOOKMARK.URL.xbel());
					    		buffer.append(CharacterCoding.unicode2xml(bmk_row.get(YMarkEntry.BOOKMARK.URL.key(), YMarkEntry.BOOKMARK.URL.deflt()), true));
					    		
					    		buffer.append(YMarkEntry.BOOKMARK.DATE_ADDED.xbel());
					    		date.set(bmk_row.get(YMarkEntry.BOOKMARK.DATE_ADDED.key()));
					    		buffer.append(CharacterCoding.unicode2xml(date.toISO8601(), true));
					    		
					    		buffer.append(YMarkEntry.BOOKMARK.DATE_MODIFIED.xbel());
					    		date.set(bmk_row.get(YMarkEntry.BOOKMARK.DATE_MODIFIED.key()));
					    		buffer.append(CharacterCoding.unicode2xml(date.toISO8601(), true));
					    		
					    		buffer.append(YMarkEntry.BOOKMARK.DATE_VISITED.xbel());
					    		date.set(bmk_row.get(YMarkEntry.BOOKMARK.DATE_VISITED.key()));
					    		buffer.append(CharacterCoding.unicode2xml(date.toISO8601(), true));
					    		
					    		buffer.append(YMarkEntry.BOOKMARK.TAGS.xbel());
					    		buffer.append(bmk_row.get(YMarkEntry.BOOKMARK.TAGS.key(), YMarkEntry.BOOKMARK.TAGS.deflt()));
					    		
					    		buffer.append(YMarkEntry.BOOKMARK.PUBLIC.xbel());
					    		buffer.append(bmk_row.get(YMarkEntry.BOOKMARK.PUBLIC.key(), YMarkEntry.BOOKMARK.PUBLIC.deflt()));
					    		
					    		buffer.append(YMarkEntry.BOOKMARK.VISITS.xbel());
					    		buffer.append(bmk_row.get(YMarkEntry.BOOKMARK.VISITS.key(), YMarkEntry.BOOKMARK.VISITS.deflt()));
					    		
					    		buffer.append("\"\n>");
					    		prop.put("xbel_"+count+"_elements", buffer.toString());
					    		count++; 
					    		
					    		buffer.setLength(0);
					    		buffer.append(YMarkXBELImporter.XBEL.TITLE.startTag(false));
					    		buffer.append(CharacterCoding.unicode2xml(bmk_row.get(YMarkEntry.BOOKMARK.TITLE.key(), YMarkEntry.BOOKMARK.TITLE.deflt()), true));
					    		buffer.append(YMarkXBELImporter.XBEL.TITLE.endTag(false));
					    		prop.put("xbel_"+count+"_elements", buffer.toString());
					    		count++;

					    		buffer.setLength(0);
					    		buffer.append(YMarkXBELImporter.XBEL.DESC.startTag(false));
					    		buffer.append(CharacterCoding.unicode2xml(bmk_row.get(YMarkEntry.BOOKMARK.DESC.key(), YMarkEntry.BOOKMARK.DESC.deflt()), true));
					    		buffer.append(YMarkXBELImporter.XBEL.DESC.endTag(false));
					    		prop.put("xbel_"+count+"_elements", buffer.toString());
					    		count++;
					    		
					    		prop.put("xbel_"+count+"_elements", YMarkXBELImporter.XBEL.BOOKMARK.endTag(false));   		
					    		count++;    
					    	}
						}
					}
        		}
        	}
			while(n > root_depth) {
				prop.put("xbel_"+count+"_elements", YMarkXBELImporter.XBEL.FOLDER.endTag(false));
	    		count++;
	    		n--;
			}
			prop.put("root", root);
    		prop.put("user", bmk_user.substring(0,1).toUpperCase() + bmk_user.substring(1));
    		prop.put("xbel", count);
    		
        }  else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }  
        // return rewrite properties
        return prop;
	}
}

	
