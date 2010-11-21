
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;

import de.anomic.data.BookmarksDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class getTag {
	final static int SORT_ALPHA = 1;
	final static int SORT_SIZE = 2;
	final static int SHOW_ALL = -1;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard switchboard = (Switchboard) env;
        final boolean isAdmin=switchboard.verifyAuthentication(header, true);
        final serverObjects prop = new serverObjects();
        Iterator<BookmarksDB.Tag> it = null;
        String tagName = "";
        int top = SHOW_ALL;
        int comp = SORT_ALPHA;
        
        
    	if(post != null){        
    		if(!isAdmin){
			// force authentication if desired
    			if(post.containsKey("login")){
    				prop.put("AUTHENTICATE","admin log-in");
    			}
    		} 
    	   			
        	if(post.containsKey("top")) {    				
        		final String s_top = post.get("top");
        		top = Integer.parseInt(s_top);    				
        	}
        	if(post.containsKey("sort")) {
        		final String sort = post.get("sort");
        		if (sort.equals("size"))
        			comp = SORT_SIZE;
        	}    				
        }
    	if(post != null && post.containsKey("tag")) {
    	    tagName=post.get("tag");    			
    	    if (!tagName.equals("")) {
    	        it = switchboard.bookmarksDB.getTagIterator(tagName, isAdmin, comp, top);						
    	    } 
    	} else {
    	    it = switchboard.bookmarksDB.getTagIterator(isAdmin, comp, top);
    	}    	 	

        // Iterator<bookmarksDB.Tag> it = switchboard.bookmarksDB.getTagIterator(isAdmin);
        
        int count=0;
        if(it != null) {
            BookmarksDB.Tag tag;
            while (it.hasNext()) {
                tag = it.next();
                if(!tag.getTagName().startsWith("/")) {						// ignore folder tags
                	prop.putXML("tags_"+count+"_name", tag.getTagName());
                	prop.put("tags_"+count+"_count", tag.size());
                	count++;
                }
            }
        }
        prop.put("tags", count);

        // return rewrite properties
        return prop;
    }
    
}
