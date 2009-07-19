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
import de.anomic.http.metadata.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexShare_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
    	// return variable that accumulates replacements
        final Switchboard switchboard = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        if(switchboard == null) {
            prop.put("linkfreq", "30");
            prop.put("wordfreq", "10");
            prop.put("dtable", "");
            prop.put("rtable", "");
            prop.putNum("wcount", 0);
            prop.putNum("ucount", 0);
            return prop; // be save
        }
        if (post == null) {
            prop.put("linkfreq", switchboard.getConfigLong("defaultLinkReceiveFrequency",30));
            prop.put("wordfreq", switchboard.getConfigLong("defaultWordReceiveFrequency",10));
            prop.put("dtable", "");
            prop.put("rtable", "");
            prop.putNum("wcount", switchboard.indexSegment.termIndex().sizesMax());
            prop.putNum("ucount", switchboard.indexSegment.urlMetadata().size());
            return prop; // be save
        }
        
        if (post.containsKey("indexsharesetting")) {
            switchboard.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, (post.containsKey("distribute")) ? "true" : "false");
            switchboard.setConfig("allowReceiveIndex", (post.containsKey("receive")) ? "true" : "false");
            switchboard.setConfig("defaultLinkReceiveFrequency", post.get("linkfreq", "30"));
            switchboard.setConfig("defaultWordReceiveFrequency", post.get("wordfreq", "10"));
        }

        // insert constants
        prop.putNum("wcount", switchboard.indexSegment.termIndex().sizesMax());
        prop.putNum("ucount", switchboard.indexSegment.urlMetadata().size());
        
        // return rewrite properties
        return prop;
    }
}
