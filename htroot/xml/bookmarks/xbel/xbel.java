package xml.bookmarks.xbel;

import java.util.Date;
import java.util.Iterator;

import de.anomic.data.bookmarksDB;
import de.anomic.data.htmlTools;
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class xbel {

	private static final serverObjects prop = new serverObjects();
	private static plasmaSwitchboard switchboard = null;
	private static boolean isAdmin = false;
	private static int R = 1; // TODO: solve the recursion problem an remove global variable
	
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
 
    	int count = 0;;
    	String root = "/";
    	String style = "";
    	
    	prop.clear();
    	switchboard = (plasmaSwitchboard) env;    	
    	isAdmin=switchboard.verifyAuthentication(header, true);   

    	if(post != null) {
    		if(!isAdmin) {
    			if(post.containsKey("login")) {
    				prop.put("AUTHENTICATE","admin log-in");
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
        		if (folderName.startsWith("/")) { root = folderName; } 
        			else { root = "/" + folderName; }
        	}
        	if(post.containsKey("style") && !post.get("style").equals("")) {
        		style = "<?xml-stylesheet type=\"text/xsl\" href=\""+htmlTools.encodeUnicode2xml(post.get("style"))+"\" ?>";
        	} 
    	}
    	prop.put("style", style);
    	R = root.replaceAll("[^/]","").length() - 1;
    	count = recurseFolders(switchboard.bookmarksDB.getFolderList(root, isAdmin),root,0,true,"");
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
   
    	if(fn.startsWith(root)){
    		prop.put("xbel_"+count+"_elements", "<folder id=\""+bookmarksDB.tagHash(fn)+"\">");
    		count++;
    		  		
    		prop.put("xbel_"+count+"_elements", "<title>" + htmlTools.encodeUnicode2xml(fn.replaceAll("(/.[^/]*)*/", "")) + "</title>");   		
    		count++;    
    		final Iterator<String> bit=switchboard.bookmarksDB.getBookmarksIterator(fn, isAdmin);
    		count = print_XBEL(bit, count);
    		if(it.hasNext()){
    			count = recurseFolders(it, fn, count, true, fn);
    		}
    	} else {		
    		prop.put("xbel_"+count+"_elements", "</folder>");        		
    		count++;
    		root = root.replaceAll("(/.[^/]*$)", ""); 		
    		if(root.equals("")) root = "/";    		
    		count = recurseFolders(it, root, count, false, fn);
    	} 
    	return count;
    }
    private static int print_XBEL(final Iterator<String> bit, int count) {
    	bookmarksDB.Bookmark bookmark;
    	Date date;
    	while(bit.hasNext()){    			
			bookmark=switchboard.bookmarksDB.getBookmark(bit.next());
			date=new Date(bookmark.getTimeStamp());
			prop.put("xbel_"+count+"_elements", "<bookmark id=\"" + bookmark.getUrlHash()
					+ "\" href=\"" + htmlTools.encodeUnicode2xml(bookmark.getUrl())
					+ "\" added=\"" + htmlTools.encodeUnicode2xml(serverDate.formatISO8601(date))+"\">");
    		count++; 
    		prop.put("xbel_"+count+"_elements", "<title>");
    		count++;
    		prop.putHTML("xbel_"+count+"_elements", bookmark.getTitle(), true);   		
    		count++; 
    		prop.put("xbel_"+count+"_elements", "</title>");
    		count++;
    		prop.put("xbel_"+count+"_elements", "<info>");   		
    		count++;
    		prop.put("xbel_"+count+"_elements", "<metadata owner=\"Mozilla\" ShortcutURL=\""
				+ htmlTools.encodeUnicode2xml(bookmark.getTagsString().replaceAll("/.*,", "").toLowerCase())
				+ "\"/>");   		
    		count++;
    		prop.put("xbel_"+count+"_elements", "<metadata owner=\"YaCy\" public=\""+Boolean.toString(bookmark.getPublic())+"\"/>");   		
    		count++;
    		prop.put("xbel_"+count+"_elements", "</info>");   		
    		count++;
    		prop.put("xbel_"+count+"_elements", "<desc>");
    		count++;
    		prop.putHTML("xbel_"+count+"_elements", bookmark.getDescription(), true);   		
    		count++; 
    		prop.put("xbel_"+count+"_elements", "</desc>");
    		count++;
    		prop.put("xbel_"+count+"_elements", "</bookmark>");   		
    		count++;     		
		}
    	return count;
    }    
}



