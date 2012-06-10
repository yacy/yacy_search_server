package interaction;

//ViewLog_p.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This File is contributed by Alexander Schier
//last major change: 14.12.2004
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


//You must compile this file with
//javac -classpath .:../classes ViewLog_p.java
//if the shell's current path is HTROOT

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.interaction.Interaction;
import net.yacy.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Triple {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
    	
    	final Switchboard sb = (Switchboard) env;
    	
        final serverObjects prop = new serverObjects();

        String url = "";
        String s = "";        
        String p = "";
        String o = "";
        String from = "";
        
        if(post != null){

            if(post.containsKey("url")){
                url = post.get("url");
            }
            
            if(post.containsKey("s")){
            	s = post.get("s");
            }
            
            if(post.containsKey("p")){
            	p = post.get("p");
            }
            
            if(post.containsKey("o")){
            	o = post.get("o");
            }
            
            if(post.containsKey("from")){
            	from = post.get("from");
            }
            
        }
        
        if (post.containsKey("load")) {
        	
        	o = Interaction.TripleGet(s, p);
        	
        } else {
        
        	Interaction.Triple(url, s, p, o, from);
        }                
        
        prop.put("result", o);

        
        
        return prop;
    }
}
