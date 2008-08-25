// /xml/config_p.java
// -------------------------------
// (C) 2006 Alexander Schier
// part of YaCy
//
// last major change: 06.02.2006
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
// Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  US


package xml;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import de.anomic.http.httpRequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class config_p {
    
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        //plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        String key; 
        
        //change a Key
        if(post != null && post.containsKey("key") && post.containsKey("value")){
            key=post.get("key");
            final String value=post.get("value");
            if(!key.equals("")){
                env.setConfig(key, value);
            }
        }
        
        Iterator<String> keys = env.configKeys();
        
        final List<String> list = new ArrayList<String>(250);
        while(keys.hasNext()){
            list.add(keys.next());
        }
        Collections.sort(list);
        keys = list.iterator();
        
        int count=0;
        while(keys.hasNext()){
            key = keys.next();
            prop.putHTML("options_"+count+"_key", key);
            prop.putHTML("options_"+count+"_value", env.getConfig(key, "ERROR"));
            count++;        
        }
        prop.put("options", count);
        
        // return rewrite properties
        return prop;
    }
    
}



