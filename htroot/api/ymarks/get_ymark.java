import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkStatics;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class get_ymark {
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);

    	final String bmk_table = (isAuthUser ? user.getUserName() : "admin")+"_"+YMarkStatics.TABLE_BOOKMARKS_BASENAME;
    	final String tag_table = (isAuthUser ? user.getUserName() : "admin")+"_"+YMarkStatics.TABLE_TAGS_BASENAME;
        
    	if(post.containsKey(YMarkStatics.TABLE_TAGS_COL_TAG)) {
    		final byte[] tagHash = YMarkStatics.getTagHash(post.get(YMarkStatics.TABLE_TAGS_COL_TAG));
            Tables.Row tag_row = null;
    		try {
				tag_row = sb.tables.select(tag_table, tagHash);
				if (tag_row != null) {
					final Iterator<String>urlIter = (YMarkStatics.keysStringToKeySet(new String(tag_row.get(YMarkStatics.TABLE_TAGS_COL_URLS)))).iterator();
					int count = 0;
					while(urlIter.hasNext()) {
						final byte[] urlHash = urlIter.next().getBytes();
						Tables.Row bmk_row = null;
			            bmk_row = sb.tables.select(bmk_table, urlHash);
			            if (bmk_row != null) {
				   			prop.putXML("bookmarks_"+count+"_link", new String(bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_URL,"")));
				   			prop.putXML("bookmarks_"+count+"_hash", new String(urlHash));
				   			prop.putXML("bookmarks_"+count+"_tags", new String(bmk_row.get(YMarkStatics.TABLE_BOOKMARKS_COL_TAGS,"")));
				            count++;
			            }
					}
					prop.put("bookmarks", count);
				}
				else {
	        		prop.put("result", "0");
        			return prop; 
				}
			} catch (IOException e) {
                Log.logException(e);
			} catch (RowSpaceExceededException e) {
                Log.logException(e);
			}
    	}
        // return rewrite properties
        return prop;
	}
}
