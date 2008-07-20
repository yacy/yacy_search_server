// ConfigGeneric_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// This File is contributed by Alexander Schier
//
// $LastChangedDate: 2005-09-13 00:20:37 +0200 (Di, 13 Sep 2005) $
// $LastChangedRevision: 715 $
// $LastChangedBy: borg-0300 $
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
// javac -classpath .:../classes Config_p.java
// if the shell's current path is HTROOT

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ConfigProperties_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        int count=0;
        String key="";

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
        while(keys.hasNext()){
            key = keys.next();
            prop.putHTML("options_"+count+"_key", key);
            prop.putHTML("options_"+count+"_value", env.getConfig(key, "ERROR"));
            count++;        
        }

        prop.put("options", count);
        return prop;
    }

}
