
import java.net.MalformedURLException;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class delete_p {
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard switchboard = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final boolean isAdmin=switchboard.verifyAuthentication(header, true);        
        if(post!= null){
    		if(!isAdmin){
    			// force authentication if desired
        			if(post.containsKey("login")){
        				prop.put("AUTHENTICATE","admin log-in");
        			}
        			return prop;
    		} 
        	try {
                if (post.containsKey("url") && switchboard.bookmarksDB.removeBookmark(UTF8.String((new DigestURI(post.get("url", "nourl"))).hash()))) {
                	prop.put("result", "1");
                } else if (post.containsKey("urlhash") && switchboard.bookmarksDB.removeBookmark(post.get("urlhash", "nohash"))) {
                	prop.put("result", "1");
                } else {
                	prop.put("result", "0");
                }
            } catch (final MalformedURLException e) {
                prop.put("result", "0");
            }
        }else{
        	prop.put("result", "0");
        }        
        // return rewrite properties
        return prop;
    }    
}



