import java.io.IOException;
import java.util.Iterator;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.UserDB;
import net.yacy.data.ymark.YMarkEntry;
import net.yacy.data.ymark.YMarkRDF;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.data.ymark.YMarkTables.TABLES;
import net.yacy.kelondro.blob.Tables;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class YMarks {
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);

        final String path = header.get(HeaderFramework.CONNECTION_PROP_PATH);
        if(path != null && path.endsWith(".rdf")) {
            YMarkRDF rdf = new YMarkRDF("http://"+sb.peers.myAlternativeAddress());
            
            if(post != null && post.containsKey(YMarkEntry.BOOKMARKS_ID)) {
            	final String id[] = post.get(YMarkEntry.BOOKMARKS_ID).split(":");
            	if(id[1].equals("b")) {
                	final String bmk_user = id[0];
                	final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
                	final byte[] urlHash = UTF8.getBytes(id[2]);
                	Tables.Row bmk_row;
    				try {
    					bmk_row = sb.tables.select(bmk_table, urlHash);
    		           	rdf.addBookmark(bmk_user, bmk_row);
    				} catch (final IOException e) {
    				} catch (final SpaceExceededException e) {
    				}    
            	}
            } else {
            	final Iterator<String> iter = sb.tables.iterator();
            	while(iter.hasNext()) {
            		final String bmk_table = iter.next();
            		final int i = bmk_table.indexOf(TABLES.BOOKMARKS.basename());
            		if(i > 0) {
                		final String bmk_user = bmk_table.substring(0, i);
                		try {
            				// TODO select only public bookmarks
                			rdf.addBookmarks(bmk_user, sb.tables.iterator(bmk_table));
            			} catch (final IOException e) {
            				// TODO exception handling
            			}
                	}
            	}	
            }
			prop.put("rdf", rdf.getRDF("RDF/XML-ABBREV"));
            return prop;
        }        
        if(isAdmin || isAuthUser) {
        	prop.put("login", 1);
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
        	prop.putHTML("user", bmk_user.substring(0,1).toUpperCase() + bmk_user.substring(1));
            int size;
			try {
				size = sb.tables.bookmarks.getSize(bmk_user);
			} catch (final IOException e) {
				ConcurrentLog.logException(e);
				size = 0;
			}
            prop.put("size", size);
        } else {
        	prop.put("login", 0);
        }        
        return prop;
	}
}