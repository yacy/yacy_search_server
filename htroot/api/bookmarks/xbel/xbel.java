

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.BookmarkHelper;
import net.yacy.data.BookmarksDB;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class xbel {

	private static final serverObjects prop = new serverObjects();
	private static Switchboard switchboard = null;
	private static boolean isAdmin = false;
	private static int R = 1; // TODO: solve the recursion problem an remove global variable

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

    	int count = 0;
    	String root = "/";
    	int style = 0;

    	prop.clear();
    	switchboard = (Switchboard) env;
    	isAdmin=switchboard.verifyAuthentication(header);

    	if(post != null) {
    		if(!isAdmin) {
    			if(post.containsKey("login")) {
                	prop.authenticationRequired();
    			}
    		}
        	if(post.containsKey("tag")) {
    			final String tagName=post.get("tag");
    			prop.putHTML("folder", tagName);
    			if (!tagName.equals("")) {
    				final Iterator<String> bit=switchboard.bookmarksDB.getBookmarksIterator(tagName, isAdmin);
    				count = print_XBEL(bit, count);
    				prop.put("xbel", count);
    				return prop;
    			}
        	}
        	if(post.containsKey("folder")) {
        		final String folderName=post.get("folder");
        		if (folderName.length() > 0 && folderName.charAt(0) == '/') { root = folderName; }
        			else { root = "/" + folderName; }
        	}
        	if(post.containsKey("style") && !post.get("style").equals("")) {
        		style = 1;
        		prop.putHTML("style_href", post.get("style"));
        		prop.putHTML("style_type", post.get("style").replaceAll("^.*\\.", ""));
        	}
    	}
    	prop.put("style", style);
    	R = root.replaceAll("[^/]","").length() - 1;
    	count = recurseFolders(BookmarkHelper.getFolderList(root, switchboard.bookmarksDB.getTagIterator(isAdmin)),root,0,true,"");
        prop.put("xbel", count);
    	return prop;    // return from serverObjects respond()
    }

    private static int recurseFolders(final Iterator<String> it, String root, int count, final boolean next, final String prev){
    	String fn="";

    	if(next) fn = it.next();
    	else fn = prev;

    	if(fn.equals("\uffff")) {
    		int i = prev.replaceAll("[^/]","").length() - R;
    		while(i>0){
    			prop.put("xbel_"+count+"_elements", "</folder>");
    			count++;
    			i--;
    		}
    		return count;
    	}

    	if(fn.startsWith((root.equals("/") ? root : root+"/"))){
    		prop.put("xbel_"+count+"_elements", "<folder id=\""+BookmarkHelper.tagHash(fn)+"\">");
    		count++;

    		final String title = fn; // just to make sure fn stays untouched
    		prop.put("xbel_"+count+"_elements", "<title>" + CharacterCoding.unicode2xml(title.replaceAll("(/.[^/]*)*/", ""), true) + "</title>");
    		count++;
    		final Iterator<String> bit=switchboard.bookmarksDB.getBookmarksIterator(fn, isAdmin);
    		count = print_XBEL(bit, count);
    		if(it.hasNext()){
    			count = recurseFolders(it, fn, count, true, fn);
    		}
    	} else {
    		if (count > 0) {
    			prop.put("xbel_"+count+"_elements", "</folder>");
        		count++;
    		}
    		root = root.replaceAll("(/.[^/]*$)", "");
    		if(root.equals("")) root = "/";
    		count = recurseFolders(it, root, count, false, fn);
    	}
    	return count;
    }
    private static int print_XBEL(final Iterator<String> bit, int count) {
    	BookmarksDB.Bookmark bookmark;
    	Date date;
    	while(bit.hasNext()){
			try {
                bookmark=switchboard.bookmarksDB.getBookmark(bit.next());
                date=new Date(bookmark.getTimeStamp());
                prop.put("xbel_"+count+"_elements", "<bookmark id=\"" + bookmark.getUrlHash()
                        + "\" href=\"" + CharacterCoding.unicode2xml(bookmark.getUrl(), true)
                        + "\" added=\"" + CharacterCoding.unicode2xml(ISO8601Formatter.FORMATTER.format(date), true)+"\">");
                count++;
                prop.put("xbel_"+count+"_elements", "<title>");
                count++;
                prop.putXML("xbel_"+count+"_elements", bookmark.getTitle());
                count++;
                prop.put("xbel_"+count+"_elements", "</title>");
                count++;
                prop.put("xbel_"+count+"_elements", "<info>");
                count++;
                prop.put("xbel_"+count+"_elements", "<metadata owner=\"Mozilla\" ShortcutURL=\""
                    + CharacterCoding.unicode2xml(bookmark.getTagsString().replaceAll("/.*,", "").toLowerCase(), true)
                    + "\"/>");
                count++;
                prop.put("xbel_"+count+"_elements", "<metadata owner=\"YaCy\" public=\""+Boolean.toString(bookmark.getPublic())+"\"/>");
                count++;
                prop.put("xbel_"+count+"_elements", "</info>");
                count++;
                prop.put("xbel_"+count+"_elements", "<desc>");
                count++;
                prop.putXML("xbel_"+count+"_elements", bookmark.getDescription());
                count++;
                prop.put("xbel_"+count+"_elements", "</desc>");
                count++;
                prop.put("xbel_"+count+"_elements", "</bookmark>");
                count++;
            } catch (final IOException e) {
            }
		}
    	return count;
    }
}



