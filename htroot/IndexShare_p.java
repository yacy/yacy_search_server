// IndexShare_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 24.08.2004
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
// javac -classpath .:../classes IndexShare_p.java
// if the shell's current path is HTROOT

//import java.util.*;
//import java.net.*;
//import java.io.*;
//import de.anomic.tools.*;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexShare_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
    	// return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
		serverObjects prop = new serverObjects();

        if ((post == null) || (env == null)) {
            prop.put("linkfreq", switchboard.getConfig("defaultLinkReceiveFrequency","30"));
            prop.put("wordfreq", switchboard.getConfig("defaultWordReceiveFrequency","10"));
            prop.put("dtable", "");
            prop.put("rtable", "");
            prop.putNum("wcount", switchboard.webIndex.size());
            prop.putNum("ucount", switchboard.webIndex.countURL());
            return prop; // be save
        }
        
        if (post.containsKey("indexsharesetting")) {
            switchboard.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, (post.containsKey("distribute")) ? "true" : "false");
            switchboard.setConfig("allowReceiveIndex", (post.containsKey("receive")) ? "true" : "false");
            switchboard.setConfig("defaultLinkReceiveFrequency", post.get("linkfreq", "30"));
            switchboard.setConfig("defaultWordReceiveFrequency", post.get("wordfreq", "10"));
        }

        // insert constants
        prop.putNum("wcount", switchboard.webIndex.size());
        prop.putNum("ucount", switchboard.webIndex.countURL());
        
        // return rewrite properties
        return prop;
    }
}
