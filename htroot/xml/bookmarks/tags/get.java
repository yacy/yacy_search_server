// /xml.bookmarks/tags/get.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// last major change: 27.12.2005
// this file is contributed by Alexander Schier
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

// You must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

//package xml.bookmarks.tags;
package xml.bookmarks.tags;
import java.util.Iterator;

import de.anomic.data.bookmarksDB;
import de.anomic.data.bookmarksDB.Tag;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get {
	final static int SORT_ALPHA = 1;
	final static int SORT_SIZE = 2;
	final static int SHOW_ALL = -1;
	
	public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        boolean isAdmin=switchboard.verifyAuthentication(header, true);
        serverObjects prop = new serverObjects();
        Iterator<Tag> it = null;
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
        		String s_top = post.get("top");
        		top = Integer.parseInt(s_top);    				
        	}
        	if(post.containsKey("sort")) {
        		String sort = post.get("sort");
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
            bookmarksDB.Tag tag;
            while (it.hasNext()) {
                tag = it.next();
                if(!tag.getTagName().startsWith("/")) {						// ignore folder tags
                	prop.putHTML("tags_"+count+"_name", tag.getTagName(), true);
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
