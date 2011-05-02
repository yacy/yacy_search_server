import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkDate;
import de.anomic.data.ymark.YMarkEntry;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.data.ymark.YMarkUtil;
import de.anomic.data.ymark.YMarkTables.TABLES;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class get_ymark {
	
	private static Switchboard sb = null;
	private static serverObjects prop = null;
	final static String FOLDER_IMG = "<img src=\"/yacy/ui/img/treeview/folder-closed.gif\" />";
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        sb = (Switchboard) env;
        prop = new serverObjects();
        
        int rp;         // items per page
        int page;                 // page
        int total;
        String sortorder;
        String sortname;
        String qtype;
        String query;
        
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
    	Iterator<Tables.Row> bookmarks = null;
        
        if(isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
        	
            query = ".*";
            qtype = YMarkEntry.BOOKMARK.TITLE.key();
            page = 1;
            rp = 10;
            total = 0;
            sortname = YMarkEntry.BOOKMARK.TITLE.key();
            sortorder = "asc";
        	
            if(post != null) {
                rp = (post.containsKey("rp")) ? post.getInt("rp", 10) : 10;
                page = (post.containsKey("page")) ? post.getInt("page", 1): 1;
                query = (post.containsKey("query")) ? post.get("query", ".*") : ".*";
                qtype = (post.containsKey("qtype")) ? post.get("qtype", YMarkEntry.BOOKMARK.TAGS.key()) : YMarkEntry.BOOKMARK.TAGS.key();
                sortname = (post.containsKey("sortname")) ? post.get("sortname", YMarkEntry.BOOKMARK.TITLE.key()) : YMarkEntry.BOOKMARK.TITLE.key();
                sortorder = (post.containsKey("sortorder")) ? post.get("sortorder", "asc") : "asc";
            }          
            try {
                final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
                final Collection<Row> result;
                if(!query.isEmpty()) {
                    if(!qtype.isEmpty()) {
                        if(qtype.equals("_tags")) {
                        	final String[] tagArray = YMarkUtil.cleanTagsString(query).split(YMarkUtil.TAGS_SEPARATOR);
                        	result = sb.tables.bookmarks.orderBookmarksBy(sb.tables.bookmarks.getBookmarksByTag(bmk_user, tagArray), sortname, sortorder);
                        } else if(qtype.equals("_folder")) {
                        	result = sb.tables.bookmarks.orderBookmarksBy(sb.tables.bookmarks.getBookmarksByFolder(bmk_user, query), sortname, sortorder);
                        } else {                
                        	result = sb.tables.bookmarks.orderBookmarksBy(sb.tables.iterator(bmk_table, qtype, Pattern.compile(query)), sortname, sortorder);
                        }
                    } else {
                    	result = sb.tables.bookmarks.orderBookmarksBy(sb.tables.iterator(bmk_table, Pattern.compile(query)), sortname, sortorder);
                    }
                } else {
                	result = sb.tables.bookmarks.orderBookmarksBy(sb.tables.iterator(bmk_table), sortname, sortorder);
                }
                total = result.size();
                bookmarks = result.iterator();
            } catch (IOException e) {
                Log.logException(e);
            }
            prop.put("page", page);
            prop.put("total", total);
	    	putProp(bookmarks, rp, page);
	    	
        } else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
	}
		
	private static void putProp(final Iterator<Tables.Row> bit, final int rp, final int page) {
	    Tables.Row bmk_row;
	    int count = 0;
        int offset = 0;
        if (page > 1) {
            offset = ((page - 1) * rp) + 1;
        }
        while(count < offset && bit.hasNext()) {
            bmk_row = bit.next();
            count++;
        }
        count = 0;
        while (count < rp && bit.hasNext()) {
            bmk_row = bit.next();
            if (bmk_row != null) {
                
                // put JSON
                prop.put("json_"+count+"_id", count);
                prop.put("json_"+count+"_hash", UTF8.String(bmk_row.getPK()));
                for (YMarkEntry.BOOKMARK bmk : YMarkEntry.BOOKMARK.values()) {
                    if(bmk == YMarkEntry.BOOKMARK.PUBLIC)
                        prop.put("json_"+count+"_"+bmk.key(), bmk_row.get(bmk.key(),bmk.deflt()).equals("true") ? 0 : 1);
                    else if(bmk == YMarkEntry.BOOKMARK.TAGS)
                    	prop.putJSON("json_"+count+"_"+bmk.key(), bmk_row.get(bmk.key(),bmk.deflt()).replaceAll(YMarkUtil.TAGS_SEPARATOR, ", "));
                    else if(bmk == YMarkEntry.BOOKMARK.FOLDERS)
                    	prop.putJSON("json_"+count+"_"+bmk.key(), bmk_row.get(bmk.key(),bmk.deflt()).replaceAll(YMarkUtil.TAGS_SEPARATOR, "<br />"+FOLDER_IMG));
                    else if(bmk == YMarkEntry.BOOKMARK.DATE_ADDED || bmk == YMarkEntry.BOOKMARK.DATE_MODIFIED || bmk == YMarkEntry.BOOKMARK.DATE_VISITED)
                    	prop.putJSON("json_"+count+"_"+bmk.key(), (new YMarkDate(bmk_row.get(bmk.key()))).toISO8601().replaceAll("T", "<br />"));
                    else
                        prop.putJSON("json_"+count+"_"+bmk.key(), bmk_row.get(bmk.key(),bmk.deflt()));
                }
                prop.put("json_"+count+"_comma", ",");
                
                // put XML
                prop.putXML("xml_"+count+"_id", UTF8.String(bmk_row.getPK()));
                for (YMarkEntry.BOOKMARK bmk : YMarkEntry.BOOKMARK.values()) {
                    prop.putXML("xml_"+count+"_"+bmk.key(), bmk_row.get(bmk.key(),bmk.deflt()));
                }
                
                count++;
            }
        }
        // eliminate the trailing comma for Json output
        prop.put("json_" + (count - 1) + "_comma", "");
        prop.put("json", count);
        prop.put("xml", count);
	}
}
