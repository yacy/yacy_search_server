
import java.net.MalformedURLException;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class delete_p {
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard switchboard = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final boolean isAdmin=switchboard.verifyAuthentication(header);
        if(post!= null){
    		if(!isAdmin){
    			// force authentication if desired
        			if(post.containsKey("login")){
                    	prop.authenticationRequired();
        			}
        			return prop;
    		}
        	try {
                if (post.containsKey("url") && switchboard.bookmarksDB.removeBookmark(ASCII.String((new DigestURL(post.get("url", "nourl"))).hash()))) {
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



