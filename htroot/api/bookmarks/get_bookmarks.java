

import java.util.Date;
import java.util.Iterator;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.DateFormatter;

import de.anomic.data.BookmarkHelper;
import de.anomic.data.BookmarksDB;
import de.anomic.data.UserDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get_bookmarks {    
	
	private static final serverObjects prop = new serverObjects();
	private static Switchboard sb = null;
	private static UserDB.Entry user = null;
	private static boolean isAdmin = false;
	
	private static int R = 1; // TODO: solve the recursion problem an remove global variable

	final static int SORT_ALPHA = 1;
	final static int SORT_SIZE = 2;	
	final static int SHOW_ALL = -1;
	final static int MAXRESULTS = 10000;
	
	// file types and display types
	final static int XML = 0;		// .xml
	final static int XHTML = 0;		// .html (.xml)
	final static int JSON = 0;		// .json	
	final static int FLEXIGRID = 1;	// .json .xml
	final static int XBEL = 2;		// .xml
	final static int RSS = 3;		// .xml (.rss)
	final static int RDF = 4;		// .xml
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        		
		prop.clear();
    	sb = (Switchboard) env;
    	user = sb.userDB.getUser(header);   
    	isAdmin = (sb.verifyAuthentication(header, true) || user != null && user.hasRight(UserDB.Entry.BOOKMARK_RIGHT));
    	    	    	
    	// set user name
    	final String username;
    	if(user != null) username=user.getUserName();
    	else if(isAdmin) username="admin";
    	else username = "unknown";
    	prop.putHTML("display_user", username);
    	
    	// set peer address    	
    	prop.put("display_address", sb.peers.mySeed().getPublicAddress());
    	prop.put("display_peer", sb.peers.mySeed().getName());
    	    	
		int rp = MAXRESULTS;			// items per page 
    	int page = 1;					// page    	
    	int display = 0;				// default for JSON, XML or XHTML
    	// String sortorder = "asc";
    	// String sortname = "date";
    	String qtype = "";
    	String query = "";
    	
    	// check for GET parameters
    	if (post != null){
    		if (post.containsKey("rp")) rp = Integer.parseInt(post.get("rp"));
    		if (post.containsKey("page")) page = Integer.parseInt(post.get("page"));
    		if (post.containsKey("query")) query = post.get("query");
    		if (post.containsKey("qtype")) qtype = post.get("qtype");
    		// if (post.containsKey("sortorder")) sortorder = post.get("sortorder");
    		if (post.containsKey("display")) {    	    	
    	    	if (post.get("display").equals("flexigrid") || post.get("display").equals("1")) {
    	    		display = FLEXIGRID;
    	    	}
    	    	else if (post.get("display").equals("xbel") || post.get("display").equals("2")) {
    	    		display = XBEL;
    	    	}
	    		else if (post.get("display").equals("rss") || post.get("display").equals("3")) {
    	    		display = RSS;    	    	
    	    	}
    		}    		
    		prop.put("display", display);
    	}
    	
    	int count = 0;
        int total = 0;
    	int start = 0;    	

    	final Iterator<String> it;
    	BookmarksDB.Bookmark bookmark;
       	
       	switch (display) {
       	case XBEL:       		
       		String root = "/";       		
       		if (qtype.equals("tags") && !query.equals("")) {
       			prop.putHTML("display_folder", "1");
       			prop.putHTML("display_folder_foldername", query);
       			prop.putHTML("display_folder_folderhash", BookmarkHelper.tagHash(query));       			
       			it = sb.bookmarksDB.getBookmarksIterator(query, isAdmin);
				count = print_XBEL(it, count);
				prop.put("display_xbel", count);
				break;
       		} else if (query.length() > 0 && qtype.equals("folders")) {       			
        		if (query.length() > 0 && query.charAt(0) == '/') { root = query; } 
        			else { root = "/" + query; }
       		}
       		prop.putHTML("display_folder", "0");
       		R = root.replaceAll("[^/]","").length() - 1;
        	count = recurseFolders(BookmarkHelper.getFolderList(root, sb.bookmarksDB.getTagIterator(isAdmin)),root,0,true,"");
            prop.put("display_xbel", count);        	 
       		break;
       	
       	default:
	    	// default covers all non XBEL formats
       		
       		// set bookmark iterator according to query
	       	if (qtype.equals("tags") && !query.equals("") && !query.equals("/")) {       		
	       		it = sb.bookmarksDB.getBookmarksIterator(query, isAdmin);
	       	} else {       		
	       		it = sb.bookmarksDB.getBookmarksIterator(isAdmin);
	       	}       	
	       	
       		if (rp < MAXRESULTS) {
	       		//skip the first entries (display next page)
		       	if (page > 1) {
		       		start = ((page-1)*rp)+1;
		       	} 
		       	count = 0;
		       	while(count < start && it.hasNext()){
		       		it.next();
		       		count++;
		       	}
		       	total += count;
       		}       		
	       	count = 0;
	       	while(count < rp && it.hasNext()){	       	       		
	       		bookmark = sb.bookmarksDB.getBookmark(it.next());
	       		if(bookmark!=null) {       			       			      			
	       			prop.put("display_bookmarks_"+count+"_id",count);
	       			prop.put("display_bookmarks_"+count+"_link",bookmark.getUrl());
	       			prop.put("display_bookmarks_"+count+"_date", DateFormatter.formatISO8601(new Date(bookmark.getTimeStamp())));
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
	       	}
			// eliminate the trailing comma for Json output
			rp--;
			prop.put("display_bookmarks_"+rp+"_comma", "");
			prop.put("display_bookmarks", count);
			
	       	while(it.hasNext()){
	       		it.next();
	       		count++;
	       	}
			total += count;
			prop.put("display_page", page);
			prop.put("display_total", total);
			break;
       	} // end switch
       	
        // return rewrite properties
        return prop;
    }
	
	private static int recurseFolders(final Iterator<String> it, String root, int count, final boolean next, final String prev){
    	String fn="";    	
    	
    	if(next) fn = it.next();    		
    	else fn = prev;

    	if(fn.equals("\uffff")) {
    		int i = prev.replaceAll("[^/]","").length() - R; 
    		while(i>0){
    			prop.put("display_xbel_"+count+"_elements", "</folder>");
    			count++;
    			i--;
    		}    		
    		return count;
    	}
   
    	if(fn.startsWith((root.equals("/") ? root : root+"/"))){
    		prop.put("display_xbel_"+count+"_elements", "<folder id=\""+BookmarkHelper.tagHash(fn)+"\">");
    		count++;
    		  		
    		final String title = fn; // just to make sure fn stays untouched    		
    		prop.put("display_xbel_"+count+"_elements", "<title>" + CharacterCoding.unicode2xml(title.replaceAll("(/.[^/]*)*/", ""), true) + "</title>");   		
    		count++;    
    		final Iterator<String> bit=sb.bookmarksDB.getBookmarksIterator(fn, isAdmin);
    		count = print_XBEL(bit, count);
    		if(it.hasNext()){
    			count = recurseFolders(it, fn, count, true, fn);
    		}
    	} else {		
    		if (count > 0) {
    			prop.put("display_xbel_"+count+"_elements", "</folder>");        		
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
			bookmark=sb.bookmarksDB.getBookmark(bit.next());
			date=new Date(bookmark.getTimeStamp());
			prop.put("display_xbel_"+count+"_elements", "<bookmark id=\"" + bookmark.getUrlHash()
					+ "\" href=\"" + CharacterCoding.unicode2xml(bookmark.getUrl(), true)
					+ "\" added=\"" + CharacterCoding.unicode2xml(DateFormatter.formatISO8601(date), true)+"\">");
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
		}
    	return count;
    }    
}