import java.io.IOException;
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkIndex;
import de.anomic.data.YMarkTables;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get_xbel {
	public static final String ROOT = "root";
	public static final String SOURCE = "source";
		
	static Switchboard sb;
	static serverObjects prop;
	static String bmk_user;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		sb = (Switchboard) env;
		prop = new serverObjects();        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
        	
        	String root = YMarkTables.FOLDERS_ROOT;  	
        	String[] foldername = null;

        	if (post != null){
        		if (post.containsKey(ROOT)) {
            		if (post.get(ROOT).equals(SOURCE) || post.get(ROOT).equals(YMarkTables.FOLDERS_ROOT)) {
            			root = "";
            		} else if (post.get(ROOT).startsWith(YMarkTables.FOLDERS_ROOT)) {
            			root = post.get(ROOT);
            		} else {
            			root = "";
            			// root = YMarkTables.FOLDERS_ROOT + post.get(ROOT);
            		}
        		}
        	}
        	
        	Iterator<String> it = null;
        	int count = 0;
			int n = YMarkIndex.getFolderDepth(root);
        	
        	try {
				it = sb.tables.bookmarks.folders.getFolders(bmk_user, root);
			} catch (IOException e) {
				Log.logException(e);
			}

			while (it.hasNext()) {    		   		
        		String folder = it.next();
        		foldername = folder.split(YMarkTables.FOLDERS_SEPARATOR);
        		Log.logInfo(YMarkTables.BOOKMARKS_LOG, "folder: "+folder+" getFolderDepth(folder): "+YMarkIndex.getFolderDepth(folder)+" n: "+n);
        		if (count > 0 && YMarkIndex.getFolderDepth(folder) <= n) {
					prop.put("xbel_"+count+"_elements", "</folder>");
            		count++;
        		} 
        		if (YMarkIndex.getFolderDepth(folder) >= n) {
        			n = YMarkIndex.getFolderDepth(folder);
            		prop.put("xbel_"+count+"_elements", "<folder id=\"f:"+new String(YMarkTables.getKeyId(foldername[n]))+"\">");
            		count++;
            		prop.put("xbel_"+count+"_elements", "<title>" + CharacterCoding.unicode2xml(foldername[n], true) + "</title>");   		
            		count++;
            		try {
						count = putBookmarks(folder, count);
					} catch (IOException e) {
						Log.logException(e);
						continue;
					} catch (RowSpaceExceededException e) {
						Log.logException(e);
						continue;
					}
        		}
        	}
			while(n >= YMarkIndex.getFolderDepth(root)) {
				prop.put("xbel_"+count+"_elements", "</folder>");
	    		count++;
	    		n--;
			}
    		prop.put("xbel", count);
        }  else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }  
        // return rewrite properties
        return prop;
	}
	public static int putBookmarks(final String folder, int count) throws IOException, RowSpaceExceededException {
		final Iterator<String> bit = sb.tables.bookmarks.folders.getBookmarks(bmk_user, folder).iterator();
    	Tables.Row bmk_row = null;
    	String urlHash;
		while(bit.hasNext()){ 
			urlHash = new String(bit.next());
    		bmk_row = sb.tables.select(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), urlHash.getBytes());
        	if(bmk_row != null) {
        		prop.put("xbel_"+count+"_elements", "<bookmark id=\"b:" + urlHash
						+ "\" href=\"" + CharacterCoding.unicode2xml(bmk_row.get(YMarkTables.BOOKMARK.URL.key(), YMarkTables.BOOKMARK.URL.deflt()), true)
						+ "\" added=\"" + CharacterCoding.unicode2xml(YMarkTables.getISO8601(bmk_row.get(YMarkTables.BOOKMARK.DATE_ADDED.key())), true)
						+ "\" modified=\"" + CharacterCoding.unicode2xml(YMarkTables.getISO8601(bmk_row.get(YMarkTables.BOOKMARK.DATE_MODIFIED.key())), true)
						+ "\" visited=\"" + CharacterCoding.unicode2xml(YMarkTables.getISO8601(bmk_row.get(YMarkTables.BOOKMARK.DATE_VISITED.key())), true)
						+"\">");
	    		count++; 
	    		prop.put("xbel_"+count+"_elements", "<title>"
	    				+ CharacterCoding.unicode2xml(bmk_row.get(YMarkTables.BOOKMARK.TITLE.key(), YMarkTables.BOOKMARK.TITLE.deflt()), true)
	    				+ "</title>");
	    		count++;
	    		prop.put("xbel_"+count+"_elements", "<info>");   		
	    		count++;
	    		prop.put("xbel_"+count+"_elements", "<metadata owner=\"YaCy\""
	    				+ " tags=\"" + bmk_row.get(YMarkTables.BOOKMARK.TAGS.key(), YMarkTables.BOOKMARK.TAGS.deflt()) +"\""
	    				+ " public=\"" + bmk_row.get(YMarkTables.BOOKMARK.PUBLIC.key(), YMarkTables.BOOKMARK.PUBLIC.deflt()) +"\""
	    				+ "/>");   		
	    		count++;
	    		prop.put("xbel_"+count+"_elements", "</info>");   		
	    		count++;
	    		prop.put("xbel_"+count+"_elements", "<desc>"
	    				+ CharacterCoding.unicode2xml(bmk_row.get(YMarkTables.BOOKMARK.DESC.key(), YMarkTables.BOOKMARK.DESC.deflt()), true)
	    				+ "</desc>");
	    		count++;
	    		prop.put("xbel_"+count+"_elements", "</bookmark>");   		
	    		count++;    
        	}
		}
    	return count;
    } 
}
