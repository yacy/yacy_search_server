

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.BookmarkHelper;
import net.yacy.data.BookmarksDB;
import net.yacy.data.UserDB;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class get_bookmarks {

    private static final serverObjects prop = new serverObjects();
    private static Switchboard sb = null;
    private static UserDB.Entry user = null;
    private static boolean isAdmin = false;

    private static int R = 1; // TODO: solve the recursion problem an remove global variable

    /*
    private final static int SORT_ALPHA = 1;
    private final static int SORT_SIZE = 2;
    private final static int SHOW_ALL = -1;
    */

    private final static int MAXRESULTS = 10000;

    // file types and display types
    private enum DisplayType {
        XML(0),         // .xml
        XHTML(0),       // .html (.xml)
        JSON(0),        // .json
        FLEXIGRID(1),   // .json .xml
        XBEL(2),        // .xml
        RSS(3),         // .xml (.rss)
        RDF(4);         // .xml

        private final int value;

        DisplayType(final int value) {
            this.value = value;
        }

        int getValue() {
            return this.value;
        }
    }

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        prop.clear();
        sb = (Switchboard) env;
        user = sb.userDB.getUser(header);
        isAdmin = (sb.verifyAuthentication(header) || user != null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT));

        // set user name
        final String username;
        if(user != null) username=user.getUserName();
        else if(isAdmin) username="admin";
        else username = "unknown";
        prop.putHTML("display_user", username);

        // set peer address
        prop.put("display_address", sb.peers.mySeed().getPublicAddress());
        prop.put("display_peer", sb.peers.mySeed().getName());

        final int itemsPerPage;         // items per page
        final int page;                 // page
        final int display;              // default for JSON, XML or XHTML
        // String sortorder = "asc";
        // String sortname = "date";
        final String qtype;
        final String query;

        // check for GET parameters
        if (post != null){
            itemsPerPage = (post.containsKey("rp")) ? post.getInt("rp", MAXRESULTS) : MAXRESULTS;
            page = (post.containsKey("page")) ? post.getInt("page", 1): 1;
            query = (post.containsKey("query")) ? post.get("query", "") : "";
            qtype = (post.containsKey("qtype")) ? post.get("qtype", "") : "";
            // if (post.containsKey("sortorder")) sortorder = post.get("sortorder");
            if (post.containsKey("display")) {
                final String d = post.get("display");
                if ("flexigrid".equals(d) || "1".equals(d)) {
                    display = DisplayType.FLEXIGRID.getValue();
                } else if ("xbel".equals(d) || "2".equals(d)) {
                    display = DisplayType.XBEL.getValue();
                } else if ("rss".equals(d) || "3".equals(d)) {
                    display = DisplayType.RSS.getValue();
                } else {
                    display = DisplayType.XML.getValue();
                }
            } else {
                display = DisplayType.XML.getValue();
            }
            prop.put("display", display);
        } else {
            query = "";
            qtype = "";
            page = 1;
            itemsPerPage = MAXRESULTS;
            display = DisplayType.XML.getValue();
        }

        int count = 0;
        int total = 0;
        int start = 0;

        final Iterator<String> it;

        if (display == DisplayType.XBEL.getValue()) {
            String root = "/";
            if ("tags".equals(qtype) && !"".equals(query)) {
                prop.putHTML("display_folder", "1");
                prop.putHTML("display_folder_foldername", query);
                prop.putHTML("display_folder_folderhash", BookmarkHelper.tagHash(query));
                it = sb.bookmarksDB.getBookmarksIterator(query, isAdmin);
                count = print_XBEL(it, count);
                prop.put("display_xbel", count);
            } else if (query.length() > 0 && "folders".equals(qtype)) {
                root = (query.charAt(0) == '/') ? query : "/" + query;
            }
            prop.putHTML("display_folder", "0");
            R = root.replaceAll("[^/]","").length() - 1;
            count = recurseFolders(BookmarkHelper.getFolderList(root, sb.bookmarksDB.getTagIterator(isAdmin)),root,0,true,"");
            prop.put("display_xbel", count);
        } else {
            // covers all non XBEL formats

            // set bookmark iterator according to query
            if ("tags".equals(qtype) && !"".equals(query) && !"/".equals(query)) {
                it = sb.bookmarksDB.getBookmarksIterator(query, isAdmin);
            } else {
                it = sb.bookmarksDB.getBookmarksIterator(isAdmin);
            }

            if (itemsPerPage < MAXRESULTS) {
                //skip the first entries (display next page)
                if (page > 1) {
                    start = ((page - 1) * itemsPerPage) + 1;
                }
                count = 0;
                while (count < start && it.hasNext()) {
                    it.next();
                    count++;
                }
                total += count;
            }
            count = 0;
            BookmarksDB.Bookmark bookmark = null;
            while (count < itemsPerPage && it.hasNext()) {
                try {
                    bookmark = sb.bookmarksDB.getBookmark(it.next());
                    if (bookmark != null) {
                        prop.put("display_bookmarks_"+count+"_id",count);
                        prop.put("display_bookmarks_"+count+"_link",bookmark.getUrl());
                        prop.put("display_bookmarks_"+count+"_date", ISO8601Formatter.FORMATTER.format(new Date(bookmark.getTimeStamp())));
                        prop.put("display_bookmarks_"+count+"_rfc822date", HeaderFramework.formatRFC1123(new Date(bookmark.getTimeStamp())));
                        prop.put("display_bookmarks_"+count+"_public", (bookmark.getPublic() ? "0" : "1"));
                        prop.put("display_bookmarks_"+count+"_hash", bookmark.getUrlHash());
                        prop.put("display_bookmarks_"+count+"_comma", ",");

                        // offer HTML encoded
                        prop.putHTML("display_bookmarks_"+count+"_title-html", bookmark.getTitle());
                        prop.putHTML("display_bookmarks_"+count+"_desc-html", bookmark.getDescription());
                        prop.putHTML("display_bookmarks_"+count+"_tags-html", bookmark.getTagsString().replaceAll(",", ", "));
                        prop.putHTML("display_bookmarks_"+count+"_folders-html", (bookmark.getFoldersString()));

                        // XML encoded
                        prop.putXML("display_bookmarks_"+count+"_title-xml", bookmark.getTitle());
                        prop.putXML("display_bookmarks_"+count+"_desc-xml", bookmark.getDescription());
                        prop.putXML("display_bookmarks_"+count+"_tags-xml", bookmark.getTagsString());
                        prop.putXML("display_bookmarks_"+count+"_folders-xml", (bookmark.getFoldersString()));

                        // and plain text (potentially unsecure)
                        prop.put("display_bookmarks_"+count+"_title", bookmark.getTitle());
                        prop.put("display_bookmarks_"+count+"_desc", bookmark.getDescription());
                        prop.put("display_bookmarks_"+count+"_tags", bookmark.getTagsString());
                        prop.put("display_bookmarks_"+count+"_folders", (bookmark.getFoldersString()));

                        count++;
                    }
                } catch (final IOException e) {
                }
            }
            // eliminate the trailing comma for Json output

            prop.put("display_bookmarks_" + (itemsPerPage - 1) + "_comma", "");
            prop.put("display_bookmarks", count);

            while(it.hasNext()){
                it.next();
                count++;
            }
            total += count;
            prop.put("display_page", page);
            prop.put("display_total", total);
        }

        // return rewrite properties
        return prop;
    }

    private static int recurseFolders(final Iterator<String> it, String root, int count, final boolean next, final String prev){
    	String fn="";

    	if (next) fn = it.next();
    	else fn = prev;

    	if ("\uffff".equals(fn)) {
            int i = prev.replaceAll("[^/]","").length() - R;
            while (i > 0) {
                prop.put("display_xbel_"+count+"_elements", "</folder>");
                count++;
                i--;
            }
            return count;
    	}

    	if (fn.startsWith(("/".equals(root) ? root : root + "/"))) {
            prop.put("display_xbel_"+count+"_elements", "<folder id=\""+BookmarkHelper.tagHash(fn)+"\">");
            count++;

            final String title = fn; // just to make sure fn stays untouched
            prop.put("display_xbel_"+count+"_elements", "<title>" + CharacterCoding.unicode2xml(title.replaceAll("(/.[^/]*)*/", ""), true) + "</title>");
            count++;
            final Iterator<String> bit=sb.bookmarksDB.getBookmarksIterator(fn, isAdmin);
            count = print_XBEL(bit, count);
            if (it.hasNext()) {
                    count = recurseFolders(it, fn, count, true, fn);
            }
    	} else {
            if (count > 0) {
                prop.put("display_xbel_"+count+"_elements", "</folder>");
                count++;
            }
            root = root.replaceAll("(/.[^/]*$)", "");
            if ("".equals(root)) {
                root = "/";
            }
            count = recurseFolders(it, root, count, false, fn);
    	}
    	return count;
    }

    private static int print_XBEL(final Iterator<String> bit, int count) {
    	BookmarksDB.Bookmark bookmark = null;
    	Date date;
    	while(bit.hasNext()){
            try {
                bookmark = sb.bookmarksDB.getBookmark(bit.next());
                date = new Date(bookmark.getTimeStamp());
                prop.put("display_xbel_"+count+"_elements", "<bookmark id=\"" + bookmark.getUrlHash()
                    + "\" href=\"" + CharacterCoding.unicode2xml(bookmark.getUrl(), true)
                    + "\" added=\"" + CharacterCoding.unicode2xml(ISO8601Formatter.FORMATTER.format(date), true)+"\">");
                count++;
                prop.put("display_xbel_"+count+"_elements", "<title>");
                count++;
                prop.putXML("display_xbel_"+count+"_elements", bookmark.getTitle());
                count++;
                prop.put("display_xbel_"+count+"_elements", "</title>");
                count++;
                prop.put("display_xbel_"+count+"_elements", "<info>");
                count++;
                prop.put("display_xbel_"+count+"_elements", "<metadata owner=\"Mozilla\" ShortcutURL=\""
                    + CharacterCoding.unicode2xml(bookmark.getTagsString().replaceAll("/.*,", "").toLowerCase(), true)
                    + "\"/>");
                count++;
                prop.put("display_xbel_"+count+"_elements", "<metadata owner=\"YaCy\" public=\""+Boolean.toString(bookmark.getPublic())+"\"/>");
                count++;
                prop.put("display_xbel_"+count+"_elements", "</info>");
                count++;
                prop.put("display_xbel_"+count+"_elements", "<desc>");
                count++;
                prop.putXML("display_xbel_"+count+"_elements", bookmark.getDescription());
                count++;
                prop.put("display_xbel_"+count+"_elements", "</desc>");
                count++;
                prop.put("display_xbel_"+count+"_elements", "</bookmark>");
                count++;
            } catch (final IOException e) {
            }
        }
    	return count;
    }
}