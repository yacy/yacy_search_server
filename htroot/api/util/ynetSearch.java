

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ynetSearch {
	
	public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {        
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final boolean isAdmin=switchboard.verifyAuthentication(header, true);
        final serverObjects prop = new serverObjects();              
                
    	if(post != null){        
    		if(!isAdmin){
			// force authentication if desired
    			if(post.containsKey("login")){
    				prop.put("AUTHENTICATE","admin log-in");
    			}
    			return prop;
    		} else {
    			InputStream is = null;    			 
    			try {
    			    String searchaddress = post.get("url");
    			    if (!searchaddress.startsWith("http://")) {
    			        // a relative path .. this addresses the local peer
    			        searchaddress = "http://" + switchboard.webIndex.seedDB.mySeed().getPublicAddress() + (searchaddress.startsWith("/") ? "" : "/") + searchaddress;
    			    }
    				final String s = searchaddress+"&search="+post.get("search")+"&count="+post.get("count")+"&offset="+post.get("offset");    				   				
    				final URL url = new URL(s);     				
    				is = url.openStream(); 
    				final String httpout = new Scanner(is).useDelimiter( "\\Z" ).next();
    				prop.put("http", httpout);    		    	
    			} 
    			catch ( final Exception e ) { 
    				prop.put("url", "error!");
    			} 
    			finally { 
    				if ( is != null ) 
    					try { is.close(); } catch ( final IOException e ) { } 
    			}
    		}
    	}    	  	
    	return prop;
	}
}