package xml.bookmarks.xbel;

import java.util.Iterator;

import de.anomic.data.bookmarksDB;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class xbel {

	private static serverObjects prop;
	private static plasmaSwitchboard switchboard;
	private static boolean isAdmin;	
	
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
 
    	int count = 0;;
    	
    	prop = new serverObjects();
    	switchboard = (plasmaSwitchboard) env;    	
    	isAdmin=switchboard.verifyAuthentication(header, true);   
  
    	if(isAdmin) {
           	count = recurseFolders(switchboard.bookmarksDB.getFolderList(isAdmin),"/",0,true,"");
           	prop.put("xbel", count);  
           	
    	}    	
    	return prop;    // return from serverObjects respond()
    
    }

    private static int recurseFolders(Iterator<String> it, String root, int count, boolean next, String prev){
    	String fn="";    	
    	bookmarksDB.Bookmark bookmark;
   	
    	if(next) fn = it.next().toString();    		
    	else fn = prev;

    	if(fn.equals("\uffff")) {    		
    		int i = prev.replaceAll("[^/]","").length();
    		while(i>0){
    			prop.put("xbel_"+count+"_elements", "</folder>");
    			count++;
    			i--;
    		}    		
    		return count;
    	}
   
    	if(fn.startsWith(root)){
    		prop.put("xbel_"+count+"_elements", "<folder id=\">"+bookmarksDB.tagHash(fn)+"\">");
    		count++;
    		prop.put("xbel_"+count+"_elements", "<title>"+fn.replaceFirst(root+"/*","")+"</title>");   		
    		count++;    
    		Iterator bit=switchboard.bookmarksDB.getBookmarksIterator(fn, isAdmin);
    		while(bit.hasNext()){    			
    			bookmark=switchboard.bookmarksDB.getBookmark((String)bit.next());
    			prop.put("xbel_"+count+"_elements", "<bookmark id=\""+bookmark.getUrlHash()+"\" href=\""+bookmark.getUrl()+"\">");   		
        		count++; 
        		prop.put("xbel_"+count+"_elements", "<title>"+bookmark.getTitle()+"</title>");   		
        		count++; 
        		prop.put("xbel_"+count+"_elements", "<info><metadata owner=\"Mozilla\" ShortcutURL=\""+bookmark.getTagsString().replaceAll("/.*,", "")+"\"/></info>");   		
        		count++; 
        		prop.put("xbel_"+count+"_elements", "<desc>"+bookmark.getDescription()+"</desc>");   		
        		count++; 
        		prop.put("xbel_"+count+"_elements", "</bookmark>");   		
        		count++;        		
    		}    	
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
}