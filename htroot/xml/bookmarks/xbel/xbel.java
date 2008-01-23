package xml.bookmarks.xbel;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.data.bookmarksDB;
import de.anomic.data.userDB;
import de.anomic.data.bookmarksDB.Tag;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class xbel {

	private static serverObjects prop;
	private static plasmaSwitchboard switchboard;
	private static userDB.Entry user;
	private static boolean isAdmin;	
	
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
 
    	int count = 0;
    	String username="";
    	Iterator it = null;    	
    	bookmarksDB.Tag tag;
    	Set<String> folders = new TreeSet<String>();
       	String path = "";
    	
    	prop = new serverObjects();
    	switchboard = (plasmaSwitchboard) env;    	
    	isAdmin=switchboard.verifyAuthentication(header, true);   
  
    	if(isAdmin) {
    	
        	//-----------------------
        	// create folder list
        	//-----------------------
           	
           	it = switchboard.bookmarksDB.getTagIterator(isAdmin);       	
           	while(it.hasNext()){
           		tag=(Tag) it.next();
           		if (tag.getFriendlyName().startsWith("/")) {
           			path = tag.getFriendlyName();
           			path = cleanPathString(path);                  
           			while(path.length() > 0){
           				folders.add(path);
           				path = path.replaceAll("(/.[^/]*$)", "");           				
           			}       			
           		}
           	}
           	
           	folders.add("\uffff");
           	it = folders.iterator();

           	count = recurseFolders(it,"/",0,true,"");
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
    private static String cleanPathString(String pathString){

        // get rid of double and trailing slashes
        while(pathString.endsWith("/")){
        	pathString = pathString.substring(0, pathString.length() -1);
        }
        while(pathString.contains("/,")){
        	pathString = pathString.replaceAll("/,", ",");
        }
        while(pathString.contains("//")){
        	pathString = pathString.replaceAll("//", "/");
        }
        return pathString;
    }
}