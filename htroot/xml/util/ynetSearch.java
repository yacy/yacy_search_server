///xml/util/ynetSearch.java
//(C) 2008 by Stefan Foerster; sof@gmx.de, Hamburg, Germany
//first published 15.05.2008 on http://yacy.net
//
//This is a part of YaCy, a peer-to-peer based web search engine
//
//$LastChangedDate: 2008-05-16 00:44:07 +0200 (Fr, 16 May 2008) $
//$LastChangedRevision: 4806 $
//$LastChangedBy: apfelmaennchen $
//
//LICENSE
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
package xml.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ynetSearch {
	
	public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {        
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        boolean isAdmin=switchboard.verifyAuthentication(header, true);
        serverObjects prop = new serverObjects();              
                
    	if(post != null){        
    		if(!isAdmin){
			// force authentication if desired
    			if(post.containsKey("login")){
    				prop.put("AUTHENTICATE","admin log-in");
    			}
    		} else {
    			InputStream is = null;    			 
    			try { 	
    				String s = post.get("url")+"&search="+post.get("search")+"&count="+post.get("count")+"&offset="+post.get("offset");    				   				
    				URL url = new URL(s);     				
    				is = url.openStream(); 
    				String httpout = new Scanner(is).useDelimiter( "\\Z" ).next();
    				prop.put("http", httpout);    		    	
    			} 
    			catch ( Exception e ) { 
    				prop.put("url", "error!");
    			} 
    			finally { 
    				if ( is != null ) 
    					try { is.close(); } catch ( IOException e ) { } 
    			}
    		}
    	}    	  	
    	return prop;
	}
}