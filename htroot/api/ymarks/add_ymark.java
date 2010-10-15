import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import de.anomic.data.YMarkStatics;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class add_ymark {
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	final String table = (isAuthUser ? user.getUserName() : "admin")+"_"+YMarkStatics.TABLE_BOOKMARKS_BASENAME;
        	
        	String url = post.get(YMarkStatics.TABLE_BOOKMARKS_COL_URL,"");
        	if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                url="http://"+url;
            }
            // generate the url hash
            byte[] pk = null;
    		try {
    			pk = (new DigestURI(url, null)).hash();
    		} catch (MalformedURLException e) {
    			Log.logException(e);
    		}
            assert pk != null;

            // read old entry from the bookmarks table (if exists)
            Tables.Row row = null;
            try {
                row = sb.tables.select(table, pk);
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
            
            // insert or update entry
            try {
                if (row == null) {
                    // create and insert new entry
                    Data data = new Data();
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_URL, url.getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,"").getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DESC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,"").getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,"false").getBytes());
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,"").getBytes());
                                    
                    byte[] date = DateFormatter.formatShortMilliSecond(new Date()).getBytes();
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_ADDED, date);
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_MODIFIED, date);
                    data.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_VISITED, date);

                    sb.tables.insert(table, pk, data);
                } else {
                    // modify and update existing entry
                    row.put(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,row.get(YMarkStatics.TABLE_BOOKMARKS_COL_TITLE,"")).getBytes());
                    row.put(YMarkStatics.TABLE_BOOKMARKS_COL_DESC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,row.get(YMarkStatics.TABLE_BOOKMARKS_COL_DESC,"")).getBytes());
                    row.put(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,row.get(YMarkStatics.TABLE_BOOKMARKS_COL_PUBLIC,"false")).getBytes());
                    row.put(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS, post.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,row.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,"")).getBytes());
                	            	
                    // modify date attribute
                    row.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_MODIFIED, DateFormatter.formatShortMilliSecond(new Date()).getBytes());                
                    
                    sb.tables.update(table, row);
                    assert pk != null;
                }
            } catch (IOException e) {
                Log.logException(e);
            }
            Log.logInfo(YMarkStatics.TABLE_BOOKMARKS_LOG, "insertBookmark: "+url);
            prop.put("result", "1");
        } else {
        	prop.put("AUTHENTICATE","Authentication required!");
        }
        // return rewrite properties
        return prop;
    }
}
