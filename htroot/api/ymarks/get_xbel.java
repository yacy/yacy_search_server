import java.io.IOException;
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.BookmarkHelper;
import de.anomic.data.YMarkIndex;
import de.anomic.data.YMarkTables;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get_xbel {
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

        	if (post != null){
        		if (post.containsKey(ROOT)) {
            		if (post.get(ROOT).equals(SOURCE) || post.get(ROOT).equals(YMarkTables.FOLDERS_ROOT)) {
            			root = "";
            		} else if (post.get(ROOT).startsWith(YMarkTables.FOLDERS_ROOT)) {
            			root = post.get(ROOT);
            		} else {
            			root = YMarkTables.FOLDERS_ROOT + post.get(ROOT);
            		}
        		}
        	}
        	
        	Iterator<String> it = null;
        	Tables.Row bmk_row = null;
        	int count = 0;
        	
        	// <![CDATA[ 
        	// ]]>
        	// loop through folderList  	
        	try {
				it = sb.tables.bookmarks.folders.getFolders(bmk_user, root);
			} catch (IOException e) {
				Log.logException(e);
			}
        	int n = YMarkIndex.getFolderDepth(root);;


        	while (it.hasNext()) {    		   		
        		String folder = it.next();
        		foldername = folder.split(YMarkTables.FOLDERS_SEPARATOR);
        		if (foldername.length == n+1) {
            		prop.put("xbel_"+count+"_elements", "<folder id=\""+new String(YMarkTables.getKeyId(foldername[n]))+"\">");
            		count++;
            		prop.put("xbel_"+count+"_elements", "<title>" + CharacterCoding.unicode2xml(foldername[n], true) + "</title>");   		
            		count++;
            		// print bookmars
        		}
        	}
        	
        }  else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }  
        // return rewrite properties
        return prop;
	}
}
